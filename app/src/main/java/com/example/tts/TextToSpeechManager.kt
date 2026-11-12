package com.example.tts

import android.content.Context
import android.util.Log
import com.example.util.LanguageDetector
import com.example.util.SubtitleEntry
import com.example.tts.neural.NeuralVoiceManager
import com.example.tts.neural.PersonaSpec
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class SentenceSpan(
    val text: String,
    val startCharOffset: Int,
    val endCharOffset: Int
)

enum class SpeechState {
    IDLE,
    INITIALIZING,
    READY,
    SPEAKING,
    PAUSED,
    ERROR
}

data class FriendlyVoice(
    val id: String,
    val displayName: String,
    val languageGroup: String,
    val locale: Locale,
    val sampleText: String = "",
    val isSystemVoice: Boolean = false,
    val systemVoice: android.speech.tts.Voice? = null,
    val rolePitch: Float = 1f,
    val roleRate: Float = 1f
)

class TextToSpeechManager(private val context: Context) {

    private val neuralVoices = NeuralVoiceManager.get(context)

    private val _speechState = MutableStateFlow(SpeechState.INITIALIZING)
    val speechState: StateFlow<SpeechState> = _speechState.asStateFlow()

    private val _highlightRange = MutableStateFlow<Pair<Int, Int>?>(null)
    val highlightRange: StateFlow<Pair<Int, Int>?> = _highlightRange.asStateFlow()

    private val _currentSentenceIndex = MutableStateFlow(0)
    val currentSentenceIndex: StateFlow<Int> = _currentSentenceIndex.asStateFlow()

    private val _totalSentences = MutableStateFlow(0)
    val totalSentences: StateFlow<Int> = _totalSentences.asStateFlow()

    private val _selectedVoice = MutableStateFlow<String?>(null)
    val selectedVoice: StateFlow<String?> = _selectedVoice.asStateFlow()

