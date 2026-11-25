package id.vern.antigravity.service

import android.content.Context
import android.util.Log
import id.vern.antigravity.executor.EngineExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections

data class CliModel(
    val cliId: String,
    val displayName: String,
    val label: String,
    val provider: String,
    val effort: String = "",

    val isDefault: Boolean = false
)

data class CliModelsResult(
    val models: List<CliModel> = emptyList(),
    val error: String? = null,

    val detail: String? = null
)

object AgyModelsService {
    private const val TAG = "AgyModels"
    private const val QUERY_TIMEOUT_MS = 30_000L
    private val queryMutex = Mutex()
    @Volatile
    private var cachedModelsResult: CliModelsResult? = null
    @Volatile
    private var lastQueryTimeMs: Long = 0L
    private const val CACHE_TTL_MS = 60_000L

    val EFFORT_LEVELS = listOf("Low", "Medium", "High", "XHigh", "Max")
    private val EFFORT_SUFFIXES = listOf("-low", "-medium", "-high", "-xhigh", "-max")

    fun invalidateCache() {
        cachedModelsResult = null
        lastQueryTimeMs = 0L
    }

    fun isRealAiModel(modelId: String, displayName: String = ""): Boolean {
        val cleanId = modelId.trim().lowercase()
        val cleanName = displayName.trim().lowercase()
        if (cleanId.isEmpty()) return false

        if (cleanId.startsWith("chat_") || cleanId.startsWith("chat-") || cleanName.startsWith("chat_") || cleanName.startsWith("chat-")) return false
        if (cleanId.startsWith("tab_") || cleanId.startsWith("tab-") || cleanName.startsWith("tab_") || cleanName.startsWith("tab-")) return false
        if (cleanId.contains("internal") || cleanName.contains("internal")) return false
        if (cleanId.contains("embedding") || cleanName.contains("embedding")) return false
        if (cleanId.endsWith("-disabled") || cleanId.endsWith("_disabled") || cleanName.endsWith("-disabled")) return false
        if (cleanId.startsWith("debug") || cleanName.startsWith("debug")) return false

        val knownAiPrefixes = listOf("gemini", "claude", "gpt", "o1", "o3", "o4", "deepseek", "llama", "mistral", "qwen", "argon", "hydrogen", "neon", "orchestrator")
        val isKnownAi = knownAiPrefixes.any { cleanId.contains(it) || cleanName.contains(it) }
        val provider = providerForDisplayName(displayName).ifBlank { providerForDisplayName(cleanId) }
        val hasKnownProvider = provider == "Google" || provider == "Anthropic" || provider == "OpenAI"

        return isKnownAi || hasKnownProvider
    }

    fun providerForDisplayName(displayName: String): String = when {
        displayName.startsWith("Gemini", ignoreCase = true) || displayName.contains("gemini", ignoreCase = true) || displayName.contains("google", ignoreCase = true) || displayName.contains("argon", ignoreCase = true) || displayName.contains("hydrogen", ignoreCase = true) || displayName.contains("neon", ignoreCase = true) -> "Google"
        displayName.startsWith("Claude", ignoreCase = true) || displayName.contains("claude", ignoreCase = true) || displayName.contains("anthropic", ignoreCase = true) -> "Anthropic"
        displayName.startsWith("GPT", ignoreCase = true) || displayName.contains("gpt", ignoreCase = true) || displayName.contains("openai", ignoreCase = true) -> "OpenAI"
        else -> ""
    }

    fun normalizeEffort(raw: String?): String? {
        val lower = raw?.trim()?.lowercase() ?: return null
        if (lower.isEmpty()) return null
        return when {
            lower.contains("xhigh") || lower.contains("x-high") -> "XHigh"
            lower.contains("max") -> "Max"
            lower.contains("low") -> "Low"
            lower.contains("medium") -> "Medium"
            lower.contains("high") -> "High"
            else -> null
        }
    }

