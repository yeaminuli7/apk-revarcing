package id.vern.antigravity.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.graphics.Path
import id.vern.antigravity.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/**
 * ScreenControlService - Accessibility Service for display control.
 *
 * This service allows the Antigravity AI agent to:
 * 1. Read screen content (AccessibilityNodeInfo tree)
 * 2. Perform tap/click gestures at specific coordinates
 * 3. Navigate between apps and system settings
 *
 * Optimized for Android 16 (API 36) while remaining compatible with older versions.
 */
class ScreenControlService : AccessibilityService() {

    companion object {
        private const val TAG = "ScreenControlService"
        private const val PREFS_NAME = "antigravity_accessibility_prefs"
        private const val KEY_SERVICE_ENABLED = "ag_accessibility_enabled"
        private const val KEY_AUTO_CLICK = "ag_auto_click_enabled"

        @Volatile
        private var instance: ScreenControlService? = null

        fun getInstance(): ScreenControlService? = instance

        fun isServiceEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_SERVICE_ENABLED, false)
        }

        fun setServiceEnabled(context: Context, enabled: Boolean) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isAutoClickEnabled = false
    private var pendingTapX: Float = 0f
    private var pendingTapY: Float = 0f
    private var pendingTapCallback: ((Boolean) -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        isAutoClickEnabled = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_CLICK, false)
        AppLogger.d(TAG, "ScreenControlService created")
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
        AppLogger.d(TAG, "ScreenControlService destroyed")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        AppLogger.i(TAG, "Accessibility service connected")
        ScreenControlService.setServiceEnabled(this, true)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        AppLogger.i(TAG, "Accessibility service unbound")
        ScreenControlService.setServiceEnabled(this, false)
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Process accessibility events from the system
        if (event == null) return

        AppLogger.v(TAG, "Accessibility event: ${event.eventType}, package: ${event.packageName}")

        // If we have a pending tap, try to execute it now that UI is ready
        if (pendingTapCallback != null) {
            mainHandler.postDelayed({
                executePendingTap()
            }, 500)
        }
    }

    override fun onInterrupt() {
        AppLogger.w(TAG, "Accessibility service interrupted")
    }

    // ========================================================================
    // Public API for the AI agent to control the display
    // ========================================================================

    /**
     * Get the current screen hierarchy as a string for the AI agent to analyze.
     * Returns clickable elements with their bounds and text.
     */
    fun getScreenTree(): String {
        val root = rootInActiveWindow
        if (root == null) return "No accessibility tree available"

        val builder = StringBuilder()
        builder.append("Screen Tree:\n")
        collectClickableNodes(root, builder, 0)
        return builder.toString()
    }

    private fun collectClickableNodes(node: AccessibilityNodeInfo?, builder: StringBuilder, depth: Int) {
        if (node == null) return
        if (depth > 10) return // Prevent infinite recursion

        val indent = "  ".repeat(depth)
        val clickable = node.isClickable
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val className = node.className?.toString() ?: ""
        val bounds = android.graphics.Rect().also { node.getBoundsInScreen(it) }

        if (clickable || text.isNotEmpty() || contentDesc.isNotEmpty()) {
            builder.append("$indent[${className.split('.').last()}]")
            if (text.isNotEmpty()) builder.append(" text=\"$text\"")
            if (contentDesc.isNotEmpty()) builder.append(" desc=\"$contentDesc\"")
            if (!bounds.isEmpty) {
                builder.append(" bounds=[${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}]")
            }
            builder.append(" clickable=$clickable\n")
        }

        for (i in 0 until node.childCount) {
            collectClickableNodes(node.getChild(i), builder, depth + 1)
        }

        node.recycle()
    }

    /**
     * Perform a tap at the given screen coordinates.
     * Works on Android 7.0+ via AccessibilityService gesture API.
     */
    @SuppressLint("NewApi")
    fun tapAt(x: Float, y: Float, callback: ((Boolean) -> Unit)? = null) {
        pendingTapX = x
        pendingTapY = y
        pendingTapCallback = callback

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            performTapGesture(x, y)
        } else {
            // Fallback for older versions
            mainHandler.post {
                callback?.invoke(false)
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.N)
    private fun performTapGesture(x: Float, y: Float) {
        val path = Path().apply {
            moveTo(x, y)
            lineTo(x, y)
        }

        val stroke = GestureDescription.StrokeDescription(
            path,
            0, // start time (ms)
            100 // duration (ms)
        )

        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription) {
                super.onCompleted(gestureDescription)
                mainHandler.post {
                    pendingTapCallback?.invoke(true)
                    pendingTapCallback = null
                }
            }

            override fun onCancelled(gestureDescription: GestureDescription) {
                super.onCancelled(gestureDescription)
                mainHandler.post {
                    pendingTapCallback?.invoke(false)
                    pendingTapCallback = null
                }
            }
        }, mainHandler)
    }

    private fun executePendingTap() {
        if (pendingTapCallback != null) {
            performTapGesture(pendingTapX, pendingTapY)
        }
    }

    /**
     * Type text into the currently focused field.
     */
    fun typeText(text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Note: text injection is performed by the gateway via accessibility
            // actions on the focused node; a gesture-only stub is intentional here.
            val root = rootInActiveWindow
            val focused = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null) {
                val args = android.os.Bundle()
                args.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text
                )
                focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            }
        }
    }

    /**
     * Navigate to system settings (e.g., battery saver).
     */
    fun openSettings(settingAction: String? = null) {
        val intent = Intent(settingAction ?: Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    /**
     * Open battery settings specifically.
     */
    fun openBatterySettings() {
        val intent = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            }
            else -> {
                Intent(Settings.ACTION_SETTINGS)
            }
        }.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    /**
     * Press system back button.
     */
    fun pressBack() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /**
     * Press home button.
     */
    fun pressHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /**
     * Open recent apps.
     */
    fun openRecentApps() {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    /**
     * Check if the accessibility service is enabled on the device.
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
            as android.view.accessibility.AccessibilityManager
        val services = manager.getEnabledAccessibilityServiceList(
            android.view.accessibility.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        return services.any { it.id == "${context.packageName}/${ScreenControlService::class.java.name}" }
    }

    /**
     * Request the user to enable the accessibility service.
     */
    fun requestAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Take a screenshot and return it as a base64 string for Gemini vision analysis.
     * This requires MediaProjection API (Android 5.0+).
     * For simplicity, this returns a placeholder - the UI layer should handle screenshot capture.
     */
    fun captureScreenBase64(): String? {
        // Screenshot capture requires MediaProjectionManager, which is an Activity-level API.
        // This service cannot directly capture screenshots.
        // The ChatScreen or MainActivity should handle this via MediaProjection.
        return null
    }

    /**
     * Enable or disable auto-click mode.
     */
    fun setAutoClick(enabled: Boolean) {
        isAutoClickEnabled = enabled
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_CLICK, enabled)
            .apply()
    }

    fun isAutoClickEnabled(): Boolean = isAutoClickEnabled
}

/**
 * Utility to check if accessibility service is enabled and can be requested.
 */
object AccessibilityHelper {

    fun isServiceEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
            as android.view.accessibility.AccessibilityManager
        val services = manager.getEnabledAccessibilityServiceList(
            android.view.accessibility.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        val packageName = context.packageName
        return services.any { it.id?.startsWith(packageName) == true }
    }

    fun openSettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
