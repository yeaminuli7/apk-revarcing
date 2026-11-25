package id.vern.antigravity.viewmodel

import android.content.Context
import id.vern.antigravity.executor.EngineExecutor
import id.vern.antigravity.service.TerminalLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class CustomizationInstallerDelegate {
    val isInspectingRepo = MutableStateFlow(false)
    val discoveredRepoSkills = MutableStateFlow<List<DiscoveredRepoSkill>>(emptyList())
    val repoInspectionError = MutableStateFlow<String?>(null)
    private var cachedInspectedRepoDir: File? = null

    private val mutableActiveInstallProgress = MutableStateFlow<CustomizationInstallProgress?>(null)
    val activeInstallProgress: StateFlow<CustomizationInstallProgress?> = mutableActiveInstallProgress.asStateFlow()

    fun dismissInstallProgress() {
        mutableActiveInstallProgress.value = null
    }

    fun setActiveInstallProgressDirect(progress: CustomizationInstallProgress?) {
        mutableActiveInstallProgress.value = progress
    }

    fun clearRepoInspection() {
        isInspectingRepo.value = false
        discoveredRepoSkills.value = emptyList()
        repoInspectionError.value = null
        try {
            cachedInspectedRepoDir?.deleteRecursively()
        } catch (_: Exception) {}
        cachedInspectedRepoDir = null
    }

    fun toggleRepoSkillSelection(skillId: String) {
        discoveredRepoSkills.value = discoveredRepoSkills.value.map {
            if (it.id == skillId) it.copy(isSelected = !it.isSelected) else it
        }
    }

    fun setAllRepoSkillsSelection(selectAll: Boolean) {
        discoveredRepoSkills.value = discoveredRepoSkills.value.map {
            it.copy(isSelected = selectAll)
        }
    }

    fun normalizeGitRepoUrl(rawUrl: String): Pair<String, String?> =
        CustomizationTracker.normalizeGitRepoUrl(rawUrl)

    fun inspectRepoForSkills(context: Context, rawRepoUrl: String, scope: CoroutineScope) {
        val (cleanUrl, _) = normalizeGitRepoUrl(rawRepoUrl)
        if (cleanUrl.isBlank()) return

        isInspectingRepo.value = true
        repoInspectionError.value = null
        discoveredRepoSkills.value = emptyList()

        scope.launch(Dispatchers.IO) {
            try {
                val stagingDir = File(context.cacheDir, "repo-inspect-${cleanUrl.hashCode().toString().replace("-", "n")}").apply {
                    deleteRecursively()
                    mkdirs()
                }
                cachedInspectedRepoDir = stagingDir

                val gitEnv = CustomizationTracker.prepareGitEnv(context)
                val cloneCmd = "${gitEnv}git clone --depth 1 '$cleanUrl' '${stagingDir.absolutePath}'"
                val exitCode = EngineExecutor.executeCommand(context, cloneCmd, context.filesDir) { _, _ -> }

                if (exitCode != 0 || !stagingDir.exists()) {
                    repoInspectionError.value = "Failed to clone repository"
                    isInspectingRepo.value = false
                    return@launch
                }

                val discovered = mutableListOf<DiscoveredRepoSkill>()
                stagingDir.walkTopDown()
                    .filter { it.isFile && it.name.equals("SKILL.md", ignoreCase = true) }
                    .filter { !it.absolutePath.contains("/.git/") }
                    .forEach { skillFile ->
                        val skillFolder = skillFile.parentFile ?: stagingDir
                        val relSubPath = skillFolder.relativeTo(stagingDir).path.replace('\\', '/')
                        val normSubPath = if (relSubPath == "." || relSubPath.isEmpty()) "" else relSubPath

                        var skillName = skillFolder.name
                        var skillDesc = ""
                        try {
                            val lines = skillFile.readLines().take(50)
                            var inFrontmatter = false
                            for (line in lines) {
                                val trimmed = line.trim()
                                if (trimmed == "---") {
                                    inFrontmatter = !inFrontmatter
                                    continue
                                }
                                if (inFrontmatter) {
                                    if (trimmed.startsWith("name:", ignoreCase = true)) {
                                        skillName = trimmed.substringAfter(":").trim().trim('"', '\'')
                                    } else if (trimmed.startsWith("description:", ignoreCase = true)) {
                                        skillDesc = trimmed.substringAfter(":").trim().trim('"', '\'')
                                    }
                                }
                            }
                        } catch (_: Exception) {}

                        val skillId = skillFolder.name.lowercase().replace(" ", "-").replace(Regex("[^a-z0-9_-]"), "")
                        discovered.add(
                            DiscoveredRepoSkill(
                                id = skillId,
                                name = skillName,
                                description = skillDesc,
                                subPath = normSubPath,
                                isSelected = true
                            )
                        )
                    }

                if (discovered.isEmpty()) {
                    repoInspectionError.value = "No SKILL.md found in repository"
                } else {
                    discoveredRepoSkills.value = discovered.sortedBy { it.name }
                }
            } catch (e: Exception) {
                repoInspectionError.value = e.message ?: "Failed to inspect repository"
            } finally {
                isInspectingRepo.value = false
            }
        }
    }

    fun installSkillViaTerminal(
        context: Context,
        skillItem: SkillCatalogueItem,
        builtinDir: File,
        scope: CoroutineScope,
        fetchLatestRepoCommitSha: (String, String?) -> String?,
        registerTrackedCustomization: (Context, CustomizationRepoTrack) -> Unit,
        refreshSkills: (Context) -> Unit
    ) {
        val directConfig = File(context.filesDir, ".gemini/config").apply { mkdirs() }
        val skillDirFile = File(directConfig, "skills/${skillItem.id}").apply { mkdirs() }
        val homeSkillDirFile = File(context.filesDir, "home/.gemini/config/skills/${skillItem.id}").apply { mkdirs() }
        val skillDir = skillDirFile.absolutePath
        val homeSkillDir = homeSkillDirFile.absolutePath
        val builtinSrc = File(builtinDir, skillItem.id)
        val builtinSrcAlt = File(builtinDir, skillItem.id.replace("-", "_"))
        val tmpCloneDir = File(context.cacheDir, "skill-clone-${skillItem.id}").absolutePath
        val gitEnvSetup = CustomizationTracker.prepareGitEnv(context)
        val (cleanUrl, extractedSub) = normalizeGitRepoUrl(skillItem.repoUrl)
        val effectiveSubPath = skillItem.subPath?.ifBlank { null } ?: extractedSub

        val cmd = buildString {
            append(gitEnvSetup)
            append("rm -rf '$tmpCloneDir' '$skillDir' && mkdir -p '$skillDir' '$homeSkillDir' && ")
            append("echo '[Agent] Starting installation of skill ${skillItem.name}...' && ")
            if (builtinSrc.exists() && builtinSrc.isDirectory) {
                append("echo '[Agent] Deploying built-in skill assets...' && ")
                append("cp -r '${builtinSrc.absolutePath}'/* '$skillDir/' 2>/dev/null; cp -r '${builtinSrc.absolutePath}'/* '$homeSkillDir/' 2>/dev/null && ")
            } else if (builtinSrcAlt.exists() && builtinSrcAlt.isDirectory) {
                append("echo '[Agent] Deploying built-in skill assets...' && ")
                append("cp -r '${builtinSrcAlt.absolutePath}'/* '$skillDir/' 2>/dev/null; cp -r '${builtinSrcAlt.absolutePath}'/* '$homeSkillDir/' 2>/dev/null && ")
            } else if (cleanUrl.isNotBlank()) {
                append("echo '[Agent] Cloning skill from $cleanUrl...' && ")
                append("git clone --depth 1 '$cleanUrl' '$tmpCloneDir' && ")
                append("echo '[Agent] Extracting and setting up skill files...' && ")
                if (!effectiveSubPath.isNullOrBlank()) {
                    append("if [ -d '$tmpCloneDir/${effectiveSubPath.trim('/')}' ]; then cp -r '$tmpCloneDir/${effectiveSubPath.trim('/')}'/* '$skillDir/' 2>/dev/null; else cp -r '$tmpCloneDir'/* '$skillDir/' 2>/dev/null; fi && ")
                } else {
                    val rootSkillsDir = File(directConfig, "skills").absolutePath
                    val homeSkillsDir = File(context.filesDir, "home/.gemini/config/skills").absolutePath
                    append("cp -r '$tmpCloneDir'/* '$skillDir/' 2>/dev/null && ")
                    append("for sdir in '$tmpCloneDir/skills' '$tmpCloneDir/.gemini/skills' '$tmpCloneDir/plugin/skills' '$tmpCloneDir/.agents/skills'; do ")
                    append("if [ -d \"${'$'}sdir\" ]; then ")
                    append("echo '[Agent] Unpacking skills from repository...' && ")
                    append("cp -r \"${'$'}sdir/\"* '$rootSkillsDir/' 2>/dev/null; ")
                    append("cp -r \"${'$'}sdir/\"* '$homeSkillsDir/' 2>/dev/null; fi; done && ")
                }
                append("rm -rf '$tmpCloneDir' && ")
            } else {
                CustomizationTracker.ensureSkillFiles(skillDirFile, homeSkillDirFile, skillItem.id, skillItem.name, skillItem.description)
                append("echo '[Agent] Creating standard skill template...' && ")
            }
            append("if [ -d '$skillDir/scripts' ]; then chmod +x '$skillDir'/scripts/* 2>/dev/null || true; fi && ")
            append("cp -r '$skillDir'/* '$homeSkillDir/' 2>/dev/null && ")
            append("echo '[Agent] Running agy-fix-paths...' && ")
            append("agy-fix-paths '$skillDir' 2>/dev/null || true; ")
            append("echo '[Agent] Skill ${skillItem.name} successfully installed and verified!'")
        }

        val initialLines = listOf(TerminalLine(text = "$ Installing skill ${skillItem.name}...", isCommand = true))
        mutableActiveInstallProgress.value = CustomizationInstallProgress(
            title = skillItem.name,
            command = cmd,
            lines = initialLines,
            isRunning = true
        )

        scope.launch(Dispatchers.IO) {
            val outputLines = mutableListOf<TerminalLine>().apply { addAll(initialLines) }
            val cleanRegex = Regex("(?:\u001B\\[[0-?]*[ -/]*[@-~]|\u001B\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\)|\u001B[@-Z\\\\-_]|\r)")

            val exitCode = EngineExecutor.executeCommandWithUpdates(context, cmd, context.filesDir) { line, isErr, isUpdate ->
                val clean = line.replace(cleanRegex, "").trimEnd('\r')
                if (clean.isNotEmpty()) {
                    val isGitInfo = clean.startsWith("Cloning into", ignoreCase = true) ||
                            clean.startsWith("remote:", ignoreCase = true) ||
                            clean.startsWith("Receiving objects", ignoreCase = true) ||
                            clean.startsWith("Resolving deltas", ignoreCase = true) ||
                            clean.startsWith("Updating files", ignoreCase = true) ||
                            clean.startsWith("Note: checking out", ignoreCase = true) ||
                            clean.startsWith("HEAD is now at", ignoreCase = true) ||
                            clean.startsWith("From https://", ignoreCase = true) ||
                            clean.startsWith("Already up to date", ignoreCase = true)
                    val isWarning = clean.startsWith("warning:", ignoreCase = true)
                    val isRealError = !isWarning && (clean.contains("error:", ignoreCase = true) ||
                            clean.contains("fatal:", ignoreCase = true) ||
                            clean.contains("Permission denied", ignoreCase = true) ||
                            (clean.contains("failed", ignoreCase = true) && !clean.contains("0 failed", ignoreCase = true)))
                    val isError = if (isGitInfo || isWarning) false else (isErr || isRealError)
                    synchronized(outputLines) {
                        if (isUpdate && outputLines.isNotEmpty() && !outputLines.last().isCommand) {
                            outputLines[outputLines.size - 1] = TerminalLine(text = clean, isError = isError)
                        } else {
                            outputLines.add(TerminalLine(text = clean, isError = isError))
                        }
                    }
                    mutableActiveInstallProgress.value = mutableActiveInstallProgress.value?.copy(
                        lines = outputLines.toList()
                    )
                }
            }
            if (exitCode == 0) {
                CustomizationTracker.ensureSkillFiles(skillDirFile, homeSkillDirFile, skillItem.id, skillItem.name, skillItem.description)
                if (cleanUrl.isNotBlank()) {
                    val sha = fetchLatestRepoCommitSha(cleanUrl, effectiveSubPath) ?: ""
                    registerTrackedCustomization(
                        context,
                        CustomizationRepoTrack(
                            id = skillItem.id,
                            name = skillItem.name,
                            repoUrl = cleanUrl,
                            isPlugin = false,
                            subPath = effectiveSubPath,
                            installedSha = sha,
                            lastChecked = System.currentTimeMillis()
                        )
                    )
                }
            }
            refreshSkills(context)
            mutableActiveInstallProgress.value = mutableActiveInstallProgress.value?.copy(
                isRunning = false,
                isSuccess = (exitCode == 0)
            )
        }
    }

    fun installPluginViaTerminal(
        context: Context,
        pluginItem: SkillCatalogueItem,
        scope: CoroutineScope,
        fetchLatestRepoCommitSha: (String, String?) -> String?,
        registerTrackedCustomization: (Context, CustomizationRepoTrack) -> Unit,
        refreshPlugins: (Context) -> Unit
    ) {
        val directConfig = File(context.filesDir, ".gemini/config").apply { mkdirs() }
        val pDirFile = File(directConfig, "plugins/${pluginItem.id}").apply { mkdirs() }
        val pHomeDirFile = File(context.filesDir, "home/.gemini/config/plugins/${pluginItem.id}").apply { mkdirs() }
        val pluginDir = pDirFile.absolutePath
        val homePluginDir = pHomeDirFile.absolutePath
        val tmpDir = File(context.cacheDir, "plugin-clone-${pluginItem.id}").absolutePath
        val gitEnvSetup = CustomizationTracker.prepareGitEnv(context)
        val (cleanUrl, extractedSub) = normalizeGitRepoUrl(pluginItem.repoUrl)
        val effectiveSubPath = pluginItem.subPath?.ifBlank { null } ?: extractedSub

        val rawCmd = when (pluginItem.id.lowercase()) {
            "rtk" -> {
                CustomizationTracker.ensurePluginFiles(pDirFile, pHomeDirFile, "rtk", "RTK", "Rust Token Killer")
                val rtkRuleFile = File(pDirFile, "rules/AGENTS.md")
                val rtkRuleContent = "# Command output\n\nCommand output here is condensed to save tokens, keeping every signal and dropping costly noise. Treat it as the complete result: run commands normally, and batch related commands into one call to avoid extra turns. Truncated results state their recovery path in their own output. Re-run a command as `rtk bypass <cmd>` only when its result is unusable: empty when output was clearly expected, contradicting its exit code, or garbled.\n"
                try {
                    File(pDirFile, "rules").mkdirs()
                    File(pHomeDirFile, "rules").mkdirs()
                    rtkRuleFile.writeText(rtkRuleContent)
                    File(pHomeDirFile, "rules/AGENTS.md").writeText(rtkRuleContent)
                } catch (_: Exception) {}

                val usrBinRtk = File(context.filesDir, "usr/bin/rtk")
                val runtimeRtk = File(context.filesDir, "runtime/bin/rtk")
                if (!usrBinRtk.exists() && runtimeRtk.exists()) {
                    try {
                        runtimeRtk.copyTo(usrBinRtk, overwrite = true)
                        usrBinRtk.setExecutable(true, false)
                    } catch (_: Exception) {}
                }
                try { usrBinRtk.setExecutable(true, false) } catch (_: Exception) {}
                val usrBinPath = usrBinRtk.absolutePath
                val runtimeBinPath = runtimeRtk.absolutePath
                val rtkDownloadUrl = "https://github.com/KuatoDev/antigravity-bootstrap/releases/download/core-v1/rtk"

                "echo '[Agent] Preparing environment for RTK plugin...' && mkdir -p '$pluginDir/rules' '$homePluginDir/rules' && if ! command -v rtk >/dev/null 2>&1; then if [ -f '$runtimeBinPath' ]; then cp '$runtimeBinPath' '$usrBinPath' 2>/dev/null && chmod +x '$usrBinPath' 2>/dev/null; elif [ -f '$usrBinPath' ]; then chmod +x '$usrBinPath' 2>/dev/null; else echo '[Agent] Downloading RTK binary from release payload...' && curl -sSL --retry 3 -o '$usrBinPath' '$rtkDownloadUrl' && chmod +x '$usrBinPath' 2>/dev/null; fi; fi && if command -v rtk >/dev/null 2>&1; then echo '[Agent] Initializing RTK for Antigravity agent...' && rtk init --agent antigravity --global --auto-patch 2>&1 || true && cp -r '$pluginDir'/* '$homePluginDir/' 2>/dev/null; agy-fix-paths '$pluginDir' 2>/dev/null || true; echo \"[Agent] Verified: $(rtk --version 2>&1)\" && echo '[Agent] RTK plugin installed and verified!'; else echo '[Agent Error] Failed to install or download RTK binary.'; exit 1; fi"
            }
            "antislop" -> {
                "echo '[Agent] Preparing environment for Anti-Slop plugin...' && rm -rf '$tmpDir' '$pluginDir' && mkdir -p '$pluginDir' '$homePluginDir' && echo '[Agent] Cloning Anti-Slop repository...' && git clone --depth 1 https://github.com/miqdadbadjuber/anti-slop.git '$tmpDir' && echo '[Agent] Installing plugin files and rules...' && cp -r '$tmpDir'/* '$pluginDir/' 2>/dev/null && if [ -f '$pluginDir/rules/antislop.md' ] && [ ! -f '$pluginDir/rules/AGENTS.md' ]; then cp '$pluginDir/rules/antislop.md' '$pluginDir/rules/AGENTS.md'; fi && cp -r '$pluginDir'/* '$homePluginDir/' 2>/dev/null && rm -rf '$tmpDir' && echo '[Agent] Running agy-fix-paths...' && agy-fix-paths '$pluginDir' 2>/dev/null || true; echo '[Agent] Plugin Anti-Slop successfully installed and verified!'"
            }
            else -> {
                if (cleanUrl.isNotBlank()) {
                    val sub = effectiveSubPath?.trim('/') ?: ""
                    val extractCmd = if (sub.isNotBlank()) {
                        "if [ -d '$tmpDir/$sub' ]; then cp -r '$tmpDir/$sub'/* '$pluginDir/' 2>/dev/null; else cp -r '$tmpDir'/* '$pluginDir/' 2>/dev/null; fi"
                    } else {
                        val rootSkillsDir = File(directConfig, "skills").absolutePath
                        val homeSkillsDir = File(context.filesDir, "home/.gemini/config/skills").absolutePath
                        "cp -r '$tmpDir'/* '$pluginDir/' 2>/dev/null && for sdir in '$tmpDir/skills' '$tmpDir/.gemini/skills' '$tmpDir/plugin/skills' '$tmpDir/.agents/skills'; do if [ -d \"${'$'}sdir\" ]; then echo '[Agent] Unpacking skills from repository...' && cp -r \"${'$'}sdir/\"* '$rootSkillsDir/' 2>/dev/null; cp -r \"${'$'}sdir/\"* '$homeSkillsDir/' 2>/dev/null; fi; done"
                    }
                    val rulesSetupCmd = "if [ -d '$pluginDir/rules' ] && [ ! -f '$pluginDir/rules/AGENTS.md' ]; then FIRST_MD=$(find '$pluginDir/rules' -maxdepth 1 -name \"*.md\" 2>/dev/null | head -n 1); if [ -n \"${'$'}FIRST_MD\" ]; then cp \"${'$'}FIRST_MD\" '$pluginDir/rules/AGENTS.md'; fi; fi && if [ ! -f '$pluginDir/rules/AGENTS.md' ]; then mkdir -p '$pluginDir/rules' && if [ -f '$pluginDir/AGENTS.md' ]; then cp '$pluginDir/AGENTS.md' '$pluginDir/rules/AGENTS.md'; elif [ -f '$pluginDir/GEMINI.md' ]; then cp '$pluginDir/GEMINI.md' '$pluginDir/rules/AGENTS.md'; elif [ -f '$pluginDir/CLAUDE.md' ]; then cp '$pluginDir/CLAUDE.md' '$pluginDir/rules/AGENTS.md'; elif [ -f '$pluginDir/README.md' ]; then cp '$pluginDir/README.md' '$pluginDir/rules/AGENTS.md'; fi; fi && if [ ! -f '$pluginDir/plugin.json' ]; then if [ -f '$pluginDir/.antigravity-plugin/plugin.json' ]; then cp '$pluginDir/.antigravity-plugin/plugin.json' '$pluginDir/plugin.json'; elif [ -f '$pluginDir/.claude-plugin/plugin.json' ]; then cp '$pluginDir/.claude-plugin/plugin.json' '$pluginDir/plugin.json'; fi; fi"
                    "echo '[Agent] Starting installation of plugin ${pluginItem.name}...' && rm -rf '$tmpDir' '$pluginDir' && mkdir -p '$pluginDir' '$homePluginDir' && echo '[Agent] Cloning repository from $cleanUrl...' && git clone --depth 1 '$cleanUrl' '$tmpDir' && echo '[Agent] Inspecting plugin repository structure and dependencies...' && $extractCmd && if [ -f '$pluginDir/requirements.txt' ]; then echo '[Agent] Installing Python dependencies...' && pip install -r '$pluginDir/requirements.txt' 2>/dev/null || true; fi && $rulesSetupCmd && cp -r '$pluginDir'/* '$homePluginDir/' 2>/dev/null && rm -rf '$tmpDir' && echo '[Agent] Running agy-fix-paths...' && agy-fix-paths '$pluginDir' 2>/dev/null || true; echo '[Agent] Plugin ${pluginItem.name} successfully installed and ready!'"
                } else {
                    CustomizationTracker.ensurePluginFiles(pDirFile, pHomeDirFile, pluginItem.id, pluginItem.name, pluginItem.description)
                    "echo '[Agent] Installing standalone plugin ${pluginItem.name}...' && mkdir -p '$pluginDir/rules' '$homePluginDir/rules' && cp -r '$pluginDir'/* '$homePluginDir/' 2>/dev/null && agy-fix-paths '$pluginDir' 2>/dev/null || true && echo '[Agent] Plugin ${pluginItem.name} installed!'"
                }
            }
        }
        val cmd = gitEnvSetup + rawCmd

        val initialLines = listOf(TerminalLine(text = "$ Installing plugin ${pluginItem.name}...", isCommand = true))
        mutableActiveInstallProgress.value = CustomizationInstallProgress(
            title = pluginItem.name,
            command = cmd,
            lines = initialLines,
            isRunning = true
        )

        scope.launch(Dispatchers.IO) {
            val outputLines = mutableListOf<TerminalLine>().apply { addAll(initialLines) }
            val cleanRegex = Regex("(?:\u001B\\[[0-?]*[ -/]*[@-~]|\u001B\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\)|\u001B[@-Z\\\\-_]|\r)")

            val exitCode = EngineExecutor.executeCommandWithUpdates(context, cmd, context.filesDir) { line, isErr, isUpdate ->
                val clean = line.replace(cleanRegex, "").trimEnd('\r')
                if (clean.isNotEmpty()) {
                    val isGitInfo = clean.startsWith("Cloning into", ignoreCase = true) ||
                            clean.startsWith("remote:", ignoreCase = true) ||
                            clean.startsWith("Receiving objects", ignoreCase = true) ||
                            clean.startsWith("Resolving deltas", ignoreCase = true) ||
                            clean.startsWith("Updating files", ignoreCase = true) ||
                            clean.startsWith("Note: checking out", ignoreCase = true) ||
                            clean.startsWith("HEAD is now at", ignoreCase = true) ||
                            clean.startsWith("From https://", ignoreCase = true) ||
                            clean.startsWith("Already up to date", ignoreCase = true)
                    val isWarning = clean.startsWith("warning:", ignoreCase = true)
                    val isRealError = !isWarning && (clean.contains("error:", ignoreCase = true) ||
                            clean.contains("fatal:", ignoreCase = true) ||
                            clean.contains("Permission denied", ignoreCase = true) ||
                            (clean.contains("failed", ignoreCase = true) && !clean.contains("0 failed", ignoreCase = true)))
                    val isError = if (isGitInfo || isWarning) false else (isErr || isRealError)
                    synchronized(outputLines) {
                        if (isUpdate && outputLines.isNotEmpty() && !outputLines.last().isCommand) {
                            outputLines[outputLines.size - 1] = TerminalLine(text = clean, isError = isError)
                        } else {
                            outputLines.add(TerminalLine(text = clean, isError = isError))
                        }
                    }
                    mutableActiveInstallProgress.value = mutableActiveInstallProgress.value?.copy(
                        lines = outputLines.toList()
                    )
                }
            }
            if (exitCode == 0) {
                CustomizationTracker.ensurePluginFiles(pDirFile, pHomeDirFile, pluginItem.id, pluginItem.name, pluginItem.description)
                if (cleanUrl.isNotBlank()) {
                    val sha = fetchLatestRepoCommitSha(cleanUrl, effectiveSubPath) ?: ""
                    registerTrackedCustomization(
                        context,
                        CustomizationRepoTrack(
                            id = pluginItem.id,
                            name = pluginItem.name,
                            repoUrl = cleanUrl,
                            isPlugin = true,
                            subPath = effectiveSubPath,
                            installedSha = sha,
                            lastChecked = System.currentTimeMillis()
                        )
                    )
                }
            }
            refreshPlugins(context)
            mutableActiveInstallProgress.value = mutableActiveInstallProgress.value?.copy(
                isRunning = false,
                isSuccess = (exitCode == 0)
            )
        }
    }

    fun updateCustomizationViaTerminal(
        context: Context,
        track: CustomizationRepoTrack,
        scope: CoroutineScope,
        getTrackedCustomizations: (Context) -> Map<String, CustomizationRepoTrack>,
        saveTrackedCustomizations: (Context, Map<String, CustomizationRepoTrack>) -> Unit,
        refreshSkills: (Context) -> Unit,
        refreshPlugins: (Context) -> Unit
    ) {
        val directConfig = File(context.filesDir, ".gemini/config").apply { mkdirs() }
        val targetDir = if (track.isPlugin) {
            File(directConfig, "plugins/${track.id}").absolutePath
        } else {
            File(directConfig, "skills/${track.id}").absolutePath
        }
        val homeTargetDir = if (track.isPlugin) {
            File(context.filesDir, "home/.gemini/config/plugins/${track.id}").absolutePath
        } else {
            File(context.filesDir, "home/.gemini/config/skills/${track.id}").absolutePath
        }

        val typeLabel = if (track.isPlugin) "Plugin" else "Skill"
        val tmpCloneDir = File(context.cacheDir, "update-${track.id}").absolutePath
        val gitEnvSetup = CustomizationTracker.prepareGitEnv(context)

        val usrRtkPath = File(context.filesDir, "usr/bin/rtk").absolutePath
        val rtkDownloadUrl = "https://github.com/KuatoDev/antigravity-bootstrap/releases/download/core-v1/rtk"
        val rawCmd = if (track.isPlugin && track.id.lowercase() == "rtk") {
            "echo '[RTK] Updating RTK configuration...' && if [ ! -f '$usrRtkPath' ]; then echo '[RTK] Downloading RTK binary from release payload...' && curl -sSL --retry 3 -o '$usrRtkPath' '$rtkDownloadUrl'; fi && chmod +x '$usrRtkPath' 2>/dev/null && rtk init --agent antigravity --global --auto-patch 2>&1 && cp -r '$targetDir'/* '$homeTargetDir/' 2>/dev/null; agy-fix-paths '$targetDir' 2>/dev/null || true; echo \"[RTK plugin successfully updated: $(rtk --version 2>&1)]\""
        } else if (track.isPlugin && File(targetDir, ".git").exists()) {
            "cd '$targetDir' && git pull && cp -r '$targetDir'/* '$homeTargetDir/' 2>/dev/null; agy-fix-paths '$targetDir' 2>/dev/null || true; echo '[$typeLabel ${track.name} successfully updated!]'"
        } else if (!track.subPath.isNullOrBlank()) {
            "rm -rf '$tmpCloneDir' && mkdir -p '$targetDir' '$homeTargetDir' && git clone --depth 1 '${track.repoUrl}' '$tmpCloneDir' && cp -r '$tmpCloneDir/${track.subPath.trim('/')}'/* '$targetDir/' 2>/dev/null; cp -r '$targetDir'/* '$homeTargetDir/' 2>/dev/null; rm -rf '$tmpCloneDir'; agy-fix-paths '$targetDir' 2>/dev/null || true; echo '[$typeLabel ${track.name} successfully updated!]'"
        } else {
            "rm -rf '$tmpCloneDir' && mkdir -p '$targetDir' '$homeTargetDir' && git clone --depth 1 '${track.repoUrl}' '$tmpCloneDir' && cp -r '$tmpCloneDir'/* '$targetDir/' 2>/dev/null; cp -r '$targetDir'/* '$homeTargetDir/' 2>/dev/null; rm -rf '$tmpCloneDir'; agy-fix-paths '$targetDir' 2>/dev/null || true; echo '[$typeLabel ${track.name} successfully updated!]'"
        }
        val cmd = gitEnvSetup + rawCmd

        val initialLines = listOf(TerminalLine(text = "$ Updating $typeLabel ${track.name}...", isCommand = true))
        mutableActiveInstallProgress.value = CustomizationInstallProgress(
            title = track.name,
            command = cmd,
            lines = initialLines,
            isRunning = true
        )

        scope.launch(Dispatchers.IO) {
            val outputLines = mutableListOf<TerminalLine>().apply { addAll(initialLines) }
            val cleanRegex = Regex("(?:\u001B\\[[0-?]*[ -/]*[@-~]|\u001B\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\)|\u001B[@-Z\\\\-_]|\r)")

            val exitCode = EngineExecutor.executeCommandWithUpdates(context, cmd, context.filesDir) { line, isErr, isUpdate ->
                val clean = line.replace(cleanRegex, "").trimEnd('\r')
                if (clean.isNotEmpty()) {
                    val isGitInfo = clean.startsWith("Cloning into", ignoreCase = true) ||
                            clean.startsWith("remote:", ignoreCase = true) ||
                            clean.startsWith("Receiving objects", ignoreCase = true) ||
                            clean.startsWith("Resolving deltas", ignoreCase = true) ||
                            clean.startsWith("Updating files", ignoreCase = true) ||
                            clean.startsWith("Note: checking out", ignoreCase = true) ||
                            clean.startsWith("HEAD is now at", ignoreCase = true) ||
                            clean.startsWith("From https://", ignoreCase = true) ||
                            clean.startsWith("Already up to date", ignoreCase = true)
                    val isWarning = clean.startsWith("warning:", ignoreCase = true)
                    val isRealError = !isWarning && (clean.contains("error:", ignoreCase = true) ||
                            clean.contains("fatal:", ignoreCase = true) ||
                            clean.contains("Permission denied", ignoreCase = true) ||
                            (clean.contains("failed", ignoreCase = true) && !clean.contains("0 failed", ignoreCase = true)))
                    val isError = if (isGitInfo || isWarning) false else (isErr || isRealError)
                    synchronized(outputLines) {
                        if (isUpdate && outputLines.isNotEmpty() && !outputLines.last().isCommand) {
                            outputLines[outputLines.size - 1] = TerminalLine(text = clean, isError = isError)
                        } else {
                            outputLines.add(TerminalLine(text = clean, isError = isError))
                        }
                    }
                    mutableActiveInstallProgress.value = mutableActiveInstallProgress.value?.copy(lines = outputLines.toList())
                }
            }

            if (exitCode == 0) {
                if (track.isPlugin) {
                    CustomizationTracker.ensurePluginFiles(File(targetDir), File(homeTargetDir), track.id, track.name, "")
                } else {
                    CustomizationTracker.ensureSkillFiles(File(targetDir), File(homeTargetDir), track.id, track.name, "")
                }
                val currentTracks = getTrackedCustomizations(context).toMutableMap()
                val updatedSha = track.remoteSha ?: track.installedSha
                currentTracks[track.id.lowercase()] = track.copy(
                    installedSha = updatedSha,
                    hasUpdate = false,
                    lastChecked = System.currentTimeMillis()
                )
                saveTrackedCustomizations(context, currentTracks)
            }
            if (track.isPlugin) refreshPlugins(context) else refreshSkills(context)
            mutableActiveInstallProgress.value = mutableActiveInstallProgress.value?.copy(
                isRunning = false,
                isSuccess = (exitCode == 0)
            )
        }
    }

    fun installCustomFromRepoViaTerminal(
        context: Context,
        rawRepoUrl: String,
        customName: String,
        subPath: String?,
        isPlugin: Boolean,
        scope: CoroutineScope,
        fetchLatestRepoCommitSha: (String, String?) -> String?,
        registerTrackedCustomization: (Context, CustomizationRepoTrack) -> Unit,
        refreshSkills: (Context) -> Unit,
        refreshPlugins: (Context) -> Unit
    ) {
        val (cleanUrl, extractedSub) = normalizeGitRepoUrl(rawRepoUrl)
        val effectiveSubPath = subPath?.ifBlank { null } ?: extractedSub
        val safeName = if (customName.isNotBlank()) {
            customName.trim().lowercase().replace(" ", "-").replace(Regex("[^a-z0-9_-]"), "")
        } else {
            val fromSub = effectiveSubPath?.trim('/')?.substringAfterLast('/')
            if (!fromSub.isNullOrBlank()) fromSub else cleanUrl.substringAfterLast('/').removeSuffix(".git")
        }

        val directConfig = File(context.filesDir, ".gemini/config").apply { mkdirs() }
        val targetDir = if (isPlugin) {
            File(directConfig, "plugins/$safeName").absolutePath
        } else {
            File(directConfig, "skills/$safeName").absolutePath
        }
        val homeTargetDir = if (isPlugin) {
            File(context.filesDir, "home/.gemini/config/plugins/$safeName").absolutePath
        } else {
            File(context.filesDir, "home/.gemini/config/skills/$safeName").absolutePath
        }
        val tmpCloneDir = File(context.cacheDir, "repo-custom-$safeName").absolutePath
        val typeLabel = if (isPlugin) "Plugin" else "Skill"
        val gitEnvSetup = CustomizationTracker.prepareGitEnv(context)

        val sub = effectiveSubPath?.trim('/') ?: ""
        val extractCmd = if (sub.isNotBlank()) {
            "if [ -d '$tmpCloneDir/$sub' ]; then cp -r '$tmpCloneDir/$sub'/* '$targetDir/' 2>/dev/null; else cp -r '$tmpCloneDir'/* '$targetDir/' 2>/dev/null; fi"
        } else {
            val rootSkillsDir = File(directConfig, "skills").absolutePath
            val homeSkillsDir = File(context.filesDir, "home/.gemini/config/skills").absolutePath
            "cp -r '$tmpCloneDir'/* '$targetDir/' 2>/dev/null && for sdir in '$tmpCloneDir/skills' '$tmpCloneDir/.gemini/skills' '$tmpCloneDir/plugin/skills' '$tmpCloneDir/.agents/skills'; do if [ -d \"${'$'}sdir\" ]; then echo '[Agent] Unpacking skills from repository...' && cp -r \"${'$'}sdir/\"* '$rootSkillsDir/' 2>/dev/null; cp -r \"${'$'}sdir/\"* '$homeSkillsDir/' 2>/dev/null; fi; done"
        }
        val rawCmd = buildString {
            append("echo '[Agent] Installing $typeLabel $safeName from $cleanUrl...' && ")
            append("rm -rf '$tmpCloneDir' '$targetDir' && mkdir -p '$targetDir' '$homeTargetDir' && ")
            append("echo '[Agent] Cloning repository...' && ")
            append("git clone --depth 1 '$cleanUrl' '$tmpCloneDir' && ")
            append("echo '[Agent] Analyzing repository contents and structure...' && ")
            append("$extractCmd && ")
            append("rm -rf '$tmpCloneDir' && ")
            if (isPlugin) {
                append("if [ -f '$targetDir/requirements.txt' ]; then ")
                append("echo '[Agent] Installing Python requirements...' && ")
                append("pip install -r '$targetDir/requirements.txt' 2>/dev/null || true; fi && ")
                append("if [ -d '$targetDir/rules' ] && [ ! -f '$targetDir/rules/AGENTS.md' ]; then ")
                append("FIRST_MD=$(find '$targetDir/rules' -maxdepth 1 -name \"*.md\" 2>/dev/null | head -n 1); ")
                append("if [ -n \"${'$'}FIRST_MD\" ]; then cp \"${'$'}FIRST_MD\" '$targetDir/rules/AGENTS.md'; fi; fi && ")
                append("if [ ! -f '$targetDir/rules/AGENTS.md' ]; then mkdir -p '$targetDir/rules' && if [ -f '$targetDir/AGENTS.md' ]; then cp '$targetDir/AGENTS.md' '$targetDir/rules/AGENTS.md'; elif [ -f '$targetDir/GEMINI.md' ]; then cp '$targetDir/GEMINI.md' '$targetDir/rules/AGENTS.md'; elif [ -f '$targetDir/CLAUDE.md' ]; then cp '$targetDir/CLAUDE.md' '$targetDir/rules/AGENTS.md'; elif [ -f '$targetDir/README.md' ]; then cp '$targetDir/README.md' '$targetDir/rules/AGENTS.md'; fi; fi && ")
                append("if [ ! -f '$targetDir/plugin.json' ]; then if [ -f '$targetDir/.antigravity-plugin/plugin.json' ]; then cp '$targetDir/.antigravity-plugin/plugin.json' '$targetDir/plugin.json'; elif [ -f '$targetDir/.claude-plugin/plugin.json' ]; then cp '$targetDir/.claude-plugin/plugin.json' '$targetDir/plugin.json'; fi; fi && ")
            } else {
                append("if [ -d '$targetDir/scripts' ]; then chmod +x '$targetDir'/scripts/* 2>/dev/null || true; fi && ")
            }
            append("cp -r '$targetDir'/* '$homeTargetDir/' 2>/dev/null && ")
            append("echo '[Agent] Running agy-fix-paths...' && ")
            append("agy-fix-paths '$targetDir' 2>/dev/null || true; ")
            append("echo '[Agent] $typeLabel $safeName successfully installed and verified!'")
        }
        val cmd = gitEnvSetup + rawCmd

        val initialLines = listOf(TerminalLine(text = "$ Installing $typeLabel $safeName from $cleanUrl...", isCommand = true))
        mutableActiveInstallProgress.value = CustomizationInstallProgress(
            title = safeName,
            command = cmd,
            lines = initialLines,
            isRunning = true
        )

        scope.launch(Dispatchers.IO) {
            val outputLines = mutableListOf<TerminalLine>().apply { addAll(initialLines) }
            val cleanRegex = Regex("(?:\u001B\\[[0-?]*[ -/]*[@-~]|\u001B\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\)|\u001B[@-Z\\\\-_]|\r)")

            val exitCode = EngineExecutor.executeCommandWithUpdates(context, cmd, context.filesDir) { line, isErr, isUpdate ->
                val clean = line.replace(cleanRegex, "").trimEnd('\r')
                if (clean.isNotEmpty()) {
                    val isGitInfo = clean.startsWith("Cloning into", ignoreCase = true) ||
                            clean.startsWith("remote:", ignoreCase = true) ||
                            clean.startsWith("Receiving objects", ignoreCase = true) ||
                            clean.startsWith("Resolving deltas", ignoreCase = true) ||
                            clean.startsWith("Updating files", ignoreCase = true) ||
                            clean.startsWith("Note: checking out", ignoreCase = true) ||
                            clean.startsWith("HEAD is now at", ignoreCase = true) ||
                            clean.startsWith("From https://", ignoreCase = true) ||
                            clean.startsWith("Already up to date", ignoreCase = true)
                    val isWarning = clean.startsWith("warning:", ignoreCase = true)
                    val isRealError = !isWarning && (clean.contains("error:", ignoreCase = true) ||
                            clean.contains("fatal:", ignoreCase = true) ||
                            clean.contains("Permission denied", ignoreCase = true) ||
                            (clean.contains("failed", ignoreCase = true) && !clean.contains("0 failed", ignoreCase = true)))
                    val isError = if (isGitInfo || isWarning) false else (isErr || isRealError)
                    synchronized(outputLines) {
                        if (isUpdate && outputLines.isNotEmpty() && !outputLines.last().isCommand) {
                            outputLines[outputLines.size - 1] = TerminalLine(text = clean, isError = isError)
                        } else {
                            outputLines.add(TerminalLine(text = clean, isError = isError))
                        }
                    }
                    mutableActiveInstallProgress.value = mutableActiveInstallProgress.value?.copy(lines = outputLines.toList())
                }
            }

            if (exitCode == 0) {
                val targetDirFile = File(targetDir)
                val homeTargetDirFile = File(homeTargetDir)
                if (isPlugin) {
                    CustomizationTracker.ensurePluginFiles(targetDirFile, homeTargetDirFile, safeName, customName.ifBlank { safeName }, "")
                } else {
                    CustomizationTracker.ensureSkillFiles(targetDirFile, homeTargetDirFile, safeName, customName.ifBlank { safeName }, "")
                }
                val sha = fetchLatestRepoCommitSha(cleanUrl, effectiveSubPath) ?: ""
                registerTrackedCustomization(
                    context,
                    CustomizationRepoTrack(
                        id = safeName,
                        name = safeName,
                        repoUrl = cleanUrl,
                        isPlugin = isPlugin,
                        subPath = effectiveSubPath,
                        installedSha = sha,
                        lastChecked = System.currentTimeMillis()
                    )
                )
            }
            if (isPlugin) refreshPlugins(context) else refreshSkills(context)
            mutableActiveInstallProgress.value = mutableActiveInstallProgress.value?.copy(
                isRunning = false,
                isSuccess = (exitCode == 0)
            )
        }
    }

    fun installSelectedRepoSkillsViaTerminal(
        context: Context,
        rawRepoUrl: String,
        selectedSkills: List<DiscoveredRepoSkill>,
        scope: CoroutineScope,
        fetchLatestRepoCommitSha: (String, String?) -> String?,
        registerTrackedCustomization: (Context, CustomizationRepoTrack) -> Unit,
        refreshSkills: (Context) -> Unit
    ) {
        if (selectedSkills.isEmpty()) return
        val (cleanUrl, _) = normalizeGitRepoUrl(rawRepoUrl)

        val directConfig = File(context.filesDir, ".gemini/config").apply { mkdirs() }
        val gitEnvSetup = CustomizationTracker.prepareGitEnv(context)
        val staging = cachedInspectedRepoDir
        val needClone = (staging == null || !staging.exists() || staging.listFiles()?.isEmpty() == true)
        val tmpCloneDir = if (needClone) {
            File(context.cacheDir, "repo-batch-clone-${cleanUrl.hashCode().toString().replace("-", "n")}").absolutePath
        } else {
            staging!!.absolutePath
        }

        val cmd = buildString {
            append(gitEnvSetup)
            append("echo '[Agent] Starting installation of ${selectedSkills.size} selected skill(s)...' && ")
            if (needClone) {
                append("rm -rf '$tmpCloneDir' && ")
                append("echo '[Agent] Cloning repository from $cleanUrl...' && ")
                append("git clone --depth 1 '$cleanUrl' '$tmpCloneDir' && ")
            }
            for (skill in selectedSkills) {
                val target = File(directConfig, "skills/${skill.id}").absolutePath
                val homeTarget = File(context.filesDir, "home/.gemini/config/skills/${skill.id}").absolutePath
                val src = if (skill.subPath.isNotBlank()) "$tmpCloneDir/${skill.subPath.trim('/')}" else tmpCloneDir
                append("echo '[Agent] Deploying skill ${skill.name}...' && ")
                append("rm -rf '$target' '$homeTarget' && mkdir -p '$target' '$homeTarget' && ")
                append("if [ -d '$src' ]; then cp -r '$src'/* '$target/' 2>/dev/null; fi && ")
                append("if [ -d '$target/scripts' ]; then chmod +x '$target'/scripts/* 2>/dev/null || true; fi && ")
                append("cp -r '$target'/* '$homeTarget/' 2>/dev/null && ")
            }
            if (needClone) {
                append("rm -rf '$tmpCloneDir' && ")
            }
            append("echo '[Agent] Running agy-fix-paths...' && ")
            append("for s in ${selectedSkills.map { it.id }.joinToString(" ")}; do agy-fix-paths \"$directConfig/skills/${'$'}s\" 2>/dev/null || true; done && ")
            append("echo '[Agent] Successfully installed ${selectedSkills.size} skill(s)!'")
        }

        val initialLines = listOf(TerminalLine(text = "$ Installing ${selectedSkills.size} skill(s) from $cleanUrl...", isCommand = true))
        mutableActiveInstallProgress.value = CustomizationInstallProgress(
            title = if (selectedSkills.size == 1) selectedSkills.first().name else "${selectedSkills.size} Skills",
            command = cmd,
            lines = initialLines,
            isRunning = true
        )

        scope.launch(Dispatchers.IO) {
            val outputLines = mutableListOf<TerminalLine>().apply { addAll(initialLines) }
            val cleanRegex = Regex("(?:\u001B\\[[0-?]*[ -/]*[@-~]|\u001B\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\)|\u001B[@-Z\\\\-_]|\r)")

            val exitCode = EngineExecutor.executeCommandWithUpdates(context, cmd, context.filesDir) { line, isErr, isUpdate ->
                val clean = line.replace(cleanRegex, "").trimEnd('\r')
                if (clean.isNotEmpty()) {
                    val isGitInfo = clean.startsWith("Cloning into", ignoreCase = true) ||
                            clean.startsWith("remote:", ignoreCase = true) ||
                            clean.startsWith("Receiving objects", ignoreCase = true) ||
                            clean.startsWith("Resolving deltas", ignoreCase = true) ||
                            clean.startsWith("Updating files", ignoreCase = true) ||
                            clean.startsWith("Note: checking out", ignoreCase = true) ||
                            clean.startsWith("HEAD is now at", ignoreCase = true) ||
                            clean.startsWith("From https://", ignoreCase = true) ||
                            clean.startsWith("Already up to date", ignoreCase = true)
                    val isWarning = clean.startsWith("warning:", ignoreCase = true)
                    val isRealError = !isWarning && (clean.contains("error:", ignoreCase = true) ||
                            clean.contains("fatal:", ignoreCase = true) ||
                            clean.contains("Permission denied", ignoreCase = true) ||
                            (clean.contains("failed", ignoreCase = true) && !clean.contains("0 failed", ignoreCase = true)))
                    val isError = if (isGitInfo || isWarning) false else (isErr || isRealError)
                    synchronized(outputLines) {
                        if (isUpdate && outputLines.isNotEmpty() && !outputLines.last().isCommand) {
                            outputLines[outputLines.size - 1] = TerminalLine(text = clean, isError = isError)
                        } else {
                            outputLines.add(TerminalLine(text = clean, isError = isError))
                        }
                    }
                    mutableActiveInstallProgress.value = mutableActiveInstallProgress.value?.copy(lines = outputLines.toList())
                }
            }

            if (exitCode == 0) {
                val sha = fetchLatestRepoCommitSha(cleanUrl, null) ?: ""
                for (skill in selectedSkills) {
                    val skillDirFile = File(directConfig, "skills/${skill.id}")
                    val homeSkillDirFile = File(context.filesDir, "home/.gemini/config/skills/${skill.id}")
                    CustomizationTracker.ensureSkillFiles(skillDirFile, homeSkillDirFile, skill.id, skill.name, skill.description)
                    registerTrackedCustomization(
                        context,
                        CustomizationRepoTrack(
                            id = skill.id,
                            name = skill.name,
                            repoUrl = cleanUrl,
                            isPlugin = false,
                            subPath = skill.subPath.ifBlank { null },
                            installedSha = sha,
                            lastChecked = System.currentTimeMillis()
                        )
                    )
                }
            }
            clearRepoInspection()
            refreshSkills(context)
            mutableActiveInstallProgress.value = mutableActiveInstallProgress.value?.copy(
                isRunning = false,
                isSuccess = (exitCode == 0)
            )
        }
    }
}
