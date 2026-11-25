package id.vern.antigravity.util

import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private var logHandler: ((String, String, Boolean) -> Unit)? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val lock = Any()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun setHandler(handler: (String, String, Boolean) -> Unit) {
        logHandler = handler
    }

    fun d(tag: String, message: String) {
        log(tag, message, false)
    }

    fun e(tag: String, message: String) {
        log(tag, message, true)
    }

    fun log(tag: String, message: String, isError: Boolean = false) {

        try {
            logHandler?.invoke(tag, message, isError)
        } catch (e: Throwable) {}

        scope.launch {
            try {
                val baseDir = Environment.getExternalStorageDirectory() ?: return@launch
                val logDir = File(baseDir, "Antigravity")
                if (!logDir.exists()) {
                    logDir.mkdirs()
                }
                val logFile = File(logDir, "logs.txt")
                val timeStr = timeFormat.format(Date())
                val line = "[$timeStr] [$tag] $message\n"
                synchronized(lock) {
                    logFile.appendText(line, Charsets.UTF_8)
                }
            } catch (e: Throwable) {

            }
        }
    }
}

