package com.example.data.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptParserTest {
  @Test
  fun parsesSrtWithMillisecondsAndMultilineText() {
    val entries = TranscriptParser.parse(
      "1\n00:00:01,250 --> 00:00:03,500\nHello\nworld\n\n2\n00:00:04,000 --> 00:00:05,000\nSecond line"
    )

    assertEquals(2, entries.size)
    assertEquals(1250L, entries[0].startMs)
    assertEquals(3500L, entries[0].endMs)
    assertEquals("Hello world", entries[0].text)
  }

  @Test
  fun parsesWebVttWithHoursOmitted() {
    val entries = TranscriptParser.parse(
      "WEBVTT\n\n00:02.000 --> 00:04.750\nA real caption"
    )

    assertEquals(1, entries.size)
    assertEquals(2000L, entries[0].startMs)
    assertEquals(4750L, entries[0].endMs)
  }

  @Test
  fun ignoresMalformedBlocks() {
    val entries = TranscriptParser.parse("not subtitles\n\n00:00:02,000 --> broken\nNo")
    assertTrue(entries.isEmpty())
  }
}
