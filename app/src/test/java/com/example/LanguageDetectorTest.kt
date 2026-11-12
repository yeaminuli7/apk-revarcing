package com.example

import com.example.util.LanguageDetector
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageDetectorTest {
    @Test
    fun detectsBengali() {
        val detected = LanguageDetector.detectLanguage("আমি বাংলায় কথা বলছি। এটি একটি পরীক্ষা।")
        assertEquals("bn", detected.code)
    }

    @Test
    fun detectsHindiNotBengali() {
        val detected = LanguageDetector.detectLanguage("नमस्ते, यह एक हिंदी वाक्य है।")
        assertEquals("hi", detected.code)
    }

    @Test
    fun detectsEnglishStopwords() {
        val detected = LanguageDetector.detectLanguage("Hello, this is a test and you have the file.")
        assertEquals("en", detected.code)
    }

    @Test
    fun blankTextFallsBackToEnglish() {
        assertEquals("en", LanguageDetector.detectLanguage("   ").code)
    }

    @Test
    fun detectsArabicWithoutUrduLetters() {
        val detected = LanguageDetector.detectLanguage("هذه جملة عربية للاختبار فقط")
        assertEquals("ar", detected.code)
    }

    @Test
    fun detectsUrduSpecificLetters() {
        val detected = LanguageDetector.detectLanguage("یہ اردو جملہ ہے اور میں بات کر رہا ہوں")
        assertEquals("ur", detected.code)
    }
}
