package id.vern.antigravity.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class RuleItem(
    val id: String,
    val name: String,
    val filePath: String,
    val content: String,
    val source: String,
    val isReadOnly: Boolean = false,
    val lineCount: Int = 0,
    val byteSize: Long = 0L
)

object RuleConfigService {
    private const val TAG = "RuleConfigService"

    private fun getCandidateBaseDirs(context: Context): List<File> {
        val list = mutableListOf<File>()
        list.add(File(context.filesDir, ".gemini/config"))
        list.add(File(context.filesDir, "home/.gemini/config"))
        return list
    }

    private fun getPrimaryConfigDir(context: Context): File {
        for (d in getCandidateBaseDirs(context)) {
            if (d.exists() && d.canWrite()) return d
        }
        val def = getCandidateBaseDirs(context).first()
        def.mkdirs()
        return def
    }

    suspend fun loadAllRules(context: Context, workspacePath: String?): List<RuleItem> = withContext(Dispatchers.IO) {
        val rules = mutableListOf<RuleItem>()
        val configDir = getPrimaryConfigDir(context)

        val globalAgents = File(configDir, "AGENTS.md")
        if (globalAgents.exists() && globalAgents.canRead()) {
            val content = try { globalAgents.readText(Charsets.UTF_8) } catch (e: Exception) { "" }
            rules.add(
                RuleItem(
                    id = "global_agents",
                    name = "Global AGENTS.md",
                    filePath = globalAgents.absolutePath,
                    content = content,
                    source = "Global",
                    isReadOnly = false,
                    lineCount = content.lines().size,
                    byteSize = globalAgents.length()
                )
            )
        }

        val globalGemini = File(configDir, "GEMINI.md")
        if (globalGemini.exists() && globalGemini.canRead()) {
            val content = try { globalGemini.readText(Charsets.UTF_8) } catch (e: Exception) { "" }
            rules.add(
                RuleItem(
                    id = "global_gemini",
                    name = "Global GEMINI.md",
                    filePath = globalGemini.absolutePath,
                    content = content,
                    source = "Global",
                    isReadOnly = false,
                    lineCount = content.lines().size,
                    byteSize = globalGemini.length()
                )
            )
        }

        val rulesDir = File(configDir, "rules")
        if (rulesDir.exists() && rulesDir.isDirectory) {
            rulesDir.listFiles { f -> f.isFile && (f.name.endsWith(".md") || f.name.endsWith(".markdown")) }?.forEach { f ->
                val content = try { f.readText(Charsets.UTF_8) } catch (e: Exception) { "" }
                rules.add(
                    RuleItem(
                        id = "rule_${f.name}",
                        name = f.name,
                        filePath = f.absolutePath,
                        content = content,
                        source = "Global Rules",
                        isReadOnly = false,
                        lineCount = content.lines().size,
                        byteSize = f.length()
                    )
                )
            }
        }

        val candidatePluginDirs = listOf(
            File(configDir, "plugins"),
            File(context.filesDir, ".gemini/config/plugins"),
            File(context.filesDir, "home/.gemini/config/plugins")
        )
        val seenPluginRules = mutableSetOf<String>()
        for (pluginsDir in candidatePluginDirs) {
            if (pluginsDir.exists() && pluginsDir.isDirectory) {
                pluginsDir.listFiles { f -> f.isDirectory }?.forEach { pDir ->
                    val pNameLower = pDir.name.lowercase()
                    if (!seenPluginRules.contains(pNameLower)) {
                        val pRule = File(pDir, "rules/AGENTS.md")
                        if (pRule.exists() && pRule.canRead()) {
                            seenPluginRules.add(pNameLower)
                            val content = try { pRule.readText(Charsets.UTF_8) } catch (e: Exception) { "" }
                            rules.add(
                                RuleItem(
                                    id = "plugin_${pDir.name}",
                                    name = "Plugin: ${pDir.name}",
                                    filePath = pRule.absolutePath,
                                    content = content,
                                    source = "Plugin (${pDir.name})",
                                    isReadOnly = false,
                                    lineCount = content.lines().size,
                                    byteSize = pRule.length()
                                )
                            )
                        }
                    }
                }
            }
        }

        if (!workspacePath.isNullOrBlank()) {
            val ws = File(workspacePath)
            if (ws.exists() && ws.isDirectory) {

                val wsAgents = File(ws, "AGENTS.md")
                if (wsAgents.exists() && wsAgents.canRead()) {
                    val content = try { wsAgents.readText(Charsets.UTF_8) } catch (e: Exception) { "" }
                    rules.add(
                        RuleItem(
                            id = "ws_agents",
                            name = "Workspace AGENTS.md",
                            filePath = wsAgents.absolutePath,
                            content = content,
                            source = "Workspace",
                            isReadOnly = false,
                            lineCount = content.lines().size,
                            byteSize = wsAgents.length()
                        )
                    )
                }

                val wsRulesDir = File(ws, ".agents/rules")
                if (wsRulesDir.exists() && wsRulesDir.isDirectory) {
                    wsRulesDir.listFiles { f -> f.isFile && f.name.endsWith(".md") }?.forEach { f ->
                        val content = try { f.readText(Charsets.UTF_8) } catch (e: Exception) { "" }
                        rules.add(
                            RuleItem(
                                id = "ws_${f.name}",
                                name = "Workspace: ${f.name}",
                                filePath = f.absolutePath,
                                content = content,
                                source = "Workspace",
                                isReadOnly = false,
                                lineCount = content.lines().size,
                                byteSize = f.length()
                            )
                        )
                    }
                }
            }
        }

        rules
    }

    suspend fun saveRule(filePath: String, newContent: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            file.parentFile?.mkdirs()
            file.writeText(newContent, Charsets.UTF_8)
            file.setReadable(true, false)
            file.setWritable(true, false)
            Log.i(TAG, "Saved rule to $filePath (${newContent.length} chars)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save rule $filePath: ${e.message}", e)
            false
        }
    }

    suspend fun createNewRule(context: Context, ruleName: String, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val configDir = getPrimaryConfigDir(context)
            val rulesDir = File(configDir, "rules")
            rulesDir.mkdirs()
            val cleanName = if (ruleName.endsWith(".md")) ruleName else "$ruleName.md"
            val target = File(rulesDir, cleanName)
            target.writeText(content, Charsets.UTF_8)
            target.setReadable(true, false)
            target.setWritable(true, false)
            Log.i(TAG, "Created new rule at ${target.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create rule: ${e.message}", e)
            false
        }
    }

    suspend fun deleteRule(filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (file.exists()) {
                val ok = file.delete()
                Log.i(TAG, "Deleted rule file $filePath: $ok")
                ok
            } else {
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete rule $filePath: ${e.message}", e)
            false
        }
    }
}

