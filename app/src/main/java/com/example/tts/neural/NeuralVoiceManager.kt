package com.example.tts.neural

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.LongBuffer
import java.util.concurrent.ConcurrentHashMap

/**
 * Offline neural TTS voices for BalaSpeak.
 *
 * Engines:
 *  - "kokoro"     : sherpa-onnx Kokoro v1.0 int8 (English, 53 studio speakers, af_heart)
 *  - "piper"      : sherpa-onnx VITS/Piper (Hindi: pratham/rohan male, priyamvada female)
 *  - "mms_direct" : direct ONNX Runtime inference of the (single-input) transformers
 *                   MMS-TTS export (Bengali). Tokenization replicates
 *                   transformers VitsTokenizer: char ids from the official MMS
 *                   vocab, then intersperse(pad=0) because add_blank=true.
 *
 * All packs are downloaded on demand (never bundled) into filesDir/tts/<lang>/.
 * Every persona of VoiceCast works on every installed pack. Output audio is
 * always normalized to 16 kHz mono 16-bit so the dubbing merger can mix
 * segments of any language without format conflicts.
 */
class NeuralVoiceManager private constructor(context: Context) {

    /** url + size; archives (tar.bz2) extract into subDir inside the pack dir. */
    data class PackDownload(val url: String, val bytes: Long, val subDir: String = "", val assetPath: String = "")

    data class Pack(
        val langCode: String,
        val displayName: String,
        val qualityNote: String,
        val engine: String, // "kokoro" | "piper" | "mms_direct"
        val downloads: List<PackDownload>
    ) {
        val totalBytes: Long get() = downloads.sumOf { it.bytes }
    }

    sealed class PackState {
        object NotDownloaded : PackState()
        data class Downloading(val received: Long, val total: Long) : PackState()
        object Extracting : PackState()
        object Ready : PackState()
        data class Failed(val message: String) : PackState()
    }

    private val appContext = context.applicationContext

    val packs: List<Pack> = listOf(
        Pack(
            langCode = "bn",
            displayName = "বাংলা (Bengali)",
            qualityNote = "Neural · MMS-TTS",
            engine = "mms_direct",
            downloads = listOf(
                PackDownload(
                    "https://huggingface.co/sriram09764/itantra-tts-onnx/resolve/main/bn/model.onnx",
                    114_417_798L,
                    "",
                    "tts/bn/model.onnx"
                )
            )
        ),
        Pack(
            langCode = "hi",
            displayName = "हिन्दी (Hindi)",
            qualityNote = "Neural · 3 Piper speakers",
            engine = "piper",
            downloads = listOf(
                PackDownload(
                    "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-hi_IN-pratham-medium-int8.tar.bz2",
                    20_985_000L, "pratham",
                    "tts/hi/pratham/vits-piper-hi_IN-pratham-medium-int8.tar.bz2"
                ),
                PackDownload(
                    "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-hi_IN-rohan-medium-int8.tar.bz2",
                    20_100_000L, "rohan",
                    "tts/hi/rohan/vits-piper-hi_IN-rohan-medium-int8.tar.bz2"
                ),
                PackDownload(
                    "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-hi_IN-priyamvada-medium-int8.tar.bz2",
                    20_100_000L, "priyamvada",
                    "tts/hi/priyamvada/vits-piper-hi_IN-priyamvada-medium-int8.tar.bz2"
                )
            )
        ),
        Pack(
            langCode = "en",
            displayName = "English",
            qualityNote = "Studio · Kokoro v1.0 · af_heart · 53 speakers",
            engine = "kokoro",
            downloads = listOf(
                PackDownload(
                    "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-multi-lang-v1_0.tar.bz2",
                    132_303_094L,
                    "",
                    "tts/en/kokoro-int8-multi-lang-v1_0.tar.bz2"
                )
            )
        )
    )

    private val _states = MutableStateFlow<Map<String, PackState>>(emptyMap())
    val states: StateFlow<Map<String, PackState>> = _states.asStateFlow()

    /** Languages currently synthesizing/loading their engine (for UI spinners). */
    private val _busy = MutableStateFlow<Set<String>>(emptySet())
    val busy: StateFlow<Set<String>> = _busy.asStateFlow()

    /** Last per-language error message (engine/synthesis failures). */
    private val _lastError = MutableStateFlow<Map<String, String>>(emptyMap())
    val lastError: StateFlow<Map<String, String>> = _lastError.asStateFlow()

    // isInstalled() walks the pack directory (espeak-ng-data alone has thousands
    // of files) — cache the result and invalidate on download/delete.
    private val installedCache = ConcurrentHashMap<String, Boolean>()

    private val client by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /** sherpa engines, keyed "en" (kokoro) or "hi:<voice>" (piper). */
    private val sherpaEngines = ConcurrentHashMap<String, OfflineTts>()

    /** Direct MMS ONNX sessions, keyed by lang ("bn"). */
    private val ortSessions = ConcurrentHashMap<String, OrtSession>()

    /** bn vocab from the official facebook/mms-tts-ben repo (bundled asset). */
    @Volatile
    private var bnVocab: Map<Char, Int>? = null

    private val previewMutex = Mutex()
    private var previewPlayer: MediaPlayer? = null

