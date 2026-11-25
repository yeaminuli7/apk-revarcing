package id.vern.antigravity.util

import android.content.Context
import android.os.Environment
import java.io.File

object FileResolver {
    fun resolveTargetFile(
        pathOrName: String,
        workspacePath: String?,
        projectList: List<File>,
        context: Context
    ): File? {
        val trimmed = pathOrName.trim().removeSurrounding("\"").removeSurrounding("'")
        if (trimmed.isBlank()) return null
        val direct = File(trimmed)
        if (direct.isAbsolute && direct.isFile && direct.exists()) {
            return direct
        }

        if (!workspacePath.isNullOrBlank()) {
            val wsDir = File(workspacePath)
            if (wsDir.isDirectory) {
                val candidate = File(wsDir, trimmed)
                if (candidate.isFile && candidate.exists()) return candidate
                val found = wsDir.walkTopDown().maxDepth(8).firstOrNull {
                    it.isFile && (it.name.equals(direct.name, ignoreCase = true) || it.absolutePath.endsWith(trimmed))
                }
                if (found != null) return found
            }
        }

        for (pDir in projectList) {
            if (pDir.isDirectory) {
                val candidate = File(pDir, trimmed)
                if (candidate.isFile && candidate.exists()) return candidate
                val found = pDir.walkTopDown().maxDepth(8).firstOrNull {
                    it.isFile && (it.name.equals(direct.name, ignoreCase = true) || it.absolutePath.endsWith(trimmed))
                }
                if (found != null) return found
            }
        }

        val commonRoots = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            File(context.filesDir, "projects"),
            File(context.filesDir, "home")
        )
        for (root in commonRoots) {
            if (root.isDirectory) {
                val found = root.walkTopDown().maxDepth(8).firstOrNull {
                    it.isFile && (it.name.equals(direct.name, ignoreCase = true) || it.absolutePath.endsWith(trimmed))
                }
                if (found != null) return found
            }
        }

        return direct
    }
}
