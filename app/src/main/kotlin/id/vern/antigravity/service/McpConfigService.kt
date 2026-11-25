package id.vern.antigravity.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class McpServerItem(
    val name: String,
    val command: String = "",
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val url: String = "",
    val source: String = "global",
    val isRemote: Boolean = false,
    val isEnabled: Boolean = true
) {
    val displayTarget: String
        get() = if (isRemote) url else "$command ${args.joinToString(" ")}".trim()
}

object McpConfigService {
    private const val TAG = "McpConfigService"

    private fun getCandidateFiles(context: Context): List<File> {
        val list = mutableListOf<File>()
        list.add(File(context.filesDir, ".gemini/config/mcp_config.json"))
        list.add(File(context.filesDir, "home/.gemini/config/mcp_config.json"))
        return list
    }

    fun getPrimaryConfigFile(context: Context): File {
        for (f in getCandidateFiles(context)) {
            if (f.exists() && f.canWrite()) return f
        }
        val defaultFile = getCandidateFiles(context).first()
        defaultFile.parentFile?.mkdirs()
        return defaultFile
    }

    suspend fun loadMcpServers(context: Context): List<McpServerItem> = withContext(Dispatchers.IO) {
        val servers = mutableListOf<McpServerItem>()
        val primaryFile = getPrimaryConfigFile(context)

        if (primaryFile.exists() && primaryFile.canRead()) {
            try {
                val text = primaryFile.readText(Charsets.UTF_8).trim()
                if (text.isNotBlank() && text.startsWith("{")) {
                    val root = JSONObject(text)
                    val mcpServers = root.optJSONObject("mcpServers") ?: JSONObject()
                    val keys = mcpServers.keys()
                    while (keys.hasNext()) {
                        val name = keys.next()
                        val cfg = mcpServers.optJSONObject(name) ?: continue
                        val url = cfg.optString("url", cfg.optString("serverUrl", "")).trim()
                        val cmd = cfg.optString("command", "").trim()
                        val argsList = mutableListOf<String>()
                        val argsArr = cfg.optJSONArray("args")
                        if (argsArr != null) {
                            for (i in 0 until argsArr.length()) {
                                argsList.add(argsArr.optString(i, ""))
                            }
                        }
                        val envMap = mutableMapOf<String, String>()
                        val envObj = cfg.optJSONObject("env")
                        if (envObj != null) {
                            val envKeys = envObj.keys()
                            while (envKeys.hasNext()) {
                                val ek = envKeys.next()
                                envMap[ek] = envObj.optString(ek, "")
                            }
                        }
                        val isRemote = url.isNotBlank()
                        val disabled = cfg.optBoolean("disabled", false)
                        servers.add(
                            McpServerItem(
                                name = name,
                                command = cmd,
                                args = argsList,
                                env = envMap,
                                url = url,
                                source = "Global",
                                isRemote = isRemote,
                                isEnabled = !disabled
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse mcp_config.json: ${e.message}", e)
            }
        }

        val configDir = primaryFile.parentFile
        val pluginsDir = File(configDir, "plugins")
        if (pluginsDir.exists() && pluginsDir.isDirectory) {
            pluginsDir.listFiles { f -> f.isDirectory }?.forEach { pDir ->
                val pMcp = File(pDir, "mcp_config.json")
                if (pMcp.exists() && pMcp.canRead()) {
                    try {
                        val pText = pMcp.readText(Charsets.UTF_8).trim()
                        if (pText.isNotBlank() && pText.startsWith("{")) {
                            val pRoot = JSONObject(pText)
                            val pServers = pRoot.optJSONObject("mcpServers") ?: JSONObject()
                            val pKeys = pServers.keys()
                            while (pKeys.hasNext()) {
                                val name = pKeys.next()
                                if (servers.any { it.name.equals(name, ignoreCase = true) }) continue
                                val cfg = pServers.optJSONObject(name) ?: continue
                                val url = cfg.optString("url", "").trim()
                                val cmd = cfg.optString("command", "").trim()
                                val argsList = mutableListOf<String>()
                                val argsArr = cfg.optJSONArray("args")
                                if (argsArr != null) {
                                    for (i in 0 until argsArr.length()) {
                                        argsList.add(argsArr.optString(i, ""))
                                    }
                                }
                                servers.add(
                                    McpServerItem(
                                        name = name,
                                        command = cmd,
                                        args = argsList,
                                        url = url,
                                        source = "Plugin (${pDir.name})",
                                        isRemote = url.isNotBlank(),
                                        isEnabled = !cfg.optBoolean("disabled", false)
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {}
                }
            }
        }

        servers
    }

    suspend fun saveMcpServer(context: Context, server: McpServerItem): Boolean = withContext(Dispatchers.IO) {
        val primaryFile = getPrimaryConfigFile(context)
        try {
            primaryFile.parentFile?.mkdirs()
            val root = if (primaryFile.exists() && primaryFile.length() > 0) {
                val t = primaryFile.readText(Charsets.UTF_8).trim()
                if (t.startsWith("{")) JSONObject(t) else JSONObject()
            } else {
                JSONObject()
            }

            val mcpServers = root.optJSONObject("mcpServers") ?: JSONObject().also { root.put("mcpServers", it) }
            val serverObj = JSONObject()
            if (server.isRemote) {
                serverObj.put("url", server.url.trim())
            } else {
                serverObj.put("command", server.command.trim())
                if (server.args.isNotEmpty()) {
                    val arr = JSONArray()
                    server.args.forEach { arr.put(it) }
                    serverObj.put("args", arr)
                }
                if (server.env.isNotEmpty()) {
                    val envObj = JSONObject()
                    server.env.forEach { (k, v) -> envObj.put(k, v) }
                    serverObj.put("env", envObj)
                }
            }
            if (!server.isEnabled) {
                serverObj.put("disabled", true)
            }

            mcpServers.put(server.name.trim(), serverObj)
            val jsonStr = root.toString(2)

            for (target in getCandidateFiles(context)) {
                try {
                    target.parentFile?.mkdirs()
                    target.writeText(jsonStr, Charsets.UTF_8)
                    target.setReadable(true, false)
                    target.setWritable(true, false)
                } catch (e: Exception) {}
            }
            Log.i(TAG, "Saved MCP server ${server.name} to mcp_config.json")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save MCP server: ${e.message}", e)
            false
        }
    }

    suspend fun deleteMcpServer(context: Context, serverName: String): Boolean = withContext(Dispatchers.IO) {
        val primaryFile = getPrimaryConfigFile(context)
        try {
            if (!primaryFile.exists()) return@withContext true
            val text = primaryFile.readText(Charsets.UTF_8).trim()
            if (!text.startsWith("{")) return@withContext true
            val root = JSONObject(text)
            val mcpServers = root.optJSONObject("mcpServers") ?: return@withContext true
            mcpServers.remove(serverName.trim())
            val jsonStr = root.toString(2)

            for (target in getCandidateFiles(context)) {
                try {
                    target.writeText(jsonStr, Charsets.UTF_8)
                    target.setReadable(true, false)
                    target.setWritable(true, false)
                } catch (e: Exception) {}
            }
            Log.i(TAG, "Deleted MCP server $serverName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete MCP server: ${e.message}", e)
            false
        }
    }

    suspend fun loadRawConfig(context: Context): String = withContext(Dispatchers.IO) {
        val file = getPrimaryConfigFile(context)
        if (file.exists() && file.canRead()) {
            file.readText(Charsets.UTF_8)
        } else {
            "{\n  \"mcpServers\": {}\n}"
        }
    }

    suspend fun saveRawConfig(context: Context, rawJson: String): Boolean = withContext(Dispatchers.IO) {
        try {

            JSONObject(rawJson)
            val primaryFile = getPrimaryConfigFile(context)
            for (target in getCandidateFiles(context)) {
                try {
                    target.parentFile?.mkdirs()
                    target.writeText(rawJson, Charsets.UTF_8)
                    target.setReadable(true, false)
                    target.setWritable(true, false)
                } catch (e: Exception) {}
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Invalid JSON in saveRawConfig: ${e.message}", e)
            false
        }
    }
}

