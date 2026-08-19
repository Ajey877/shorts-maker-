package com.example.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiClipperServiceTest {
  private val service = GeminiClipperService()

  @Test
  fun extractsWatchVideoId() {
    assertEquals(
      "dQw4w9WgXcQ",
      service.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
    )
  }

  @Test
  fun extractsShortsVideoId() {
    assertEquals(
      "dQw4w9WgXcQ",
      service.extractVideoId("https://youtube.com/shorts/dQw4w9WgXcQ?feature=share")
    )
  }

  @Test
  fun acceptsDirectElevenCharacterId() {
    assertEquals("dQw4w9WgXcQ", service.extractVideoId("dQw4w9WgXcQ"))
  }

  @Test
  fun invalidInputGetsSafeDemoFallbackId() {
    assertTrue(service.extractVideoId("not a YouTube URL").startsWith("demo_"))
  }
}
