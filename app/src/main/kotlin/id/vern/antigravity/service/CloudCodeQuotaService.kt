package id.vern.antigravity.service

import android.content.Context
import android.os.Environment
import android.util.Log
import id.vern.antigravity.data.AvailableModelInfo
import id.vern.antigravity.data.CodeAssistTier
import id.vern.antigravity.data.ModelAvailability
import id.vern.antigravity.data.QuotaBucket
import id.vern.antigravity.data.QuotaGroup
import id.vern.antigravity.data.TierInfo
import id.vern.antigravity.data.UserQuotaSummary
import id.vern.antigravity.oauth.OAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.TrustManager
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

object CloudCodeQuotaService {
    private const val TAG = "CloudCodeQuota"
    private val CLOUDCODE_API_BASES = listOf(
        "https://daily-cloudcode-pa.googleapis.com",
        "https://cloudcode-pa.googleapis.com"
    )
    private const val QUOTA_PATH = "/v1internal:retrieveUserQuotaSummary"
    private const val MODELS_PATH = "/v1internal:fetchAvailableModels"
    private const val TIER_PATH = "/v1internal:loadCodeAssist"

    private val cachedSslContext: SSLContext? by lazy {
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509ExtendedTrustManager() {
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {}
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {}
            })
            SSLContext.getInstance("TLS").apply {
                init(null, trustAllCerts, SecureRandom())
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun persistEndpoint(port: Int, csrf: String, context: Context? = null) {
        try {
            val json = JSONObject().apply {
                put("port", port)
                put("csrf_token", csrf)
            }.toString()

            val files = mutableListOf<File>()
            if (context != null) {
                files.add(File(context.filesDir, ".antigravity_endpoint"))
                files.add(File(context.filesDir, ".gemini/.antigravity_endpoint"))
            }

            for (f in files) {
                try {
                    f.parentFile?.mkdirs()
                    f.writeText(json, Charsets.UTF_8)
                } catch (e: Exception) {}
            }
        } catch (e: Exception) {}
    }

    private fun findCurlBinary(context: Context? = null): String {
        val candidates = mutableListOf<String>()
        if (context != null) {
            candidates.add(File(context.filesDir, "usr/bin/curl").absolutePath)
            candidates.add(File(context.filesDir, "bin/curl").absolutePath)
            val parent = context.filesDir.parentFile
            if (parent != null) {
                candidates.add(File(parent, "files/usr/bin/curl").absolutePath)
            }
        }
        val prefix = System.getenv("PREFIX")
        if (!prefix.isNullOrBlank()) {
            candidates.add("$prefix/bin/curl")
        }
        candidates.add("/system/bin/curl")
        candidates.add("/vendor/bin/curl")
        candidates.add("curl")
        return candidates.firstOrNull {
            if (it == "curl") true else File(it).canExecute()
        } ?: "curl"
    }

    private fun createCurlProcessBuilder(context: Context?, vararg command: String): ProcessBuilder {
        val pb = ProcessBuilder(*command).redirectErrorStream(true)
        val env = pb.environment()
        env.remove("LD_PRELOAD")
        if (context != null) {
            val prefix = File(context.filesDir, "usr")
            val prefixLib = File(prefix, "lib")
            if (prefixLib.isDirectory) {
                env["PREFIX"] = prefix.absolutePath
                env["LD_LIBRARY_PATH"] = "${prefixLib.absolutePath}:/system/lib64"
            }
            val certFile = File(prefix, "etc/tls/cert.pem")
            if (certFile.isFile) {
                env["SSL_CERT_FILE"] = certFile.absolutePath
                env["CURL_CA_BUNDLE"] = certFile.absolutePath
            }
            val opensslConf = File(prefix, "etc/tls/openssl.cnf")
            if (opensslConf.isFile) {
                env["OPENSSL_CONF"] = opensslConf.absolutePath
            }
        }
        return pb
    }

    private fun fetchCsrfTokenFromPort(port: Int, context: Context? = null): String? {
        val schemes = listOf("https", "http")
        var connectionRefused = false
        for (scheme in schemes) {
            try {
                val sc = cachedSslContext
                val allHostsValid = HostnameVerifier { hostname, session -> true }

                val url = URL("$scheme://127.0.0.1:$port/")
                val conn = url.openConnection()
                if (conn is HttpsURLConnection && sc != null) {
                    conn.sslSocketFactory = sc.socketFactory
                    conn.hostnameVerifier = allHostsValid
                }
                conn.connectTimeout = 2500
                conn.readTimeout = 2500
                conn.setRequestProperty("User-Agent", "antigravity")
                val html = conn.inputStream.bufferedReader().use { it.readText() }
                val tokenRegex = Regex("""\"?csrfToken\"?\s*:\s*\"([^\"]+)\"""")
                val altRegex = Regex("""\"?csrf_token\"?\s*:\s*\"([^\"]+)\"""")
                val token = tokenRegex.find(html)?.groupValues?.get(1)
                    ?: altRegex.find(html)?.groupValues?.get(1)
                if (!token.isNullOrBlank()) {
                    Log.i(TAG, "Discovered live CSRF token from $scheme://127.0.0.1:$port/: $token")
                    return token
                }
            } catch (e: ConnectException) {
                connectionRefused = true
                break
            } catch (e: Exception) {}
        }

        if (connectionRefused) {
            return null
        }

        try {
            val curlBin = findCurlBinary(context)
            val process = createCurlProcessBuilder(
                context,
                curlBin, "-k", "-s", "--max-time", "3",
                "https://127.0.0.1:$port/"
            ).start()
            val html = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            val tokenRegex = Regex("""\"?csrfToken\"?\s*:\s*\"([^\"]+)\"""")
            val altRegex = Regex("""\"?csrf_token\"?\s*:\s*\"([^\"]+)\"""")
            val token = tokenRegex.find(html)?.groupValues?.get(1)
                ?: altRegex.find(html)?.groupValues?.get(1)
            if (!token.isNullOrBlank()) {
                Log.i(TAG, "Discovered live CSRF token via curl from port $port: $token")
                return token
            }
        } catch (e: Exception) {}

        return null
    }

    private fun getCandidateEndpoints(context: Context? = null): List<Pair<Int, String>> {
        val list = mutableListOf<Pair<Int, String>>()

        val candidatePorts = listOf(45157, 45158, 45159, 45160)
        for (port in candidatePorts) {
            val liveToken = fetchCsrfTokenFromPort(port, context)
            if (!liveToken.isNullOrBlank()) {
                list.add(Pair(port, liveToken))
                persistEndpoint(port, liveToken, context)
                break
            }
        }

        val candidateFiles = mutableListOf<File>()
        if (context != null) {
            candidateFiles.add(File(context.filesDir, ".antigravity_endpoint"))
            candidateFiles.add(File(context.filesDir, ".gemini/.antigravity_endpoint"))
        }

        for (f in candidateFiles) {
            try {
                if (f.isFile && f.canRead()) {
                    val content = f.readText(Charsets.UTF_8).trim()
                    val obj = JSONObject(content)
                    val port = obj.optInt("port", 45157)
                    val csrf = obj.optString("csrf_token", "")
                    if (csrf.isNotBlank()) {
                        list.add(Pair(port, csrf))
                    }
                }
            } catch (e: Exception) {}
        }

        return list.distinct()
    }

    fun formatResetCountdown(resetTime: String?): String {
        if (resetTime.isNullOrBlank()) return "<1 minute"
        return try {
            val target = Instant.parse(resetTime)
            val now = Instant.now()
            val diffMs = Duration.between(now, target).toMillis()
            if (diffMs <= 0) return "<1 minute"
            val totalMins = diffMs / 60000
            val days = totalMins / 1440
            val hours = (totalMins % 1440) / 60
            val mins = totalMins % 60
            when {
                days > 0 && hours > 0 -> "$days days, $hours hours"
                days > 0 -> "$days days"
                hours > 0 && mins > 0 -> "$hours hours, $mins minutes"
                hours > 0 -> "$hours hours"
                mins > 0 -> "$mins minutes"
                else -> "<1 minute"
            }
        } catch (e: Exception) {
            "<1 minute"
        }
    }

    fun formatSubtext(resetTime: String?): String? {
        if (resetTime.isNullOrBlank()) return null
        return try {
            val target = Instant.parse(resetTime)
            val now = Instant.now()
            val diffMs = Duration.between(now, target).toMillis()
            if (diffMs <= 0) return "Resets in <1m"
            val totalMins = diffMs / 60000
            val days = totalMins / 1440
            val hours = (totalMins % 1440) / 60
            val mins = totalMins % 60
            val str = when {
                days > 0 && hours > 0 -> "${days}d ${hours}h"
                days > 0 -> "${days}d"
                hours > 0 && mins > 0 -> "${hours}h ${mins}m"
                hours > 0 -> "${hours}h"
                mins > 0 -> "${mins}m"
                else -> "<1m"
            }
            "Resets in $str"
        } catch (e: Exception) {
            null
        }
    }

    private fun queryLocalEndpointViaCurl(port: Int, csrf: String, rpcPath: String = "RetrieveUserQuotaSummary", requestBody: String = "{}", context: Context? = null): String? {
        return try {
            val curlBin = findCurlBinary(context)
            val process = createCurlProcessBuilder(
                context,
                curlBin,
                "-k", "-s",
                "--max-time", "5",
                "-X", "POST",
                "-H", "Content-Type: application/json",
                "-H", "x-codeium-csrf-token: $csrf",
                "-H", "Connect-Protocol-Version: 1",
                "-d", requestBody,
                "https://127.0.0.1:$port/exa.language_server_pb.LanguageServerService/$rpcPath"
            ).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            if (exitCode == 0 && output.isNotBlank() && (output.contains("\"groups\"") || output.contains("\"models\""))) {
                Log.i(TAG, "curl query succeeded for local server port $port ($rpcPath)")
                output
            } else {
                Log.w(TAG, "curl query failed (exit $exitCode, path $rpcPath): $output")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "curl query execution error: ${e.message}")
            null
        }
    }

    private fun orderedModelIds(mResp: JSONObject, modelsMap: JSONObject?): List<String> {
        val ids = mutableListOf<String>()
        val sorts = mResp.optJSONArray("agentModelSorts")
        if (sorts != null) {
            for (i in 0 until sorts.length()) {
                val groups = sorts.optJSONObject(i)?.optJSONArray("groups")
                if (groups != null) {
                    for (j in 0 until groups.length()) {
                        val mArr = groups.optJSONObject(j)?.optJSONArray("modelIds")
                        if (mArr != null) {
                            for (k in 0 until mArr.length()) {
                                val id = mArr.optString(k)
                                if (id.isNotBlank() && !ids.contains(id)) ids.add(id)
                            }
                        }
                    }
                }
            }
        }
        if (modelsMap != null) {
            val it = modelsMap.keys()
            while (it.hasNext()) {
                val k = it.next()
                if (!ids.contains(k)) ids.add(k)
            }
        }
        return ids
    }

    private val EFFORT_TITLE_REGEX = Regex("\\s*\\((High|Medium|Low|XHigh|Max|Thinking|Default)\\)", RegexOption.IGNORE_CASE)

    private fun modelTitle(mObj: JSONObject?, key: String): String {
        val t = mObj?.optString("title")?.ifBlank { null }
            ?: mObj?.optString("label")?.ifBlank { null }
            ?: mObj?.optString("name")?.ifBlank { null }
        if (!t.isNullOrBlank() && !t.contains("-tiered", ignoreCase = true) && !t.startsWith("gemini-", ignoreCase = true)) {
            return AgyModelsService.cleanDisplayName(t)
        }
        val d = mObj?.optString("displayName")?.ifBlank { null }
        if (!d.isNullOrBlank() && !d.contains("-tiered", ignoreCase = true) && !d.startsWith("gemini-", ignoreCase = true)) {
            return AgyModelsService.cleanDisplayName(d)
        }
        return AgyModelsService.displayNameForId(key)
    }

    private fun modelProviderName(mObj: JSONObject?): String = when (mObj?.optString("modelProvider")) {
        "MODEL_PROVIDER_ANTHROPIC" -> "Anthropic"
        "MODEL_PROVIDER_OPENAI" -> "OpenAI"
        else -> "Google"
    }

    fun isArgonModel(id: String, title: String = ""): Boolean {
        return id.contains("argon", ignoreCase = true) || title.contains("argon", ignoreCase = true)
    }

    private fun sharedModelTitles(orderedIds: List<String>, modelsMap: JSONObject?, googleGroup: Boolean, cliModels: List<CliModel> = emptyList()): List<String> {
        val titles = mutableListOf<String>()
        if (modelsMap != null) {
            for (id in orderedIds) {
                if (id.endsWith("-disabled", ignoreCase = true)) continue
                if (id.startsWith("tab_", ignoreCase = true) || id.startsWith("chat_", ignoreCase = true) || id.contains("embedding", ignoreCase = true)) continue
                val mObj = modelsMap.optJSONObject(id) ?: continue
                val isGoogle = modelProviderName(mObj) == "Google"
                if (isGoogle != googleGroup) continue
                val t = modelTitle(mObj, id)
                if (googleGroup && isArgonModel(id, t)) continue
                val cleanTitle = t.replace(EFFORT_TITLE_REGEX, "").trim()
                if (cleanTitle.isNotBlank() && !titles.contains(cleanTitle)) titles.add(cleanTitle)
            }
        }
        for (cli in cliModels) {
            val isGoogle = cli.provider.equals("Google", ignoreCase = true) ||
                    AgyModelsService.providerForDisplayName(cli.displayName).equals("Google", ignoreCase = true)
            if (isGoogle != googleGroup) continue
            if (googleGroup && isArgonModel(cli.cliId, cli.displayName)) continue
            val cleanTitle = cli.displayName.replace(EFFORT_TITLE_REGEX, "").trim()
            if (cleanTitle.isNotBlank() && !titles.contains(cleanTitle)) titles.add(cleanTitle)
        }
        return titles
    }

    private fun argonModelTitles(orderedIds: List<String>, modelsMap: JSONObject?, cliModels: List<CliModel> = emptyList()): List<String> {
        val titles = mutableListOf<String>()
        if (modelsMap != null) {
            for (id in orderedIds) {
                if (id.endsWith("-disabled", ignoreCase = true)) continue
                if (id.startsWith("tab_", ignoreCase = true) || id.startsWith("chat_", ignoreCase = true) || id.contains("embedding", ignoreCase = true)) continue
                val mObj = modelsMap.optJSONObject(id) ?: continue
                val tag = mObj.optString("tagTitle", "")
                if (tag.equals("Unavailable", ignoreCase = true)) continue
                val isGoogle = modelProviderName(mObj) == "Google"
                if (!isGoogle) continue
                val t = modelTitle(mObj, id)
                if (!isArgonModel(id, t)) continue
                val cleanTitle = t.replace(EFFORT_TITLE_REGEX, "").trim()
                if (cleanTitle.isNotBlank() && !titles.contains(cleanTitle)) titles.add(cleanTitle)
            }
        }
        for (cli in cliModels) {
            if (!isArgonModel(cli.cliId, cli.displayName)) continue
            val cleanTitle = cli.displayName.replace(EFFORT_TITLE_REGEX, "").trim()
            if (cleanTitle.isNotBlank() && !titles.contains(cleanTitle)) titles.add(cleanTitle)
        }
        return titles
    }

    private fun buildAvailableModels(
        orderedIds: List<String>,
        modelsMap: JSONObject?,
        cliModels: List<CliModel>,
        geminiFraction: Double?,
        geminiReset: String?,
        argonFraction: Double?,
        argonReset: String?,
        thirdPartyFraction: Double?,
        thirdPartyReset: String?
    ): List<AvailableModelInfo> {
        val out = mutableListOf<AvailableModelInfo>()
        val seen = HashSet<String>()
        if (modelsMap != null) {
            for (k in orderedIds) {
                if (k.isBlank() || !seen.add(k)) continue
                val mObj = modelsMap.optJSONObject(k) ?: continue
                val title = modelTitle(mObj, k)
                if (!AgyModelsService.isRealAiModel(k, title)) continue
                val tag = mObj.optString("tagTitle", "")
                val prov = modelProviderName(mObj)
                val isArgon = isArgonModel(k, title)
                val qi = mObj.optJSONObject("quotaInfo")
                val qiReset = qi?.optString("resetTime")?.takeIf { it.isNotBlank() }
                val rem = if (qi != null && qi.has("remainingFraction")) qi.optDouble("remainingFraction")
                    else if (qi != null && qiReset != null) 0.0
                    else if (isArgon) argonFraction
                    else if (prov == "Google") geminiFraction
                    else thirdPartyFraction
                val resetT = qiReset
                    ?: if (isArgon) argonReset
                    else if (prov == "Google") geminiReset
                    else thirdPartyReset
                out.add(
                    AvailableModelInfo(
                        modelId = k,
                        displayName = title,
                        provider = prov,
                        remainingFraction = rem,
                        resetTime = resetT,
                        availability = if (tag.equals("Unavailable", ignoreCase = true)) ModelAvailability.UNAVAILABLE else ModelAvailability.AVAILABLE
                    )
                )
            }
        }
        for (cli in cliModels) {
            if (cli.cliId.isBlank() || !seen.add(cli.cliId)) continue
            if (!AgyModelsService.isRealAiModel(cli.cliId, cli.displayName)) continue
            val prov = cli.provider.ifBlank { AgyModelsService.providerForDisplayName(cli.displayName).ifBlank { "Google" } }
            val isArgon = isArgonModel(cli.cliId, cli.displayName)
            val rem = if (isArgon) argonFraction
                else if (prov == "Google") geminiFraction
                else thirdPartyFraction
            val resetT = if (isArgon) argonReset
                else if (prov == "Google") geminiReset
                else thirdPartyReset
            out.add(
                AvailableModelInfo(
                    modelId = cli.cliId,
                    displayName = cli.label.ifBlank { cli.displayName },
                    provider = prov,
                    remainingFraction = rem,
                    resetTime = resetT,
                    availability = ModelAvailability.AVAILABLE
                )
            )
        }
        return out
    }

    private fun buildPickerModels(orderedIds: List<String>, modelsMap: JSONObject?): List<CliModel> {
        val picker = mutableListOf<CliModel>()
        val seen = HashSet<String>()
        for (id in orderedIds) {
            if (id.isBlank() || !seen.add(id)) continue
            val mObj = modelsMap?.optJSONObject(id)
            val title = if (mObj != null) modelTitle(mObj, id) else id
            if (!AgyModelsService.isRealAiModel(id, title)) continue
            val tag = mObj?.optString("tagTitle", "") ?: ""
            if (tag.equals("Unavailable", ignoreCase = true)) continue
            val model = AgyModelsService.cliModelFromBackendEntry(id, mObj)
            if (model != null && AgyModelsService.isRealAiModel(model.cliId, model.displayName)) picker.add(model)
        }
        return picker
    }

    private fun parseQuotaSummaryResponse(
        respStr: String,
        modelsRespStr: String? = null,
        tier: CodeAssistTier? = null,
        modelsFetchFailed: Boolean = false,
        context: Context? = null
    ): UserQuotaSummary? {
        return try {
            val root = JSONObject(respStr)
            val json = if (root.has("response")) root.getJSONObject("response") else root

            val modelsRoot = if (!modelsRespStr.isNullOrBlank()) {
                try { JSONObject(modelsRespStr) } catch (e: Exception) { null }
            } else null
            val modelsRespObj = modelsRoot?.let { if (it.has("response")) it.getJSONObject("response") else it }
            val modelsMap = modelsRespObj?.optJSONObject("models")
            val orderedIds = if (modelsRespObj != null) orderedModelIds(modelsRespObj, modelsMap) else emptyList()

            val decodedCliModels = context?.let { ctx ->
                val prefsJson = ctx.getSharedPreferences("antigravity_prefs", Context.MODE_PRIVATE)
                    .getString("ag_cli_models_json", null).orEmpty()
                AgyModelsService.decodeCliModels(prefsJson)
            } ?: emptyList()

            val groupsList = mutableListOf<QuotaGroup>()
            var geminiWeeklyRem: Double? = null
            var gemini5hRem: Double? = null
            var geminiWeeklyReset: String? = null
            var gemini5hReset: String? = null
            var argonWeeklyRem: Double? = null
            var argonWeeklyReset: String? = null
            var thirdPartyWeeklyRem: Double? = null
            var thirdPartyWeeklyReset: String? = null

            if (json.has("groups")) {
                val groupsArr = json.getJSONArray("groups")
                for (i in 0 until groupsArr.length()) {
                    val gObj = groupsArr.getJSONObject(i)
                    val gName = gObj.optString("displayName", "Model Quota")
                    val isArgon = gName.contains("argon", ignoreCase = true)
                    val isGemini = !isArgon && gName.contains("gemini", ignoreCase = true)
                    val is3p = gName.contains("claude", ignoreCase = true) || gName.contains("gpt", ignoreCase = true)

                    val groupSharedModels = if (isArgon) {
                        argonModelTitles(orderedIds, modelsMap, decodedCliModels)
                    } else if (isGemini) {
                        sharedModelTitles(orderedIds, modelsMap, googleGroup = true, decodedCliModels)
                    } else if (is3p) {
                        sharedModelTitles(orderedIds, modelsMap, googleGroup = false, decodedCliModels)
                    } else {
                        emptyList()
                    }

                    val bucketsList = mutableListOf<QuotaBucket>()
                    if (gObj.has("buckets")) {
                        val bArr = gObj.getJSONArray("buckets")
                        val hasNon5hBucket = (0 until bArr.length()).any { idx ->
                            val candidate = bArr.optJSONObject(idx) ?: return@any false
                            val cWin = candidate.optString("window", "")
                            val cId = candidate.optString("bucketId", "")
                            val cName = candidate.optString("displayName", "")
                            !(cWin.equals("5h", ignoreCase = true) ||
                                    cId.contains("5h", ignoreCase = true) ||
                                    cName.contains("5h", ignoreCase = true) ||
                                    cName.contains("Five Hour", ignoreCase = true))
                        }
                        for (j in 0 until bArr.length()) {
                            val bObj = bArr.getJSONObject(j)
                            val bId = bObj.optString("bucketId", "bucket-$j")
                            val bName = bObj.optString("displayName", "Limit Remaining")
                            val bWin = bObj.optString("window", "")
                            val bReset = if (bObj.has("resetTime")) bObj.optString("resetTime").takeIf { it.isNotBlank() } else null
                            val bDesc = bObj.optString("description", "")
                            val bRem = bObj.optDouble("remainingFraction", 0.0)
                            val bDis = bObj.optBoolean("disabled", false)
                            val bSub = formatSubtext(bReset)

                            val is5h = bWin.equals("5h", ignoreCase = true) ||
                                    bId.contains("5h", ignoreCase = true) ||
                                    bName.contains("5h", ignoreCase = true) ||
                                    bName.contains("Five Hour", ignoreCase = true)
                            val isWeekly = bWin.equals("weekly", ignoreCase = true) ||
                                    bWin.contains("weekly", ignoreCase = true) ||
                                    bWin.equals("7d", ignoreCase = true) ||
                                    bWin.equals("168h", ignoreCase = true) ||
                                    bId.contains("weekly", ignoreCase = true) ||
                                    bName.contains("weekly", ignoreCase = true)

                            val isBucketArgon = isArgon || bId.contains("argon", ignoreCase = true) || bName.contains("argon", ignoreCase = true)
                            if (isBucketArgon) {
                                if (is5h && hasNon5hBucket) {
                                    continue
                                }
                                if (isWeekly || bWin.isBlank() || !is5h || !hasNon5hBucket) {
                                    argonWeeklyRem = bRem
                                    if (bReset != null) argonWeeklyReset = bReset
                                }
                            } else if (isGemini) {
                                if (isWeekly) {
                                    geminiWeeklyRem = bRem
                                    if (bReset != null) geminiWeeklyReset = bReset
                                } else if (is5h) {
                                    gemini5hRem = bRem
                                    if (bReset != null) gemini5hReset = bReset
                                }
                            } else if (is3p) {
                                if (isWeekly || bWin.isBlank()) {
                                    thirdPartyWeeklyRem = bRem
                                    if (bReset != null) thirdPartyWeeklyReset = bReset
                                }
                            }

                            bucketsList.add(
                                QuotaBucket(
                                    bucketId = bId,
                                    displayName = bName,
                                    window = if (isBucketArgon && !isWeekly) "weekly" else bWin,
                                    resetTime = bReset,
                                    description = bDesc,
                                    remainingFraction = bRem,
                                    disabled = bDis,
                                    subtext = bSub
                                )
                            )
                        }
                    }

                    val rawDesc = gObj.optString("description", "")
                    val groupDesc = if (rawDesc.isNotBlank()) rawDesc else if (groupSharedModels.isNotEmpty()) "Models within this group: ${groupSharedModels.joinToString(", ")}" else ""
                    groupsList.add(
                        QuotaGroup(
                            displayName = gName,
                            description = groupDesc,
                            sharedModels = groupSharedModels,
                            buckets = bucketsList
                        )
                    )
                }
            }

            if (modelsMap != null) {
                val it = modelsMap.keys()
                while (it.hasNext()) {
                    val mid = it.next()
                    val mObj = modelsMap.optJSONObject(mid) ?: continue
                    val prov = modelProviderName(mObj)
                    if (prov == "Google") {
                        val isArgon = isArgonModel(mid, modelTitle(mObj, mid))
                        val qi = mObj.optJSONObject("quotaInfo")
                        if (qi != null) {
                            val rt = if (qi.has("resetTime")) qi.optString("resetTime").takeIf { it.isNotBlank() } else null
                            val rem = when {
                                qi.has("remainingFraction") -> qi.optDouble("remainingFraction")
                                rt != null -> 0.0
                                else -> null
                            }
                            if (isArgon) {
                                if (rem != null && (argonWeeklyRem == null || rem < argonWeeklyRem!!)) {
                                    argonWeeklyRem = rem
                                }
                                if (rt != null && argonWeeklyReset == null) {
                                    argonWeeklyReset = rt
                                }
                            } else {
                                if (rem != null && (gemini5hRem == null || rem < gemini5hRem!!)) {
                                    gemini5hRem = rem
                                }
                                if (rt != null && gemini5hReset == null) {
                                    gemini5hReset = rt
                                }
                            }
                        }
                    }
                }
            }

            val hasGeminiGroup = groupsList.any { it.displayName.contains("gemini", ignoreCase = true) && !it.displayName.contains("argon", ignoreCase = true) }
            if (!hasGeminiGroup) {
                val googleShared = sharedModelTitles(orderedIds, modelsMap, googleGroup = true, decodedCliModels)
                if (googleShared.isNotEmpty() && (gemini5hRem != null || geminiWeeklyRem != null || gemini5hReset != null || geminiWeeklyReset != null)) {
                    val fiveHourFraction = gemini5hRem ?: geminiWeeklyRem ?: 0.0
                    val fiveHourReset = gemini5hReset
                    val weeklyFraction = geminiWeeklyRem ?: fiveHourFraction
                    val weeklyReset = geminiWeeklyReset ?: fiveHourReset

                    val buckets = mutableListOf<QuotaBucket>()
                    buckets.add(
                        QuotaBucket(
                            bucketId = "gemini-weekly",
                            displayName = "Weekly Limit Remaining",
                            window = "weekly",
                            resetTime = weeklyReset,
                            description = if (!weeklyReset.isNullOrBlank()) {
                                "You have used some of your weekly limit, it will fully refresh in ${formatResetCountdown(weeklyReset)}."
                            } else {
                                "Your individual tier weekly quota."
                            },
                            remainingFraction = weeklyFraction,
                            disabled = false,
                            subtext = formatSubtext(weeklyReset)
                        )
                    )
                    buckets.add(
                        QuotaBucket(
                            bucketId = "gemini-5h",
                            displayName = "Five Hour Limit Remaining",
                            window = "5h",
                            resetTime = fiveHourReset,
                            description = if (!fiveHourReset.isNullOrBlank()) {
                                "You have used some of your 5-hour limit, it will fully refresh in ${formatResetCountdown(fiveHourReset)}."
                            } else {
                                "Resets in 5 hours"
                            },
                            remainingFraction = fiveHourFraction,
                            disabled = false,
                            subtext = formatSubtext(fiveHourReset)
                        )
                    )

                    val geminiGroup = QuotaGroup(
                        displayName = "Gemini Models",
                        description = "Models within this group: ${googleShared.joinToString(", ")}",
                        sharedModels = googleShared,
                        buckets = buckets
                    )
                    groupsList.add(0, geminiGroup)
                }
            }

            val cliArgonTitles = decodedCliModels
                .filter { isArgonModel(it.cliId, it.displayName) }
                .map { it.displayName.replace(EFFORT_TITLE_REGEX, "").trim() }
                .filter { it.isNotBlank() }
                .distinct()

            val hasArgonGroup = groupsList.any { it.displayName.contains("argon", ignoreCase = true) }
            val argonShared = argonModelTitles(orderedIds, modelsMap, decodedCliModels)
            val isArgonSupported = hasArgonGroup || argonShared.isNotEmpty() || cliArgonTitles.isNotEmpty()

            val resolvedArgonReset = argonWeeklyReset ?: geminiWeeklyReset
            val resolvedArgonRem = argonWeeklyRem
                ?: (if (argonWeeklyReset != null) 0.0 else null)
                ?: geminiWeeklyRem

            if (!isArgonSupported) {
                groupsList.removeAll { it.displayName.contains("argon", ignoreCase = true) }
            } else if (resolvedArgonRem != null) {
                val argonTitlesList = argonShared.ifEmpty { cliArgonTitles }.ifEmpty { listOf("Gemini 4 Argon") }
                val rawGroupTitle = argonTitlesList.firstOrNull() ?: "Gemini 4 Argon"
                val displayName = if (rawGroupTitle.contains("argon", ignoreCase = true)) rawGroupTitle else "Gemini 4 Argon"
                val aWeeklyFraction = resolvedArgonRem
                val aWeeklyReset = resolvedArgonReset

                val defaultArgonBucket = QuotaBucket(
                    bucketId = "argon-weekly",
                    displayName = "Weekly Limit Remaining",
                    window = "weekly",
                    resetTime = aWeeklyReset,
                    description = if (!aWeeklyReset.isNullOrBlank()) {
                        "You have used some of your Gemini 4 Argon limit, it will fully refresh in ${formatResetCountdown(aWeeklyReset)}."
                    } else {
                        "Individual Gemini 4 Argon weekly quota."
                    },
                    remainingFraction = aWeeklyFraction,
                    disabled = false,
                    subtext = formatSubtext(aWeeklyReset)
                )

                if (!hasArgonGroup) {
                    val argonGroup = QuotaGroup(
                        displayName = displayName,
                        description = "Models within this group: ${argonTitlesList.joinToString(", ")}",
                        sharedModels = argonTitlesList,
                        buckets = listOf(defaultArgonBucket)
                    )
                    val insertIdx = if (groupsList.isNotEmpty()) 1.coerceAtMost(groupsList.size) else 0
                    groupsList.add(insertIdx, argonGroup)
                } else {
                    for (idx in groupsList.indices) {
                        val g = groupsList[idx]
                        if (g.displayName.contains("argon", ignoreCase = true) && g.buckets.isEmpty()) {
                            groupsList[idx] = g.copy(
                                sharedModels = g.sharedModels.ifEmpty { argonTitlesList },
                                description = g.description?.ifBlank { null } ?: "Models within this group: ${argonTitlesList.joinToString(", ")}",
                                buckets = listOf(defaultArgonBucket)
                            )
                        }
                    }
                }
            }

            val pickerModels: List<CliModel> = if (modelsMap != null) {
                buildPickerModels(orderedIds, modelsMap)
            } else {
                if (modelsFetchFailed) {
                    Log.w(TAG, "Model list could not be fetched; no models are listed")
                }
                emptyList()
            }

            val availableModels = buildAvailableModels(
                orderedIds, modelsMap,
                decodedCliModels,
                gemini5hRem ?: geminiWeeklyRem, gemini5hReset ?: geminiWeeklyReset,
                if (!isArgonSupported) null else resolvedArgonRem,
                if (!isArgonSupported) null else resolvedArgonReset,
                thirdPartyWeeklyRem, thirdPartyWeeklyReset
            )

            val summaryDesc = if (json.has("description")) json.optString("description") else null
            if (groupsList.isNotEmpty()) {
                UserQuotaSummary(
                    groups = groupsList,
                    availableModels = availableModels,
                    description = summaryDesc,
                    modelsFetchFailed = modelsFetchFailed,
                    pickerModels = pickerModels
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse quota response: ${e.message}", e)
            null
        }
    }

    private suspend fun fetchFromLocalLanguageServer(context: Context? = null, tier: CodeAssistTier? = null): UserQuotaSummary? {
        val candidates = getCandidateEndpoints(context)
        for ((port, initialCsrf) in candidates) {
            var activeCsrf = initialCsrf
            var respStr: String? = null
            var modelsRespStr: String? = null

            val sc = cachedSslContext
            val allHostsValid = HostnameVerifier { hostname, session -> true }

            try {
                if (sc != null) {
                    val url = URL("https://127.0.0.1:$port/exa.language_server_pb.LanguageServerService/RetrieveUserQuotaSummary")
                    val conn = url.openConnection() as HttpsURLConnection
                    conn.sslSocketFactory = sc.socketFactory
                    conn.hostnameVerifier = allHostsValid
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.connectTimeout = 4000
                    conn.readTimeout = 4000
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("x-codeium-csrf-token", activeCsrf)
                    conn.setRequestProperty("Connect-Protocol-Version", "1")

                    OutputStreamWriter(conn.outputStream).use { it.write("{}"); it.flush() }

                    val code = conn.responseCode
                    if (code in 200..299) {
                        respStr = conn.inputStream.bufferedReader().use { it.readText() }
                    } else {
                        Log.w(TAG, "Local server candidate $port HTTP response: $code")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Local server candidate $port HttpsURLConnection failed: ${e.message}")
            }

            if (respStr.isNullOrBlank() || !respStr.contains("\"groups\"")) {
                respStr = queryLocalEndpointViaCurl(port, activeCsrf, "RetrieveUserQuotaSummary", "{}", context)
            }

            if (respStr.isNullOrBlank() || !respStr.contains("\"groups\"")) {
                val liveCsrf = fetchCsrfTokenFromPort(port, context)
                if (!liveCsrf.isNullOrBlank() && liveCsrf != activeCsrf) {
                    activeCsrf = liveCsrf
                    persistEndpoint(port, liveCsrf, context)
                    respStr = queryLocalEndpointViaCurl(port, activeCsrf, "RetrieveUserQuotaSummary", "{}", context)
                }
            }

            if (!respStr.isNullOrBlank() && respStr.contains("\"groups\"")) {
                try {
                    if (sc != null) {
                        val urlM = URL("https://127.0.0.1:$port/exa.language_server_pb.LanguageServerService/GetAvailableModels")
                        val connM = urlM.openConnection() as HttpsURLConnection
                        connM.sslSocketFactory = sc.socketFactory
                        connM.hostnameVerifier = allHostsValid
                        connM.requestMethod = "POST"
                        connM.doOutput = true
                        connM.connectTimeout = 4000
                        connM.readTimeout = 4000
                        connM.setRequestProperty("Content-Type", "application/json")
                        connM.setRequestProperty("x-codeium-csrf-token", activeCsrf)
                        connM.setRequestProperty("Connect-Protocol-Version", "1")

                        OutputStreamWriter(connM.outputStream).use { it.write("{\"forceRefresh\": true}"); it.flush() }

                        val codeM = connM.responseCode
                        if (codeM in 200..299) {
                            modelsRespStr = connM.inputStream.bufferedReader().use { it.readText() }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Local server candidate $port GetAvailableModels failed: ${e.message}")
                }

                if (modelsRespStr.isNullOrBlank()) {
                    modelsRespStr = queryLocalEndpointViaCurl(port, activeCsrf, "GetAvailableModels", "{\"forceRefresh\": true}", context)
                }

                var modelsFetchFailed = false
                if (modelsRespStr.isNullOrBlank() && context != null) {
                    try {
                        OAuthManager.refreshTokenIfNeeded(context)
                        val token = OAuthManager.getAccessToken(context)
                        if (!token.isNullOrBlank()) {
                            modelsRespStr = makePostRequestWithFallback(MODELS_PATH, token)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Remote fetchAvailableModels retry failed: ${e.message}")
                    }
                }
                if (modelsRespStr.isNullOrBlank()) {
                    modelsFetchFailed = true
                    Log.w(TAG, "Local server candidate $port: model list could not be fetched (local GetAvailableModels and remote fetchAvailableModels both failed)")
                }

                val summary = parseQuotaSummaryResponse(respStr, modelsRespStr, tier, modelsFetchFailed, context)
                if (summary != null && summary.groups.isNotEmpty()) {
                    Log.i(TAG, "Successfully loaded live quota from local server on port $port (${summary.groups.size} groups, models: ${summary.availableModels.size})")
                    persistEndpoint(port, activeCsrf, context)
                    return summary
                }
            }
        }
        return null
    }

    private fun makePostRequestWithFallback(path: String, token: String): String? {
        for (baseUrl in CLOUDCODE_API_BASES) {
            try {
                val resp = makePostRequest("$baseUrl$path", token)
                if (!resp.isNullOrBlank()) {
                    return resp
                }
            } catch (e: Exception) {
                Log.w(TAG, "Request to $baseUrl$path threw exception: ${e.message}")
            }
        }
        return null
    }

    private fun makePostRequest(urlStr: String, token: String): String? {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("User-Agent", "antigravity")

        OutputStreamWriter(conn.outputStream).use { it.write("{}"); it.flush() }

        val code = conn.responseCode
        return if (code in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
            Log.w(TAG, "Request to $urlStr failed ($code): $err")
            null
        }
    }

    fun restartLocalLanguageServer(context: Context? = null) {
        val candidates = getCandidateEndpoints(context)
        for ((port, csrf) in candidates) {
            try {
                val sc = cachedSslContext ?: continue
                val url = URL("https://127.0.0.1:$port/exa.language_server_pb.LanguageServerService/Restart")
                val conn = url.openConnection() as HttpsURLConnection
                conn.sslSocketFactory = sc.socketFactory
                conn.hostnameVerifier = HostnameVerifier { hostname, session -> true }
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("x-codeium-csrf-token", csrf)
                conn.setRequestProperty("Connect-Protocol-Version", "1")
                OutputStreamWriter(conn.outputStream).use { it.write("{}"); it.flush() }
                val code = conn.responseCode
                Log.i(TAG, "Notified LanguageServer on port $port to restart (HTTP $code)")
            } catch (e: Exception) {

            }
        }
    }

    suspend fun fetchQuotaSummary(context: Context, preloadedTier: CodeAssistTier? = null): UserQuotaSummary? = withContext(Dispatchers.IO) {
        val tier = preloadedTier ?: fetchCodeAssistTier(context)

        val localSummary = fetchFromLocalLanguageServer(context, tier)
        if (localSummary != null && localSummary.groups.isNotEmpty()) {
            Log.i(TAG, "Priority 1: Successfully retrieved live quota from local Language Server (${localSummary.groups.size} groups)")
            return@withContext localSummary
        }

        OAuthManager.refreshTokenIfNeeded(context)
        val token = OAuthManager.getAccessToken(context)

        if (!token.isNullOrBlank()) {
            for (baseUrl in CLOUDCODE_API_BASES) {
                try {
                    val modelsRespStr = makePostRequest("$baseUrl$MODELS_PATH", token)
                    val quotaRespStr = makePostRequest("$baseUrl$QUOTA_PATH", token)
                    if (quotaRespStr != null || modelsRespStr != null) {
                        val summary = parseQuotaSummaryResponse(
                            respStr = quotaRespStr ?: "{}",
                            modelsRespStr = modelsRespStr,
                            tier = tier,
                            modelsFetchFailed = modelsRespStr == null,
                            context = context
                        )
                        if (summary != null && summary.groups.isNotEmpty()) {
                            Log.i(TAG, "Successfully loaded live quota from $baseUrl for current token (${summary.groups.size} groups)")
                            return@withContext summary
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load live quota from $baseUrl: ${e.message}")
                }
            }
        }

        null
    }

    suspend fun fetchCodeAssistTier(context: Context): CodeAssistTier? = withContext(Dispatchers.IO) {
        OAuthManager.refreshTokenIfNeeded(context)
        val token = OAuthManager.getAccessToken(context) ?: return@withContext null

        try {
            val respStr = makePostRequestWithFallback(TIER_PATH, token) ?: return@withContext null
            val json = JSONObject(respStr)

            var curTier = TierInfo()
            if (json.has("currentTier")) {
                val cObj = json.getJSONObject("currentTier")
                curTier = TierInfo(
                    id = cObj.optString("id", ""),
                    name = cObj.optString("name", "Gemini Code Assist"),
                    description = cObj.optString("description", ""),
                    upgradeSubscriptionUri = cObj.optString("upgradeSubscriptionUri", ""),
                    upgradeSubscriptionText = cObj.optString("upgradeSubscriptionText", "")
                )
            } else if (json.has("allowedTiers")) {
                val arr = json.getJSONArray("allowedTiers")
                if (arr.length() > 0) {
                    val cObj = arr.getJSONObject(0)
                    curTier = TierInfo(
                        id = cObj.optString("id", "standard-tier"),
                        name = cObj.optString("name", "Gemini Code Assist"),
                        description = cObj.optString("description", ""),
                        upgradeSubscriptionUri = cObj.optString("upgradeSubscriptionUri", ""),
                        upgradeSubscriptionText = cObj.optString("upgradeSubscriptionText", "")
                    )
                }
            }

            var paidTier: TierInfo? = null
            if (json.has("paidTier")) {
                val pObj = json.getJSONObject("paidTier")
                paidTier = TierInfo(
                    id = pObj.optString("id", ""),
                    name = pObj.optString("name", "Google AI Pro"),
                    description = pObj.optString("description", ""),
                    upgradeSubscriptionUri = pObj.optString("upgradeSubscriptionUri", ""),
                    upgradeSubscriptionText = pObj.optString("upgradeSubscriptionText", "")
                )
            }

            val resultTier = CodeAssistTier(currentTier = curTier, paidTier = paidTier)
            val email = OAuthManager.getUserEmail(context)
            if (!email.isNullOrBlank()) {
                id.vern.antigravity.oauth.AccountManager.updateAccountTier(
                    context,
                    email,
                    resultTier.normalizedTierString
                )
            }
            resultTier
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching code assist tier: ${e.message}", e)
            null
        }
    }
}

