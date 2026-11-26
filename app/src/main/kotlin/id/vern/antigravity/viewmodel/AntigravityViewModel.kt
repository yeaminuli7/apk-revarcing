package id.vern.antigravity.viewmodel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.isActive
import id.vern.antigravity.AntigravityApp
import id.vern.antigravity.R
import id.vern.antigravity.data.AccentColor
import id.vern.antigravity.data.AppearanceConfig
import id.vern.antigravity.data.AttachedFile
import id.vern.antigravity.data.ChatMessage
import id.vern.antigravity.data.QueuedMessage
import id.vern.antigravity.data.RunningTask
import id.vern.antigravity.data.RunningSubagent
import id.vern.antigravity.data.DaemonTask
import id.vern.antigravity.data.AgentExecutionStep
import id.vern.antigravity.data.AgentStepKind
import id.vern.antigravity.data.ChangedFileDetail
import id.vern.antigravity.data.ChangedFileItem
import id.vern.antigravity.data.OpenTab
import id.vern.antigravity.data.ArtifactFileItem
import id.vern.antigravity.data.TurnFileChanges
import id.vern.antigravity.data.EngineStatus
import id.vern.antigravity.data.ExecutionLogEntry
import id.vern.antigravity.data.MessageRole
import id.vern.antigravity.data.MessageStatus
import id.vern.antigravity.data.ProjectSortOrder
import id.vern.antigravity.data.ThemeMode
import id.vern.antigravity.data.ThemePreset
import id.vern.antigravity.data.UserQuotaSummary
import id.vern.antigravity.data.CodeAssistTier
import id.vern.antigravity.data.CliSlashCommand
import id.vern.antigravity.data.ModelAvailability
import id.vern.antigravity.data.SlashAction
import id.vern.antigravity.data.SlashCommandParser
import id.vern.antigravity.data.SYSTEM_SLASH_COMMANDS
import id.vern.antigravity.data.mergeDynamicSlashActions
import id.vern.antigravity.service.AgyModelsService
import id.vern.antigravity.service.CliModel
import id.vern.antigravity.service.CloudCodeQuotaService
import id.vern.antigravity.service.PermissionConfigService
import id.vern.antigravity.service.PermissionGrantsState
import id.vern.antigravity.service.PermissionRule
import id.vern.antigravity.service.RuleConfigService
import id.vern.antigravity.service.RuleItem
import id.vern.antigravity.service.McpConfigService
import id.vern.antigravity.service.McpServerItem
import id.vern.antigravity.service.WakeLockHolder
import id.vern.antigravity.service.TerminalLine
import id.vern.antigravity.service.TerminalSessionManager
import id.vern.antigravity.executor.EngineExecutor
import id.vern.antigravity.install.BootstrapInstaller
import id.vern.antigravity.api.GeminiClient
import id.vern.antigravity.service.CoreServerService
import id.vern.antigravity.util.AppLogger
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import android.provider.DocumentsContract
import android.util.Log
import android.system.Os
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

data class CustomizationInstallProgress(
    val title: String,
    val command: String,
    val lines: List<TerminalLine> = emptyList(),
    val isRunning: Boolean = true,
    val isSuccess: Boolean? = null
)

data class ConversationSummary(
    val id: String,
    val title: String,
    val timestamp: Long,
    val messageCount: Int,
    val isPinned: Boolean = false,
    val projectPath: String? = null
)

data class CustomizationItem(
    val id: String,
    val name: String,
    val description: String,
    val type: String,
    val isBuiltIn: Boolean = false,
    val path: String? = null
)

data class SkillCatalogueItem(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val isPlugin: Boolean = false,
    val defaultPrompt: String = "",
    val repoUrl: String = "",
    val subPath: String? = null
)

data class DiscoveredRepoSkill(
    val id: String,
    val name: String,
    val description: String,
    val subPath: String,
    val isSelected: Boolean = true
)

data class CustomizationRepoTrack(
    val id: String,
    val name: String,
    val repoUrl: String,
    val isPlugin: Boolean = false,
    val subPath: String? = null,
    val installedSha: String = "",
    val remoteSha: String? = null,
    val lastChecked: Long = 0L,
    val hasUpdate: Boolean = false
)

data class TokenUsageStats(
    val rulesTokens: Int,
    val rulesBreakdowns: List<Pair<String, Int>>,
    val skillsTokens: Int,
    val skillsBreakdowns: List<Pair<String, Int>>,
    val totalLimit: Int = 20000
)

class AntigravityViewModel : ViewModel() {

    companion object {
        const val PREFS_NAME = "antigravity_prefs"
        const val KEY_THEME_MODE = "ag_theme_mode"
        const val KEY_ACCENT_COLOR = "ag_accent_color"
        const val KEY_WORKSPACE_PATH = "ag_workspace_path"
        const val KEY_SELECTED_MODEL = "ag_selected_model"
        const val KEY_SELECTED_MODEL_CLI_ID = "ag_selected_model_cli_id"
        const val KEY_CLI_MODELS_JSON = "ag_cli_models_json"
        const val KEY_CLI_MODELS_FETCHED_AT = "ag_cli_models_fetched_at"
        const val KEY_CLI_SLASH_COMMANDS = "ag_cli_slash_commands"
        const val KEY_CLI_SLASH_VERSION = "ag_cli_slash_version"
        const val KEY_CLI_SLASH_FETCHED_AT = "ag_cli_slash_fetched_at"
        const val CLI_MODELS_THROTTLE_MS = 10L * 60L * 1000L
        const val KEY_THINKING_BUDGET = "ag_thinking_budget"
        const val KEY_VERBOSE_THINKING = "ag_verbose_thinking"
        const val KEY_CONVERSATION_WIDTH = "ag_conversation_width"
        const val KEY_THEME_PRESET = "ag_theme_preset"
        const val KEY_CUSTOM_THEME_BG = "ag_custom_theme_bg"
        const val KEY_CUSTOM_THEME_FG = "ag_custom_theme_fg"
        const val KEY_CUSTOM_THEME_ACCENT = "ag_custom_theme_accent"

        // Default Gemini models (always available, no server needed)
        val DEFAULT_GEMINI_MODELS: List<CliModel> = listOf(
            CliModel(
                cliId = "gemini-2.5-flash",
                displayName = "Gemini 2.5 Flash",
                label = "Gemini 2.5 Flash",
                provider = "Google",
                effort = "",
                isDefault = true
            ),
            CliModel(
                cliId = "gemini-2.5-flash-lite",
                displayName = "Gemini 2.5 Flash-Lite",
                label = "Gemini 2.5 Flash-Lite",
                provider = "Google",
                effort = "",
                isDefault = false
            )
        )
        const val KEY_EDITOR_WORD_WRAP = "ag_editor_word_wrap"
        const val KEY_EDITOR_LINE_NUMBERS = "ag_editor_line_numbers"
        const val KEY_EDITOR_FONT_SIZE = "ag_editor_font_size"
        const val KEY_SKIP_PERMISSIONS = "ag_skip_permissions"
        const val KEY_PROJECT_SORT = "ag_project_sort"
        const val KEY_QUEUED_MESSAGES_MODE = "ag_queued_messages_mode"
        const val KEY_SECURITY_PRESET = "ag_security_preset"
        const val KEY_PLAN_REVIEW_POLICY = "ag_plan_review_policy"
        const val KEY_BROWSER_JS_POLICY = "ag_browser_js_policy"
        const val KEY_ENABLE_CREDIT_OVERAGES = "ag_enable_credit_overages"
        const val KEY_USER_PROJECTS = "ag_user_projects"
        const val KEY_CONVERSATION_HISTORY = "ag_conversation_history"
        const val KEY_LAST_ACTIVE_CONV_ID = "ag_last_active_conv_id"
        const val KEY_CUSTOMIZATION_TRACKING = "ag_customization_tracking"
        const val KEY_CUSTOMIZATION_LAST_CHECK = "ag_customization_last_check"
    }

    private val projectWorkspaceDelegate = ProjectWorkspaceDelegate()
    private val conversationManagerDelegate = ConversationManagerDelegate()
    private val authDelegate = AuthDelegate()
    private val settingsPreferencesDelegate = SettingsPreferencesDelegate()
    private val customizationInstallerDelegate = CustomizationInstallerDelegate()
    private val customizationCatalogueDelegate = CustomizationCatalogueDelegate()

    private val mutableChatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = mutableChatMessages.asStateFlow()

    val conversationTitle: StateFlow<String> = conversationManagerDelegate.conversationTitle
    val activeConversationId: StateFlow<String?> = conversationManagerDelegate.activeConversationId
    val isConversationPinned: StateFlow<Boolean> = conversationManagerDelegate.isConversationPinned
    val conversationHistory: StateFlow<List<ConversationSummary>> = conversationManagerDelegate.conversationHistory

    val appearanceConfig: StateFlow<AppearanceConfig> = settingsPreferencesDelegate.appearanceConfig
    val workspacePath: StateFlow<String> = projectWorkspaceDelegate.workspacePath
    val selectedModel: StateFlow<String> = settingsPreferencesDelegate.selectedModel
    val selectedModelCliId: StateFlow<String?> = settingsPreferencesDelegate.selectedModelCliId
    val thinkingBudget: StateFlow<String> = settingsPreferencesDelegate.thinkingBudget
    val verboseThinking: StateFlow<Boolean> = settingsPreferencesDelegate.verboseThinking
    val conversationWidth: StateFlow<String> = settingsPreferencesDelegate.conversationWidth
    val themePreset: StateFlow<String> = settingsPreferencesDelegate.themePreset
    val customThemeBg: StateFlow<String> = settingsPreferencesDelegate.customThemeBg
    val customThemeFg: StateFlow<String> = settingsPreferencesDelegate.customThemeFg
    val customThemeAccent: StateFlow<String> = settingsPreferencesDelegate.customThemeAccent
    val editorWordWrap: StateFlow<Boolean> = settingsPreferencesDelegate.editorWordWrap
    val editorLineNumbers: StateFlow<Boolean> = settingsPreferencesDelegate.editorLineNumbers
    val editorFontSize: StateFlow<Int> = settingsPreferencesDelegate.editorFontSize
    val skipPermissions: StateFlow<Boolean> = settingsPreferencesDelegate.skipPermissions
    val queuedMessagesMode: StateFlow<String> = settingsPreferencesDelegate.queuedMessagesMode
    val securityPreset: StateFlow<String> = settingsPreferencesDelegate.securityPreset
    val planReviewPolicy: StateFlow<String> = settingsPreferencesDelegate.planReviewPolicy
    val browserJsPolicy: StateFlow<String> = settingsPreferencesDelegate.browserJsPolicy
    val enableCreditOverages: StateFlow<Boolean> = settingsPreferencesDelegate.enableCreditOverages
    val permissionState: StateFlow<PermissionGrantsState> = settingsPreferencesDelegate.permissionState
    val installedRulesList: StateFlow<List<RuleItem>> = settingsPreferencesDelegate.installedRulesList
    val mcpServers: StateFlow<List<McpServerItem>> = settingsPreferencesDelegate.mcpServers

    // Google sign-in removed: the app is API-key only, so there is no account
    // state to expose. Null/empty values keep older composables compiling.
    val userEmail: StateFlow<String?> = MutableStateFlow(null)
    val userName: StateFlow<String> = MutableStateFlow("")
    val userPicture: StateFlow<String?> = MutableStateFlow(null)

    val projectList: StateFlow<List<File>> = projectWorkspaceDelegate.projectList
    val projectSortOrder: StateFlow<ProjectSortOrder> = projectWorkspaceDelegate.projectSortOrder

    val installedSkills: StateFlow<List<CustomizationItem>> = customizationCatalogueDelegate.installedSkills
    val installedPlugins: StateFlow<List<CustomizationItem>> = customizationCatalogueDelegate.installedPlugins
    val customizationTracks: StateFlow<Map<String, CustomizationRepoTrack>> = customizationCatalogueDelegate.customizationTracks
    val isCheckingCustomizationUpdates: StateFlow<Boolean> = customizationCatalogueDelegate.isCheckingCustomizationUpdates
    val skillsCatalogue: StateFlow<List<SkillCatalogueItem>> = customizationCatalogueDelegate.skillsCatalogue
    val pluginsCatalogue: StateFlow<List<SkillCatalogueItem>> = customizationCatalogueDelegate.pluginsCatalogue
    val isFetchingCatalogue: StateFlow<Boolean> = customizationCatalogueDelegate.isFetchingCatalogue
    val installingItemId: StateFlow<String?> = customizationCatalogueDelegate.installingItemId
    val tokenUsageStats: StateFlow<TokenUsageStats> = customizationCatalogueDelegate.tokenUsageStats

    val activeInstallProgress: StateFlow<CustomizationInstallProgress?> = customizationInstallerDelegate.activeInstallProgress
    val isInspectingRepo: StateFlow<Boolean> = customizationInstallerDelegate.isInspectingRepo
    val discoveredRepoSkills: StateFlow<List<DiscoveredRepoSkill>> = customizationInstallerDelegate.discoveredRepoSkills
    val repoInspectionError: StateFlow<String?> = customizationInstallerDelegate.repoInspectionError
    private val mutableAttachedFiles = MutableStateFlow<List<AttachedFile>>(emptyList())
    val attachedFiles: StateFlow<List<AttachedFile>> = mutableAttachedFiles.asStateFlow()

    private val mutableIsExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = mutableIsExecuting.asStateFlow()

    private val mutableQueuedMessages = MutableStateFlow<List<QueuedMessage>>(emptyList())
    val queuedMessages: StateFlow<List<QueuedMessage>> = mutableQueuedMessages.asStateFlow()

    private val mutableActiveWorkingStatus = MutableStateFlow<String?>(null)
    val activeWorkingStatus: StateFlow<String?> = mutableActiveWorkingStatus.asStateFlow()

    private val mutableRunningTasks = MutableStateFlow<List<RunningTask>>(emptyList())
    val runningTasks: StateFlow<List<RunningTask>> = mutableRunningTasks.asStateFlow()

    private val mutableRunningSubagents = MutableStateFlow<List<RunningSubagent>>(emptyList())
    val runningSubagents: StateFlow<List<RunningSubagent>> = mutableRunningSubagents.asStateFlow()

