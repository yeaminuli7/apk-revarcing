package id.vern.antigravity.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import id.vern.antigravity.R
import id.vern.antigravity.accessibility.ScreenControlService
import id.vern.antigravity.ui.MainActivity
import id.vern.antigravity.util.AppLogger
import id.vern.antigravity.vision.ScreenAgent
import id.vern.antigravity.vision.ScreenCaptureManager
import id.vern.antigravity.voice.VoiceController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * AssistantBubbleService - a floating bubble that stays on top of every other app, so the
 * agent can be started, stopped and talked to without leaving what the user is doing.
 *
 * Tap the bubble: open a small command bar (typing + voice).
 * Drag the bubble: move it anywhere (position is remembered).
 * Long press: quick menu (stop agent, toggle voice, hide bubble).
 */
class AssistantBubbleService : Service() {

    companion object {
        private const val TAG = "AssistantBubble"
        private const val CHANNEL_ID = "agy_bubble_channel"
        private const val NOTIFICATION_ID = 1002
        private const val PREFS = "antigravity_bubble_prefs"
        private const val KEY_X = "bubble_x"
        private const val KEY_Y = "bubble_y"

        const val ACTION_SHOW = "id.vern.antigravity.action.BUBBLE_SHOW"
        const val ACTION_HIDE = "id.vern.antigravity.action.BUBBLE_HIDE"

        @Volatile
        private var isRunning = false

        fun isShowing(): Boolean = isRunning

        fun show(context: Context) {
            val intent = Intent(context, AssistantBubbleService::class.java).apply {
                action = ACTION_SHOW
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun hide(context: Context) {
            context.startService(
                Intent(context, AssistantBubbleService::class.java).apply { action = ACTION_HIDE }
            )
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var commandBar: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var stateJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundNotification()
        VoiceController.onCommand = { command -> runCommand(command) }
        scope.launch {
            VoiceController.state.collectLatest { state ->
                updateBubbleTint(state.listening, state.speaking, state.awake)
            }
        }
        AppLogger.d(TAG, "Bubble service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> {
                removeBubble()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> showBubble()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        VoiceController.onCommand = null
        removeBubble()
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------- notification

    private fun startForegroundNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Floating assistant", NotificationManager.IMPORTANCE_MIN)
                    .apply { setShowBadge(false) }
            )
        }
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val hide = PendingIntent.getService(
            this, 1,
            Intent(this, AssistantBubbleService::class.java).apply { action = ACTION_HIDE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Floating assistant is on")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(open)
            .addAction(R.drawable.ic_action_stop, "Hide bubble", hide)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    // ------------------------------------------------------------------- bubble

    private fun bubbleParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        return WindowManager.LayoutParams(
            dp(56), dp(56), type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt(KEY_X, 0)
            y = prefs.getInt(KEY_Y, 320)
        }
    }

    private fun showBubble() {
        if (bubbleView != null) return
        val params = bubbleParams()
        bubbleParams = params

        val bubble = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF1F2937.toInt())
                setStroke(dp(2), 0xFF34D399.toInt())
            }
            elevation = dp(10).toFloat()
        }
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_notification)
            setColorFilter(0xFF34D399.toInt())
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        bubble.addView(icon, FrameLayout.LayoutParams(-1, -1))

        bubble.setOnTouchListener(object : View.OnTouchListener {
            private var downX = 0f
            private var downY = 0f
            private var startX = 0
            private var startY = 0
            private var dragged = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        startX = params.x
                        startY = params.y
                        dragged = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downX
                        val dy = event.rawY - downY
                        if (kotlin.math.abs(dx) > dp(8) || kotlin.math.abs(dy) > dp(8)) dragged = true
                        if (dragged) {
                            params.x = (startX + dx).toInt()
                            params.y = (startY + dy).toInt()
                            try { windowManager.updateViewLayout(bubble, params) } catch (e: Exception) {}
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (dragged) {
                            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                                .putInt(KEY_X, params.x)
                                .putInt(KEY_Y, params.y)
                                .apply()
                        } else {
                            openCommandBar()
                        }
                        return true
                    }
                }
                return false
            }
        })

        bubble.setOnLongClickListener {
            toggleVoice()
            true
        }

        try {
            windowManager.addView(bubble, params)
            bubbleView = bubble
            isRunning = true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Could not add bubble overlay: ${e.message}")
            Toast.makeText(this, "Overlay permission is required", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun removeBubble() {
        bubbleView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {}
        }
        bubbleView = null
        removeCommandBar()
        isRunning = false
    }

    private fun updateBubbleTint(listening: Boolean, speaking: Boolean, awake: Boolean) {
        val bubble = bubbleView as? FrameLayout ?: return
        val color = when {
            speaking -> 0xFF3B82F6.toInt()   // blue: talking
            awake -> 0xFFF59E0B.toInt()      // amber: heard the wake word
            listening -> 0xFF34D399.toInt()  // green: listening
            else -> 0xFF6B7280.toInt()       // grey: idle
        }
        try {
            (bubble.background as? GradientDrawable)?.setStroke(dp(2), color)
        } catch (e: Exception) {}
    }

    // --------------------------------------------------------------- command UI

    private fun openCommandBar() {
        if (commandBar != null) {
            removeCommandBar()
            return
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(0xF2181B1F.toInt())
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), 0xFF34D399.toInt())
            }
        }

        val input = EditText(this).apply {
            hint = "Ask Antigravity…"
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF9CA3AF.toInt())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine()
            background = null
        }
        row.addView(input, LinearLayout.LayoutParams(0, -2, 1f))

        val mic = TextView(this).apply {
            text = "🎤"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener {
                toggleVoice()
                val on = VoiceController.state.value.listening
                text = if (on) "🎤" else "🔇"
            }
        }
        row.addView(mic)

        val send = TextView(this).apply {
            text = "➤"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(0xFF34D399.toInt())
            setPadding(dp(10), dp(6), dp(6), dp(6))
            setOnClickListener {
                val text = input.text?.toString()?.trim().orEmpty()
                if (text.isNotEmpty()) {
                    runCommand(text)
                    input.setText("")
                    removeCommandBar()
                }
            }
        }
        row.addView(send)

        try {
            windowManager.addView(row, params)
            commandBar = row
            input.requestFocus()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Could not add command bar: ${e.message}")
        }
    }

    private fun removeCommandBar() {
        commandBar?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {}
        }
        commandBar = null
    }

    // ----------------------------------------------------------------- commands

    /**
     * Every voice utterance and typed command arrives here. Local keywords are handled
     * instantly; anything else is handed to the screen agent as a goal.
     */
    private fun runCommand(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        val lower = text.lowercase()

        when {
            lower == "stop" || lower == "cancel" || lower.contains("stop agent") -> {
                ScreenAgent.stop()
                VoiceController.speak("Stopped.")
                return
            }
            lower == "hide" || lower.contains("hide bubble") -> {
                VoiceController.speak("Hiding.")
                removeBubble()
                stopSelf()
                return
            }
            lower == "home" || lower.contains("go home") -> {
                ScreenControlService.getInstance()?.pressHome()
                return
            }
            lower == "back" || lower.contains("go back") -> {
                ScreenControlService.getInstance()?.pressBack()
                return
            }
        }

        if (ScreenControlService.getInstance() == null) {
            VoiceController.speak("The accessibility service is off. Please enable it in settings.")
            Toast.makeText(this, "Enable the Antigravity accessibility service", Toast.LENGTH_LONG).show()
            return
        }
        if (!ScreenCaptureManager.isReady()) {
            VoiceController.speak("Allow screen sharing in the app first, then try again.")
            Toast.makeText(this, "Open Antigravity and allow screen sharing once", Toast.LENGTH_LONG).show()
            return
        }

        val metrics = resources.displayMetrics
        ScreenAgent.start(
            context = this,
            scope = scope,
            goal = text,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            onLog = { line -> AppLogger.d(TAG, line) }
        )

        // Speak the outcome when the run finishes so it works hands-free.
        stateJob?.cancel()
        stateJob = scope.launch {
            ScreenAgent.state.collectLatest { state ->
                if (state.finished) {
                    val msg = when {
                        !state.summary.isBlank() -> state.summary
                        state.error != null -> state.error ?: "I could not finish that."
                        else -> "Done."
                    }
                    VoiceController.speak(msg)
                    stateJob?.cancel()
                } else if (state.running && state.step == 1) {
                    VoiceController.speak("Working on it.")
                }
            }
        }
    }

    private fun toggleVoice() {
        if (VoiceController.state.value.listening) {
            VoiceController.stop()
            Toast.makeText(this, "Voice off", Toast.LENGTH_SHORT).show()
        } else {
            VoiceController.start(this)
            Toast.makeText(this, "Voice on — say \"Hey Antigravity\"", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
