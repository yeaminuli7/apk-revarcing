package id.vern.antigravity.data

import java.io.File
import java.util.Locale

data class ProjectFile(
    val name: String,
    val file: File,
    val isDirectory: Boolean,
    val sizeBytes: Long = 0L,
    val lastModified: Long = 0L,
    val children: List<ProjectFile> = emptyList(),
    val isExpanded: Boolean = false
) {
    val absolutePath: String get() = file.absolutePath
    val extension: String get() = file.extension.lowercase(Locale.ROOT)

    val formattedSize: String
        get() {
            if (isDirectory) return ""
            if (sizeBytes < 1024) return "$sizeBytes B"
            val kb = sizeBytes / 1024.0
            if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
            val mb = kb / 1024.0
            return String.format(Locale.US, "%.1f MB", mb)
        }
}

data class OpenTab(
    val id: String,
    val file: File,
    val title: String,
    var content: String,
    var originalContent: String = "",
    var isDirty: Boolean = false,
    val isDiffView: Boolean = false
)

data class ChangedFileItem(
    val name: String,
    val relativePath: String,
    val isKotlin: Boolean = false
)

data class ArtifactFileItem(
    val name: String,
    val filePath: String,
    val byteSize: Long
)

