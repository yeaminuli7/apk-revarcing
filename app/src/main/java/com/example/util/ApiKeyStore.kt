package com.example.util

import android.content.Context
import java.io.File

/**
 * Optional private key file. If rec/api.xml contains real keys, those are used.
 * If it is empty, the app falls back to keys typed in Settings.
 * This file is not a hiding method. Do not put a shared or public key here.
 */
object ApiKeyStore {
    private const val TEMPLATE = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<api>\n</api>\n"

    fun editableFile(context: Context): File {
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(root, "rec")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "api.xml")
        if (!file.exists() || file.length() == 0L) {
            val fromAsset = try {
                context.assets.open("rec/api.xml").bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                TEMPLATE
            }
            file.writeText(if (fromAsset.isBlank()) TEMPLATE else fromAsset, Charsets.UTF_8)
        }
        return file
    }

    fun readKeys(context: Context): List<String> {
        val editable = try {
            parse(editableFile(context).readText(Charsets.UTF_8))
        } catch (e: Exception) {
            emptyList()
        }
        if (editable.isNotEmpty()) return editable
        return try {
            parse(context.assets.open("rec/api.xml").bufferedReader().use { it.readText() })
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun parse(raw: String): List<String> {
        val tagged = Regex("<key>(.*?)</key>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .findAll(raw)
            .map { it.groupValues[1] }
            .toList()
        val source = if (tagged.isNotEmpty()) tagged.joinToString("\n") else raw
        return source.split(',', '\n', ';', ' ', '\t')
            .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
            .filter { isUsableKey(it) }
            .distinct()
    }

    private fun isUsableKey(value: String): Boolean {
        if (value.length < 20) return false
        if (value.any { it == '<' || it == '>' || it == ' ' }) return false
        val blocked = listOf("YOUR_KEY", "MY_GEMINI_API_KEY", "PASTE_KEY", "gsk_...", "AIza...")
        if (blocked.any { value.contains(it, ignoreCase = true) }) return false
        return value.startsWith("gsk_") || value.startsWith("AIza")
    }
}
