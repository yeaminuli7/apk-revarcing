package com.example

import com.example.util.SubtitleTextProtector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleTextProtectorTest {
    @Test
    fun numbersAndTimesRoundTrip() {
        val original = "Meet me at 7:30 in room 12."
        val (masked, tokens) = SubtitleTextProtector.protect(original)
        assertFalse(masked.contains("7:30"))
        assertFalse(masked.contains("12"))
        assertEquals(original, SubtitleTextProtector.restore(masked, tokens))
    }

    @Test
    fun timestampLineHasNothingToTranslate() {
        val (masked, _) = SubtitleTextProtector.protect("00:01:20,000")
        assertFalse(SubtitleTextProtector.hasTranslatableWords(masked))
    }

    @Test
    fun droppedMarkerIsDetected() {
        val (_, tokens) = SubtitleTextProtector.protect("Room 12 at 7:30")
        assertFalse(SubtitleTextProtector.containsAllMarkers("Room twelve at evening", tokens.size))
        assertTrue(SubtitleTextProtector.containsAllMarkers("Room {{n0}} at {{n1}}", tokens.size))
    }

    @Test
    fun dialogueStillHasWords() {
        val (masked, _) = SubtitleTextProtector.protect("আমি ৩ জনকে দেখেছি")
        assertTrue(SubtitleTextProtector.hasTranslatableWords(masked))
    }
}
