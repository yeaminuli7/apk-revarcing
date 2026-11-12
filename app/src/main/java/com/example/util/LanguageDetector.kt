package com.example.util

data class DetectedLanguage(
    val code: String,          // e.g. "en"
    val name: String,          // e.g. "English"
    val nativeName: String,    // e.g. "English"
    val confidence: Float,     // 0.0 to 1.0
    val scriptName: String     // e.g. "Latin"
)

/**
 * Offline script + stop-word detector. It does not call a network model.
 * Strong on Bengali, Hindi, Arabic/Urdu, CJK, Hangul, Thai, and Cyrillic.
 * Latin languages use distinctive words and fall back to English when unsure.
 */
object LanguageDetector {

    private data class Profile(
        val code: String,
        val name: String,
        val nativeName: String,
        val scriptName: String
    )

    private val profiles = mapOf(
        "en" to Profile("en", "English", "English", "Latin"),
        "bn" to Profile("bn", "Bengali", "বাংলা", "Bengali"),
        "hi" to Profile("hi", "Hindi", "हिन्दी", "Devanagari"),
        "ur" to Profile("ur", "Urdu", "اردو", "Arabic"),
        "ar" to Profile("ar", "Arabic", "العربية", "Arabic"),
        "es" to Profile("es", "Spanish", "Español", "Latin"),
        "fr" to Profile("fr", "French", "Français", "Latin"),
        "de" to Profile("de", "German", "Deutsch", "Latin"),
        "it" to Profile("it", "Italian", "Italiano", "Latin"),
        "pt" to Profile("pt", "Portuguese", "Português", "Latin"),
        "ru" to Profile("ru", "Russian", "Русский", "Cyrillic"),
        "ja" to Profile("ja", "Japanese", "日本語", "Japanese"),
        "ko" to Profile("ko", "Korean", "한국어", "Hangul"),
        "zh" to Profile("zh", "Chinese", "中文", "Han"),
        "id" to Profile("id", "Indonesian", "Bahasa Indonesia", "Latin"),
        "tr" to Profile("tr", "Turkish", "Türkçe", "Latin"),
        "th" to Profile("th", "Thai", "ไทย", "Thai"),
        "vi" to Profile("vi", "Vietnamese", "Tiếng Việt", "Latin"),
        "el" to Profile("el", "Greek", "Ελληνικά", "Greek"),
        "he" to Profile("he", "Hebrew", "עברית", "Hebrew")
    )

    // Letters that are normal in Urdu and uncommon in Arabic. Shared letters such as ی are excluded.
    private val urduHintCodes = setOf(
        0x0679, 0x067E, 0x0686, 0x0688, 0x0691, 0x0698,
        0x06A9, 0x06AF, 0x06BA, 0x06D2
    )

    private val latinStopwords = mapOf(
        "en" to setOf("the", "and", "you", "that", "this", "with", "have", "from", "what", "your", "hello"),
        "es" to setOf("que", "los", "las", "una", "por", "para", "está", "hola", "gracias", "también"),
        "fr" to setOf("les", "des", "une", "est", "pas", "pour", "avec", "dans", "bonjour", "vous"),
        "de" to setOf("der", "die", "das", "und", "nicht", "ich", "ein", "eine", "ist", "hallo"),
        "pt" to setOf("não", "uma", "para", "você", "está", "isso", "também", "obrigado"),
        "it" to setOf("che", "non", "una", "per", "sono", "ciao", "questo", "della", "grazie"),
        "id" to setOf("yang", "dan", "ini", "itu", "tidak", "dengan", "untuk", "saya", "apa"),
        "tr" to setOf("bir", "ve", "bu", "için", "değil", "çok", "merhaba", "ben"),
        "vi" to setOf("không", "một", "của", "được", "những", "trong", "xin", "chào")
    )

