package id.vern.antigravity.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import id.vern.antigravity.R
import id.vern.antigravity.oauth.StoredAccount
import id.vern.antigravity.oauth.AccountManager
import id.vern.antigravity.oauth.OAuthManager
import id.vern.antigravity.executor.EngineExecutor
import id.vern.antigravity.service.CloudCodeQuotaService
import id.vern.antigravity.service.AgyModelsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthDelegate {
    private val mutableUserEmail = MutableStateFlow<String?>(null)
    val userEmail: StateFlow<String?> = mutableUserEmail.asStateFlow()

    private val mutableUserName = MutableStateFlow("")
    val userName: StateFlow<String> = mutableUserName.asStateFlow()

    private val mutableUserPicture = MutableStateFlow<String?>(null)
    val userPicture: StateFlow<String?> = mutableUserPicture.asStateFlow()

    private val mutableAccounts = MutableStateFlow<List<StoredAccount>>(emptyList())
    val accounts: StateFlow<List<StoredAccount>> = mutableAccounts.asStateFlow()

    private val mutableIsAuthenticating = MutableStateFlow(false)
    val isAuthenticating: StateFlow<Boolean> = mutableIsAuthenticating.asStateFlow()

    private val mutableAuthError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = mutableAuthError.asStateFlow()

    private val mutableShowGoogleSignInDialog = MutableStateFlow(false)
    val showGoogleSignInDialog: StateFlow<Boolean> = mutableShowGoogleSignInDialog.asStateFlow()

    private val mutableShowApiKeyDialog = MutableStateFlow(false)
    val showApiKeyDialog: StateFlow<Boolean> = mutableShowApiKeyDialog.asStateFlow()

    private val mutableShowAccessibilityDialog = MutableStateFlow(false)
    val showAccessibilityDialog: StateFlow<Boolean> = mutableShowAccessibilityDialog.asStateFlow()

    private val mutablePendingAuthUrl = MutableStateFlow<String?>(null)
    val pendingAuthUrl: StateFlow<String?> = mutablePendingAuthUrl.asStateFlow()

    fun showGoogleSignInDialog() {
        mutableShowGoogleSignInDialog.value = true
    }

    fun dismissGoogleSignInDialog() {
        mutableShowGoogleSignInDialog.value = false
    }

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

    fun setPendingAuthUrl(url: String?) {
        mutablePendingAuthUrl.value = url
    }

    fun refreshAuthState(
        context: Context,
        scope: CoroutineScope,
        onRefreshUserQuota: (Context) -> Unit
    ) {
        OAuthManager.checkAndImportExistingToken(context)
        val allAccounts = AccountManager.getAccounts(context)
        mutableAccounts.value = allAccounts
        val email = OAuthManager.getUserEmail(context)
        mutableUserEmail.value = email
        val name = OAuthManager.getUserName(context) ?: if (email != null) {
            email.substringBefore("@").replace(".", " ").split(" ")
                .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
        } else ""
        mutableUserName.value = name
        val pic = OAuthManager.getUserPicture(context)
        if (!pic.isNullOrBlank()) {
            mutableUserPicture.value = pic
        }

        onRefreshUserQuota(context)

        scope.launch {
            if (OAuthManager.refreshTokenIfNeeded(context)) {
                val newEmail = OAuthManager.getUserEmail(context)
                mutableUserEmail.value = newEmail
                val newName = OAuthManager.getUserName(context) ?: if (newEmail != null) {
                    newEmail.substringBefore("@").replace(".", " ").split(" ")
                        .joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
                } else ""
                mutableUserName.value = newName
                val refreshedPic = OAuthManager.getUserPicture(context)
                if (!refreshedPic.isNullOrBlank()) {
                    mutableUserPicture.value = refreshedPic
                }
                if (newEmail != null) {
                    onRefreshUserQuota(context)
                }
            }
            val userInfo = OAuthManager.fetchUserInfo(context)
            if (userInfo != null) {
                if (!userInfo.name.isNullOrBlank()) {
                    mutableUserName.value = userInfo.name
                }
                if (!userInfo.picture.isNullOrBlank()) {
                    mutableUserPicture.value = userInfo.picture
                }
                if (!userInfo.email.isNullOrBlank()) {
                    mutableUserEmail.value = userInfo.email
                }
            }
            mutableAccounts.value = AccountManager.getAccounts(context)
        }
    }

    fun switchAccount(
        context: Context,
        accountId: String,
        scope: CoroutineScope,
        onAccountSwitched: () -> Unit
    ) {
        scope.launch {
            val success = AccountManager.switchAccount(context, accountId)
            if (success) {
                AgyModelsService.invalidateCache()
                val prefs = context.getSharedPreferences(AntigravityViewModel.PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit()
                    .remove(AntigravityViewModel.KEY_CLI_MODELS_FETCHED_AT)
                    .remove(AntigravityViewModel.KEY_CLI_MODELS_JSON)
                    .remove(AntigravityViewModel.KEY_SELECTED_MODEL_CLI_ID)
                    .apply()
                onAccountSwitched()
            }
        }
    }

    fun removeAccount(
        context: Context,
        accountId: String,
        scope: CoroutineScope,
        onAccountRemoved: () -> Unit
    ) {
        scope.launch {
            val success = AccountManager.removeAccount(context, accountId)
            if (success) {
                val currentActive = AccountManager.getActiveAccount(context)
                if (currentActive != null) {
                    AgyModelsService.invalidateCache()
                    val prefs = context.getSharedPreferences(AntigravityViewModel.PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit()
                        .remove(AntigravityViewModel.KEY_CLI_MODELS_FETCHED_AT)
                        .remove(AntigravityViewModel.KEY_CLI_MODELS_JSON)
                        .remove(AntigravityViewModel.KEY_SELECTED_MODEL_CLI_ID)
                        .apply()
                }
                onAccountRemoved()
            }
        }
    }

    fun startGoogleSignIn(
        context: Context,
        scope: CoroutineScope,
        onSuccess: (String?) -> Unit,
        onLog: (String, String, Boolean) -> Unit
    ) {
        val pendingUrl = mutablePendingAuthUrl.value
        if (!pendingUrl.isNullOrBlank()) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(pendingUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                onLog("Auth", "Opening official engine PKCE auth URL in browser...", false)
                mutableShowGoogleSignInDialog.value = true
                return
            } catch (e: Exception) {
                onLog("Auth", "Failed to open auth URL: ${e.message}", true)
            }
        }

        if (mutableIsAuthenticating.value) {
            mutableShowGoogleSignInDialog.value = true
            return
        }

        scope.launch {
            mutableIsAuthenticating.value = true
            mutableAuthError.value = null
            mutableShowGoogleSignInDialog.value = true
            onLog("Auth", "Initiating Google OAuth flow on port 8085...", false)

            OAuthManager.startBrowserAuth(
                context = context,
                onSuccess = { email ->
                    mutableUserEmail.value = email
                    mutableIsAuthenticating.value = false
                    mutableShowGoogleSignInDialog.value = false
                    mutablePendingAuthUrl.value = null
                    onSuccess(email)
                },
                onError = { err ->
                    mutableAuthError.value = err
                    mutableIsAuthenticating.value = false
                    onLog("Auth", "Google sign-in error: $err", true)
                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    fun onActivityResume(
        context: Context,
        scope: CoroutineScope,
        onCheckUpdates: (Context) -> Unit,
        onAccountChangeDetected: (String) -> Unit
    ) {
        onCheckUpdates(context)
        OAuthManager.checkAndImportExistingToken(context)
        val email = OAuthManager.getUserEmail(context)
        if (email != null && email != mutableUserEmail.value) {
            mutableUserEmail.value = email
            onAccountChangeDetected(email)
        } else if (email != null) {
            if (mutableUserPicture.value.isNullOrBlank()) {
                val pic = OAuthManager.getUserPicture(context)
                if (!pic.isNullOrBlank()) {
                    mutableUserPicture.value = pic
                } else {
                    scope.launch {
                        val userInfo = OAuthManager.fetchUserInfo(context)
                        if (!userInfo?.picture.isNullOrBlank()) {
                            mutableUserPicture.value = userInfo?.picture
                        }
                    }
                }
            }
        }
    }

    fun exchangeManualAuthCode(
        context: Context,
        rawCodeOrToken: String,
        scope: CoroutineScope,
        onStdinSent: () -> Unit,
        onSuccess: (String?) -> Unit,
        onLog: (String, String, Boolean) -> Unit
    ) {
        var trimmed = rawCodeOrToken.trim()
        if (trimmed.isEmpty()) return

        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            try {
                val uri = Uri.parse(trimmed)
                val codeParam = uri.getQueryParameter("code")
                if (!codeParam.isNullOrBlank()) {
                    trimmed = codeParam
                }
            } catch (_: Exception) {}
        }
        if (trimmed.contains("%")) {
            try {
                trimmed = java.net.URLDecoder.decode(trimmed, "UTF-8")
            } catch (_: Exception) {}
        }

        val sent = EngineExecutor.writeToAgyStdin(trimmed)
        if (sent) {
            mutableShowGoogleSignInDialog.value = false
            mutablePendingAuthUrl.value = null
            onLog("Auth", "Submitted authorization code directly to active engine stdin", false)
            Toast.makeText(context, context.getString(R.string.auth_verifying_code), Toast.LENGTH_SHORT).show()
            onStdinSent()
            return
        }

        scope.launch {
            mutableIsAuthenticating.value = true
            mutableAuthError.value = null
            onLog("Auth", "Processing authentication code/token...", false)

            OAuthManager.exchangeCodeDirectly(
                context = context,
                rawInput = trimmed,
                onSuccess = { email ->
                    mutableUserEmail.value = email
                    mutableIsAuthenticating.value = false
                    mutableShowGoogleSignInDialog.value = false
                    mutablePendingAuthUrl.value = null
                    onSuccess(email)
                },
                onError = { err ->
                    mutableAuthError.value = err
                    mutableIsAuthenticating.value = false
                    onLog("Auth", "Google sign-in error: $err", true)
                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    fun signOutGoogle(
        context: Context,
        onSignedOut: () -> Unit,
        onLog: (String, String) -> Unit
    ) {
        OAuthManager.clearToken(context)
        mutableUserEmail.value = null
        mutableUserName.value = ""
        mutableUserPicture.value = null

        val prefs = context.getSharedPreferences(AntigravityViewModel.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(AntigravityViewModel.KEY_SELECTED_MODEL)
            .remove(AntigravityViewModel.KEY_SELECTED_MODEL_CLI_ID)
            .remove(AntigravityViewModel.KEY_THINKING_BUDGET)
            .remove(AntigravityViewModel.KEY_CLI_MODELS_JSON)
            .remove(AntigravityViewModel.KEY_CLI_MODELS_FETCHED_AT)
            .remove(OAuthManager.KEY_USER_NAME)
            .remove(OAuthManager.KEY_USER_PICTURE)
            .remove(OAuthManager.KEY_USER_EMAIL)
            .apply()

        onSignedOut()
        onLog("Auth", "Signed out of Google account")
        Toast.makeText(context, context.getString(R.string.account_toast_signed_out), Toast.LENGTH_SHORT).show()
    }
}
