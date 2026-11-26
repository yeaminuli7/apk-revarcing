package id.vern.antigravity.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import id.vern.antigravity.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * VoiceController - always-on voice control for the agent.
 *
 * Listens continuously with [SpeechRecognizer] (on-device recognition where the phone
 * supports it), waits for the wake word, then treats the following words as a command:
 *
 *   "Hey Antigravity, open WhatsApp"
 *   "Antigravity, scroll down"
 *
 * Speech results are also spoken back through [TextToSpeech] so the agent can answer
 * hands-free while the user is looking at another screen.
 */
object VoiceController {

    private const val TAG = "VoiceController"

    /** Words that arm the listener. Keep them short and phonetically distinct. */
    private val WAKE_WORDS = listOf(
        "hey antigravity", "hi antigravity", "ok antigravity", "antigravity",
        "hey anti gravity", "antigravity", "এন্টিগ্র্যাভিটি", "অ্যান্টিগ্র্যাভিটি"
    )

    data class VoiceState(
        val listening: Boolean = false,
        val speaking: Boolean = false,
        val awake: Boolean = false,
        val lastHeard: String = "",
        val error: String? = null
    )

    private val mutableState = MutableStateFlow(VoiceState())
    val state: StateFlow<VoiceState> = mutableState.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private val main = Handler(Looper.getMainLooper())

    /** Set by the floating bubble service; receives the command text. */
    @Volatile
    var onCommand: ((String) -> Unit)? = null

    private var wantListening = false
    private var ttsReady = false
    private var restartPending = false
    private var languageTag: String = "en-US"

    // ---------------------------------------------------------------- lifecycle

    fun start(context: Context, language: String = "en-US") {
        val app = context.applicationContext
        languageTag = language
        wantListening = true
        initTts(app)
        if (!SpeechRecognizer.isRecognitionAvailable(app)) {
            mutableState.value = VoiceState(error = "Speech recognition is not available on this device")
            AppLogger.e(TAG, "SpeechRecognizer unavailable")
            return
        }
        createRecognizer(app)
        mutableState.value = mutableState.value.copy(listening = true, error = null)
        AppLogger.i(TAG, "Voice control started")
    }

    fun stop() {
        wantListening = false
        restartPending = false
        try { recognizer?.destroy() } catch (e: Exception) {}
        recognizer = null
        mutableState.value = VoiceState(listening = false, speaking = false)
        AppLogger.i(TAG, "Voice control stopped")
    }

    fun isListening(): Boolean = wantListening

    private fun createRecognizer(context: Context) {
        try { recognizer?.destroy() } catch (e: Exception) {}
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        r.setRecognitionListener(listener)
        recognizer = r
        listenOnce(context)
    }

    private fun listenOnce(context: Context) {
        val r = recognizer ?: return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
            // Prefer on-device recognition when the phone supports it (works offline).
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            }
        }
        try {
            r.startListening(intent)
        } catch (e: Exception) {
            AppLogger.e(TAG, "startListening failed: ${e.message}")
            scheduleRestart(context, 1500)
        }
    }

    /** Restarts recognition politely: some devices throw if called too fast. */
    private fun scheduleRestart(context: Context, delayMs: Long = 600) {
        if (!wantListening || restartPending) return
        restartPending = true
        main.postDelayed({
            restartPending = false
            if (wantListening) {
                if (recognizer == null) createRecognizer(context) else listenOnce(context)
            }
        }, delayMs)
    }

    // ----------------------------------------------------------------- listener

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {}

        override fun onError(error: Int) {
            val msg = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "no match"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "timeout"
                SpeechRecognizer.ERROR_AUDIO -> "audio error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "microphone permission missing"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "recognizer busy"
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network error"
                else -> "error $error"
            }
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                mutableState.value = mutableState.value.copy(error = msg)
                wantListening = false
                return
            }
            // Everything else is normal in continuous mode: just listen again.
            main.postDelayed({
                if (wantListening) {
                    val ctx = appContext
                    if (ctx != null) listenOnce(ctx)
                }
            }, 400)
        }

        override fun onResults(results: Bundle?) {
            val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            handleHeard(texts.firstOrNull().orEmpty())
            val ctx = appContext
            if (ctx != null) scheduleRestart(ctx, 300)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val texts = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val heard = texts.firstOrNull().orEmpty()
            mutableState.value = mutableState.value.copy(lastHeard = heard)
            // Fast path: fire as soon as the wake word plus a command is recognised.
            if (heard.isNotBlank()) maybeDispatch(heard, final = false)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private var appContext: Context? = null

    private fun handleHeard(heard: String) {
        mutableState.value = mutableState.value.copy(lastHeard = heard)
        maybeDispatch(heard, final = true)
    }

    private var lastDispatchText = ""
    private var lastDispatchAt = 0L

    private fun maybeDispatch(heard: String, final: Boolean) {
        val lower = heard.lowercase(Locale.ROOT)
        val wake = WAKE_WORDS.firstOrNull { lower.contains(it) } ?: return

        var command = heard.substring(heard.lowercase(Locale.ROOT).indexOf(wake) + wake.length)
        command = command.trim().trimStart(',', '.', '!', '?', ' ')
        if (command.isBlank()) {
            mutableState.value = mutableState.value.copy(awake = true)
            speak("Yes?")
            return
        }
        if (command.length < 2) return

        val now = System.currentTimeMillis()
        // Partial and final results repeat the same phrase; dedupe.
        if (command == lastDispatchText && now - lastDispatchAt < 4000) return
        lastDispatchText = command
        lastDispatchAt = now

        mutableState.value = mutableState.value.copy(awake = true, lastHeard = heard)
        AppLogger.i(TAG, "Voice command: $command")
        val handler = onCommand
        if (handler != null) {
            main.post { handler(command) }
        } else {
            AppLogger.w(TAG, "No command handler registered")
        }
    }

    // ---------------------------------------------------------------------- tts

    private fun initTts(context: Context) {
        if (tts != null) return
        appContext = context
        tts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                try {
                    tts?.language = Locale.forLanguageTag(languageTag)
                } catch (e: Exception) {
                }
                AppLogger.i(TAG, "TTS ready")
            } else {
                AppLogger.w(TAG, "TTS init failed")
            }
        }
    }

    /** Speaks a short answer aloud. No-op when TTS is unavailable. */
    fun speak(text: String) {
        if (text.isBlank()) return
        val engine = tts
        if (engine == null || !ttsReady) return
        val clipped = if (text.length > 400) text.take(400) + "…" else text
        mutableState.value = mutableState.value.copy(speaking = true)
        engine.speak(clipped, TextToSpeech.QUEUE_FLUSH, null, "agy-voice")
    }

    fun shutdown() {
        stop()
        try { tts?.shutdown() } catch (e: Exception) {}
        tts = null
        ttsReady = false
    }
}
