package com.example.data.remote

import com.example.model.RetentionPoint
import com.example.model.ShortClip
import com.example.model.SubtitlePhrase
import com.example.model.YouTubeVideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

/**
 * Source and candidate analysis service.
 *
 * Important: this class never claims synthetic candidates are measured YouTube retention.
 * Real transcript/AI analysis will be plugged into this boundary when a source transcript is available.
 */
class GeminiClipperService {
  fun extractVideoId(input: String): String {
    val trimmed = input.trim()
    if (trimmed.length == 11 && !trimmed.contains(" ") && !trimmed.contains("/")) return trimmed
    val patterns = listOf(
      Pattern.compile("(?:v=|/v/|youtu\\.be/|/embed/|/shorts/|\\?v=)([^#&?\\n]+)"),
      Pattern.compile("youtu\\.be/([^#&?\\n]+)"),
      Pattern.compile("youtube\\.com/watch\\?v=([^#&?\\n]+)"),
      Pattern.compile("youtube\\.com/shorts/([^#&?\\n]+)")
    )
    patterns.forEach { pattern ->
      val matcher = pattern.matcher(trimmed)
      if (matcher.find()) return matcher.group(1)?.take(11).orEmpty()
    }
    return "source_${trimmed.hashCode().toUInt().toString(16)}"
  }

  fun resolveVideoInfo(input: String): YouTubeVideoInfo {
    val videoId = extractVideoId(input)
    val trimmed = input.trim()
    PRESETS.firstOrNull { it.id.equals(videoId, true) || input.contains(it.id) }?.let { return it }

    return YouTubeVideoInfo(
      id = videoId,
      url = trimmed,
      title = if (trimmed.startsWith("http", true)) "YouTube source $videoId" else trimmed,
      channelName = "Unknown source",
      durationSeconds = 0,
      viewCountFormatted = "Unavailable",
      thumbnailUrl = if (videoId.startsWith("source_")) "" else "https://img.youtube.com/vi/$videoId/hqdefault.jpg",
      description = "Metadata-only source. Import the actual media for playback and export."
    )
  }

  suspend fun generateShorts(videoInfo: YouTubeVideoInfo): List<ShortClip> = withContext(Dispatchers.Default) {
    generateCandidateClips(videoInfo)
  }

  /**
   * Deterministic timeline candidates for personal editing when no transcript is available.
   * These are deliberately labeled as candidates rather than retention/virality measurements.
   */
  private fun generateCandidateClips(videoInfo: YouTubeVideoInfo): List<ShortClip> {
    val total = videoInfo.durationSeconds
    if (total < 10) return emptyList()

    val maxClip = minOf(30, total)
    val minClip = minOf(10, maxClip)
    val windows = if (total <= maxClip) {
      listOf(0)
    } else {
      listOf(
        (total * 0.12f).toInt(),
        (total * 0.38f).toInt(),
        (total * 0.64f).toInt(),
        (total * 0.84f).toInt()
      )
    }

    return windows.mapIndexedNotNull { index, rawStart ->
      val start = rawStart.coerceIn(0, (total - minClip).coerceAtLeast(0))
      val end = (start + maxClip).coerceAtMost(total)
      if (end - start < minClip) return@mapIndexedNotNull null
      ShortClip(
        videoId = videoInfo.id,
        clipIndex = index + 1,
        title = "Candidate Clip ${index + 1}",
        hookHeadline = "EDIT THIS HOOK",
        startSeconds = start,
        endSeconds = end,
        viralityScore = 0,
        whyViralReason = "Timeline candidate only. No audience-retention data was supplied.",
        keyTakeaway = "Add a transcript or review this segment manually.",
        suggestedHashtags = listOf("#Shorts"),
        youtubeShortsDescription = "Created with ClipMint from a user-provided source.",
        sampleSubtitles = emptyList()
      )
    }
  }

  fun generateRetentionCurve(clips: List<ShortClip>): List<RetentionPoint> {
    // Kept for compatibility with the existing UI, but values are explicitly neutral.
    if (clips.isEmpty()) return emptyList()
    return (0..60).map { step ->
      RetentionPoint(
        timeFraction = step / 60f,
        retentionPercent = 50,
        isPeak = false,
        associatedClipIndex = null
      )
    }
  }

  companion object {
    val PRESETS = listOf(
      YouTubeVideoInfo(
        id = "0e3GPea1Tyg",
        url = "https://www.youtube.com/watch?v=0e3GPea1Tyg",
        title = "$456,000 Squid Game In Real Life!",
        channelName = "MrBeast",
        durationSeconds = 1542,
        viewCountFormatted = "620M views",
        thumbnailUrl = "https://img.youtube.com/vi/0e3GPea1Tyg/hqdefault.jpg",
        description = "Sample metadata only; the source video itself is not bundled with ClipMint."
      ),
      YouTubeVideoInfo(
        id = "dQw4w9WgXcQ",
        url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        title = "Never Gonna Give You Up (Official Music Video)",
        channelName = "Rick Astley",
        durationSeconds = 213,
        viewCountFormatted = "1.5B views",
        thumbnailUrl = "https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
        description = "Sample metadata only; the source video itself is not bundled with ClipMint."
      ),
      YouTubeVideoInfo(
        id = "jNQXAC9IVRw",
        url = "https://www.youtube.com/watch?v=jNQXAC9IVRw",
        title = "Me at the zoo",
        channelName = "jawed",
        durationSeconds = 19,
        viewCountFormatted = "Unavailable",
        thumbnailUrl = "https://img.youtube.com/vi/jNQXAC9IVRw/hqdefault.jpg",
        description = "Sample metadata only."
      )
    )
  }
}
