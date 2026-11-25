package id.vern.antigravity.data

import android.net.Uri
import androidx.annotation.StringRes
import id.vern.antigravity.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

enum class MessageStatus {
    SENDING,
    SENT,
    ERROR
}

data class AttachedFile(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val localPath: String? = null
) {
    val size: Long get() = sizeBytes

    val formattedSize: String
        get() {
            if (sizeBytes < 1024) return "$sizeBytes B"
            val kb = sizeBytes / 1024.0
            if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
            val mb = kb / 1024.0
            return String.format(Locale.US, "%.1f MB", mb)
        }
}

enum class AgentStepKind {
    THOUGHT,
    ANALYZED,
    EDITED,
    SEARCH,
    COMMAND,
    WORKING
}

data class AgentExecutionStep(
    val id: String = UUID.randomUUID().toString(),
    val kind: AgentStepKind,
    val title: String,
    val detail: String? = null,
    val lines: String? = null,
    val diff: String? = null,
    val durationSeconds: Int? = null,
    val isCompleted: Boolean = true,
    val filePath: String? = null
)

data class DaemonTask(
    val id: String,
    val name: String,
    val command: String,
    val triggerType: String = "interval",
    val intervalSec: Int = 0,
    val cron: String = "",
    val enabled: Boolean = true,
    val lastStatus: String = "idle"
)

data class QueuedMessage(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val attachments: List<AttachedFile> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

data class RunningTask(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val command: String,
    val startTime: Long = System.currentTimeMillis()
)

data class RunningSubagent(
    val id: String = UUID.randomUUID().toString(),
    val conversationId: String? = null,
    val typeName: String = "",
    val role: String = "",
    val prompt: String = "",
    val isRunning: Boolean = true,
    val startTime: Long = System.currentTimeMillis()
)

data class ChangedFileDetail(
    val name: String,
    val relativePath: String,
    val status: String = "M",
    val additions: Int = 0,
    val deletions: Int = 0,
    val diff: String = ""
)

data class TurnFileChanges(
    val totalFiles: Int = 0,
    val totalAdditions: Int = 0,
    val totalDeletions: Int = 0,
    val files: List<ChangedFileDetail> = emptyList()
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENT,
    val attachments: List<AttachedFile> = emptyList(),
    val thinking: String? = null,
    val commandRun: String? = null,
    val commandOutput: String? = null,
    val codeBlocks: List<String> = emptyList(),
    val steps: List<AgentExecutionStep> = emptyList(),
    val durationText: String? = null,
    val actionSummary: String? = null,
    val fileChanges: TurnFileChanges? = null
) {
    val attachedFiles: List<AttachedFile> get() = attachments

    val formattedTime: String
        get() {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class AccentColor(@StringRes val titleRes: Int, val colorHex: Long) {
    DEFAULT(R.string.accent_default, 0xFF0B57D0),
    BLUE(R.string.accent_blue, 0xFF2979FF),
    RED(R.string.accent_red, 0xFFE53935),
    YELLOW(R.string.accent_yellow, 0xFFF59E0B),
    GREEN(R.string.accent_green, 0xFF10B981),
    PURPLE(R.string.accent_purple, 0xFF8B5CF6);

    val displayName: String
        get() = name
}

data class AppearanceConfig(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentColor: AccentColor = AccentColor.BLUE
)

data class ExecutionLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val tag: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false
) {
    val formattedTime: String
        get() {
            val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
}

data class EngineStatus(
    val isRunning: Boolean = false,
    val pid: Int = -1,
    val httpsPort: Int = 45158,
    val gatewayPort: Int = 45161,
    val csrfToken: String = "",
    val serverUrl: String? = null,
    val daemonRunning: Boolean = false
)

enum class ProjectSortOrder(val displayName: String) {
    NAME_ASC("Name (A to Z)"),
    NAME_DESC("Name (Z to A)"),
    DATE_DESC("Date Modified (Newest)"),
    DATE_ASC("Date Modified (Oldest)")
}

enum class ThemePreset(
    val id: String,
    val displayName: String,
    val bgHex: String,
    val fgHex: String,
    val accentHex: String
) {
    DEFAULT_DARK("default_dark", "Default Dark", "#101010", "#CCCCCC", "#007ACC"),
    DEFAULT_LIGHT("default_light", "Default Light", "#FFFFFF", "#101010", "#007ACC"),
    HIGH_CONTRAST("high_contrast", "High Contrast", "#000000", "#FFFFFF", "#00FFFF"),
    CYBERPUNK("cyberpunk", "Cyberpunk", "#0B001A", "#00FFCC", "#FF0055"),
    SOLARIZED("solarized", "Solarized Dark", "#002B36", "#839496", "#268BD2"),
    MONOKAI("monokai", "Monokai Pro", "#2D2A2E", "#FCFCFA", "#FFD866"),
    CUSTOM("custom", "Custom", "#101010", "#CCCCCC", "#1A73E8")
}

