package id.vern.antigravity.viewmodel

import android.content.Context
import id.vern.antigravity.data.ArtifactFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

class ArtifactManagerDelegate {
    private val mutableConversationArtifacts = MutableStateFlow<List<ArtifactFileItem>>(emptyList())
    val conversationArtifacts: StateFlow<List<ArtifactFileItem>> = mutableConversationArtifacts.asStateFlow()

    suspend fun refreshArtifacts(context: Context, conversationId: String? = null) = withContext(Dispatchers.IO) {
        val results = mutableListOf<ArtifactFileItem>()
        val candidateDirs = mutableListOf<File>()

        if (!conversationId.isNullOrBlank()) {
            candidateDirs.add(File(context.filesDir, ".gemini/antigravity-app/brain/$conversationId"))
            candidateDirs.add(File(context.filesDir, "home/.gemini/antigravity-app/brain/$conversationId"))
        }
        candidateDirs.add(File(context.filesDir, ".gemini/antigravity-app/brain"))
        candidateDirs.add(File(context.filesDir, "home/.gemini/antigravity-app/brain"))

        val visitedPaths = mutableSetOf<String>()
        for (dir in candidateDirs) {
            if (dir.exists() && dir.isDirectory) {
                dir.walkTopDown().maxDepth(3).filter {
                    it.isFile && !it.name.startsWith(".") && it.extension in listOf("md", "json", "png", "jpg", "html")
                }.forEach { file ->
                    if (visitedPaths.add(file.absolutePath)) {
                        results.add(ArtifactFileItem(file.name, file.absolutePath, file.length()))
                    }
                }
            }
        }
        mutableConversationArtifacts.value = results.sortedByDescending { it.byteSize }.take(50)
    }
}
