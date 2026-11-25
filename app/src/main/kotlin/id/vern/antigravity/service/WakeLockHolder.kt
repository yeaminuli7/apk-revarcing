package id.vern.antigravity.service

import android.content.Context
import android.os.PowerManager
import android.util.Log

class WakeLockHolder {

    companion object {
        private const val TAG = "WakeLockHolder"
        private const val LOCK_TAG = "id.vern.antigravity:wakelock"
    }

    private var wakeLock: PowerManager.WakeLock? = null

    fun acquire(context: Context, timeoutMs: Long = 10 * 60 * 1000L) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm != null) {
                if (wakeLock == null) {
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, LOCK_TAG).apply {
                        setReferenceCounted(false)
                    }
                }
                wakeLock?.let {
                    if (it.isHeld) it.release()
                    it.acquire(timeoutMs)
                }
                Log.i(TAG, "Partial WakeLock acquired (timeout=${timeoutMs}ms)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire WakeLock: ${e.message}")
        }
    }

    fun release() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.i(TAG, "Partial WakeLock released")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release WakeLock: ${e.message}")
        } finally {
            wakeLock = null
        }
    }

    val isHeld: Boolean
        get() = wakeLock?.isHeld == true
}

