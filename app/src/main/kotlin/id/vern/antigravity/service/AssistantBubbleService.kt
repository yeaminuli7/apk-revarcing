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
import android.widget.ScrollView
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * AssistantBubbleService - a small "chat head" that floats above every app, like the
 * Messenger bubble: a compact circle that can be dragged around, and a tap opens a
 * chat sheet with the agent (text + voice + typing indicator).
 *
 * The sheet keeps its own short transcript so a conversation started over another app
 * still reads like a chat, and the same exchange is mirrored into the main app's
 * conversation history.
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

        /** Compact chat-head size, matching the Messenger bubble feel. */
        private const val BUBBLE_DP = 48

        @Volatile
        private var isRunning = false

        fun isShowing(): Boolean = isRunning

        fun canDrawOverlays(context: Context): Boolean =
            android.provider.Settings.canDrawOverlays(context)

        fun openOverlayPermission(context: Context) {
            try {
                context.startActivity(
                    Intent(
                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:${context.packageName}")
                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                )
            } catch (e: Exception) {
            }
        }

        fun show(context: Context) {
            if (!canDrawOverlays(context)) {
                Toast.makeText(context, "Allow \"Display over other apps\" first", Toast.LENGTH_LONG).show()
                openOverlayPermission(context)
                return
            }
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
    private var sheetView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var stateJob: Job? = null

    /** Mini transcript for the floating sheet: role to text. */
    private val sheetMessages = mutableListOf<Pair<String, String>>()
    private var sheetList: LinearLayout? = null
    private var sheetScroll: ScrollView? = null
    private var bubbleBadge: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundNotification()
        VoiceController.onCommand = { command ->
            // A spoken command opens the floating chat so the user sees the reply.
            handleUserInput(command, openSheetIfClosed = true)
        }
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
            else -> {
                showBubble()
                autoStartVoice()
            }
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
            dp(BUBBLE_DP), dp(BUBBLE_DP), type,
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

        // Chat-head look: a clean circle with the app glyph and a small online dot.
        val bubble = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF111827.toInt())
                setStroke(dp(2), 0xFF34D399.toInt())
            }
            elevation = dp(8).toFloat()
        }
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_notification)
            setColorFilter(0xFF34D399.toInt())
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        bubble.addView(icon, FrameLayout.LayoutParams(-1, -1))

        val badge = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF34D399.toInt())
            }
        }
        val badgeParams = FrameLayout.LayoutParams(dp(10), dp(10)).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = dp(2)
            marginEnd = dp(2)
        }
        bubble.addView(badge, badgeParams)
        bubbleBadge = badge

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
                        v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(90).start()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downX
                        val dy = event.rawY - downY
                        if (kotlin.math.abs(dx) > dp(6) || kotlin.math.abs(dy) > dp(6)) dragged = true
                        if (dragged) {
                            params.x = (startX + dx).toInt()
                            params.y = (startY + dy).toInt()
                            try { windowManager.updateViewLayout(bubble, params) } catch (e: Exception) {}
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
                        if (dragged) {
                            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                                .putInt(KEY_X, params.x)
                                .putInt(KEY_Y, params.y)
                                .apply()
                        } else {
                            toggleSheet()
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
        removeSheet()
        isRunning = false
    }

    private fun updateBubbleTint(listening: Boolean, speaking: Boolean, awake: Boolean) {
        val bubble = bubbleView as? FrameLayout ?: return
        val color = when {
            speaking -> 0xFF3B82F6.toInt()
            awake -> 0xFFF59E0B.toInt()
            listening -> 0xFF34D399.toInt()
            else -> 0xFF6B7280.toInt()
        }
        try {
            (bubble.background as? GradientDrawable)?.setStroke(dp(2), color)
            (bubbleBadge?.background as? GradientDrawable)?.setColor(color)
        } catch (e: Exception) {}
    }

    // -------------------------------------------------------------- chat sheet

    private fun toggleSheet() {
        if (sheetView != null) {
            removeSheet()
        } else {
            openSheet()
        }
    }

    /**
     * A Messenger-style conversation card: scrollable transcript, a typing indicator
     * while the agent works, a text field and a mic button for voice input.
     */
    private fun openSheet() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            dp(360),
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = GradientDrawable().apply {
                setColor(0xF2171A1F.toInt())
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), 0xFF34D399.toInt())
            }
        }

        // header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "Antigravity"
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        header.addView(TextView(this).apply {
            text = "✕"
            setTextColor(0xFF9CA3AF.toInt())
            setPadding(dp(10), 0, dp(4), 0)
            setOnClickListener { removeSheet() }
        })
        root.addView(header)

        // transcript
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        scroll.addView(list)
        root.addView(scroll)
        sheetList = list
        sheetScroll = scroll
        renderSheetMessages()

        // input row
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, 0)
        }
        val input = EditText(this).apply {
            hint = "Message Antigravity…"
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF9CA3AF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            maxLines = 4
            background = GradientDrawable().apply {
                setColor(0xFF23272E.toInt())
                cornerRadius = dp(18).toFloat()
            }
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        row.addView(input, LinearLayout.LayoutParams(0, -2, 1f))

        val mic = iconButton("🎤") {
            toggleVoice()
            Toast.makeText(
                this,
                if (VoiceController.state.value.listening) "Voice on — say \"Hey Antigravity\""
                else "Voice off",
                Toast.LENGTH_SHORT
            ).show()
        }
        row.addView(mic)

        val send = iconButton("➤") {
            val text = input.text?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) {
                input.setText("")
                submitFromSheet(text)
            }
        }
        send.setTextColor(0xFF34D399.toInt())
        row.addView(send)

        root.addView(row)

        try {
            windowManager.addView(root, params)
            sheetView = root
            input.requestFocus()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Could not add chat sheet: ${e.message}")
        }
    }

    private fun iconButton(label: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(0xFFE5E7EB.toInt())
            setPadding(dp(9), dp(6), dp(9), dp(6))
            setOnClickListener { onClick() }
        }

    private fun removeSheet() {
        sheetView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {}
        }
        sheetView = null
        sheetList = null
        sheetScroll = null
    }

    private fun appendSheetMessage(role: String, text: String) {
        sheetMessages.add(role to text)
        if (sheetMessages.size > 30) sheetMessages.removeAt(0)
        renderSheetMessages()
    }

    private fun renderSheetMessages() {
        val list = sheetList ?: return
        list.removeAllViews()
        if (sheetMessages.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "Ask me to do something on your phone — by text or voice."
                setTextColor(0xFF9CA3AF.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(0, dp(6), 0, dp(6))
            })
        }
        for ((role, text) in sheetMessages) {
            val isUser = role == "user"
            val bubble = TextView(this).apply {
                this.text = text
                setTextColor(if (isUser) 0xFFFFFFFF.toInt() else 0xFFE5E7EB.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(if (isUser) 0xFF2563EB.toInt() else 0xFF2B3038.toInt())
                }
                layoutParams = LinearLayout.LayoutParams(-2, -2).apply {
                    gravity = if (isUser) Gravity.END else Gravity.START
                    topMargin = dp(4)
                }
            }
            list.addView(bubble)
        }
        sheetScroll?.post { sheetScroll?.fullScroll(View.FOCUS_DOWN) }
    }

    /**
     * Entry point for anything the user says (voice) or types (sheet). Both paths land
     * here so the floating transcript always shows the command and the answer.
     */
    private fun handleUserInput(text: String, openSheetIfClosed: Boolean) {
        if (openSheetIfClosed && sheetView == null) openSheet()
        appendSheetMessage("user", text)
        appendSheetMessage("assistant", "…")
        runCommand(text)
    }

    private fun submitFromSheet(text: String) {
        handleUserInput(text, openSheetIfClosed = false)
    }

    // ----------------------------------------------------------------- commands

    private fun runCommand(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        val lower = text.lowercase()

        when {
            lower == "stop" || lower == "cancel" || lower.contains("stop agent") -> {
                ScreenAgent.stop()
                appendSheetMessage("assistant", "Stopped.")
                VoiceController.speak("Stopped.")
                return
            }
            lower == "hide" || lower.contains("hide bubble") -> {
                removeBubble()
                stopSelf()
                return
            }
            lower == "home" || lower.contains("go home") -> {
                ScreenControlService.getInstance()?.pressHome()
                appendSheetMessage("assistant", "Home.")
                return
            }
            lower == "back" || lower.contains("go back") -> {
                ScreenControlService.getInstance()?.pressBack()
                appendSheetMessage("assistant", "Back.")
                return
            }
        }

        if (ScreenControlService.getInstance() == null) {
            appendSheetMessage("assistant", "Enable the Antigravity accessibility service first.")
            VoiceController.speak("The accessibility service is off.")
            Toast.makeText(this, "Enable the Antigravity accessibility service", Toast.LENGTH_LONG).show()
            return
        }
        if (!ScreenCaptureManager.isReady()) {
            appendSheetMessage("assistant", "Allow screen sharing in the app once, then try again.")
            VoiceController.speak("Allow screen sharing in the app first.")
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
        stateJob?.cancel()
        stateJob = scope.launch {
            ScreenAgent.state.collectLatest { state ->
                when {
                    state.running && state.step > 0 -> {
                        // keep the transcript honest while it works
                        if (sheetMessages.lastOrNull()?.second == "…") {
                            sheetMessages[sheetMessages.size - 1] =
                                "assistant" to "Step ${state.step}: ${state.lastAction.ifBlank { "working" }}…"
                            renderSheetMessages()
                        }
                    }
                    state.finished -> {
                        val msg = when {
                            state.summary.isNotBlank() -> state.summary
                            state.error != null -> state.error ?: "I could not finish that."
                            else -> "Done."
                        }
                        // replace the typing line with the real answer
                        if (sheetMessages.lastOrNull()?.first == "assistant" &&
                            (sheetMessages.last().second.startsWith("Step ") || sheetMessages.last().second == "…")
                        ) {
                            sheetMessages[sheetMessages.size - 1] = "assistant" to msg
                            renderSheetMessages()
                        } else {
                            appendSheetMessage("assistant", msg)
                        }
                        VoiceController.speak(msg)
                        stateJob?.cancel()
                    }
                }
            }
        }
    }

    private fun autoStartVoice() {
        if (VoiceController.isListening()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(
                this,
                "Allow microphone in Antigravity to use voice control",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        VoiceController.start(this)
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