    fun detectLanguage(text: String): DetectedLanguage {
        if (text.isBlank()) return profile("en", 0.2f)

        var letters = 0
        var bengali = 0
        var devanagari = 0
        var arabic = 0
        var urduHints = 0
        var thai = 0
        var cyrillic = 0
        var hangul = 0
        var kana = 0
        var cjk = 0
        var greek = 0
        var hebrew = 0
        var latin = 0

        for (ch in text) {
            val code = ch.code
            when {
                code in 0x0980..0x09FF -> { letters++; bengali++ }
                code in 0x0900..0x097F -> { letters++; devanagari++ }
                code in 0x0600..0x06FF || code in 0x0750..0x077F || code in 0x08A0..0x08FF || code in 0xFB50..0xFDFF -> {
                    letters++
                    arabic++
                    if (code in urduHintCodes) urduHints++
                }
                code in 0x0E00..0x0E7F -> { letters++; thai++ }
                code in 0x0400..0x04FF -> { letters++; cyrillic++ }
                code in 0xAC00..0xD7AF || code in 0x1100..0x11FF -> { letters++; hangul++ }
                code in 0x3040..0x30FF -> { letters++; kana++ }
                code in 0x4E00..0x9FFF -> { letters++; cjk++ }
                code in 0x0370..0x03FF -> { letters++; greek++ }
                code in 0x0590..0x05FF -> { letters++; hebrew++ }
                ch.isLetter() -> { letters++; latin++ }
            }
        }

        if (letters == 0) return profile("en", 0.2f)

        val scriptWinner = listOf(
            "bn" to bengali,
            "hi" to devanagari,
            "th" to thai,
            "ru" to cyrillic,
            "el" to greek,
            "he" to hebrew
        ).maxByOrNull { it.second }

        scriptWinner?.let { (code, count) ->
            if (count >= 2 && count.toFloat() / letters >= 0.35f) {
                return profile(code, confidenceFor(count, letters))
            }
        }

        if (arabic >= 2 && arabic.toFloat() / letters >= 0.35f) {
            val urdu = urduHints >= 2
            return profile(if (urdu) "ur" else "ar", confidenceFor(arabic, letters))
        }

        if (hangul >= 2 && hangul >= kana) {
            return profile("ko", confidenceFor(hangul + cjk, letters))
        }
        if (kana >= 1 && (kana + cjk) >= 2) {
            return profile("ja", confidenceFor(kana + cjk, letters))
        }
        if (cjk >= 2 && kana == 0 && hangul == 0) {
            return profile("zh", confidenceFor(cjk, letters))
        }

        val latinGuess = detectLatin(text)
        if (latinGuess != null) return latinGuess

        return if (latin >= bengali && latin >= devanagari && latin >= arabic) {
            profile("en", 0.35f)
        } else {
            profile("en", 0.25f)
        }
    }

    private fun detectLatin(text: String): DetectedLanguage? {
        val tokens = text.lowercase()
            .split(Regex("[^\\p{L}\\p{M}]+"))
            .filter { it.length >= 2 }
        if (tokens.isEmpty()) return null

        var bestCode = "en"
        var bestScore = 0
        var secondScore = 0
        for ((code, words) in latinStopwords) {
            val score = tokens.count { it in words }
            if (score > bestScore) {
                secondScore = bestScore
                bestScore = score
                bestCode = code
            } else if (score > secondScore) {
                secondScore = score
            }
        }

        // Need a clear signal. One shared word such as "una" is not enough.
        if (bestScore >= 2 && bestScore > secondScore) {
            val confidence = (0.5f + (bestScore.coerceAtMost(6) * 0.07f)).coerceIn(0.5f, 0.9f)
            return profile(bestCode, confidence)
        }
        return null
    }

    private fun confidenceFor(count: Int, letters: Int): Float {
        val ratio = count.toFloat() / letters.coerceAtLeast(1)
        return (0.6f + ratio * 0.35f).coerceIn(0.6f, 0.98f)
    }

    private fun profile(code: String, confidence: Float): DetectedLanguage {
        val known = profiles[code] ?: profiles.getValue("en")
        return DetectedLanguage(
            code = known.code,
            name = known.name,
            nativeName = known.nativeName,
            confidence = confidence.coerceIn(0f, 1f),
            scriptName = known.scriptName
        )
    }
}
