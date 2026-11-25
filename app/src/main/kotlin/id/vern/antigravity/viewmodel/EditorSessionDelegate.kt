package id.vern.antigravity.viewmodel

import android.content.Context
import android.widget.Toast
import id.vern.antigravity.R
import id.vern.antigravity.data.OpenTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

class EditorSessionDelegate {
    private val mutableOpenTabs = MutableStateFlow<List<OpenTab>>(emptyList())
    val openTabs: StateFlow<List<OpenTab>> = mutableOpenTabs.asStateFlow()

    private val mutableActiveTabId = MutableStateFlow<String?>(null)
    val activeTabId: StateFlow<String?> = mutableActiveTabId.asStateFlow()

    fun openFile(file: File) {
        val existing = mutableOpenTabs.value.find { it.file.absolutePath == file.absolutePath }
        if (existing != null) {
            mutableActiveTabId.value = existing.id
            return
        }

        val content = try {
            if (file.exists() && file.isFile) {
                if (file.length() < 2 * 1024 * 1024) file.readText() else ""
            } else ""
        } catch (_: Exception) {
            ""
        }

        val tab = OpenTab(
            id = file.absolutePath,
            file = file,
            title = file.name,
            content = content,
            originalContent = content
        )

        mutableOpenTabs.update { it + tab }
        mutableActiveTabId.value = tab.id
    }

    fun closeTab(tabId: String) {
        val current = mutableOpenTabs.value
        val tabToClose = current.find { it.id == tabId } ?: return
        val updated = current.filter { it.id != tabId }
        mutableOpenTabs.value = updated
        if (mutableActiveTabId.value == tabId) {
            mutableActiveTabId.value = updated.lastOrNull()?.id
        }
    }

    fun selectTab(tabId: String) {
        if (mutableOpenTabs.value.any { it.id == tabId }) {
            mutableActiveTabId.value = tabId
        }
    }

    fun updateContent(tabId: String, newContent: String) {
        mutableOpenTabs.update { tabs ->
            tabs.map { tab ->
                if (tab.id == tabId) {
                    tab.copy(content = newContent, isDirty = newContent != tab.originalContent)
                } else {
                    tab
                }
            }
        }
    }

    fun setDiffView(tabId: String, isDiff: Boolean) {
        mutableOpenTabs.update { tabs ->
            tabs.map { tab ->
                if (tab.id == tabId) {
                    tab.copy(isDiffView = isDiff)
                } else {
                    tab
                }
            }
        }
    }

    fun saveTab(context: Context, tabId: String): Boolean {
        val tab = mutableOpenTabs.value.find { it.id == tabId } ?: return false
        return try {
            tab.file.writeText(tab.content)
            mutableOpenTabs.update { tabs ->
                tabs.map { t ->
                    if (t.id == tabId) {
                        t.copy(originalContent = t.content, isDirty = false)
                    } else {
                        t
                    }
                }
            }
            Toast.makeText(context, context.getString(R.string.file_saved_success, tab.file.name), Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.file_saved_error, tab.file.name, e.message ?: ""), Toast.LENGTH_SHORT).show()
            false
        }
    }
}
