package com.example.util

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.yield
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * On-device subtitle translation using the same downloadable Google language
 * packs as the Google Translate app. Only dialogue text is sent to the model.
 * Cue numbers and timestamps stay on the subtitle entry and are never translated.
 */
object OfflineSubtitleTranslator {

    private const val TAG = "OfflineSubtitleTranslator"

    fun normalizeCode(code: String): String {
        return when (code.trim().lowercase()) {
            "zh-cn", "zh_cn", "zh-hans", "chinese" -> TranslateLanguage.CHINESE
            "bangla", "bengali" -> TranslateLanguage.BENGALI
            else -> code.trim().lowercase().substringBefore("-").substringBefore("_")
        }
    }

    fun mlKitLanguageOrNull(code: String): String? {
        val normalized = normalizeCode(code)
        return TranslateLanguage.fromLanguageTag(normalized)
    }

    suspend fun areModelsReady(sourceCode: String, targetCode: String): Boolean {
        val source = mlKitLanguageOrNull(sourceCode) ?: return false
        val target = mlKitLanguageOrNull(targetCode) ?: return false
        if (source == target) return true
        return isDownloaded(source) && isDownloaded(target)
    }

    suspend fun downloadModels(
        sourceCode: String,
        targetCode: String,
        onStatus: (String) -> Unit = {}
    ) {
        val source = requireLanguage(sourceCode)
        val target = requireLanguage(targetCode)
        if (source == target) {
            onStatus("Same language. No pack needed.")
            return
        }
        onStatus("Downloading offline language pack...")
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(source)
            .setTargetLanguage(target)
            .build()
        val translator = Translation.getClient(options)
        try {
            val conditions = DownloadConditions.Builder().build()
            translator.downloadModelIfNeeded(conditions).awaitResult()
            onStatus("Language pack ready. Translation can work offline.")
        } finally {
            translator.close()
        }
    }

    /**
     * Translates dialogue only. Each result keeps the original index and times.
     */
    suspend fun translateSubtitles(
        subtitles: List<SubtitleEntry>,
        sourceCode: String,
        targetCode: String,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): List<SubtitleEntry> {
        if (subtitles.isEmpty()) return emptyList()
        val source = requireLanguage(sourceCode)
        val target = requireLanguage(targetCode)
        val total = subtitles.size

        if (source == target) {
            onProgress(total, total)
            return subtitles
        }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(source)
            .setTargetLanguage(target)
            .build()
        val translator = Translation.getClient(options)
        try {
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).awaitResult()
            val results = ArrayList<SubtitleEntry>(total)
            for ((index, entry) in subtitles.withIndex()) {
                yield()
                val translatedText = translateDialogueOnly(translator, entry.text)
                results += entry.copy(text = translatedText)
                onProgress(index + 1, total)
            }
            return results
        } finally {
            translator.close()
        }
    }

    private suspend fun translateDialogueOnly(
        translator: com.google.mlkit.nl.translate.Translator,
        text: String
    ): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return text

        val (masked, tokens) = SubtitleTextProtector.protect(text)
        if (!SubtitleTextProtector.hasTranslatableWords(masked)) {
            return text
        }

        return try {
            val translated = translator.translate(masked).awaitResult()
            if (translated.isBlank() || !SubtitleTextProtector.containsAllMarkers(translated, tokens.size)) {
                // Keep the original line so a dropped time or number is never lost.
                return text
            }
            val restored = SubtitleTextProtector.restore(translated, tokens)
            if (restored.isBlank()) text else restored
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Line kept original: ${e.message}")
            text
        }
    }

    private suspend fun isDownloaded(language: String): Boolean {
        val model = TranslateRemoteModel.Builder(language).build()
        return try {
            RemoteModelManager.getInstance().isModelDownloaded(model).awaitResult()
        } catch (e: Exception) {
            Log.w(TAG, "Could not check model $language: ${e.message}")
            false
        }
    }

    private fun requireLanguage(code: String): String {
        return mlKitLanguageOrNull(code)
            ?: throw IllegalArgumentException("Offline packs do not include \"$code\".")
    }

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { value ->
            if (cont.isActive) cont.resume(value)
        }
        addOnFailureListener { error ->
            if (cont.isActive) cont.resumeWithException(error)
        }
        addOnCanceledListener {
            cont.cancel()
        }
    }
}
