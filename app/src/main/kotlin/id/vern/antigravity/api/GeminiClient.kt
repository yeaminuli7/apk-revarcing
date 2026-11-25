package id.vern.antigravity.api

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object GeminiClient {

    private const val PREFS_NAME = "antigravity_api_prefs"
    private const val KEY_API_KEY = "ag_gemini_api_key"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"

    // ---------- API Key Storage ----------

    fun saveApiKey(context: Context, apiKey: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_API_KEY, apiKey).apply()
    }

    fun getApiKey(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_API_KEY, null)
    }

    fun hasApiKey(context: Context): Boolean {
        return getApiKey(context)?.isNotBlank() == true
    }

    fun clearApiKey(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_API_KEY).apply()
    }

    // ---------- Data Models ----------

    data class GeminiResponse(
        val text: String?,
        val error: String?
    )

    // ---------- Send Message to Gemini ----------

    suspend fun sendMessage(
        context: Context,
        userMessage: String,
        systemInstruction: String? = null
    ): GeminiResponse = withContext(Dispatchers.IO) {
        val apiKey = getApiKey(context) ?: return@withContext GeminiResponse(
            null,
            "জেমিনি API কি সেট করা নেই। সেটিংস থেকে জেমিনি API কি যোগ করুন।"
        )

        try {
            val url = URL("$BASE_URL?key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 20000
            conn.readTimeout = 60000
            conn.setRequestProperty("Content-Type", "application/json")

            val root = JSONObject()
            val contentsArray = JSONArray()

            // System instruction as first user turn if provided
            if (systemInstruction.isNotBlank()) {
                contentsArray.put(
                    JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().put(JSONObject().put("text", systemInstruction)))
                    }
                )
            }

            // Actual user message
            contentsArray.put(
                JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", userMessage)))
                }
            )

            root.put("contents", contentsArray)

            val config = JSONObject().apply {
                put("temperature", 0.7)
                put("topP", 0.95)
                put("topK", 40)
                put("maxOutputTokens", 4096)
            }
            root.put("generationConfig", config)

            OutputStreamWriter(conn.outputStream).use { it.write(root.toString()); it.flush() }

            val responseCode = conn.responseCode
            val responseBody = conn.inputStream.bufferedReader().use { it.readText() }

            if (responseCode in 200..299) {
                val json = JSONObject(responseBody)
                val candidates = json.optJSONArray("candidates")
                val text = if (candidates != null && candidates.length() > 0) {
                    val content = candidates.getJSONObject(0).optJSONObject("content")
                    content?.optJSONArray("parts")?.getJSONObject(0)?.optString("text") ?: null
                } else null
                GeminiResponse(text, null)
            } else {
                GeminiResponse(null, "API এরর ($responseCode): $responseBody")
            }
        } catch (e: Exception) {
            GeminiResponse(null, "যোগাযোগে সমস্যা: ${e.message ?: "অज्ञात এরর"}")
        }
    }
}
