package id.vern.antigravity.oauth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import id.vern.antigravity.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

data class UserInfoResult(
    val name: String?,
    val picture: String?,
    val email: String?
)

object OAuthManager {

    private const val TAG = "OAuthManager"

    const val PREFS_NAME = "antigravity_prefs"
    const val KEY_USER_NAME = "ag_user_name"
    const val KEY_USER_PICTURE = "ag_user_picture"
    const val KEY_USER_EMAIL = "ag_user_email"

    // Google OAuth desktop client used by the Antigravity agent core.
    // Values come from build-time configuration (gradle property, env var or
    // CI secret) so that no credentials live in source control.
    // See app/build.gradle.kts -> defaultConfig -> buildConfigField.
    const val CLIENT_ID = id.vern.antigravity.BuildConfig.OAUTH_CLIENT_ID
    const val CLIENT_SECRET = id.vern.antigravity.BuildConfig.OAUTH_CLIENT_SECRET
    const val SCOPES = "openid https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/userinfo.profile https://www.googleapis.com/auth/cloud-platform https://www.googleapis.com/auth/cclog https://www.googleapis.com/auth/experimentsandconfigs https://www.googleapis.com/auth/aicode"
    const val TOKEN_DIR = ".gemini"
    const val CLI_TOKEN_FILE_NAME = "antigravity-oauth-token"
    const val JETSKI_TOKEN_FILE_NAME = "jetski-standalone-oauth-token"
    const val TOKEN_FILE_NAME = CLI_TOKEN_FILE_NAME
    const val CALLBACK_PORT = 8085
    const val JETSKI_STATE_CONTENT = """post_onboarding:  {
  completed_steps:  POST_ONBOARDING_STEP_TYPE_MANAGER_WELCOME
  completed_steps:  POST_ONBOARDING_STEP_TYPE_USAGE_MODE
  completed_steps:  POST_ONBOARDING_STEP_TYPE_AGENT_CONFIGURATION
  completed_steps:  POST_ONBOARDING_STEP_TYPE_ADD_WORKSPACE
}
installation_uuid:  "9fc7cbce-f730-499f-b7eb-4aa982fc1cac"
migrations:  {
  key:  3
  value:  MIGRATION_STATUS_COMPLETED
}
migrations:  {
  key:  4
  value:  MIGRATION_STATUS_COMPLETED
}
migrations:  {
  key:  5
  value:  MIGRATION_STATUS_COMPLETED
}
"""
    const val INSTALLATION_ID = "52dc9f7e-08b5-45e6-8c2d-9c8f615b773f"

    @Volatile
    private var lastTokenCheckTimeMs: Long = 0L
    @Volatile
    private var cachedHasToken: Boolean? = null
    @Volatile
    private var cachedTokenFile: File? = null

    fun invalidateCache() {
        cachedHasToken = null
        cachedTokenFile = null
        lastTokenCheckTimeMs = 0L
    }

    fun getTargetDirs(context: Context): List<File> {
        val filesDir = context.filesDir
        val homeDir = File(filesDir, "home")
        val dirs = mutableListOf(
            File(filesDir, TOKEN_DIR),
            File(filesDir, ".gemini/antigravity-cli"),
            File(filesDir, ".gemini/antigravity-app"),
            File(homeDir, TOKEN_DIR),
            File(homeDir, ".gemini/antigravity-cli"),
            File(homeDir, ".gemini/antigravity-app")
        )
        return dirs
    }

    private fun getAllTokenTargets(context: Context): List<File> {
        val dirs = getTargetDirs(context)
        val files = mutableListOf<File>()
        for (dir in dirs) {
            files.add(File(dir, CLI_TOKEN_FILE_NAME))
            files.add(File(dir, JETSKI_TOKEN_FILE_NAME))
        }
        return files
    }

    fun parseTokenExpiryMs(file: File): Long {
        if (!file.isFile || file.length() == 0L) return 0L
        return try {
            val content = file.readText(Charsets.UTF_8)
            val json = JSONObject(content)
            val tokenObj = if (json.has("token")) json.getJSONObject("token") else json
            val expiryStr = if (tokenObj.has("expiry")) tokenObj.getString("expiry") else null
            if (expiryStr != null) {
                java.time.Instant.parse(expiryStr).toEpochMilli()
            } else {
                file.lastModified()
            }
        } catch (e: Exception) {
            file.lastModified()
        }
    }

