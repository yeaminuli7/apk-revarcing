package id.vern.antigravity.viewmodel

import android.content.Context
import android.system.Os
import android.util.Log
import id.vern.antigravity.executor.EngineExecutor
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import id.vern.antigravity.util.NetworkDownloader

object CustomizationTracker {
    private const val PREFS_NAME = "antigravity_prefs"
    private const val KEY_CUSTOMIZATION_TRACKING = "ag_customization_tracking"

    fun normalizeGitRepoUrl(rawUrl: String): Pair<String, String?> {
        var clean = rawUrl.trim()
        if (clean.isBlank()) return Pair("", null)
        if (!clean.startsWith("http://") && !clean.startsWith("https://") && !clean.startsWith("git@")) {
            clean = "https://github.com/" + clean.trimStart('/')
        }
        val treeRegex = Regex("""^(https?://[^/]+/[^/]+/[^/]+)/(?:tree|blob)/([^/]+)(?:/(.*))?$""")
        val match = treeRegex.find(clean)
        if (match != null) {
            var baseRepo = match.groupValues[1]
            if (!baseRepo.endsWith(".git")) {
                baseRepo = "$baseRepo.git"
            }
            var subpath = match.groupValues.getOrNull(3)?.trim('/')?.ifEmpty { null }
            if (subpath != null && subpath.lowercase().endsWith(".md")) {
                val parent = subpath.substringBeforeLast('/', "")
                subpath = parent.ifEmpty { null }
            }
            return Pair(baseRepo, subpath)
        }
        clean = clean.trimEnd('/')
        if (!clean.endsWith(".git") && !clean.contains("@")) {
            clean = "$clean.git"
        }
        return Pair(clean, null)
    }

    fun prepareGitEnv(context: Context): String {
        val filesDir = context.filesDir
        val prefix = File(filesDir, "usr")
        val binDir = File(prefix, "bin")
        val gitExecDir = File(prefix, "libexec/git-core")
        val templateDir = File(prefix, "share/git-core/templates").apply { mkdirs() }
        val bashBin = File(binDir, "bash")
        if (bashBin.exists()) {
            try { Os.chmod(bashBin.absolutePath, 493) } catch (e: Exception) {}
        }
        val gitBin = File(binDir, "git")
        if (gitBin.exists()) {
            try { Os.chmod(gitBin.absolutePath, 493) } catch (e: Exception) {}
        }
        if (gitExecDir.isDirectory) {
            gitExecDir.listFiles()?.forEach { file ->
                file.setReadable(true, false)
                file.setExecutable(true, false)
                try { Os.chmod(file.absolutePath, 493) } catch (e: Exception) {}
            }
        }

        val gitExecPath = gitExecDir.absolutePath
        val certFile = File(prefix, "etc/tls/cert.pem")
        val certPath = certFile.absolutePath

        try {
            EngineExecutor.healEnvironment(context)
        } catch (e: Exception) {}

        val ensureGitCmd = "if ! command -v git >/dev/null 2>&1 || [ ! -s '$certPath' ]; then echo '[Agent] Ensuring git and ca-certificates are installed...' && pkg update 2>/dev/null && pkg install git ca-certificates; fi && "
        val exportCert = "export SSL_CERT_FILE='$certPath' && export CURL_CA_BUNDLE='$certPath' && export GIT_SSL_CAINFO='$certPath' && export OPENSSL_CONF='${prefix.absolutePath}/etc/tls/openssl.cnf' && "
        val configGitCert = "if command -v git >/dev/null 2>&1 && [ -f '$certPath' ]; then git config --global http.sslCAInfo '$certPath' 2>/dev/null || true; git config --global http.sslVerify true 2>/dev/null || true; fi && "

        return "export GIT_TERMINAL_PROMPT=0 && export GIT_CONFIG_NOSYSTEM=1 && export GIT_ATTR_NOSYSTEM=1 && export GIT_EXEC_PATH='$gitExecPath' && export GIT_TEMPLATE_DIR='${templateDir.absolutePath}' && export PATH=\"${binDir.absolutePath}:$gitExecPath:\$PATH\" && $ensureGitCmd$exportCert$configGitCert"
    }

