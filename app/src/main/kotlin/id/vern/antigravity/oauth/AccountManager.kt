package id.vern.antigravity.oauth

import android.content.Context
import id.vern.antigravity.data.SubscriptionTierType
import id.vern.antigravity.util.AppLogger
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class StoredAccount(
    val id: String,
    val email: String,
    val name: String,
    val picture: String? = null,
    val tokenJson: String,
    val tier: String = "FREE",
    val lastUsedAt: Long = System.currentTimeMillis()
) {
    val tierType: SubscriptionTierType get() = when (tier.uppercase()) {
        "ULTRA" -> SubscriptionTierType.ULTRA
        "PRO" -> SubscriptionTierType.PRO
        "ENTERPRISE" -> SubscriptionTierType.ENTERPRISE
        else -> SubscriptionTierType.FREE
    }
}

object AccountManager {
    private const val ACCOUNTS_FILE_NAME = "accounts_store.json"
    private val lock = Any()

    private fun getStoreFile(context: Context): File {
        val dir = File(context.filesDir, ".gemini")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, ACCOUNTS_FILE_NAME)
    }

    fun getAccounts(context: Context): List<StoredAccount> = synchronized(lock) {
        val file = getStoreFile(context)
        if (!file.isFile || file.length() == 0L) {
            return emptyList()
        }
        return try {
            val content = file.readText(Charsets.UTF_8)
            val root = JSONObject(content)
            val arr = root.optJSONArray("accounts") ?: return emptyList()
            val list = mutableListOf<StoredAccount>()
            var needsPersist = false
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.optString("id")
                val email = obj.optString("email")
                var name = obj.optString("name")
                var picture = obj.optString("picture").takeIf { it.isNotBlank() }
                val tokenJson = obj.optString("tokenJson")
                val tier = obj.optString("tier", "FREE")
                val lastUsedAt = obj.optLong("lastUsedAt", System.currentTimeMillis())

                if (tokenJson.isNotBlank()) {
                    val profile = OAuthManager.parseProfileFromTokenJson(tokenJson)
                    val realName = profile.name?.takeIf { it.isNotBlank() }
                    val realPic = profile.picture?.takeIf { it.isNotBlank() }
                    if (realName != null && realName != name) {
                        name = realName
                        needsPersist = true
                    }
                    if (realPic != null && realPic != picture) {
                        picture = realPic
                        needsPersist = true
                    }
                }

                if (id.isNotBlank() && email.isNotBlank()) {
                    list.add(
                        StoredAccount(
                            id = id,
                            email = email,
                            name = name,
                            picture = picture,
                            tokenJson = tokenJson,
                            tier = tier,
                            lastUsedAt = lastUsedAt
                        )
                    )
                }
            }
            if (needsPersist) {
                val activeId = root.optString("activeAccountId").takeIf { it.isNotBlank() }
                persist(context, activeId, list)
            }
            list.sortedByDescending { it.lastUsedAt }
        } catch (e: Exception) {
            AppLogger.e("AccountManager", "Error reading accounts: ${e.message}")
            emptyList()
        }
    }

    fun getActiveAccountId(context: Context): String? = synchronized(lock) {
        val file = getStoreFile(context)
        if (!file.isFile || file.length() == 0L) {
            return null
        }
        return try {
            val content = file.readText(Charsets.UTF_8)
            val root = JSONObject(content)
            root.optString("activeAccountId").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    fun getActiveAccount(context: Context): StoredAccount? {
        val accounts = getAccounts(context)
        if (accounts.isEmpty()) return null
        val activeId = getActiveAccountId(context)
        return accounts.firstOrNull { it.id == activeId } ?: accounts.firstOrNull()
    }

    private fun persist(context: Context, activeId: String?, accounts: List<StoredAccount>) {
        val file = getStoreFile(context)
        try {
            val root = JSONObject()
            root.put("activeAccountId", activeId ?: "")
            val arr = JSONArray()
            for (acc in accounts) {
                val obj = JSONObject()
                obj.put("id", acc.id)
                obj.put("email", acc.email)
                obj.put("name", acc.name)
                obj.put("picture", acc.picture ?: "")
                obj.put("tokenJson", acc.tokenJson)
                obj.put("tier", acc.tier)
                obj.put("lastUsedAt", acc.lastUsedAt)
                arr.put(obj)
            }
            root.put("accounts", arr)
            file.parentFile?.mkdirs()
            file.writeText(root.toString(2), Charsets.UTF_8)
            file.setReadable(true, false)
            file.setWritable(true, false)
        } catch (e: Exception) {
            AppLogger.e("AccountManager", "Failed to persist accounts: ${e.message}")
        }
    }

    fun saveOrUpdateAccount(
        context: Context,
        email: String,
        name: String,
        picture: String?,
        tokenJson: String,
        tier: String? = null,
        makeActive: Boolean = true
    ): StoredAccount = synchronized(lock) {
        val current = getAccounts(context).toMutableList()
        val index = current.indexOfFirst { it.email.equals(email, ignoreCase = true) }
        val now = System.currentTimeMillis()
        val existingTier = if (index >= 0) current[index].tier else "FREE"
        val resolvedTier = tier?.uppercase() ?: existingTier
        val finalName = if (name.isNotBlank()) name else if (index >= 0 && current[index].name.isNotBlank()) current[index].name else email.substringBefore("@")
        val finalPic = picture?.takeIf { it.isNotBlank() } ?: if (index >= 0) current[index].picture else null

        val stored = StoredAccount(
            id = email,
            email = email,
            name = finalName,
            picture = finalPic,
            tokenJson = tokenJson,
            tier = resolvedTier,
            lastUsedAt = now
        )

        if (index >= 0) {
            current[index] = stored
        } else {
            current.add(0, stored)
        }

        val activeId = if (makeActive) email else (getActiveAccountId(context) ?: email)
        persist(context, activeId, current)
        stored
    }

    fun updateAccountProfile(context: Context, email: String, name: String?, picture: String?) = synchronized(lock) {
        val current = getAccounts(context).toMutableList()
        val index = current.indexOfFirst { it.email.equals(email, ignoreCase = true) }
        if (index >= 0) {
            val item = current[index]
            val updated = item.copy(
                name = name?.takeIf { it.isNotBlank() } ?: item.name,
                picture = picture?.takeIf { it.isNotBlank() } ?: item.picture
            )
            current[index] = updated
            persist(context, getActiveAccountId(context), current)
        }
    }

    fun updateAccountTier(context: Context, email: String, tier: String) = synchronized(lock) {
        val current = getAccounts(context).toMutableList()
        val index = current.indexOfFirst { it.email.equals(email, ignoreCase = true) }
        if (index >= 0) {
            val item = current[index]
            val updated = item.copy(tier = tier.uppercase())
            current[index] = updated
            persist(context, getActiveAccountId(context), current)
            AppLogger.d("AccountManager", "Updated tier for ${item.email}: ${tier.uppercase()}")
        }
    }

    fun switchAccount(context: Context, accountId: String): Boolean = synchronized(lock) {
        val accounts = getAccounts(context)
        val target = accounts.firstOrNull { it.id.equals(accountId, ignoreCase = true) || it.email.equals(accountId, ignoreCase = true) }
            ?: return false

        val updatedList = accounts.map {
            if (it.id == target.id) it.copy(lastUsedAt = System.currentTimeMillis()) else it
        }
        persist(context, target.id, updatedList)

        OAuthManager.writeRawTokenJson(context, target.tokenJson)
        val prefs = context.getSharedPreferences(OAuthManager.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(OAuthManager.KEY_USER_EMAIL, target.email)
            .putString(OAuthManager.KEY_USER_NAME, target.name)
            .putString(OAuthManager.KEY_USER_PICTURE, target.picture)
            .apply()

        AppLogger.d("AccountManager", "Switched active account to: ${target.email}")
        true
    }

    fun removeAccount(context: Context, accountId: String): Boolean = synchronized(lock) {
        val accounts = getAccounts(context).toMutableList()
        val index = accounts.indexOfFirst { it.id.equals(accountId, ignoreCase = true) || it.email.equals(accountId, ignoreCase = true) }
        if (index < 0) return false

        val removed = accounts.removeAt(index)
        val currentActive = getActiveAccountId(context)
        val newActiveId = if (currentActive == removed.id || currentActive == removed.email) {
            accounts.firstOrNull()?.id
        } else {
            currentActive
        }

        persist(context, newActiveId, accounts)

        if (newActiveId != null) {
            val next = accounts.first { it.id == newActiveId }
            OAuthManager.writeRawTokenJson(context, next.tokenJson)
            val prefs = context.getSharedPreferences(OAuthManager.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(OAuthManager.KEY_USER_EMAIL, next.email)
                .putString(OAuthManager.KEY_USER_NAME, next.name)
                .putString(OAuthManager.KEY_USER_PICTURE, next.picture)
                .apply()
        } else {
            OAuthManager.clearToken(context)
        }

        AppLogger.d("AccountManager", "Removed account: ${removed.email}")
        true
    }

    fun importExistingTokenIfEmpty(context: Context) {
        val accounts = getAccounts(context)
        if (accounts.isNotEmpty()) return

        val email = OAuthManager.getUserEmail(context) ?: return
        val name = OAuthManager.getUserName(context) ?: email.substringBefore("@")
        val picture = OAuthManager.getUserPicture(context)
        val tokenFile = OAuthManager.tokenFile(context)
        if (!tokenFile.isFile || tokenFile.length() == 0L) return

        try {
            val tokenJson = tokenFile.readText(Charsets.UTF_8)
            saveOrUpdateAccount(
                context = context,
                email = email,
                name = name,
                picture = picture,
                tokenJson = tokenJson,
                tier = "FREE",
                makeActive = true
            )
            AppLogger.d("AccountManager", "Imported existing token as initial account: $email")
        } catch (e: Exception) {
            AppLogger.e("AccountManager", "Failed to import existing token: ${e.message}")
        }
    }
}