    private val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + Dispatchers.Default
    )

    init {
        scope.launch { ensurePacksInstalled() }
        val restored = packs.filter { isInstalled(it.langCode) }
            .associate { it.langCode to PackState.Ready as PackState }
        if (restored.isNotEmpty()) _states.value = restored
    }

    // ---------------------------------------------------------------- paths

    fun packDir(langCode: String): File = File(appContext.filesDir, "tts/$langCode")

    /**
     * Builds that ship the models already unpacked put them here:
     * assets/tts/extracted/<lang>/…  (see scripts/prepare_builtin_assets.sh).
     * When that folder exists the phone only copies files — it never downloads
     * and never runs bzip2.
     */
    private val preExtractedRoot = "tts/extracted"

    private fun assetDirExists(assetPath: String): Boolean = try {
        (appContext.assets.list(assetPath) ?: emptyArray()).isNotEmpty()
    } catch (e: Exception) {
        false
    }

    /** Recursively copies an asset directory into [destDir] (files keep their paths). */
    private fun copyAssetTree(assetPath: String, destDir: File) {
        val children = try {
            appContext.assets.list(assetPath) ?: emptyArray()
        } catch (e: Exception) {
            emptyArray()
        }
        if (children.isEmpty()) {
            destDir.parentFile?.mkdirs()
            appContext.assets.open(assetPath).use { input ->
                java.io.BufferedOutputStream(destDir.outputStream(), 1 shl 20).use { out ->
                    input.copyTo(out, 1 shl 20)
                }
            }
            return
        }
        destDir.mkdirs()
        for (child in children) copyAssetTree("$assetPath/$child", File(destDir, child))
    }

    private fun findFile(dir: File, name: String): File? =
        dir.walkTopDown().firstOrNull { it.isFile && it.name.equals(name, ignoreCase = true) }

    private fun findFirst(dir: File, suffix: String): File? =
        dir.walkTopDown().firstOrNull { it.isFile && it.name.endsWith(suffix) }

    suspend fun ensurePacksInstalled() {
        for (pack in packs) {
            if (isInstalled(pack.langCode)) continue
            _states.value = _states.value + (pack.langCode to PackState.Downloading(0L, pack.totalBytes))
            try {
                withContext(Dispatchers.IO) {
                    val dir = packDir(pack.langCode)
                    if (pack.engine == "kokoro" && dir.exists()) {
                        dir.deleteRecursively()
                        sherpaEngines.keys.filter { it == pack.langCode || it.startsWith("${pack.langCode}:") }
                            .forEach { sherpaEngines.remove(it)?.release() }
                        ortSessions.remove(pack.langCode)?.close()
                    }
                    dir.mkdirs()
                    val preExtracted = "$preExtractedRoot/${pack.langCode}"
                    if (assetDirExists(preExtracted)) {
                        // Fully-offline build: the models are already unpacked in the APK.
                        Log.i(TAG, "Copying pre-extracted models for ${pack.langCode} from assets")
                        _states.value = _states.value + (pack.langCode to PackState.Extracting)
                        copyAssetTree(preExtracted, dir)
                    } else {
                        var done = 0L
                        for (dl in pack.downloads) {
                            val targetDir = if (dl.subDir.isBlank()) dir else File(dir, dl.subDir)
                            targetDir.mkdirs()
                            val isArchive = dl.assetPath.endsWith(".tar.bz2") || dl.url.endsWith(".tar.bz2")
                            if (dl.assetPath.isNotEmpty()) {
                                val archiveName = dl.assetPath.substringAfterLast('/')
                                val archiveDest = File(targetDir, archiveName)
                                copyAssetToFile(dl.assetPath, archiveDest)
                                done += dl.bytes
                                if (isArchive) {
                                    _states.value = _states.value + (pack.langCode to PackState.Extracting)
                                    extractTarBz2(archiveDest, targetDir)
                                    archiveDest.delete()
                                    flattenNestedArchiveDir(targetDir, ".onnx")
                                }
                            } else {
                                val fileName = if (isArchive) "pack_${dl.subDir}.tar.bz2" else dl.url.substringAfterLast('/')
                                downloadToFile(dl.url, File(targetDir, fileName), dl.bytes) { r, t ->
                                    _states.value = _states.value +
                                        (pack.langCode to PackState.Downloading(done + r, pack.totalBytes))
                                }
                                done += dl.bytes
                                if (isArchive) {
                                    _states.value = _states.value + (pack.langCode to PackState.Extracting)
                                    extractTarBz2(File(targetDir, fileName), targetDir)
                                    File(targetDir, fileName).delete()
                                    flattenNestedArchiveDir(targetDir, ".onnx")
                                }
                            }
                        }
                    }
                    if (!isInstalled(pack.langCode)) {
                        throw IllegalStateException("Pack files missing after extraction")
                    }
                }
                sherpaEngines.keys.filter { it == pack.langCode || it.startsWith("${pack.langCode}:") }
                    .forEach { sherpaEngines.remove(it)?.release() }
                ortSessions.remove(pack.langCode)?.close()
                installedCache.remove(pack.langCode)
                _states.value = _states.value + (pack.langCode to PackState.Ready)
                scope.launch {
                    _busy.value = _busy.value + pack.langCode
                    val test = File(appContext.cacheDir, "warmup_${pack.langCode}.wav")
                    val ok = synthesizeToFileBlocking(
                        "Hello.", "cast:young_male:${pack.langCode}", pack.langCode, 1f, 1f, test
                    )
                    test.delete()
                    _busy.value = _busy.value - pack.langCode
                    if (!ok) {
                        _lastError.value = _lastError.value +
                            (pack.langCode to "Engine warm-up failed — device may be low on memory")
                        _states.value = _states.value + (pack.langCode to PackState.Failed("engine warm-up failed"))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Pack install failed (${pack.langCode}): ${e.message}", e)
                _states.value = _states.value + (pack.langCode to PackState.Failed(e.message ?: "error"))
            }
        }
    }

    private suspend fun copyAssetToFile(assetPath: String, dest: File) = withContext(Dispatchers.IO) {
        appContext.assets.open(assetPath).use { input ->
            dest.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    fun isInstalled(langCode: String): Boolean {
        installedCache[langCode]?.let { return it }
        val v = isInstalledUncached(langCode)
        installedCache[langCode] = v
        return v
    }

    private fun isInstalledUncached(langCode: String): Boolean {
        val dir = packDir(langCode)
        return when (langCode) {
            "bn" -> findFile(dir, "model.onnx") != null
            "hi" -> listOf("pratham", "rohan", "priyamvada").all { voice ->
                val vDir = File(dir, voice)
                findFirst(vDir, ".onnx") != null && findFile(vDir, "tokens.txt") != null &&
                        findFirst(vDir, "espeak-ng-data")?.isDirectory == true
            }
            "en" -> findFile(dir, "model.int8.onnx") != null &&
                    findFile(dir, "voices.bin") != null &&
                    findFile(dir, "tokens.txt") != null &&
                    findFile(dir, "lexicon-us-en.txt") != null &&
                    findFirst(dir, "espeak-ng-data")?.isDirectory == true
            else -> false
        }
    }

    fun readyLangs(): Set<String> = packs.map { it.langCode }.filter { isReady(it) }.toSet()

    /**
     * The same set, as a flow. First launch unpacks ~294 MB of bundled models in
     * the background, so the UI must react when a pack becomes usable instead of
     * reading [readyLangs] once (that used to leave the voice picker empty).
     */
    val readyLangsFlow: StateFlow<Set<String>> = _states
        .map { packs.map { it.langCode }.filter { isReady(it) }.toSet() }
        .stateIn(scope, SharingStarted.Eagerly, readyLangs())

    fun isReady(langCode: String): Boolean =
        isInstalled(langCode) && _states.value[langCode] !is PackState.Failed

    /** Human-readable list of what is missing from an installed pack. */
    fun filesStatus(langCode: String): String {
        val dir = packDir(langCode)
        val missing = mutableListOf<String>()
        when (langCode) {
            "bn" -> if (findFile(dir, "model.onnx") == null) missing.add("model.onnx")
            "hi" -> for (v in listOf("pratham", "rohan", "priyamvada")) {
                val vd = File(dir, v)
                if (findFirst(vd, ".onnx") == null) missing.add("$v/*.onnx")
                if (findFile(vd, "tokens.txt") == null) missing.add("$v/tokens.txt")
                if (findFirst(vd, "espeak-ng-data")?.isDirectory != true) missing.add("$v/espeak-ng-data")
            }
            "en" -> {
                if (findFile(dir, "model.int8.onnx") == null) missing.add("model.int8.onnx")
                if (findFile(dir, "voices.bin") == null) missing.add("voices.bin")
                if (findFile(dir, "tokens.txt") == null) missing.add("tokens.txt")
                if (findFile(dir, "lexicon-us-en.txt") == null) missing.add("lexicon-us-en.txt (v1.0 pack)")
                if (findFirst(dir, "espeak-ng-data")?.isDirectory != true) missing.add("espeak-ng-data")
            }
        }
        return if (missing.isEmpty()) "files OK" else "missing: " + missing.joinToString(", ")
    }

    // ------------------------------------------------------------ resolution

    /** Parses "cast:roleId:lang" voice ids used across the app. */
    fun neuralRoleOf(voiceName: String?): Pair<String, String>? {
        if (voiceName.isNullOrBlank() || !voiceName.startsWith("cast:")) return null
        val body = voiceName.removePrefix("cast:")
        val parts = body.split(":", limit = 2)
        if (parts.size != 2) return null
        val roleId = parts[0]
        val lang = parts[1].lowercase().substringBefore("-")
        if (packs.none { it.langCode == lang }) return null
        return lang to roleId
    }

    /** True only when every given voice can be synthesized with installed packs. */
    fun canHandleAll(voices: List<String?>, fallbackLang: String?): Boolean {
        if (packs.none { isReady(it.langCode) }) return false
        val effective = voices.filter { !it.isNullOrBlank() }
        if (effective.isEmpty()) return false
        return effective.all { v ->
            neuralRoleOf(v)?.let { (lang, _) -> isReady(lang) }
                ?: (fallbackLang != null && isReady(fallbackLang) && v?.startsWith("cast:") == true)
        }
    }

    // ------------------------------------------------------------ synthesis

    suspend fun synthesizeToFile(
        text: String,
        voiceName: String?,
        fallbackLang: String?,
        speed: Float,
        pitch: Float,
        outFile: File
    ): Boolean = withContext(Dispatchers.Default) {
        synthesizeToFileBlocking(text, voiceName, fallbackLang, speed, pitch, outFile)
    }

    fun synthesizeToFileBlocking(
        text: String,
        voiceName: String?,
        fallbackLang: String?,
        speed: Float,
        pitch: Float,
        outFile: File
    ): Boolean {
        val parsed = neuralRoleOf(voiceName)
            ?: fallbackLang?.takeIf { isReady(it) }?.let { it to PersonaSpec.YOUNG_MALE }
            ?: return false
        val (lang, roleId) = parsed
        if (text.isBlank()) return false

        return try {
            when (packs.first { it.langCode == lang }.engine) {
                "kokoro" -> synthesizeSherpa(
                    "en", kokoroSidFor(roleId), text,
                    speed * kokoroSpeedFor(roleId), pitch * kokoroPitchFor(roleId),
                    outFile, fxFor(roleId), fxIntensityFor(roleId)
                )
                "piper" -> {
                    val voiceDir = piperVoiceFor(roleId)
                    synthesizeSherpa("hi:$voiceDir", 0, text, speed * speedForRole(roleId), pitch * pitchForRole(roleId), outFile)
                }
                "mms_direct" -> synthesizeMmsDirect(lang, text, speed * speedForRole(roleId), pitch * pitchForRole(roleId), outFile)
                else -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Neural synthesis failed ($lang/$roleId): ${e.message}", e)
            false
        }
    }

    private fun synthesizeSherpa(
        engineKey: String,
        sid: Int,
        text: String,
        speed: Float,
        pitchFactor: Float,
        outFile: File,
        fx: PitchTools.Fx = PitchTools.Fx.NONE,
        fxIntensity: Float = 1f
    ): Boolean {
        val engine = sherpaEngine(engineKey) ?: return false
        val genSpeed = speed.coerceIn(0.5f, 2.0f)
        val audio: GeneratedAudio = engine.generate(text.trim(), sid, genSpeed)
        var pcm = ShortArray(audio.samples.size) { (audio.samples[it] * 32767f).toInt().toShort() }
        val nativeRate = audio.sampleRate
        pcm = applyPersonaDsp(pcm, nativeRate, pitchFactor, 1f)
        if (pcm.isNotEmpty() && fx != PitchTools.Fx.NONE) {
            pcm = PitchTools.applyFx(pcm, TARGET_SAMPLE_RATE, fx, fxIntensity)
        }
        if (pcm.isEmpty()) return false
        WavIo.write16kMono(outFile, pcm)
        return true
    }

    /** Direct ONNX Runtime inference for the single-input transformers MMS export. */
    private fun synthesizeMmsDirect(
        lang: String,
        text: String,
        speedFactor: Float,
        pitchFactor: Float,
        outFile: File
    ): Boolean {
        val session = ortSession(lang) ?: return false
        val env = OrtEnvironment.getEnvironment()
        val ids = tokenizeMms(text, lang)
        if (ids.isEmpty()) return false

        // transformers VitsTokenizer with add_blank=true: intersperse pad (0)
        val blanked = LongArray(ids.size * 2 + 1) { i ->
            if (i % 2 == 1) ids[i / 2].toLong() else 0L
        }
        val tensor = OnnxTensor.createTensor(env, LongBuffer.wrap(blanked), longArrayOf(1, blanked.size.toLong()))
        val audio = try {
            session.run(mapOf("x" to tensor)).use { result ->
                when (val v = result[0].value) {
                    is Array<*> -> when {
                        v.isEmpty() -> FloatArray(0)
                        v[0] is FloatArray -> (v as Array<FloatArray>)[0]
                        v[0] is Array<*> -> ((v[0] as Array<*>)[0] as FloatArray)
                        else -> FloatArray(0)
                    }
                    is FloatArray -> v
                    else -> FloatArray(0)
                }
            }
        } finally {
            tensor.close()
        }
        if (audio.isEmpty()) return false

        var pcm = ShortArray(audio.size) { (audio[it] * 32767f).toInt().toShort() }
        // MMS-direct has no native speed input -> DSP time-stretch (pitch preserved)
        pcm = applyPersonaDsp(pcm, TARGET_SAMPLE_RATE, pitchFactor, speedFactor)
        if (pcm.isEmpty()) return false
        WavIo.write16kMono(outFile, pcm)
        return true
    }

    /**
     * Persona DSP, pitch-preserving:
     *  - pitchFactor ≠ 1: resample (raises pitch) then WSOLA stretch back to the
     *    original duration — voice gets higher/lower without chipmunk timing.
     *  - speedFactor: WSOLA duration change for engines without a native speed.
     */
    private fun applyPersonaDsp(
        pcm: ShortArray,
        sampleRate: Int,
        pitchFactor: Float,
        speedFactor: Float
    ): ShortArray {
        var out = pcm
        val pf = pitchFactor.coerceIn(0.55f, 1.75f)
        if (kotlin.math.abs(pf - 1f) > 0.03f) {
            out = PitchTools.pitchShift(out, sampleRate, pf)
        }
        val sf = 1f / speedFactor.coerceIn(0.5f, 2.0f)
        if (kotlin.math.abs(sf - 1f) > 0.03f && out.isNotEmpty()) {
            out = PitchTools.timeStretch(out, sf)
        }
        if (sampleRate != TARGET_SAMPLE_RATE) {
            out = PitchTools.resampleTo(out, sampleRate, TARGET_SAMPLE_RATE)
        }
        return out
    }

    private fun tokenizeMms(text: String, lang: String): IntArray {
        val vocab = vocabFor(lang) ?: return IntArray(0)
        val clean = text.replace(Regex("\\s+"), " ").trim()
        return clean.mapNotNull { vocab[it] }.toIntArray()
    }

    private fun vocabFor(lang: String): Map<Char, Int>? {
        if (lang != "bn") return null
        bnVocab?.let { return it }
        return try {
            appContext.assets.open("tts/bn_vocab.json").bufferedReader().use { reader ->
                val obj = JSONObject(reader.readText())
                val map = HashMap<Char, Int>(obj.length())
                for (key in obj.keys()) {
                    if (key.isNotEmpty()) map[key[0]] = obj.getInt(key)
                }
                bnVocab = map
                map
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load bn vocab: ${e.message}", e)
            null
        }
    }

    // ------------------------------------------------- persona → engine params

    // v2.4: wider gaps so single-voice models (Bangla MMS) still give six
    // clearly different characters. Verified in sandbox: pitch proxies and
    // durations separate strongly (elderly 4.0s vs young 2.6s vs child 1.8s).
    private fun speedForRole(roleId: String): Float = when (roleId) {
        PersonaSpec.ELDERLY_MALE, PersonaSpec.ELDERLY_FEMALE -> 0.84f
        PersonaSpec.GRANDPA, PersonaSpec.GRANDMA -> 0.84f
        PersonaSpec.CHILD_BOY, PersonaSpec.CHILD_GIRL -> 1.12f
        PersonaSpec.BABY_BOY, PersonaSpec.BABY_GIRL,
        PersonaSpec.TODDLER_BOY, PersonaSpec.TODDLER_GIRL -> 1.14f
        PersonaSpec.GIANT -> 0.78f
        PersonaSpec.CHIPMUNK -> 1.24f
        PersonaSpec.MONSTER, PersonaSpec.DEMON, PersonaSpec.VAMPIRE -> 0.88f
        else -> 0.98f
    }

    /** Speed shaping used for the Kokoro (English) cast. */
    private fun kokoroSpeedFor(roleId: String): Float = when (roleId) {
        PersonaSpec.ELDERLY_MALE, PersonaSpec.ELDERLY_FEMALE,
        PersonaSpec.GRANDPA, PersonaSpec.GRANDMA -> 0.88f
        PersonaSpec.BABY_BOY, PersonaSpec.BABY_GIRL,
        PersonaSpec.TODDLER_BOY, PersonaSpec.TODDLER_GIRL -> 1.10f
        PersonaSpec.CHILD_BOY, PersonaSpec.CHILD_GIRL -> 1.06f
        PersonaSpec.TEEN_BOY, PersonaSpec.TEEN_GIRL -> 1.03f
        PersonaSpec.MIDDLE_MALE, PersonaSpec.MIDDLE_FEMALE -> 0.97f
        PersonaSpec.NARRATOR -> 0.93f
        PersonaSpec.GIANT -> 0.80f
        PersonaSpec.MONSTER, PersonaSpec.DEMON, PersonaSpec.VAMPIRE -> 0.88f
        PersonaSpec.VILLAIN -> 0.86f
        PersonaSpec.ROBOT -> 0.96f
        PersonaSpec.GHOST, PersonaSpec.WHISPER, PersonaSpec.WITCH -> 0.90f
        PersonaSpec.CHIPMUNK -> 1.22f
        else -> 1f
    }

    private fun pitchForRole(roleId: String): Float = when (roleId) {
        PersonaSpec.ELDERLY_MALE -> 0.72f
        PersonaSpec.ELDERLY_FEMALE -> 0.85f
        PersonaSpec.YOUNG_MALE -> 0.95f
        PersonaSpec.YOUNG_FEMALE -> 1.10f
        PersonaSpec.CHILD_BOY -> 1.38f
        PersonaSpec.CHILD_GIRL -> 1.40f
        PersonaSpec.GRANDPA -> 0.74f
        PersonaSpec.GRANDMA -> 0.86f
        PersonaSpec.BABY_BOY -> 1.62f
        PersonaSpec.BABY_GIRL -> 1.65f
        PersonaSpec.TODDLER_BOY -> 1.50f
        PersonaSpec.TODDLER_GIRL -> 1.52f
        PersonaSpec.TEEN_BOY -> 1.12f
        PersonaSpec.TEEN_GIRL -> 1.10f
        PersonaSpec.MONSTER -> 0.62f
        PersonaSpec.DEMON -> 0.60f
        PersonaSpec.GIANT -> 0.58f
        PersonaSpec.VAMPIRE -> 0.72f
        PersonaSpec.VILLAIN -> 0.80f
        PersonaSpec.ROBOT -> 0.95f
        PersonaSpec.ALIEN -> 1.24f
        PersonaSpec.GHOST -> 0.95f
        PersonaSpec.CHIPMUNK -> 1.55f
        else -> 1f
    }

    /**
     * Kokoro v1.0 speaker ids (53 speakers, 28 of them English).
     * 0-10 af_*, 11-19 am_*, 20-23 bf_*, 24-27 bm_*.
     */
    private fun kokoroSidFor(roleId: String): Int = when (roleId) {
        PersonaSpec.ELDERLY_MALE -> 17      // am_onyx (deep male)
        PersonaSpec.ELDERLY_FEMALE -> 21    // bf_emma (mature British)
        PersonaSpec.YOUNG_MALE -> 16        // am_michael
        PersonaSpec.YOUNG_FEMALE -> 3       // af_heart — flagship young female
        PersonaSpec.CHILD_BOY -> 18         // am_puck (lively)
        PersonaSpec.CHILD_GIRL -> 23        // bf_lily (light)
        PersonaSpec.BABY_BOY -> 18          // am_puck + strong pitch lift
        PersonaSpec.BABY_GIRL -> 23         // bf_lily + strong pitch lift
        PersonaSpec.TODDLER_BOY -> 19       // am_santa (playful)
        PersonaSpec.TODDLER_GIRL -> 7       // af_nova (bright)
        PersonaSpec.TEEN_BOY -> 12          // am_echo (young)
        PersonaSpec.TEEN_GIRL -> 10         // af_sky
        PersonaSpec.ADULT_MALE -> 14        // am_fenrir (warm)
        PersonaSpec.ADULT_FEMALE -> 2       // af_bella
        PersonaSpec.MIDDLE_MALE -> 26       // bm_george (mature British male)
        PersonaSpec.MIDDLE_FEMALE -> 9      // af_sarah
        PersonaSpec.GRANDPA -> 27           // bm_lewis
        PersonaSpec.GRANDMA -> 20           // bf_alice
        PersonaSpec.NARRATOR -> 17          // am_onyx, slower + deeper
        PersonaSpec.HERO -> 14              // am_fenrir
        PersonaSpec.VILLAIN -> 26           // bm_george
        PersonaSpec.MONSTER -> 17           // am_onyx + growl
        PersonaSpec.DEMON -> 25             // bm_fable + growl
        PersonaSpec.GIANT -> 17             // am_onyx, very deep
        PersonaSpec.VAMPIRE -> 24           // bm_daniel
        PersonaSpec.ROBOT -> 12             // am_echo + metallic FX
        PersonaSpec.ALIEN -> 12             // am_echo + metallic FX
        PersonaSpec.GHOST -> 6              // af_nicole (airy)
        PersonaSpec.WHISPER -> 6            // af_nicole
        PersonaSpec.WITCH -> 22             // bf_isabella
        PersonaSpec.CHIPMUNK -> 10          // af_sky + big pitch lift
        else -> 16
    }

    /** Character timbre applied after pitch/speed shaping. */
    private fun fxFor(roleId: String): PitchTools.Fx = when (roleId) {
        PersonaSpec.MONSTER, PersonaSpec.DEMON, PersonaSpec.GIANT,
        PersonaSpec.VAMPIRE, PersonaSpec.WITCH, PersonaSpec.VILLAIN -> PitchTools.Fx.GROWL
        PersonaSpec.ROBOT, PersonaSpec.ALIEN -> PitchTools.Fx.METAL
        PersonaSpec.GHOST, PersonaSpec.WHISPER -> PitchTools.Fx.WHISPER
        else -> PitchTools.Fx.NONE
    }

    private fun fxIntensityFor(roleId: String): Float = when (roleId) {
        PersonaSpec.MONSTER, PersonaSpec.DEMON -> 1f
        PersonaSpec.GIANT -> 0.8f
        PersonaSpec.VAMPIRE -> 0.45f
        PersonaSpec.WITCH -> 0.3f
        PersonaSpec.VILLAIN -> 0.25f
        PersonaSpec.ROBOT -> 1f
        PersonaSpec.ALIEN -> 0.7f
        PersonaSpec.GHOST -> 1f
        PersonaSpec.WHISPER -> 0.6f
        else -> 1f
    }

    /** Gentle pitch shaping for Kokoro v1.0 — voices are already age/gender cast. */
    private fun kokoroPitchFor(roleId: String): Float = when (roleId) {
        PersonaSpec.ELDERLY_MALE -> 0.97f
        PersonaSpec.ELDERLY_FEMALE -> 0.93f
        PersonaSpec.YOUNG_MALE -> 1.00f
        PersonaSpec.YOUNG_FEMALE -> 1.02f
        PersonaSpec.CHILD_BOY -> 1.26f
        PersonaSpec.CHILD_GIRL -> 1.28f
        PersonaSpec.BABY_BOY -> 1.45f
        PersonaSpec.BABY_GIRL -> 1.48f
        PersonaSpec.TODDLER_BOY -> 1.34f
        PersonaSpec.TODDLER_GIRL -> 1.36f
        PersonaSpec.TEEN_BOY -> 1.12f
        PersonaSpec.TEEN_GIRL -> 1.09f
        PersonaSpec.ADULT_MALE -> 0.98f
        PersonaSpec.ADULT_FEMALE -> 1.02f
        PersonaSpec.MIDDLE_MALE -> 0.93f
        PersonaSpec.MIDDLE_FEMALE -> 0.97f
        PersonaSpec.GRANDPA -> 0.90f
        PersonaSpec.GRANDMA -> 0.92f
        PersonaSpec.NARRATOR -> 0.90f
        PersonaSpec.HERO -> 0.94f
        PersonaSpec.VILLAIN -> 0.80f
        PersonaSpec.MONSTER -> 0.62f
        PersonaSpec.DEMON -> 0.60f
        PersonaSpec.GIANT -> 0.58f
        PersonaSpec.VAMPIRE -> 0.72f
        PersonaSpec.ROBOT -> 0.95f
        PersonaSpec.ALIEN -> 1.24f
        PersonaSpec.GHOST -> 0.95f
        PersonaSpec.WHISPER -> 0.99f
        PersonaSpec.WITCH -> 0.86f
        PersonaSpec.CHIPMUNK -> 1.72f
        else -> 1f
    }

    /** Hindi Piper voice directories. */
    private fun piperVoiceFor(roleId: String): String = when (roleId) {
        PersonaSpec.ELDERLY_MALE -> "pratham"
        PersonaSpec.YOUNG_MALE, PersonaSpec.CHILD_BOY -> "rohan"
        else -> "priyamvada"
    }

    // -------------------------------------------------------------- preview

    /** Synthesizes and plays a persona sample. Returns null on success, or an error message. */
    suspend fun preview(langCode: String, roleId: String, speed: Float = 1f, pitch: Float = 1f): String? {
        if (!isInstalled(langCode)) {
            return "Pack not installed — download it first. (" + filesStatus(langCode) + ")"
        }
        previewMutex.withLock {
            stopPreviewLocked()
            _busy.value = _busy.value + langCode
            try {
                val sample = PersonaSpec.sampleText(langCode, roleId)
                val tmp = File(appContext.cacheDir, "neural_preview_${langCode}_${roleId}.wav")
                val voiceName = "cast:$roleId:$langCode"
                val ok = withContext(Dispatchers.Default) {
                    try {
                        synthesizeToFileBlocking(sample, voiceName, langCode, speed, pitch, tmp)
                    } catch (t: Throwable) {
                        Log.e(TAG, "preview synth failed", t)
                        false
                    }
                }
                if (!ok) {
                    val msg = _lastError.value[langCode]
                        ?: "Synthesis failed — free RAM/storage and retry. (" + filesStatus(langCode) + ")"
                    return msg
                }
                val player = MediaPlayer()
                previewPlayer = player
                try {
                    player.setDataSource(tmp.absolutePath)
                    player.setOnCompletionListener {
                        try { it.release() } catch (_: Exception) {}
                        if (previewPlayer === player) previewPlayer = null
                        tmp.delete()
                    }
                    player.prepare()
                    player.start()
                } catch (e: Exception) {
                    Log.e(TAG, "preview playback failed", e)
                    try { player.release() } catch (_: Exception) {}
                    previewPlayer = null
                    tmp.delete()
                    return "Playback failed: ${e.message}"
                }
                _lastError.value = _lastError.value - langCode
                return null
            } finally {
                _busy.value = _busy.value - langCode
            }
        }
    }

    fun stopPreview() {
        previewMutex.tryLock().let { locked ->
            if (locked) {
                try { stopPreviewLocked() } finally { previewMutex.unlock() }
            }
        }
    }

    /** UI hook to surface a preview error next to the pack card. */
    fun reportPreviewError(langCode: String, message: String) {
        _lastError.value = _lastError.value + (langCode to message)
    }

    /** Fire-and-forget preview used by UI and by TextToSpeechManager. */
    fun previewAsync(langCode: String, roleId: String, speed: Float = 1f, pitch: Float = 1f) {
        scope.launch { preview(langCode, roleId, speed, pitch) }
    }

    private fun stopPreviewLocked() {
        try { previewPlayer?.stop() } catch (_: Exception) {}
        try { previewPlayer?.release() } catch (_: Exception) {}
        previewPlayer = null
    }

    // -------------------------------------------------------------- download

    suspend fun downloadPack(pack: Pack) {
        _states.value = _states.value + (pack.langCode to PackState.Downloading(0, pack.totalBytes))
        try {
            withContext(Dispatchers.IO) {
                val dir = packDir(pack.langCode)
                // Kokoro generations share one flat directory; a stale older pack
                // (e.g. v0.19) would block the new files from replacing them
                // (rename-to-existing is skipped). Wipe so the new pack lands clean.
                if (pack.engine == "kokoro" && dir.exists()) {
                    dir.deleteRecursively()
                    sherpaEngines.keys.filter { it == pack.langCode || it.startsWith("${pack.langCode}:") }
                        .forEach { sherpaEngines.remove(it)?.release() }
                    ortSessions.remove(pack.langCode)?.close()
                }
                dir.mkdirs()
                val preExtracted = "$preExtractedRoot/${pack.langCode}"
                if (assetDirExists(preExtracted)) {
                    // Built-in build: nothing to download, just copy the unpacked tree.
                    _states.value = _states.value + (pack.langCode to PackState.Extracting)
                    copyAssetTree(preExtracted, dir)
                } else {
                    var done = 0L
                    for (dl in pack.downloads) {
                        val targetDir = if (dl.subDir.isBlank()) dir else File(dir, dl.subDir)
                        targetDir.mkdirs()
                        val isArchive = dl.url.endsWith(".tar.bz2")
                        val fileName = if (isArchive) "pack_${dl.subDir}.tar.bz2" else dl.url.substringAfterLast('/')
                        downloadToFile(dl.url, File(targetDir, fileName), dl.bytes) { r, t ->
                            _states.value = _states.value +
                                (pack.langCode to PackState.Downloading(done + r, pack.totalBytes))
                        }
                        done += dl.bytes
                        if (isArchive) {
                            _states.value = _states.value + (pack.langCode to PackState.Extracting)
                            extractTarBz2(File(targetDir, fileName), targetDir)
                            File(targetDir, fileName).delete()
                            flattenNestedArchiveDir(targetDir, ".onnx")
                        }
                    }
                }
                if (!isInstalled(pack.langCode)) {
                    throw IllegalStateException("Pack files missing after download/extract")
                }
            }
            // Drop cached engines so they reload with the new files.
            sherpaEngines.keys.filter { it == pack.langCode || it.startsWith("${pack.langCode}:") }
                .forEach { sherpaEngines.remove(it)?.release() }
            ortSessions.remove(pack.langCode)?.close()
            installedCache.remove(pack.langCode)
            _states.value = _states.value + (pack.langCode to PackState.Ready)
            // Warm the engine up right away and surface any init problem NOW.
            scope.launch {
                _busy.value = _busy.value + pack.langCode
                val test = File(appContext.cacheDir, "warmup_${pack.langCode}.wav")
                val ok = synthesizeToFileBlocking(
                    "Hello.", "cast:young_male:${pack.langCode}", pack.langCode, 1f, 1f, test
                )
                test.delete()
                _busy.value = _busy.value - pack.langCode
                if (!ok) {
                    _lastError.value = _lastError.value +
                        (pack.langCode to "Engine warm-up failed — device may be low on memory")
                    _states.value = _states.value + (pack.langCode to PackState.Failed("engine warm-up failed"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pack download failed (${pack.langCode}): ${e.message}", e)
            _states.value = _states.value + (pack.langCode to PackState.Failed(e.message ?: "error"))
        }
    }

    suspend fun deletePack(pack: Pack) {
        withContext(Dispatchers.IO) {
            sherpaEngines.keys.filter { it == pack.langCode || it.startsWith("${pack.langCode}:") }
                .forEach { sherpaEngines.remove(it)?.release() }
            ortSessions.remove(pack.langCode)?.close()
            packDir(pack.langCode).deleteRecursively()
            installedCache.remove(pack.langCode)
            _lastError.value = _lastError.value - pack.langCode
            _states.value = _states.value + (pack.langCode to PackState.NotDownloaded)
        }
    }

    private fun downloadToFile(
        url: String,
        dest: File,
        totalHint: Long,
        onProgress: (Long, Long) -> Unit
    ) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code} for $url")
            val body = response.body ?: throw IllegalStateException("Empty body for $url")
            val total = body.contentLength().takeIf { it > 0 } ?: totalHint
            val part = File(dest.absolutePath + ".part")
            var received = 0L
            body.byteStream().use { input ->
                FileOutputStream(part).use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        received += n
                        onProgress(received, total)
                    }
                }
            }
            if (total > 0 && received < total * 95L / 100L) {
                throw IllegalStateException("Incomplete download ($received/$total)")
            }
            if (dest.exists()) dest.delete()
            if (!part.renameTo(dest)) {
                part.copyTo(dest, overwrite = true)
                part.delete()
            }
        }
    }

    private fun extractTarBz2(archive: File, destDir: File) {
        TarArchiveInputStream(BZip2CompressorInputStream(FileInputStream(archive))).use { tar ->
            while (true) {
                val entry = tar.nextTarEntry ?: break
                val name = entry.name
                if (name.contains("..") || name.startsWith("/")) continue // path safety
                val outFile = File(destDir, name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { output -> tar.copyTo(output) }
                }
            }
        }
    }

    /** If a tarball nests everything in "name-medium-int8/", move files up. */
    private fun flattenNestedArchiveDir(dir: File, markerSuffix: String) {
        val nested = dir.listFiles { f -> f.isDirectory } ?: return
        for (n in nested) {
            if (n.walkTopDown().any { it.isFile && it.name.endsWith(markerSuffix) }) {
                n.listFiles()?.forEach { child ->
                    val target = File(dir, child.name)
                    if (!target.exists()) child.renameTo(target)
                }
                n.deleteRecursively()
                return
            }
        }
    }

    // --------------------------------------------------------------- engines

    private fun sherpaEngine(key: String): OfflineTts? {
        sherpaEngines[key]?.let { return it }
        return synchronized(this) {
            sherpaEngines[key]?.let { return it }
            try {
                run {
                    sherpaEngines[key]?.let { return it }
                    val lang = key.substringBefore(":")
                    val pack = packs.firstOrNull { it.langCode == lang } ?: return null
                    val dir = packDir(lang)
                    val modelDir = if (key.contains(":")) File(dir, key.substringAfter(":")) else dir
                    val modelFile = findFirst(modelDir, ".onnx") ?: return null
                    val tokensFile = findFile(modelDir, "tokens.txt") ?: return null
                    val espeakDir = findFirst(modelDir, "espeak-ng-data")
                    // Kokoro v1.0 multi-lang extras (absent in the old v0.19 pack —
                    // every lookup is filtered, so both pack generations keep working).
                    val lexicon = listOf("lexicon-us-en.txt", "lexicon-gb-en.txt", "lexicon-zh.txt")
                        .mapNotNull { findFile(modelDir, it) }
                        .joinToString(",") { it.absolutePath }
                    val ruleFsts = listOf("date-zh.fst", "number-zh.fst", "phone-zh.fst")
                        .mapNotNull { findFile(modelDir, it) }
                        .joinToString(",") { it.absolutePath }
                    val dictDir = File(modelDir, "dict").takeIf { it.isDirectory }?.absolutePath ?: ""
                    val modelCfg: OfflineTtsModelConfig = if (pack.engine == "kokoro") {
                        OfflineTtsModelConfig(
                            kokoro = OfflineTtsKokoroModelConfig(
                                model = modelFile.absolutePath,
                                voices = findFile(modelDir, "voices.bin")?.absolutePath ?: "",
                                tokens = tokensFile.absolutePath,
                                dataDir = espeakDir?.absolutePath ?: "",
                                lexicon = lexicon,
                                dictDir = dictDir
                            ),
                            numThreads = 2,
                            debug = false,
                            provider = "cpu"
                        )
                    } else {
                        OfflineTtsModelConfig(
                            vits = OfflineTtsVitsModelConfig(
                                model = modelFile.absolutePath,
                                tokens = tokensFile.absolutePath,
                                dataDir = espeakDir?.absolutePath ?: ""
                            ),
                            numThreads = 2,
                            debug = false,
                            provider = "cpu"
                        )
                    }
                    val tts = OfflineTts(
                        config = OfflineTtsConfig(
                            model = modelCfg,
                            ruleFsts = ruleFsts,
                            maxNumSentences = 1
                        )
                    )
                    sherpaEngines[key] = tts
                    tts
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to init sherpa engine '$key': ${e.message}", e)
                _lastError.value = _lastError.value +
                    (key.substringBefore(":") to "engine: ${e.message?.take(120)}")
                null
            }
        }
    }

    private fun ortSession(lang: String): OrtSession? {
        ortSessions[lang]?.let { return it }
        return synchronized(this) {
            ortSessions[lang]?.let { return it }
            try {
                val modelFile = findFile(packDir(lang), "model.onnx") ?: return null
                val opts = OrtSession.SessionOptions()
                opts.setIntraOpNumThreads(2)
                val session = OrtEnvironment.getEnvironment().createSession(modelFile.absolutePath, opts)
                ortSessions[lang] = session
                session
            } catch (e: Exception) {
                Log.e(TAG, "Failed to init ORT session for $lang: ${e.message}", e)
                _lastError.value = _lastError.value + (lang to "engine: ${e.message?.take(120)}")
                null
            }
        }
    }


    companion object {
        private const val TAG = "NeuralVoiceManager"
        const val TARGET_SAMPLE_RATE = 16000

        @Volatile
        private var INSTANCE: NeuralVoiceManager? = null

        fun get(context: Context): NeuralVoiceManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NeuralVoiceManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
