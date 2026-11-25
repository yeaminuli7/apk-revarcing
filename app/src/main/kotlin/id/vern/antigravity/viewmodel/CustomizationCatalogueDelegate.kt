package id.vern.antigravity.viewmodel

import android.content.Context
import android.util.Log
import id.vern.antigravity.executor.EngineExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL

class CustomizationCatalogueDelegate {
    private val mutableInstalledSkills = MutableStateFlow<List<CustomizationItem>>(emptyList())
    val installedSkills: StateFlow<List<CustomizationItem>> = mutableInstalledSkills.asStateFlow()

    private val mutableInstalledPlugins = MutableStateFlow<List<CustomizationItem>>(emptyList())
    val installedPlugins: StateFlow<List<CustomizationItem>> = mutableInstalledPlugins.asStateFlow()

    private val mutableCustomizationTracks = MutableStateFlow<Map<String, CustomizationRepoTrack>>(emptyMap())
    val customizationTracks: StateFlow<Map<String, CustomizationRepoTrack>> = mutableCustomizationTracks.asStateFlow()

    private val mutableIsCheckingCustomizationUpdates = MutableStateFlow(false)
    val isCheckingCustomizationUpdates: StateFlow<Boolean> = mutableIsCheckingCustomizationUpdates.asStateFlow()

    private val mutableSkillsCatalogue = MutableStateFlow<List<SkillCatalogueItem>>(getDefaultSkillsCatalogue())
    val skillsCatalogue: StateFlow<List<SkillCatalogueItem>> = mutableSkillsCatalogue.asStateFlow()

    private val mutablePluginsCatalogue = MutableStateFlow<List<SkillCatalogueItem>>(getDefaultPluginsCatalogue())
    val pluginsCatalogue: StateFlow<List<SkillCatalogueItem>> = mutablePluginsCatalogue.asStateFlow()

    private val mutableIsFetchingCatalogue = MutableStateFlow(false)
    val isFetchingCatalogue: StateFlow<Boolean> = mutableIsFetchingCatalogue.asStateFlow()

    private val mutableInstallingItemId = MutableStateFlow<String?>(null)
    val installingItemId: StateFlow<String?> = mutableInstallingItemId.asStateFlow()

    private val mutableTokenUsageStats = MutableStateFlow(TokenUsageStats(0, emptyList(), 0, emptyList()))
    val tokenUsageStats: StateFlow<TokenUsageStats> = mutableTokenUsageStats.asStateFlow()

    private var lastCatalogueFetchTime = 0L

    fun getTrackedCustomizations(context: Context): Map<String, CustomizationRepoTrack> =
        CustomizationTracker.getTrackedCustomizations(context)

    fun saveTrackedCustomizations(context: Context, tracks: Map<String, CustomizationRepoTrack>) {
        CustomizationTracker.saveTrackedCustomizations(context, tracks)
        mutableCustomizationTracks.value = tracks
    }

    fun registerTrackedCustomization(context: Context, track: CustomizationRepoTrack) {
        CustomizationTracker.registerTrackedCustomization(context, track)
        loadTrackedCustomizations(context)
    }

    fun unregisterTrackedCustomization(context: Context, id: String) {
        CustomizationTracker.unregisterTrackedCustomization(context, id)
        loadTrackedCustomizations(context)
    }

    fun loadTrackedCustomizations(context: Context) {
        val tracks = CustomizationTracker.getTrackedCustomizations(context)
        mutableCustomizationTracks.value = tracks
    }

    fun fetchLatestRepoCommitSha(repoUrl: String, subPath: String? = null): String? =
        CustomizationTracker.fetchLatestRepoCommitSha(repoUrl, subPath)

    fun getGeminiConfigDir(context: Context): File {
        val directConfig = File(context.filesDir, ".gemini/config").apply { mkdirs() }
        File(context.filesDir, "home/.gemini/config").apply { mkdirs() }
        try {
            syncConfigBidirectional(context)
        } catch (_: Exception) {}
        return directConfig
    }

    fun syncConfigBidirectional(context: Context) {
        try {
            val directConfig = File(context.filesDir, ".gemini/config")
            val homeConfig = File(context.filesDir, "home/.gemini/config")
            if (directConfig.exists() && directConfig.isDirectory) {
                directConfig.copyRecursively(homeConfig, overwrite = true)
            }
            if (homeConfig.exists() && homeConfig.isDirectory) {
                homeConfig.copyRecursively(directConfig, overwrite = false)
            }
        } catch (_: Exception) {}
    }

