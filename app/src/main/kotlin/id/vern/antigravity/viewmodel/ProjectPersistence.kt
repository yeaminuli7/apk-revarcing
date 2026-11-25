package id.vern.antigravity.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import id.vern.antigravity.data.ProjectSortOrder
import java.io.File

object ProjectPersistence {
    private const val PREFS_NAME = "antigravity_prefs"
    private const val KEY_USER_PROJECTS = "ag_user_projects"
    private const val KEY_PROJECT_SORT = "ag_project_sort"

    fun sortProjects(list: List<File>, order: ProjectSortOrder): List<File> {
        return when (order) {
            ProjectSortOrder.NAME_ASC -> list.sortedBy { it.name.lowercase() }
            ProjectSortOrder.NAME_DESC -> list.sortedByDescending { it.name.lowercase() }
            ProjectSortOrder.DATE_DESC -> list.sortedByDescending { it.lastModified() }
            ProjectSortOrder.DATE_ASC -> list.sortedBy { it.lastModified() }
        }
    }

    fun loadProjects(context: Context, order: ProjectSortOrder): List<File> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedPaths = prefs.getStringSet(KEY_USER_PROJECTS, null)
        val list = mutableListOf<File>()
        if (savedPaths != null) {
            val defaultDocsPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath
            for (path in savedPaths) {
                if (path == defaultDocsPath) continue
                val f = File(path)
                if (f.exists() && f.isDirectory) {
                    list.add(f)
                }
            }
        }
        return sortProjects(list, order)
    }

    fun saveProjects(context: Context, list: List<File>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val paths = list.map { it.absolutePath }.toSet()
        prefs.edit().putStringSet(KEY_USER_PROJECTS, paths).apply()
    }

    fun saveProjectSortOrder(context: Context, order: ProjectSortOrder) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PROJECT_SORT, order.name).apply()
    }

    fun resolveTreeUriToFolder(context: Context, treeUri: Uri): File? {
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) {}

        var resolvedPath: String? = null
        try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val split = docId.split(":")
            val type = split.getOrNull(0) ?: ""
            val relPath = if (split.size > 1) split[1] else ""
            if (type.equals("primary", ignoreCase = true)) {
                resolvedPath = if (relPath.isNotEmpty()) {
                    "${Environment.getExternalStorageDirectory().absolutePath}/$relPath"
                } else {
                    Environment.getExternalStorageDirectory().absolutePath
                }
            } else if (type.isNotEmpty()) {
                resolvedPath = "/storage/$type/$relPath"
            }
        } catch (e: Exception) {}

        if (resolvedPath == null) {
            val path = treeUri.path ?: ""
            if (path.contains("primary:")) {
                val sub = path.substringAfter("primary:")
                resolvedPath = "${Environment.getExternalStorageDirectory().absolutePath}/$sub"
            }
        }

        return resolvedPath?.let { File(it) }
    }

    fun createProjectInFolder(name: String, parentDir: File): File? {
        val sanitized = name.trim().replace(Regex("[^a-zA-Z0-9._ -]"), "_")
        if (sanitized.isEmpty()) return null
        if (!parentDir.exists()) {
            parentDir.mkdirs()
        }
        val newDir = File(parentDir, sanitized)
        if (!newDir.exists()) {
            newDir.mkdirs()
        }
        return newDir
    }

    fun createProjectFolder(name: String, targetBase: File? = null): File? {
        val baseDir = targetBase
            ?: (Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) ?: Environment.getExternalStorageDirectory()).apply { mkdirs() }
        return createProjectInFolder(name, baseDir)
    }
}
