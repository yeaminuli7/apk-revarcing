package com.example.util

/**
 * Hides cue numbers, clock times, and plain digits before translation,
 * then puts the original characters back. Dialogue words are left untouched.
 */
object SubtitleTextProtector {

    // ASCII and local digits (including Bengali ০-৯) plus clock times.
    private val numericToken = Regex(
        """\p{Nd}{1,2}:\p{Nd}{2}(?::\p{Nd}{2})?(?:[.,]\p{Nd}{1,3})?|\p{Nd}+(?:[.,]\p{Nd}+)?"""
    )
    private val marker = Regex("""\{\{\s*n\s*(\d+)\s*\}\}""")

    fun protect(text: String): Pair<String, List<String>> {
        if (text.isEmpty()) return text to emptyList()
        val tokens = mutableListOf<String>()
        val masked = numericToken.replace(text) { match ->
            val token = "{{n${tokens.size}}}"
            tokens += match.value
            token
        }
        return masked to tokens
    }

    fun restore(text: String, tokens: List<String>): String {
        if (tokens.isEmpty()) return text
        return marker.replace(text) { match ->
            val index = match.groupValues[1].toIntOrNull()
            if (index != null && index in tokens.indices) tokens[index] else match.value
        }
    }

    /** Every protected number must still be marked. A missing mark means the model dropped it. */
    fun containsAllMarkers(text: String, tokenCount: Int): Boolean {
        if (tokenCount <= 0) return true
        val found = marker.findAll(text).mapNotNull { it.groupValues[1].toIntOrNull() }.toSet()
        return (0 until tokenCount).all { it in found }
    }

    /** True when something other than a protected number is left to translate. */
    fun hasTranslatableWords(masked: String): Boolean {
        val stripped = masked.replace(marker, " ")
        return stripped.any { it.isLetter() }
    }
}
