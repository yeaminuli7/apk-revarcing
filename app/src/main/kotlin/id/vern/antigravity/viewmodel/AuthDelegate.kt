package id.vern.antigravity.viewmodel

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Authentication delegate.
 *
 * Google OAuth / account switching has been removed from this build on purpose:
 * the only supported credential is a Gemini API key. Everything here is therefore
 * limited to the API-key dialog and the accessibility-setup dialog.
 */
class AuthDelegate {

    private val mutableShowApiKeyDialog = MutableStateFlow(false)
    val showApiKeyDialog: StateFlow<Boolean> = mutableShowApiKeyDialog.asStateFlow()

    private val mutableShowAccessibilityDialog = MutableStateFlow(false)
    val showAccessibilityDialog: StateFlow<Boolean> = mutableShowAccessibilityDialog.asStateFlow()

    fun showApiKeyDialog() {
        mutableShowApiKeyDialog.value = true
    }

    fun dismissApiKeyDialog() {
        mutableShowApiKeyDialog.value = false
    }

    fun showAccessibilityDialog() {
        mutableShowAccessibilityDialog.value = true
    }

    fun dismissAccessibilityDialog() {
        mutableShowAccessibilityDialog.value = false
    }

    /**
     * Kept as a no-op so existing call sites stay valid: there is no session to
     * refresh when the app is driven purely by an API key.
     */
    fun refreshAuthState(
        context: Context,
        onRefreshUserQuota: (Context) -> Unit
    ) {
        try {
            onRefreshUserQuota(context)
        } catch (e: Exception) {
        }
    }
}