    fun syncConfigToRoot(context: Context) {
        syncConfigBidirectional(context)
    }

    fun getBuiltinSkillsDir(context: Context): File {
        val candidates = listOf(
            File(context.filesDir, ".gemini/antigravity-app/builtin/skills"),
            File(context.filesDir, "usr/share/antigravity/builtin/skills"),
            File(context.filesDir, "home/.gemini/antigravity-app/builtin/skills")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory } ?: candidates.first()
    }

    fun getAvailableSkillsCatalogue(): List<SkillCatalogueItem> {
        val remote = mutableSkillsCatalogue.value
        return if (remote.isNotEmpty()) remote else getDefaultSkillsCatalogue()
    }

    fun getDefaultSkillsCatalogue(): List<SkillCatalogueItem> =
        CustomizationCatalogue.getDefaultSkillsCatalogue()

    fun getAvailablePluginsCatalogue(): List<SkillCatalogueItem> {
        val remote = mutablePluginsCatalogue.value
        return if (remote.isNotEmpty()) remote else getDefaultPluginsCatalogue()
    }

    fun getDefaultPluginsCatalogue(): List<SkillCatalogueItem> =
        CustomizationCatalogue.getDefaultPluginsCatalogue()

    fun loadCachedCatalogue(context: Context, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            val (skills, plugins) = CustomizationCatalogue.loadCachedCatalogue(context)
            if (skills.isNotEmpty()) mutableSkillsCatalogue.value = skills
            if (plugins.isNotEmpty()) mutablePluginsCatalogue.value = plugins
        }
    }

    fun fetchRemoteCatalogue(context: Context, scope: CoroutineScope, force: Boolean = false, onLog: (String, String) -> Unit) {
        val now = System.currentTimeMillis()
        if (!force && (now - lastCatalogueFetchTime < 15 * 60 * 1000L)) {
            return
        }
        if (mutableIsFetchingCatalogue.value) return
        mutableIsFetchingCatalogue.value = true

        scope.launch(Dispatchers.IO) {
            try {
                val pair = CustomizationCatalogue.fetchRemoteCatalogue(context)
                if (pair != null) {
                    val (skills, plugins) = pair
                    if (skills.isNotEmpty()) mutableSkillsCatalogue.value = skills
                    if (plugins.isNotEmpty()) mutablePluginsCatalogue.value = plugins
                    lastCatalogueFetchTime = System.currentTimeMillis()
                    onLog("Catalogue", "Updated catalogue: ${skills.size} skills, ${plugins.size} plugins from remote.")
                }
            } finally {
                mutableIsFetchingCatalogue.value = false
            }
        }
    }

    fun getInstalledSkills(context: Context, workspacePath: String? = null): List<CustomizationItem> {
        val list = mutableListOf<CustomizationItem>()
        val seen = mutableSetOf<String>()

        val configDir = getGeminiConfigDir(context)
        val configDirsToCheck = listOf(
            File(configDir, "skills"),
            File(context.filesDir, ".gemini/config/skills"),
            File(context.filesDir, "home/.gemini/config/skills")
        )

        for (skillsDir in configDirsToCheck) {
            if (skillsDir.isDirectory) {
                skillsDir.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
                    val nameLower = dir.name.lowercase()
                    val skillFile = File(dir, "SKILL.md")
                    if (skillFile.exists() && !seen.contains(nameLower)) {
                        var desc = "Custom user skill"
                        val descLine = skillFile.readLines().take(20).firstOrNull { it.trim().startsWith("description:") }
                        if (descLine != null) {
                            desc = descLine.substringAfter("description:").trim().trim('"', '\'')
                        }
                        seen.add(nameLower)
                        list.add(CustomizationItem(dir.name, dir.name, desc, "skill", false, dir.absolutePath))
                    }

                    val nestedSkills = File(dir, "skills")
                    if (nestedSkills.isDirectory) {
                        nestedSkills.listFiles()?.filter { it.isDirectory }?.forEach { nDir ->
                            val nNameLower = nDir.name.lowercase()
                            if (!seen.contains(nNameLower)) {
                                val nSkillFile = File(nDir, "SKILL.md")
                                var desc = "Community skill from ${dir.name}"
                                if (nSkillFile.exists()) {
                                    val descLine = nSkillFile.readLines().take(20).firstOrNull { it.trim().startsWith("description:") }
                                    if (descLine != null) {
                                        desc = descLine.substringAfter("description:").trim().trim('"', '\'')
                                    }
                                }
                                seen.add(nNameLower)
                                list.add(CustomizationItem(nDir.name, nDir.name, desc, "skill", false, nDir.absolutePath))
                            }
                        }
                    }
                }
            }
        }

        val pluginBaseDirs = listOf(
            File(configDir, "plugins"),
            File(context.filesDir, ".gemini/config/plugins"),
            File(context.filesDir, "home/.gemini/config/plugins")
        )
        for (pBase in pluginBaseDirs) {
            if (pBase.isDirectory) {
                pBase.listFiles()?.filter { it.isDirectory }?.forEach { pDir ->
                    val pluginSkillsDir = File(pDir, "skills")
                    if (pluginSkillsDir.isDirectory) {
                        pluginSkillsDir.listFiles()?.filter { it.isDirectory }?.forEach { sDir ->
                            val sNameLower = sDir.name.lowercase()
                            if (!seen.contains(sNameLower)) {
                                val skillFile = File(sDir, "SKILL.md")
                                var desc = "Plugin skill from ${pDir.name}"
                                if (skillFile.exists()) {
                                    val descLine = skillFile.readLines().take(20).firstOrNull { it.trim().startsWith("description:") }
                                    if (descLine != null) {
                                        desc = descLine.substringAfter("description:").trim().trim('"', '\'')
                                    }
                                }
                                seen.add(sNameLower)
                                list.add(CustomizationItem(sDir.name, sDir.name, desc, "skill", false, sDir.absolutePath))
                            }
                        }
                    }
                }
            }
        }

        if (!workspacePath.isNullOrBlank()) {
            val wsDir = File(workspacePath)
            if (wsDir.isDirectory) {
                val wsSkillsDirs = listOf(File(wsDir, ".gemini/skills"), File(wsDir, "skills"))
                for (wSkills in wsSkillsDirs) {
                    if (wSkills.isDirectory) {
                        wSkills.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
                            val nameLower = dir.name.lowercase()
                            if (!seen.contains(nameLower)) {
                                val skillFile = File(dir, "SKILL.md")
                                var desc = "Workspace skill"
                                if (skillFile.exists()) {
                                    val descLine = skillFile.readLines().take(20).firstOrNull { it.trim().startsWith("description:") }
                                    if (descLine != null) {
                                        desc = descLine.substringAfter("description:").trim().trim('"', '\'')
                                    }
                                }
                                seen.add(nameLower)
                                list.add(CustomizationItem(dir.name, dir.name, desc, "skill", false, dir.absolutePath))
                            }
                        }
                    }
                }
            }
        }

        val builtinDir = getBuiltinSkillsDir(context)
        if (builtinDir.isDirectory) {
            builtinDir.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
                val nameLower = dir.name.lowercase()
                if (!seen.contains(nameLower)) {
                    val skillFile = File(dir, "SKILL.md")
                    var desc = "Built-in Antigravity skill"
                    if (skillFile.exists()) {
                        val descLine = skillFile.readLines().take(20).firstOrNull { it.trim().startsWith("description:") }
                        if (descLine != null) {
                            desc = descLine.substringAfter("description:").trim().trim('"', '\'')
                        }
                    }
                    seen.add(nameLower)
                    list.add(CustomizationItem(dir.name, dir.name, desc, "skill", true, dir.absolutePath))
                }
            }
        }

        return list
    }

    fun refreshSkills(context: Context, scope: CoroutineScope, workspacePath: String? = null) {
        scope.launch(Dispatchers.IO) {
            val skills = getInstalledSkills(context, workspacePath)
            mutableInstalledSkills.value = skills
            mutableTokenUsageStats.value = calculateTokenUsage(context)
        }
    }

    fun installSkill(context: Context, name: String, description: String, content: String, scope: CoroutineScope, onLog: (String, String) -> Unit): Boolean {
        return try {
            val trimmedName = name.trim().lowercase().replace("\\s+".toRegex(), "-")
            if (trimmedName.isEmpty()) return false
            val directConfig = File(context.filesDir, ".gemini/config").apply { mkdirs() }
            val homeConfig = File(context.filesDir, "home/.gemini/config").apply { mkdirs() }
            val skillDir = File(directConfig, "skills/$trimmedName").apply { mkdirs() }
            val homeSkillDir = File(homeConfig, "skills/$trimmedName").apply { mkdirs() }
            val mdContent = buildString {
                appendLine("---")
                appendLine("name: $trimmedName")
                appendLine("description: \"${description.trim()}\"")
                appendLine("---")
                appendLine()
                if (content.isNotBlank()) {
                    appendLine(content.trim())
                } else {
                    appendLine("# $trimmedName")
                    appendLine()
                    appendLine(description.trim())
                }
            }
            File(skillDir, "SKILL.md").writeText(mdContent)
            File(homeSkillDir, "SKILL.md").writeText(mdContent)
            syncConfigBidirectional(context)
            refreshSkills(context, scope)
            onLog("Customization", "Installed custom skill: $trimmedName")
            true
        } catch (e: Exception) {
            Log.e("AntigravityViewModel", "Failed installing skill", e)
            false
        }
    }

    fun uninstallSkill(context: Context, name: String, scope: CoroutineScope, onLog: (String, String) -> Unit): Boolean {
        return try {
            val trimmedName = name.trim().lowercase()
            val skillDir = File(context.filesDir, ".gemini/config/skills/$trimmedName")
            val homeSkillDir = File(context.filesDir, "home/.gemini/config/skills/$trimmedName")
            var ok = false
            if (skillDir.exists()) ok = skillDir.deleteRecursively() || ok
            if (homeSkillDir.exists()) ok = homeSkillDir.deleteRecursively() || ok
            syncConfigBidirectional(context)
            unregisterTrackedCustomization(context, trimmedName)
            refreshSkills(context, scope)
            onLog("Customization", "Uninstalled skill: $trimmedName")
            ok
        } catch (e: Exception) {
            Log.e("AntigravityViewModel", "Failed uninstalling skill", e)
            false
        }
    }

    fun getInstalledPlugins(context: Context): List<CustomizationItem> {
        val list = mutableListOf<CustomizationItem>()
        val seen = mutableSetOf<String>()

        val configDir = getGeminiConfigDir(context)
        val pluginBaseDirs = listOf(
            File(configDir, "plugins"),
            File(context.filesDir, ".gemini/config/plugins"),
            File(context.filesDir, "home/.gemini/config/plugins")
        )

        for (pBase in pluginBaseDirs) {
            if (pBase.isDirectory) {
                pBase.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
                    val nameLower = dir.name.lowercase()
                    if (!seen.contains(nameLower)) {
                        seen.add(nameLower)
                        var desc = "Plugin extension for Antigravity"
                        val pluginJson = File(dir, "plugin.json")
                        var jsonValid = false
                        if (pluginJson.exists()) {
                            try {
                                val text = pluginJson.readText(Charsets.UTF_8).trim()
                                if (!text.contains("\\n") && !text.contains("\\\"")) {
                                    val json = JSONObject(text)
                                    jsonValid = true
                                    if (json.has("description")) {
                                        desc = json.getString("description")
                                    }
                                }
                            } catch (_: Exception) {
                                jsonValid = false
                            }
                        }

                        if (desc == "Plugin extension for Antigravity") {
                            val ruleFile = File(dir, "rules/AGENTS.md")
                            if (ruleFile.exists()) {
                                val firstLines = ruleFile.readLines().take(10)
                                val header = firstLines.firstOrNull { it.trim().startsWith("#") }
                                if (header != null) {
                                    desc = header.removePrefix("#").trim()
                                }
                            }
                        }

                        if (!jsonValid && dir.canWrite()) {
                            try {
                                val repaired = JSONObject().apply {
                                    put("name", dir.name)
                                    put("version", "1.0.0")
                                    put("description", desc)
                                }.toString(2)
                                pluginJson.writeText(repaired, Charsets.UTF_8)
                            } catch (_: Exception) {}
                        }

                        val ruleFile = File(dir, "rules/AGENTS.md")
                        val altRuleFile = File(dir, "rules/antislop.md")
                        if (!ruleFile.exists() && altRuleFile.exists()) {
                            try {
                                altRuleFile.copyTo(ruleFile, overwrite = true)
                            } catch (_: Exception) {}
                        }

                        list.add(CustomizationItem(dir.name, dir.name, desc, "plugin", false, dir.absolutePath))
                    }
                }
            }
        }

        return list
    }

    fun refreshPlugins(context: Context, scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            val plugins = getInstalledPlugins(context)
            mutableInstalledPlugins.value = plugins
            mutableTokenUsageStats.value = calculateTokenUsage(context)
        }
    }

    fun installPlugin(context: Context, name: String, description: String, scope: CoroutineScope, onLog: (String, String) -> Unit): Boolean {
        return try {
            val trimmedName = name.trim().lowercase().replace("\\s+".toRegex(), "-")
            if (trimmedName.isEmpty()) return false
            val directConfig = File(context.filesDir, ".gemini/config").apply { mkdirs() }
            val homeConfig = File(context.filesDir, "home/.gemini/config").apply { mkdirs() }
            val pluginDir = File(directConfig, "plugins/$trimmedName/rules").apply { mkdirs() }
            val homePluginDir = File(homeConfig, "plugins/$trimmedName/rules").apply { mkdirs() }
            val mdContent = buildString {
                appendLine("# Plugin: $trimmedName")
                appendLine()
                appendLine(description.trim())
            }
            File(pluginDir, "AGENTS.md").writeText(mdContent)
            File(homePluginDir, "AGENTS.md").writeText(mdContent)
            File(pluginDir.parentFile, "plugin.json").writeText("{\n  \"name\": \"$trimmedName\"\n}\n")
            File(homePluginDir.parentFile, "plugin.json").writeText("{\n  \"name\": \"$trimmedName\"\n}\n")
            syncConfigBidirectional(context)
            refreshPlugins(context, scope)
            onLog("Customization", "Installed custom plugin: $trimmedName")
            true
        } catch (e: Exception) {
            Log.e("AntigravityViewModel", "Failed installing plugin", e)
            false
        }
    }

    fun uninstallPlugin(context: Context, name: String, scope: CoroutineScope, onLog: (String, String) -> Unit): Boolean {
        return try {
            val trimmedName = name.trim().lowercase()
            val pluginDir = File(context.filesDir, ".gemini/config/plugins/$trimmedName")
            val homePluginDir = File(context.filesDir, "home/.gemini/config/plugins/$trimmedName")
            var ok = false
            if (pluginDir.exists()) ok = pluginDir.deleteRecursively() || ok
            if (homePluginDir.exists()) ok = homePluginDir.deleteRecursively() || ok
            syncConfigBidirectional(context)
            unregisterTrackedCustomization(context, trimmedName)
            refreshPlugins(context, scope)
            onLog("Customization", "Uninstalled plugin: $trimmedName")
            ok
        } catch (e: Exception) {
            Log.e("AntigravityViewModel", "Failed uninstalling plugin", e)
            false
        }
    }

    fun seedCatalogueTrackingIfInstalled(context: Context) {
        val installed = mutableListOf<CustomizationItem>()
        installed.addAll(getInstalledSkills(context))
        installed.addAll(getInstalledPlugins(context))

        val catalogue = mutableListOf<SkillCatalogueItem>()
        catalogue.addAll(getAvailableSkillsCatalogue())
        catalogue.addAll(getAvailablePluginsCatalogue())

        val currentTracks = getTrackedCustomizations(context).toMutableMap()
        var changed = false

        for (item in installed) {
            val key = item.name.lowercase()
            if (!currentTracks.containsKey(key)) {
                val match = catalogue.firstOrNull { it.name.equals(item.name, ignoreCase = true) || it.id.equals(item.name, ignoreCase = true) }
                if (match != null && match.repoUrl.isNotBlank()) {
                    currentTracks[key] = CustomizationRepoTrack(
                        id = match.id,
                        name = match.name,
                        repoUrl = match.repoUrl,
                        isPlugin = match.isPlugin,
                        subPath = match.subPath,
                        installedSha = "",
                        lastChecked = 0L
                    )
                    changed = true
                }
            }
        }
        if (changed) {
            saveTrackedCustomizations(context, currentTracks)
        }
    }

    fun checkForCustomizationUpdates(context: Context, scope: CoroutineScope, force: Boolean = false) {
        val prefs = context.getSharedPreferences(AntigravityViewModel.PREFS_NAME, Context.MODE_PRIVATE)
        val lastCheckTime = prefs.getLong(AntigravityViewModel.KEY_CUSTOMIZATION_LAST_CHECK, 0L)
        val now = System.currentTimeMillis()
        val oneDayMillis = 24 * 60 * 60 * 1000L

        if (!force && (now - lastCheckTime < oneDayMillis)) {
            loadTrackedCustomizations(context)
            return
        }

        scope.launch(Dispatchers.IO) {
            mutableIsCheckingCustomizationUpdates.value = true
            try {
                seedCatalogueTrackingIfInstalled(context)
                val currentTracks = getTrackedCustomizations(context).toMutableMap()

                for ((id, track) in currentTracks) {
                    if (track.repoUrl.isBlank()) continue
                    val latestSha = fetchLatestRepoCommitSha(track.repoUrl, track.subPath)
                    if (latestSha != null && latestSha.isNotBlank()) {
                        if (track.installedSha.isBlank()) {
                            currentTracks[id] = track.copy(
                                installedSha = latestSha,
                                remoteSha = latestSha,
                                hasUpdate = false,
                                lastChecked = now
                            )
                        } else {
                            val hasUpdate = track.installedSha != latestSha
                            currentTracks[id] = track.copy(
                                remoteSha = latestSha,
                                hasUpdate = hasUpdate,
                                lastChecked = now
                            )
                        }
                    }
                }

                saveTrackedCustomizations(context, currentTracks)
                prefs.edit().putLong(AntigravityViewModel.KEY_CUSTOMIZATION_LAST_CHECK, now).apply()
            } catch (e: Exception) {
                Log.e("AntigravityViewModel", "Failed checking customization updates", e)
            } finally {
                mutableIsCheckingCustomizationUpdates.value = false
            }
        }
    }

    fun getInstalledRules(context: Context): List<Pair<String, String>> {
        val rules = mutableListOf<Pair<String, String>>()
        val configDir = getGeminiConfigDir(context)

        val agentsFile = File(configDir, "AGENTS.md")
        if (agentsFile.exists() && agentsFile.length() > 0) {
            val firstLine = agentsFile.readLines().firstOrNull { it.trim().startsWith("#") }?.removePrefix("#")?.trim()
            val desc = firstLine ?: "Core global behavior and environment execution standards"
            rules.add("AGENTS.md" to desc)
        }

        val pluginsDir = File(configDir, "plugins")
        if (pluginsDir.isDirectory) {
            pluginsDir.listFiles()?.filter { it.isDirectory }?.forEach { pDir ->
                val ruleFile = File(pDir, "rules/AGENTS.md")
                if (ruleFile.exists()) {
                    val firstLine = ruleFile.readLines().firstOrNull { it.trim().startsWith("#") }?.removePrefix("#")?.trim()
                    val desc = firstLine ?: "Plugin rules for ${pDir.name}"
                    rules.add("RULE[${pDir.name}/rules/AGENTS.md]" to desc)
                }
            }
        }

        return rules
    }

    fun installPluginFromCatalogueAsync(
        context: Context,
        pluginId: String,
        scope: CoroutineScope,
        onLog: (String, String, Boolean) -> Unit,
        onComplete: (Boolean) -> Unit
    ) {
        if (mutableInstallingItemId.value != null) return
        mutableInstallingItemId.value = pluginId

        scope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val configDir = getGeminiConfigDir(context)
                    val pluginDir = File(configDir, "plugins/$pluginId")
                    val rulesDir = File(pluginDir, "rules").apply { mkdirs() }

                    when (pluginId.lowercase()) {
                        "rtk" -> {
                            onLog("Plugin", "Initializing RTK (Rust Token Killer)...", false)
                            val usrBinRtk = File(context.filesDir, "usr/bin/rtk")
                            val prefixRtk = File(context.filesDir, "home/usr/bin/rtk")
                            val runtimeRtk = File(context.filesDir, "runtime/bin/rtk")

                            if (runtimeRtk.exists() && !usrBinRtk.exists()) {
                                try {
                                    runtimeRtk.copyTo(usrBinRtk, overwrite = true)
                                    usrBinRtk.setExecutable(true, false)
                                } catch (_: Exception) {}
                            }
                            if (!usrBinRtk.exists()) {
                                try {
                                    onLog("Plugin", "Downloading RTK binary from release payload...", false)
                                    val rtkUrl = URL("https://github.com/KuatoDev/antigravity-bootstrap/releases/download/core-v1/rtk")
                                    usrBinRtk.parentFile?.mkdirs()
                                    rtkUrl.openStream().use { input ->
                                        usrBinRtk.outputStream().use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                    usrBinRtk.setExecutable(true, false)
                                    onLog("Plugin", "RTK binary downloaded successfully.", false)
                                } catch (e: Exception) {
                                    onLog("Plugin", "Failed to download RTK: ${e.message}", true)
                                }
                            }
                            if (usrBinRtk.exists()) {
                                usrBinRtk.setExecutable(true, false)
                            }
                            if (prefixRtk.exists()) {
                                prefixRtk.setExecutable(true, false)
                            }

                            EngineExecutor.executeCommand(context, "which rtk && rtk --version") { line, isErr ->
                                onLog("Plugin", line, isErr)
                            }

                            onLog("Plugin", "Running rtk init --agent antigravity --global --auto-patch...", false)
                            EngineExecutor.executeCommand(context, "rtk init --agent antigravity --global --auto-patch") { line, isErr ->
                                onLog("Plugin", line, isErr)
                            }

                            if (!pluginDir.exists()) pluginDir.mkdirs()
                            rulesDir.mkdirs()

                            val pluginJson = File(pluginDir, "plugin.json")
                            if (!pluginJson.exists() || pluginJson.length() == 0L) {
                                pluginJson.writeText(
                                    """
                                    {
                                      "name": "rtk",
                                      "version": "1.0.0",
                                      "description": "Antigravity plugin for transparent command rewriting and token optimization using rtk"
                                    }
                                    """.trimIndent()
                                )
                            }

                            val agentRule = File(rulesDir, "AGENTS.md")
                            if (!agentRule.exists() || agentRule.length() == 0L) {
                                agentRule.writeText(
                                    """
                                    # Command output

                                    Command output here is condensed to save tokens, keeping every signal and
                                    dropping costly noise. Treat it as the complete result: run commands
                                    normally, and batch related commands into one call to avoid extra turns.
                                    Truncated results state their recovery path in their own output. Re-run a
                                    command as `rtk bypass <cmd>` only when its result is unusable: empty when
                                    output was clearly expected, contradicting its exit code, or garbled.
                                    """.trimIndent()
                                )
                            }
                            onLog("Plugin", "RTK plugin fully configured and verified.", false)
                            true
                        }
                        "antislop" -> {
                            onLog("Plugin", "Installing antislop plugin suite from upstream repository...", false)
                            if (pluginDir.exists()) {
                                pluginDir.deleteRecursively()
                            }
                            val cloneExit = EngineExecutor.executeCommand(
                                context,
                                "git clone --depth 1 https://github.com/miqdadbadjuber/anti-slop.git '${pluginDir.absolutePath}'"
                            ) { line, isErr ->
                                onLog("Plugin", line, isErr)
                            }
                            if (cloneExit != 0 || !File(pluginDir, "rules/AGENTS.md").exists()) {
                                rulesDir.mkdirs()
                                File(pluginDir, "plugin.json").writeText("{\n  \"name\": \"antislop\"\n}\n")
                                File(rulesDir, "AGENTS.md").writeText(
                                    """
                                    <EXTREMELY_IMPORTANT>
                                    You use antislop (Anti Slop: Rules for AI Coding Agents). It is a filter, not a style guide: it stops generic AI slop in generated UI, copy, and code, without prescribing aesthetics.

                                    For UI, copy, people, mobile layout, or code comments work, load the matching antislop skill before starting. Each skill is a folder in this plugin's skills/ directory (read its SKILL.md):
                                    - Core filter, always on: skills/antislop/SKILL.md
                                    - UI / visual: skills/antislop-ui/SKILL.md
                                    - Copy & text: skills/antislop-copywriting/SKILL.md
                                    - People: skills/antislop-human/SKILL.md
                                    - Mobile / responsive: skills/antislop-layoutmobile/SKILL.md
                                    - Code comments: skills/antislop-code/SKILL.md
                                    Before starting, follow the core's "Two Usage Modes" section: explicit session instruction first, then global preference, then ask. For a resolved mode, announce `antislop active: <mode> (session override).` or `antislop active: <mode> (global preference).` once, using the actual mode and source. Ask only when no mode is resolved.
                                    </EXTREMELY_IMPORTANT>
                                    """.trimIndent()
                                )
                            }
                            onLog("Plugin", "Anti-slop plugin suite installed.", false)
                            syncConfigToRoot(context)
                            true
                        }
                        else -> {
                            onLog("Plugin", "Configuring plugin '$pluginId'...", false)
                            val item = getAvailablePluginsCatalogue().firstOrNull { it.id == pluginId }
                            val desc = item?.description ?: "Plugin $pluginId"
                            File(pluginDir, "plugin.json").writeText("{\n  \"name\": \"$pluginId\"\n}\n")
                            File(rulesDir, "AGENTS.md").writeText("# Plugin: $pluginId\n\n$desc\n")
                            onLog("Plugin", "Plugin '$pluginId' configured.", false)
                            syncConfigToRoot(context)
                            true
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AntigravityViewModel", "Error installing plugin $pluginId", e)
                    onLog("Plugin", "Failed to install plugin $pluginId: ${e.message}", true)
                    false
                }
            }
            mutableInstallingItemId.value = null
            refreshPlugins(context, scope)
            withContext(Dispatchers.Main) {
                onComplete(success)
            }
        }
    }

    fun installSkillFromCatalogueAsync(
        context: Context,
        skillId: String,
        scope: CoroutineScope,
        onLog: (String, String, Boolean) -> Unit,
        onComplete: (Boolean) -> Unit
    ) {
        if (mutableInstallingItemId.value != null) return
        mutableInstallingItemId.value = skillId

        scope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val configDir = getGeminiConfigDir(context)
                    val destDir = File(configDir, "skills/$skillId").apply { mkdirs() }
                    val builtinDir = getBuiltinSkillsDir(context)
                    val srcDir = File(builtinDir, skillId)

                    onLog("Skill", "Installing skill '$skillId'...", false)
                    if (srcDir.exists() && srcDir.isDirectory) {
                        srcDir.copyRecursively(destDir, overwrite = true)
                        syncConfigBidirectional(context)
                        onLog("Skill", "Skill '$skillId' installed from built-in repository.", false)
                        return@withContext true
                    }

                    val antislopSkillsDir = File(configDir, "plugins/antislop/skills/$skillId")
                    if (antislopSkillsDir.exists() && antislopSkillsDir.isDirectory) {
                        antislopSkillsDir.copyRecursively(destDir, overwrite = true)
                        syncConfigBidirectional(context)
                        onLog("Skill", "Skill '$skillId' imported from antislop plugin.", false)
                        return@withContext true
                    }

                    val item = getAvailableSkillsCatalogue().firstOrNull { it.id == skillId }
                    val desc = item?.description ?: "Skill $skillId"
                    val skillFile = File(destDir, "SKILL.md")
                    val mdContent = buildString {
                        appendLine("---")
                        appendLine("name: $skillId")
                        appendLine("description: \"$desc\"")
                        appendLine("---")
                        appendLine()
                        appendLine("# $skillId")
                        appendLine()
                        appendLine(desc)
                    }
                    skillFile.writeText(mdContent)
                    syncConfigBidirectional(context)
                    onLog("Skill", "Skill '$skillId' installed.", false)
                    true
                } catch (e: Exception) {
                    Log.e("AntigravityViewModel", "Failed installing skill: $skillId", e)
                    onLog("Skill", "Failed to install skill $skillId: ${e.message}", true)
                    false
                }
            }
            mutableInstallingItemId.value = null
            refreshSkills(context, scope)
            withContext(Dispatchers.Main) {
                onComplete(success)
            }
        }
    }

    fun calculateTokenUsage(context: Context): TokenUsageStats {
        return CustomizationCatalogue.calculateTokenUsage(
            context = context,
            configDir = getGeminiConfigDir(context),
            builtinDir = getBuiltinSkillsDir(context)
        )
    }
}
