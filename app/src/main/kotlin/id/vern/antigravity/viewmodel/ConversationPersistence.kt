package id.vern.antigravity.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import id.vern.antigravity.data.AgentExecutionStep
import id.vern.antigravity.data.AgentStepKind
import id.vern.antigravity.data.AttachedFile
import id.vern.antigravity.data.ChatMessage
import id.vern.antigravity.data.MessageRole
import id.vern.antigravity.data.MessageStatus
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

object ConversationPersistence {
    private const val PREFS_NAME = "antigravity_prefs"
    private const val KEY_CONVERSATION_HISTORY = "ag_conversation_history"
    private const val KEY_LAST_ACTIVE_CONV_ID = "ag_last_active_conv_id"

    fun saveConversationHistory(context: Context, history: List<ConversationSummary>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        for (item in history) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("timestamp", item.timestamp)
                put("messageCount", item.messageCount)
                put("isPinned", item.isPinned)
                if (item.projectPath != null) put("projectPath", item.projectPath)
            }
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_CONVERSATION_HISTORY, jsonArray.toString()).apply()
    }

    fun loadConversationHistory(context: Context): List<ConversationSummary> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_CONVERSATION_HISTORY, null)
        val list = mutableListOf<ConversationSummary>()
        if (!jsonStr.isNullOrBlank()) {
            try {
                val jsonArray = JSONArray(jsonStr)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        ConversationSummary(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            messageCount = obj.optInt("messageCount", 0),
                            isPinned = obj.optBoolean("isPinned", false),
                            projectPath = if (obj.has("projectPath")) obj.getString("projectPath") else null
                        )
                    )
                }
            } catch (e: Exception) {}
        }
        return list
    }

    fun saveLastActiveConversationId(context: Context, id: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (id == null) {
            prefs.edit().remove(KEY_LAST_ACTIVE_CONV_ID).apply()
        } else {
            prefs.edit().putString(KEY_LAST_ACTIVE_CONV_ID, id).apply()
        }
    }

    fun getLastActiveConversationId(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_ACTIVE_CONV_ID, null)
    }

    fun saveMessagesForConversation(context: Context, conversationId: String, messages: List<ChatMessage>) {
        try {
            val convDir = File(context.filesDir, "conversations").apply { mkdirs() }
            val file = File(convDir, "$conversationId.json")
            val array = JSONArray()
            for (msg in messages) {
                val obj = JSONObject().apply {
                    put("id", msg.id)
                    put("role", msg.role.name)
                    put("content", msg.content)
                    put("timestamp", msg.timestamp)
                    put("status", msg.status.name)
                    if (msg.thinking != null) put("thinking", msg.thinking)
                    if (msg.commandRun != null) put("commandRun", msg.commandRun)
                    if (msg.commandOutput != null) put("commandOutput", msg.commandOutput)
                    if (msg.codeBlocks.isNotEmpty()) {
                        val blocksArr = JSONArray()
                        msg.codeBlocks.forEach { blocksArr.put(it) }
                        put("codeBlocks", blocksArr)
                    }
                    if (msg.attachments.isNotEmpty()) {
                        val attArr = JSONArray()
                        for (att in msg.attachments) {
                            val attObj = JSONObject().apply {
                                put("id", att.id)
                                put("uri", att.uri.toString())
                                put("name", att.name)
                                put("sizeBytes", att.sizeBytes)
                                put("mimeType", att.mimeType)
                                if (att.localPath != null) put("localPath", att.localPath)
                            }
                            attArr.put(attObj)
                        }
                        put("attachments", attArr)
                    }
                    if (msg.durationText != null) put("durationText", msg.durationText)
                    if (msg.actionSummary != null) put("actionSummary", msg.actionSummary)
                    if (msg.steps.isNotEmpty()) {
                        val stepsArr = JSONArray()
                        for (s in msg.steps) {
                            val sObj = JSONObject().apply {
                                put("id", s.id)
                                put("kind", s.kind.name)
                                put("title", s.title)
                                if (s.detail != null) put("detail", s.detail)
                                if (s.lines != null) put("lines", s.lines)
                                if (s.diff != null) put("diff", s.diff)
                                if (s.durationSeconds != null) put("durationSeconds", s.durationSeconds)
                                put("isCompleted", s.isCompleted)
                                if (s.filePath != null) put("filePath", s.filePath)
                            }
                            stepsArr.put(sObj)
                        }
                        put("steps", stepsArr)
                    }
                }
                array.put(obj)
            }
            file.writeText(array.toString())
        } catch (e: Exception) {
            Log.w("ConversationPersistence", "Failed saving messages for conversation $conversationId: ${e.message}")
        }
    }

    fun loadMessagesForConversation(context: Context, conversationId: String): List<ChatMessage> {
        try {
            val file = File(File(context.filesDir, "conversations"), "$conversationId.json")
            if (!file.exists()) {
                val transcriptMessages = TranscriptTurnParser.parseTranscriptToMessages(context, conversationId)
                if (transcriptMessages.isNotEmpty()) return transcriptMessages
                return emptyList()
            }
            val text = file.readText()
            if (text.isBlank()) return emptyList()
            val array = JSONArray(text)
            val result = mutableListOf<ChatMessage>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val roleStr = obj.optString("role", "USER")
                val role = try { MessageRole.valueOf(roleStr) } catch (e: Exception) { MessageRole.USER }
                val statusStr = obj.optString("status", "SENT")
                val status = try { MessageStatus.valueOf(statusStr) } catch (e: Exception) { MessageStatus.SENT }
                val codeBlocks = mutableListOf<String>()
                if (obj.has("codeBlocks")) {
                    val cbArr = obj.getJSONArray("codeBlocks")
                    for (j in 0 until cbArr.length()) {
                        codeBlocks.add(cbArr.getString(j))
                    }
                }
                val attachments = mutableListOf<AttachedFile>()
                if (obj.has("attachments")) {
                    val attArr = obj.getJSONArray("attachments")
                    for (j in 0 until attArr.length()) {
                        val attObj = attArr.getJSONObject(j)
                        attachments.add(
                            AttachedFile(
                                id = attObj.optString("id", UUID.randomUUID().toString()),
                                uri = Uri.parse(attObj.optString("uri", "")),
                                name = attObj.optString("name", "attachment"),
                                sizeBytes = attObj.optLong("sizeBytes", 0L),
                                mimeType = attObj.optString("mimeType", "application/octet-stream"),
                                localPath = attObj.optString("localPath", "").ifEmpty { null }
                            )
                        )
                    }
                }
                val steps = mutableListOf<AgentExecutionStep>()
                if (obj.has("steps")) {
                    val stepsArr = obj.getJSONArray("steps")
                    for (k in 0 until stepsArr.length()) {
                        val sObj = stepsArr.getJSONObject(k)
                        val kStr = sObj.optString("kind", "COMMAND")
                        val kind = try { AgentStepKind.valueOf(kStr) } catch (e: Exception) { AgentStepKind.COMMAND }
                        steps.add(
                            AgentExecutionStep(
                                id = sObj.optString("id", UUID.randomUUID().toString()),
                                kind = kind,
                                title = sObj.optString("title", ""),
                                detail = if (sObj.has("detail")) sObj.getString("detail") else null,
                                lines = if (sObj.has("lines")) sObj.getString("lines") else null,
                                diff = if (sObj.has("diff")) sObj.getString("diff") else null,
                                durationSeconds = if (sObj.has("durationSeconds")) sObj.getInt("durationSeconds") else null,
                                isCompleted = sObj.optBoolean("isCompleted", true),
                                filePath = if (sObj.has("filePath")) sObj.getString("filePath") else null
                            )
                        )
                    }
                }
                result.add(
                    ChatMessage(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        role = role,
                        content = obj.optString("content", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        status = status,
                        attachments = attachments,
                        thinking = if (obj.has("thinking")) obj.getString("thinking") else null,
                        commandRun = if (obj.has("commandRun")) obj.getString("commandRun") else null,
                        commandOutput = if (obj.has("commandOutput")) obj.getString("commandOutput") else null,
                        codeBlocks = codeBlocks,
                        durationText = if (obj.has("durationText")) obj.getString("durationText") else null,
                        actionSummary = if (obj.has("actionSummary")) obj.getString("actionSummary") else null,
                        steps = steps
                    )
                )
            }
            return result
        } catch (e: Exception) {
            Log.w("ConversationPersistence", "Failed loading messages for conversation $conversationId: ${e.message}")
            return emptyList()
        }
    }

    fun deleteConversationFile(context: Context, conversationId: String) {
        try {
            val file = File(File(context.filesDir, "conversations"), "$conversationId.json")
            if (file.exists()) file.delete()
        } catch (e: Exception) {}
    }
}
