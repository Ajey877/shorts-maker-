package com.example.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiClipperServiceTest {
  private val service = GeminiClipperService()

  @Test
  fun extractsStandardYouTubeIds() {
    assertEquals("dQw4w9WgXcQ", service.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    assertEquals("dQw4w9WgXcQ", service.extractVideoId("https://youtu.be/dQw4w9WgXcQ"))
    assertEquals("dQw4w9WgXcQ", service.extractVideoId("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
  }

  @Test
  fun presetCandidateRangesStayInsideSource() {
    val video = service.resolveVideoInfo("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
    val clips = kotlinx.coroutines.runBlocking { service.generateShorts(video) }
    assertTrue(clips.isNotEmpty())
    clips.forEach { clip ->
      assertTrue(clip.startSeconds >= 0)
      assertTrue(clip.endSeconds <= video.durationSeconds)
      assertTrue(clip.endSeconds > clip.startSeconds)
      assertTrue(clip.durationSeconds in 10..30)
    }
  }
}
