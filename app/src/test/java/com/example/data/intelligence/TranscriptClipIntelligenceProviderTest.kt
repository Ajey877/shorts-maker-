package com.example.data.intelligence

import com.example.data.media.TimedSubtitle
import com.example.model.YouTubeVideoInfo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptClipIntelligenceProviderTest {
  private val video = YouTubeVideoInfo(
    id = "local-test",
    url = "content://test/video",
    title = "Test video",
    channelName = "Local",
    durationSeconds = 90,
    viewCountFormatted = "Local media",
    thumbnailUrl = "",
    isLocalMedia = true
  )

  @Test
  fun selectsAtMostFourNonOverlappingCandidatesWithinSource() = runTest {
    val cues = (0 until 12).map { index ->
      TimedSubtitle(index * 7_000L, index * 7_000L + 4_000L, if (index == 6) "The secret is actually because this mistake matters!" else "A normal sentence")
    }

    val clips = TranscriptClipIntelligenceProvider().findCandidates(video, cues)

    assertTrue(clips.size <= 4)
    assertTrue(clips.zipWithNext().all { (a, b) -> a.endSeconds <= b.startSeconds })
    assertTrue(clips.all { it.startSeconds >= 0 && it.endSeconds <= video.durationSeconds })
    assertTrue(clips.all { it.durationSeconds in 10..30 })
    assertTrue(clips.any { it.viralityScore > 35 })
  }

  @Test
  fun emptyTranscriptProducesNoCandidates() = runTest {
    assertTrue(TranscriptClipIntelligenceProvider().findCandidates(video, emptyList()).isEmpty())
  }
}
