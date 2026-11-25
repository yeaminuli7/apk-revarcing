package id.vern.antigravity.viewmodel

import android.content.Context
import id.vern.antigravity.AntigravityApp
import id.vern.antigravity.data.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class ConversationManagerDelegate {
    private val mutableConversationHistory = MutableStateFlow<List<ConversationSummary>>(emptyList())
    val conversationHistory: StateFlow<List<ConversationSummary>> = mutableConversationHistory.asStateFlow()

    private val mutableActiveConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = mutableActiveConversationId.asStateFlow()

    private val mutableConversationTitle = MutableStateFlow("New Conversation")
    val conversationTitle: StateFlow<String> = mutableConversationTitle.asStateFlow()

    private val mutableIsConversationPinned = MutableStateFlow(false)
    val isConversationPinned: StateFlow<Boolean> = mutableIsConversationPinned.asStateFlow()

    fun saveConversationHistory(context: Context) {
        ConversationPersistence.saveConversationHistory(context, mutableConversationHistory.value)
    }

    fun saveLastActiveConversationId(context: Context, id: String?) {
        ConversationPersistence.saveLastActiveConversationId(context, id)
    }

    fun saveMessagesForConversation(context: Context, conversationId: String, messages: List<ChatMessage>, scope: CoroutineScope) {
        val messagesSnapshot = messages.toList()
        scope.launch(Dispatchers.IO) {
            ConversationPersistence.saveMessagesForConversation(context, conversationId, messagesSnapshot)
        }
    }

    fun loadMessagesForConversation(context: Context, conversationId: String): List<ChatMessage> {
        return ConversationPersistence.loadMessagesForConversation(context, conversationId)
    }

    fun deleteConversationFile(context: Context, conversationId: String) {
        ConversationPersistence.deleteConversationFile(context, conversationId)
    }

    fun loadConversationHistory(
        context: Context,
        onWorkspaceChange: (String) -> Unit,
        onMessagesLoaded: (List<ChatMessage>) -> Unit
    ) {
        val list = ConversationPersistence.loadConversationHistory(context)
        mutableConversationHistory.value = list

        val lastActiveId = ConversationPersistence.getLastActiveConversationId(context)
        val activeSummary = list.firstOrNull { it.id == lastActiveId } ?: list.firstOrNull()
        if (activeSummary != null) {
            mutableActiveConversationId.value = activeSummary.id
            mutableConversationTitle.value = activeSummary.title
            mutableIsConversationPinned.value = activeSummary.isPinned
            if (!activeSummary.projectPath.isNullOrBlank()) {
                onWorkspaceChange(activeSummary.projectPath)
            }
            onMessagesLoaded(loadMessagesForConversation(context, activeSummary.id))
            saveLastActiveConversationId(context, activeSummary.id)
        } else {
            mutableActiveConversationId.value = null
            mutableConversationTitle.value = "New Conversation"
            mutableIsConversationPinned.value = false
            onMessagesLoaded(emptyList())
        }
    }

    fun startNewConversation(
        context: Context?,
        projectPath: String?,
        currentMessages: List<ChatMessage>,
        scope: CoroutineScope,
        onWorkspaceChange: (String) -> Unit,
        onClearState: () -> Unit
    ) {
        val currentId = mutableActiveConversationId.value
        if (context != null && currentId != null && currentMessages.isNotEmpty()) {
            saveMessagesForConversation(context, currentId, currentMessages, scope)
        }
        mutableConversationTitle.value = "New Conversation"
        mutableActiveConversationId.value = null
        mutableIsConversationPinned.value = false
        onClearState()
        if (!projectPath.isNullOrBlank()) {
            onWorkspaceChange(projectPath)
        }
        if (context != null) {
            saveLastActiveConversationId(context, null)
        }
    }

    fun loadConversation(
        context: Context,
        summary: ConversationSummary,
        currentMessages: List<ChatMessage>,
        scope: CoroutineScope,
        onWorkspaceChange: (String) -> Unit,
        onMessagesLoaded: (List<ChatMessage>) -> Unit,
        onLog: (String, String) -> Unit
    ) {
        val currentId = mutableActiveConversationId.value
        if (currentId != null && currentMessages.isNotEmpty()) {
            saveMessagesForConversation(context, currentId, currentMessages, scope)
        }
        mutableActiveConversationId.value = summary.id
        mutableConversationTitle.value = summary.title
        mutableIsConversationPinned.value = summary.isPinned
        if (!summary.projectPath.isNullOrBlank()) {
            onWorkspaceChange(summary.projectPath)
        }
        onMessagesLoaded(loadMessagesForConversation(context, summary.id))
        saveLastActiveConversationId(context, summary.id)
        onLog("Conversation", "Switched to conversation: ${summary.title}")
    }

    fun loadConversation(
        summary: ConversationSummary,
        onWorkspaceChange: (String) -> Unit,
        onLog: (String, String) -> Unit
    ) {
        mutableActiveConversationId.value = summary.id
        mutableConversationTitle.value = summary.title
        mutableIsConversationPinned.value = summary.isPinned
        if (!summary.projectPath.isNullOrBlank()) {
            onWorkspaceChange(summary.projectPath)
        }
        onLog("Conversation", "Switched to conversation: ${summary.title}")
    }

    fun renameConversation(
        newTitle: String,
        summaryId: String? = null,
        context: Context? = null,
        currentMessageCount: Int = 0,
        currentWorkspacePath: String = "",
        onLog: (String, String) -> Unit
    ) {
        val trimmed = newTitle.trim()
        if (trimmed.isNotEmpty()) {
            val targetContext = context ?: try { AntigravityApp.instance } catch (_: Exception) { null }
            if (summaryId == null) {
                mutableConversationTitle.value = trimmed
                var currentId = mutableActiveConversationId.value
                if (currentId == null) {
                    currentId = UUID.randomUUID().toString()
                    mutableActiveConversationId.value = currentId
                }
                mutableConversationHistory.update { list ->
                    val exists = list.any { it.id == currentId }
                    if (exists) {
                        list.map { if (it.id == currentId) it.copy(title = trimmed) else it }
                    } else {
                        listOf(
                            ConversationSummary(
                                id = currentId,
                                title = trimmed,
                                timestamp = System.currentTimeMillis(),
                                messageCount = currentMessageCount,
                                isPinned = mutableIsConversationPinned.value,
                                projectPath = currentWorkspacePath
                            )
                        ) + list
                    }
                }
                if (targetContext != null) {
                    saveLastActiveConversationId(targetContext, currentId)
                }
            } else {
                mutableConversationHistory.update { list ->
                    list.map { if (it.id == summaryId) it.copy(title = trimmed) else it }
                }
                if (mutableActiveConversationId.value == summaryId) {
                    mutableConversationTitle.value = trimmed
                }
            }
            if (targetContext != null) {
                saveConversationHistory(targetContext)
            }
            onLog("Conversation", "Conversation renamed to: $trimmed")
        }
    }

    fun togglePinConversation(
        summaryId: String? = null,
        context: Context? = null,
        onLog: (String, String) -> Unit
    ): Boolean {
        val targetContext = context ?: try { AntigravityApp.instance } catch (_: Exception) { null }
        if (summaryId == null) {
            mutableIsConversationPinned.update { !it }
            val pinned = mutableIsConversationPinned.value
            val currentId = mutableActiveConversationId.value
            if (currentId != null) {
                mutableConversationHistory.update { list ->
                    list.map { if (it.id == currentId) it.copy(isPinned = pinned) else it }
                }
                if (targetContext != null) saveConversationHistory(targetContext)
            }
            onLog("Conversation", if (pinned) "Conversation pinned" else "Conversation unpinned")
            return pinned
        } else {
            var newPinned = false
            mutableConversationHistory.update { list ->
                list.map {
                    if (it.id == summaryId) {
                        newPinned = !it.isPinned
                        it.copy(isPinned = newPinned)
                    } else it
                }
            }
            if (mutableActiveConversationId.value == summaryId) {
                mutableIsConversationPinned.value = newPinned
            }
            if (targetContext != null) saveConversationHistory(targetContext)
            return true
        }
    }

    fun deleteConversation(
        context: Context?,
        id: String,
        onNextSummaryLoaded: ((ConversationSummary) -> Unit)? = null,
        onCleared: (() -> Unit)? = null,
        onLog: (String, String) -> Unit
    ) {
        val targetContext = context ?: try { AntigravityApp.instance } catch (_: Exception) { null }
        mutableConversationHistory.update { list -> list.filter { it.id != id } }
        if (targetContext != null) {
            saveConversationHistory(targetContext)
            deleteConversationFile(targetContext, id)
        }
        if (mutableActiveConversationId.value == id) {
            val nextConv = mutableConversationHistory.value.firstOrNull()
            if (nextConv != null && onNextSummaryLoaded != null) {
                onNextSummaryLoaded(nextConv)
            } else {
                mutableActiveConversationId.value = null
                mutableConversationTitle.value = "New Conversation"
                mutableIsConversationPinned.value = false
                onCleared?.invoke()
                if (targetContext != null) {
                    saveLastActiveConversationId(targetContext, null)
                }
            }
        }
        onLog("Conversation", "Deleted conversation $id")
    }

    fun deleteCurrentConversation(
        context: Context,
        onNextSummaryLoaded: ((ConversationSummary) -> Unit)? = null,
        onCleared: (() -> Unit)? = null,
        onLog: (String, String) -> Unit
    ) {
        val currentId = mutableActiveConversationId.value
        if (currentId != null) {
            deleteConversation(context, currentId, onNextSummaryLoaded, onCleared, onLog)
        } else {
            val currentTitle = mutableConversationTitle.value
            val match = mutableConversationHistory.value.firstOrNull { it.title == currentTitle }
            if (match != null) {
                deleteConversation(context, match.id, onNextSummaryLoaded, onCleared, onLog)
            } else {
                onCleared?.invoke()
                mutableConversationTitle.value = "New Conversation"
                mutableIsConversationPinned.value = false
            }
        }
    }

    fun archiveConversation(
        context: Context?,
        currentMessages: List<ChatMessage>,
        currentWorkspacePath: String
    ) {
        val targetContext = context ?: try { AntigravityApp.instance } catch (_: Exception) { null }
        val currentId = mutableActiveConversationId.value
        if (currentMessages.isNotEmpty()) {
            val title = mutableConversationTitle.value
            val convId = currentId ?: UUID.randomUUID().toString()
            val summary = ConversationSummary(
                id = convId,
                title = title,
                timestamp = System.currentTimeMillis(),
                messageCount = currentMessages.size,
                isPinned = mutableIsConversationPinned.value,
                projectPath = currentWorkspacePath
            )
            mutableConversationHistory.update { list ->
                listOf(summary) + list.filter { it.id != convId }
            }
            if (targetContext != null) {
                saveConversationHistory(targetContext)
            }
        }
    }

    fun setActiveConversationIdDirect(id: String?) {
        mutableActiveConversationId.value = id
    }

    fun setConversationTitleDirect(title: String) {
        mutableConversationTitle.value = title
    }

    fun setConversationPinnedDirect(pinned: Boolean) {
        mutableIsConversationPinned.value = pinned
    }

    fun updateConversationHistoryDirect(block: (List<ConversationSummary>) -> List<ConversationSummary>) {
        mutableConversationHistory.update(block)
    }
}
