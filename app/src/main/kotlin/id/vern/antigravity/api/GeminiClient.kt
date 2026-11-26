package id.vern.antigravity.api

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal Gemini REST client used by the on-device screen agent.
 *
 * Supports multimodal input (a screenshot + text) and function calling, which is what
 * lets the model ask for taps/typing instead of only describing them.
 */
object GeminiClient {

    private const val PREFS_NAME = "antigravity_api_prefs"
    private const val KEY_API_KEY = "ag_gemini_api_key"
    private const val KEY_SCREEN_MODEL = "ag_screen_model"

    /** Model used by the screen agent. Gemini 2.5 Flash: fast, cheap, good at UI screenshots. */
    const val DEFAULT_SCREEN_MODEL = "gemini-2.5-flash"

    private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models"

    // ---------- API Key Storage ----------

    fun saveApiKey(context: Context, apiKey: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_API_KEY, apiKey.trim()).apply()
    }

    fun getApiKey(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_API_KEY, null)
    }

    fun hasApiKey(context: Context): Boolean = getApiKey(context)?.isNotBlank() == true

    fun clearApiKey(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_API_KEY).apply()
    }

    fun getScreenModel(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SCREEN_MODEL, DEFAULT_SCREEN_MODEL) ?: DEFAULT_SCREEN_MODEL
    }

    fun setScreenModel(context: Context, model: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SCREEN_MODEL, model.trim()).apply()
    }

    // ---------- Data Models ----------

    data class GeminiResponse(
        val text: String?,
        val error: String?
    )

    /** One function call requested by the model. */
    data class ToolCall(
        val name: String,
        val args: JSONObject
    )

    /** Result of a function-calling turn. */
    data class ToolTurn(
        val text: String?,
        val calls: List<ToolCall>,
        val error: String?,
        /** Raw model content JSON, sent back verbatim as history. */
        val modelContent: JSONObject? = null,
        /** True when the API answered 429: wait, do not treat it as a fatal error. */
        val rateLimited: Boolean = false,
        /** Milliseconds the API asked us to wait, when it said so. */
        val retryAfterMs: Long = 0L
    )

    // ---------- Simple text call (legacy, kept for existing callers) ----------

    suspend fun sendMessage(
        context: Context,
        userMessage: String,
        systemInstruction: String? = null
    ): GeminiResponse = withContext(Dispatchers.IO) {
        val apiKey = getApiKey(context)
        if (apiKey.isNullOrBlank()) {
            return@withContext GeminiResponse(
                null,
                "Gemini API key is not set. Add it in Settings."
            )
        }

        // Gemini's free tier answers 429 when the per-minute quota runs out; wait it out.
        var attempts = 0
        var result = sendMessageOnce(context, apiKey, userMessage, systemInstruction)
        while (result.error != null && attempts < 2 && isRateLimitText(result.error!!)) {
            attempts++
            delay(parseRetryDelayMs(result.error!!).coerceAtLeast(6_000L))
            result = sendMessageOnce(context, apiKey, userMessage, systemInstruction)
        }
        result
    }

    /** One HTTP attempt for [sendMessage]; separated so the caller can retry on 429. */
    private fun sendMessageOnce(
        context: Context,
        apiKey: String,
        userMessage: String,
        systemInstruction: String?
    ): GeminiResponse {
        try {
            val model = getScreenModel(context)
            val url = URL("$BASE/$model:generateContent?key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 20000
            conn.readTimeout = 90000
            conn.setRequestProperty("Content-Type", "application/json")

            val root = JSONObject()
            val contents = JSONArray()

            if (!systemInstruction.isNullOrBlank()) {
                contents.put(
                    JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
                    }
                )
            }
            contents.put(
                JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", userMessage)))
                }
            )
            root.put("contents", contents)
            root.put(
                "generationConfig",
                JSONObject().apply {
                    put("temperature", 0.7)
                    put("topP", 0.95)
                    put("maxOutputTokens", 8192)
                }
            )

            val body = postJson(conn, root)
            return parseText(body)
        } catch (e: Exception) {
            return GeminiResponse(null, "Network error: ${e.message}")
        }
    }

    private fun isRateLimitText(message: String): Boolean =
        message.contains("429") || message.contains("quota", true) ||
            message.contains("rate limit", true)

    // ---------- Vision + function calling (screen agent) ----------

    /**
     * Runs one turn of the screen agent.
     *
     * @param history prior turns in Gemini `contents` format (kept by the caller)
     * @param screenshotJpegBase64 current screen as a base64 JPEG, or null when a tree-only turn
     * @param screenText the accessibility tree rendered by ScreenControlService
     * @param userGoal what the user asked for
     * @param tools JSON array of functionDeclarations
     */
    suspend fun screenAgentTurn(
        context: Context,
        history: JSONArray,
        screenshotJpegBase64: String?,
        screenText: String,
        userGoal: String,
        systemInstruction: String,
        tools: JSONArray
    ): ToolTurn = withContext(Dispatchers.IO) {
        val apiKey = getApiKey(context)
        if (apiKey.isNullOrBlank()) {
            return@withContext ToolTurn(null, emptyList(), "Gemini API key is not set. Add it in Settings.")
        }

        try {
            val model = getScreenModel(context)
            val url = URL("$BASE/$model:generateContent?key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 20000
            conn.readTimeout = 120000
            conn.setRequestProperty("Content-Type", "application/json")

            val root = JSONObject()
            root.put("contents", history)

            root.put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", systemInstruction))
                )
            )

            root.put("tools", JSONArray().put(JSONObject().put("functionDeclarations", tools)))

            root.put(
                "generationConfig",
                JSONObject().apply {
                    put("temperature", 0.2)
                    put("maxOutputTokens", 4096)
                }
            )

            val body = postJson(conn, root)
            val err = body.optJSONObject("error")
            if (err != null) {
                val msg = err.optString("message")
                val code = err.optInt("code", 0)
                return@withContext ToolTurn(
                    null,
                    emptyList(),
                    msg,
                    rateLimited = code == 429 || msg.contains("quota", ignoreCase = true) ||
                        msg.contains("rate limit", ignoreCase = true),
                    retryAfterMs = parseRetryDelayMs(msg)
                )
            }

            val candidates = body.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return@withContext ToolTurn(null, emptyList(), "The model returned no candidates.")
            }

            val content = candidates.getJSONObject(0).optJSONObject("content")
                ?: return@withContext ToolTurn(null, emptyList(), "Empty response content.")

            val parts = content.optJSONArray("parts") ?: JSONArray()
            val textSb = StringBuilder()
            val calls = mutableListOf<ToolCall>()

            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                part.optString("text").takeIf { it.isNotBlank() }?.let { textSb.append(it) }
                val fc = part.optJSONObject("functionCall")
                if (fc != null) {
                    calls.add(
                        ToolCall(
                            name = fc.optString("name"),
                            args = fc.optJSONObject("args") ?: JSONObject()
                        )
                    )
                }
            }

            ToolTurn(
                text = textSb.toString().ifBlank { null },
                calls = calls,
                error = null,
                modelContent = content
            )
        } catch (e: Exception) {
            ToolTurn(null, emptyList(), "Network error: ${e.message}")
        }
    }

    /**
     * Builds the user turn that carries the goal, the accessibility tree and optionally a screenshot.
     */
    fun buildObservationParts(
        userGoal: String,
        screenText: String,
        screenshotJpegBase64: String?,
        step: Int
    ): JSONObject {
        val parts = JSONArray()

        parts.put(
            JSONObject().put(
                "text",
                buildString {
                    append("USER GOAL: ").append(userGoal).append("\n\n")
                    append("STEP: ").append(step).append("\n\n")
                    append("CURRENT SCREEN (accessibility tree, [id] can be tapped):\n")
                    append(screenText.take(12000))
                }
            )
        )

        if (!screenshotJpegBase64.isNullOrBlank()) {
            parts.put(
                JSONObject().put(
                    "inline_data",
                    JSONObject().apply {
                        put("mime_type", "image/jpeg")
                        put("data", screenshotJpegBase64)
                    }
                )
            )
        }

        return JSONObject().apply {
            put("role", "user")
            put("parts", parts)
        }
    }

    /** Builds the `contents` entry that returns tool results to the model. */
    fun buildFunctionResponseTurn(results: List<Pair<String, JSONObject>>): JSONObject {
        val parts = JSONArray()
        for ((name, response) in results) {
            parts.put(
                JSONObject().put(
                    "functionResponse",
                    JSONObject().apply {
                        put("name", name)
                        put("response", response)
                    }
                )
            )
        }
        return JSONObject().apply {
            put("role", "user")
            put("parts", parts)
        }
    }

    // ---------- HTTP helpers ----------

    /**
     * Reads the wait time out of a 429 message, e.g. "Please retry in 4.94328827s".
     * Returns 0 when the text carries no hint.
     */
    fun parseRetryDelayMs(message: String): Long {
        val m = Regex("retry in ([0-9]+(?:[.][0-9]+)?)s", RegexOption.IGNORE_CASE).find(message)
            ?: Regex("retryDelay\\W+([0-9]+)s", RegexOption.IGNORE_CASE).find(message)
        val seconds = m?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: return 0L
        return (seconds * 1000).toLong().coerceIn(1000L, 70_000L)
    }

    private fun postJson(conn: HttpURLConnection, root: JSONObject): JSONObject {
        OutputStreamWriter(conn.outputStream).use {
            it.write(root.toString())
            it.flush()
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
        val text = stream.bufferedReader().use { it.readText() }
        return try {
            JSONObject(text)
        } catch (e: Exception) {
            JSONObject().put("error", JSONObject().put("message", "HTTP $code: ${text.take(300)}"))
        }
    }

    private fun parseText(body: JSONObject): GeminiResponse {
        val err = body.optJSONObject("error")
        if (err != null) return GeminiResponse(null, err.optString("message"))
        val candidates = body.optJSONArray("candidates")
            ?: return GeminiResponse(null, "Empty response")
        if (candidates.length() == 0) return GeminiResponse(null, "Empty response")
        val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
            ?: return GeminiResponse(null, "Empty response")
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            sb.append(parts.getJSONObject(i).optString("text"))
        }
        return GeminiResponse(sb.toString().ifBlank { null }, null)
    }
}
