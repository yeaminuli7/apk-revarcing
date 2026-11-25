package id.vern.antigravity.viewmodel

import android.content.Context
import id.vern.antigravity.data.ChangedFileItem
import id.vern.antigravity.executor.EngineExecutor
import id.vern.antigravity.util.NetworkDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

class GitStatusDelegate {
    private val mutableChangedFiles = MutableStateFlow<List<ChangedFileItem>>(emptyList())
    val changedFiles: StateFlow<List<ChangedFileItem>> = mutableChangedFiles.asStateFlow()

    private val mutableDiffCache = MutableStateFlow<Map<String, String>>(emptyMap())
    val diffCache: StateFlow<Map<String, String>> = mutableDiffCache.asStateFlow()

    private val mutableLoadingDiffs = MutableStateFlow<Set<String>>(emptySet())
    val loadingDiffs: StateFlow<Set<String>> = mutableLoadingDiffs.asStateFlow()

    suspend fun fetchGitChangedFiles(context: Context, workspacePath: String?): List<ChangedFileItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ChangedFileItem>()
        val rootDir = workspacePath?.let { File(it) }
        if (rootDir != null && rootDir.exists() && rootDir.isDirectory && File(rootDir, ".git").exists()) {
            try {
                val porcelainRegex = Regex("""^[ MADRCU?!]{1,2}\s+(.+)$""")
                EngineExecutor.executeCommand(context, "cd '${rootDir.absolutePath}' && git status --porcelain 2>/dev/null") { line, _ ->
                    val trimmed = line.trim()
                    val match = porcelainRegex.find(trimmed)
                    if (match != null) {
                        val rawPath = NetworkDownloader.cleanPath(match.groupValues[1])
                        val f = File(rawPath)
                        val name = f.name
                        val rel = f.parent ?: ""
                        list.add(ChangedFileItem(name = name, relativePath = rel, isKotlin = name.endsWith(".kt") || name.endsWith(".kts")))
                    }
                }
            } catch (_: Exception) {}
        }
        mutableChangedFiles.value = list
        list
    }

    suspend fun fetchGitDiff(context: Context, workspacePath: String?, relativePath: String): String = withContext(Dispatchers.IO) {
        val cached = mutableDiffCache.value[relativePath]
        if (cached != null) return@withContext cached

        mutableLoadingDiffs.value = mutableLoadingDiffs.value + relativePath
        val root = workspacePath?.let { File(it) }
        if (root == null || !root.exists()) {
            mutableLoadingDiffs.value = mutableLoadingDiffs.value - relativePath
            return@withContext ""
        }
        val diffBuilder = StringBuilder()
        try {
            EngineExecutor.executeCommand(context, "cd '${root.absolutePath}' && git diff HEAD -- '$relativePath' 2>/dev/null") { line, _ ->
                diffBuilder.append(line).append("\n")
            }
        } catch (_: Exception) {}
        val diff = diffBuilder.toString()
        val result = if (diff.isNotBlank()) {
            diff
        } else {
            val targetF = File(root, relativePath)
            if (targetF.exists() && targetF.isFile) {
                targetF.readLines().take(40).joinToString("\n")
            } else {
                "[File modified or newly created]"
            }
        }
        mutableDiffCache.value = mutableDiffCache.value + (relativePath to result)
        mutableLoadingDiffs.value = mutableLoadingDiffs.value - relativePath
        result
    }

    fun clearDiffCache() {
        mutableDiffCache.value = emptyMap()
        mutableLoadingDiffs.value = emptySet()
    }
}
