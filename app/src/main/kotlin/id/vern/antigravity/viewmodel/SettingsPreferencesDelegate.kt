package id.vern.antigravity.viewmodel

import android.content.Context
import android.widget.Toast
import id.vern.antigravity.R
import id.vern.antigravity.oauth.OAuthManager
import id.vern.antigravity.service.McpConfigService
import id.vern.antigravity.service.McpServerItem
import id.vern.antigravity.service.PermissionConfigService
import id.vern.antigravity.service.PermissionGrantsState
import id.vern.antigravity.service.PermissionRule
import id.vern.antigravity.service.RuleConfigService
import id.vern.antigravity.service.RuleItem
import id.vern.antigravity.data.AccentColor
import id.vern.antigravity.data.AppearanceConfig
import id.vern.antigravity.data.ModelAvailability
import id.vern.antigravity.data.ThemeMode
import id.vern.antigravity.data.ThemePreset
import id.vern.antigravity.data.UserQuotaSummary
import id.vern.antigravity.service.AgyModelsService
import id.vern.antigravity.service.CliModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class SettingsPreferencesDelegate {
    private val mutableAppearanceConfig = MutableStateFlow(
        AppearanceConfig(
            themeMode = ThemeMode.SYSTEM,
            accentColor = AccentColor.BLUE
        )
    )
    val appearanceConfig: StateFlow<AppearanceConfig> = mutableAppearanceConfig.asStateFlow()

    private val mutableSelectedModel = MutableStateFlow("")
    val selectedModel: StateFlow<String> = mutableSelectedModel.asStateFlow()

    private val mutableSelectedModelCliId = MutableStateFlow<String?>(null)
    val selectedModelCliId: StateFlow<String?> = mutableSelectedModelCliId.asStateFlow()

    private val mutableThinkingBudget = MutableStateFlow("")
    val thinkingBudget: StateFlow<String> = mutableThinkingBudget.asStateFlow()

    private val mutableVerboseThinking = MutableStateFlow(false)
    val verboseThinking: StateFlow<Boolean> = mutableVerboseThinking.asStateFlow()

    private val mutableConversationWidth = MutableStateFlow("Default")
    val conversationWidth: StateFlow<String> = mutableConversationWidth.asStateFlow()

    private val mutableThemePreset = MutableStateFlow("Default Dark")
    val themePreset: StateFlow<String> = mutableThemePreset.asStateFlow()

    private val mutableCustomThemeBg = MutableStateFlow("#101010")
    val customThemeBg: StateFlow<String> = mutableCustomThemeBg.asStateFlow()

    private val mutableCustomThemeFg = MutableStateFlow("#CCCCCC")
    val customThemeFg: StateFlow<String> = mutableCustomThemeFg.asStateFlow()

    private val mutableCustomThemeAccent = MutableStateFlow("#1A73E8")
    val customThemeAccent: StateFlow<String> = mutableCustomThemeAccent.asStateFlow()

    private val mutableEditorWordWrap = MutableStateFlow(true)
    val editorWordWrap: StateFlow<Boolean> = mutableEditorWordWrap.asStateFlow()

    private val mutableEditorLineNumbers = MutableStateFlow(true)
    val editorLineNumbers: StateFlow<Boolean> = mutableEditorLineNumbers.asStateFlow()

    private val mutableEditorFontSize = MutableStateFlow(13)
    val editorFontSize: StateFlow<Int> = mutableEditorFontSize.asStateFlow()

    private val mutableSkipPermissions = MutableStateFlow(true)
    val skipPermissions: StateFlow<Boolean> = mutableSkipPermissions.asStateFlow()

    private val mutableQueuedMessagesMode = MutableStateFlow("Queue")
    val queuedMessagesMode: StateFlow<String> = mutableQueuedMessagesMode.asStateFlow()

    private val mutableSecurityPreset = MutableStateFlow("Turbo Mode")
    val securityPreset: StateFlow<String> = mutableSecurityPreset.asStateFlow()

    private val mutablePlanReviewPolicy = MutableStateFlow("Always Ask")
    val planReviewPolicy: StateFlow<String> = mutablePlanReviewPolicy.asStateFlow()

    private val mutableBrowserJsPolicy = MutableStateFlow("Request Review")
    val browserJsPolicy: StateFlow<String> = mutableBrowserJsPolicy.asStateFlow()

    private val mutableEnableCreditOverages = MutableStateFlow(false)
    val enableCreditOverages: StateFlow<Boolean> = mutableEnableCreditOverages.asStateFlow()

    private val mutablePermissionState = MutableStateFlow(PermissionGrantsState())
    val permissionState: StateFlow<PermissionGrantsState> = mutablePermissionState.asStateFlow()

    private val mutableInstalledRulesList = MutableStateFlow<List<RuleItem>>(emptyList())
    val installedRulesList: StateFlow<List<RuleItem>> = mutableInstalledRulesList.asStateFlow()

    private val mutableMcpServers = MutableStateFlow<List<McpServerItem>>(emptyList())
    val mcpServers: StateFlow<List<McpServerItem>> = mutableMcpServers.asStateFlow()

    fun loadPreferences(
        context: Context,
        onCliModelsDecoded: (List<CliModel>) -> Unit,
        onWorkspaceLoaded: (String) -> Unit
    ) {
        val prefs = context.getSharedPreferences(AntigravityViewModel.PREFS_NAME, Context.MODE_PRIVATE)
        val themeName = prefs.getString(AntigravityViewModel.KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        val themeMode = try { ThemeMode.valueOf(themeName) } catch (_: Exception) { ThemeMode.SYSTEM }
        val accentName = prefs.getString(AntigravityViewModel.KEY_ACCENT_COLOR, AccentColor.BLUE.name) ?: AccentColor.BLUE.name
        val accentColor = try { AccentColor.valueOf(accentName) } catch (_: Exception) { AccentColor.BLUE }
        val savedWorkspace = prefs.getString(AntigravityViewModel.KEY_WORKSPACE_PATH, null)
        val savedModel = prefs.getString(AntigravityViewModel.KEY_SELECTED_MODEL, "") ?: ""

        val budget = prefs.getString(AntigravityViewModel.KEY_THINKING_BUDGET, "") ?: ""
        val verbose = prefs.getBoolean(AntigravityViewModel.KEY_VERBOSE_THINKING, false)
        val width = prefs.getString(AntigravityViewModel.KEY_CONVERSATION_WIDTH, "Default") ?: "Default"
        val preset = prefs.getString(AntigravityViewModel.KEY_THEME_PRESET, if (themeMode == ThemeMode.LIGHT) "Default Light" else "Default Dark") ?: "Default Dark"
        val customBg = prefs.getString(AntigravityViewModel.KEY_CUSTOM_THEME_BG, "#101010") ?: "#101010"
        val customFg = prefs.getString(AntigravityViewModel.KEY_CUSTOM_THEME_FG, "#CCCCCC") ?: "#CCCCCC"
        val customAccent = prefs.getString(AntigravityViewModel.KEY_CUSTOM_THEME_ACCENT, "#1A73E8") ?: "#1A73E8"
        val wrap = prefs.getBoolean(AntigravityViewModel.KEY_EDITOR_WORD_WRAP, true)
        val lineNums = prefs.getBoolean(AntigravityViewModel.KEY_EDITOR_LINE_NUMBERS, true)
        val fontSize = prefs.getInt(AntigravityViewModel.KEY_EDITOR_FONT_SIZE, 13)
        val skipPerms = prefs.getBoolean(AntigravityViewModel.KEY_SKIP_PERMISSIONS, true)
        val queued = prefs.getString(AntigravityViewModel.KEY_QUEUED_MESSAGES_MODE, "Queue") ?: "Queue"
        val secPreset = prefs.getString(AntigravityViewModel.KEY_SECURITY_PRESET, "Turbo Mode") ?: "Turbo Mode"
        val planReview = prefs.getString(AntigravityViewModel.KEY_PLAN_REVIEW_POLICY, "Always Ask") ?: "Always Ask"
        val browserJs = prefs.getString(AntigravityViewModel.KEY_BROWSER_JS_POLICY, "Request Review") ?: "Request Review"
        val creditOverages = prefs.getBoolean(AntigravityViewModel.KEY_ENABLE_CREDIT_OVERAGES, false)

        mutableAppearanceConfig.value = AppearanceConfig(
            themeMode = themeMode,
            accentColor = accentColor
        )
        mutableSelectedModel.value = savedModel

        val savedCliId = prefs.getString(AntigravityViewModel.KEY_SELECTED_MODEL_CLI_ID, null)
        val cleanSavedCliId = savedCliId?.let { AgyModelsService.cleanModelId(it) }
        mutableSelectedModelCliId.value = cleanSavedCliId
        if (savedCliId != cleanSavedCliId) {
            val prefEditor = prefs.edit()
            if (cleanSavedCliId != null) {
                prefEditor.putString(AntigravityViewModel.KEY_SELECTED_MODEL_CLI_ID, cleanSavedCliId)
            } else {
                prefEditor.remove(AntigravityViewModel.KEY_SELECTED_MODEL_CLI_ID)
            }
            prefEditor.apply()
        }
        val cachedCliJson = prefs.getString(AntigravityViewModel.KEY_CLI_MODELS_JSON, null)
        if (!cachedCliJson.isNullOrBlank()) {
            onCliModelsDecoded(AgyModelsService.decodeCliModels(cachedCliJson))
        }
        mutableThinkingBudget.value = budget
        mutableVerboseThinking.value = verbose
        mutableConversationWidth.value = width
        mutableThemePreset.value = preset
        mutableCustomThemeBg.value = customBg
        mutableCustomThemeFg.value = customFg
        mutableCustomThemeAccent.value = customAccent
        mutableEditorWordWrap.value = wrap
        mutableEditorLineNumbers.value = lineNums
        mutableEditorFontSize.value = fontSize
        mutableSkipPermissions.value = skipPerms
        mutableQueuedMessagesMode.value = queued
        mutableSecurityPreset.value = secPreset
        mutablePlanReviewPolicy.value = planReview
        mutableBrowserJsPolicy.value = browserJs
        mutableEnableCreditOverages.value = creditOverages

        if (!savedWorkspace.isNullOrBlank() && File(savedWorkspace).exists()) {
            onWorkspaceLoaded(savedWorkspace)
        } else {
            onWorkspaceLoaded("")
        }
    }

    fun setModel(
        context: Context,
        model: String,
        budget: String = mutableThinkingBudget.value,
        cliId: String? = null,
        userQuotaSummary: UserQuotaSummary?,
        onLog: (String, String) -> Unit
    ) {
        val availability = userQuotaSummary?.availableModels?.firstOrNull { it.matches(model) }?.availability ?: ModelAvailability.UNKNOWN

        if (availability == ModelAvailability.UNAVAILABLE) {
            val unavailMsg = context.getString(R.string.model_toast_not_available, model)
            onLog("Model", unavailMsg)
            Toast.makeText(context, unavailMsg, Toast.LENGTH_SHORT).show()
            return
        }
        val cleanCliId = cliId?.let { AgyModelsService.cleanModelId(it) }
        mutableSelectedModel.value = model
        mutableThinkingBudget.value = budget
        mutableSelectedModelCliId.value = cleanCliId
        val prefs = context.getSharedPreferences(AntigravityViewModel.PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit().putString(AntigravityViewModel.KEY_SELECTED_MODEL, model).putString(AntigravityViewModel.KEY_THINKING_BUDGET, budget)
        if (cleanCliId != null) editor.putString(AntigravityViewModel.KEY_SELECTED_MODEL_CLI_ID, cleanCliId) else editor.remove(AntigravityViewModel.KEY_SELECTED_MODEL_CLI_ID)
        editor.apply()
        val thinkingDesc = if (budget.isNotBlank()) "$budget Thinking" else "Default"
        onLog("Model", "Selected model: $model ($thinkingDesc)")
    }

    fun setThinkingBudget(context: Context, budget: String) {
        mutableThinkingBudget.value = budget
        val prefs = context.getSharedPreferences(AntigravityViewModel.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(AntigravityViewModel.KEY_THINKING_BUDGET, budget).apply()
    }

    fun setVerboseThinking(context: Context, verbose: Boolean) {
        mutableVerboseThinking.value = verbose
        val prefs = context.getSharedPreferences(AntigravityViewModel.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(AntigravityViewModel.KEY_VERBOSE_THINKING, verbose).apply()
    }

    fun setConversationWidth(context: Context, width: String) {
        mutableConversationWidth.value = width
        val prefs = context.getSharedPreferences(AntigravityViewModel.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(AntigravityViewModel.KEY_CONVERSATION_WIDTH, width).apply()
    }

    fun setThemeMode(context: Context, mode: ThemeMode, onLog: (String, String) -> Unit) {
        val newPreset = when (mode) {
            ThemeMode.LIGHT -> ThemePreset.DEFAULT_LIGHT.displayName
            ThemeMode.DARK -> {
                if (mutableThemePreset.value == ThemePreset.DEFAULT_LIGHT.displayName) {
                    ThemePreset.DEFAULT_DARK.displayName
                } else {
                    mutableThemePreset.value
                }
            }
            ThemeMode.SYSTEM -> mutableThemePreset.value
        }
        mutableAppearanceConfig.update { it.copy(themeMode = mode) }
        mutableThemePreset.value = newPreset
        val prefs = context.getSharedPreferences(AntigravityViewModel.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(AntigravityViewModel.KEY_THEME_MODE, mode.name)
            .putString(AntigravityViewModel.KEY_THEME_PRESET, newPreset)
            .apply()
        onLog("Appearance", "Theme set to: ${mode.name}")
    }

    fun setAccentColor(context: Context, accent: AccentColor, onLog: (String, String) -> Unit) {
        mutableAppearanceConfig.update { it.copy(accentColor = accent) }
        val prefs = context.getSharedPreferences(AntigravityViewModel.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(AntigravityViewModel.KEY_ACCENT_COLOR, accent.name).apply()
        onLog("Appearance", "Accent color set to: ${accent.displayName}")
    }

    fun setThemePreset(context: Context, preset: String, onLog: (String, String) -> Unit) {
        mutableThemePreset.value = preset
        val prefs = context.getSharedPreferences(AntigravityViewModel.PREFS_NAME, Context.MODE_PRIVATE)
        if (preset == ThemePreset.DEFAULT_LIGHT.displayName) {
            mutableAppearanceConfig.update { it.copy(themeMode = ThemeMode.LIGHT) }
            prefs.edit().putString(AntigravityViewModel.KEY_THEME_MODE, ThemeMode.LIGHT.name).apply()
        } else if (mutableAppearanceConfig.value.themeMode == ThemeMode.LIGHT) {
            mutableAppearanceConfig.update { it.copy(themeMode = ThemeMode.DARK) }
            prefs.edit().putString(AntigravityViewModel.KEY_THEME_MODE, ThemeMode.DARK.name).apply()
        }
        prefs.edit().putString(AntigravityViewModel.KEY_THEME_PRESET, preset).apply()
        onLog("Appearance", "Theme preset set to: $preset")
    }

    fun updateCustomThemeColor(
        context: Context,
        target: String,
        newHex: String,
        currentPreset: ThemePreset,
        onLog: (String, String) -> Unit
    ) {
        val prefs = context.getSharedPreferences(AntigravityViewModel.PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        if (mutableThemePreset.value != ThemePreset.CUSTOM.displayName && currentPreset != ThemePreset.CUSTOM) {
            mutableCustomThemeBg.value = currentPreset.bgHex
            mutableCustomThemeFg.value = currentPreset.fgHex
            mutableCustomThemeAccent.value = currentPreset.accentHex
            editor.putString(AntigravityViewModel.KEY_CUSTOM_THEME_BG, currentPreset.bgHex)
            editor.putString(AntigravityViewModel.KEY_CUSTOM_THEME_FG, currentPreset.fgHex)
            editor.putString(AntigravityViewModel.KEY_CUSTOM_THEME_ACCENT, currentPreset.accentHex)
        }

        when (target) {
            "bg" -> {
                mutableCustomThemeBg.value = newHex
                editor.putString(AntigravityViewModel.KEY_CUSTOM_THEME_BG, newHex)
            }
            "fg" -> {
                mutableCustomThemeFg.value = newHex
                editor.putString(AntigravityViewModel.KEY_CUSTOM_THEME_FG, newHex)
            }
            "accent" -> {
                mutableCustomThemeAccent.value = newHex
                editor.putString(AntigravityViewModel.KEY_CUSTOM_THEME_ACCENT, newHex)
            }
        }
        mutableThemePreset.value = ThemePreset.CUSTOM.displayName
        editor.putString(AntigravityViewModel.KEY_THEME_PRESET, ThemePreset.CUSTOM.displayName)
        editor.apply()
        onLog("Appearance", "Custom theme color $target updated to: $newHex")
    }

    fun setEditorWordWrap(context: Context, wrap: Boolean) {
        mutableEditorWordWrap.value = wrap
        val prefs = context.getSharedPreferences(AntigravityViewModel.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(AntigravityViewModel.KEY_EDITOR_WORD_WRAP, wrap).apply()
    }

    fun setEditorLineNumbers(context: Context, show: Boolean) {
        mutableEditorLineNumbers.value = show
        val prefs = context.getSharedPreferences(AntigravityViewModel.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(AntigravityViewModel.KEY_EDITOR_LINE_NUMBERS, show).apply()
    }

    fun setEditorFontSize(context: Context, size: Int) {
        mutableEditorFontSize.value = size
        val prefs = context.getSharedPreferences(AntigravityViewModel.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(AntigravityViewModel.KEY_EDITOR_FONT_SIZE, size).apply()
    }

    fun setSkipPermissions(context: Context, skip: Boolean) {
        mutableSkipPermissions.value = skip
        val prefs = context.getSharedPreferences(AntigravityViewModel.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(AntigravityViewModel.KEY_SKIP_PERMISSIONS, skip).apply()
    }

    fun setQueuedMessagesMode(context: Context, mode: String, onLog: (String, String) -> Unit) {
        mutableQueuedMessagesMode.value = mode
        val prefs = context.getSharedPreferences(AntigravityViewModel.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(AntigravityViewModel.KEY_QUEUED_MESSAGES_MODE, mode).apply()
        onLog("General", "Queued messages delivery set to: $mode")
    }

    fun setSecurityPreset(
        context: Context,
        preset: String,
        scope: CoroutineScope,
        onLog: (String, String) -> Unit
    ) {
        val normalizedPreset = when (preset.lowercase()) {
            "turbo", "turbo mode" -> "Turbo"
            "auto", "permissive" -> "Auto"
            "default", "standard" -> "Default"
            "request review", "request_review" -> "Request Review"
            else -> preset
        }
        mutableSecurityPreset.value = normalizedPreset
        val prefs = context.getSharedPreferences(AntigravityViewModel.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(AntigravityViewModel.KEY_SECURITY_PRESET, normalizedPreset).apply()
        onLog("General", "Security preset set to: $normalizedPreset")

        val autoExec = when (normalizedPreset) {
            "Turbo" -> "CASCADE_COMMANDS_AUTO_EXECUTION_EAGER"
            "Auto" -> "CASCADE_COMMANDS_AUTO_EXECUTION_ALLOW"
            "Default" -> "CASCADE_COMMANDS_AUTO_EXECUTION_ASK"
            "Request Review" -> "CASCADE_COMMANDS_AUTO_EXECUTION_NEVER"
            else -> "CASCADE_COMMANDS_AUTO_EXECUTION_EAGER"
        }
        val planReview = when (normalizedPreset) {
            "Turbo", "Auto" -> "Always Proceed"
            "Default" -> "Ask for Sensitive"
            "Request Review" -> "Always Ask"
            else -> "Always Proceed"
        }
        val enableSandbox = normalizedPreset == "Default" || normalizedPreset == "Request Review"
        val skipPerms = normalizedPreset == "Turbo"

        setSkipPermissions(context, skipPerms)
        mutablePlanReviewPolicy.value = planReview

        scope.launch {
            val current = mutablePermissionState.value
            val updated = current.copy(
                securityPreset = normalizedPreset,
                autoExecutionPolicy = autoExec,
                planReviewPolicy = planReview,
                enableTerminalSandbox = enableSandbox
            )
            mutablePermissionState.value = updated
            PermissionConfigService.savePermissions(context, updated)
        }
    }

    fun setPlanReviewPolicy(
        context: Context,
        policy: String,
        scope: CoroutineScope,
        onLog: (String, String) -> Unit
    ) {
        mutablePlanReviewPolicy.value = policy
        val prefs = context.getSharedPreferences(AntigravityViewModel.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(AntigravityViewModel.KEY_PLAN_REVIEW_POLICY, policy).apply()
        onLog("General", "Plan review policy set to: $policy")

        scope.launch {
            val current = mutablePermissionState.value
            val updated = current.copy(planReviewPolicy = policy)
            mutablePermissionState.value = updated
            PermissionConfigService.savePermissions(context, updated)
        }
    }

    fun setBrowserJsPolicy(context: Context, policy: String, onLog: (String, String) -> Unit) {
        mutableBrowserJsPolicy.value = policy
        val prefs = context.getSharedPreferences(AntigravityViewModel.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(AntigravityViewModel.KEY_BROWSER_JS_POLICY, policy).apply()
        onLog("General", "Browser JS policy set to: $policy")
    }

    fun setEnableCreditOverages(context: Context, enabled: Boolean, onLog: (String, String) -> Unit) {
        mutableEnableCreditOverages.value = enabled
        val prefs = context.getSharedPreferences(AntigravityViewModel.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(AntigravityViewModel.KEY_ENABLE_CREDIT_OVERAGES, enabled).apply()
        onLog("Models", "AI credit overages set to: $enabled")
    }

    fun refreshPermissions(context: Context, scope: CoroutineScope) {
        scope.launch {
            val state = PermissionConfigService.loadPermissions(context)
            mutablePermissionState.value = state
        }
    }

    fun addPermissionRule(
        context: Context,
        action: String,
        pattern: String,
        mode: String,
        scope: CoroutineScope,
        onLog: (String, String) -> Unit
    ) {
        scope.launch {
            val current = mutablePermissionState.value
            val cleanPattern = pattern.trim()
            if (cleanPattern.isBlank()) return@launch
            val filtered = current.rules.filterNot { it.action == action && it.pattern == cleanPattern }
            val updated = filtered + PermissionRule(action, cleanPattern, mode)
            val newState = current.copy(rules = updated)
            mutablePermissionState.value = newState
            PermissionConfigService.savePermissions(context, newState)
            onLog("Permissions", "Added $mode rule for $action: $cleanPattern")
        }
    }

    fun updatePermissionRule(
        context: Context,
        action: String,
        oldPattern: String,
        newPattern: String,
        newMode: String,
        scope: CoroutineScope,
        onLog: (String, String) -> Unit
    ) {
        scope.launch {
            val current = mutablePermissionState.value
            val cleanOld = oldPattern.trim()
            val cleanNew = newPattern.trim()
            if (cleanNew.isBlank()) return@launch
            val updated = current.rules.map {
                if (it.action == action && it.pattern == cleanOld) PermissionRule(action, cleanNew, newMode) else it
            }
            val newState = current.copy(rules = updated)
            mutablePermissionState.value = newState
            PermissionConfigService.savePermissions(context, newState)
            onLog("Permissions", "Updated rule for $action: $cleanNew ($newMode)")
        }
    }

    fun removePermissionRule(
        context: Context,
        action: String,
        pattern: String,
        scope: CoroutineScope,
        onLog: (String, String) -> Unit
    ) {
        scope.launch {
            val current = mutablePermissionState.value
            val cleanPattern = pattern.trim()
            val updated = current.rules.filterNot { it.action == action && it.pattern == cleanPattern }
            val newState = current.copy(rules = updated)
            mutablePermissionState.value = newState
            PermissionConfigService.savePermissions(context, newState)
            onLog("Permissions", "Removed rule for $action: $cleanPattern")
        }
    }

    fun refreshRules(context: Context, workspacePath: String, scope: CoroutineScope) {
        scope.launch {
            val rules = RuleConfigService.loadAllRules(context, workspacePath)
            mutableInstalledRulesList.value = rules
        }
    }

    fun saveRuleContent(
        context: Context,
        filePath: String,
        content: String,
        workspacePath: String,
        scope: CoroutineScope,
        onLog: (String, String) -> Unit,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        scope.launch {
            val ok = RuleConfigService.saveRule(filePath, content)
            if (ok) {
                refreshRules(context, workspacePath, scope)
                onLog("Rules", "Rule saved: ${File(filePath).name}")
            }
            onComplete?.invoke(ok)
        }
    }

    fun createRule(
        context: Context,
        ruleName: String,
        content: String,
        workspacePath: String,
        scope: CoroutineScope,
        onLog: (String, String) -> Unit,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        scope.launch {
            val ok = RuleConfigService.createNewRule(context, ruleName, content)
            if (ok) {
                refreshRules(context, workspacePath, scope)
                onLog("Rules", "Created new rule: $ruleName")
            }
            onComplete?.invoke(ok)
        }
    }

    fun deleteRule(
        context: Context,
        filePath: String,
        workspacePath: String,
        scope: CoroutineScope,
        onLog: (String, String) -> Unit,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        scope.launch {
            val ok = RuleConfigService.deleteRule(filePath)
            if (ok) {
                refreshRules(context, workspacePath, scope)
                onLog("Rules", "Deleted rule: ${File(filePath).name}")
            }
            onComplete?.invoke(ok)
        }
    }

    fun refreshMcpServers(context: Context, scope: CoroutineScope) {
        scope.launch {
            val list = McpConfigService.loadMcpServers(context)
            mutableMcpServers.value = list
        }
    }

    fun saveMcpServer(
        context: Context,
        server: McpServerItem,
        scope: CoroutineScope,
        onLog: (String, String) -> Unit,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        scope.launch {
            val ok = McpConfigService.saveMcpServer(context, server)
            if (ok) {
                refreshMcpServers(context, scope)
                onLog("MCP", "Configured MCP server: ${server.name}")
            }
            onComplete?.invoke(ok)
        }
    }

    fun deleteMcpServer(
        context: Context,
        serverName: String,
        scope: CoroutineScope,
        onLog: (String, String) -> Unit,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        scope.launch {
            val ok = McpConfigService.deleteMcpServer(context, serverName)
            if (ok) {
                refreshMcpServers(context, scope)
                onLog("MCP", "Removed MCP server: $serverName")
            }
            onComplete?.invoke(ok)
        }
    }

    suspend fun loadRawMcpConfig(context: Context): String {
        return McpConfigService.loadRawConfig(context)
    }

    fun saveRawMcpConfig(
        context: Context,
        rawJson: String,
        scope: CoroutineScope,
        onLog: (String, String) -> Unit,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        scope.launch {
            val ok = McpConfigService.saveRawConfig(context, rawJson)
            if (ok) {
                refreshMcpServers(context, scope)
                onLog("MCP", "Updated raw mcp_config.json")
            }
            onComplete?.invoke(ok)
        }
    }

    fun setSelectedModelDirect(model: String) {
        mutableSelectedModel.value = model
    }

    fun setSelectedModelCliIdDirect(cliId: String?) {
        mutableSelectedModelCliId.value = cliId
    }

    fun setThinkingBudgetDirect(budget: String) {
        mutableThinkingBudget.value = budget
    }
}