    fun tokenFile(context: Context): File {
        cachedTokenFile?.let { if (it.isFile && it.length() > 0L) return it }
        val targets = getAllTokenTargets(context).filter { it.isFile && it.length() > 0L }
        val best = targets.maxByOrNull { parseTokenExpiryMs(it) } ?: targets.maxByOrNull { it.lastModified() }
        if (best != null) {
            cachedTokenFile = best
            return best
        }

        val homeCli = File(File(context.filesDir, "home"), ".gemini/antigravity-cli")
        val fallback = File(homeCli, CLI_TOKEN_FILE_NAME)
        cachedTokenFile = fallback
        return fallback
    }

    fun hasToken(context: Context): Boolean {
        cachedHasToken?.let { return it }
        checkAndImportExistingToken(context)
        val file = tokenFile(context)
        if (!file.isFile || file.length() == 0L) {
            cachedHasToken = false
            return false
        }
        val result = try {
            val content = file.readText(Charsets.UTF_8).trim()
            content.contains("\"access_token\"") || (content.contains("\"token\"") && !content.contains("\"token\":null"))
        } catch (e: Exception) {
            false
        }
        cachedHasToken = result
        return result
    }

    fun checkAndImportExistingToken(context: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastTokenCheckTimeMs < 10_000L) {
            return
        }
        lastTokenCheckTimeMs = now
        val targets = getAllTokenTargets(context)
        val validTargets = targets.filter { it.isFile && it.length() > 0L }
        val best = validTargets.maxByOrNull { parseTokenExpiryMs(it) } ?: validTargets.maxByOrNull { it.lastModified() }

        if (best != null) {

            val content = try { best.readText(Charsets.UTF_8) } catch (e: Exception) { null }
            if (!content.isNullOrBlank()) {
                val allTargets = targets.distinct()
                val bestExpiry = parseTokenExpiryMs(best)
                for (target in allTargets) {
                    if (target != best) {
                        try {
                            val targetExpiry = parseTokenExpiryMs(target)
                            val needsUpdate = !target.exists() || target.length() == 0L || targetExpiry < bestExpiry || target.readText(Charsets.UTF_8) != content
                            if (needsUpdate) {
                                target.parentFile?.mkdirs()
                                target.writeText(content, Charsets.UTF_8)
                                target.setReadable(true, false)
                                target.setWritable(true, false)
                            }
                        } catch (e: Exception) {}
                    }
                }
            }
        }

