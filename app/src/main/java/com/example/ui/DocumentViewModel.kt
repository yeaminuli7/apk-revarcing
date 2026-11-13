package com.example.ui

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.service.DubbingForegroundService
import com.example.tts.SpeechState
import com.example.tts.TextToSpeechManager
import com.example.tts.FriendlyVoice
import com.example.tts.VoiceCast
import com.example.BuildConfig
import com.example.util.AudioVideoMerger
import com.example.util.DetectedLanguage
import com.example.util.LanguageDetector
import com.example.util.OfflineSubtitleTranslator
import com.example.util.LanguageOption
import com.example.util.SubtitleEntry
import com.example.util.SubtitleParser
import com.example.util.SubtitleTranslator
import com.example.tts.neural.NeuralVoiceManager
import com.example.util.ApiKeyStore
import com.example.util.VideoTranscriber
import com.example.data.AppDatabase
import com.example.data.DubCueState
import com.example.data.MediaTaskHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

data class BatchDubItem(
    val id: String = UUID.randomUUID().toString(),
    val videoUri: Uri? = null,
    val videoFile: File? = null,
    val videoName: String = "",
    val sourceDirectory: File? = null,
    val srtUri: Uri? = null,
    val srtName: String = "",
    val srtContent: String = "",
    val srtSubtitles: List<SubtitleEntry> = emptyList(),
    val detectedLanguage: DetectedLanguage? = null,
    val videoVolume: Float = 0.20f,
    val ttsVolume: Float = 2.0f,
    val itemSpeed: Float = 1.00f,
    val itemPitch: Float = 1.00f,
    val itemVoiceName: String? = null,
    val status: String = "Select video and subtitle files",
    val isProcessing: Boolean = false,
    val isTranslating: Boolean = false,
    val progressText: String = "",
    val dubbedVideoFile: File? = null
)

data class DubCue(
    val id: String = UUID.randomUUID().toString(),
    val index: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String,
    val voiceName: String? = null,
    val locked: Boolean = false,
    val voicePinned: Boolean = false
)

enum class TurboPerformanceProfile(
    val title: String,
    val description: String,
    val cpuTarget: String,
    val defaultThreads: Int,
    val bufferMb: Int
) {
    BEAST_MODE("Turbo Beast Mode", "Most parallel transcription and the largest safe mix cache", "High", 4, 512),
    ULTRA_PERFORMANCE("Ultra Performance", "Higher parallel transcription with a large mix cache", "Higher", 3, 256),
    BALANCED("Balanced Mode", "Steady speed with moderate memory use", "Medium", 2, 128),
    BATTERY_SAVER("Battery Saver", "Fewer parallel requests and a smaller cache", "Low", 1, 64)
}

class DocumentViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs: SharedPreferences = application.getSharedPreferences("balaspeak_studio_prefs", Context.MODE_PRIVATE)

    val ttsManager = TextToSpeechManager(application)

    // Hardware Telemetry & Turbo Optimization State
    val detectedCpuCores: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(4)
    val detectedMaxMemoryMb: Long = Runtime.getRuntime().maxMemory() / (1024 * 1024)

    // Theme Mode Setting ("system", "dark", "light")
    private val _themeMode = MutableStateFlow(prefs.getString("pref_theme_mode", "system") ?: "system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _performanceProfile = MutableStateFlow(
        try {
            TurboPerformanceProfile.valueOf(prefs.getString("pref_perf_profile", TurboPerformanceProfile.BEAST_MODE.name) ?: TurboPerformanceProfile.BEAST_MODE.name)
        } catch (e: Exception) {
            TurboPerformanceProfile.BEAST_MODE
        }
    )
    val performanceProfile: StateFlow<TurboPerformanceProfile> = _performanceProfile.asStateFlow()

    private val _workerThreads = MutableStateFlow(
        prefs.getInt("pref_worker_threads", minOf(4, maxOf(2, detectedCpuCores.coerceAtMost(4))))
    )
    val workerThreads: StateFlow<Int> = _workerThreads.asStateFlow()

    private val _ramBufferSizeMb = MutableStateFlow(
        prefs.getInt("pref_ram_buffer_mb", 256)
    )
    val ramBufferSizeMb: StateFlow<Int> = _ramBufferSizeMb.asStateFlow()

    private val _translationConcurrency = MutableStateFlow(
        prefs.getInt("pref_translation_concurrency", 8)
    )
    val translationConcurrency: StateFlow<Int> = _translationConcurrency.asStateFlow()

    private val _hardwareAcceleration = MutableStateFlow(
        prefs.getBoolean("pref_hardware_accel", true)
    )
    val hardwareAcceleration: StateFlow<Boolean> = _hardwareAcceleration.asStateFlow()

    private val _showHiddenFiles = MutableStateFlow(
        prefs.getBoolean("pref_show_hidden_files", false)
    )
    val showHiddenFiles: StateFlow<Boolean> = _showHiddenFiles.asStateFlow()

    private val _backgroundProcessingActive = MutableStateFlow(
        prefs.getBoolean("pref_background_active", false)
    )
    val backgroundProcessingActive: StateFlow<Boolean> = _backgroundProcessingActive.asStateFlow()
    val backgroundProcessing: StateFlow<Boolean> = _backgroundProcessingActive.asStateFlow()

    private val _customOutputDirPath = MutableStateFlow(
        prefs.getString("pref_custom_output_dir", "") ?: ""
    )
    val customOutputDirPath: StateFlow<String> = _customOutputDirPath.asStateFlow()
    val customOutputPath: StateFlow<String?> = _customOutputDirPath.map { if (it.isBlank()) null else it }.stateIn(viewModelScope, SharingStarted.Eagerly, if (_customOutputDirPath.value.isBlank()) null else _customOutputDirPath.value)

    val ramBufferSizeKb: StateFlow<Int> = _ramBufferSizeMb.map { it * 1024 }.stateIn(viewModelScope, SharingStarted.Eagerly, _ramBufferSizeMb.value * 1024)

    // Batch Dubbing Queue
    private val _batchItems = MutableStateFlow<List<BatchDubItem>>(listOf(BatchDubItem()))
    val batchItems: StateFlow<List<BatchDubItem>> = _batchItems.asStateFlow()

    private val _v2VideoFile = MutableStateFlow<File?>(null)
    val v2VideoFile: StateFlow<File?> = _v2VideoFile.asStateFlow()
    private val _v2VideoName = MutableStateFlow("")
    val v2VideoName: StateFlow<String> = _v2VideoName.asStateFlow()
    private val _v2SubtitleName = MutableStateFlow("")
    val v2SubtitleName: StateFlow<String> = _v2SubtitleName.asStateFlow()
    private val _v2Cues = MutableStateFlow<List<DubCue>>(emptyList())
    val v2Cues: StateFlow<List<DubCue>> = _v2Cues.asStateFlow()
    private val _v2DefaultVoice = MutableStateFlow<String?>(null)
    val v2DefaultVoice: StateFlow<String?> = _v2DefaultVoice.asStateFlow()
    private val _v2VideoVolume = MutableStateFlow(0.20f)
    val v2VideoVolume: StateFlow<Float> = _v2VideoVolume.asStateFlow()
    private val _v2TtsVolume = MutableStateFlow(2.0f)
    val v2TtsVolume: StateFlow<Float> = _v2TtsVolume.asStateFlow()
    private val _v2Rendering = MutableStateFlow(false)
    val v2Rendering: StateFlow<Boolean> = _v2Rendering.asStateFlow()
    private val _v2Progress = MutableStateFlow("")
    val v2Progress: StateFlow<String> = _v2Progress.asStateFlow()
    private val _v2ProgressCount = MutableStateFlow(0 to 0)
    val v2ProgressCount: StateFlow<Pair<Int, Int>> = _v2ProgressCount.asStateFlow()
    private val _v2OutputFile = MutableStateFlow<File?>(null)
    val v2OutputFile: StateFlow<File?> = _v2OutputFile.asStateFlow()
    private val _v2Status = MutableStateFlow("Load a video and a subtitle to review")
    val v2Status: StateFlow<String> = _v2Status.asStateFlow()
    private var v2RenderJob: kotlinx.coroutines.Job? = null

    // ---- Locked blocks & pinned voices (Dubber V2) — persist across restarts ----
    private val cueStateDao by lazy { AppDatabase.getDatabase(getApplication()).dubCueStateDao() }

    private fun v2ProjectKey(): String {
        val vf = _v2VideoFile.value
        return if (vf != null && vf.exists()) "v:${vf.name}:${vf.length()}"
        else "s:${_v2SubtitleName.value.ifBlank { "none" }}"
    }

    private fun cueStateKey(cue: DubCue): String =
        "${v2ProjectKey()}#${cue.index}"

    /** Saves (or clears) the persistent row for one cue, based on its flags. */
    private fun persistCueState(cue: DubCue) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (cue.locked || cue.voicePinned) {
                    cueStateDao.upsert(
                        DubCueState(
                            stateKey = cueStateKey(cue),
                            videoKey = v2ProjectKey(),
                            cueIndex = cue.index,
                            locked = cue.locked,
                            textOverride = if (cue.locked) cue.text else null,
                            voiceName = if (cue.voicePinned) cue.voiceName else null,
                            voicePinned = cue.voicePinned
                        )
                    )
                } else {
                    cueStateDao.deleteByKey(cueStateKey(cue))
                }
            } catch (e: Exception) {
                android.util.Log.w("BalaSpeakV2", "persist cue state failed: ${e.message}")
            }
        }
    }

    /** Re-applies locked texts and pinned voices to freshly loaded cues. */
    private fun restoreDubberV2States(cues: List<DubCue>): List<DubCue> {
        return try {
            val saved = cueStateDao.forVideo(v2ProjectKey()).associateBy { it.cueIndex }
            if (saved.isEmpty()) return cues
            var lockedCount = 0
            var pinnedCount = 0
            val restored = cues.map { cue ->
                val st = saved[cue.index] ?: return@map cue
                var out = cue
                if (st.locked) {
                    out = out.copy(locked = true, text = st.textOverride ?: cue.text)
                    lockedCount++
                }
                if (st.voicePinned) {
                    out = out.copy(voicePinned = true, voiceName = st.voiceName ?: out.voiceName)
                    pinnedCount++
                }
                out
            }
            if (lockedCount > 0 || pinnedCount > 0) {
                val parts = mutableListOf<String>()
                if (lockedCount > 0) parts.add("$lockedCount locked")
                if (pinnedCount > 0) parts.add("$pinnedCount pinned voices")
                viewModelScope.launch {
                    _toastMessage.emit("Restored ${parts.joinToString(" + ")} from last session")
                }
            }
            restored
        } catch (e: Exception) {
            android.util.Log.w("BalaSpeakV2", "restore cue states failed: ${e.message}")
            cues
        }
    }

    private val _isGlobalProcessing = MutableStateFlow(false)
    val isGlobalProcessing: StateFlow<Boolean> = _isGlobalProcessing.asStateFlow()

    private var globalProcessingJob: kotlinx.coroutines.Job? = null
    private val cancellableJobs = mutableListOf<kotlinx.coroutines.Job>()

    private fun trackJob(job: kotlinx.coroutines.Job): kotlinx.coroutines.Job {
        synchronized(cancellableJobs) { cancellableJobs.add(job) }
        job.invokeOnCompletion {
            synchronized(cancellableJobs) { cancellableJobs.remove(job) }
        }
        return job
    }

    private fun cancelTrackedJobs() {
        val jobs = synchronized(cancellableJobs) { cancellableJobs.toList() }
        jobs.forEach { it.cancel() }
    }

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage = _toastMessage.asSharedFlow()

    // Standalone Subtitle Translator State
    private val _standaloneSrtFileName = MutableStateFlow("")
    val standaloneSrtFileName: StateFlow<String> = _standaloneSrtFileName.asStateFlow()

    private val _standaloneOriginalSubtitles = MutableStateFlow<List<SubtitleEntry>>(emptyList())
    val standaloneOriginalSubtitles: StateFlow<List<SubtitleEntry>> = _standaloneOriginalSubtitles.asStateFlow()

    private val _standaloneTranslatedSubtitles = MutableStateFlow<List<SubtitleEntry>>(emptyList())
    val standaloneTranslatedSubtitles: StateFlow<List<SubtitleEntry>> = _standaloneTranslatedSubtitles.asStateFlow()

    private val _standaloneIsTranslating = MutableStateFlow(false)
    val standaloneIsTranslating: StateFlow<Boolean> = _standaloneIsTranslating.asStateFlow()

    private val _standaloneTranslateProgress = MutableStateFlow(Pair(0, 0))
    val standaloneTranslateProgress: StateFlow<Pair<Int, Int>> = _standaloneTranslateProgress.asStateFlow()

    private val _standaloneSourceLang = MutableStateFlow("auto")
    val standaloneSourceLang: StateFlow<String> = _standaloneSourceLang.asStateFlow()

    private val _standaloneTargetLang = MutableStateFlow(
        SubtitleTranslator.supportedLanguages.firstOrNull { it.code == "en" } ?: SubtitleTranslator.supportedLanguages.first()
    )
    val standaloneTargetLang: StateFlow<LanguageOption> = _standaloneTargetLang.asStateFlow()

    private val _offlineSourceLang = MutableStateFlow(
        SubtitleTranslator.supportedLanguages.first { it.code == "en" }
    )
    val offlineSourceLang: StateFlow<LanguageOption> = _offlineSourceLang.asStateFlow()

    private val _offlinePackStatus = MutableStateFlow("Language pack not checked yet")
    val offlinePackStatus: StateFlow<String> = _offlinePackStatus.asStateFlow()

    private val _isOfflinePackReady = MutableStateFlow(false)
    val isOfflinePackReady: StateFlow<Boolean> = _isOfflinePackReady.asStateFlow()

    private val _isDownloadingOfflinePack = MutableStateFlow(false)
    val isDownloadingOfflinePack: StateFlow<Boolean> = _isDownloadingOfflinePack.asStateFlow()

    // --- AI Video Transcription State ---
    private val _geminiApiKey = MutableStateFlow(
        prefs.getString("pref_gemini_api_key", "") ?: ""
    )
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    private val _transcribeVideoUri = MutableStateFlow<Uri?>(null)
    val transcribeVideoUri: StateFlow<Uri?> = _transcribeVideoUri.asStateFlow()

    private val _transcribeVideoFile = MutableStateFlow<File?>(null)
    val transcribeVideoFile: StateFlow<File?> = _transcribeVideoFile.asStateFlow()

    private val _transcribeVideoName = MutableStateFlow("")
    val transcribeVideoName: StateFlow<String> = _transcribeVideoName.asStateFlow()

    private val _transcribeVideoDurationMs = MutableStateFlow(0L)
    val transcribeVideoDurationMs: StateFlow<Long> = _transcribeVideoDurationMs.asStateFlow()

    private val _transcribeVideoSizeBytes = MutableStateFlow(0L)
    val transcribeVideoSizeBytes: StateFlow<Long> = _transcribeVideoSizeBytes.asStateFlow()

    private val _transcribeTargetLang = MutableStateFlow("Original")
    val transcribeTargetLang: StateFlow<String> = _transcribeTargetLang.asStateFlow()

    private val _isTranscribing = MutableStateFlow(false)
    val isTranscribing: StateFlow<Boolean> = _isTranscribing.asStateFlow()

    private val _transcribeStatusText = MutableStateFlow("Select audio or video to start transcribing")
    val transcribeStatusText: StateFlow<String> = _transcribeStatusText.asStateFlow()

    private val _transcribeProgress = MutableStateFlow(0f)
    val transcribeProgress: StateFlow<Float> = _transcribeProgress.asStateFlow()

    private val _transcribeCurrentChunk = MutableStateFlow(0)
    val transcribeCurrentChunk: StateFlow<Int> = _transcribeCurrentChunk.asStateFlow()

    private val _transcribeTotalChunks = MutableStateFlow(0)
    val transcribeTotalChunks: StateFlow<Int> = _transcribeTotalChunks.asStateFlow()

    private val _transcribePercent = MutableStateFlow(0)
    val transcribePercent: StateFlow<Int> = _transcribePercent.asStateFlow()

    private val _transcribePhase = MutableStateFlow("IDLE")
    val transcribePhase: StateFlow<String> = _transcribePhase.asStateFlow()

    private var transcriptionJob: kotlinx.coroutines.Job? = null

    private val _transcribedSrtContent = MutableStateFlow("")
    val transcribedSrtContent: StateFlow<String> = _transcribedSrtContent.asStateFlow()

    private val _transcribedSubtitles = MutableStateFlow<List<SubtitleEntry>>(emptyList())
    val transcribedSubtitles: StateFlow<List<SubtitleEntry>> = _transcribedSubtitles.asStateFlow()

    private val _savedSubtitleFilePath = MutableStateFlow<String?>(null)
    val savedSubtitleFilePath: StateFlow<String?> = _savedSubtitleFilePath.asStateFlow()

    // Room Database & Persistent History for Tasks and Crash/Close Recovery
    private val database = AppDatabase.getDatabase(application)
    val mediaTaskHistoryDao = database.mediaTaskHistoryDao()

    val mediaHistoryTasks: StateFlow<List<MediaTaskHistory>> = mediaTaskHistoryDao.getAllHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentActiveHistoryTaskId = MutableStateFlow<Long?>(null)
    val currentActiveHistoryTaskId: StateFlow<Long?> = _currentActiveHistoryTaskId.asStateFlow()

    private val _restoredSessionBanner = MutableStateFlow<MediaTaskHistory?>(null)
    val restoredSessionBanner: StateFlow<MediaTaskHistory?> = _restoredSessionBanner.asStateFlow()

    val speechState: StateFlow<SpeechState> = ttsManager.speechState
    // Live list — repopulates by itself when the bundled/ downloaded packs
    // finish installing, instead of freezing whatever existed at startup.
    val friendlyVoicesList: StateFlow<List<FriendlyVoice>> = ttsManager.voicesFlow

    val previewingVoiceId: StateFlow<String?> = ttsManager.previewingVoiceId

    val globalSpeed: StateFlow<Float> = ttsManager.speed
    val globalPitch: StateFlow<Float> = ttsManager.pitch
    val globalSelectedVoice: StateFlow<String?> = ttsManager.selectedVoice
    val autoFitDuration: StateFlow<Boolean> = ttsManager.autoFitSubtitleDuration

    init {
        com.example.service.StopProcessingReceiver.onStopProcessing = {
            stopAllProcessing()
        }
        // Restore persistent speech settings (Default 1.0f speed + locked Auto-Fit for subtitle synchrony)
        val savedPitch = prefs.getFloat("pref_global_pitch", 1.0f)
        val savedVoice = prefs.getString("pref_global_voice", null)
        ttsManager.setSpeed(1.0f)
        ttsManager.setPitch(savedPitch)
        ttsManager.setAutoFitSubtitleDuration(true)
        if (savedVoice != null) {
            ttsManager.setVoice(savedVoice)
        }
        try {
            ApiKeyStore.editableFile(getApplication())
        } catch (e: Exception) {
            Log.w("DocumentViewModel", "Could not create rec/api.xml: ${e.message}")
        }

        // Auto-recover last active session (if user exited app or it closed unexpectedly)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val lastId = prefs.getLong("pref_last_active_task_id", -1L)
                val task = if (lastId != -1L) {
                    mediaTaskHistoryDao.getTaskById(lastId)
                } else {
                    mediaTaskHistoryDao.getLatestTask()
                }

                if (task != null && (task.srtContent.isNotBlank() || !task.filePath.isNullOrBlank() || !task.fileUri.isNullOrBlank())) {
                    restoreSessionFromHistory(task, auto = true)
                }
            } catch (e: Exception) {
                Log.w("DocumentViewModel", "Could not auto-recover task: ${e.message}")
            }
        }
    }

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        prefs.edit().putString("pref_theme_mode", mode).apply()
    }

    fun setPerformanceProfile(profile: TurboPerformanceProfile) {
        _performanceProfile.value = profile
        _workerThreads.value = profile.defaultThreads
        _ramBufferSizeMb.value = profile.bufferMb
        prefs.edit()
            .putString("pref_perf_profile", profile.name)
            .putInt("pref_worker_threads", profile.defaultThreads)
            .putInt("pref_ram_buffer_mb", profile.bufferMb)
            .apply()
    }

    fun setWorkerThreads(threads: Int) {
        val bounded = threads.coerceIn(1, 16)
        _workerThreads.value = bounded
        prefs.edit().putInt("pref_worker_threads", bounded).apply()
    }

    fun setRamBufferSizeMb(mb: Int) {
        _ramBufferSizeMb.value = mb
        prefs.edit().putInt("pref_ram_buffer_mb", mb).apply()
    }

    fun setTranslationConcurrency(concurrency: Int) {
        val bounded = concurrency.coerceIn(1, 16)
        _translationConcurrency.value = bounded
        prefs.edit().putInt("pref_translation_concurrency", bounded).apply()
    }

    fun setHardwareAcceleration(enabled: Boolean) {
        _hardwareAcceleration.value = enabled
        prefs.edit().putBoolean("pref_hardware_accel", enabled).apply()
    }

    fun setRamBufferSizeKb(kb: Int) {
        setRamBufferSizeMb(maxOf(16, kb / 1024))
    }

    fun setBackgroundProcessing(enabled: Boolean) = setBackgroundProcessingActive(enabled)

    fun setShowHiddenFiles(enabled: Boolean) {
        _showHiddenFiles.value = enabled
        prefs.edit().putBoolean("pref_show_hidden_files", enabled).apply()
    }

    fun setBackgroundProcessingActive(enabled: Boolean) {
        _backgroundProcessingActive.value = enabled
        prefs.edit().putBoolean("pref_background_active", enabled).apply()
        if (enabled) {
            DubbingForegroundService.startService(
                getApplication(),
                "BalaSpeak Studio Active",
                "Turbo background engine running..."
            )
        } else {
            DubbingForegroundService.stopService(getApplication())
        }
    }

    private fun fileProviderAuthority(): String {
        return "${getApplication<Application>().packageName}.provider"
    }

    private fun mixIoBufferBytes(): Int {
        // The slider is a preset, not a literal allocation. A 512 MB buffer would OOM.
        val mb = _ramBufferSizeMb.value.coerceIn(16, 512)
        return (mb * 4 * 1024).coerceIn(64 * 1024, 1024 * 1024)
    }

    private fun isAnyWorkRunning(): Boolean {
        return _isGlobalProcessing.value ||
            _v2Rendering.value ||
            _isTranscribing.value ||
            _standaloneIsTranslating.value ||
            _batchItems.value.any { it.isProcessing || it.isTranslating }
    }

    private fun startBackgroundEngine(title: String, status: String) {
        if (!_backgroundProcessingActive.value) return
        DubbingForegroundService.startService(getApplication(), title, status)
    }

    private fun updateBackgroundEngine(status: String, progress: Int, max: Int) {
        if (!_backgroundProcessingActive.value) return
        DubbingForegroundService.updateProgress(getApplication(), status, progress, max)
    }

    private fun finishBackgroundEngine() {
        if (isAnyWorkRunning()) return
        if (_backgroundProcessingActive.value) {
            DubbingForegroundService.startService(
                getApplication(),
                "BalaSpeak Studio",
                "Background engine is on. Waiting for the next task."
            )
        } else {
            DubbingForegroundService.stopService(getApplication())
        }
    }

    private fun deleteIfAppOwnedCache(file: File?) {
        if (file == null || !file.exists()) return
        try {
            val path = file.canonicalPath
            val roots = listOfNotNull(
                getApplication<Application>().cacheDir.canonicalPath,
                getApplication<Application>().externalCacheDir?.canonicalPath
            )
            val owned = roots.any { root -> path == root || path.startsWith(root + File.separator) }
            if (owned) file.delete()
        } catch (e: Exception) {
            Log.w("DocumentViewModel", "Skipped cache cleanup: ${e.message}")
        }
    }

    fun setCustomOutputPath(path: String?) {
        setCustomOutputDirPath(path ?: "")
    }

    fun setCustomOutputDirPath(path: String) {
        _customOutputDirPath.value = path
        prefs.edit().putString("pref_custom_output_dir", path).apply()
    }

    fun setGlobalSpeed(value: Float) {
        ttsManager.setSpeed(value)
        prefs.edit().putFloat("pref_global_speed", value).apply()
    }

    fun setGlobalPitch(value: Float) {
        ttsManager.setPitch(value)
        prefs.edit().putFloat("pref_global_pitch", value).apply()
    }

    fun setAutoFitDuration(enabled: Boolean) {
        ttsManager.setAutoFitSubtitleDuration(enabled)
        prefs.edit().putBoolean("pref_auto_fit", enabled).apply()
    }

    fun selectGlobalVoice(voiceName: String) {
        ttsManager.setVoice(voiceName)
        prefs.edit().putString("pref_global_voice", voiceName).apply()
    }

    // Voice Audition / Preview Methods
    fun previewVoice(voice: FriendlyVoice?) {
        ttsManager.previewVoice(voice, _batchItems.value.firstOrNull()?.itemSpeed ?: globalSpeed.value, _batchItems.value.firstOrNull()?.itemPitch ?: globalPitch.value)
    }

    fun previewVoice(voiceId: String, locale: java.util.Locale? = null) {
        val friendly = friendlyVoicesList.value.firstOrNull { it.id == voiceId }
        if (friendly != null) {
            previewVoice(friendly)
        } else {
            ttsManager.previewLanguageVoice(locale?.language ?: "en", globalSpeed.value, globalPitch.value)
        }
    }

    fun previewLanguageVoice(langCode: String) {
        ttsManager.previewLanguageVoice(langCode, globalSpeed.value, globalPitch.value)
    }

    fun stopVoicePreview() {
        ttsManager.stopVoicePreview()
    }

    fun addBatchItem() {
        val currentList = _batchItems.value.toMutableList()
        currentList.add(BatchDubItem())
        _batchItems.value = currentList
    }

    fun removeBatchItem(itemId: String) {
        val removed = _batchItems.value.firstOrNull { it.id == itemId }
        deleteIfAppOwnedCache(removed?.videoFile)
        val currentList = _batchItems.value.filterNot { it.id == itemId }
        _batchItems.value = if (currentList.isEmpty()) listOf(BatchDubItem()) else currentList
    }

    private fun updateBatchItem(itemId: String, transform: (BatchDubItem) -> BatchDubItem) {
        _batchItems.value = _batchItems.value.map { item ->
            if (item.id == itemId) transform(item) else item
        }
    }

    fun updateVideoVolume(itemId: String, volume: Float) {
        updateBatchItem(itemId) { it.copy(videoVolume = volume.coerceIn(0f, 1f)) }
    }

    fun updateTtsVolume(itemId: String, volume: Float) {
        updateBatchItem(itemId) { it.copy(ttsVolume = volume.coerceIn(0f, 2f)) }
    }

    fun updateItemSpeed(itemId: String, speedVal: Float) {
        updateBatchItem(itemId) { it.copy(itemSpeed = speedVal.coerceIn(0.5f, 2.5f)) }
    }

    fun updateItemPitch(itemId: String, pitchVal: Float) {
        updateBatchItem(itemId) { it.copy(itemPitch = pitchVal.coerceIn(0.5f, 2.0f)) }
    }

    fun updateItemVoice(itemId: String, voiceName: String?) {
        updateBatchItem(itemId) { it.copy(itemVoiceName = voiceName) }
    }

    fun getFileNameFromUri(uri: Uri): String {
        var name = ""
        try {
            val cursor = getApplication<Application>().contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIdx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1) {
                        name = it.getString(nameIdx)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("BalaSpeakTTS", "Error getting file name: ${e.message}")
        }
        if (name.isEmpty()) {
            name = uri.lastPathSegment ?: "unknown_video.mp4"
        }
        return name
    }

    private fun resolveRealFileFromUri(uri: Uri): File? {
        try {
            if (uri.scheme == "file") {
                val path = uri.path
                if (!path.isNullOrBlank()) {
                    val f = File(path)
                    if (f.exists()) return f
                }
            }
            if (uri.scheme == "content") {
                val proj = arrayOf(MediaStore.MediaColumns.DATA)
                getApplication<Application>().contentResolver.query(uri, proj, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                        if (idx != -1) {
                            val path = cursor.getString(idx)
                            if (!path.isNullOrBlank()) {
                                val f = File(path)
                                if (f.exists()) return f
                            }
                        }
                    }
                }

                val docId = try {
                    if (DocumentsContract.isDocumentUri(getApplication(), uri)) {
                        DocumentsContract.getDocumentId(uri)
                    } else null
                } catch (e: Exception) { null }

                if (docId != null) {
                    if (docId.startsWith("raw:")) {
                        val f = File(docId.removePrefix("raw:"))
                        if (f.exists()) return f
                    } else if (docId.startsWith("primary:")) {
                        val relPath = docId.removePrefix("primary:")
                        val f = File(Environment.getExternalStorageDirectory(), relPath)
                        if (f.exists()) return f
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("BalaSpeakTTS", "Could not resolve real file from uri: ${e.message}")
        }
        return null
    }

    fun loadVideoForBatchItem(itemId: String, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                updateBatchItem(itemId) {
                    it.copy(
                        status = "Loading video file...",
                        progressText = "Reading video data..."
                    )
                }

                val name = getFileNameFromUri(uri)
                val cleanName = if (name.contains("/")) name.substringAfterLast("/") else name

                // Try to resolve real file path directly to preserve original source directory
                val realFile = resolveRealFileFromUri(uri)
                val sourceDir = realFile?.parentFile

                val videoCacheFile = if (realFile != null && realFile.exists() && realFile.canRead()) {
                    realFile
                } else {
                    val temp = File(getApplication<Application>().cacheDir, "source_${itemId}_${System.currentTimeMillis()}.mp4")
                    val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val outputStream = FileOutputStream(temp)
                        inputStream.copyTo(outputStream)
                        inputStream.close()
                        outputStream.close()
                        temp
                    } else null
                }

                if (videoCacheFile != null && videoCacheFile.exists() && videoCacheFile.length() > 0) {
                    val previous = _batchItems.value.firstOrNull { it.id == itemId }?.videoFile
                    if (previous != null && previous.absolutePath != videoCacheFile.absolutePath) {
                        deleteIfAppOwnedCache(previous)
                    }
                    updateBatchItem(itemId) { item ->
                        val isReady = item.srtSubtitles.isNotEmpty()
                        item.copy(
                            videoUri = uri,
                            videoFile = videoCacheFile,
                            videoName = cleanName,
                            sourceDirectory = sourceDir ?: if (videoCacheFile != realFile) null else videoCacheFile.parentFile,
                            status = if (isReady) "Ready for Dubbing" else "Add Subtitle (SRT) File",
                            progressText = ""
                        )
                    }
                } else {
                    updateBatchItem(itemId) { it.copy(status = "Failed to load video", progressText = "") }
                    _toastMessage.emit("Cannot open video stream!")
                }
            } catch (e: Exception) {
                updateBatchItem(itemId) { it.copy(status = "Video error: ${e.message}", progressText = "") }
                _toastMessage.emit("Video load error: ${e.message}")
            }
        }
    }

    fun loadVideoFileDirect(itemId: String, file: File) {
        if (!file.exists() || !file.canRead()) {
            viewModelScope.launch { _toastMessage.emit("Selected video file cannot be read") }
            return
        }
        updateBatchItem(itemId) { item ->
            val isReady = item.srtSubtitles.isNotEmpty()
            item.copy(
                videoFile = file,
                videoName = file.name,
                sourceDirectory = file.parentFile,
                videoUri = Uri.fromFile(file),
                status = if (isReady) "Ready for Dubbing" else "Add Subtitle (SRT) File",
                progressText = ""
            )
        }
    }

    fun loadMultipleVideosForDubbing(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val newItems = mutableListOf<BatchDubItem>()
            for (uri in uris) {
                try {
                    val name = getFileNameFromUri(uri)
                    val cleanName = if (name.contains("/")) name.substringAfterLast("/") else name
                    val realFile = resolveRealFileFromUri(uri)
                    val sourceDir = realFile?.parentFile

                    val videoCacheFile = if (realFile != null && realFile.exists() && realFile.canRead()) {
                        realFile
                    } else {
                        val tempId = UUID.randomUUID().toString().take(8)
                        val temp = File(getApplication<Application>().cacheDir, "source_batch_${tempId}_${System.currentTimeMillis()}.mp4")
                        getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(temp).use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (temp.exists() && temp.length() > 0) temp else null
                    }

                    val item = BatchDubItem(
                        videoUri = uri,
                        videoFile = videoCacheFile,
                        videoName = cleanName,
                        sourceDirectory = sourceDir ?: videoCacheFile?.parentFile,
                        status = "Add Subtitle (SRT) File"
                    )
                    newItems.add(item)
                } catch (e: Exception) {
                    Log.e("DocumentViewModel", "Failed to load video uri: ${e.message}")
                }
            }

            if (newItems.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    val current = _batchItems.value.toMutableList()
                    if (current.size == 1 && current[0].videoFile == null && current[0].srtSubtitles.isEmpty()) {
                        _batchItems.value = newItems
                    } else {
                        current.addAll(newItems)
                        _batchItems.value = current
                    }
                    _toastMessage.emit("${newItems.size} video(s) added to dubbing list!")
                }
            }
        }
    }

    fun loadMultipleVideoFilesDirect(files: List<File>) {
        if (files.isEmpty()) return
        val validFiles = files.filter { it.exists() && it.canRead() }
        if (validFiles.isEmpty()) return

        val newItems = validFiles.map { file ->
            BatchDubItem(
                videoFile = file,
                videoName = file.name,
                sourceDirectory = file.parentFile,
                videoUri = Uri.fromFile(file),
                status = "Add Subtitle (SRT) File"
            )
        }
        val current = _batchItems.value.toMutableList()
        if (current.size == 1 && current[0].videoFile == null && current[0].srtSubtitles.isEmpty()) {
            _batchItems.value = newItems
        } else {
            current.addAll(newItems)
            _batchItems.value = current
        }
        viewModelScope.launch {
            _toastMessage.emit("${newItems.size} video(s) added to dubbing list!")
        }
    }

    fun loadSrtForBatchItem(itemId: String, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                updateBatchItem(itemId) { it.copy(status = "Parsing subtitle file...") }

                val name = getFileNameFromUri(uri)
                val cleanName = if (name.contains("/")) name.substringAfterLast("/") else name

                val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
                val content = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                val entries = SubtitleParser.parseSrtOrVtt(content)

                if (entries.isEmpty()) {
                    updateBatchItem(itemId) { it.copy(status = "Empty or invalid subtitle format") }
                    _toastMessage.emit("No valid subtitle timestamps found!")
                    return@launch
                }

                // Automatic language detection from parsed subtitle text
                val sampleText = entries.take(15).joinToString(" ") { it.text }
                val detected = LanguageDetector.detectLanguage(sampleText)
                val autoVoiceName = VoiceCast.defaultSpeakerId(detected.code)

                updateBatchItem(itemId) { item ->
                    val isReady = item.videoFile != null && item.videoFile.exists()
                    item.copy(
                        srtUri = uri,
                        srtName = cleanName,
                        srtContent = content,
                        srtSubtitles = entries,
                        detectedLanguage = detected,
                        itemVoiceName = autoVoiceName ?: item.itemVoiceName,
                        status = if (isReady) "Ready for Dubbing • ${detected.name} (${detected.nativeName})" else "Add Video (MP4) File • ${detected.name} (${detected.nativeName})",
                        progressText = ""
                    )
                }
                _toastMessage.emit("Detected: ${detected.name} (${detected.nativeName}) - Voice updated!")
            } catch (e: Exception) {
                updateBatchItem(itemId) { it.copy(status = "Subtitle error: ${e.message}") }
                _toastMessage.emit("Subtitle read error: ${e.message}")
            }
        }
    }

    fun loadSubtitleFileDirect(itemId: String, file: File) {
        if (!file.exists() || !file.canRead()) {
            viewModelScope.launch { _toastMessage.emit("Selected subtitle file cannot be read") }
            return
        }
        try {
            val content = file.readText(Charsets.UTF_8)
            val entries = SubtitleParser.parseSrtOrVtt(content)
            if (entries.isEmpty()) {
                viewModelScope.launch { _toastMessage.emit("No valid subtitle entries found") }
                return
            }

            // Automatic language detection from parsed subtitle text
            val sampleText = entries.take(15).joinToString(" ") { it.text }
            val detected = LanguageDetector.detectLanguage(sampleText)
            val autoVoiceName = VoiceCast.defaultSpeakerId(detected.code)

            updateBatchItem(itemId) { item ->
                val isReady = item.videoFile != null && item.videoFile.exists()
                item.copy(
                    srtUri = Uri.fromFile(file),
                    srtName = file.name,
                    srtContent = content,
                    srtSubtitles = entries,
                    detectedLanguage = detected,
                    itemVoiceName = autoVoiceName ?: item.itemVoiceName,
                    status = if (isReady) "Ready for Dubbing • ${detected.name} (${detected.nativeName})" else "Add Video (MP4) File • ${detected.name} (${detected.nativeName})",
                    progressText = ""
                )
            }
            viewModelScope.launch {
                _toastMessage.emit("Detected: ${detected.name} (${detected.nativeName}) - Voice updated!")
            }
        } catch (e: Exception) {
            viewModelScope.launch { _toastMessage.emit("Failed to read subtitle: ${e.message}") }
        }
    }

    /**
     * Online Subtitle Translation: Translates all lines while maintaining exact time codes.
     */
    fun translateSubtitlesForBatchItem(itemId: String, targetLangCode: String, targetLangName: String) {
        trackJob(viewModelScope.launch(Dispatchers.IO) {
            val item = _batchItems.value.firstOrNull { it.id == itemId } ?: return@launch
            if (item.srtSubtitles.isEmpty()) {
                _toastMessage.emit("No subtitles loaded to translate!")
                return@launch
            }

            startBackgroundEngine("BalaSpeak Translation", "Translating subtitles to $targetLangName...")
            updateBatchItem(itemId) {
                it.copy(
                    isTranslating = true,
                    status = "Translating to $targetLangName...",
                    progressText = "Translating subtitles online..."
                )
            }

            try {
                val translated = SubtitleTranslator.translateSubtitles(
                    subtitles = item.srtSubtitles,
                    targetLang = targetLangCode,
                    sourceLang = "auto",
                    concurrencyLimit = _translationConcurrency.value
                ) { count, total ->
                    updateBatchItem(itemId) {
                        it.copy(progressText = "Translating: $count / $total lines")
                    }
                    updateBackgroundEngine("Translating to $targetLangName: $count/$total", count, total)
                }

                val newSrtContent = SubtitleTranslator.exportToSrt(translated)
                val cleanName = if (item.srtName.isNotEmpty()) {
                    "${item.srtName.substringBeforeLast(".")}_$targetLangCode.srt"
                } else "subtitles_$targetLangCode.srt"

                val targetLangOpt = SubtitleTranslator.supportedLanguages.firstOrNull { it.code.equals(targetLangCode, ignoreCase = true) }
                val targetDetected = DetectedLanguage(
                    code = targetLangCode,
                    name = targetLangName,
                    nativeName = targetLangOpt?.nativeName ?: targetLangName,
                    confidence = 1.0f,
                    scriptName = targetLangName
                )
                updateBatchItem(itemId) {
                    it.copy(
                        isTranslating = false,
                        srtSubtitles = translated,
                        srtContent = newSrtContent,
                        srtName = cleanName,
                        detectedLanguage = targetDetected,
                        itemVoiceName = VoiceCast.defaultSpeakerId(targetLangCode),
                        status = "Translated to $targetLangName! Ready to Dub",
                        progressText = ""
                    )
                }
                _toastMessage.emit("Translated to $targetLangName & voice matched!")
            } catch (e: kotlinx.coroutines.CancellationException) {
                updateBatchItem(itemId) {
                    it.copy(isTranslating = false, status = "Stopped", progressText = "")
                }
                throw e
            } catch (e: Exception) {
                Log.e("BalaSpeakTTS", "Translation failed: ${e.message}", e)
                updateBatchItem(itemId) {
                    it.copy(
                        isTranslating = false,
                        status = "Translation failed: ${e.localizedMessage}",
                        progressText = ""
                    )
                }
                _toastMessage.emit("Translation error: ${e.message}")
            } finally {
                updateBatchItem(itemId) { current ->
                    if (current.isTranslating) current.copy(isTranslating = false) else current
                }
                finishBackgroundEngine()
            }
        })
    }

    fun resetBatchItem(itemId: String) {
        updateBatchItem(itemId) { old ->
            // Only a cache copy may be deleted. The original video and the saved dub stay on disk.
            deleteIfAppOwnedCache(old.videoFile)
            BatchDubItem(id = old.id)
        }
    }

    fun shareDubbedVideoForBatchItem(itemId: String) {
        val item = _batchItems.value.firstOrNull { it.id == itemId } ?: return
        val file = item.dubbedVideoFile ?: return

        try {
            val uri: Uri = FileProvider.getUriForFile(getApplication(), fileProviderAuthority(), file)
            val mime = when (file.extension.lowercase()) {
                "m4a", "aac" -> "audio/mp4"
                "mp3" -> "audio/mpeg"
                "wav" -> "audio/wav"
                "mkv" -> "video/x-matroska"
                "webm" -> "video/webm"
                else -> "video/mp4"
            }

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, "Share dubbed video...")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            getApplication<Application>().startActivity(chooser)
        } catch (e: Exception) {
            viewModelScope.launch {
                _toastMessage.emit("Share error: ${e.message}")
            }
        }
    }

    fun attachCreatedAudioForBatchItem(itemId: String, uri: Uri) {
        if (_isGlobalProcessing.value || _v2Rendering.value) {
            viewModelScope.launch { _toastMessage.emit("Wait until the current job finishes") }
            return
        }
        val item = _batchItems.value.firstOrNull { it.id == itemId } ?: return
        val videoFile = item.videoFile
        if (videoFile == null || !videoFile.exists()) {
            viewModelScope.launch { _toastMessage.emit("Load a video first") }
            return
        }
        _isGlobalProcessing.value = true
        trackJob(viewModelScope.launch(Dispatchers.IO) {
            startBackgroundEngine("BalaSpeak Dubbing", "Adding audio to video...")
            val tempMux = File(getApplication<Application>().cacheDir, "temp_attach_${itemId}_${System.currentTimeMillis()}.mp4")
            var ownedAudio: File? = null
            try {
                updateBatchItem(itemId) {
                    it.copy(isProcessing = true, status = "Adding audio to video...", progressText = "One-step mix")
                }
                val opened = materializeReadableFile(uri, "attach_$itemId")
                if (opened == null) {
                    updateBatchItem(itemId) { it.copy(isProcessing = false, status = "Could not open the audio", progressText = "") }
                    _toastMessage.emit("Could not open that audio file")
                    return@launch
                }
                if (opened.owned) ownedAudio = opened.file
                val rawName = if (item.videoName.isNotEmpty()) item.videoName else videoFile.name
                val fileName = "${rawName.substringBeforeLast(".")}u.mp4"
                val mixed = AudioVideoMerger.mixAndEncodeDubbedMedia(
                    videoFile = videoFile,
                    ttsWavFile = opened.file,
                    targetOutputFile = tempMux,
                    videoAudioVolume = item.videoVolume,
                    ttsAudioVolume = item.ttsVolume,
                    preferHardwareEncoder = _hardwareAcceleration.value,
                    ioBufferBytes = mixIoBufferBytes()
                ) { message ->
                    updateBatchItem(itemId) { it.copy(progressText = message) }
                }
                if (!mixed || !tempMux.exists() || tempMux.length() == 0L) {
                    updateBatchItem(itemId) { it.copy(isProcessing = false, status = "Mix failed", progressText = "") }
                    _toastMessage.emit("Could not add the audio to the video")
                    return@launch
                }
                val saved = copyMixedBesideSource(item.sourceDirectory ?: videoFile.parentFile, tempMux, fileName)
                updateBatchItem(itemId) {
                    it.copy(
                        isProcessing = false,
                        status = "Audio added",
                        progressText = "Saved: ${saved.name}",
                        dubbedVideoFile = saved
                    )
                }
                _toastMessage.emit("Saved ${saved.name}")
            } catch (e: kotlinx.coroutines.CancellationException) {
                updateBatchItem(itemId) { it.copy(isProcessing = false, status = "Stopped", progressText = "") }
                throw e
            } catch (e: Exception) {
                Log.e("BalaSpeakTTS", "Attach audio failed: ${e.message}", e)
                updateBatchItem(itemId) { it.copy(isProcessing = false, status = "Mix failed", progressText = "") }
                _toastMessage.emit("Mix failed: ${e.message}")
            } finally {
                try { tempMux.delete() } catch (e: Exception) {}
                try { ownedAudio?.delete() } catch (e: Exception) {}
                _isGlobalProcessing.value = false
                finishBackgroundEngine()
            }
        })
    }

    fun attachDubberV2Audio(uri: Uri) {
        if (_isGlobalProcessing.value || _v2Rendering.value) {
            viewModelScope.launch { _toastMessage.emit("Wait until the current job finishes") }
            return
        }
        val videoFile = _v2VideoFile.value
        if (videoFile == null || !videoFile.exists()) {
            viewModelScope.launch { _toastMessage.emit("Load a video first") }
            return
        }
        _isGlobalProcessing.value = true
        _v2Rendering.value = true
        _v2Progress.value = "Adding audio to video..."
        v2RenderJob = trackJob(viewModelScope.launch(Dispatchers.IO) {
            startBackgroundEngine("BalaSpeak Dubber V2", "Adding audio to video...")
            val tempMux = File(getApplication<Application>().cacheDir, "v2_attach_${System.currentTimeMillis()}.mp4")
            var ownedAudio: File? = null
            try {
                val opened = materializeReadableFile(uri, "v2_audio")
                if (opened == null) {
                    _v2Progress.value = "Could not open the audio"
                    _toastMessage.emit("Could not open that audio file")
                    return@launch
                }
                if (opened.owned) ownedAudio = opened.file
                val base = _v2VideoName.value.substringBeforeLast(".").ifBlank { "dubber" }
                val mixed = AudioVideoMerger.mixAndEncodeDubbedMedia(
                    videoFile = videoFile,
                    ttsWavFile = opened.file,
                    targetOutputFile = tempMux,
                    videoAudioVolume = _v2VideoVolume.value,
                    ttsAudioVolume = _v2TtsVolume.value,
                    preferHardwareEncoder = _hardwareAcceleration.value,
                    ioBufferBytes = mixIoBufferBytes()
                ) { message -> _v2Progress.value = message }
                if (!mixed || !tempMux.exists() || tempMux.length() == 0L) {
                    _v2Progress.value = "Mix failed"
                    _toastMessage.emit("Could not add the audio to the video")
                    return@launch
                }
                val saved = saveDubberV2Output(tempMux, "${base}u.mp4")
                _v2OutputFile.value = saved
                _v2Progress.value = "Saved ${saved.name}"
                _v2Status.value = "Audio added in one step"
                _toastMessage.emit("Saved ${saved.name}")
            } catch (e: kotlinx.coroutines.CancellationException) {
                _v2Progress.value = "Stopped"
                throw e
            } catch (e: Exception) {
                Log.e("BalaSpeakTTS", "Dubber V2 attach failed: ${e.message}", e)
                _v2Progress.value = "Mix failed"
                _toastMessage.emit("Mix failed: ${e.message}")
            } finally {
                try { tempMux.delete() } catch (e: Exception) {}
                try { ownedAudio?.delete() } catch (e: Exception) {}
                _v2Rendering.value = false
                _isGlobalProcessing.value = false
                finishBackgroundEngine()
            }
        })
    }

    private data class OpenedFile(val file: File, val owned: Boolean)

    private fun materializeReadableFile(uri: Uri, prefix: String): OpenedFile? {
        val real = resolveRealFileFromUri(uri)
        if (real != null && real.exists() && real.canRead()) return OpenedFile(real, false)
        val name = getFileNameFromUri(uri)
        val ext = name.substringAfterLast('.', "mp3").lowercase().filter { it.isLetterOrDigit() }.ifBlank { "mp3" }
        val temp = File(getApplication<Application>().cacheDir, "${prefix}_${System.currentTimeMillis()}.$ext")
        return try {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).use { output -> input.copyTo(output) }
            }
            if (temp.exists() && temp.length() > 0L) OpenedFile(temp, true) else null
        } catch (e: Exception) {
            Log.w("BalaSpeakTTS", "Could not copy audio: ${e.message}")
            null
        }
    }

    private fun copyMixedBesideSource(sourceDir: File?, tempMux: File, fileName: String): File {
        val writable = sourceDir != null && sourceDir.exists() && sourceDir.canWrite() &&
            sourceDir != getApplication<Application>().cacheDir
        if (writable) {
            val candidate = File(sourceDir, fileName)
            try {
                tempMux.copyTo(candidate, overwrite = true)
                if (candidate.exists() && candidate.length() > 0L) {
                    MediaScannerConnection.scanFile(getApplication(), arrayOf(candidate.absolutePath), null, null)
                    return candidate
                }
            } catch (e: Exception) {
                Log.w("BalaSpeakTTS", "Direct attach save failed: ${e.message}")
            }
        }
        val publicFile = saveToPublicDownloadsIfPossible(tempMux, fileName)
        if (publicFile != null && publicFile.exists() && publicFile.length() > 0L) return publicFile
        val fallbackDir = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: getApplication<Application>().filesDir
        val fallback = File(fallbackDir, fileName)
        tempMux.copyTo(fallback, overwrite = true)
        return fallback
    }

    fun startVideoDubbingForBatchItem(itemId: String) {
        trackJob(viewModelScope.launch(Dispatchers.IO) {
            startBackgroundEngine("BalaSpeak Dubbing", "Dubbing one video in the background...")
            try {
                processItemDubbing(itemId)
            } finally {
                finishBackgroundEngine()
            }
        })
    }

    fun startAllBatchDubbing() {
        if (_isGlobalProcessing.value) return
        _isGlobalProcessing.value = true

        globalProcessingJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                if (_backgroundProcessingActive.value) {
                    DubbingForegroundService.startService(
                        getApplication(),
                        "BalaSpeak Turbo Dubbing",
                        "Processing batch video dubbing in background..."
                    )
                }

                val list = _batchItems.value
                for ((index, item) in list.withIndex()) {
                    kotlinx.coroutines.yield()
                    if (item.videoFile != null && item.videoFile.exists() && item.srtSubtitles.isNotEmpty()) {
                        DubbingForegroundService.updateProgress(
                            getApplication(),
                            "Dubbing video ${index + 1} of ${list.size}",
                            index + 1,
                            list.size
                        )
                        processItemDubbing(item.id)
                    }
                }
                _toastMessage.emit("All batch dubbing tasks completed!")
            } catch (e: kotlinx.coroutines.CancellationException) {
                _toastMessage.emit("Dubbing process stopped.")
            } finally {
                _isGlobalProcessing.value = false
                finishBackgroundEngine()
            }
        }
    }

    fun stopAllProcessing() {
        globalProcessingJob?.cancel()
        transcriptionJob?.cancel()
        v2RenderJob?.cancel()
        cancelTrackedJobs()
        _isGlobalProcessing.value = false
        _v2Rendering.value = false
        _v2Progress.value = "Stopped"
        _isTranscribing.value = false
        _standaloneIsTranslating.value = false
        _batchItems.value = _batchItems.value.map { item ->
            if (item.isProcessing || item.isTranslating) {
                item.copy(isProcessing = false, isTranslating = false, status = "Stopped", progressText = "")
            } else item
        }
        DubbingForegroundService.stopService(getApplication())
        if (_backgroundProcessingActive.value) {
            DubbingForegroundService.startService(
                getApplication(),
                "BalaSpeak Studio",
                "Processing stopped. Background engine is still on."
            )
        }
    }

    private suspend fun processItemDubbing(itemId: String) {
        val item = _batchItems.value.firstOrNull { it.id == itemId } ?: return
        val videoFile = item.videoFile
        val subtitles = item.srtSubtitles

        if (videoFile == null || !videoFile.exists() || subtitles.isEmpty()) {
            updateBatchItem(itemId) { it.copy(status = "Files not loaded") }
            return
        }

        updateBatchItem(itemId) {
            it.copy(
                isProcessing = true,
                status = "Dubbing in progress...",
                progressText = "Rendering high-speed voice tracks..."
            )
        }

        try {
            if (item.itemVoiceName != null) {
                ttsManager.setVoice(item.itemVoiceName)
            }

            val tempTtsWav = File(getApplication<Application>().cacheDir, "tts_${itemId}_${System.currentTimeMillis()}.wav")

            // Base rate stays 1.0x unless a per-item speed was set. Pitch comes from Settings.
            // Auto-fit still compresses a line that would otherwise run past its subtitle window.
            val dubSpeed = if (item.itemSpeed != 1.0f) item.itemSpeed else 1.0f
            val dubPitch = if (item.itemPitch != 1.0f) item.itemPitch else globalPitch.value
            val dubVoice = item.itemVoiceName ?: globalSelectedVoice.value

            // Make the engine choice visible: warn when a needed pack is absent.
            val nm = NeuralVoiceManager.get(getApplication())
            val missing = listOfNotNull(dubVoice).mapNotNull { nm.neuralRoleOf(it)?.first }
                .distinct().filter { !nm.isReady(it) }
            if (missing.isNotEmpty()) {
                updateBatchItem(itemId) {
                    it.copy(progressText = "⚠ \u09AA\u09CD\u09AF\u09BE\u0995 \u09A8\u09C7\u0987 ($missing) — \u09B8\u09BF\u09B8\u09CD\u099F\u09C7\u09AE \u0995\u09A3\u09CD\u09A0\u09C7 \u09A1\u09BE\u09AC \u09B9\u099A\u09CD\u099B\u09C7")
                }
                _toastMessage.emit("🎙 \u09AD\u09AF\u09BC\u09C7\u09B8 \u09AA\u09CD\u09AF\u09BE\u0995 \u09A1\u09BE\u0989\u09A8\u09B2\u09CB\u09A1 \u09A8\u09C7\u0987 ($missing) — Settings \u2192 AI Voice Packs")
            }

            val ttsSuccess = ttsManager.compileSubtitlesToWav(
                subtitles = subtitles,
                destWavFile = tempTtsWav,
                speed = dubSpeed,
                pitch = dubPitch,
                voiceName = dubVoice,
                autoFitDuration = true
            ) { completed, total ->
                updateBatchItem(itemId) {
                    it.copy(progressText = "Synthesizing voice: $completed / $total cards")
                }
            }

            if (!ttsSuccess || !tempTtsWav.exists() || tempTtsWav.length() < 44) {
                updateBatchItem(itemId) {
                    it.copy(
                        isProcessing = false,
                        status = "Voice generation failed",
                        progressText = ""
                    )
                }
                _toastMessage.emit("Voice synthesis failed. Please check voice language.")
                return
            }

            updateBatchItem(itemId) {
                it.copy(progressText = "Mixing audio with original video...")
            }

            // Name output file e.g., 1.mp4 -> 1u.mp4, or 1.mp3 -> 1u.m4a
            val rawName = if (item.videoName.isNotEmpty()) item.videoName else videoFile.name
            val cleanBaseName = rawName.substringBeforeLast(".")
            val rawExt = rawName.substringAfterLast(".", "mp4").lowercase()
            val cleanExtension = when (rawExt) {
                "mp3", "wav" -> "m4a"
                else -> rawExt
            }
            val fileNameu = "${cleanBaseName}u.$cleanExtension"

            // Target directory: Automatically use the exact directory where the input video came from!
            val sourceDir = item.sourceDirectory ?: videoFile.parentFile
            val isSourceDirWritable = sourceDir != null && sourceDir.exists() && sourceDir.canWrite() && sourceDir != getApplication<Application>().cacheDir

            val targetDir = if (isSourceDirWritable) {
                sourceDir!!
            } else {
                getEffectiveOutputDir()
            }

            // Encode to a clean temporary file in cache to ensure fast, unbuffered disk I/O without any partial-lock truncation
            val tempMuxFile = File(getApplication<Application>().cacheDir, "temp_mux_${itemId}_${System.currentTimeMillis()}.$cleanExtension")

            val mergerSuccess = AudioVideoMerger.mixAndEncodeDubbedMedia(
                videoFile = videoFile,
                ttsWavFile = tempTtsWav,
                targetOutputFile = tempMuxFile,
                videoAudioVolume = item.videoVolume,
                ttsAudioVolume = item.ttsVolume,
                preferHardwareEncoder = _hardwareAcceleration.value,
                ioBufferBytes = mixIoBufferBytes()
            ) { mergerMsg ->
                updateBatchItem(itemId) { it.copy(progressText = mergerMsg) }
            }

            // Cleanup temp wave file
            try { tempTtsWav.delete() } catch (e: Exception) {}

            if (mergerSuccess && tempMuxFile.exists() && tempMuxFile.length() > 0) {
                var finalOutputFile: File? = null
                var displayLocation = fileNameu

                // 1. Direct copy to the source folder (or configured targetDir)
                if (targetDir.exists() && targetDir.canWrite()) {
                    val candidateFile = File(targetDir, fileNameu)
                    try {
                        tempMuxFile.copyTo(candidateFile, overwrite = true)
                        if (candidateFile.exists() && candidateFile.length() > 0) {
                            finalOutputFile = candidateFile
                            displayLocation = "${targetDir.name}/$fileNameu"
                            MediaScannerConnection.scanFile(getApplication(), arrayOf(candidateFile.absolutePath), null, null)
                        }
                    } catch (e: Exception) {
                        Log.w("BalaSpeakTTS", "Direct save to source directory failed: ${e.message}")
                    }
                }

                // 2. Fallback to public Downloads/MediaStore if source folder was read-only
                if (finalOutputFile == null) {
                    val publicFile = saveToPublicDownloadsIfPossible(tempMuxFile, fileNameu)
                    if (publicFile != null && publicFile.exists() && publicFile.length() > 0) {
                        finalOutputFile = publicFile
                        displayLocation = if (publicFile.parentFile != null) "${publicFile.parentFile?.name}/$fileNameu" else "Downloads/$fileNameu"
                    } else {
                        // 3. Fallback: app external downloads directory
                        val fallbackDir = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                            ?: getApplication<Application>().filesDir
                        val fallbackFile = File(fallbackDir, fileNameu)
                        tempMuxFile.copyTo(fallbackFile, overwrite = true)
                        finalOutputFile = fallbackFile
                        displayLocation = fallbackFile.name
                    }
                }

                // Cleanup temp mux file
                try { tempMuxFile.delete() } catch (e: Exception) {}

                updateBatchItem(itemId) {
                    it.copy(
                        isProcessing = false,
                        status = "Dubbing Complete! ✨",
                        progressText = "Saved: $displayLocation",
                        dubbedVideoFile = finalOutputFile
                    )
                }
                _toastMessage.emit("Dubbed successfully: $displayLocation")
            } else {
                try { tempMuxFile.delete() } catch (e: Exception) {}

                updateBatchItem(itemId) {
                    it.copy(
                        isProcessing = false,
                        status = "Merging failed",
                        progressText = ""
                    )
                }
                _toastMessage.emit("Video merging failed. Check input video format.")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            updateBatchItem(itemId) {
                it.copy(isProcessing = false, status = "Stopped", progressText = "")
            }
            throw e
        } catch (e: Exception) {
            Log.e("BalaSpeakTTS", "Error dubbing item $itemId: ${e.message}", e)
            updateBatchItem(itemId) {
                it.copy(
                    isProcessing = false,
                    status = "Error: ${e.localizedMessage}",
                    progressText = ""
                )
            }
            _toastMessage.emit("Dubbing error: ${e.message}")
        }
    }

    fun getEffectiveOutputDir(): File {
        val customPath = _customOutputDirPath.value
        if (customPath.isNotBlank()) {
            val customFolder = File(customPath)
            if (customFolder.exists() && customFolder.canWrite()) {
                return customFolder
            }
        }
        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (publicDownloads != null && publicDownloads.exists() && publicDownloads.canWrite()) {
            return publicDownloads
        }
        return getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: getApplication<Application>().cacheDir
    }

    private fun saveToPublicDownloadsIfPossible(sourceFile: File, displayName: String): File? {
        try {
            val customPath = _customOutputDirPath.value
            if (customPath.isNotBlank()) {
                val customFolder = File(customPath)
                if (customFolder.exists() && customFolder.canWrite()) {
                    val dest = File(customFolder, displayName)
                    if (dest.canonicalPath == sourceFile.canonicalPath) {
                        return dest
                    }
                    sourceFile.copyTo(dest, overwrite = true)
                    MediaScannerConnection.scanFile(getApplication(), arrayOf(dest.absolutePath), null, null)
                    return dest
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, if (displayName.endsWith(".m4a", ignoreCase = true)) "audio/mp4" else "video/mp4")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/BalaSpeak")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val resolver = getApplication<Application>().contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { os ->
                        FileInputStream(sourceFile).use { `is` -> `is`.copyTo(os) }
                    }
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    return File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "BalaSpeak/$displayName")
                }
            } else {
                val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (publicDir.exists() || publicDir.mkdirs()) {
                    val destFile = File(publicDir, displayName)
                    if (destFile.canonicalPath != sourceFile.canonicalPath) {
                        sourceFile.copyTo(destFile, overwrite = true)
                    }
                    MediaScannerConnection.scanFile(getApplication(), arrayOf(destFile.absolutePath), null, null)
                    return destFile
                }
            }
        } catch (e: Exception) {
            Log.e("BalaSpeakTTS", "Public save fallback error: ${e.message}")
        }
        return null
    }

    // --- Standalone Subtitle Translator Actions ---

    private val _standaloneSrtSourceDir = MutableStateFlow<File?>(null)
    val standaloneSrtSourceDir: StateFlow<File?> = _standaloneSrtSourceDir.asStateFlow()

    fun setStandaloneSourceLang(lang: String) {
        _standaloneSourceLang.value = lang
    }

    fun setStandaloneTargetLang(lang: LanguageOption) {
        _standaloneTargetLang.value = lang
    }

    fun loadStandaloneSrt(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val name = getFileNameFromUri(uri)
                val cleanName = if (name.contains("/")) name.substringAfterLast("/") else name

                val realFile = resolveRealFileFromUri(uri)
                _standaloneSrtSourceDir.value = realFile?.parentFile

                val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
                val content = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                val entries = SubtitleParser.parseSrtOrVtt(content)

                if (entries.isEmpty()) {
                    _toastMessage.emit("No valid subtitle timestamps found!")
                    return@launch
                }

                _standaloneSrtFileName.value = cleanName
                _standaloneOriginalSubtitles.value = entries
                _standaloneTranslatedSubtitles.value = emptyList()
                rememberDetectedOfflineSource(entries)
                _toastMessage.emit("Loaded ${entries.size} subtitle entries")
            } catch (e: Exception) {
                _toastMessage.emit("Error loading subtitles: ${e.message}")
            }
        }
    }

    fun loadStandaloneSrtFileDirect(file: File) {
        if (!file.exists() || !file.canRead()) {
            viewModelScope.launch { _toastMessage.emit("Subtitle file cannot be read") }
            return
        }
        try {
            val content = file.readText(Charsets.UTF_8)
            val entries = SubtitleParser.parseSrtOrVtt(content)
            if (entries.isEmpty()) {
                viewModelScope.launch { _toastMessage.emit("No valid subtitle entries in file") }
                return
            }
            _standaloneSrtSourceDir.value = file.parentFile
            _standaloneSrtFileName.value = file.name
            _standaloneOriginalSubtitles.value = entries
            _standaloneTranslatedSubtitles.value = emptyList()
            rememberDetectedOfflineSource(entries)
            viewModelScope.launch { _toastMessage.emit("Loaded ${entries.size} subtitle entries from ${file.name}") }
        } catch (e: Exception) {
            viewModelScope.launch { _toastMessage.emit("Error loading subtitles: ${e.message}") }
        }
    }

    fun loadStandaloneSrtFromText(text: String, fileName: String = "custom_subtitles.srt") {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (text.isNotBlank()) {
                    val parsed = SubtitleParser.parseSrtOrVtt(text)
                    _standaloneSrtFileName.value = fileName
                    _standaloneOriginalSubtitles.value = parsed
                    _standaloneTranslatedSubtitles.value = emptyList()
                    rememberDetectedOfflineSource(parsed)
                    _toastMessage.emit("Loaded ${parsed.size} subtitle entries")
                }
            } catch (e: Exception) {
                _toastMessage.emit("Failed to parse subtitle text: ${e.message}")
            }
        }
    }

    fun translateStandaloneSubtitles() {
        val originalList = _standaloneOriginalSubtitles.value
        if (originalList.isEmpty()) {
            viewModelScope.launch { _toastMessage.emit("Please select or paste a subtitle file first!") }
            return
        }

        val target = _standaloneTargetLang.value
        val source = _standaloneSourceLang.value

        trackJob(viewModelScope.launch(Dispatchers.IO) {
            try {
                _standaloneIsTranslating.value = true
                _standaloneTranslateProgress.value = Pair(0, originalList.size)
                startBackgroundEngine("BalaSpeak Translation", "Translating subtitles to ${target.name}...")

                val translated = SubtitleTranslator.translateSubtitles(
                    subtitles = originalList,
                    targetLang = target.code,
                    sourceLang = source,
                    concurrencyLimit = _translationConcurrency.value,
                    onProgress = { cur, total ->
                        _standaloneTranslateProgress.value = Pair(cur, total)
                        updateBackgroundEngine("Translating to ${target.name}: $cur/$total", cur, total)
                    }
                )

                _standaloneTranslatedSubtitles.value = translated
                _standaloneIsTranslating.value = false
                _toastMessage.emit("Successfully translated ${translated.size} subtitles to ${target.name}!")
            } catch (e: kotlinx.coroutines.CancellationException) {
                _standaloneIsTranslating.value = false
                _toastMessage.emit("Translation stopped")
                throw e
            } catch (e: Exception) {
                Log.e("BalaSpeakTTS", "Standalone translation error: ${e.message}")
                _standaloneIsTranslating.value = false
                _toastMessage.emit("Translation error: ${e.message}")
            } finally {
                _standaloneIsTranslating.value = false
                finishBackgroundEngine()
            }
        })
    }

    fun setOfflineSourceLang(lang: LanguageOption) {
        _offlineSourceLang.value = lang
    }

    private fun rememberDetectedOfflineSource(entries: List<SubtitleEntry>) {
        val sample = entries.take(12).joinToString(" ") { it.text }
        val detected = LanguageDetector.detectLanguage(sample)
        val match = SubtitleTranslator.supportedLanguages.firstOrNull {
            it.code.equals(detected.code, ignoreCase = true)
        }
        if (match != null && detected.confidence >= 0.55f) {
            _offlineSourceLang.value = match
        }
    }

    fun refreshOfflinePackStatus() {
        val source = _offlineSourceLang.value
        val target = _standaloneTargetLang.value
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (OfflineSubtitleTranslator.mlKitLanguageOrNull(source.code) == null ||
                    OfflineSubtitleTranslator.mlKitLanguageOrNull(target.code) == null
                ) {
                    _isOfflinePackReady.value = false
                    _offlinePackStatus.value = "This language is not in the offline pack list."
                    return@launch
                }
                val ready = OfflineSubtitleTranslator.areModelsReady(source.code, target.code)
                _isOfflinePackReady.value = ready
                _offlinePackStatus.value = if (ready) {
                    "Pack ready on this phone. No internet needed."
                } else {
                    "Pack not downloaded yet. First download needs internet."
                }
            } catch (e: Exception) {
                _isOfflinePackReady.value = false
                _offlinePackStatus.value = "Could not check the pack. Google Play services is required."
                Log.w("DocumentViewModel", "Offline pack check failed: ${e.message}")
            }
        }
    }

    fun downloadOfflineLanguagePack() {
        if (_isDownloadingOfflinePack.value || _standaloneIsTranslating.value) return
        val source = _offlineSourceLang.value
        val target = _standaloneTargetLang.value
        trackJob(viewModelScope.launch(Dispatchers.IO) {
            _isDownloadingOfflinePack.value = true
            try {
                OfflineSubtitleTranslator.downloadModels(source.code, target.code) { status ->
                    _offlinePackStatus.value = status
                }
                _isOfflinePackReady.value = true
                _offlinePackStatus.value = "Pack ready on this phone. No internet needed."
                _toastMessage.emit("Offline pack downloaded for ${source.name} → ${target.name}")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _isOfflinePackReady.value = false
                _offlinePackStatus.value = "Download failed. Connect once, and check Google Play services."
                _toastMessage.emit("Offline pack download failed: ${e.message}")
            } finally {
                _isDownloadingOfflinePack.value = false
            }
        })
    }

    fun translateStandaloneOffline() {
        val originalList = _standaloneOriginalSubtitles.value
        if (originalList.isEmpty()) {
            viewModelScope.launch { _toastMessage.emit("Please select or paste a subtitle file first!") }
            return
        }
        val source = _offlineSourceLang.value
        val target = _standaloneTargetLang.value
        if (OfflineSubtitleTranslator.mlKitLanguageOrNull(source.code) == null ||
            OfflineSubtitleTranslator.mlKitLanguageOrNull(target.code) == null
        ) {
            viewModelScope.launch { _toastMessage.emit("Offline packs do not include this language.") }
            return
        }

        trackJob(viewModelScope.launch(Dispatchers.IO) {
            try {
                _standaloneIsTranslating.value = true
                _standaloneTranslateProgress.value = Pair(0, originalList.size)
                startBackgroundEngine("BalaSpeak Offline Translate", "Translating subtitles on this phone...")
                _offlinePackStatus.value = "Preparing offline pack..."
                val translated = OfflineSubtitleTranslator.translateSubtitles(
                    subtitles = originalList,
                    sourceCode = source.code,
                    targetCode = target.code
                ) { done, total ->
                    _standaloneTranslateProgress.value = Pair(done, total)
                    _offlinePackStatus.value = "Translating offline: $done / $total"
                    updateBackgroundEngine("Offline translate $done/$total", done, total)
                }
                _standaloneTranslatedSubtitles.value = translated
                _isOfflinePackReady.value = true
                _offlinePackStatus.value = "Pack ready on this phone. No internet needed."
                _toastMessage.emit("Offline translation done. Numbers and timestamps were kept.")
            } catch (e: kotlinx.coroutines.CancellationException) {
                _toastMessage.emit("Offline translation stopped")
                throw e
            } catch (e: Exception) {
                Log.e("BalaSpeakTTS", "Offline translation error: ${e.message}", e)
                _offlinePackStatus.value = "Offline translation failed. Download the pack while online."
                _toastMessage.emit("Offline translation failed: ${e.message}")
            } finally {
                _standaloneIsTranslating.value = false
                finishBackgroundEngine()
            }
        })
    }

    fun translateSubtitlesForBatchItemOffline(itemId: String, targetLangCode: String, targetLangName: String) {
        trackJob(viewModelScope.launch(Dispatchers.IO) {
            val item = _batchItems.value.firstOrNull { it.id == itemId } ?: return@launch
            if (item.srtSubtitles.isEmpty()) {
                _toastMessage.emit("No subtitles loaded to translate!")
                return@launch
            }
            val detectedCode = item.detectedLanguage?.code
            val sourceCode = when {
                detectedCode != null && OfflineSubtitleTranslator.mlKitLanguageOrNull(detectedCode) != null ->
                    detectedCode
                detectedCode == null -> "en"
                else -> {
                    _toastMessage.emit(
                        "Offline packs do not include the detected language ${item.detectedLanguage?.name}."
                    )
                    return@launch
                }
            }
            if (OfflineSubtitleTranslator.mlKitLanguageOrNull(targetLangCode) == null) {
                _toastMessage.emit("Offline packs do not include $targetLangName.")
                return@launch
            }

            startBackgroundEngine("BalaSpeak Offline Translate", "Translating to $targetLangName on this phone...")
            updateBatchItem(itemId) {
                it.copy(
                    isTranslating = true,
                    status = "Offline translating to $targetLangName...",
                    progressText = "Downloading pack if needed..."
                )
            }
            try {
                val translated = OfflineSubtitleTranslator.translateSubtitles(
                    subtitles = item.srtSubtitles,
                    sourceCode = sourceCode,
                    targetCode = targetLangCode
                ) { count, total ->
                    updateBatchItem(itemId) {
                        it.copy(progressText = "Offline: $count / $total lines")
                    }
                    updateBackgroundEngine("Offline translate $count/$total", count, total)
                }
                val newSrtContent = SubtitleTranslator.exportToSrt(translated)
                val cleanName = if (item.srtName.isNotEmpty()) {
                    "${item.srtName.substringBeforeLast(".")}_${targetLangCode}.srt"
                } else "subtitles_$targetLangCode.srt"
                val targetLangOpt = SubtitleTranslator.supportedLanguages.firstOrNull {
                    it.code.equals(targetLangCode, ignoreCase = true)
                }
                updateBatchItem(itemId) {
                    it.copy(
                        isTranslating = false,
                        srtSubtitles = translated,
                        srtContent = newSrtContent,
                        srtName = cleanName,
                        detectedLanguage = DetectedLanguage(
                            code = targetLangCode,
                            name = targetLangName,
                            nativeName = targetLangOpt?.nativeName ?: targetLangName,
                            confidence = 1.0f,
                            scriptName = targetLangName
                        ),
                        itemVoiceName = VoiceCast.defaultSpeakerId(targetLangCode),
                        status = "Offline translated to $targetLangName. Ready to dub",
                        progressText = ""
                    )
                }
                _toastMessage.emit("Offline translation done. Timings were kept.")
            } catch (e: kotlinx.coroutines.CancellationException) {
                updateBatchItem(itemId) {
                    it.copy(isTranslating = false, status = "Stopped", progressText = "")
                }
                throw e
            } catch (e: Exception) {
                Log.e("BalaSpeakTTS", "Batch offline translation failed: ${e.message}", e)
                updateBatchItem(itemId) {
                    it.copy(
                        isTranslating = false,
                        status = "Offline translation failed",
                        progressText = ""
                    )
                }
                _toastMessage.emit("Offline translation failed: ${e.message}")
            } finally {
                updateBatchItem(itemId) { current ->
                    if (current.isTranslating) current.copy(isTranslating = false) else current
                }
                finishBackgroundEngine()
            }
        })
    }

    fun exportStandaloneTranslatedSrt(): String {
        val list = if (_standaloneTranslatedSubtitles.value.isNotEmpty()) _standaloneTranslatedSubtitles.value else _standaloneOriginalSubtitles.value
        return SubtitleTranslator.exportToSrt(list)
    }

    fun saveStandaloneTranslatedSrtToDownloads(): File? {
        val srtText = exportStandaloneTranslatedSrt()
        if (srtText.isBlank()) return null

        try {
            val baseName = _standaloneSrtFileName.value.ifBlank { "subtitles" }.substringBeforeLast(".")
            // Output name strictly ends with u.srt (e.g. 1.srt -> 1u.srt, NO "-en" or ".txt")
            val fileName = "${baseName}u.srt"

            // Target folder: directly in the exact directory where the input file came from!
            val sourceDir = _standaloneSrtSourceDir.value
            val isSourceDirWritable = sourceDir != null && sourceDir.exists() && sourceDir.canWrite() && sourceDir != getApplication<Application>().cacheDir

            val targetDir = if (isSourceDirWritable) {
                sourceDir!!
            } else {
                getEffectiveOutputDir()
            }

            val srtFile = File(targetDir, fileName)
            srtFile.writeText(srtText, Charsets.UTF_8)
            MediaScannerConnection.scanFile(getApplication(), arrayOf(srtFile.absolutePath), null, null)

            val displayLocation = if (isSourceDirWritable) "${targetDir.name}/$fileName" else fileName
            viewModelScope.launch { _toastMessage.emit("Saved: $displayLocation") }
            return srtFile
        } catch (e: Exception) {
            Log.e("BalaSpeakTTS", "Error saving srt: ${e.message}")
            viewModelScope.launch { _toastMessage.emit("Failed to save SRT: ${e.message}") }
            return null
        }
    }

    fun sendTranslatedToDubbingQueue() {
        val translated = _standaloneTranslatedSubtitles.value
        if (translated.isEmpty()) {
            viewModelScope.launch { _toastMessage.emit("No translated subtitles to send!") }
            return
        }

        val baseName = _standaloneSrtFileName.value.ifBlank { "subtitles" }.substringBeforeLast(".")
        val srtName = "${baseName}u.srt"
        val srtContent = SubtitleTranslator.exportToSrt(translated)

        val newItem = BatchDubItem(
            srtContent = srtContent,
            srtName = srtName,
            srtSubtitles = translated,
            status = "Translated subtitles loaded. Please select a video."
        )

        val currentList = _batchItems.value.toMutableList()
        if (currentList.size == 1 && currentList[0].videoFile == null && currentList[0].srtSubtitles.isEmpty()) {
            currentList[0] = newItem
        } else {
            currentList.add(newItem)
        }
        _batchItems.value = currentList
        viewModelScope.launch { _toastMessage.emit("Added translated subtitle to Dubbing Queue!") }
    }

    private fun saveSrtToPublicDownloads(sourceFile: File, displayName: String): File? {
        try {
            val customPath = _customOutputDirPath.value
            if (customPath.isNotBlank()) {
                val customFolder = File(customPath)
                if (customFolder.exists() && customFolder.canWrite()) {
                    val dest = File(customFolder, displayName)
                    FileInputStream(sourceFile).use { `is` ->
                        FileOutputStream(dest).use { os -> `is`.copyTo(os) }
                    }
                    return dest
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/BalaSpeak")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val resolver = getApplication<Application>().contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { os ->
                        FileInputStream(sourceFile).use { `is` -> `is`.copyTo(os) }
                    }
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    return File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "BalaSpeak/$displayName"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("BalaSpeakTTS", "Public srt save fallback error: ${e.message}")
        }
        return null
    }

    fun shareSrt(content: String, fileName: String = "subtitles.srt") {
        try {
            val cacheFile = File(getApplication<Application>().cacheDir, fileName)
            cacheFile.writeText(content, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(
                getApplication<Application>(),
                fileProviderAuthority(),
                cacheFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(shareIntent, "Share Subtitle File").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            getApplication<Application>().startActivity(chooser)
        } catch (e: Exception) {
            Log.e("BalaSpeakTTS", "Error sharing srt: ${e.message}")
            viewModelScope.launch { _toastMessage.emit("Share error: ${e.message}") }
        }
    }

    // --- AI Audio & Video Transcription Actions ---

    fun apiKeyFile(): File = ApiKeyStore.editableFile(getApplication())

    fun getEffectiveApiKeys(): List<String> {
        val fileKeys = try {
            ApiKeyStore.readKeys(getApplication())
        } catch (e: Exception) {
            emptyList()
        }
        if (fileKeys.isNotEmpty()) return fileKeys

        val userRaw = _geminiApiKey.value.trim()
        val buildKey = BuildConfig.GEMINI_API_KEY.trim()
        val envKey = (try { System.getenv("GEMINI_API_KEY") } catch (e: Exception) { null })?.trim() ?: ""

        val combined = "$userRaw\n$buildKey\n$envKey"
        return combined.split(',', '\n', ';', ' ')
            .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
            .filter { it.isNotBlank() && it != "MY_GEMINI_API_KEY" }
            .distinct()
    }

    fun getEffectiveGeminiApiKey(): String {
        return getEffectiveApiKeys().firstOrNull() ?: ""
    }

    fun isUsingFreeUnlimitedMode(): Boolean {
        return getEffectiveApiKeys().isEmpty()
    }

    fun setGeminiApiKey(key: String) {
        _geminiApiKey.value = key.trim()
        prefs.edit().putString("pref_gemini_api_key", key.trim()).apply()
    }

    fun setTranscribeVideo(uri: Uri?, file: File?, name: String) {
        _transcribeVideoUri.value = uri
        _transcribeVideoFile.value = file
        _transcribeVideoName.value = name
        _savedSubtitleFilePath.value = null
        _transcribeProgress.value = 0f
        _transcribeCurrentChunk.value = 0
        _transcribeTotalChunks.value = 0
        _transcribedSrtContent.value = ""
        _transcribedSubtitles.value = emptyList()

        viewModelScope.launch(Dispatchers.IO) {
            val realFile = file ?: uri?.let { resolveRealFileFromUri(it) }
            if (realFile != null && realFile.exists()) {
                _transcribeVideoFile.value = realFile
                _transcribeVideoSizeBytes.value = realFile.length()
                _transcribeVideoDurationMs.value = VideoTranscriber.getVideoDurationMs(realFile)
            } else if (uri != null) {
                try {
                    getApplication<Application>().contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        _transcribeVideoSizeBytes.value = pfd.statSize
                    }
                } catch (e: Exception) {
                    _transcribeVideoSizeBytes.value = 0L
                }
            }

            // Auto-persist active session draft into Room Database
            try {
                val initialTask = MediaTaskHistory(
                    taskType = "TRANSCRIBE",
                    title = name,
                    filePath = realFile?.absolutePath,
                    fileUri = uri?.toString(),
                    targetLanguage = _transcribeTargetLang.value,
                    status = "SAVED",
                    durationMs = _transcribeVideoDurationMs.value,
                    fileSizeBytes = _transcribeVideoSizeBytes.value,
                    updatedAt = System.currentTimeMillis()
                )
                val newId = mediaTaskHistoryDao.insertTask(initialTask)
                _currentActiveHistoryTaskId.value = newId
                prefs.edit().putLong("pref_last_active_task_id", newId).apply()
            } catch (e: Exception) {
                Log.w("DocumentViewModel", "Error saving draft task: ${e.message}")
            }
        }
    }

    fun setTranscribeTargetLang(lang: String) {
        _transcribeTargetLang.value = lang
    }

    fun cancelTranscription() {
        transcriptionJob?.cancel()
        _isTranscribing.value = false
        _transcribeStatusText.value = "Transcription cancelled (${_transcribedSubtitles.value.size} lines saved)"
        finishBackgroundEngine()
    }

    fun startVideoTranscription() {
        val uri = _transcribeVideoUri.value
        val file = _transcribeVideoFile.value
        if (uri == null && file == null) {
            viewModelScope.launch { _toastMessage.emit("Please select an audio or video file first") }
            return
        }

        val effectiveKeys = getEffectiveApiKeys()
        if (effectiveKeys.isEmpty()) {
            viewModelScope.launch { _toastMessage.emit("Please enter at least one Gemini or Groq API Key in Settings") }
            return
        }

        _isTranscribing.value = true
        _transcribeProgress.value = 0f
        _transcribePercent.value = 0
        _transcribePhase.value = "INITIALIZING"
        _transcribeCurrentChunk.value = 0
        _transcribeTotalChunks.value = 0
        _transcribedSrtContent.value = ""
        _transcribedSubtitles.value = emptyList()
        _transcribeStatusText.value = "Preparing media file..."

        transcriptionJob = viewModelScope.launch(Dispatchers.IO) {
            startBackgroundEngine("BalaSpeak Transcriber", "Transcribing media in the background...")
            try {
                val context = getApplication<Application>()
                var resolvedMediaFile = file ?: uri?.let { resolveRealFileFromUri(it) }

                // If uri cannot be resolved directly to real file, copy stream to cache
                if (resolvedMediaFile == null || !resolvedMediaFile.exists()) {
                    _transcribeStatusText.value = "Copying media file..."
                    val ext = _transcribeVideoName.value.substringAfterLast('.', "mp4")
                    val temp = File(context.cacheDir, "transcribe_src_${System.currentTimeMillis()}.$ext")
                    context.contentResolver.openInputStream(uri!!)?.use { input ->
                        FileOutputStream(temp).use { output ->
                            input.copyTo(output)
                        }
                    }
                    resolvedMediaFile = temp
                }

                if (!resolvedMediaFile.exists() || resolvedMediaFile.length() == 0L) {
                    throw IllegalStateException("Failed to open media file.")
                }

                val tempDir = File(context.cacheDir, "transcribe_work").apply { mkdirs() }

                val (srtText, entries) = VideoTranscriber.transcribeFullMedia(
                    context = context,
                    mediaFile = resolvedMediaFile,
                    tempDir = tempDir,
                    apiKeys = effectiveKeys,
                    targetLanguage = _transcribeTargetLang.value,
                    parallelChunks = _workerThreads.value.coerceIn(1, 4),
                    translationConcurrency = _translationConcurrency.value,
                    onProgress = { chunkProg ->
                        _transcribeCurrentChunk.value = chunkProg.currentChunk
                        _transcribeTotalChunks.value = chunkProg.totalChunks
                        _transcribeProgress.value = chunkProg.progressFraction
                        _transcribePercent.value = chunkProg.progressPercent
                        _transcribePhase.value = chunkProg.phase
                        _transcribeStatusText.value = chunkProg.statusMessage
                        updateBackgroundEngine(
                            chunkProg.statusMessage,
                            chunkProg.progressPercent,
                            100
                        )
                        if (chunkProg.accumulatedSubtitles.isNotEmpty()) {
                            _transcribedSubtitles.value = chunkProg.accumulatedSubtitles
                            val currentSrt = SubtitleTranslator.exportToSrt(chunkProg.accumulatedSubtitles)
                            _transcribedSrtContent.value = currentSrt

                            // Immediately persist intermediate subtitles in Room so user never loses progress!
                            val activeId = _currentActiveHistoryTaskId.value ?: 0L
                            val inProgressTask = MediaTaskHistory(
                                id = activeId,
                                taskType = "TRANSCRIBE",
                                title = _transcribeVideoName.value.ifBlank { "Media Task" },
                                filePath = resolvedMediaFile.absolutePath,
                                fileUri = uri?.toString(),
                                targetLanguage = _transcribeTargetLang.value,
                                srtContent = currentSrt,
                                subtitleCount = chunkProg.accumulatedSubtitles.size,
                                currentChunk = chunkProg.currentChunk,
                                totalChunks = chunkProg.totalChunks,
                                status = "IN_PROGRESS",
                                durationMs = _transcribeVideoDurationMs.value,
                                fileSizeBytes = _transcribeVideoSizeBytes.value,
                                updatedAt = System.currentTimeMillis()
                            )
                            viewModelScope.launch(Dispatchers.IO) {
                                try {
                                    val savedId = mediaTaskHistoryDao.insertTask(inProgressTask)
                                    _currentActiveHistoryTaskId.value = savedId
                                    prefs.edit().putLong("pref_last_active_task_id", savedId).apply()
                                } catch (e: Exception) {
                                    Log.w("DocumentViewModel", "Error updating history progress: ${e.message}")
                                }
                            }
                        }
                    }
                )

                _transcribedSrtContent.value = srtText
                _transcribedSubtitles.value = entries
                _transcribeProgress.value = 1.0f
                _transcribePercent.value = 100
                _transcribePhase.value = "COMPLETED"
                _transcribeStatusText.value = "Transcription completed (${entries.size} subtitles)"

                // Automatically save SRT alongside media or in output directory
                val targetDir = resolvedMediaFile.parentFile ?: getEffectiveOutputDir()
                val baseName = _transcribeVideoName.value.ifBlank { "audio_video" }.substringBeforeLast('.')
                val savedFile = saveTranscribedSrt(targetDir, "$baseName.srt")
                if (savedFile != null) {
                    _savedSubtitleFilePath.value = savedFile.absolutePath
                    _toastMessage.emit("Subtitles saved: ${savedFile.name}")
                } else {
                    _toastMessage.emit("Transcription completed successfully!")
                }

                // Persist completed task permanently in Room
                val activeId = _currentActiveHistoryTaskId.value ?: 0L
                val completedTask = MediaTaskHistory(
                    id = activeId,
                    taskType = "TRANSCRIBE",
                    title = _transcribeVideoName.value.ifBlank { "Media Task" },
                    filePath = resolvedMediaFile.absolutePath,
                    fileUri = uri?.toString(),
                    targetLanguage = _transcribeTargetLang.value,
                    srtContent = srtText,
                    subtitleCount = entries.size,
                    currentChunk = _transcribeTotalChunks.value,
                    totalChunks = _transcribeTotalChunks.value,
                    status = "COMPLETED",
                    savedOutputPath = savedFile?.absolutePath,
                    durationMs = _transcribeVideoDurationMs.value,
                    fileSizeBytes = _transcribeVideoSizeBytes.value,
                    updatedAt = System.currentTimeMillis()
                )
                try {
                    val finalId = mediaTaskHistoryDao.insertTask(completedTask)
                    _currentActiveHistoryTaskId.value = finalId
                    prefs.edit().putLong("pref_last_active_task_id", finalId).apply()
                } catch (e: Exception) {
                    Log.w("DocumentViewModel", "Error finalizing history task: ${e.message}")
                }

            } catch (e: kotlinx.coroutines.CancellationException) {
                _transcribeStatusText.value = "Transcription cancelled (${_transcribedSubtitles.value.size} lines saved)"
                _toastMessage.emit("Transcription cancelled")
                throw e
            } catch (t: Throwable) {
                Log.e("VideoTranscribe", "Transcription error: ${t.message}", t)
                val errorDetails = when (t) {
                    is OutOfMemoryError -> "Low memory on device. Please close other apps and try again."
                    else -> t.localizedMessage ?: t.message ?: "Unexpected error occurred"
                }
                _transcribeStatusText.value = "Error: $errorDetails"
                _toastMessage.emit("Transcription failed: $errorDetails")
            } finally {
                _isTranscribing.value = false
                finishBackgroundEngine()
            }
        }
    }

    fun saveTranscribedSrt(customDir: File? = null, customFileName: String? = null): File? {
        val content = _transcribedSrtContent.value
        if (content.isBlank()) return null
        return try {
            val baseName = _transcribeVideoName.value.ifBlank { "video" }.substringBeforeLast('.')
            val fileName = customFileName?.ifBlank { null } ?: "$baseName.srt"

            val targetDir = customDir ?: getEffectiveOutputDir()
            if (!targetDir.exists()) targetDir.mkdirs()

            val srtFile = File(targetDir, fileName)
            srtFile.writeText(content, Charsets.UTF_8)
            MediaScannerConnection.scanFile(getApplication(), arrayOf(srtFile.absolutePath), null, null)
            _savedSubtitleFilePath.value = srtFile.absolutePath
            viewModelScope.launch { _toastMessage.emit("Saved: ${srtFile.absolutePath}") }
            srtFile
        } catch (e: Exception) {
            Log.e("VideoTranscribe", "Save srt error: ${e.message}")
            viewModelScope.launch { _toastMessage.emit("Save failed: ${e.message}") }
            null
        }
    }

    fun updateTranscribedSubtitleText(index: Int, newText: String) {
        val current = _transcribedSubtitles.value.toMutableList()
        val idx = current.indexOfFirst { it.index == index }
        if (idx != -1) {
            current[idx] = current[idx].copy(text = newText)
            _transcribedSubtitles.value = current
            _transcribedSrtContent.value = SubtitleTranslator.exportToSrt(current)
        }
    }

    fun clearTranscribeSession() {
        _transcribeVideoUri.value = null
        _transcribeVideoFile.value = null
        _transcribeVideoName.value = ""
        _transcribeVideoDurationMs.value = 0L
        _transcribeVideoSizeBytes.value = 0L
        _transcribedSrtContent.value = ""
        _transcribedSubtitles.value = emptyList()
        _savedSubtitleFilePath.value = null
        _transcribeStatusText.value = "Select audio or video to start transcribing"
        _transcribeProgress.value = 0f
        _currentActiveHistoryTaskId.value = null
        _restoredSessionBanner.value = null
        prefs.edit().remove("pref_last_active_task_id").apply()
    }

    fun restoreSessionFromHistory(task: MediaTaskHistory, auto: Boolean = false) {
        viewModelScope.launch(Dispatchers.Main) {
            _currentActiveHistoryTaskId.value = task.id
            prefs.edit().putLong("pref_last_active_task_id", task.id).apply()

            if (!task.filePath.isNullOrBlank()) {
                val f = File(task.filePath)
                if (f.exists()) {
                    _transcribeVideoFile.value = f
                }
            }
            if (!task.fileUri.isNullOrBlank()) {
                try {
                    _transcribeVideoUri.value = Uri.parse(task.fileUri)
                } catch (e: Exception) {
                    Log.w("DocumentViewModel", "Error parsing uri: ${e.message}")
                }
            }
            _transcribeVideoName.value = task.title
            _transcribeVideoDurationMs.value = task.durationMs
            _transcribeVideoSizeBytes.value = task.fileSizeBytes
            _transcribeTargetLang.value = task.targetLanguage

            if (task.srtContent.isNotBlank()) {
                _transcribedSrtContent.value = task.srtContent
                val parsed = SubtitleParser.parseSrtOrVtt(task.srtContent)
                _transcribedSubtitles.value = parsed
                _transcribeStatusText.value = "Restored previous task (${parsed.size} subtitles)"
            }
            _savedSubtitleFilePath.value = task.savedOutputPath

            if (auto) {
                _restoredSessionBanner.value = task
            } else {
                _toastMessage.emit("Restored '${task.title}' from history!")
            }
        }
    }

    fun dismissRestoredBanner() {
        _restoredSessionBanner.value = null
    }

    fun deleteHistoryTask(taskId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                mediaTaskHistoryDao.deleteTaskById(taskId)
                if (_currentActiveHistoryTaskId.value == taskId) {
                    _currentActiveHistoryTaskId.value = null
                    prefs.edit().remove("pref_last_active_task_id").apply()
                }
                _toastMessage.emit("Removed from history")
            } catch (e: Exception) {
                Log.w("DocumentViewModel", "Error deleting history: ${e.message}")
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                mediaTaskHistoryDao.clearAll()
                _currentActiveHistoryTaskId.value = null
                prefs.edit().remove("pref_last_active_task_id").apply()
                _toastMessage.emit("All history cleared")
            } catch (e: Exception) {
                Log.w("DocumentViewModel", "Error clearing history: ${e.message}")
            }
        }
    }

    fun sendHistoryTaskToDubbing(task: MediaTaskHistory): Boolean {
        if (task.srtContent.isBlank()) return false
        val subs = SubtitleParser.parseSrtOrVtt(task.srtContent)
        val videoFile = task.filePath?.let { File(it) }?.takeIf { it.exists() }
        val videoUri = task.fileUri?.let { Uri.parse(it) }

        val item = BatchDubItem(
            videoUri = videoUri,
            videoFile = videoFile,
            videoName = task.title,
            sourceDirectory = videoFile?.parentFile,
            srtName = "${task.title.substringBeforeLast('.')}.srt",
            srtContent = task.srtContent,
            srtSubtitles = subs,
            status = "Ready for Dubbing (${subs.size} entries)"
        )
        val current = _batchItems.value.toMutableList()
        if (current.size == 1 && current[0].videoFile == null && current[0].srtSubtitles.isEmpty()) {
            _batchItems.value = listOf(item)
        } else {
            current.add(item)
            _batchItems.value = current
        }
        viewModelScope.launch { _toastMessage.emit("Loaded '${task.title}' into Dubbing Studio!") }
        return true
    }

    fun sendTranscribedToDubbing(): Boolean {
        val videoFile = _transcribeVideoFile.value
        val videoUri = _transcribeVideoUri.value
        val srtContent = _transcribedSrtContent.value
        val subs = _transcribedSubtitles.value
        if ((videoFile == null && videoUri == null) || srtContent.isBlank()) return false

        val baseName = _transcribeVideoName.value.ifBlank { "video" }.substringBeforeLast('.')
        val item = BatchDubItem(
            videoUri = videoUri,
            videoFile = videoFile,
            videoName = _transcribeVideoName.value,
            sourceDirectory = videoFile?.parentFile,
            srtName = "$baseName.srt",
            srtContent = srtContent,
            srtSubtitles = subs,
            status = "Ready for Dubbing (${subs.size} entries)"
        )
        _batchItems.value = listOf(item)
        viewModelScope.launch { _toastMessage.emit("Loaded into Dubbing Studio!") }
        return true
    }

    fun sendTranscribedToTranslator(): Boolean {
        val srtContent = _transcribedSrtContent.value
        val subs = _transcribedSubtitles.value
        if (srtContent.isBlank()) return false

        val baseName = _transcribeVideoName.value.ifBlank { "video" }.substringBeforeLast('.')
        _standaloneSrtFileName.value = "$baseName.srt"
        _standaloneOriginalSubtitles.value = subs
        _standaloneTranslatedSubtitles.value = emptyList()
        viewModelScope.launch { _toastMessage.emit("Loaded into Subtitle Translator!") }
        return true
    }

    fun loadDubberV2Video(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _v2Status.value = "Loading video..."
                val name = getFileNameFromUri(uri)
                val cleanName = if (name.contains("/")) name.substringAfterLast("/") else name
                val realFile = resolveRealFileFromUri(uri)
                val videoFile = if (realFile != null && realFile.exists() && realFile.canRead()) {
                    realFile
                } else {
                    val temp = File(getApplication<Application>().cacheDir, "v2_video_${System.currentTimeMillis()}.mp4")
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(temp).use { output -> input.copyTo(output) }
                    }
                    if (temp.exists() && temp.length() > 0) temp else null
                }
                if (videoFile == null) {
                    _v2Status.value = "Could not open the video"
                    _toastMessage.emit("Cannot open that video")
                    return@launch
                }
                val previous = _v2VideoFile.value
                if (previous != null && previous.absolutePath != videoFile.absolutePath) {
                    deleteIfAppOwnedCache(previous)
                }
                _v2VideoFile.value = videoFile
                _v2VideoName.value = cleanName
                _v2OutputFile.value = null
                _v2Status.value = if (_v2Cues.value.isEmpty()) "Video loaded. Add a subtitle." else "Ready to review"
                _toastMessage.emit("Video loaded")
            } catch (e: Exception) {
                _v2Status.value = "Video error"
                _toastMessage.emit("Video load error: ${e.message}")
            }
        }
    }

    fun loadDubberV2Subtitle(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _v2Status.value = "Reading subtitle..."
                val name = getFileNameFromUri(uri)
                val cleanName = if (name.contains("/")) name.substringAfterLast("/") else name
                val content = getApplication<Application>().contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                val entries = SubtitleParser.parseSrtOrVtt(content)
                if (entries.isEmpty()) {
                    _v2Status.value = "No subtitle blocks found"
                    _toastMessage.emit("No valid subtitle timestamps found")
                    return@launch
                }
                val sample = entries.take(15).joinToString(" ") { it.text }
                val detected = LanguageDetector.detectLanguage(sample)
                val voice = VoiceCast.defaultSpeakerId(detected.code)
                _v2DefaultVoice.value = voice
                val fresh = entries.map { entry ->
                    DubCue(
                        index = entry.index,
                        startTimeMs = entry.startTimeMs,
                        endTimeMs = entry.endTimeMs,
                        text = entry.text,
                        voiceName = voice
                    )
                }
                // Locked blocks keep their stored text; pinned voices survive.
                _v2Cues.value = restoreDubberV2States(fresh)
                _v2SubtitleName.value = cleanName
                _v2OutputFile.value = null
                _v2Status.value = "${entries.size} blocks • ${detected.name}. Default speaker is a young man."
                _toastMessage.emit("Loaded ${entries.size} subtitle blocks")
            } catch (e: Exception) {
                _v2Status.value = "Subtitle error"
                _toastMessage.emit("Subtitle load error: ${e.message}")
            }
        }
    }

    fun updateDubberV2CueText(cueId: String, text: String) {
        val cue = _v2Cues.value.firstOrNull { it.id == cueId } ?: return
        if (cue.locked) {
            viewModelScope.launch {
                _toastMessage.emit("🔒 This block is locked — unlock it to edit")
            }
            return
        }
        _v2Cues.value = _v2Cues.value.map { if (it.id == cueId) it.copy(text = text) else it }
    }

    /** Toggle the lock on a block. Locking freezes its text (even across restarts). */
    fun toggleDubberV2CueLock(cueId: String) {
        _v2Cues.value = _v2Cues.value.map {
            if (it.id == cueId) {
                val next = it.copy(locked = !it.locked)
                persistCueState(next)
                next
            } else it
        }
        val now = _v2Cues.value.firstOrNull { it.id == cueId }
        viewModelScope.launch {
            if (now?.locked == true) _toastMessage.emit("🔒 Block ${now.index} locked — text is frozen")
            else _toastMessage.emit("🔓 Block ${now?.index} unlocked")
        }
    }

    fun setAllDubberV2CuesLocked(locked: Boolean) {
        val updated = _v2Cues.value.map { it.copy(locked = locked) }
        _v2Cues.value = updated
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val key = v2ProjectKey()
                if (locked) {
                    updated.forEach { cue ->
                        cueStateDao.upsert(
                            DubCueState(
                                stateKey = "$key#${cue.index}",
                                videoKey = key,
                                cueIndex = cue.index,
                                locked = true,
                                textOverride = cue.text,
                                voiceName = if (cue.voicePinned) cue.voiceName else null,
                                voicePinned = cue.voicePinned
                            )
                        )
                    }
                } else {
                    // Unlock all: drop rows that were lock-only (keep pinned voices).
                    updated.forEach { cue -> persistCueStateBlocking(cue) }
                }
            } catch (e: Exception) {
                android.util.Log.w("BalaSpeakV2", "bulk lock failed: ${e.message}")
            }
        }
        viewModelScope.launch {
            _toastMessage.emit(if (locked) "🔒 All blocks locked" else "🔓 All blocks unlocked")
        }
    }

    /** Assign a voice to one block and pin it (survives reload + restart). */
    fun updateDubberV2CueVoice(cueId: String, voiceName: String?, pin: Boolean = true) {
        _v2Cues.value = _v2Cues.value.map {
            if (it.id == cueId) {
                val next = it.copy(voiceName = voiceName, voicePinned = pin)
                persistCueState(next)
                next
            } else it
        }
    }

    fun toggleDubberV2CueVoicePin(cueId: String) {
        _v2Cues.value = _v2Cues.value.map {
            if (it.id == cueId) {
                val next = it.copy(voicePinned = !it.voicePinned)
                persistCueState(next)
                next
            } else it
        }
    }

    /** Mass-assign. skipPinned=true keeps deliberately pinned blocks untouched. */
    fun applyDubberV2VoiceToAll(voiceName: String?, skipPinned: Boolean = false) {
        _v2DefaultVoice.value = voiceName
        _v2Cues.value = _v2Cues.value.map {
            if (skipPinned && it.voicePinned) it
            else {
                val next = it.copy(voiceName = voiceName, voicePinned = false)
                persistCueState(next)
                next
            }
        }
        viewModelScope.launch {
            _toastMessage.emit(
                if (skipPinned) "Voice set on all blocks except pinned ones"
                else "Voice set on every block (pins cleared)"
            )
        }
    }

    private fun persistCueStateBlocking(cue: DubCue) {
        try {
            if (cue.locked || cue.voicePinned) {
                cueStateDao.upsert(
                    DubCueState(
                        stateKey = cueStateKey(cue),
                        videoKey = v2ProjectKey(),
                        cueIndex = cue.index,
                        locked = cue.locked,
                        textOverride = if (cue.locked) cue.text else null,
                        voiceName = if (cue.voicePinned) cue.voiceName else null,
                        voicePinned = cue.voicePinned
                    )
                )
            } else {
                cueStateDao.deleteByKey(cueStateKey(cue))
            }
        } catch (e: Exception) {
            android.util.Log.w("BalaSpeakV2", "persist failed: ${e.message}")
        }
    }

    fun setDubberV2VideoVolume(volume: Float) {
        _v2VideoVolume.value = volume.coerceIn(0f, 1f)
    }

    fun setDubberV2TtsVolume(volume: Float) {
        _v2TtsVolume.value = volume.coerceIn(0f, 2f)
    }

    fun previewDubberV2Cue(cueId: String) {
        val cue = _v2Cues.value.firstOrNull { it.id == cueId } ?: return
        ttsManager.previewBlock(cue.text, cue.voiceName ?: _v2DefaultVoice.value, globalSpeed.value, globalPitch.value)
    }

    fun saveDubberV2Srt() {
        val cues = _v2Cues.value
        if (cues.isEmpty()) {
            viewModelScope.launch { _toastMessage.emit("Load a subtitle first") }
            return
        }
        val entries = cues.map { SubtitleEntry(it.index, it.startTimeMs, it.endTimeMs, it.text) }
        val base = _v2SubtitleName.value.substringBeforeLast(".").ifBlank { "dubber_v2" }
        shareSrt(SubtitleTranslator.exportToSrt(entries), "${base}_edited.srt")
    }

    fun shareDubberV2Output() {
        val file = _v2OutputFile.value ?: return
        if (!file.exists()) return
        try {
            val uri = FileProvider.getUriForFile(getApplication(), fileProviderAuthority(), file)
            val mime = when (file.extension.lowercase()) {
                "m4a", "aac" -> "audio/mp4"
                "mkv" -> "video/x-matroska"
                "webm" -> "video/webm"
                else -> "video/mp4"
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            getApplication<Application>().startActivity(
                Intent.createChooser(intent, "Share dubbed video").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            viewModelScope.launch { _toastMessage.emit("Could not share the file") }
        }
    }

    fun renderDubberV2() {
        if (_isGlobalProcessing.value || _v2Rendering.value) {
            viewModelScope.launch { _toastMessage.emit("Wait until the current job finishes") }
            return
        }
        val videoFile = _v2VideoFile.value
        val cues = _v2Cues.value
        if (videoFile == null || !videoFile.exists() || cues.isEmpty()) {
            viewModelScope.launch { _toastMessage.emit("Load a video and a subtitle first") }
            return
        }
        _isGlobalProcessing.value = true
        _v2Rendering.value = true
        _v2ProgressCount.value = 0 to cues.size
        _v2Progress.value = "Preparing voices..."
        v2RenderJob = trackJob(viewModelScope.launch(Dispatchers.IO) {
            val tempTtsWav = File(getApplication<Application>().cacheDir, "v2_tts_${System.currentTimeMillis()}.wav")
            val tempMux = File(getApplication<Application>().cacheDir, "v2_mux_${System.currentTimeMillis()}.mp4")
            try {
                startBackgroundEngine("BalaSpeak Dubber V2", "Rendering block voices...")
                val entries = cues.map { SubtitleEntry(it.index, it.startTimeMs, it.endTimeMs, it.text) }
                val voices = cues.map { it.voiceName ?: _v2DefaultVoice.value }
                val nm = NeuralVoiceManager.get(getApplication())
                val missing = voices.mapNotNull { nm.neuralRoleOf(it)?.first }
                    .distinct().filter { !nm.isReady(it) }
                if (missing.isNotEmpty()) {
                    _v2Progress.value = "⚠ \u09AA\u09CD\u09AF\u09BE\u0995 \u09A8\u09C7\u0987 ($missing) — \u09B8\u09BF\u09B8\u09CD\u099F\u09C7\u09AE \u0995\u09A3\u09CD\u09A0\u09C7 \u09AF\u09BE\u099A\u09CD\u099B\u09C7"
                    _toastMessage.emit("🎙 \u09AD\u09AF\u09BC\u09C7\u09B8 \u09AA\u09CD\u09AF\u09BE\u0995 \u09A1\u09BE\u0989\u09A8\u09B2\u09CB\u09A1 \u09A8\u09C7\u0987 ($missing) — Settings \u2192 AI Voice Packs")
                }
                val spoke = ttsManager.compileSubtitlesToWav(
                    subtitles = entries,
                    destWavFile = tempTtsWav,
                    speed = globalSpeed.value,
                    pitch = globalPitch.value,
                    voiceName = _v2DefaultVoice.value,
                    autoFitDuration = true,
                    perCueVoices = voices
                ) { done, total ->
                    _v2ProgressCount.value = done to total
                    _v2Progress.value = "Speaking block $done / $total"
                    updateBackgroundEngine("Dubber V2 $done/$total", done, total)
                }
                if (!spoke || !tempTtsWav.exists() || tempTtsWav.length() < 44) {
                    _v2Progress.value = "Voice generation failed"
                    _toastMessage.emit("Voice synthesis failed. Check the block voices.")
                    return@launch
                }
                _v2Progress.value = "Mixing voice into the video..."
                val mixed = AudioVideoMerger.mixAndEncodeDubbedMedia(
                    videoFile = videoFile,
                    ttsWavFile = tempTtsWav,
                    targetOutputFile = tempMux,
                    videoAudioVolume = _v2VideoVolume.value,
                    ttsAudioVolume = _v2TtsVolume.value,
                    preferHardwareEncoder = _hardwareAcceleration.value,
                    ioBufferBytes = mixIoBufferBytes()
                ) { message -> _v2Progress.value = message }
                if (!mixed || !tempMux.exists() || tempMux.length() == 0L) {
                    _v2Progress.value = "Mixing failed"
                    _toastMessage.emit("Could not mix the dubbed video")
                    return@launch
                }
                val base = _v2VideoName.value.substringBeforeLast(".").ifBlank { "dubber_v2" }
                val saved = saveDubberV2Output(tempMux, "${base}_v2.mp4")
                _v2OutputFile.value = saved
                _v2Progress.value = "Saved ${saved.name}"
                _v2Status.value = "Dubbed video ready"
                _toastMessage.emit("Dubber V2 video saved")
            } catch (e: kotlinx.coroutines.CancellationException) {
                _v2Progress.value = "Stopped"
                _toastMessage.emit("Dubber V2 stopped")
                throw e
            } catch (e: Exception) {
                Log.e("BalaSpeakTTS", "Dubber V2 render failed: ${e.message}", e)
                _v2Progress.value = "Render failed"
                _toastMessage.emit("Dubber V2 failed: ${e.message}")
            } finally {
                try { tempTtsWav.delete() } catch (e: Exception) {}
                try { if (tempMux.exists()) tempMux.delete() } catch (e: Exception) {}
                _v2Rendering.value = false
                _isGlobalProcessing.value = false
                finishBackgroundEngine()
            }
        })
    }

    private fun saveDubberV2Output(tempMux: File, fileName: String): File {
        val sourceDir = _v2VideoFile.value?.parentFile
        val writableSource = sourceDir != null && sourceDir.exists() && sourceDir.canWrite() &&
            sourceDir != getApplication<Application>().cacheDir
        val targetDir = if (writableSource) sourceDir!! else getEffectiveOutputDir()
        if (targetDir.exists() && targetDir.canWrite()) {
            val candidate = File(targetDir, fileName)
            try {
                tempMux.copyTo(candidate, overwrite = true)
                if (candidate.exists() && candidate.length() > 0) {
                    MediaScannerConnection.scanFile(getApplication(), arrayOf(candidate.absolutePath), null, null)
                    return candidate
                }
            } catch (e: Exception) {
                Log.w("BalaSpeakTTS", "Dubber V2 direct save failed: ${e.message}")
            }
        }
        val fallback = File(getApplication<Application>().cacheDir, fileName)
        tempMux.copyTo(fallback, overwrite = true)
        return fallback
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.shutdown()
    }
}
