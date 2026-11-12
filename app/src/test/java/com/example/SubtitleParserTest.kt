package com.example

import com.example.util.SubtitleEntry
import com.example.util.SubtitleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the SRT/VTT subtitle parser (no Android dependencies).
 */
class SubtitleParserTest {

    @Test
    fun `parses standard srt with bangla text and index numbers`() {
        val srt = """
            1
            00:00:01,000 --> 00:00:03,500
            আসসালামু আলাইকুম

            2
            00:00:04,000 --> 00:00:06,250
            দ্বিতীয় লাইন
        """.trimIndent()

        val entries = SubtitleParser.parseSrtOrVtt(srt)

        assertEquals(2, entries.size)
        assertEquals(1000L, entries[0].startTimeMs)
        assertEquals(3500L, entries[0].endTimeMs)
        assertEquals("আসসালামু আলাইকুম", entries[0].text)
        assertEquals(4000L, entries[1].startTimeMs)
        assertEquals(6250L, entries[1].endTimeMs)
        // indexes are reassigned continuously
        assertEquals(1, entries[0].index)
        assertEquals(2, entries[1].index)
    }

    @Test
    fun `parses webvtt with header and dot milliseconds`() {
        val vtt = """
            WEBVTT

            NOTE This is a comment block

            00:01:20.000 --> 00:01:23.120
            Hello from VTT
        """.trimIndent()

        val entries = SubtitleParser.parseSrtOrVtt(vtt)

        assertEquals(1, entries.size)
        assertEquals(80_000L, entries[0].startTimeMs)
        assertEquals(83_120L, entries[0].endTimeMs)
        assertEquals("Hello from VTT", entries[0].text)
    }

    @Test
    fun `strips style tags and ass overrides from text`() {
        val srt = """
            1
            00:00:01,000 --> 00:00:02,000
            <i>italic</i> <b>bold</b> {\an5}সাধারণ কথা
        """.trimIndent()

        val entries = SubtitleParser.parseSrtOrVtt(srt)

        assertEquals(1, entries.size)
        assertEquals("italic bold সাধারণ কথা", entries[0].text)
    }

    @Test
    fun `parses short minute based timestamps`() {
        val srt = "01:20.000 --> 01:23.120\nshort form line"

        val entries = SubtitleParser.parseSrtOrVtt(srt)

        assertEquals(1, entries.size)
        assertEquals(80_000L, entries[0].startTimeMs)
        assertEquals(83_120L, entries[0].endTimeMs)
        assertEquals("short form line", entries[0].text)
    }

    @Test
    fun `multi line text blocks are joined with newline`() {
        val srt = """
            1
            00:00:01,000 --> 00:00:04,000
            প্রথম লাইন
            দ্বিতীয় লাইন
        """.trimIndent()

        val entries = SubtitleParser.parseSrtOrVtt(srt)

        assertEquals(1, entries.size)
        assertTrue(entries[0].text.contains("\n"))
        assertEquals("প্রথম লাইন\nদ্বিতীয় লাইন", entries[0].text)
    }

    @Test
    fun `empty input returns empty list`() {
        assertTrue(SubtitleParser.parseSrtOrVtt("").isEmpty())
        assertTrue(SubtitleParser.parseSrtOrVtt("WEBVTT\n").isEmpty())
    }

    @Test
    fun `getCleanTextOnly joins entry texts with blank line`() {
        val srt = """
            1
            00:00:01,000 --> 00:00:02,000
            এক

            2
            00:00:03,000 --> 00:00:04,000
            দুই
        """.trimIndent()

        val clean = SubtitleParser.getCleanTextOnly(srt)

        assertEquals("এক\n\nদুই", clean)
    }

    @Test
    fun `parsed entries match SubtitleEntry shape`() {
        val entry = SubtitleEntry(index = 1, startTimeMs = 0L, endTimeMs = 1L, text = "x")
        assertEquals(1, entry.index)
        assertEquals("x", entry.text)
    }
}