        syncCompanionStateFiles(context)
        AccountManager.importExistingTokenIfEmpty(context)
    }

    fun syncCompanionStateFiles(context: Context) {
        val dirs = getTargetDirs(context)
        for (dir in dirs) {
            try {
                dir.mkdirs()
                val jetskiState = File(dir, "jetski_state.pbtxt")
                if (!jetskiState.exists() || jetskiState.length() == 0L) {
                    jetskiState.writeText(JETSKI_STATE_CONTENT, Charsets.UTF_8)
                    jetskiState.setReadable(true, false)
                }
                val agyState = File(dir, "antigravity_state.pbtxt")
                if (!agyState.exists() || agyState.length() == 0L) {
                    agyState.writeText(JETSKI_STATE_CONTENT, Charsets.UTF_8)
                    agyState.setReadable(true, false)
                }
                val idFile = File(dir, "installation_id")
                if (!idFile.exists() || idFile.length() == 0L) {
                    idFile.writeText(INSTALLATION_ID, Charsets.UTF_8)
                    idFile.setReadable(true, false)
                }
            } catch (e: Exception) {}
        }
    }

    suspend fun refreshTokenIfNeeded(context: Context): Boolean = withContext(Dispatchers.IO) {
        val file = tokenFile(context)
        if (!file.isFile || file.length() == 0L) return@withContext false
        try {
            val content = file.readText(Charsets.UTF_8)
            val root = JSONObject(content)
            val tokenObj = if (root.has("token")) root.getJSONObject("token") else root
            val refreshToken = if (tokenObj.has("refresh_token")) tokenObj.getString("refresh_token") else null
            if (refreshToken.isNullOrBlank()) return@withContext false

            val expiryStr = if (tokenObj.has("expiry")) tokenObj.getString("expiry") else null
            var needRefresh = false
            if (expiryStr != null) {
                try {
                    val expiryInstant = java.time.Instant.parse(expiryStr)
                    val bufferTime = java.time.Instant.now().plusSeconds(300)
                    if (bufferTime.isAfter(expiryInstant)) {
                        needRefresh = true
                    }
                } catch (e: Exception) {
                    needRefresh = true
                }
            } else {
                needRefresh = true
            }

            if (!needRefresh) {
                return@withContext true
            }

            Log.i(TAG, "Access token expiring or expired, refreshing using refresh_token...")
            val url = URL("https://oauth2.googleapis.com/token")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

            val postData = "client_id=" + URLEncoder.encode(CLIENT_ID, "UTF-8") +
                    "&client_secret=" + URLEncoder.encode(CLIENT_SECRET, "UTF-8") +
                    "&refresh_token=" + URLEncoder.encode(refreshToken, "UTF-8") +
                    "&grant_type=refresh_token"

            OutputStreamWriter(conn.outputStream).use { it.write(postData); it.flush() }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val respStr = conn.inputStream.bufferedReader().use { it.readText() }
                val newTok = JSONObject(respStr)

                val newAccessToken = newTok.getString("access_token")
                val expiresInSec = if (newTok.has("expires_in")) newTok.optLong("expires_in", 3600) else 3600
                val newExpiry = java.time.Instant.now().plusSeconds(expiresInSec).toString()

                tokenObj.put("access_token", newAccessToken)
                tokenObj.put("expiry", newExpiry)
                if (newTok.has("id_token")) {
                    tokenObj.put("id_token", newTok.getString("id_token"))
                    root.put("id_token", newTok.getString("id_token"))
                }
                root.put("token", tokenObj)
                root.put("auth_method", "consumer")

                saveTokenJson(context, root.toString(2))
                Log.i(TAG, "Access token refreshed successfully! New expiry: $newExpiry")
                true
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                Log.w(TAG, "Failed refreshing token ($responseCode): $err")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing token: ${e.message}", e)
            false
        }
    }

    fun getAccessToken(context: Context): String? {
        val file = tokenFile(context)
        if (!file.isFile || file.length() == 0L) return null
        return try {
            val content = file.readText(Charsets.UTF_8)
            val root = JSONObject(content)
            val tokenObj = if (root.has("token")) root.getJSONObject("token") else root
            if (tokenObj.has("access_token")) tokenObj.getString("access_token") else null
        } catch (e: Exception) {
            null
        }
    }

    data class TokenProfile(
        val email: String?,
        val name: String?,
        val picture: String?
    )

    fun parseProfileFromTokenJson(tokenJson: String): TokenProfile {
        if (tokenJson.isBlank()) return TokenProfile(null, null, null)
        return try {
            val root = JSONObject(tokenJson)
            val tokenObj = root.optJSONObject("token")
            val idToken = when {
                root.has("id_token") && root.optString("id_token").isNotBlank() -> root.optString("id_token")
                tokenObj != null && tokenObj.has("id_token") && tokenObj.optString("id_token").isNotBlank() -> tokenObj.optString("id_token")
                else -> null
            }
            var email: String? = null
            var name: String? = null
            var picture: String? = null

            if (!idToken.isNullOrBlank()) {
                val parts = idToken.split(".")
                if (parts.size >= 2) {
                    val payloadStr = String(
                        Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING),
                        StandardCharsets.UTF_8
                    )
                    val jwt = JSONObject(payloadStr)
                    email = jwt.optString("email").takeIf { it.isNotBlank() }
                    name = jwt.optString("name").takeIf { it.isNotBlank() }
                    picture = jwt.optString("picture").takeIf { it.isNotBlank() }
                }
            }

            if (email.isNullOrBlank()) {
                email = when {
                    root.has("email") && root.optString("email").isNotBlank() -> root.optString("email")
                    tokenObj != null && tokenObj.has("email") && tokenObj.optString("email").isNotBlank() -> tokenObj.optString("email")
                    else -> null
                }
            }
            if (name.isNullOrBlank()) {
                name = when {
                    root.has("name") && root.optString("name").isNotBlank() -> root.optString("name")
                    tokenObj != null && tokenObj.has("name") && tokenObj.optString("name").isNotBlank() -> tokenObj.optString("name")
                    else -> null
                }
            }
            if (picture.isNullOrBlank()) {
                picture = when {
                    root.has("picture") && root.optString("picture").isNotBlank() -> root.optString("picture")
                    tokenObj != null && tokenObj.has("picture") && tokenObj.optString("picture").isNotBlank() -> tokenObj.optString("picture")
                    else -> null
                }
            }

            TokenProfile(email = email, name = name, picture = picture)
        } catch (e: Exception) {
            TokenProfile(email = null, name = null, picture = null)
        }
    }

    fun getUserEmail(context: Context): String? {
        val file = tokenFile(context)
        if (!file.isFile || file.length() == 0L) return null
        return try {
            val content = file.readText(Charsets.UTF_8)
            parseProfileFromTokenJson(content).email
        } catch (e: Exception) {
            null
        }
    }

    fun getUserName(context: Context): String? {
        val file = tokenFile(context)
        if (!file.isFile || file.length() == 0L) return null
        return try {
            val content = file.readText(Charsets.UTF_8)
            val parsedName = parseProfileFromTokenJson(content).name
            if (!parsedName.isNullOrBlank()) return parsedName
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getString(KEY_USER_NAME, null)
        } catch (e: Exception) {
            null
        }
    }

    fun getUserPicture(context: Context): String? {
        val file = tokenFile(context)
        if (!file.isFile || file.length() == 0L) return null
        return try {
            val content = file.readText(Charsets.UTF_8)
            val parsedPic = parseProfileFromTokenJson(content).picture
            if (!parsedPic.isNullOrBlank()) return parsedPic
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getString(KEY_USER_PICTURE, null)
        } catch (e: Exception) {
            null
        }
    }

    fun updateTokenFileMetadata(context: Context, name: String?, picture: String?) {
        val allTargets = getAllTokenTargets(context).toMutableList()
        for (target in allTargets.distinct()) {
            if (target.isFile && target.length() > 0L) {
                try {
                    val content = target.readText(Charsets.UTF_8)
                    val json = JSONObject(content)
                    var changed = false
                    if (!name.isNullOrBlank() && json.optString("name") != name) {
                        json.put("name", name)
                        changed = true
                    }
                    if (!picture.isNullOrBlank() && json.optString("picture") != picture) {
                        json.put("picture", picture)
                        changed = true
                    }
                    if (changed) {
                        target.writeText(json.toString(2), Charsets.UTF_8)
                        target.setReadable(true, false)
                    }
                } catch (e: Exception) {}
            }
        }
    }

    suspend fun fetchUserInfo(context: Context): UserInfoResult? = withContext(Dispatchers.IO) {
        val accessToken = getAccessToken(context) ?: return@withContext null
        try {
            val url = URL("https://www.googleapis.com/oauth2/v3/userinfo")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            if (conn.responseCode in 200..299) {
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(resp)
                val name = if (json.has("name")) json.getString("name") else null
                val picture = if (json.has("picture")) json.getString("picture") else null
                val email = if (json.has("email")) json.getString("email") else null

                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val editor = prefs.edit()
                if (!name.isNullOrBlank()) editor.putString(KEY_USER_NAME, name)
                if (!picture.isNullOrBlank()) editor.putString(KEY_USER_PICTURE, picture)
                if (!email.isNullOrBlank()) editor.putString(KEY_USER_EMAIL, email)
                editor.apply()

                if (!name.isNullOrBlank() || !picture.isNullOrBlank()) {
                    updateTokenFileMetadata(context, name, picture)
                    if (!email.isNullOrBlank()) {
                        AccountManager.updateAccountProfile(context, email, name, picture)
                    }
                }

                UserInfoResult(name = name, picture = picture, email = email)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun clearToken(context: Context): Boolean {
        invalidateCache()
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(KEY_USER_NAME).remove(KEY_USER_PICTURE).remove(KEY_USER_EMAIL).apply()
        } catch (e: Exception) {}
        var ok = true
        val allTargets = getAllTokenTargets(context).toMutableList()
        for (target in allTargets.distinct()) {
            if (target.exists()) {
                try {
                    if (!target.delete()) {
                        target.writeText("")
                    }
                } catch (e: Exception) {
                    try { target.writeText("") } catch (e: Exception) {}
                    ok = false
                }
            }
        }
        syncCompanionStateFiles(context)
        AppLogger.d("Auth", "Cleared OAuth tokens across targets")
        try {
            id.vern.antigravity.service.CloudCodeQuotaService.restartLocalLanguageServer(context)
        } catch (e: Exception) {}
        return ok
    }

    fun writeRawTokenJson(context: Context, tokenJson: String) {
        invalidateCache()
        val allTargets = getAllTokenTargets(context).toMutableList()
        for (target in allTargets.distinct()) {
            try {
                target.parentFile?.mkdirs()
                target.writeText(tokenJson, Charsets.UTF_8)
                target.setReadable(true, false)
                target.setWritable(true, false)
            } catch (e: Exception) {}
        }
        syncCompanionStateFiles(context)
        try {
            id.vern.antigravity.service.CloudCodeQuotaService.restartLocalLanguageServer(context)
        } catch (e: Exception) {}
    }

    fun saveTokenJson(context: Context, tokenJson: String) {
        invalidateCache()
        val allTargets = getAllTokenTargets(context).toMutableList()
        val profile = parseProfileFromTokenJson(tokenJson)

        val finalJsonStr = try {
            val json = JSONObject(tokenJson)
            var modified = false
            if (!profile.name.isNullOrBlank() && !json.has("name")) {
                json.put("name", profile.name)
                modified = true
            }
            if (!profile.picture.isNullOrBlank() && !json.has("picture")) {
                json.put("picture", profile.picture)
                modified = true
            }
            if (modified) json.toString(2) else tokenJson
        } catch (e: Exception) {
            tokenJson
        }

        for (target in allTargets.distinct()) {
            try {
                target.parentFile?.mkdirs()
                target.writeText(finalJsonStr, Charsets.UTF_8)
                target.setReadable(true, false)
                target.setWritable(true, false)
            } catch (e: Exception) {}
        }
        syncCompanionStateFiles(context)
        val email = profile.email ?: getUserEmail(context)
        val name = profile.name ?: getUserName(context) ?: email?.substringBefore("@") ?: ""
        val pic = profile.picture ?: getUserPicture(context)
        if (!email.isNullOrBlank()) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_USER_EMAIL, email)
                .putString(KEY_USER_NAME, name)
                .putString(KEY_USER_PICTURE, pic)
                .apply()

            AccountManager.saveOrUpdateAccount(
                context = context,
                email = email,
                name = name,
                picture = pic,
                tokenJson = finalJsonStr,
                makeActive = true
            )
        }
        AppLogger.d("Auth", "Saved OAuth credentials for ${if (email.isNullOrBlank()) "user" else email}")
        try {
            id.vern.antigravity.service.CloudCodeQuotaService.restartLocalLanguageServer(context)
        } catch (e: Exception) {}
    }

    const val OFFICIAL_REDIRECT_URI = "https://antigravity.google/oauth-callback"

    @Volatile
    private var currentCodeVerifier: String? = null

    fun generatePkceAuthUrl(): Pair<String, String> {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val verifier = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP).trim()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(verifier.toByteArray(Charsets.US_ASCII))
        val challenge = Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP).trim()

        val encodedClientId = URLEncoder.encode(CLIENT_ID, "UTF-8")
        val encodedScopes = URLEncoder.encode(SCOPES, "UTF-8")
        val encodedRedirect = URLEncoder.encode(OFFICIAL_REDIRECT_URI, "UTF-8")

        val url = "https://accounts.google.com/o/oauth2/v2/auth" +
                "?access_type=offline" +
                "&client_id=$encodedClientId" +
                "&code_challenge=$challenge" +
                "&code_challenge_method=S256" +
                "&prompt=consent" +
                "&redirect_uri=$encodedRedirect" +
                "&response_type=code" +
                "&scope=$encodedScopes"

        currentCodeVerifier = verifier
        return Pair(url, verifier)
    }

    fun buildAuthUrl(redirectUri: String): String {
        val encodedRedirect = URLEncoder.encode(redirectUri, "UTF-8")
        val encodedScopes = URLEncoder.encode(SCOPES, "UTF-8")
        val encodedClientId = URLEncoder.encode(CLIENT_ID, "UTF-8")

        return "https://accounts.google.com/o/oauth2/v2/auth" +
                "?client_id=$encodedClientId" +
                "&redirect_uri=$encodedRedirect" +
                "&response_type=code" +
                "&scope=$encodedScopes" +
                "&access_type=offline" +
                "&prompt=consent"
    }

    @Volatile
    private var activeServerSocket: ServerSocket? = null

    private fun extractCodeFromRequest(requestLine: String): String? {
        if (!requestLine.contains("code=")) return null
        return try {
            val query = requestLine.substringAfter("?").substringBefore(" ")
            val params = query.split("&").associate {
                val p = it.split("=")
                if (p.size == 2) p[0] to p[1] else "" to ""
            }
            val rawCode = params["code"] ?: return null
            java.net.URLDecoder.decode(rawCode, "UTF-8")
        } catch (e: Exception) {
            val after = requestLine.substringAfter("code=").substringBefore("&").substringBefore(" ")
            try { java.net.URLDecoder.decode(after, "UTF-8") } catch (e: Exception) { after }
        }
    }

    private var cachedLogoBase64: String? = null

    private fun getLogoBase64(context: Context): String {
        cachedLogoBase64?.let { return it }
        return try {
            val bmp = android.graphics.BitmapFactory.decodeResource(context.resources, id.vern.antigravity.R.drawable.ic_launcher_logo)
            val stream = java.io.ByteArrayOutputStream()
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
            val b64 = android.util.Base64.encodeToString(stream.toByteArray(), android.util.Base64.NO_WRAP)
            cachedLogoBase64 = b64
            b64
        } catch (e: Exception) {
            ""
        }
    }

    private fun buildCallbackHtml(
        context: Context,
        email: String?
    ): String {
        val safeEmail = (email ?: "").replace("<", "&lt;").replace(">", "&gt;").trim()
        val logoB64 = getLogoBase64(context)

        return """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Login Authenticated - Antigravity</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      background: #090D14;
      color: #F8FAFC;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
      display: flex;
      align-items: center;
      justify-content: center;
      min-height: 100vh;
      padding: 24px;
    }
    .card {
      background: #131924;
      border: 1px solid #1E293B;
      border-radius: 24px;
      max-width: 400px;
      width: 100%;
      padding: 36px 28px;
      text-align: center;
      box-shadow: 0 20px 48px rgba(0, 0, 0, 0.7);
    }
    .logo-img {
      width: 80px;
      height: 80px;
      border-radius: 20px;
      box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4);
      margin-bottom: 24px;
    }
    h2 {
      font-size: 22px;
      font-weight: 700;
      color: #F8FAFC;
      margin-bottom: 10px;
      letter-spacing: -0.3px;
    }
    .description {
      font-size: 14px;
      color: #94A3B8;
      line-height: 1.5;
      margin-bottom: 20px;
    }
    .email-badge {
      display: inline-block;
      padding: 6px 14px;
      background: rgba(37, 99, 235, 0.12);
      border: 1px solid rgba(37, 99, 235, 0.3);
      border-radius: 20px;
      font-size: 13px;
      font-weight: 500;
      color: #60A5FA;
      margin-bottom: 24px;
      word-break: break-all;
    }
    .btn-open {
      display: block;
      width: 100%;
      padding: 15px 24px;
      background: #2563EB;
      color: #FFFFFF;
      font-size: 15px;
      font-weight: 600;
      text-decoration: none;
      border-radius: 14px;
      box-shadow: 0 4px 18px rgba(37, 99, 235, 0.4);
      cursor: pointer;
      transition: background 0.2s, transform 0.1s;
    }
    .btn-open:hover {
      background: #1D4ED8;
    }
    .btn-open:active {
      transform: scale(0.98);
    }
  </style>
</head>
<body>
  <div class="card">
    ${if (logoB64.isNotEmpty()) """<img src="data:image/png;base64,$logoB64" alt="Antigravity" class="logo-img">""" else ""}
    <h2>Login Authenticated</h2>
    <p class="description">Press button to open Antigravity for Android by Vern.</p>
    ${if (safeEmail.isNotEmpty()) """<div class="email-badge">$safeEmail</div>""" else ""}
    <a href="antigravity://open" class="btn-open" onclick="openApp(event)">Open Antigravity</a>
  </div>

  <script>
    function openApp(e) {
      if (e) e.preventDefault();
      window.location.href = "antigravity://open";
      setTimeout(function() {
        window.location.href = "intent://open#Intent;scheme=antigravity;package=id.vern.antigravity;action=android.intent.action.VIEW;category=android.intent.category.BROWSABLE;end";
      }, 300);
    }
    setTimeout(function() {
      openApp();
    }, 400);
  </script>
</body>
</html>
""".trimIndent()
    }

    suspend fun startBrowserAuth(
        context: Context,
        onSuccess: (email: String?) -> Unit,
        onError: (message: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            activeServerSocket?.close()
        } catch (e: Exception) {}

        var serverSocket: ServerSocket? = null
        try {
            serverSocket = ServerSocket().apply {
                reuseAddress = true
                bind(java.net.InetSocketAddress("0.0.0.0", CALLBACK_PORT))
            }
            activeServerSocket = serverSocket

            val redirectUri = "http://127.0.0.1:$CALLBACK_PORT/oauth/callback"
            val authUrl = buildAuthUrl(redirectUri)

            withContext(Dispatchers.Main) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    onError("Failed to open browser: ${e.message}")
                }
            }

            serverSocket.soTimeout = 180000
            var clientSocket: java.net.Socket? = null
            var code: String? = null

            while (code == null && !serverSocket.isClosed) {
                val sock = try {
                    serverSocket.accept()
                } catch (e: Exception) {
                    break
                }
                val reader = BufferedReader(InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8))
                val reqLine = reader.readLine() ?: ""
                val extracted = extractCodeFromRequest(reqLine)
                if (extracted != null) {
                    code = extracted
                    clientSocket = sock
                } else {
                    try {
                        val out = OutputStreamWriter(sock.getOutputStream(), StandardCharsets.UTF_8)
                        out.write("HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n")
                        out.flush()
                        sock.close()
                    } catch (e: Exception) {}
                }
            }

            if (code != null && clientSocket != null) {
                val emailEstimate = getUserEmail(context)
                val html = buildCallbackHtml(
                    context = context,
                    email = emailEstimate
                )

                val htmlBytes = html.toByteArray(StandardCharsets.UTF_8)
                val out = OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8)
                out.write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${htmlBytes.size}\r\nConnection: close\r\n\r\n")
                out.write(html)
                out.flush()
                clientSocket.close()

                var userEmail: String? = null
                val exchangeSuccess = exchangeCodeForTokens(
                    context = context,
                    code = code,
                    redirectUri = redirectUri,
                    codeVerifier = null,
                    onSuccess = { email ->
                        userEmail = email
                    },
                    onError = { err ->
                        Log.w(TAG, "Direct exchange warning on callback: $err")
                    }
                )

                if (exchangeSuccess && userEmail == null) {
                    userEmail = getUserEmail(context)
                }

                withContext(Dispatchers.Main) {
                    if (exchangeSuccess) {
                        onSuccess(userEmail)
                    } else {
                        onError("Authorization code received, but token exchange failed.")
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    onError("No authorization code received from Google.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "OAuth browser auth error: ${e.message}", e)
            withContext(Dispatchers.Main) {
                onError("Sign in timed out or failed: ${e.message}")
            }
        } finally {
            try { serverSocket?.close() } catch (e: Exception) {}
            activeServerSocket = null
        }
    }

    private suspend fun exchangeCodeForTokens(
        context: Context,
        code: String,
        redirectUri: String,
        codeVerifier: String? = null,
        onSuccess: (email: String?) -> Unit,
        onError: (message: String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://oauth2.googleapis.com/token")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

            val bodySb = StringBuilder()
            bodySb.append("client_id=").append(URLEncoder.encode(CLIENT_ID, "UTF-8"))
            bodySb.append("&client_secret=").append(URLEncoder.encode(CLIENT_SECRET, "UTF-8"))
            bodySb.append("&code=").append(URLEncoder.encode(code, "UTF-8"))
            bodySb.append("&grant_type=authorization_code")
            bodySb.append("&redirect_uri=").append(URLEncoder.encode(redirectUri, "UTF-8"))
            if (!codeVerifier.isNullOrBlank()) {
                bodySb.append("&code_verifier=").append(URLEncoder.encode(codeVerifier, "UTF-8"))
            }

            OutputStreamWriter(conn.outputStream).use { it.write(bodySb.toString()); it.flush() }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val tokenObj = JSONObject(response)

                if (!tokenObj.has("expiry")) {
                    val expiresInSec = if (tokenObj.has("expires_in")) tokenObj.optLong("expires_in", 3600) else 3600
                    val expiryInstant = java.time.Instant.now().plusSeconds(expiresInSec)
                    tokenObj.put("expiry", expiryInstant.toString())
                }

                val rootJson = JSONObject().apply {
                    put("token", tokenObj)
                    put("auth_method", "consumer")
                    if (tokenObj.has("id_token")) {
                        put("id_token", tokenObj.getString("id_token"))
                    }
                }

                saveTokenJson(context, rootJson.toString(2))
                try {
                    fetchUserInfo(context)
                } catch (e: Exception) {}
                val email = getUserEmail(context)

                withContext(Dispatchers.Main) {
                    onSuccess(email)
                }
                true
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                withContext(Dispatchers.Main) {
                    onError("Token exchange failed ($responseCode): $err")
                }
                false
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onError("Failed to exchange auth code: ${e.message}")
            }
            false
        }
    }

    suspend fun exchangeCodeDirectly(
        context: Context,
        rawInput: String,
        onSuccess: (email: String?) -> Unit,
        onError: (message: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        var trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            withContext(Dispatchers.Main) { onError("Kode otorisasi atau token tidak boleh kosong.") }
            return@withContext
        }

        if (trimmed.startsWith("{") && (trimmed.contains("\"access_token\"") || trimmed.contains("\"token\""))) {
            try {
                val json = JSONObject(trimmed)
                val finalJson = if (json.has("token")) {
                    json
                } else {
                    JSONObject().apply {
                        put("token", json)
                        put("auth_method", "consumer")
                        if (json.has("id_token")) {
                            put("id_token", json.getString("id_token"))
                        }
                    }
                }
                saveTokenJson(context, finalJson.toString(2))
                val email = getUserEmail(context)
                withContext(Dispatchers.Main) { onSuccess(email) }
                return@withContext
            } catch (e: Exception) {
                Log.w(TAG, "Failed parsing pasted token JSON: ${e.message}")
            }
        }

        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            try {
                val uri = Uri.parse(trimmed)
                val codeParam = uri.getQueryParameter("code")
                if (!codeParam.isNullOrBlank()) {
                    trimmed = codeParam
                }
            } catch (e: Exception) {}
        }
        if (trimmed.contains("%")) {
            try {
                trimmed = java.net.URLDecoder.decode(trimmed, "UTF-8")
            } catch (e: Exception) {}
        }

        val verifier = currentCodeVerifier
        var lastErr = ""

        val okLoopback = exchangeCodeForTokens(
            context = context,
            code = trimmed,
            redirectUri = "http://127.0.0.1:$CALLBACK_PORT/oauth/callback",
            codeVerifier = null,
            onSuccess = onSuccess,
            onError = { lastErr = it }
        )
        if (okLoopback) return@withContext

        val okLocalhost = exchangeCodeForTokens(
            context = context,
            code = trimmed,
            redirectUri = "http://localhost:$CALLBACK_PORT/oauth/callback",
            codeVerifier = null,
            onSuccess = onSuccess,
            onError = { lastErr = it }
        )
        if (okLocalhost) return@withContext

        val ok1 = exchangeCodeForTokens(
            context = context,
            code = trimmed,
            redirectUri = OFFICIAL_REDIRECT_URI,
            codeVerifier = verifier,
            onSuccess = onSuccess,
            onError = { lastErr = it }
        )
        if (ok1) return@withContext

        val ok2 = exchangeCodeForTokens(
            context = context,
            code = trimmed,
            redirectUri = OFFICIAL_REDIRECT_URI,
            codeVerifier = null,
            onSuccess = onSuccess,
            onError = { lastErr = it }
        )
        if (ok2) return@withContext

        withContext(Dispatchers.Main) {
            onError(lastErr)
        }
    }
}

