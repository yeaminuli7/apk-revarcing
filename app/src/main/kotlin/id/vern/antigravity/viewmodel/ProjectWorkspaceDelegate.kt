package id.vern.antigravity.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Environment
import id.vern.antigravity.data.ProjectSortOrder
import id.vern.antigravity.service.TerminalSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

class ProjectWorkspaceDelegate {
    private val mutableProjectList = MutableStateFlow<List<File>>(emptyList())
    val projectList: StateFlow<List<File>> = mutableProjectList.asStateFlow()

    private val mutableProjectSortOrder = MutableStateFlow(ProjectSortOrder.NAME_ASC)
    val projectSortOrder: StateFlow<ProjectSortOrder> = mutableProjectSortOrder.asStateFlow()

    private val mutableWorkspacePath = MutableStateFlow("")
    val workspacePath: StateFlow<String> = mutableWorkspacePath.asStateFlow()

    fun loadProjects(context: Context) {
        val sorted = ProjectPersistence.loadProjects(context, mutableProjectSortOrder.value)
        mutableProjectList.value = sorted
    }

    fun saveProjects(context: Context, list: List<File>) {
        ProjectPersistence.saveProjects(context, list)
    }

    fun addProject(context: Context, path: String, onLog: ((String, String) -> Unit)? = null): File {
        val f = File(path)
        if (!f.exists()) f.mkdirs()
        val currentList = mutableProjectList.value.toMutableList()
        if (currentList.none { it.absolutePath == f.absolutePath }) {
            currentList.add(f)
            val sorted = ProjectPersistence.sortProjects(currentList, mutableProjectSortOrder.value)
            mutableProjectList.value = sorted
            saveProjects(context, sorted)
        }
        setWorkspacePath(context, f.absolutePath, onLog)
        onLog?.invoke("Workspace", "Added project: ${f.name} (${f.absolutePath})")
        return f
    }

    fun removeProject(context: Context, path: String, onLog: ((String, String) -> Unit)? = null) {
        val currentList = mutableProjectList.value.filter { it.absolutePath != path }
        mutableProjectList.value = currentList
        saveProjects(context, currentList)
        if (mutableWorkspacePath.value == path) {
            val nextPath = currentList.firstOrNull()?.absolutePath ?: ""
            setWorkspacePath(context, nextPath, onLog)
        }
        onLog?.invoke("Workspace", "Removed project from list: $path")
    }

    fun resolveTreeUriToFolder(context: Context, treeUri: Uri): File? =
        ProjectPersistence.resolveTreeUriToFolder(context, treeUri)

    fun addProjectFromTreeUri(context: Context, treeUri: Uri, onLog: ((String, String) -> Unit)? = null): File? {
        val folder = resolveTreeUriToFolder(context, treeUri)
        if (folder != null) {
            if (!folder.exists()) {
                folder.mkdirs()
            }
            return addProject(context, folder.absolutePath, onLog)
        }
        return null
    }

    fun setProjectSortOrder(context: Context, order: ProjectSortOrder) {
        mutableProjectSortOrder.value = order
        ProjectPersistence.saveProjectSortOrder(context, order)
        mutableProjectList.update { ProjectPersistence.sortProjects(it, order) }
    }

    fun createProjectInFolder(context: Context, name: String, parentDir: File, onLog: ((String, String) -> Unit)? = null): File? {
        val newDir = ProjectPersistence.createProjectInFolder(name, parentDir) ?: return null
        val added = addProject(context, newDir.absolutePath, onLog)
        onLog?.invoke("Workspace", "Created project folder: ${added.name} in ${parentDir.absolutePath}")
        return added
    }

    fun createProjectFolder(context: Context, name: String, targetBase: File? = null, onLog: ((String, String) -> Unit)? = null): File? {
        val baseDir = targetBase
            ?: (Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) ?: Environment.getExternalStorageDirectory()).apply { mkdirs() }
        return createProjectInFolder(context, name, baseDir, onLog)
    }

    fun setWorkspacePath(context: Context, path: String, onLog: ((String, String) -> Unit)? = null) {
        mutableWorkspacePath.value = path
        val prefs = context.getSharedPreferences(AntigravityViewModel.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(AntigravityViewModel.KEY_WORKSPACE_PATH, path).apply()
        TerminalSessionManager.currentWorkingDir = File(path)
        onLog?.invoke("Workspace", "Active directory changed: $path")
    }

    fun setWorkspacePathDirect(path: String) {
        mutableWorkspacePath.value = path
        if (path.isNotBlank()) {
            TerminalSessionManager.currentWorkingDir = File(path)
        }
    }
}
