package com.example

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the SRT timestamp formatter split out of MainActivity into
 * TranslatorScreen.kt (mm:ss.mmm, as used inside subtitle cards).
 */
class SrtTimestampFormatTest {

    @Test
    fun `formats zero as 00 00 000`() {
        assertEquals("00:00.000", formatSrtTimestamp(0L))
    }

    @Test
    fun `formats minutes seconds and millis`() {
        assertEquals("01:05.123", formatSrtTimestamp(65_123L))
    }

    @Test
    fun `drops hours and keeps minutes within the hour`() {
        // 1 hour, 2 minutes, 3 seconds, 4 ms -> minutes are ((ms % 1h) / 60k)
        assertEquals("02:03.004", formatSrtTimestamp(3_723_004L))
    }

    @Test
    fun `pads single digits`() {
        assertEquals("00:09.001", formatSrtTimestamp(9_001L))
    }
}
