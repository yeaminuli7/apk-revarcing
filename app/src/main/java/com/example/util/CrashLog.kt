package com.example.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tiny, dependency-free diagnostics log.
 *
 * The voice engine runs native code (sherpa-onnx / ONNX Runtime); when something
 * goes wrong there it either throws an [Error] or kills the process. Neither is
 * visible to the user, so we keep a rolling text log in the app's private folder:
 *
 *  * unexpected exceptions from the voice paths are appended explicitly,
 *  * the default uncaught-exception handler records anything that would have
 *    crashed the app (the crash itself still happens — Android will kill it).
 *
 * The log is readable in Settings → 🎙️ AI Voice Packs → "লগ" — no adb needed.
 */
object CrashLog {
    private const val FILE_NAME = "balaspeak-diagnostics.log"
    private const val MAX_BYTES = 48 * 1024

    @Volatile
    private var logFile: File? = null

    @Volatile
    private var installed = false

    private val stamp = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    fun install(context: Context) {
        if (installed) return
        installed = true
        val file = File(context.applicationContext.filesDir, FILE_NAME)
        logFile = file
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                append(context, "💥 CRASH in ${thread.name}: ${describe(error)}")
                append(context, error.stackTraceToString())
            } catch (_: Throwable) {
                // never let the logger mask the original crash
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun describe(t: Throwable): String =
        "${t.javaClass.simpleName}: ${t.message ?: "(no message)"}"

    fun append(context: Context, message: String) {
        try {
            val file = logFile ?: File(context.applicationContext.filesDir, FILE_NAME).also { logFile = it }
            if (file.exists() && file.length() > MAX_BYTES) {
                val tail = file.readText().takeLast(MAX_BYTES / 2)
                file.writeText(tail)
            }
            file.appendText("[${stamp.format(Date())}] $message\n")
        } catch (_: Throwable) {
            // logging must never throw
        }
    }

    fun read(context: Context, maxLines: Int = 40): String {
        return try {
            val file = logFile ?: File(context.applicationContext.filesDir, FILE_NAME)
            if (!file.exists()) return "লগ খালি"
            val lines = file.readLines().filter { it.isNotBlank() }
            if (lines.isEmpty()) "লগ খালি" else lines.takeLast(maxLines).joinToString("\n")
        } catch (e: Throwable) {
            "লগ পড়া যায়নি: ${e.message}"
        }
    }

    fun clear(context: Context) {
        try {
            (logFile ?: File(context.applicationContext.filesDir, FILE_NAME)).delete()
        } catch (_: Throwable) {
        }
    }
}
