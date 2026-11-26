package id.vern.antigravity.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ScreenControlService - accessibility bridge that lets the agent see and act on the screen
 * the way a human does: look at what is on screen, then tap, type, scroll or swipe.
 *
 * Design notes
 *  - The UI tree is flattened into stable, numbered node ids so the model can say
 *    "tap 12" instead of guessing raw screen coordinates.
 *  - Every action is blocking (it waits for the gesture to finish) so the agent loop can
 *    capture a fresh screenshot right after acting.
 *  - Works across apps: the service config no longer restricts packageNames.
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

    /** Nodes parsed by the most recent [describeScreen] call, indexed by the ids handed to the model. */
    private val nodeCache = mutableMapOf<Int, AccessibilityNodeInfo>()

    override fun onCreate() {
        super.onCreate()
        instance = this
        isAutoClickEnabled = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_CLICK, false)
        Log.i(TAG, "ScreenControlService created")
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        setServiceEnabled(this, true)
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        setServiceEnabled(this, false)
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Screen state is read on demand; events only keep the service alive.
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    // ========================================================================
    // Reading the screen
    // ========================================================================

    /**
     * Describes the current screen as text for the model.
     *
     * Every interactive element gets a stable index (`[12]`) which can be passed to
     * [clickNode] later. The text also carries type, label and bounds so the model can
     * reason about layout even when it only has the tree.
     */
    fun describeScreen(): String {
        val sb = StringBuilder()
        val windows = try {
            windows
        } catch (e: Exception) {
            null
        }

        sb.append("Screen: ").append(packageNameOfActiveWindow()).append('\n')

        nodeCache.values.forEach { try { it.recycle() } catch (e: Exception) {} }
        nodeCache.clear()

        var nextId = 1
        if (windows != null && windows.isNotEmpty()) {
            for (window in windows) {
                if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue
                val root = window.root ?: continue
                sb.append("Window (layer ").append(window.layer).append(")\n")
                nextId = collectNodes(root, sb, 0, nextId, window.isActive)
            }
        } else {
            val root = rootInActiveWindow ?: return "No accessibility tree available"
            nextId = collectNodes(root, sb, 0, nextId, true)
        }

        if (nodeCache.isEmpty()) sb.append("(no interactive elements found)\n")
        return sb.toString()
    }

    private fun packageNameOfActiveWindow(): String {
        val root = rootInActiveWindow
        val pkg = root?.packageName?.toString()
        return pkg ?: activeWindowPackage ?: "unknown"
    }

    @Volatile
    private var activeWindowPackage: String? = null

    private fun collectNodes(
        node: AccessibilityNodeInfo?,
        sb: StringBuilder,
        depth: Int,
        startId: Int,
        inActiveWindow: Boolean
    ): Int {
        if (node == null || depth > 24) return startId

        var nextId = startId
        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        val hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            node.hintText?.toString()?.trim().orEmpty()
        } else ""
        val isEditable = node.className?.contains("EditText") == true || node.isEditable
        val interesting = node.isClickable || node.isLongClickable || isEditable ||
            node.isScrollable || node.isCheckable || text.isNotEmpty() || desc.isNotEmpty()

        if (interesting) {
            val id = nextId++
            try {
                nodeCache[id] = AccessibilityNodeInfo.obtain(node)
            } catch (e: Exception) {
            }

            val bounds = android.graphics.Rect().also { node.getBoundsInScreen(it) }
            sb.append('[').append(id).append("] ")

            val role = when {
                isEditable -> "input"
                node.isScrollable -> "scroll"
                node.isCheckable -> if (node.isChecked) "checked" else "checkbox"
                node.isClickable -> "button"
                else -> "text"
            }
            sb.append(role).append(' ')
            if (text.isNotEmpty()) sb.append("text=\"").append(text.take(120)).append("\" ")
            if (desc.isNotEmpty()) sb.append("desc=\"").append(desc.take(120)).append("\" ")
            if (hint.isNotEmpty()) sb.append("hint=\"").append(hint.take(60)).append("\" ")
            if (node.isEnabled.not()) sb.append("disabled ")
            if (!inActiveWindow) sb.append("background ")
            sb.append("at (").append(bounds.centerX()).append(',').append(bounds.centerY()).append(")\n")
        }

        for (i in 0 until node.childCount) {
            val child = try {
                node.getChild(i)
            } catch (e: Exception) {
                null
            }
            nextId = collectNodes(child, sb, depth + 1, nextId, inActiveWindow)
        }
        return nextId
    }

    /** Returns the currently focused editable node, if any. */
    private fun focusedEditable(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
    }

    // ========================================================================
    // Acting on the screen
    // ========================================================================

    /** Taps the node previously described with [id]. Returns false when it disappeared. */
    fun clickNode(id: Int): Boolean {
        val node = nodeCache[id]
        if (node == null) {
            Log.w(TAG, "clickNode($id): node no longer cached")
            return false
        }
        var target: AccessibilityNodeInfo? = node
        var guard = 0
        while (target != null && !target.isClickable && guard < 8) {
            target = target.parent
            guard++
        }
        val clickable = target ?: node
        val ok = try {
            clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } catch (e: Exception) {
            false
        }
        if (ok) return true
        // Fall back to a coordinate tap, which works on custom-drawn views.
        val rect = android.graphics.Rect().also { node.getBoundsInScreen(it) }
        return tap(rect.centerX().toFloat(), rect.centerY().toFloat())
    }

    /** Blocking tap at absolute screen coordinates. */
    fun tap(x: Float, y: Float, durationMs: Long = 60L): Boolean =
        performGesture(x, y, x, y, durationMs, waitMs = 1200)

    /** Blocking long press at absolute screen coordinates. */
    fun longPress(x: Float, y: Float): Boolean =
        performGesture(x, y, x, y, 700L, waitMs = 1800)

    /** Swipe from one point to another; used for scrolling lists. */
    fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long = 300L): Boolean =
        performGesture(fromX, fromY, toX, toY, durationMs, waitMs = 1600)

    /** Scrolls the largest scrollable container on screen in the given direction. */
    fun scroll(direction: String, screenWidth: Int, screenHeight: Int): Boolean {
        val cx = screenWidth / 2f
        val cy = screenHeight / 2f
        val delta = (screenHeight * 0.30f).toInt()
        return when (direction.lowercase()) {
            "down" -> swipe(cx, cy + delta, cx, cy - delta)
            "up" -> swipe(cx, cy - delta, cx, cy + delta)
            "right" -> swipe(cx + delta, cy, cx - delta, cy)
            else -> swipe(cx - delta, cy, cx + delta, cy)
        }
    }

    /** Types into the focused field, tapping [id] first when provided. */
    fun typeInto(id: Int?, text: String): Boolean {
        if (id != null) {
            clickNode(id)
            try {
                Thread.sleep(350)
            } catch (e: InterruptedException) {
            }
        }
        val focused = focusedEditable()
        if (focused != null) {
            val args = Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            val ok = try {
                focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            } catch (e: Exception) {
                false
            }
            if (ok) return true
        }
        // Node text injection not possible (custom canvas / IME): fall back to key events.
        return sendTextAsKeys(text)
    }

    private fun sendTextAsKeys(text: String): Boolean {
        // Android offers no public text injection without an input connection; report
        // failure so the agent can retry with a different strategy.
        Log.w(TAG, "typeInto fallback unavailable for text length ${text.length}")
        return false
    }

    fun pressBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun pressHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun openRecentApps(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun openNotifications(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        } else false
    }

    /** Launches an app by package name. */
    fun openApp(packageName: String): Boolean {
        return try {
            val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    @SuppressLint("NewApi")
    private fun performGesture(
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        durationMs: Long,
        waitMs: Long
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false

        val path = Path().apply {
            moveTo(fromX, fromY)
            lineTo(toX, toY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val latch = CountDownLatch(1)
        val success = AtomicBoolean(false)

        val dispatched = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    success.set(true)
                    latch.countDown()
                }

                override fun onCancelled(gestureDescription: GestureDescription) {
                    success.set(false)
                    latch.countDown()
                }
            },
            mainHandler
        )
        if (!dispatched) return false
        latch.await(waitMs, TimeUnit.MILLISECONDS)
        // Give the UI a moment to settle so the next screenshot shows the result.
        try {
            Thread.sleep(250)
        } catch (e: InterruptedException) {
        }
        return success.get()
    }

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
 * Static helpers so callers do not need a live service instance just to check status.
 */
object AccessibilityHelper {

    fun isServiceEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
            as android.view.accessibility.AccessibilityManager
        val services = manager.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
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
