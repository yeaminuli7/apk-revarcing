package id.vern.antigravity

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Looper
import android.os.Process
import android.util.Log
import id.vern.antigravity.ui.CrashHandlerActivity
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

class AntigravityApp : Application() {

    companion object {
        private const val TAG = "AntigravityApp"
        lateinit var instance: AntigravityApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        setupCrashHandler()
        Log.i(TAG, "Antigravity App initialized with crash handler.")
    }

    private fun isCrashHandlerProcess(): Boolean {
        return try {
            val pid = Process.myPid()
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val processName = am.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName ?: ""
            processName.endsWith(":crash_handler")
        } catch (e: Exception) {
            false
        }
    }

    private fun setupCrashHandler() {
        if (isCrashHandlerProcess()) return

        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            val sw = StringWriter()
            exception.printStackTrace(PrintWriter(sw))
            val rawTrace = sw.toString().trim()
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

            val crashDetails = buildString {
                appendLine("[Aplikasi Terhenti (Force Close)]")
                appendLine("Timestamp: $time")
                appendLine("Thread: ${thread.name}")
                appendLine("Exception: ${exception.javaClass.name}")
                appendLine("Message: ${exception.localizedMessage ?: "No message"}")
                appendLine()
                appendLine("Stack Trace:")
                appendLine(rawTrace)
            }.trim()

            Log.e(TAG, "Antigravity force closed on thread: ${thread.name}", exception)

            try {
                File(filesDir, "crash.log").writeText(crashDetails)
            } catch (e: Exception) {}

            try {
                val prefs = getSharedPreferences("crash_recovery", Context.MODE_PRIVATE)
                prefs.edit().putString("pending_crash_log", crashDetails).commit()
            } catch (e: Exception) {}

            // The crash handler runs in its own process (":crash_handler"), so we
            // must give it a moment to come up before killing this process.
            // Sleeping on the UI thread used to freeze the app on a black screen
            // whenever the crash itself happened on the main thread, so the delay
            // is skipped in that case and the handler activity is launched first.
            try {
                CrashHandlerActivity.start(applicationContext, crashDetails)
            } catch (e: Exception) {
                Log.e(TAG, "Failed launching CrashHandlerActivity", e)
            }

            val onMainThread = thread === Looper.getMainLooper().thread
            if (!onMainThread) {
                try {
                    Thread.sleep(1800)
                } catch (ignored: InterruptedException) {
                }
            } else {
                // Let the new process attach and draw before this one dies.
                android.os.SystemClock.sleep(400)
            }

            Process.killProcess(Process.myPid())
            exitProcess(1)
        }
    }
}

