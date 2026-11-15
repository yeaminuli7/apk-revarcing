package com.example.data

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * A voice the user has saved from the "Who is speaking?" picker, so they never have to
 * search through 40+ characters again. One tap applies it to every block and makes it the
 * default for the next video as well.
 */
data class VoicePreset(
    val id: String,
    val name: String,
    val voiceId: String,
    val createdAt: Long = System.currentTimeMillis()
)

object VoicePresetStore {
    private const val LIST_KEY = "pref_voice_presets"
    private const val ACTIVE_KEY = "pref_voice_preset_active"
    const val MAX_PRESETS = 12

    fun load(prefs: SharedPreferences): List<VoicePreset> {
        val raw = prefs.getString(LIST_KEY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val voiceId = item.optString("voiceId")
                    if (voiceId.isBlank()) continue
                    add(
                        VoicePreset(
                            id = item.optString("id").ifBlank { voiceId },
                            name = item.optString("name").ifBlank { voiceId },
                            voiceId = voiceId,
                            createdAt = item.optLong("createdAt", 0L)
                        )
                    )
                }
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    fun save(prefs: SharedPreferences, presets: List<VoicePreset>) {
        val array = JSONArray()
        presets.take(MAX_PRESETS).forEach { preset ->
            array.put(
                JSONObject()
                    .put("id", preset.id)
                    .put("name", preset.name)
                    .put("voiceId", preset.voiceId)
                    .put("createdAt", preset.createdAt)
            )
        }
        prefs.edit().putString(LIST_KEY, array.toString()).apply()
    }

    fun activeId(prefs: SharedPreferences): String? =
        prefs.getString(ACTIVE_KEY, null)?.takeIf { it.isNotBlank() }

    fun setActiveId(prefs: SharedPreferences, id: String?) {
        prefs.edit().putString(ACTIVE_KEY, id).apply()
    }
}