    fun ensureSkillFiles(skillDir: File, homeSkillDir: File, id: String, name: String, desc: String) {
        try {
            skillDir.mkdirs()
            val skillMd = File(skillDir, "SKILL.md")
            if (!skillMd.exists()) {
                val foundSkillMd = skillDir.walkTopDown()
                    .filter { it.isFile && it.name.equals("SKILL.md", ignoreCase = true) && !it.absolutePath.contains("/.git/") }
                    .firstOrNull()
                if (foundSkillMd != null && foundSkillMd.parentFile != null && foundSkillMd.parentFile != skillDir) {
                    val srcDir = foundSkillMd.parentFile!!
                    srcDir.copyRecursively(skillDir, overwrite = true)
                }
            }
            if (!skillMd.exists()) {
                val safeDesc = desc.ifBlank { name }
                skillMd.writeText("---\nname: $id\ndescription: $safeDesc\n---\n\n# $name\n\n$safeDesc\n")
            }
            homeSkillDir.mkdirs()
            skillDir.copyRecursively(homeSkillDir, overwrite = true)
        } catch (e: Exception) {
            Log.w("CustomizationTracker", "Failed ensuring skill files: ${e.message}")
        }
    }

    fun ensurePluginFiles(pluginDir: File, homePluginDir: File, id: String, name: String, desc: String) {
        try {
            pluginDir.mkdirs()
            val pJson = File(pluginDir, "plugin.json")
            if (!pJson.exists()) {
                val candidatePluginJson = File(pluginDir, ".antigravity-plugin/plugin.json")
                val candidateClaudeJson = File(pluginDir, ".claude-plugin/plugin.json")
                if (candidatePluginJson.exists()) {
                    candidatePluginJson.copyTo(pJson, overwrite = true)
                } else if (candidateClaudeJson.exists()) {
                    candidateClaudeJson.copyTo(pJson, overwrite = true)
                } else {
                    val safeDesc = desc.ifBlank { name }.replace("\"", "\\\"").replace("\n", " ")
                    pJson.writeText("{\n  \"name\": \"$id\",\n  \"version\": \"1.0.0\",\n  \"description\": \"$safeDesc\"\n}\n")
                }
            }
            val rulesDir = File(pluginDir, "rules").apply { mkdirs() }
            val agentsMd = File(rulesDir, "AGENTS.md")
            if (!agentsMd.exists()) {
                val candidateRules = listOf(
                    File(pluginDir, "AGENTS.md"),
                    File(pluginDir, "GEMINI.md"),
                    File(pluginDir, "CLAUDE.md"),
                    File(pluginDir, "README.md")
                )
                val existingCandidate = candidateRules.firstOrNull { it.exists() && it.length() > 0 }
                if (existingCandidate != null) {
                    existingCandidate.copyTo(agentsMd, overwrite = true)
                } else {
                    val firstMd = rulesDir.listFiles()?.firstOrNull { it.isFile && it.extension.equals("md", ignoreCase = true) }
                    if (firstMd != null) {
                        firstMd.copyTo(agentsMd, overwrite = true)
                    } else {
                        agentsMd.writeText("# $name\n\n$desc\n")
                    }
                }
            }
            homePluginDir.mkdirs()
            pluginDir.copyRecursively(homePluginDir, overwrite = true)
        } catch (e: Exception) {
            Log.w("CustomizationTracker", "Failed ensuring plugin files: ${e.message}")
        }
    }

