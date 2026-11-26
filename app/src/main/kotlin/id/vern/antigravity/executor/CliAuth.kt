package id.vern.antigravity.executor

import android.content.Context
import id.vern.antigravity.api.GeminiClient
import id.vern.antigravity.util.AppLogger
import org.json.JSONObject
import java.io.File

/**
 * Wires the user's Gemini API key into the bundled `agy` CLI.
 *
 * agy supports a key-only mode (no Google sign-in):
 *   export GEMINI_API_KEY=…            -> credential
 *   settings.json: "modelProvider": "gemini"
 *
 * Both are applied on every engine start so code writing, file management and the
 * command line keep working after the OAuth flow was removed from the app.
 */
object CliAuth {

    private const val TAG = "CliAuth"

    /** Name used when agy asks settings.json which provider to talk to. */
    private const val PROVIDER = "gemini"

    private fun settingsFiles(context: Context): List<File> {
        val files = context.filesDir
        return listOf(
            File(files, ".gemini/settings.json"),
            File(files, "home/.gemini/settings.json"),
            File(files, ".gemini/antigravity-cli/settings.json"),
            File(files, "home/.gemini/antigravity-cli/settings.json")
        )
    }

    /**
     * Exports the key + provider into a process environment map.
     * Safe to call on every process spawn: it is a no-op without a key.
     */
    fun applyEnvironment(context: Context, env: MutableMap<String, String>) {
        val key = GeminiClient.getApiKey(context)
        if (key.isNullOrBlank()) {
            AppLogger.d(TAG, "No API key - agy will report that sign-in is required")
            return
        }
        env["GEMINI_API_KEY"] = key
        // Some builds look for the generic Google name as well.
        env["GOOGLE_API_KEY"] = key
        env["GOOGLE_GENAI_API_KEY"] = key
        env["AGY_MODEL_PROVIDER"] = PROVIDER
        // Never try interactive login: there is no terminal in the app.
        env["NO_COLOR"] = "1"
    }

    /**
     * Writes `modelProvider: "gemini"` into every settings.json the CLI may read.
     * Preserves everything else (permissions, userSettings).
     */
    fun applySettings(context: Context) {
        val key = GeminiClient.getApiKey(context)
        if (key.isNullOrBlank()) return
        for (file in settingsFiles(context)) {
            try {
                file.parentFile?.mkdirs()
                val root = if (file.exists() && file.canRead()) {
                    val text = file.readText(Charsets.UTF_8).trim()
                    if (text.startsWith("{")) JSONObject(text) else JSONObject()
                } else {
                    JSONObject()
                }
                // Only touch the provider when a key exists; credentials stay in the env.
                root.put("modelProvider", PROVIDER)
                file.writeText(root.toString(2), Charsets.UTF_8)
                file.setReadable(true, false)
            } catch (e: Exception) {
                AppLogger.w(TAG, "Could not update ${file.name}: ${e.message}")
            }
        }
    }

    /** True when the CLI has everything it needs - used to give a clear error early. */
    fun isConfigured(context: Context): Boolean = !GeminiClient.getApiKey(context).isNullOrBlank()
}