    private val mutableDaemonTasks = MutableStateFlow<List<DaemonTask>>(emptyList())
    val daemonTasks: StateFlow<List<DaemonTask>> = mutableDaemonTasks.asStateFlow()

    private val mutableExecutionLogs = MutableStateFlow<List<ExecutionLogEntry>>(emptyList())
    val executionLogs: StateFlow<List<ExecutionLogEntry>> = mutableExecutionLogs.asStateFlow()

    private val editorSessionDelegate = EditorSessionDelegate()
    val openEditorTabs: StateFlow<List<OpenTab>> = editorSessionDelegate.openTabs
    val activeEditorTabId: StateFlow<String?> = editorSessionDelegate.activeTabId

    fun openFileInEditor(file: File) = editorSessionDelegate.openFile(file)
    fun closeEditorTab(tabId: String) = editorSessionDelegate.closeTab(tabId)
    fun selectEditorTab(tabId: String) = editorSessionDelegate.selectTab(tabId)
    fun updateEditorContent(tabId: String, newContent: String) = editorSessionDelegate.updateContent(tabId, newContent)
    fun setEditorTabDiffView(tabId: String, isDiff: Boolean) = editorSessionDelegate.setDiffView(tabId, isDiff)
    fun saveEditorTab(context: Context, tabId: String): Boolean = editorSessionDelegate.saveTab(context, tabId)

    private val artifactManagerDelegate = ArtifactManagerDelegate()
    val conversationArtifacts: StateFlow<List<ArtifactFileItem>> = artifactManagerDelegate.conversationArtifacts

    fun refreshConversationArtifacts(context: Context) {
        viewModelScope.launch {
            artifactManagerDelegate.refreshArtifacts(context, activeConversationId.value)
        }
    }

    private val gitStatusDelegate = GitStatusDelegate()
    val gitChangedFiles: StateFlow<List<ChangedFileItem>> = gitStatusDelegate.changedFiles
    val gitDiffCache: StateFlow<Map<String, String>> = gitStatusDelegate.diffCache
    val gitLoadingDiffs: StateFlow<Set<String>> = gitStatusDelegate.loadingDiffs

    suspend fun fetchGitChangedFiles(context: Context, workspacePath: String?): List<ChangedFileItem> =
        gitStatusDelegate.fetchGitChangedFiles(context, workspacePath)

    suspend fun fetchGitDiff(context: Context, workspacePath: String?, relativePath: String): String =
        gitStatusDelegate.fetchGitDiff(context, workspacePath, relativePath)

    fun clearGitDiffCache() = gitStatusDelegate.clearDiffCache()

    private val convIdToCliSessionId = java.util.concurrent.ConcurrentHashMap<String, String>()

    init {
        AppLogger.setHandler { tag, message, isError ->
            val entry = ExecutionLogEntry(
                tag = tag,
                message = message,
                isError = isError
            )
            mutableExecutionLogs.update { current ->
                val capacity = 1000
                if (current.size >= capacity) {
                    val dropCount = current.size - capacity + 1
                    ArrayList<ExecutionLogEntry>(capacity).apply {
                        addAll(current.subList(dropCount, current.size))
                        add(entry)
                    }
                } else {
                    ArrayList<ExecutionLogEntry>(current.size + 1).apply {
                        addAll(current)
                        add(entry)
                    }
                }
            }
        }
    }

    private val mutableEngineStatus = MutableStateFlow(EngineStatus())
    val engineStatus: StateFlow<EngineStatus> = mutableEngineStatus.asStateFlow()

    private val mutablePendingInputText = MutableStateFlow<String?>(null)
    val pendingInputText: StateFlow<String?> = mutablePendingInputText.asStateFlow()

    fun clearPendingInputText() {
        mutablePendingInputText.value = null
    }

    private val mutableIsBooting = MutableStateFlow(true)
    val isBooting: StateFlow<Boolean> = mutableIsBooting.asStateFlow()

    private val mutableSetupProgress = MutableStateFlow(0f)
    val setupProgress: StateFlow<Float> = mutableSetupProgress.asStateFlow()

    private val mutableBootStatusText = MutableStateFlow("Initializing Antigravity...")
    val bootStatusText: StateFlow<String> = mutableBootStatusText.asStateFlow()

    private val mutableBootError = MutableStateFlow<String?>(null)
    val bootError: StateFlow<String?> = mutableBootError.asStateFlow()

    private val mutableBootErrorDetail = MutableStateFlow<String?>(null)
    val bootErrorDetail: StateFlow<String?> = mutableBootErrorDetail.asStateFlow()

    private val mutableUserQuotaSummary = MutableStateFlow<UserQuotaSummary?>(null)
    val userQuotaSummary: StateFlow<UserQuotaSummary?> = mutableUserQuotaSummary.asStateFlow()

    private val mutableCodeAssistTier = MutableStateFlow<CodeAssistTier?>(null)
    val codeAssistTier: StateFlow<CodeAssistTier?> = mutableCodeAssistTier.asStateFlow()

    private val mutableIsQuotaLoading = MutableStateFlow(false)
    val isQuotaLoading: StateFlow<Boolean> = mutableIsQuotaLoading.asStateFlow()

    private val mutableQuotaErrorMessage = MutableStateFlow<String?>(null)
    val quotaErrorMessage: StateFlow<String?> = mutableQuotaErrorMessage.asStateFlow()

    private val mutableCliModels = MutableStateFlow<List<CliModel>>(emptyList())
    val cliModels: StateFlow<List<CliModel>> = mutableCliModels.asStateFlow()

    fun refreshCliModels(context: Context, force: Boolean = false) {
        viewModelScope.launch {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastFetch = prefs.getLong(KEY_CLI_MODELS_FETCHED_AT, 0L)
            if (!force && System.currentTimeMillis() - lastFetch < CLI_MODELS_THROTTLE_MS && mutableCliModels.value.isNotEmpty()) return@launch
            val result = AgyModelsService.fetchCliModels(context, force = force)
            if (result.models.isNotEmpty()) {
                adoptCliCatalog(context, result.models)
                val hasArgonInCli = result.models.any { CloudCodeQuotaService.isArgonModel(it.cliId, it.displayName) }
                val currentGroups = mutableUserQuotaSummary.value?.groups.orEmpty()
                val hasArgonGroup = currentGroups.any { it.displayName.contains("argon", ignoreCase = true) }
                if (hasArgonInCli && !hasArgonGroup) {
                    val refreshedQuota = CloudCodeQuotaService.fetchQuotaSummary(context, mutableCodeAssistTier.value)
                    if (refreshedQuota != null) {
                        mutableUserQuotaSummary.value = refreshedQuota
                    }
                }
            } else {
                val quotaFallback = mutableUserQuotaSummary.value?.pickerModels.orEmpty()
                if (mutableCliModels.value.isEmpty() && quotaFallback.isNotEmpty()) {
                    adoptCliCatalog(context, quotaFallback)
                    addLog("Models", "Adopted backend model catalog as fallback (${quotaFallback.size} models)")
                } else if (mutableCliModels.value.isEmpty()) {
                    // Seed with default Gemini models (Flash and Flash-Lite) when no server models available
                    adoptCliCatalog(context, DEFAULT_GEMINI_MODELS)
                    addLog("Models", "Seeded default Gemini models (Flash, Flash-Lite)")
                } else if (mutableCliModels.value.isNotEmpty()) {
                    addLog("Models", "CLI model query skipped or timed out, keeping active catalog (${mutableCliModels.value.size} models)")
                } else if (result.error != null) {
                    addLog("Models", "agy models failed: ${result.error}" + (result.detail?.let { " — $it" } ?: ""))
                }
            }
        }
    }