    fun getTrackedCustomizations(context: Context): Map<String, CustomizationRepoTrack> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_CUSTOMIZATION_TRACKING, null) ?: return emptyMap()
        val result = mutableMapOf<String, CustomizationRepoTrack>()
        try {
            val root = JSONObject(raw)
            val keys = root.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val obj = root.optJSONObject(k) ?: continue
                result[k.lowercase()] = CustomizationRepoTrack(
                    id = obj.optString("id", k),
                    name = obj.optString("name", k),
                    repoUrl = obj.optString("repoUrl", ""),
                    isPlugin = obj.optBoolean("isPlugin", false),
                    subPath = if (obj.has("subPath") && !obj.isNull("subPath")) obj.optString("subPath") else null,
                    installedSha = obj.optString("installedSha", ""),
                    remoteSha = if (obj.has("remoteSha") && !obj.isNull("remoteSha")) obj.optString("remoteSha") else null,
                    lastChecked = obj.optLong("lastChecked", 0L),
                    hasUpdate = obj.optBoolean("hasUpdate", false)
                )
            }
        } catch (e: Exception) {
            Log.e("CustomizationTracker", "Failed parsing customization tracks", e)
        }
        return result
    }

    fun saveTrackedCustomizations(context: Context, tracks: Map<String, CustomizationRepoTrack>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        try {
            val root = JSONObject()
            for ((k, track) in tracks) {
                val obj = JSONObject().apply {
                    put("id", track.id)
                    put("name", track.name)
                    put("repoUrl", track.repoUrl)
                    put("isPlugin", track.isPlugin)
                    put("subPath", track.subPath)
                    put("installedSha", track.installedSha)
                    put("remoteSha", track.remoteSha)
                    put("lastChecked", track.lastChecked)
                    put("hasUpdate", track.hasUpdate)
                }
                root.put(k.lowercase(), obj)
            }
            prefs.edit().putString(KEY_CUSTOMIZATION_TRACKING, root.toString()).apply()
        } catch (e: Exception) {
            Log.e("CustomizationTracker", "Failed saving customization tracks", e)
        }
    }

    fun registerTrackedCustomization(context: Context, track: CustomizationRepoTrack) {
        val current = getTrackedCustomizations(context).toMutableMap()
        current[track.id.lowercase()] = track
        saveTrackedCustomizations(context, current)
    }

    fun unregisterTrackedCustomization(context: Context, id: String) {
        val current = getTrackedCustomizations(context).toMutableMap()
        current.remove(id.lowercase())
        saveTrackedCustomizations(context, current)
    }

    fun fetchLatestRepoCommitSha(repoUrl: String, subPath: String? = null): String? {
        try {
            val (cleanRepo, extractedSub) = normalizeGitRepoUrl(repoUrl)
            var clean = cleanRepo.trim()
            if (clean.startsWith("https://github.com/")) {
                clean = clean.removePrefix("https://github.com/")
            } else if (clean.startsWith("http://github.com/")) {
                clean = clean.removePrefix("http://github.com/")
            } else if (clean.startsWith("git@github.com:")) {
                clean = clean.removePrefix("git@github.com:")
            }
            clean = clean.removeSuffix(".git").trim('/')
            if (!clean.contains("/")) return null

            val effectiveSub = subPath?.ifBlank { null } ?: extractedSub
            val apiUrl = if (!effectiveSub.isNullOrBlank()) {
                val cleanSub = effectiveSub.trim('/')
                "https://api.github.com/repos/$clean/commits?path=$cleanSub&per_page=1"
            } else {
                "https://api.github.com/repos/$clean/commits?per_page=1"
            }

            val (code, text) = NetworkDownloader.httpGetString(
                url = apiUrl,
                headers = mapOf("Accept" to "application/vnd.github.v3+json"),
                userAgent = "Antigravity-Agent",
                connectTimeout = 8000,
                readTimeout = 8000
            )
            if (code in 200..299 && !text.isNullOrBlank()) {
                val array = JSONArray(text)
                if (array.length() > 0) {
                    val first = array.getJSONObject(0)
                    return first.optString("sha")
                }
            }
        } catch (e: Exception) {
            Log.w("CustomizationTracker", "Failed fetching commit sha for $repoUrl: ${e.message}")
        }
        return null
    }
}