    private val _speed = MutableStateFlow(1.0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _pitch = MutableStateFlow(1.0f)
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    private val _autoFitSubtitleDuration = MutableStateFlow(true)
    val autoFitSubtitleDuration: StateFlow<Boolean> = _autoFitSubtitleDuration.asStateFlow()

    private val _previewingVoiceId = MutableStateFlow<String?>(null)
    val previewingVoiceId: StateFlow<String?> = _previewingVoiceId.asStateFlow()

    val concurrentListeners = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    var saveListener: CompletableDeferred<File?>? = null
    var saveFileTarget: File? = null

    private var fullText: String = ""
    private var sentences: List<SentenceSpan> = emptyList()
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    init {
        _speechState.value = SpeechState.READY
    }

    fun setSpeed(speed: Float) { _speed.value = speed }
    fun setPitch(pitch: Float) { _pitch.value = pitch }
    fun setAutoFitSubtitleDuration(enabled: Boolean) { _autoFitSubtitleDuration.value = enabled }
    fun setVoice(voiceName: String) { _selectedVoice.value = voiceName }

    fun getLocaleForLanguageCode(langCode: String): Locale {
        return when (langCode.lowercase()) {
            "bn" -> Locale("bn", "BD")
            "hi" -> Locale("hi", "IN")
            "ur" -> Locale("ur", "PK")
            "ar" -> Locale("ar", "SA")
            "ja" -> Locale.JAPAN
            "ko" -> Locale.KOREA
            "zh", "zh-cn" -> Locale.SIMPLIFIED_CHINESE
            "es" -> Locale("es", "ES")
            "fr" -> Locale.FRENCH
            "de" -> Locale.GERMAN
            "it" -> Locale.ITALY
            "pt" -> Locale("pt", "BR")
            "ru" -> Locale("ru", "RU")
            "id" -> Locale("id", "ID")
            "tr" -> Locale("tr", "TR")
            "th" -> Locale("th", "TH")
            "vi" -> Locale("vi", "VN")
            else -> Locale(langCode)
        }
    }

    fun applyVoiceOrLocale(voiceName: String?, fallbackLangCode: String? = null) {
        // Neural-only — no system engine to configure.
    }

    fun getFriendlyVoicesList(): List<FriendlyVoice> {
        return VoiceCast.voicesFor(neuralVoices.readyLangs())
    }

    fun previewVoice(voice: FriendlyVoice?, speed: Float = _speed.value, pitch: Float = _pitch.value) {
        val neural = NeuralVoiceManager.get(context)
        stopVoicePreview()
        _previewingVoiceId.value = voice?.id
        if (voice == null) return
        neural.neuralRoleOf(voice.id)?.let { (lang, role) ->
            if (neural.isReady(lang)) {
                neural.previewAsync(lang, role, speed, pitch)
                return
            }
        }
        _previewingVoiceId.value = null
    }

    fun previewLanguageVoice(langCode: String, speed: Float = _speed.value, pitch: Float = _pitch.value) {
        val neural = NeuralVoiceManager.get(context)
        stopVoicePreview()
        _previewingVoiceId.value = langCode
        if (neural.isReady(langCode)) {
            neural.previewAsync(langCode, PersonaSpec.YOUNG_MALE, speed, pitch)
        } else {
            _previewingVoiceId.value = null
        }
    }

    fun previewBlock(text: String, voiceName: String?, speed: Float, pitch: Float) {
        val spoken = text.trim()
        if (spoken.isEmpty()) return
        stopVoicePreview()
        _previewingVoiceId.value = voiceName ?: "block"

        neuralVoices.neuralRoleOf(voiceName)?.let { (lang, role) ->
            if (neuralVoices.isReady(lang)) {
                neuralVoices.previewAsync(lang, role, speed, pitch)
                return
            }
        }

        try {
            val detected = LanguageDetector.detectLanguage(spoken)
            val lang = detected.code
            val neural = NeuralVoiceManager.get(context)
            if (neural.isReady(lang)) {
                val tmp = File(context.cacheDir, "preview_block_${System.currentTimeMillis()}.wav")
                val ok = neural.synthesizeToFileBlocking(
                    spoken, voiceName ?: "cast:young_male:$lang", lang, speed, pitch, tmp
                )
                if (ok && tmp.exists()) {
                    android.media.MediaPlayer().apply {
                        setDataSource(tmp.absolutePath)
                        prepare()
                        start()
                        setOnCompletionListener {
                            release()
                            tmp.delete()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("BalaSpeakTTS", "Error previewing block: ${e.message}")
            _previewingVoiceId.value = null
        }
    }

    fun stopVoicePreview() {
        if (_previewingVoiceId.value != null) {
            NeuralVoiceManager.get(context).stopPreview()
            _previewingVoiceId.value = null
        }
    }

    fun parseAndLoadText(text: String) {
        fullText = text
        sentences = parseSentencesText(text)
        _totalSentences.value = sentences.size
        _currentSentenceIndex.value = 0
        _highlightRange.value = null
    }

    private fun parseSentencesText(text: String): List<SentenceSpan> {
        if (text.isEmpty()) return emptyList()
        val list = mutableListOf<SentenceSpan>()
        val delimiters = setOf('.', '?', '!', '।', '\n', '\r')
        var start = 0
        var i = 0
        val len = text.length
        while (i < len) {
            val char = text[i]
            if (delimiters.contains(char)) {
                val sentenceText = text.substring(start, i + 1)
                if (sentenceText.trim().isNotEmpty()) {
                    list.add(SentenceSpan(sentenceText, start, i + 1))
                }
                start = i + 1
            }
            i++
        }
        if (start < len) {
            val remaining = text.substring(start)
            if (remaining.trim().isNotEmpty()) {
                list.add(SentenceSpan(remaining, start, len))
            }
        }
        return list
    }

    fun startSpeaking() {
        if (_speechState.value == SpeechState.INITIALIZING) return
        if (sentences.isEmpty()) {
            _speechState.value = SpeechState.READY
            return
        }
        if (_speechState.value == SpeechState.PAUSED) {
            _speechState.value = SpeechState.SPEAKING
            speakCurrentSentence()
        } else {
            _currentSentenceIndex.value = 0
            speakCurrentSentence()
        }
    }

    private fun speakCurrentSentence() {
        val i = _currentSentenceIndex.value
        if (i in sentences.indices) {
            val textToSpeak = sentences[i].text.trim()
            if (textToSpeak.isEmpty()) {
                playNextSentence()
                return
            }
            val neural = NeuralVoiceManager.get(context)
            val voiceName = _selectedVoice.value
            val lang = voiceName?.let { neural.neuralRoleOf(it)?.first } ?: "en"
            if (neural.isReady(lang)) {
                val tmp = File(context.cacheDir, "speak_seg_${System.currentTimeMillis()}.wav")
                val ok = neural.synthesizeToFileBlocking(
                    textToSpeak, voiceName, lang, _speed.value, _pitch.value, tmp
                )
                if (ok && tmp.exists()) {
                    _speechState.value = SpeechState.SPEAKING
                    android.media.MediaPlayer().apply {
                        setDataSource(tmp.absolutePath)
                        prepare()
                        start()
                        setOnCompletionListener {
                            try { release() } catch (_: Exception) {}
                            tmp.delete()
                            if (_speechState.value == SpeechState.SPEAKING) {
                                playNextSentence()
                            }
                        }
                    }
                } else {
                    _speechState.value = SpeechState.ERROR
                }
            } else {
                _speechState.value = SpeechState.ERROR
            }
        } else {
            _speechState.value = SpeechState.READY
            _highlightRange.value = null
        }
    }

    private fun playNextSentence() {
        val nextIdx = _currentSentenceIndex.value + 1
        if (nextIdx < sentences.size) {
            _currentSentenceIndex.value = nextIdx
            speakCurrentSentence()
        } else {
            _currentSentenceIndex.value = 0
            _speechState.value = SpeechState.READY
            _highlightRange.value = null
        }
    }

    fun pauseSpeaking() {
        if (_speechState.value == SpeechState.SPEAKING) {
            _speechState.value = SpeechState.PAUSED
            NeuralVoiceManager.get(context).stopPreview()
        }
    }

    fun stopSpeaking() {
        _speechState.value = SpeechState.READY
        _highlightRange.value = null
        _currentSentenceIndex.value = 0
        NeuralVoiceManager.get(context).stopPreview()
    }

    fun skipNext() {
        val nextIdx = _currentSentenceIndex.value + 1
        if (nextIdx < sentences.size) {
            _currentSentenceIndex.value = nextIdx
            if (_speechState.value == SpeechState.SPEAKING) {
                speakCurrentSentence()
            }
        }
    }

    fun skipPrevious() {
        val prevIdx = (_currentSentenceIndex.value - 1).coerceAtLeast(0)
        _currentSentenceIndex.value = prevIdx
        if (_speechState.value == SpeechState.SPEAKING) {
            speakCurrentSentence()
        }
    }

    fun seekToSentence(index: Int) {
        if (index in sentences.indices) {
            _currentSentenceIndex.value = index
            if (_speechState.value == SpeechState.SPEAKING) {
                speakCurrentSentence()
            }
        }
    }

    suspend fun compileSubtitlesToWav(
        subtitles: List<SubtitleEntry>,
        destWavFile: File,
        speed: Float,
        pitch: Float,
        voiceName: String? = null,
        autoFitDuration: Boolean = true,
        perCueVoices: List<String?>? = null,
        onProgress: (Int, Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        if (subtitles.isEmpty()) return@withContext false

        val voiced = if (perCueVoices != null && perCueVoices.size == subtitles.size) {
            subtitles.mapIndexed { index, entry -> entry to perCueVoices[index] }
                .sortedBy { it.first.startTimeMs }
        } else {
            null
        }
        val sortedSubtitles = voiced?.map { it.first } ?: subtitles.sortedBy { it.startTimeMs }
        val sortedVoices = voiced?.map { it.second }
        val tempDir = File(context.cacheDir, "sub_batch_${System.currentTimeMillis()}")
        tempDir.mkdirs()

        val sampleText = sortedSubtitles.take(10).joinToString(" ") { it.text }
        val detected = LanguageDetector.detectLanguage(sampleText)

        val neural = NeuralVoiceManager.get(context)
        val total = sortedSubtitles.size
        val tempFilesList = ArrayList<File>(total)
        repeat(total) { tempFilesList.add(File("")) }

        val completedCount = AtomicInteger(0)
        val neuralCandidates = sortedVoices ?: List(total) { voiceName }
        val useNeural = neuralVoices.canHandleAll(neuralCandidates, detected.code)

        if (useNeural) {
            for (i in sortedSubtitles.indices) {
                yield()
                val textTrimmed = sortedSubtitles[i].text.trim()
                if (textTrimmed.isEmpty()) {
                    onProgress(i + 1, total)
                    continue
                }
                val segmentWav = File(tempDir, "seg_$i.wav")
                val ok = neural.synthesizeToFileBlocking(
                    text = textTrimmed,
                    voiceName = neuralCandidates[i] ?: voiceName,
                    fallbackLang = detected.code,
                    speed = speed,
                    pitch = pitch,
                    outFile = segmentWav
                )
                if (ok && segmentWav.exists() && segmentWav.length() >= 44) {
                    tempFilesList[i] = segmentWav
                }
                onProgress(i + 1, total)
            }
        } else {
            tempDir.deleteRecursively()
            return@withContext false
        }

        val successCount = tempFilesList.count { it.exists() && it.length() >= 44 }
        if (successCount == 0) {
            tempDir.deleteRecursively()
            return@withContext false
        }

        val sampleWav = tempFilesList.first { it.exists() && it.length() >= 44 }
        val headerBuffer = ByteArray(44)
        FileInputStream(sampleWav).use { fis -> fis.read(headerBuffer) }
        val channels = ByteBuffer.wrap(headerBuffer, 22, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt().coerceIn(1, 2)
        val sampleRate = ByteBuffer.wrap(headerBuffer, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int.coerceIn(8000, 48000)
        val bitsPerSample = ByteBuffer.wrap(headerBuffer, 34, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt().coerceIn(8, 32)
        val blockAlign = channels * (bitsPerSample / 8)

        val lastCard = sortedSubtitles.last()
        val totalDurationMs = lastCard.endTimeMs + 3500L
        val totalFrames = (totalDurationMs * sampleRate) / 1000L
        val totalPcmBytes = totalFrames * blockAlign

        val raf = RandomAccessFile(destWavFile, "rw")
        raf.setLength(0L)
        writeWavHeader(raf, totalPcmBytes, sampleRate, channels, bitsPerSample)
        raf.setLength(44L + totalPcmBytes)

        for (i in sortedSubtitles.indices) {
            val card = sortedSubtitles[i]
            val segmentWav = tempFilesList[i]
            if (!segmentWav.exists() || segmentWav.length() < 44) continue
            val countShorts = ((segmentWav.length() - 44) / 2).toInt()
            if (countShorts <= 0) continue
            val pcmShorts = ShortArray(countShorts)
            FileInputStream(segmentWav).use { fis ->
                fis.skip(44)
                val rawBuffer = ByteArray(countShorts * 2)
                var read = 0
                while (read < rawBuffer.size) {
                    val r = fis.read(rawBuffer, read, rawBuffer.size - read)
                    if (r <= 0) break
                    read += r
                }
                ByteBuffer.wrap(rawBuffer, 0, read).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcmShorts, 0, read / 2)
            }
            val spokenShorts = if (bitsPerSample == 16) trimEdgeSilence(pcmShorts, channels, sampleRate) else pcmShorts
            val originalDurationMs = ((spokenShorts.size.toLong() * 1000L) / (sampleRate * channels)).toLong()
            val nextCardStartMs = if (i + 1 < sortedSubtitles.size) sortedSubtitles[i + 1].startTimeMs else (card.endTimeMs + 2500L)
            val availableWindowMs = (nextCardStartMs - card.startTimeMs).coerceAtLeast(300L)
            val cardDurationMs = (card.endTimeMs - card.startTimeMs).coerceAtLeast(300L)
            val maxAllowedDurationMs = minOf(availableWindowMs, maxOf(cardDurationMs, 750L))
            val processedShorts = if (autoFitDuration && originalDurationMs > maxAllowedDurationMs) {
                val speedFactor = (originalDurationMs.toDouble() / maxAllowedDurationMs.toDouble()).coerceIn(1.0, 2.4)
                timeStretchWSOLA(spokenShorts, speedFactor, sampleRate, channels)
            } else {
                spokenShorts
            }
            val startFrame = (card.startTimeMs * sampleRate) / 1000L
            val startByteOffset = 44L + startFrame * blockAlign
            val existingBytes = ByteArray(processedShorts.size * 2)
            raf.seek(startByteOffset)
            val bytesRead = raf.read(existingBytes)
            val existingShorts = ShortArray(processedShorts.size)
            if (bytesRead > 0) {
                ByteBuffer.wrap(existingBytes, 0, bytesRead).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(existingShorts, 0, bytesRead / 2)
            }
            val mixedShorts = ShortArray(processedShorts.size)
            for (s in processedShorts.indices) {
                val s1 = existingShorts[s].toInt()
                val s2 = processedShorts[s].toInt()
                mixedShorts[s] = (s1 + s2).coerceIn(-32768, 32767).toShort()
            }
            val outBytes = ByteArray(mixedShorts.size * 2)
            ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(mixedShorts)
            raf.seek(startByteOffset)
            raf.write(outBytes)
        }
        val finalAudioLen = (raf.length() - 44L).coerceAtLeast(totalPcmBytes)
        writeWavHeader(raf, finalAudioLen, sampleRate, channels, bitsPerSample)
        raf.close()
        tempDir.deleteRecursively()
        return@withContext true
    }

    private fun trimEdgeSilence(pcm: ShortArray, channels: Int, sampleRate: Int): ShortArray {
        if (channels <= 0 || pcm.size < channels * 8) return pcm
        val frames = pcm.size / channels
        val threshold = 480
        fun loud(frame: Int): Boolean {
            val base = frame * channels
            var peak = 0
            for (c in 0 until channels) {
                val value = kotlin.math.abs(pcm[base + c].toInt())
                if (value > peak) peak = value
            }
            return peak >= threshold
        }
        var start = 0
        while (start < frames && !loud(start)) start++
        var end = frames - 1
        while (end > start && !loud(end)) end--
        if (end - start < frames / 10) return pcm
        val pad = (sampleRate * 0.04).toInt().coerceAtLeast(1)
        val from = (start - pad).coerceAtLeast(0)
        val to = (end + pad + 1).coerceAtMost(frames)
        if (from == 0 && to == frames) return pcm
        return pcm.copyOfRange(from * channels, to * channels)
    }

    private fun timeStretchWSOLA(
        input: ShortArray,
        speedRatio: Double,
        sampleRate: Int,
        channels: Int
    ): ShortArray {
        if (speedRatio in 0.98..1.02 || input.isEmpty() || channels <= 0) return input
        val winSizeSamples = ((sampleRate * 0.025).toInt() / 2) * 2
        val winSize = winSizeSamples.coerceIn(128, 2048)
        val synthHop = winSize / 2
        val maxDelta = winSize / 4
        val numFramesIn = input.size / channels
        val numFramesOutEstimate = (numFramesIn / speedRatio).toInt() + winSize
        val output = ShortArray(numFramesOutEstimate * channels)
        val overlapWeights = FloatArray(numFramesOutEstimate)
        val window = FloatArray(winSize) { i ->
            (0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / (winSize - 1)))).toFloat()
        }
        var inPos = 0.0
        var outPos = 0
        while (inPos + winSize + maxDelta < numFramesIn && outPos + winSize < numFramesOutEstimate) {
            val targetIn = inPos.toInt()
            var bestOffset = 0
            if (outPos > 0 && synthHop < winSize) {
                var bestCorr = Long.MIN_VALUE
                for (delta in -maxDelta..maxDelta) {
                    val candidateIn = targetIn + delta
                    if (candidateIn < 0 || candidateIn + winSize > numFramesIn) continue
                    var corr = 0L
                    val step = if (winSize > 512) 2 else 1
                    for (k in 0 until winSize step step) {
                        val inIdx = (candidateIn + k) * channels
                        val outIdx = (outPos + k) * channels
                        val inSample = input[inIdx].toLong()
                        val outSample = output[outIdx].toLong()
                        corr += inSample * outSample
                    }
                    if (corr > bestCorr) {
                        bestCorr = corr
                        bestOffset = delta
                    }
                }
            }
            val chosenIn = (targetIn + bestOffset).coerceIn(0, numFramesIn - winSize)
            for (k in 0 until winSize) {
                val w = window[k]
                val fOut = outPos + k
                if (fOut >= numFramesOutEstimate) break
                for (c in 0 until channels) {
                    val inVal = input[(chosenIn + k) * channels + c].toFloat() * w
                    val outIdx = fOut * channels + c
                    val currVal = output[outIdx].toFloat() + inVal
                    output[outIdx] = currVal.coerceIn(-32768f, 32767f).toInt().toShort()
                }
                overlapWeights[fOut] += w
            }
            inPos += synthHop * speedRatio
            outPos += synthHop
        }
        val finalLength = minOf(outPos + winSize / 2, numFramesOutEstimate)
        val result = ShortArray(finalLength * channels)
        for (f in 0 until finalLength) {
            val weight = overlapWeights[f]
            val norm = if (weight > 0.01f) 1.0f / weight else 1.0f
            for (c in 0 until channels) {
                val idx = f * channels + c
                val normalized = (output[idx].toFloat() * norm).coerceIn(-32768f, 32767f)
                result[idx] = normalized.toInt().toShort()
            }
        }
        return result
    }

    private fun writeWavHeader(
        outStream: RandomAccessFile,
        totalAudioLen: Long,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = channels * (bitsPerSample / 8)
        outStream.seek(0)
        outStream.writeBytes("RIFF")
        outStream.write(intToByteArray(totalDataLen.toInt()), 0, 4)
        outStream.writeBytes("WAVE")
        outStream.writeBytes("fmt ")
        outStream.write(intToByteArray(16), 0, 4)
        outStream.write(shortToByteArray(1.toShort()), 0, 2)
        outStream.write(shortToByteArray(channels.toShort()), 0, 2)
        outStream.write(intToByteArray(sampleRate), 0, 4)
        outStream.write(intToByteArray(byteRate), 0, 4)
        outStream.write(shortToByteArray(blockAlign.toShort()), 0, 2)
        outStream.write(shortToByteArray(bitsPerSample.toShort()), 0, 2)
        outStream.writeBytes("data")
        outStream.write(intToByteArray(totalAudioLen.toInt()), 0, 4)
    }

    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xff).toByte(),
            ((value shr 8) and 0xff).toByte(),
            ((value shr 16) and 0xff).toByte(),
            ((value shr 24) and 0xff).toByte()
        )
    }

    private fun shortToByteArray(value: Short): ByteArray {
        return byteArrayOf(
            (value.toInt() and 0xff).toByte(),
            ((value.toInt() shr 8) and 0xff).toByte()
        )
    }

    fun shutdown() {
        // Neural engine managed by NeuralVoiceManager singleton
    }
}