    fun effortSuffixOf(id: String): String? {
        val lower = id.lowercase()
        for (suffix in EFFORT_SUFFIXES) {
            if (lower.endsWith(suffix)) {
                return normalizeEffort(suffix.removePrefix("-"))
            }
        }
        return null
    }

    private fun splitDisplayName(raw: String): Pair<String, String?> {
        val trimmed = raw.trim()
        val match = Regex("\\((Low|Medium|High|XHigh|Max|Thinking)\\)\\s*$").find(trimmed)
            ?: return Pair(trimmed, null)
        val name = trimmed.substring(0, match.range.first).trim()
        return Pair(name, match.groupValues[1])
    }

    fun encodeCliModels(models: List<CliModel>): String {
        val arr = JSONArray()
        models.forEach { m ->
            val obj = JSONObject()
            obj.put("cliId", m.cliId)
            obj.put("displayName", m.displayName)
            obj.put("label", m.label)
            obj.put("provider", m.provider)
            obj.put("effort", m.effort)
            obj.put("isDefault", m.isDefault)
            arr.put(obj)
        }
        return arr.toString()
    }

    fun decodeCliModels(json: String): List<CliModel> {
        return try {
            val arr = JSONArray(json)
            val out = mutableListOf<CliModel>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val cliId = obj.optString("cliId").trim()
                val displayName = obj.optString("displayName").trim()
                if (cliId.isBlank() || displayName.isBlank()) continue
                out.add(
                    CliModel(
                        cliId = cliId,
                        displayName = displayName,
                        label = obj.optString("label").ifBlank { displayName },
                        provider = obj.optString("provider"),
                        effort = obj.optString("effort"),
                        isDefault = obj.optBoolean("isDefault")
                    )
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetchCliModels(context: Context, force: Boolean = false): CliModelsResult = withContext(Dispatchers.IO) {
        if (force) {
            invalidateCache()
        }
        val now = System.currentTimeMillis()
        if (!force) {
            cachedModelsResult?.let { cached ->
                if (cached.models.isNotEmpty() && now - lastQueryTimeMs < CACHE_TTL_MS) {
                    return@withContext cached
                }
            }
        }

        queryMutex.withLock {
            val lockedNow = System.currentTimeMillis()
            if (!force) {
                cachedModelsResult?.let { cached ->
                    if (cached.models.isNotEmpty() && lockedNow - lastQueryTimeMs < CACHE_TTL_MS) {
                        return@withContext cached
                    }
                }
            }

            if (EngineExecutor.isAgyRunning()) {
                Log.i(TAG, "Engine busy, returning cached or empty models")
                return@withContext cachedModelsResult?.takeIf { it.models.isNotEmpty() }
                    ?: CliModelsResult(error = "engine-busy")
            }

            val json = runCliQuery(context, listOf("--output-format", "json", "models"))
            if (json.exitCode == 0) {
                val fromJson = parseModelsJson(json.stdout).filter { isRealAiModel(it.cliId, it.displayName) }
                if (fromJson.isNotEmpty()) {
                    Log.i(TAG, "Loaded ${fromJson.size} models from `agy --output-format json models`")
                    val res = CliModelsResult(models = fromJson)
                    cachedModelsResult = res
                    lastQueryTimeMs = System.currentTimeMillis()
                    return@withContext res
                }
                Log.w(TAG, "JSON model query returned no parseable models: ${json.stdout.take(300)}")
            } else {
                Log.w(TAG, "JSON model query exited ${json.exitCode}: ${json.stderrTail}")
            }

            val isTimedOut = json.exitCode == -2 || json.stderrTail.contains("timed out", ignoreCase = true)
            val text = if (!isTimedOut) {
                runCliQuery(context, listOf("models"))
            } else {
                json
            }
            if (!isTimedOut && text.exitCode == 0) {
                val fromText = parseModelsText(text.stdout).filter { isRealAiModel(it.cliId, it.displayName) }
                if (fromText.isNotEmpty()) {
                    Log.i(TAG, "Loaded ${fromText.size} models from `agy models`")
                    val res = CliModelsResult(models = fromText)
                    cachedModelsResult = res
                    lastQueryTimeMs = System.currentTimeMillis()
                    return@withContext res
                }
                Log.w(TAG, "Unparseable `agy models` output: ${text.stdout.take(300)}")
            } else if (!isTimedOut) {
                Log.w(TAG, "Text model query exited ${text.exitCode}: ${text.stderrTail}")
            }

            Log.w(TAG, "Could not load model list from the Antigravity CLI")
            val detail = json.stderrTail.ifBlank { text.stderrTail }
            val failResult = CliModelsResult(error = "cli-query-failed", detail = detail.ifBlank { null })

            if (!force) {
                val prefsCached = decodeCliModels(
                    context.getSharedPreferences("antigravity_prefs", Context.MODE_PRIVATE)
                        .getString("ag_cli_models_json", null).orEmpty()
                ).filter { isRealAiModel(it.cliId, it.displayName) }
                if (prefsCached.isNotEmpty()) {
                    val res = CliModelsResult(models = prefsCached)
                    cachedModelsResult = res
                    return@withContext res
                }
            }

            cachedModelsResult?.takeIf { it.models.isNotEmpty() } ?: failResult
        }
    }

    private data class QueryOutcome(
        val exitCode: Int,
        val stdout: String,
        val stderrTail: String
    )

    private suspend fun runCliQuery(context: Context, args: List<String>): QueryOutcome {
        val stdout = StringBuffer()
        val stderrLines = Collections.synchronizedList(mutableListOf<String>())
        val exit = try {
            withTimeoutOrNull(QUERY_TIMEOUT_MS) {
                EngineExecutor.executeAgy(context, args) { line, isError ->
                    if (isError) stderrLines.add(line) else stdout.appendLine(line)
                }
            } ?: run {
                EngineExecutor.stopAgyProcess()
                -2
            }
        } catch (e: Exception) {
            Log.w(TAG, "CLI model query failed to launch: ${e.message}")
            return QueryOutcome(-1, "", e.message ?: "launch-failed")
        }
        val tail = synchronized(stderrLines) { stderrLines.takeLast(3).joinToString(" | ") }
        return QueryOutcome(exit, stdout.toString(), tail)
    }

    fun parseModelsJson(raw: String): List<CliModel> {
        return try {
            val jsonStart = raw.indexOfFirst { it == '{' || it == '[' }
            if (jsonStart < 0) return emptyList()
            val trimmed = raw.substring(jsonStart).trim()
            val items = mutableListOf<JSONObject>()
            if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    items.add(obj)
                }
            } else {
                val root = JSONObject(trimmed)
                var modelsNode: Any? = root.opt("models")
                if (modelsNode == null) {
                    modelsNode = root.optJSONObject("command")?.optJSONObject("data")?.opt("models")
                }
                when (modelsNode) {
                    is JSONArray -> {
                        for (i in 0 until modelsNode.length()) {
                            val entry = modelsNode.opt(i) ?: continue
                            when (entry) {
                                is JSONObject -> items.add(entry)
                                is String -> items.add(JSONObject().put("id", entry))
                                else -> {}
                            }
                        }
                    }
                    is JSONObject -> {
                        val keys = modelsNode.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            val obj = modelsNode.optJSONObject(key) ?: continue
                            if (!obj.has("id") && !obj.has("modelId") && !obj.has("slug")) {
                                obj.put("id", key)
                            }
                            items.add(obj)
                        }
                    }
                    else -> {
                        val resp = root.optString("response")
                        if (resp.isNotBlank()) {
                            return parseModelsText(resp)
                        }
                        return emptyList()
                    }
                }
            }
            items.mapNotNull { jsonToModel(it) }.distinctBy { it.cliId }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun cleanModelId(rawId: String): String {
        return rawId
            .replace(Regex("-tiered(?=-|$)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\btiered\\b", RegexOption.IGNORE_CASE), "")
            .trim('-', ' ')
    }

    private fun jsonToModel(obj: JSONObject): CliModel? {
        val rawCandidate = obj.optString("id").ifBlank { obj.optString("modelId") }
            .ifBlank { obj.optString("slug") }.ifBlank { obj.optString("model") }.trim()
        val rawId = cleanModelId(rawCandidate)
        if (rawId.isBlank() || rawId.contains(" ") || rawId.contains("(")) return null
        val rawName = obj.optString("label").ifBlank { obj.optString("name") }
            .ifBlank { obj.optString("displayName") }
            .ifBlank { obj.optString("title") }
        if (!isRealAiModel(rawId, rawName)) return null
        val rawProvider = obj.optString("provider").ifBlank { obj.optString("modelProvider") }
        val rawEffort = obj.optString("effort").ifBlank { obj.optString("thinking") }
            .ifBlank { obj.optString("thinkingLevel") }

        val idEffort = effortSuffixOf(rawId)
        val (namePart, nameSuffix) = splitDisplayName(rawName)
        val effort = normalizeEffort(nameSuffix)
            ?: idEffort
            ?: normalizeEffort(rawEffort)
        val rawDisplayName = namePart.ifBlank { displayNameForId(rawId) }
        val displayName = cleanDisplayName(rawDisplayName)
        if (displayName.isBlank()) return null
        val provider = when (rawProvider) {
            "MODEL_PROVIDER_GOOGLE", "Google" -> "Google"
            "MODEL_PROVIDER_ANTHROPIC", "Anthropic" -> "Anthropic"
            "MODEL_PROVIDER_OPENAI", "OpenAI" -> "OpenAI"
            else -> providerForDisplayName(displayName).ifBlank { "Other" }
        }
        val cleanedRawName = cleanDisplayName(rawName.trim())
        return CliModel(
            cliId = rawId,
            displayName = displayName,
            label = cleanedRawName.ifBlank {
                if (effort != null) "$displayName ($effort)" else displayName
            },
            provider = provider,
            effort = effort ?: "",
            isDefault = obj.optBoolean("isDefault", false) || obj.optBoolean("default", false)
        )
    }

    fun cliModelFromBackendEntry(id: String, entry: JSONObject?): CliModel? {
        val cleanId = cleanModelId(id)
        val obj = JSONObject()
        obj.put("id", cleanId)
        if (entry != null) {
            for (key in listOf("displayName", "title", "modelProvider")) {
                val value = entry.optString(key, "")
                if (value.isNotBlank()) obj.put(key, value)
            }
        }
        val model = jsonToModel(obj) ?: return null

        return if (model.effort.isNotEmpty() && model.label == model.displayName) {
            model.copy(label = "${model.displayName} (${model.effort})")
        } else {
            model
        }
    }

    fun cleanDisplayName(name: String): String {
        return name
            .replace(Regex("-tiered(?=-|$)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\btiered\\b", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    fun displayNameForId(id: String): String {
        val effort = effortSuffixOf(id)
        val cleanId = id.replace(Regex("-tiered(?=-|$)", RegexOption.IGNORE_CASE), "")
        val base = if (effort != null) {
            cleanId.substring(0, cleanId.length - effort.length - 1)
        } else {
            cleanId
        }
        val tokens = base.split("-").filter { it.isNotBlank() }
        if (tokens.isEmpty()) return ""
        val merged = mutableListOf<String>()
        for (token in tokens) {
            if (token.all { it.isDigit() } && merged.isNotEmpty() &&
                merged.last().last().isDigit()
            ) {
                merged[merged.lastIndex] = merged.last() + "." + token
            } else {
                merged.add(token)
            }
        }
        return merged.joinToString(" ") { token ->
            when {
                token.equals("gpt", ignoreCase = true) -> "GPT"
                token.equals("oss", ignoreCase = true) -> "OSS"
                token.matches(Regex("^\\d+[a-z]+$")) -> token.uppercase()
                token.first().isLetter() -> token.replaceFirstChar { it.uppercase() }
                else -> token
            }
        }
    }

    private val SLUG_TOKEN = Regex("[a-z0-9]+(?:[-._][a-z0-9]+)+")

    fun parseModelsText(raw: String): List<CliModel> {
        val parsed = mutableListOf<CliModel>()
        for (line in raw.lines()) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) continue
            if (trimmedLine.contains("://")) continue
            if (trimmedLine.startsWith("⠋") || trimmedLine.startsWith("⠙") || trimmedLine.startsWith("⠹") ||
                trimmedLine.startsWith("⠸") || trimmedLine.startsWith("⠼") || trimmedLine.startsWith("⠴") ||
                trimmedLine.startsWith("⠦") || trimmedLine.startsWith("⠧") || trimmedLine.startsWith("⠇") ||
                trimmedLine.startsWith("⠏") || trimmedLine.contains("Fetching available models", ignoreCase = true)) continue
            if (trimmedLine.contains("Navigate", ignoreCase = true) && trimmedLine.contains("Select", ignoreCase = true)) continue
            if (trimmedLine.contains("esc to", ignoreCase = true) || trimmedLine.contains("Introducing", ignoreCase = true)) continue
            if (trimmedLine.startsWith("↓") || trimmedLine.startsWith("↑")) continue
            if (trimmedLine.startsWith("> /model") || trimmedLine.startsWith("/model")) continue

            val cleanLine = trimmedLine.trimStart('>', '*', '-', '•', ' ').trim()
            val parts = cleanLine.split(Regex("[\t]{1,}|[ ]{2,}")).map { it.trim() }.filter { it.isNotBlank() }
            if (parts.isEmpty()) continue

            val slugMatch = SLUG_TOKEN.find(cleanLine)?.value
            val id: String
            val explicitLabel: String?

            if (parts.size >= 2) {
                if (SLUG_TOKEN.matches(parts[0]) && !parts[0].contains(" ")) {
                    id = parts[0]
                    explicitLabel = parts[1]
                } else if (SLUG_TOKEN.matches(parts[parts.lastIndex]) && !parts[parts.lastIndex].contains(" ")) {
                    id = parts[parts.lastIndex]
                    explicitLabel = parts.subList(0, parts.lastIndex).joinToString(" ")
                } else if (slugMatch != null) {
                    id = slugMatch
                    explicitLabel = cleanLine.replace(slugMatch, "").trim()
                } else {
                    continue
                }
            } else {
                if (slugMatch != null) {
                    id = slugMatch
                    val rem = cleanLine.replace(slugMatch, "").trim()
                    explicitLabel = rem.ifBlank { null }
                } else {
                    continue
                }
            }

            val cleanId = cleanModelId(id)
            if (cleanId.isBlank() || cleanId.contains(" ")) continue

            val (namePart, labelEffort) = if (explicitLabel != null) splitDisplayName(explicitLabel) else Pair("", null)
            val displayName = cleanDisplayName(namePart.ifBlank { displayNameForId(cleanId) })
            if (displayName.isBlank()) continue
            if (!isRealAiModel(cleanId, displayName)) continue

            val idEffort = effortSuffixOf(cleanId)
            val effort = normalizeEffort(labelEffort) ?: idEffort ?: ""
            val label = if (!explicitLabel.isNullOrBlank()) cleanDisplayName(explicitLabel) else if (effort.isNotEmpty()) "$displayName ($effort)" else displayName
            val provider = providerForDisplayName(displayName).ifBlank { "Other" }

            parsed.add(
                CliModel(
                    cliId = cleanId,
                    displayName = displayName,
                    label = label,
                    provider = provider,
                    effort = effort,
                    isDefault = line.contains("(default)", ignoreCase = true)
                )
            )
        }
        return parsed.distinctBy { it.cliId }
    }
}