    private fun adoptCliCatalog(context: Context, models: List<CliModel>) {
        if (models.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        mutableCliModels.value = models
        prefs.edit()
            .putString(KEY_CLI_MODELS_JSON, AgyModelsService.encodeCliModels(models))
            .putLong(KEY_CLI_MODELS_FETCHED_AT, System.currentTimeMillis())
            .apply()
        reconcileSelectionWithCatalog(prefs, models)
    }

    private fun reconcileSelectionWithCatalog(prefs: android.content.SharedPreferences, catalog: List<CliModel>) {
        if (catalog.isEmpty()) return
        val currentId = selectedModelCliId.value
        if (currentId != null && catalog.any { it.cliId == currentId }) return
        val currentName = selectedModel.value
        val family = catalog.filter { it.displayName == currentName }
        val adopted = when {
            family.isNotEmpty() -> family.firstOrNull { it.effort.equals(thinkingBudget.value, ignoreCase = true) }
                ?: family.firstOrNull { it.effort.isEmpty() }
                ?: family.firstOrNull()
            currentName.isBlank() -> defaultCatalogEntry(catalog)
            else -> null
        }
        settingsPreferencesDelegate.setSelectedModelCliIdDirect(adopted?.cliId)
        val editor = prefs.edit()
        if (adopted != null) {
            editor.putString(KEY_SELECTED_MODEL_CLI_ID, adopted.cliId)
            if (currentName.isBlank()) {
                settingsPreferencesDelegate.setSelectedModelDirect(adopted.displayName)
                settingsPreferencesDelegate.setThinkingBudgetDirect(adopted.effort)
                editor.putString(KEY_SELECTED_MODEL, adopted.displayName)
                editor.putString(KEY_THINKING_BUDGET, adopted.effort)
            }
        } else {
            editor.remove(KEY_SELECTED_MODEL_CLI_ID)
        }
        editor.apply()
        addLog("Model", "CLI catalog changed; stored model id reconciled to ${adopted?.cliId ?: "(CLI default)"}")
    }

    private fun defaultCatalogEntry(catalog: List<CliModel> = mutableCliModels.value): CliModel? {
        if (catalog.isEmpty()) return null
        return catalog.firstOrNull { it.isDefault }
            ?: catalog.firstOrNull { it.effort.equals("High", ignoreCase = true) }
            ?: catalog.firstOrNull { it.effort.isEmpty() }
            ?: catalog.firstOrNull()
    }

    fun refreshUserQuota(context: Context) {
        viewModelScope.launch {
            mutableIsQuotaLoading.value = true
            mutableQuotaErrorMessage.value = null
            try {
                val tier = CloudCodeQuotaService.fetchCodeAssistTier(context)
                val quota = CloudCodeQuotaService.fetchQuotaSummary(context, tier)
                mutableCodeAssistTier.value = tier
                mutableUserQuotaSummary.value = quota
                if (mutableCliModels.value.isEmpty() && !quota?.pickerModels.isNullOrEmpty()) {
                    adoptCliCatalog(context, quota!!.pickerModels)
                }

                refreshCliModels(context)
                val currentSelected = selectedModel.value

                val currentAvailability = quota?.availableModels?.firstOrNull { it.matches(currentSelected) }?.availability ?: ModelAvailability.UNKNOWN
                if (currentAvailability == ModelAvailability.UNAVAILABLE) {

                    val fallback = defaultCatalogEntry()
                    settingsPreferencesDelegate.setSelectedModelDirect(fallback?.displayName ?: "")
                    settingsPreferencesDelegate.setSelectedModelCliIdDirect(fallback?.cliId)
                    settingsPreferencesDelegate.setThinkingBudgetDirect(fallback?.effort ?: "")
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val editor = prefs.edit()
                    if (fallback != null) {
                        editor.putString(KEY_SELECTED_MODEL, fallback.displayName)
                        editor.putString(KEY_SELECTED_MODEL_CLI_ID, fallback.cliId)
                        editor.putString(KEY_THINKING_BUDGET, fallback.effort)
                    } else {
                        editor.remove(KEY_SELECTED_MODEL).remove(KEY_SELECTED_MODEL_CLI_ID).remove(KEY_THINKING_BUDGET)
                    }
                    editor.apply()
                    val fallbackMsg = if (fallback != null) {
                        context.getString(
                            R.string.model_switched_fallback,
                            currentSelected,
                            fallback.displayName
                        )
                    } else {
                        context.getString(R.string.model_switched_none, currentSelected)
                    }
                    addLog("Model", fallbackMsg)
                    Toast.makeText(context, fallbackMsg, Toast.LENGTH_SHORT).show()
                }
                if (quota?.modelsFetchFailed == true || (quota == null && tier == null)) {
                    mutableQuotaErrorMessage.value = context.getString(R.string.quota_models_load_failed)
                }
            } catch (e: Exception) {
                mutableQuotaErrorMessage.value = e.localizedMessage ?: "Network error fetching quota."
            } finally {
                mutableIsQuotaLoading.value = false
            }
        }
    }

    private val mutableShowModelSelector = MutableStateFlow(false)
    val showModelSelector: StateFlow<Boolean> = mutableShowModelSelector.asStateFlow()

    fun loadPreferences(context: Context) {
        settingsPreferencesDelegate.loadPreferences(
            context = context,
            onCliModelsDecoded = { mutableCliModels.value = it },
            onWorkspaceLoaded = { projectWorkspaceDelegate.setWorkspacePathDirect(it) }
        )
        refreshAuthState(context)
        refreshPermissions(context)
        refreshRules(context)
        refreshSkills(context)
        refreshPlugins(context)
        refreshMcpServers(context)
        loadProjects(context)
        loadConversationHistory(context)
    }

    fun setModel(context: Context, model: String, budget: String = thinkingBudget.value, cliId: String? = null) =
        settingsPreferencesDelegate.setModel(context, model, budget, cliId, mutableUserQuotaSummary.value, ::addLog)

    fun setThinkingBudget(context: Context, budget: String) =
        settingsPreferencesDelegate.setThinkingBudget(context, budget)

    fun setVerboseThinking(context: Context, verbose: Boolean) =
        settingsPreferencesDelegate.setVerboseThinking(context, verbose)

    fun setConversationWidth(context: Context, width: String) =
        settingsPreferencesDelegate.setConversationWidth(context, width)

    fun setThemeMode(context: Context, mode: ThemeMode) =
        settingsPreferencesDelegate.setThemeMode(context, mode, ::addLog)

    fun setAccentColor(context: Context, accent: AccentColor) =
        settingsPreferencesDelegate.setAccentColor(context, accent, ::addLog)

    fun setThemePreset(context: Context, preset: String) =
        settingsPreferencesDelegate.setThemePreset(context, preset, ::addLog)

    fun updateCustomThemeColor(context: Context, target: String, newHex: String, currentPreset: ThemePreset) =
        settingsPreferencesDelegate.updateCustomThemeColor(context, target, newHex, currentPreset, ::addLog)

    fun setEditorWordWrap(context: Context, wrap: Boolean) =
        settingsPreferencesDelegate.setEditorWordWrap(context, wrap)

    fun setEditorLineNumbers(context: Context, show: Boolean) =
        settingsPreferencesDelegate.setEditorLineNumbers(context, show)

    fun setEditorFontSize(context: Context, size: Int) =
        settingsPreferencesDelegate.setEditorFontSize(context, size)

    fun setSkipPermissions(context: Context, skip: Boolean) =
        settingsPreferencesDelegate.setSkipPermissions(context, skip)

    fun setQueuedMessagesMode(context: Context, mode: String) =
        settingsPreferencesDelegate.setQueuedMessagesMode(context, mode, ::addLog)

    fun setSecurityPreset(context: Context, preset: String) =
        settingsPreferencesDelegate.setSecurityPreset(context, preset, viewModelScope, ::addLog)

    fun setPlanReviewPolicy(context: Context, policy: String) =
        settingsPreferencesDelegate.setPlanReviewPolicy(context, policy, viewModelScope, ::addLog)

    fun setBrowserJsPolicy(context: Context, policy: String) =
        settingsPreferencesDelegate.setBrowserJsPolicy(context, policy, ::addLog)

    fun setEnableCreditOverages(context: Context, enabled: Boolean) =
        settingsPreferencesDelegate.setEnableCreditOverages(context, enabled, ::addLog)

    fun refreshPermissions(context: Context) =
        settingsPreferencesDelegate.refreshPermissions(context, viewModelScope)

    fun addPermissionRule(context: Context, action: String, pattern: String, mode: String) =
        settingsPreferencesDelegate.addPermissionRule(context, action, pattern, mode, viewModelScope, ::addLog)

    fun updatePermissionRule(context: Context, action: String, oldPattern: String, newPattern: String, newMode: String) =
        settingsPreferencesDelegate.updatePermissionRule(context, action, oldPattern, newPattern, newMode, viewModelScope, ::addLog)

    fun removePermissionRule(context: Context, action: String, pattern: String) =
        settingsPreferencesDelegate.removePermissionRule(context, action, pattern, viewModelScope, ::addLog)

    fun refreshRules(context: Context) =
        settingsPreferencesDelegate.refreshRules(context, workspacePath.value, viewModelScope)

    fun saveRuleContent(context: Context, filePath: String, content: String, onComplete: ((Boolean) -> Unit)? = null) =
        settingsPreferencesDelegate.saveRuleContent(context, filePath, content, workspacePath.value, viewModelScope, ::addLog, onComplete)

    fun createRule(context: Context, ruleName: String, content: String, onComplete: ((Boolean) -> Unit)? = null) =
        settingsPreferencesDelegate.createRule(context, ruleName, content, workspacePath.value, viewModelScope, ::addLog, onComplete)

    fun deleteRule(context: Context, filePath: String, onComplete: ((Boolean) -> Unit)? = null) =
        settingsPreferencesDelegate.deleteRule(context, filePath, workspacePath.value, viewModelScope, ::addLog, onComplete)

    fun refreshMcpServers(context: Context) =
        settingsPreferencesDelegate.refreshMcpServers(context, viewModelScope)

    fun saveMcpServer(context: Context, server: McpServerItem, onComplete: ((Boolean) -> Unit)? = null) =
        settingsPreferencesDelegate.saveMcpServer(context, server, viewModelScope, ::addLog, onComplete)

    fun deleteMcpServer(context: Context, serverName: String, onComplete: ((Boolean) -> Unit)? = null) =
        settingsPreferencesDelegate.deleteMcpServer(context, serverName, viewModelScope, ::addLog, onComplete)

    suspend fun loadRawMcpConfig(context: Context): String =
        settingsPreferencesDelegate.loadRawMcpConfig(context)

    fun saveRawMcpConfig(context: Context, rawJson: String, onComplete: ((Boolean) -> Unit)? = null) =
        settingsPreferencesDelegate.saveRawMcpConfig(context, rawJson, viewModelScope, ::addLog, onComplete)

    fun refreshAuthState(context: Context) =
        authDelegate.refreshAuthState(context, ::refreshUserQuota)

    /**
     * Google sign-in and account switching were removed: this build only works
     * with a Gemini API key. These stubs keep the settings UI call sites valid
     * and simply route the user to the API-key dialog.
     */
    fun startGoogleSignIn(context: Context) {
        authDelegate.showApiKeyDialog()
    }

    fun onActivityResume(context: Context) {
        // Nothing to refresh: no OAuth session exists.
    }


    fun showAccessibilityDialog() = authDelegate.showAccessibilityDialog()
    fun dismissAccessibilityDialog() = authDelegate.dismissAccessibilityDialog()
    val showAccessibilityDialog: StateFlow<Boolean> = authDelegate.showAccessibilityDialog

    fun loadProjects(context: Context) = projectWorkspaceDelegate.loadProjects(context)
    fun saveProjects(context: Context, list: List<File>) = projectWorkspaceDelegate.saveProjects(context, list)
    fun addProject(context: Context, path: String): File = projectWorkspaceDelegate.addProject(context, path, ::addLog)
    fun removeProject(context: Context, path: String) = projectWorkspaceDelegate.removeProject(context, path, ::addLog)
    fun resolveTreeUriToFolder(context: Context, treeUri: Uri): File? = projectWorkspaceDelegate.resolveTreeUriToFolder(context, treeUri)
    fun addProjectFromTreeUri(context: Context, treeUri: Uri): File? = projectWorkspaceDelegate.addProjectFromTreeUri(context, treeUri, ::addLog)
    fun setProjectSortOrder(context: Context, order: ProjectSortOrder) = projectWorkspaceDelegate.setProjectSortOrder(context, order)
    fun createProjectInFolder(context: Context, name: String, parentDir: File): File? = projectWorkspaceDelegate.createProjectInFolder(context, name, parentDir, ::addLog)
    fun createProjectFolder(context: Context, name: String, targetBase: File? = null): File? = projectWorkspaceDelegate.createProjectFolder(context, name, targetBase, ::addLog)
    fun setWorkspacePath(context: Context, path: String) = projectWorkspaceDelegate.setWorkspacePath(context, path, ::addLog)

    fun saveConversationHistory(context: Context) = conversationManagerDelegate.saveConversationHistory(context)
    fun saveLastActiveConversationId(context: Context, id: String?) = conversationManagerDelegate.saveLastActiveConversationId(context, id)
    fun saveMessagesForConversation(context: Context, conversationId: String, messages: List<ChatMessage>) =
        conversationManagerDelegate.saveMessagesForConversation(context, conversationId, messages, viewModelScope)
    fun loadMessagesForConversation(context: Context, conversationId: String): List<ChatMessage> =
        conversationManagerDelegate.loadMessagesForConversation(context, conversationId)
    fun loadConversationHistory(context: Context) =
        conversationManagerDelegate.loadConversationHistory(
            context = context,
            onWorkspaceChange = { setWorkspacePath(context, it) },
            onMessagesLoaded = { mutableChatMessages.value = it }
        )
    fun startNewConversation(context: Context? = null, projectPath: String? = workspacePath.value) =
        conversationManagerDelegate.startNewConversation(
            context = context,
            projectPath = projectPath,
            currentMessages = mutableChatMessages.value,
            scope = viewModelScope,
            onWorkspaceChange = { if (context != null) setWorkspacePath(context, it) else projectWorkspaceDelegate.setWorkspacePathDirect(it) },
            onClearState = {
                mutableChatMessages.value = emptyList()
                mutableAttachedFiles.value = emptyList()
            }
        )
    fun loadConversation(context: Context, summary: ConversationSummary) =
        conversationManagerDelegate.loadConversation(
            context = context,
            summary = summary,
            currentMessages = mutableChatMessages.value,
            scope = viewModelScope,
            onWorkspaceChange = { setWorkspacePath(context, it) },
            onMessagesLoaded = { mutableChatMessages.value = it },
            onLog = ::addLog
        )
    fun loadConversation(summary: ConversationSummary) =
        conversationManagerDelegate.loadConversation(
            summary = summary,
            onWorkspaceChange = { projectWorkspaceDelegate.setWorkspacePathDirect(it) },
            onLog = ::addLog
        )
    fun renameConversation(newTitle: String, summaryId: String? = null, context: Context? = null) =
        conversationManagerDelegate.renameConversation(
            newTitle = newTitle,
            summaryId = summaryId,
            context = context,
            currentMessageCount = mutableChatMessages.value.size,
            currentWorkspacePath = workspacePath.value,
            onLog = ::addLog
        )
    fun togglePinConversation(summaryId: String? = null, context: Context? = null): Boolean =
        conversationManagerDelegate.togglePinConversation(summaryId, context, ::addLog)
    fun deleteConversation(id: String) =
        conversationManagerDelegate.deleteConversation(
            context = null,
            id = id,
            onNextSummaryLoaded = { loadConversation(it) },
            onCleared = {
                mutableChatMessages.value = emptyList()
                mutableAttachedFiles.value = emptyList()
            },
            onLog = ::addLog
        )
    fun deleteConversation(context: Context, id: String) =
        conversationManagerDelegate.deleteConversation(
            context = context,
            id = id,
            onNextSummaryLoaded = { loadConversation(context, it) },
            onCleared = {
                mutableChatMessages.value = emptyList()
                mutableAttachedFiles.value = emptyList()
            },
            onLog = ::addLog
        )
    fun deleteCurrentConversation(context: Context) =
        conversationManagerDelegate.deleteCurrentConversation(
            context = context,
            onNextSummaryLoaded = { loadConversation(context, it) },
            onCleared = {
                mutableChatMessages.value = emptyList()
                mutableAttachedFiles.value = emptyList()
            },
            onLog = ::addLog
        )
    fun archiveConversation(context: Context? = null) =
        conversationManagerDelegate.archiveConversation(context, mutableChatMessages.value, workspacePath.value)

    fun clearRepoInspection() = customizationInstallerDelegate.clearRepoInspection()
    fun toggleRepoSkillSelection(skillId: String) = customizationInstallerDelegate.toggleRepoSkillSelection(skillId)
    fun setAllRepoSkillsSelection(selectAll: Boolean) = customizationInstallerDelegate.setAllRepoSkillsSelection(selectAll)
    fun normalizeGitRepoUrl(rawUrl: String): Pair<String, String?> = customizationInstallerDelegate.normalizeGitRepoUrl(rawUrl)
    fun inspectRepoForSkills(context: Context, rawRepoUrl: String) = customizationInstallerDelegate.inspectRepoForSkills(context, rawRepoUrl, viewModelScope)
    fun installSkillViaTerminal(context: Context, skillItem: SkillCatalogueItem) =
        customizationInstallerDelegate.installSkillViaTerminal(context, skillItem, getBuiltinSkillsDir(context), viewModelScope, ::fetchLatestRepoCommitSha, ::registerTrackedCustomization, ::refreshSkills)
    fun installPluginViaTerminal(context: Context, pluginItem: SkillCatalogueItem) =
        customizationInstallerDelegate.installPluginViaTerminal(context, pluginItem, viewModelScope, ::fetchLatestRepoCommitSha, ::registerTrackedCustomization, ::refreshPlugins)
    fun updateCustomizationViaTerminal(context: Context, track: CustomizationRepoTrack) =
        customizationInstallerDelegate.updateCustomizationViaTerminal(context, track, viewModelScope, ::getTrackedCustomizations, ::saveTrackedCustomizations, ::refreshSkills, ::refreshPlugins)
    fun installCustomFromRepoViaTerminal(context: Context, rawRepoUrl: String, customName: String, subPath: String?, isPlugin: Boolean) =
        customizationInstallerDelegate.installCustomFromRepoViaTerminal(context, rawRepoUrl, customName, subPath, isPlugin, viewModelScope, ::fetchLatestRepoCommitSha, ::registerTrackedCustomization, ::refreshSkills, ::refreshPlugins)
    fun installSelectedRepoSkillsViaTerminal(context: Context, rawRepoUrl: String, selectedSkills: List<DiscoveredRepoSkill>) =
        customizationInstallerDelegate.installSelectedRepoSkillsViaTerminal(context, rawRepoUrl, selectedSkills, viewModelScope, ::fetchLatestRepoCommitSha, ::registerTrackedCustomization, ::refreshSkills)
    fun dismissInstallProgress() = customizationInstallerDelegate.dismissInstallProgress()

    fun getTrackedCustomizations(context: Context): Map<String, CustomizationRepoTrack> = customizationCatalogueDelegate.getTrackedCustomizations(context)
    fun saveTrackedCustomizations(context: Context, tracks: Map<String, CustomizationRepoTrack>) = customizationCatalogueDelegate.saveTrackedCustomizations(context, tracks)
    fun registerTrackedCustomization(context: Context, track: CustomizationRepoTrack) = customizationCatalogueDelegate.registerTrackedCustomization(context, track)
    fun unregisterTrackedCustomization(context: Context, id: String) = customizationCatalogueDelegate.unregisterTrackedCustomization(context, id)
    fun loadTrackedCustomizations(context: Context) = customizationCatalogueDelegate.loadTrackedCustomizations(context)
    fun fetchLatestRepoCommitSha(repoUrl: String, subPath: String? = null): String? = customizationCatalogueDelegate.fetchLatestRepoCommitSha(repoUrl, subPath)
    fun seedCatalogueTrackingIfInstalled(context: Context) = customizationCatalogueDelegate.seedCatalogueTrackingIfInstalled(context)
    fun checkForCustomizationUpdates(context: Context, force: Boolean = false) = customizationCatalogueDelegate.checkForCustomizationUpdates(context, viewModelScope, force)
    fun getGeminiConfigDir(context: Context): File = customizationCatalogueDelegate.getGeminiConfigDir(context)
    fun syncConfigBidirectional(context: Context) = customizationCatalogueDelegate.syncConfigBidirectional(context)
    fun syncConfigToRoot(context: Context) = customizationCatalogueDelegate.syncConfigToRoot(context)
    fun getBuiltinSkillsDir(context: Context): File = customizationCatalogueDelegate.getBuiltinSkillsDir(context)
    fun getInstalledSkills(context: Context): List<CustomizationItem> = customizationCatalogueDelegate.getInstalledSkills(context, workspacePath.value)
    fun refreshSkills(context: Context) = customizationCatalogueDelegate.refreshSkills(context, viewModelScope, workspacePath.value)
    fun installSkill(context: Context, name: String, description: String, content: String): Boolean = customizationCatalogueDelegate.installSkill(context, name, description, content, viewModelScope, ::addLog)
    fun uninstallSkill(context: Context, name: String): Boolean = customizationCatalogueDelegate.uninstallSkill(context, name, viewModelScope, ::addLog)
    fun getInstalledPlugins(context: Context): List<CustomizationItem> = customizationCatalogueDelegate.getInstalledPlugins(context)
    fun refreshPlugins(context: Context) = customizationCatalogueDelegate.refreshPlugins(context, viewModelScope)
    fun installPlugin(context: Context, name: String, description: String): Boolean = customizationCatalogueDelegate.installPlugin(context, name, description, viewModelScope, ::addLog)
    fun uninstallPlugin(context: Context, name: String): Boolean = customizationCatalogueDelegate.uninstallPlugin(context, name, viewModelScope, ::addLog)
    fun getAvailableSkillsCatalogue(): List<SkillCatalogueItem> = customizationCatalogueDelegate.getAvailableSkillsCatalogue()
    fun getDefaultSkillsCatalogue(): List<SkillCatalogueItem> = customizationCatalogueDelegate.getDefaultSkillsCatalogue()
    fun getAvailablePluginsCatalogue(): List<SkillCatalogueItem> = customizationCatalogueDelegate.getAvailablePluginsCatalogue()
    fun getDefaultPluginsCatalogue(): List<SkillCatalogueItem> = customizationCatalogueDelegate.getDefaultPluginsCatalogue()
    fun loadCachedCatalogue(context: Context) = customizationCatalogueDelegate.loadCachedCatalogue(context, viewModelScope)
    fun fetchRemoteCatalogue(context: Context, force: Boolean = false) = customizationCatalogueDelegate.fetchRemoteCatalogue(context, viewModelScope, force, ::addLog)
    fun getInstalledRules(context: Context): List<Pair<String, String>> = customizationCatalogueDelegate.getInstalledRules(context)
    fun installPluginFromCatalogueAsync(context: Context, pluginId: String, onComplete: (Boolean) -> Unit) = customizationCatalogueDelegate.installPluginFromCatalogueAsync(context, pluginId, viewModelScope, ::addLog, onComplete)
    fun installSkillFromCatalogueAsync(context: Context, skillId: String, onComplete: (Boolean) -> Unit) = customizationCatalogueDelegate.installSkillFromCatalogueAsync(context, skillId, viewModelScope, ::addLog, onComplete)
    fun installSkillFromCatalogue(context: Context, skillId: String): Boolean {
        installSkillFromCatalogueAsync(context, skillId) {}
        return true
    }
    fun installPluginFromCatalogue(context: Context, pluginId: String): Boolean {
        installPluginFromCatalogueAsync(context, pluginId) {}
        return true
    }
    fun calculateTokenUsage(context: Context): TokenUsageStats = customizationCatalogueDelegate.calculateTokenUsage(context)
    private val mutableOpenTerminalRequest = MutableStateFlow<String?>(null)
    val openTerminalRequest: StateFlow<String?> = mutableOpenTerminalRequest.asStateFlow()

    fun requestOpenTerminal(cmd: String? = null) {
        mutableOpenTerminalRequest.value = cmd ?: ""
    }

    fun consumeOpenTerminalRequest() {
        mutableOpenTerminalRequest.value = null
    }



    fun bootEngine(context: Context, onReady: () -> Unit) {
        viewModelScope.launch {
            mutableIsBooting.value = true
            mutableBootError.value = null
            mutableSetupProgress.value = 0.05f
            loadPreferences(context)

            mutableBootStatusText.value = "Checking Linux userspace bootstrap..."
            addLog("Boot", "Checking Linux userspace installation...")

            val installOk = BootstrapInstaller.installIfNeeded(context) { step, progress ->
                mutableBootStatusText.value = step
                mutableSetupProgress.value = progress
                addLog("Setup", step)
            }

            if (!installOk) {
                val detail = BootstrapInstaller.lastStackTrace
                    ?: BootstrapInstaller.lastError
                    ?: "Failed to extract bootstrap. Please ensure device internal storage is sufficient."
                mutableBootError.value = BootstrapInstaller.lastError ?: "Failed to extract Linux bootstrap & engine."
                mutableBootErrorDetail.value = detail
                addLog("Boot", "Bootstrap installation failed: $detail", isError = true)
                mutableIsBooting.value = false
                return@launch
            }

            val engineProblem = BootstrapInstaller.engineProblem(context)
            if (engineProblem != null) {
                val detail = "Core engine is not usable: $engineProblem"
                mutableBootError.value = detail
                mutableBootErrorDetail.value =
                    "The agy binary could not be installed. Check your internet connection and tap Retry. " +
                        "If it keeps failing, reinstall the app."
                addLog("Boot", detail, isError = true)
                mutableIsBooting.value = false
                return@launch
            }

            mutableSetupProgress.value = 0.90f
            mutableBootStatusText.value = "Starting Antigravity Core Service..."
            addLog("Boot", "Launching CoreServerService (port 45158 & 45161)...")
            startCoreService(context)

            delay(1000)
            mutableSetupProgress.value = 1.0f
            mutableBootStatusText.value = "Antigravity ready"
            addLog("Boot", "Antigravity ready")
            mutableIsBooting.value = false
            onReady()
        }
    }

    fun startCoreService(context: Context) {
        CoreServerService.startService(context)
        mutableEngineStatus.update { it.copy(isRunning = true) }
        addLog("Engine", "Core Server started")
    }

    fun stopCoreService(context: Context) {
        CoreServerService.stopService(context)
        mutableEngineStatus.update { it.copy(isRunning = false) }
        addLog("Engine", "Core Server stopped")
    }

    fun checkForCoreEngineUpdate(context: Context, force: Boolean = false) {
        viewModelScope.launch {
            addLog("Engine", "Checking for core engine update (force=$force)...")
            var installed = ""
            val stderr = StringBuilder()
            EngineExecutor.executeCommand(
                context = context,
                command = "command -v agy || command -v language_server || true; " +
                    "(agy --version 2>/dev/null || language_server --version 2>/dev/null || true)"
            ) { line, isErr ->
                if (isErr) stderr.appendLine(line) else if (installed.isBlank()) installed = line.trim()
            }
            if (installed.isBlank()) {
                addLog("Engine", "Core engine is not installed yet. Reinstall the bootstrap to continue.", isError = true)
            } else {
                addLog("Engine", "Core engine ready: $installed")
            }
        }
    }

    fun applyCoreEngineUpdate(context: Context) {
        viewModelScope.launch {
            addLog("Engine", "Applying core engine update...")
        }
        restartCoreService(context)
    }

    fun restartCoreService(context: Context) {
        viewModelScope.launch {
            addLog("Engine", "Restarting Core Server...")
            stopCoreService(context)
            delay(500)
            startCoreService(context)
        }
    }

    fun addLog(tag: String, message: String, isError: Boolean = false) {
        AppLogger.log(tag, message, isError)
    }

    fun clearLogs() {
        mutableExecutionLogs.value = emptyList()
    }

    fun copyLogsToClipboard(context: Context) {
        val logs = mutableExecutionLogs.value
        val logText = if (logs.isEmpty()) {
            context.getString(R.string.settings_logs_empty)
        } else {
            logs.joinToString("\n") { "[${it.formattedTime}] [${it.tag}] ${it.message}" }
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Terminal Logs", logText)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, context.getString(R.string.splash_logs_copied), Toast.LENGTH_SHORT).show()
    }

    fun exportLogsToFile(context: Context) {
        viewModelScope.launch {
            try {
                val logs = mutableExecutionLogs.value
                val logDir = File(context.filesDir, "logs").apply { mkdirs() }
                val logFile = File(logDir, "execution_${System.currentTimeMillis()}.log")
                val logText = logs.joinToString("\n") { "[${it.formattedTime}] [${it.tag}] ${it.message}" }
                logFile.writeText(logText, Charsets.UTF_8)
                Toast.makeText(context, "Logs exported: ${logFile.name}", Toast.LENGTH_SHORT).show()
                addLog("Log", "Exported logs to ${logFile.absolutePath}")
            } catch (e: Exception) {
                addLog("Log", "Failed to export logs: ${e.message}", isError = true)
            }
        }
    }

    fun addAttachment(context: Context, uri: Uri): Boolean {
        if (mutableAttachedFiles.value.size >= 5) {
            return false
        }
        try {
            var name = "file_${System.currentTimeMillis()}"
            var size = 0L
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex != -1) name = it.getString(nameIndex)
                    if (sizeIndex != -1) size = it.getLong(sizeIndex)
                }
            }
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

            val uploadsDir = File(context.filesDir, "uploads").apply { mkdirs() }
            uploadsDir.setReadable(true, false)
            uploadsDir.setExecutable(true, false)
            try { android.system.Os.chmod(uploadsDir.absolutePath, 493) } catch (e: Exception) {}

            val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val targetFile = File(uploadsDir, "${System.currentTimeMillis()}_$safeName")
            context.contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output -> input.copyTo(output) }
            }
            targetFile.setReadable(true, false)
            try { android.system.Os.chmod(targetFile.absolutePath, 420) } catch (e: Exception) {}

            val file = AttachedFile(
                id = UUID.randomUUID().toString(),
                uri = uri,
                name = name,
                sizeBytes = if (size > 0L) size else targetFile.length(),
                mimeType = mimeType,
                localPath = targetFile.absolutePath
            )
            mutableAttachedFiles.update { if (it.size < 5) it + file else it }
            addLog("Chat", "Attached file: $name (${file.formattedSize})")
            return true
        } catch (e: Exception) {
            addLog("Chat", "Error attaching file: ${e.message}", isError = true)
            return false
        }
    }

    fun removeAttachment(file: AttachedFile) {
        file.localPath?.let { path ->
            runCatching {
                val f = File(path)
                if (f.exists() && f.parentFile?.name == "uploads") {
                    f.delete()
                }
            }
        }
        mutableAttachedFiles.update { it.filter { item -> item.id != file.id } }
    }

    fun showModelSelector() {
        mutableShowModelSelector.value = true
    }

    val showApiKeyDialog: StateFlow<Boolean> = authDelegate.showApiKeyDialog

    /** Opens the API-key dialog from any screen (used by the settings drawer). */
    fun showApiKeyDialogExternal() {
        authDelegate.showApiKeyDialog()
    }

    fun dismissApiKeyDialog() {
        authDelegate.dismissApiKeyDialog()
    }

    fun dismissModelSelector() {
        mutableShowModelSelector.value = false
    }

    fun queueMessage(content: String, attachments: List<AttachedFile> = emptyList()) {
        if (content.isBlank() && attachments.isEmpty()) return
        val item = QueuedMessage(
            content = content.trim(),
            attachments = attachments
        )
        mutableQueuedMessages.update { it + item }
    }

    fun addRunningTask(name: String, command: String): RunningTask {
        val task = RunningTask(name = name, command = command)
        mutableRunningTasks.update { it + task }
        return task
    }

    fun removeRunningTask(id: String) {
        mutableRunningTasks.update { list -> list.filter { it.id != id } }
    }

    fun stopRunningTask(id: String) {
        removeRunningTask(id)
        if (mutableRunningTasks.value.isEmpty()) {
            stopAiExecution()
        }
    }

    fun addRunningSubagent(subagent: RunningSubagent) {
        mutableRunningSubagents.update { list ->
            val idx = list.indexOfFirst { it.id == subagent.id || (it.conversationId != null && it.conversationId == subagent.conversationId) }
            if (idx >= 0) {
                list.toMutableList().apply { set(idx, subagent) }
            } else {
                list + subagent
            }
        }
    }

    fun completeSubagent(convIdOrId: String) {
        mutableRunningSubagents.update { list ->
            list.map { if (it.conversationId == convIdOrId || it.id == convIdOrId) it.copy(isRunning = false) else it }
        }
    }

    fun removeRunningSubagent(id: String) {
        mutableRunningSubagents.update { list -> list.filter { it.id != id && it.conversationId != id } }
    }

    fun stopSubagent(id: String) {
        removeRunningSubagent(id)
    }

    fun loadSubagentMessages(context: Context, convId: String?): List<ChatMessage> {
        if (convId.isNullOrBlank()) return emptyList()
        val persisted = ConversationPersistence.loadMessagesForConversation(context, convId)
        if (persisted.isNotEmpty()) return persisted
        return TranscriptTurnParser.parseTranscriptToMessages(context, convId)
    }

    fun openSubagentAsActiveConversation(context: Context, subagent: RunningSubagent) {
        val convId = subagent.conversationId ?: subagent.id
        val title = subagent.role.ifEmpty { subagent.typeName.ifEmpty { context.getString(R.string.subagent_default_title) } }
        val summary = ConversationSummary(
            id = convId,
            title = title,
            timestamp = System.currentTimeMillis(),
            messageCount = 1,
            isPinned = false,
            projectPath = workspacePath.value
        )
        val history = ConversationPersistence.loadConversationHistory(context)
        if (history.none { it.id == convId }) {
            ConversationPersistence.saveConversationHistory(context, listOf(summary) + history)
        }
        loadConversation(context, summary)
    }

    fun loadDaemonTasks(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val tasks = mutableListOf<DaemonTask>()
            val candidates = listOf(
                File(context.filesDir, ".gemini/antigravity-app/daemon/tasks.json"),
                File(context.filesDir, "home/.gemini/antigravity-app/daemon/tasks.json")
            )
            for (file in candidates) {
                if (file.exists() && file.isFile) {
                    try {
                        val text = file.readText()
                        val array = org.json.JSONArray(text)
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            tasks.add(
                                DaemonTask(
                                    id = obj.optString("id", "task_$i"),
                                    name = obj.optString("name", obj.optString("id", "Task")),
                                    command = obj.optString("command", obj.optString("prompt", "")),
                                    triggerType = obj.optString("trigger_type", "interval"),
                                    intervalSec = obj.optInt("interval", 0),
                                    cron = obj.optString("cron", ""),
                                    enabled = obj.optBoolean("enabled", true),
                                    lastStatus = obj.optString("last_status", "idle")
                                )
                            )
                        }
                        break
                    } catch (e: Exception) {

                    }
                }
            }
            mutableDaemonTasks.value = tasks
        }
    }

    fun removeQueuedMessage(id: String) {
        mutableQueuedMessages.update { list -> list.filter { it.id != id } }
    }

    fun editQueuedMessage(id: String): QueuedMessage? {
        val item = mutableQueuedMessages.value.find { it.id == id } ?: return null
        removeQueuedMessage(id)
        return item
    }

    fun processQueuedMessageNow(context: Context, id: String) {
        val item = mutableQueuedMessages.value.find { it.id == id } ?: return
        removeQueuedMessage(id)
        if (mutableIsExecuting.value) {
            stopAiExecution()
        }
        sendMessage(context, item.content, item.attachments)
    }

    fun processNextQueuedMessage(context: Context) {
        if (mutableIsExecuting.value) return
        val next = mutableQueuedMessages.value.firstOrNull() ?: return
        removeQueuedMessage(next.id)
        sendMessage(context, next.content, next.attachments)
    }

    fun getWorkspaceFiles(query: String = ""): List<String> {
        val rootPath = workspacePath.value
        val rootDir = if (!rootPath.isNullOrEmpty()) File(rootPath) else null
        val result = mutableListOf<String>()

        if (rootDir != null && rootDir.exists() && rootDir.isDirectory) {
            try {
                fun scan(dir: File, depth: Int) {
                    if (depth > 4 || result.size > 50) return
                    dir.listFiles()?.forEach { file ->
                        if (file.name.startsWith(".") || file.name == "build" || file.name == "node_modules" || file.name == ".git") return@forEach
                        val relPath = file.relativeTo(rootDir).path
                        if (query.isBlank() || relPath.contains(query, ignoreCase = true) || file.name.contains(query, ignoreCase = true)) {
                            result.add(relPath)
                        }
                        if (file.isDirectory) scan(file, depth + 1)
                    }
                }
                scan(rootDir, 0)
            } catch (e: Exception) {}
        }
        if (result.isEmpty()) {
            listOf("README.md", "build.gradle.kts", "AndroidManifest.xml", "settings.gradle.kts", "package.json").forEach {
                if (query.isBlank() || it.contains(query, ignoreCase = true)) result.add(it)
            }
        }
        return result.take(15)
    }

    private val mutableSlashActions = MutableStateFlow<List<SlashAction>>(SYSTEM_SLASH_COMMANDS)
    val slashActions: StateFlow<List<SlashAction>> = mutableSlashActions.asStateFlow()
    private var slashRefreshJob: Job? = null

    fun refreshCliSlashCommands(context: Context) {
        if (slashRefreshJob?.isActive == true) return
        slashRefreshJob = viewModelScope.launch(Dispatchers.IO) {
            try {

                val skills = getInstalledSkills(context).map {
                    SlashAction(
                        cmd = it.name.lowercase().trimStart('/'),
                        descText = it.description.ifBlank { null },
                        category = if (it.isBuiltIn) "Built-in Skill" else "Skill"
                    )
                }

                val workflows = getDiscoveredWorkflows(context)

                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val cached = loadCachedSlashCommands(prefs)
                val cachedVersion = prefs.getString(KEY_CLI_SLASH_VERSION, null)

                var cliCommands = cached
                if (!EngineExecutor.isAgyRunning() && !mutableIsExecuting.value) {
                    val version = runAgyCapture(context, listOf("--version"), 10_000L)
                        ?.second?.lines()?.firstOrNull { it.isNotBlank() }?.trim()

                    if (version != null && (version != cachedVersion || cached.isEmpty())) {
                        val help = runAgyCapture(context, listOf("--help"), 15_000L)
                        val parsed = help?.second?.let { SlashCommandParser.parse(it) } ?: emptyList()
                        if (parsed.isNotEmpty()) {
                            saveCachedSlashCommands(prefs, parsed, version)
                            cliCommands = parsed
                        }
                    }
                }

                val allActions = mergeDynamicSlashActions(
                    systemCommands = SYSTEM_SLASH_COMMANDS,
                    cliCommands = cliCommands,
                    skills = skills,
                    workflows = workflows
                )

                mutableSlashActions.value = allActions
                addLog("Slash", "Loaded dynamic slash menu (${allActions.size} actions: ${skills.size} skills, ${workflows.size} workflows, ${cliCommands.size} CLI)")
            } catch (e: Exception) {
                try {
                    val fallbackSkills = getInstalledSkills(context).map {
                        SlashAction(it.name.lowercase().trimStart('/'), it.description.ifBlank { null }, "Skill")
                    }
                    mutableSlashActions.value = mergeDynamicSlashActions(
                        systemCommands = SYSTEM_SLASH_COMMANDS,
                        cliCommands = emptyList(),
                        skills = fallbackSkills,
                        workflows = emptyList()
                    )
                } catch (e: Exception) {}
                addLog("Slash", "Failed to load slash menu: ${e.message}", isError = true)
            }
        }
    }

    private fun getDiscoveredWorkflows(context: Context): List<SlashAction> {
        val list = mutableListOf<SlashAction>()
        val seen = mutableSetOf<String>()
        val configDir = getGeminiConfigDir(context)
        val dirs = mutableListOf(
            File(configDir, "workflows"),
            File(context.filesDir, ".gemini/config/workflows"),
            File(context.filesDir, "home/.gemini/config/workflows")
        )
        val wsPath = workspacePath.value
        if (!wsPath.isNullOrBlank()) {
            val wsDir = File(wsPath)
            dirs.add(File(wsDir, ".gemini/workflows"))
            dirs.add(File(wsDir, ".agents/workflows"))
            dirs.add(File(wsDir, ".agent/workflows"))
            dirs.add(File(wsDir, "workflows"))
        }
        for (dir in dirs) {
            if (dir.isDirectory) {
                dir.listFiles()?.filter { it.isFile && (it.extension == "md" || it.extension == "prompt" || it.extension == "yaml") }?.forEach { f ->
                    val name = f.nameWithoutExtension.lowercase()
                    if (!seen.contains(name)) {
                        seen.add(name)
                        var desc = "Workflow from ${f.name}"
                        try {
                            val firstLine = f.readLines().firstOrNull { it.isNotBlank() && !it.startsWith("---") && !it.startsWith("#") }
                            if (!firstLine.isNullOrBlank()) desc = firstLine.trim()
                        } catch (e: Exception) {}
                        list.add(SlashAction(name, desc, "Workflow"))
                    }
                }
            }
        }
        return list
    }

    private suspend fun runAgyCapture(
        context: Context,
        args: List<String>,
        timeoutMs: Long,
    ): Pair<Int, String>? {
        val sb = StringBuilder()
        val wsDir = run {
            val currentWorkspace = workspacePath.value
            val dir = if (!currentWorkspace.isNullOrBlank()) File(currentWorkspace) else null
            if (dir != null && dir.exists() && dir.isDirectory) dir else null
        }
        val exit = withTimeoutOrNull(timeoutMs) {
            EngineExecutor.executeAgy(context, args, workingDir = wsDir) { line, isErr ->
                sb.appendLine(line)
            }
        } ?: return null
        if (exit != 0) return null
        return exit to sb.toString()
    }

    private fun loadCachedSlashCommands(
        prefs: SharedPreferences,
    ): List<CliSlashCommand> {
        val raw = prefs.getString(KEY_CLI_SLASH_COMMANDS, null) ?: return emptyList()
        val out = ArrayList<CliSlashCommand>()
        for (line in raw.lines()) {
            if (line.isBlank()) continue
            val tab = line.indexOf('\t')
            if (tab <= 0) continue
            out.add(CliSlashCommand(line.substring(0, tab), line.substring(tab + 1)))
        }
        return out
    }

    private fun saveCachedSlashCommands(
        prefs: SharedPreferences,
        commands: List<CliSlashCommand>,
        version: String?,
    ) {
        val text = commands.joinToString("\n") { cmd ->
            val desc = cmd.description.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
            "${cmd.cmd}\t$desc"
        }
        prefs.edit()
            .putString(KEY_CLI_SLASH_COMMANDS, text)
            .putString(KEY_CLI_SLASH_VERSION, version)
            .putLong(KEY_CLI_SLASH_FETCHED_AT, System.currentTimeMillis())
            .apply()
    }

    fun sendMessage(context: Context, content: String, attachments: List<AttachedFile> = emptyList()) {
        val currentAttachments = if (attachments.isNotEmpty()) attachments else mutableAttachedFiles.value
        if (content.isBlank() && currentAttachments.isEmpty()) return

        if (content.trim().equals("/clear", ignoreCase = true)) {
            val convId = activeConversationId.value
            mutableChatMessages.value = emptyList()
            if (convId != null) {
                saveMessagesForConversation(context, convId, emptyList())
                conversationManagerDelegate.updateConversationHistoryDirect { list ->
                    list.map { if (it.id == convId) it.copy(messageCount = 0) else it }
                }
                saveConversationHistory(context)
            }
            return
        }

        if (mutableIsExecuting.value) {
            if (queuedMessagesMode.value.equals("Send Immediately", ignoreCase = true)) {
                stopAiExecution()
            } else {
                queueMessage(content, currentAttachments)
                mutableAttachedFiles.value = emptyList()
                return
            }
        }

        var convId = activeConversationId.value
        val isFirstMessage = (convId == null || mutableChatMessages.value.isEmpty())
        if (convId == null) {
            convId = UUID.randomUUID().toString()
            conversationManagerDelegate.setActiveConversationIdDirect(convId)
        }

        if (conversationTitle.value == "New Conversation" || conversationTitle.value.isBlank()) {
            val titleCandidate = content.lines().firstOrNull { it.isNotBlank() }?.trim()?.take(35)
                ?: currentAttachments.firstOrNull()?.name?.take(35)
            if (!titleCandidate.isNullOrEmpty()) {
                conversationManagerDelegate.setConversationTitleDirect(titleCandidate)
            }
        }

        val effectiveContent = if (content.isBlank() && currentAttachments.isNotEmpty()) {
            context.getString(R.string.chat_attached_files_prompt)
        } else {
            content
        }

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.USER,
            content = effectiveContent,
            attachments = currentAttachments
        )
        val updatedMessages = mutableChatMessages.value + userMessage
        mutableChatMessages.value = updatedMessages
        mutableAttachedFiles.value = emptyList()

        val currentSummary = ConversationSummary(
            id = convId,
            title = conversationTitle.value,
            timestamp = System.currentTimeMillis(),
            messageCount = updatedMessages.size,
            isPinned = isConversationPinned.value,
            projectPath = workspacePath.value
        )
        conversationManagerDelegate.updateConversationHistoryDirect { list ->
            listOf(currentSummary) + list.filter { it.id != convId }
        }
        saveConversationHistory(context)
        saveMessagesForConversation(context, convId, updatedMessages)
        saveLastActiveConversationId(context, convId)

        val isShellCommand = content.startsWith("!") || content.startsWith("$ ")
        addLog("Chat", "User prompt: ${effectiveContent.take(150)}")
        if (!isShellCommand && !GeminiClient.hasApiKey(context)) {
            authDelegate.showApiKeyDialog()
            val assistantMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                role = MessageRole.ASSISTANT,
                content = "Add your Gemini API key in Settings to start using the agent.",
                status = MessageStatus.SENT,
                thinking = null
            )
            val finalMessages = updatedMessages + assistantMsg
            mutableChatMessages.value = finalMessages
            conversationManagerDelegate.updateConversationHistoryDirect { list ->
                list.map { if (it.id == convId) it.copy(messageCount = finalMessages.size) else it }
            }
            saveConversationHistory(context)
            saveMessagesForConversation(context, convId, finalMessages)
            return
        }

        executeAiTurn(context, effectiveContent, convId, currentAttachments)
    }

    /**
     * Appends a message pair to the current conversation without touching the engine.
     * Used by the floating bubble and the screen agent so their results appear in the
     * normal chat transcript, exactly like a typed message would.
     */
    fun appendConversationMessages(
        context: Context,
        userText: String,
        assistantText: String
    ) {
        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.USER,
            content = userText,
            status = MessageStatus.SENT,
            thinking = null
        )
        val assistantMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.ASSISTANT,
            content = assistantText,
            status = MessageStatus.SENT,
            thinking = null
        )
        val updated = mutableChatMessages.value + userMsg + assistantMsg
        mutableChatMessages.value = updated
        try {
            var convId = activeConversationId.value
            if (convId == null) {
                convId = UUID.randomUUID().toString()
                conversationManagerDelegate.setActiveConversationIdDirect(convId)
            }
            saveConversationHistory(context)
            saveMessagesForConversation(context, convId, updated)
            conversationManagerDelegate.updateConversationHistoryDirect { list ->
                list.map { if (it.id == convId) it.copy(messageCount = updated.size) else it }
            }
            saveConversationHistory(context)
        } catch (e: Exception) {
            addLog("Chat", "Could not persist agent message: ${e.message}")
        }
    }

    private val executionWakeLock = WakeLockHolder()
    private var currentAiExecutionJob: Job? = null

    fun stopAiExecution() {
        executionWakeLock.release()
        addLog("Chat", "AI generation stopped by user")
        currentAiExecutionJob?.cancel()
        currentAiExecutionJob = null
        EngineExecutor.stopAgyProcess()
        mutableIsExecuting.value = false
        mutableRunningTasks.value = emptyList()
        mutableChatMessages.update { list ->
            list.map { msg ->
                if (msg.status == MessageStatus.SENDING) {
                    msg.copy(
                        status = MessageStatus.SENT,
                        content = if (msg.content.isBlank()) "[Generation stopped by user]" else "${msg.content}\n\n*[Stopped by user]*",
                        thinking = null
                    )
                } else msg
            }
        }
    }

    fun undoPrompt(context: Context, userMessage: ChatMessage) {
        if (mutableIsExecuting.value) {
            stopAiExecution()
        }
        val promptText = userMessage.content
        mutablePendingInputText.value = promptText

        val current = mutableChatMessages.value.toMutableList()
        val userIdx = current.indexOfFirst { it.id == userMessage.id }
        if (userIdx != -1) {
            var endIdx = userIdx + 1
            while (endIdx < current.size && current[endIdx].role != MessageRole.USER) {
                endIdx++
            }
            current.subList(userIdx, endIdx).clear()
            mutableChatMessages.value = current
            val activeId = activeConversationId.value
            if (activeId != null) {
                saveMessagesForConversation(context, activeId, current)
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            val root = File(workspacePath.value)
            if (root.exists() && File(root, ".git").exists()) {
                EngineExecutor.executeCommand(
                    context,
                    "cd '${root.absolutePath}' && (git restore --staged . 2>/dev/null || true) && (git restore . 2>/dev/null || git checkout -- . 2>/dev/null || true)"
                ) { _, _ -> }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, context.getString(R.string.chat_toast_prompt_undone), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun executeAiTurn(
        context: Context,
        prompt: String,
        convId: String,
        attachments: List<AttachedFile> = emptyList()
    ) {
        currentAiExecutionJob?.cancel()
        currentAiExecutionJob = viewModelScope.launch(Dispatchers.IO) {
            executionWakeLock.acquire(context, 30 * 60 * 1000L)
            mutableIsExecuting.value = true
            mutableActiveWorkingStatus.value = "Working"
            val turnStartTime = System.currentTimeMillis()

            val assistantMsgId = UUID.randomUUID().toString()
            val initialAssistantMsg = ChatMessage(
                id = assistantMsgId,
                role = MessageRole.ASSISTANT,
                content = "",
                status = MessageStatus.SENDING,
                thinking = null
            )
            mutableChatMessages.update { it + initialAssistantMsg }

            val currentWorkspace = workspacePath.value
            val wsDir = if (!currentWorkspace.isNullOrBlank()) File(currentWorkspace) else null
            val validWsDir = if (wsDir != null && wsDir.exists() && wsDir.isDirectory) wsDir else null

            try {

                if (prompt.startsWith("!") || prompt.startsWith("$ ")) {
                    val cmd = if (prompt.startsWith("!")) prompt.substring(1).trim() else prompt.substring(2).trim()
                    mutableChatMessages.update { list ->
                        list.map { msg ->
                            if (msg.id == assistantMsgId) {
                                msg.copy(commandRun = cmd, thinking = "Executing command in Linux shell...")
                            } else msg
                        }
                    }

                    val outputSb = StringBuilder()
                    val exitCode = EngineExecutor.executeCommand(context, cmd, workingDir = validWsDir) { line, exitCode ->
                        outputSb.append(line).append("\n")
                        mutableChatMessages.update { list ->
                            list.map { msg ->
                                if (msg.id == assistantMsgId) {
                                    msg.copy(commandOutput = outputSb.toString())
                                } else msg
                            }
                        }
                    }

                    mutableChatMessages.update { list ->
                        list.map { msg ->
                            if (msg.id == assistantMsgId) {
                                msg.copy(
                                    status = if (exitCode == 0) MessageStatus.SENT else MessageStatus.ERROR,
                                    content = "",
                                    thinking = null
                                )
                            } else msg
                        }
                    }
                } else {

                    val stagedAttachments = mutableListOf<Triple<String, String, String>>()
                    if (attachments.isNotEmpty()) {
                        val targetDirs = listOf(
                            File(context.filesDir, ".gemini/antigravity-app/brain/$convId/.user_uploaded"),
                            File(context.filesDir, "home/.gemini/antigravity-app/brain/$convId/.user_uploaded")
                        )
                        for (dir in targetDirs) {
                            dir.mkdirs()
                            dir.setReadable(true, false)
                            dir.setExecutable(true, false)
                            try { android.system.Os.chmod(dir.absolutePath, 493) } catch (e: Exception) {}
                        }
                        for (att in attachments) {
                            val safeName = att.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                            var finalPath: String? = null
                            for (dir in targetDirs) {
                                val destFile = File(dir, safeName)
                                val sourceFile = att.localPath?.let { File(it) }
                                if (sourceFile != null && sourceFile.exists()) {
                                    runCatching { sourceFile.copyTo(destFile, overwrite = true) }
                                } else {
                                    runCatching {
                                        context.contentResolver.openInputStream(att.uri)?.use { input ->
                                            destFile.outputStream().use { output -> input.copyTo(output) }
                                        }
                                    }
                                }
                                destFile.setReadable(true, false)
                                try { android.system.Os.chmod(destFile.absolutePath, 420) } catch (e: Exception) {}
                                if (destFile.exists() && finalPath == null) {
                                    finalPath = destFile.absolutePath
                                }
                            }
                            stagedAttachments.add(Triple(att.name, finalPath ?: (att.localPath ?: att.name), att.mimeType))
                        }
                    }

                    val model = selectedModel.value
                    val thinking = thinkingBudget.value
                    val outputSb = StringBuilder()

                    val rawCliId = selectedModelCliId.value?.trim().orEmpty()
                    val cliId = AgyModelsService.cleanModelId(rawCliId)
                    val priorCliSession = convIdToCliSessionId[convId]

                    addLog("Agent", "Turn started: prompt='${prompt.take(120)}', attachments=${attachments.size}, model='${cliId.ifEmpty { "default" }}'")
                    TerminalSessionManager.ensureAgyLiveSession(autoSwitch = false)
                    TerminalSessionManager.appendAgyLine("[AGY] Turn started (model: ${cliId.ifEmpty { "default" }})", isCommand = true)
                    if (validWsDir != null) {
                        addLog("Agent", "Workspace: ${validWsDir.absolutePath}")
                        TerminalSessionManager.appendAgyLine("Working Directory: ${validWsDir.absolutePath}")
                    }


                    var activeCliConvId: String? = priorCliSession
                    var lastStreamUpdateMs = 0L
                    var lastStepUpdateMs = 0L
                    var lastTranscriptPollMs = 0L
                    var activeThoughtStartMs = 0L
                    val activeThoughtSb = StringBuilder()
                    val liveThoughtDurations = mutableListOf<Int>()
                    var lastAgentResponseDurationSec = 0
                    var thoughtSeq = 0
                    val authUrlRegex = "(https://accounts\\.google\\.com/o/oauth2/auth\\S+)".toRegex()

                    var currentTurn = 0
                    val maxAutoTurns = 8
                    var autoContinue = true
                    var exitCode = 0
                    var authRequired = false

                    while (autoContinue && currentTurn < maxAutoTurns && coroutineContext.isActive) {
                        currentTurn++
                        outputSb.clear()

                        val args = mutableListOf<String>()
                        val currentSec = securityPreset.value.lowercase()
                        args.add("--dangerously-skip-permissions")
                        if (currentSec == "request review" || currentSec == "request_review") {
                            args.add("--mode")
                            args.add("plan")
                        }
                        args.add("--output-format")
                        args.add("stream-json")
                        if (cliId.isNotBlank()) {
                            args.add("--model")
                            args.add(cliId)
                        }
                        if (validWsDir != null) {
                            args.add("--add-dir")
                            args.add(validWsDir.absolutePath)
                        }
                        val sessionToResume = activeCliConvId ?: priorCliSession
                        if (!sessionToResume.isNullOrBlank()) {
                            args.add("--conversation")
                            args.add(sessionToResume)
                        }
                        args.add("--print")
                        val currentPrompt = if (currentTurn == 1 && stagedAttachments.isNotEmpty()) {
                            buildString {
                                append("### USER ATTACHMENTS\n")
                                append("The user has attached the following ${stagedAttachments.size} file(s):\n")
                                stagedAttachments.forEachIndexed { idx, (name, path, mime) ->
                                    append("${idx + 1}. [Filename: $name]\n")
                                    append("   Path: $path\n")
                                    append("   MIME: $mime\n")
                                    if (mime.startsWith("image/")) {
                                        append("   Action: Call the `view_file` tool with AbsolutePath=\"$path\" to inspect this image.\n")
                                    } else {
                                        append("   Action: Call the `view_file` tool with AbsolutePath=\"$path\" to inspect this file.\n")
                                    }
                                }
                                append("MANDATORY: You must inspect the attached file(s) with the `view_file` tool before answering.\n\n")
                                append("### USER REQUEST\n")
                                append(if (prompt.isNotBlank()) prompt else "Please inspect the attached file(s) and assist the user.")
                            }
                        } else if (currentTurn == 1) {
                            prompt
                        } else {
                            "Continue and complete the task based on previous tool results. Provide thorough analysis or confirmation once finished."
                        }
                        args.add(currentPrompt)

                        if (currentTurn > 1) {
                            addLog("Agent", "Autonomous turn $currentTurn started...")
                        }

                        val onAgyLine: (String, Boolean) -> Unit = { line, _ ->
                        val trimmed = line.trim()
                        val match = authUrlRegex.find(trimmed)
                        if (match != null) {
                            // Google sign-in is not part of this build. Surface the URL
                            // as a log line so the user can still see what the engine
                            // asked for, without opening any OAuth flow.
                            addLog("Auth", "Engine requested Google auth (unsupported in this build): ${match.value}")
                        }

                        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                            try {
                                val json = org.json.JSONObject(trimmed)
                                val event = json.optString("event")
                                val detectedConv = json.optString("conversation_id").ifBlank {
                                    json.optString("conversationId").ifBlank {
                                        json.optString("session_id").ifBlank {
                                            json.optString("sessionId")
                                        }
                                    }
                                }
                                if (detectedConv.isNotBlank() && activeCliConvId != detectedConv) {
                                    activeCliConvId = detectedConv
                                    convIdToCliSessionId[convId] = detectedConv
                                    addLog("Agent", "Linked conversation session: $detectedConv")
                                }

                                when (event) {
                                    "step_update" -> {
                                        val stepUpdate = json.optJSONObject("step_update")
                                        if (stepUpdate != null) {
                                            val stepType = stepUpdate.optString("step_type")
                                            val state = stepUpdate.optString("state")
                                            if (stepType == "tool") {
                                                val rawToolName = stepUpdate.optString("tool_name")
                                                val toolName = rawToolName.substringAfterLast(":")
                                                val toolInfo = stepUpdate.optJSONObject("tool_info")
                                                val params = toolInfo?.optJSONObject("parameters")
                                                    ?: toolInfo?.optJSONObject("args")
                                                    ?: toolInfo?.optJSONObject("arguments")
                                                    ?: stepUpdate.optJSONObject("parameters")
                                                    ?: stepUpdate.optJSONObject("args")
                                                    ?: toolInfo

                                                val stepKind: AgentStepKind
                                                val stepTitle: String
                                                val stepLines: String?
                                                val stepDiff: String?
                                                val stepFilePath: String?

                                                when (toolName) {
                                                    "read_file", "view_file" -> {
                                                        val path = extractPathFromParams(params)
                                                        stepFilePath = path.ifEmpty { null }
                                                        stepTitle = extractFileNameFromPath(path, fallback = "file")
                                                        val start = params?.optInt("StartLine", -1) ?: -1
                                                        val end = params?.optInt("EndLine", -1) ?: -1
                                                        stepLines = if (start > 0 && end >= start) "#L$start-$end" else if (start > 0) "#L$start" else null
                                                        stepDiff = null
                                                        stepKind = AgentStepKind.ANALYZED
                                                    }
                                                    "write_to_file", "replace_file_content" -> {
                                                        val path = extractPathFromParams(params)
                                                        stepFilePath = path.ifEmpty { null }
                                                        stepTitle = extractFileNameFromPath(path, fallback = "file")
                                                        stepLines = null
                                                        val targetContent = params?.optString("TargetContent") ?: ""
                                                        val replacementContent = params?.optString("ReplacementContent") ?: params?.optString("CodeContent") ?: ""
                                                        val delCount = if (targetContent.isNotBlank()) targetContent.count { it == '\n' } + 1 else 0
                                                        val addCount = if (replacementContent.isNotBlank()) replacementContent.count { it == '\n' } + 1 else 1
                                                        stepDiff = "+$addCount -$delCount"
                                                        stepKind = AgentStepKind.EDITED
                                                    }
                                                    "run_command" -> {
                                                        val action = params?.optString("toolAction")?.trim()?.removeSurrounding("\"")?.removeSurrounding("'") ?: ""
                                                        val summary = params?.optString("toolSummary")?.trim()?.removeSurrounding("\"")?.removeSurrounding("'") ?: ""
                                                        val cmd = params?.optString("CommandLine")?.trim()?.removeSurrounding("\"")?.removeSurrounding("'") ?: ""
                                                        val cleanCmd = cmd.trim()
                                                        stepTitle = when {
                                                            action.isNotBlank() -> action
                                                            summary.isNotBlank() -> summary
                                                            cleanCmd.isNotBlank() -> cleanCmd
                                                            else -> "Command"
                                                        }
                                                        stepFilePath = null
                                                        stepLines = null
                                                        stepDiff = null
                                                        stepKind = AgentStepKind.COMMAND
                                                    }
                                                    "invoke_subagent" -> {
                                                        val subList = params?.optJSONArray("Subagents")
                                                        val firstSub = subList?.optJSONObject(0) ?: params
                                                        val role = firstSub?.optString("Role")?.ifEmpty { firstSub.optString("role") } ?: ""
                                                        val type = firstSub?.optString("TypeName")?.ifEmpty { firstSub.optString("typeName") } ?: ""
                                                        stepTitle = role.ifEmpty { type.ifEmpty { context.getString(R.string.subagent_default_title) } }
                                                        stepFilePath = null
                                                        stepLines = null
                                                        stepDiff = null
                                                        stepKind = AgentStepKind.WORKING
                                                    }
                                                    "manage_task" -> {
                                                        val action = params?.optString("Action") ?: "status"
                                                        val taskId = params?.optString("TaskId") ?: ""
                                                        stepTitle = if (taskId.isNotBlank()) "manage_task $action $taskId" else "manage_task $action"
                                                        stepFilePath = null
                                                        stepLines = null
                                                        stepDiff = null
                                                        stepKind = AgentStepKind.COMMAND
                                                    }
                                                    "schedule" -> {
                                                        val dur = params?.optInt("DurationSeconds", 0) ?: 0
                                                        stepTitle = if (dur > 0) "schedule (${dur}s)" else "schedule task"
                                                        stepFilePath = null
                                                        stepLines = null
                                                        stepDiff = null
                                                        stepKind = AgentStepKind.COMMAND
                                                    }
                                                    "search_web" -> {
                                                        val q = params?.optString("query")?.trim()?.removeSurrounding("\"") ?: ""
                                                        stepTitle = q.ifEmpty { "web" }
                                                        stepFilePath = null
                                                        stepLines = null
                                                        stepDiff = null
                                                        stepKind = AgentStepKind.SEARCH
                                                    }
                                                    "grep_search" -> {
                                                        val q = params?.optString("query")?.trim()?.removeSurrounding("\"") ?: ""
                                                        stepTitle = q.ifEmpty { "code" }
                                                        stepFilePath = null
                                                        stepLines = null
                                                        stepDiff = null
                                                        stepKind = AgentStepKind.SEARCH
                                                    }
                                                    "list_dir" -> {
                                                        val path = extractPathFromParams(params)
                                                        stepFilePath = path.ifEmpty { null }
                                                        stepTitle = extractFileNameFromPath(path, fallback = "directory")
                                                        stepLines = null
                                                        stepDiff = null
                                                        stepKind = AgentStepKind.ANALYZED
                                                    }
                                                    else -> {
                                                        stepFilePath = null
                                                        stepTitle = toolName.replace('_', ' ')
                                                        stepLines = null
                                                        stepDiff = null
                                                        stepKind = AgentStepKind.COMMAND
                                                    }
                                                }

                                                val stepDetail = stepUpdate.optString("output").ifEmpty {
                                                    stepUpdate.optString("result").ifEmpty { null }
                                                }
                                                val safeDetail = stepDetail

                                                val stableStepId = "step_live_${stepKind.name}_${stepFilePath ?: stepTitle}_${stepLines ?: ""}".replace(" ", "_")
                                                val execStep = AgentExecutionStep(
                                                    id = stableStepId,
                                                    kind = stepKind,
                                                    title = stepTitle,
                                                    lines = stepLines,
                                                    diff = stepDiff,
                                                    filePath = stepFilePath,
                                                    detail = safeDetail,
                                                    isCompleted = state != "ACTIVE"
                                                )

                                                if (toolName == "run_command") {
                                                    val cmdClean = params?.optString("CommandLine")?.trim()?.removeSurrounding("\"")?.removeSurrounding("'") ?: ""
                                                    if (state == "ACTIVE") {
                                                        if (cmdClean.isNotBlank() && mutableRunningTasks.value.none { it.id == stableStepId || it.command == cmdClean }) {
                                                            mutableRunningTasks.update { it + RunningTask(id = stableStepId, name = stepTitle, command = cmdClean) }
                                                        }
                                                    } else {
                                                        removeRunningTask(stableStepId)
                                                        if (cmdClean.isNotBlank()) {
                                                            mutableRunningTasks.update { list -> list.filter { it.command != cmdClean } }
                                                        }
                                                    }
                                                } else if (toolName == "invoke_subagent") {
                                                    if (state == "ACTIVE") {
                                                        val subList = params?.optJSONArray("Subagents")
                                                        if (subList != null && subList.length() > 0) {
                                                            for (s in 0 until subList.length()) {
                                                                val sObj = subList.optJSONObject(s) ?: continue
                                                                val r = sObj.optString("Role").ifEmpty { sObj.optString("role") }
                                                                val t = sObj.optString("TypeName").ifEmpty { sObj.optString("typeName") }
                                                                val p = sObj.optString("Prompt").ifEmpty { sObj.optString("prompt") }
                                                                val subId = "${stableStepId}_$s"
                                                                addRunningSubagent(RunningSubagent(id = subId, typeName = t, role = r, prompt = p, isRunning = true))
                                                            }
                                                        } else {
                                                            val r = params?.optString("Role")?.ifEmpty { params.optString("role") } ?: context.getString(R.string.subagent_default_title)
                                                            val t = params?.optString("TypeName")?.ifEmpty { params.optString("typeName") } ?: ""
                                                            val p = params?.optString("Prompt")?.ifEmpty { params.optString("prompt") } ?: ""
                                                            addRunningSubagent(RunningSubagent(id = stableStepId, typeName = t, role = r, prompt = p, isRunning = true))
                                                        }
                                                    } else {
                                                        val detectedSubConvId = try {
                                                            if (!stepDetail.isNullOrBlank()) {
                                                                val detailJson = JSONObject(stepDetail)
                                                                val res = detailJson.optJSONObject("result")
                                                                res?.optString("conversationId")?.ifEmpty { null }
                                                                    ?: detailJson.optString("conversationId").ifEmpty { null }
                                                            } else null
                                                        } catch (e: Exception) {
                                                            val regex = "([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})".toRegex()
                                                            stepDetail?.let { regex.find(it)?.value }
                                                        }
                                                        if (!detectedSubConvId.isNullOrBlank()) {
                                                            mutableRunningSubagents.update { list ->
                                                                list.map { if (it.id.startsWith(stableStepId) && it.conversationId == null) it.copy(conversationId = detectedSubConvId) else it }
                                                            }
                                                            val summary = ConversationSummary(
                                                                id = detectedSubConvId,
                                                                title = stepTitle,
                                                                timestamp = System.currentTimeMillis(),
                                                                messageCount = 1,
                                                                isPinned = false,
                                                                projectPath = validWsDir?.absolutePath
                                                            )
                                                            val history = ConversationPersistence.loadConversationHistory(context)
                                                            if (history.none { it.id == detectedSubConvId }) {
                                                                ConversationPersistence.saveConversationHistory(context, listOf(summary) + history)
                                                            }
                                                        }
                                                    }
                                                }

                                                if (state == "ACTIVE") {
                                                    val toolDesc = when {
                                                        stepKind == AgentStepKind.COMMAND -> "$stepTitle"
                                                        stepKind == AgentStepKind.EDITED -> "Edit $stepTitle ${stepDiff ?: ""}".trim()
                                                        stepKind == AgentStepKind.ANALYZED -> "Read $stepTitle ${stepLines ?: ""}".trim()
                                                        stepKind == AgentStepKind.SEARCH -> "Search $stepTitle"
                                                        else -> "$toolName $stepTitle"
                                                    }
                                                    TerminalSessionManager.appendAgyLine("> $toolDesc", isCommand = true)
                                                } else {
                                                    if (!stepDetail.isNullOrBlank()) {
                                                        TerminalSessionManager.appendAgyLine(stepDetail)
                                                    }
                                                }

                                                val now = System.currentTimeMillis()
                                                if (state != "ACTIVE" || now - lastStepUpdateMs > 300L) {
                                                    lastStepUpdateMs = now
                                                    val liveParsed = if (now - lastTranscriptPollMs > 3000L || state != "ACTIVE") {
                                                        lastTranscriptPollMs = now
                                                        parseTranscriptTurn(context, activeCliConvId)
                                                    } else null
                                                    val liveThinking = liveParsed?.thinking
                                                    val liveThoughts = liveParsed?.steps?.filter { it.kind == AgentStepKind.THOUGHT }.orEmpty()

                                                    mutableChatMessages.update { list ->
                                                        list.map { msg ->
                                                            if (msg.id == assistantMsgId) {
                                                                val curSteps = msg.steps
                                                                val completedSteps = curSteps.map { if (it.kind == AgentStepKind.THOUGHT && !it.isCompleted) it.copy(isCompleted = true) else it }
                                                                val existingIdx = completedSteps.indexOfFirst { it.id == stableStepId || (it.title == stepTitle && it.kind == stepKind) }
                                                                val baseSteps = if (existingIdx >= 0) {
                                                                    completedSteps.toMutableList().apply { set(existingIdx, execStep) }
                                                                } else {
                                                                    completedSteps + execStep
                                                                }
                                                                val updatedSteps = if (liveThoughts.isNotEmpty() && baseSteps.none { it.kind == AgentStepKind.THOUGHT }) {
                                                                    liveThoughts + baseSteps
                                                                } else {
                                                                    baseSteps
                                                                }
                                                                msg.copy(steps = updatedSteps, thinking = liveThinking ?: msg.thinking)
                                                            } else msg
                                                        }
                                                    }
                                                }
                                            } else if (stepType == "thought" || stepType == "reasoning") {
                                                val rawText = stepUpdate.optString("text", "")
                                                val textDelta = stepUpdate.optString("text_delta", "")
                                                if (rawText.isNotEmpty()) {
                                                    activeThoughtSb.clear()
                                                    activeThoughtSb.append(rawText)
                                                } else if (textDelta.isNotEmpty()) {
                                                    activeThoughtSb.append(textDelta)
                                                }
                                                val thoughtText = activeThoughtSb.toString()
                                                val now = System.currentTimeMillis()
                                                if (activeThoughtStartMs == 0L) {
                                                    activeThoughtStartMs = now
                                                }
                                                val rawDur = stepUpdate.optDouble("duration_seconds", 0.0).toInt()
                                                val duration = if (rawDur > 0) {
                                                    rawDur
                                                } else {
                                                    ((now - activeThoughtStartMs) / 1000L).toInt().coerceAtLeast(1)
                                                }
                                                val isDone = state == "DONE"
                                                val thoughtStep = AgentExecutionStep(
                                                    id = "step_live_thought_${assistantMsgId}_${thoughtSeq}",
                                                    kind = AgentStepKind.THOUGHT,
                                                    title = "Thought",
                                                    detail = thoughtText,
                                                    durationSeconds = duration,
                                                    isCompleted = isDone
                                                )
                                                if (isDone || now - lastStepUpdateMs > 150L) {
                                                    lastStepUpdateMs = now
                                                    if (thoughtText.isNotBlank() && isDone) {
                                                        TerminalSessionManager.appendAgyLine("* Thinking (${duration}s): ${thoughtText.replace('\n', ' ').take(200)}")
                                                    }
                                                    mutableChatMessages.update { list ->
                                                        list.map { msg ->
                                                            if (msg.id == assistantMsgId) {
                                                                val curSteps = msg.steps
                                                                val updated = if (curSteps.isNotEmpty() && curSteps.last().kind == AgentStepKind.THOUGHT && !curSteps.last().isCompleted) {
                                                                    curSteps.dropLast(1) + curSteps.last().copy(
                                                                        detail = thoughtText,
                                                                        durationSeconds = duration,
                                                                        isCompleted = isDone
                                                                    )
                                                                } else {
                                                                    curSteps.map { it.copy(isCompleted = true) } + thoughtStep
                                                                }
                                                                msg.copy(thinking = thoughtText.ifBlank { msg.thinking }, steps = updated)
                                                            } else msg
                                                        }
                                                    }
                                                }
                                                if (isDone) {
                                                    liveThoughtDurations.add(duration)
                                                    thoughtSeq++
                                                    activeThoughtStartMs = 0L
                                                    activeThoughtSb.clear()
                                                }
                                            } else if (stepType == "agent_response") {
                                                val respDur = stepUpdate.optDouble("duration_seconds", 0.0).toInt()
                                                if (respDur > 0) {
                                                    lastAgentResponseDurationSec = respDur
                                                }
                                                val textDelta = stepUpdate.optString("text_delta", "")
                                                if (textDelta.isNotEmpty()) {
                                                    outputSb.append(textDelta)
                                                    val now = System.currentTimeMillis()
                                                    if (now - lastStreamUpdateMs > 200L) {
                                                        lastStreamUpdateMs = now
                                                        val currentOutput = outputSb.toString()
                                                        mutableChatMessages.update { list ->
                                                            list.map { msg ->
                                                                if (msg.id == assistantMsgId) {
                                                                    val finalizedSteps = msg.steps.map { step ->
                                                                        if (step.kind == AgentStepKind.THOUGHT && !step.isCompleted) {
                                                                            step.copy(isCompleted = true)
                                                                        } else step
                                                                    }
                                                                    msg.copy(content = currentOutput, steps = finalizedSteps)
                                                                } else msg
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    "result" -> {
                                        val resultObj = json.optJSONObject("result")
                                        val finalResponse = resultObj?.optString("response")
                                        if (!finalResponse.isNullOrBlank()) {
                                            TerminalSessionManager.appendAgyLine(finalResponse)
                                            outputSb.clear()
                                            outputSb.append(finalResponse)
                                            val currentOutput = outputSb.toString().trim()
                                            val liveParsed = parseTranscriptTurn(context, activeCliConvId)
                                            val liveThinking = liveParsed.thinking
                                            val liveTranscriptSteps = liveParsed.steps
                                            mutableChatMessages.update { list ->
                                                list.map { msg ->
                                                    if (msg.id == assistantMsgId) {
                                                        msg.copy(
                                                            content = currentOutput,
                                                            thinking = liveThinking ?: msg.thinking,
                                                            steps = liveTranscriptSteps ?: msg.steps
                                                        )
                                                    } else msg
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                TerminalSessionManager.appendAgyLine(line)
                                outputSb.append(line).append("\n")
                                val now = System.currentTimeMillis()
                                if (now - lastStreamUpdateMs > 250L) {
                                    lastStreamUpdateMs = now
                                    val currentOutput = outputSb.toString()
                                    mutableChatMessages.update { list ->
                                        list.map { msg ->
                                            if (msg.id == assistantMsgId) {
                                                msg.copy(content = currentOutput)
                                            } else msg
                                        }
                                    }
                                }
                            }
                        } else if (trimmed.isNotEmpty()) {
                            if (!trimmed.startsWith("Get:") && !trimmed.startsWith("#") && !trimmed.startsWith("Unpacking")) {
                                TerminalSessionManager.appendAgyLine(line)
                                outputSb.append(line).append("\n")
                                val now = System.currentTimeMillis()
                                if (now - lastStreamUpdateMs > 250L) {
                                    lastStreamUpdateMs = now
                                    val currentOutput = outputSb.toString()
                                    mutableChatMessages.update { list ->
                                        list.map { msg ->
                                            if (msg.id == assistantMsgId) {
                                                msg.copy(content = currentOutput)
                                            } else msg
                                        }
                                    }
                                }
                            }
                        }
                    }

                        val useAcp = currentSec != "request review" && currentSec != "request_review"
                        exitCode = if (useAcp) {
                            EngineExecutor.executeAgyAcpTurn(
                                context = context,
                                prompt = currentPrompt,
                                cliModelId = cliId.ifBlank { null },
                                sessionToResume = sessionToResume,
                                workingDir = validWsDir,
                                onOutputLine = onAgyLine
                            ).let { acpCode ->
                                if (acpCode == EngineExecutor.ACP_UNAVAILABLE) {
                                    EngineExecutor.executeAgy(context, args, workingDir = validWsDir, onOutputLine = onAgyLine)
                                } else acpCode
                            }
                        } else {
                            EngineExecutor.executeAgy(context, args, workingDir = validWsDir, onOutputLine = onAgyLine)
                        }

                        if (outputSb.isNotEmpty()) {
                            val finalCurrentOutput = outputSb.toString()
                            mutableChatMessages.update { list ->
                                list.map { msg ->
                                    if (msg.id == assistantMsgId && msg.content != finalCurrentOutput) {
                                        msg.copy(content = finalCurrentOutput)
                                    } else msg
                                }
                            }
                        }

                        val outputStr = outputSb.toString()
                        val keyMissing = outputStr.contains("GEMINI_API_KEY", ignoreCase = true) ||
                                outputStr.contains("is not set", ignoreCase = true)
                        authRequired = keyMissing ||
                                outputStr.contains("authentication required", ignoreCase = true) ||
                                outputStr.contains("authentication failed", ignoreCase = true) ||
                                outputStr.contains("visit the url to log in", ignoreCase = true) ||
                                outputStr.contains("not logged in", ignoreCase = true)

                        val intermediateParsed = parseTranscriptTurn(context, activeCliConvId)
                        val transcriptResponse = intermediateParsed.assistantResponse
                        val hasTextOutput = outputSb.isNotBlank() || !transcriptResponse.isNullOrBlank()
                        val liveTranscriptSteps = intermediateParsed.steps
                        val hasExecutedSteps = !liveTranscriptSteps.isNullOrEmpty()

                        if (exitCode != 0 || authRequired || hasTextOutput || !hasExecutedSteps) {
                            autoContinue = false
                        } else {
                            addLog("Agent", "Turn $currentTurn completed with intermediate tool execution. Continuing to next turn for completion...")
                        }
                    }

                    if (exitCode == 0) {
                        refreshAuthState(context)
                    } else if (authRequired) {
                        addLog("Auth", "Engine reported that authentication is required.", isError = true)
                    }

                    val elapsedSec = ((System.currentTimeMillis() - turnStartTime) / 1000).coerceAtLeast(1)
                    val durText = if (elapsedSec >= 60) "${elapsedSec / 60}m" else "${elapsedSec}s"
                    val turnChanges = computeTurnFileChanges(context, workspacePath.value)

                    val currentSteps = mutableChatMessages.value.find { it.id == assistantMsgId }?.steps.orEmpty()
                    val finalParsed = parseTranscriptTurn(context, activeCliConvId)
                    val finalThinking = finalParsed.thinking
                        ?: currentSteps.find { it.kind == AgentStepKind.THOUGHT }?.detail

                    val transcriptSteps = finalParsed.steps
                    val finalSteps = if (!transcriptSteps.isNullOrEmpty()) {
                        val liveThoughtSteps = currentSteps.filter { it.kind == AgentStepKind.THOUGHT }
                        var thoughtIdx = 0
                        transcriptSteps.map { step ->
                            if (step.kind == AgentStepKind.THOUGHT) {
                                val resolvedDur = step.durationSeconds
                                    ?: liveThoughtDurations.getOrNull(thoughtIdx)
                                    ?: liveThoughtSteps.getOrNull(thoughtIdx)?.durationSeconds
                                    ?: if (thoughtIdx == 0 && lastAgentResponseDurationSec > 0) lastAgentResponseDurationSec else null
                                thoughtIdx++
                                step.copy(durationSeconds = resolvedDur, isCompleted = true)
                            } else {
                                step
                            }
                        }
                    } else if (currentSteps.isNotEmpty()) {
                        val hasThought = currentSteps.any { it.kind == AgentStepKind.THOUGHT }
                        if (!hasThought && finalThinking != null) {
                            val thoughtStep = AgentExecutionStep(
                                id = "step_thought_final",
                                kind = AgentStepKind.THOUGHT,
                                title = "Thought",
                                detail = finalThinking,
                                durationSeconds = elapsedSec.toInt(),
                                isCompleted = true
                            )
                            listOf(thoughtStep) + currentSteps.map { it.copy(isCompleted = true) }
                        } else {
                            currentSteps.map { it.copy(isCompleted = true) }
                        }
                    } else if (finalThinking != null) {
                        listOf(
                            AgentExecutionStep(
                                id = "step_thought_final",
                                kind = AgentStepKind.THOUGHT,
                                title = "Thought",
                                detail = finalThinking,
                                durationSeconds = elapsedSec.toInt(),
                                isCompleted = true
                            )
                        )
                    } else {
                        emptyList()
                    }

                    val transcriptResponse = finalParsed.assistantResponse
                    val finalContent = if (exitCode != 0 && authRequired) {
                        if (GeminiClient.getApiKey(context).isNullOrBlank()) {
                            "No Gemini API key found. Open Settings > API Key, paste a key from " +
                                "Google AI Studio, and send the message again."
                        } else {
                            "The on-device agent could not authenticate with your Gemini API key. " +
                                "Check that the key is valid and has the Generative Language API enabled, " +
                                "then try again."
                        }
                    } else if (outputSb.isNotEmpty()) {
                        outputSb.toString().trim()
                    } else if (!transcriptResponse.isNullOrBlank()) {
                        transcriptResponse
                    } else if (finalSteps.isNotEmpty()) {
                        val executedNames = finalSteps.filter { it.kind != AgentStepKind.THOUGHT }.map { it.title }
                        if (executedNames.isNotEmpty()) {
                            context.getString(R.string.chat_step_execution_finished, executedNames.joinToString(", "))
                        } else {
                            context.getString(R.string.chat_inspection_finished)
                        }
                    } else {
                        context.getString(R.string.chat_task_completed)
                    }

                    addLog("Agent", "Turn completed ($durText). Output: ${finalContent.take(150)}")
                    TerminalSessionManager.appendAgyLine("[AGY] Turn $currentTurn completed ($durText)")

                    mutableChatMessages.update { list ->
                        list.map { msg ->
                            if (msg.id == assistantMsgId) {
                                val summaryParts = mutableListOf<String>()
                                if (finalSteps.any { it.kind == AgentStepKind.ANALYZED }) summaryParts.add("Explored file")
                                if (finalSteps.any { it.kind == AgentStepKind.COMMAND }) summaryParts.add("ran commands")
                                if (finalSteps.any { it.kind == AgentStepKind.EDITED } || (turnChanges != null && turnChanges.totalFiles > 0)) summaryParts.add("edited file")
                                val actSummary = if (summaryParts.isNotEmpty()) summaryParts.joinToString(", ") else null

                                msg.copy(
                                    status = if (exitCode == 0) MessageStatus.SENT else MessageStatus.ERROR,
                                    content = finalContent,
                                    thinking = finalThinking,
                                    durationText = durText,
                                    actionSummary = actSummary,
                                    fileChanges = turnChanges,
                                    steps = finalSteps
                                )
                            } else msg
                        }
                    }
                }
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) {
                    mutableChatMessages.update { list ->
                        list.filter { it.id != assistantMsgId || it.content.isNotBlank() }
                    }
                } else {
                    mutableChatMessages.update { list ->
                        list.map { msg ->
                            if (msg.id == assistantMsgId) {
                                msg.copy(
                                    status = MessageStatus.ERROR,
                                    content = "Execution error: ${e.message}",
                                    thinking = null
                                )
                            } else msg
                        }
                    }
                }
            } finally {
                executionWakeLock.release()
                mutableIsExecuting.value = false
                mutableActiveWorkingStatus.value = null
                currentAiExecutionJob = null
                mutableRunningTasks.value = emptyList()
                mutableRunningSubagents.update { list -> list.map { it.copy(isRunning = false) } }
                val currentList = mutableChatMessages.value
                conversationManagerDelegate.updateConversationHistoryDirect { list ->
                    list.map { if (it.id == convId) it.copy(messageCount = currentList.size, timestamp = System.currentTimeMillis()) else it }
                }
                saveConversationHistory(context)
                saveMessagesForConversation(context, convId, currentList)
                processNextQueuedMessage(context)
                refreshUserQuota(context)
            }
        }
    }

    suspend fun computeTurnFileChanges(context: Context, workspacePath: String?): TurnFileChanges? = withContext(Dispatchers.IO) {
        val root = workspacePath?.let { File(it) } ?: return@withContext null
        if (!root.exists() || !root.isDirectory || !File(root, ".git").exists()) return@withContext null

        val fileList = mutableListOf<ChangedFileDetail>()
        var totalAdd = 0
        var totalDel = 0

        try {
            val statusMap = mutableMapOf<String, String>()
            EngineExecutor.executeCommand(context, "cd '${root.absolutePath}' && git status --porcelain 2>/dev/null") { line, exitCode ->
                val trimmed = line.trim()
                if (trimmed.length >= 3) {
                    val st = trimmed.substring(0, 2).trim()
                    val path = trimmed.substring(2).trim().trim('"', '\'')
                    val statusDesc = when {
                        st.contains("D") -> "D"
                        st.contains("A") || st.contains("?") -> "A"
                        else -> "M"
                    }
                    statusMap[path] = statusDesc
                }
            }

            val numstats = mutableListOf<Triple<String, Int, Int>>()
            EngineExecutor.executeCommand(context, "cd '${root.absolutePath}' && git diff --numstat 2>/dev/null") { line, exitCode ->
                val parts = line.trim().split(Regex("""\s+"""))
                if (parts.size >= 3) {
                    val add = parts[0].toIntOrNull() ?: 0
                    val del = parts[1].toIntOrNull() ?: 0
                    val path = parts[2].trim().trim('"', '\'')
                    numstats.add(Triple(path, add, del))
                }
            }

            val diffsMap = mutableMapOf<String, String>()
            if (numstats.isNotEmpty() && numstats.size <= 30) {
                val combinedDiffSb = StringBuilder()
                EngineExecutor.executeCommand(context, "cd '${root.absolutePath}' && git diff -U3 2>/dev/null | head -n 1200") { line, isErr ->
                    combinedDiffSb.append(line).append("\n")
                }
                val fullDiff = combinedDiffSb.toString()
                if (fullDiff.isNotBlank()) {
                    val fileBlocks = fullDiff.split(Regex("""(?=diff --git)"""))
                    for (block in fileBlocks) {
                        val headerMatch = Regex("""diff --git a/(.+?) b/""").find(block)
                        if (headerMatch != null) {
                            val p = headerMatch.groupValues[1].trim()
                            diffsMap[p] = block.take(5000)
                        }
                    }
                }
            }

            for ((path, add, del) in numstats) {
                totalAdd += add
                totalDel += del
                val st = statusMap[path] ?: "M"
                val diffContent = diffsMap[path] ?: "[File modified (${add} additions, ${del} deletions)]"
                fileList.add(
                    ChangedFileDetail(
                        name = path.substringAfterLast('/'),
                        relativePath = path,
                        status = st,
                        additions = add,
                        deletions = del,
                        diff = diffContent
                    )
                )
            }

            for ((path, st) in statusMap) {
                if (fileList.none { it.relativePath == path }) {
                    val diffContent = diffsMap[path] ?: if (st == "A") "[New file created]" else "[File deleted]"
                    fileList.add(
                        ChangedFileDetail(
                            name = path.substringAfterLast('/'),
                            relativePath = path,
                            status = st,
                            additions = if (st == "A") 1 else 0,
                            deletions = if (st == "D") 1 else 0,
                            diff = diffContent
                        )
                    )
                    if (st == "A") totalAdd++
                    if (st == "D") totalDel++
                }
            }
        } catch (e: Throwable) {}

        if (fileList.isNotEmpty()) {
            TurnFileChanges(
                totalFiles = fileList.size,
                totalAdditions = totalAdd,
                totalDeletions = totalDel,
                files = fileList
            )
        } else null
    }

    private fun extractPathFromParams(params: JSONObject?): String =
        TranscriptTurnParser.extractPathFromParams(params)

    private fun extractFileNameFromPath(path: String, fallback: String = "file"): String =
        TranscriptTurnParser.extractFileNameFromPath(path, fallback)

    fun findActiveTranscriptFile(context: Context, cliConvId: String?): File? =
        TranscriptTurnParser.findActiveTranscriptFile(context, cliConvId)

    fun parseTranscriptTurn(context: Context, cliConvId: String?): ParsedTranscriptTurn =
        TranscriptTurnParser.parseTranscriptTurn(context, cliConvId)

    fun extractStepsFromCliTranscript(context: Context, cliConvId: String?): List<AgentExecutionStep>? =
        TranscriptTurnParser.parseTranscriptTurn(context, cliConvId).steps

    fun extractAllThinkingFromCliTranscript(context: Context, cliConvId: String?): String? =
        TranscriptTurnParser.parseTranscriptTurn(context, cliConvId).thinking

    fun extractAssistantResponseFromCliTranscript(context: Context, cliConvId: String?): String? =
        TranscriptTurnParser.parseTranscriptTurn(context, cliConvId).assistantResponse
}
