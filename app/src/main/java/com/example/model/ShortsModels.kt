package com.example.model

import java.util.UUID

data class YouTubeVideoInfo(
  val id: String,
  val url: String,
  val title: String,
  val channelName: String,
  val durationSeconds: Int,
  val viewCountFormatted: String,
  val thumbnailUrl: String,
  val description: String = "",
  val isLocalMedia: Boolean = false
) {
  val formattedDuration: String
    get() {
      val m = durationSeconds / 60
      val s = durationSeconds % 60
      return String.format("%02d:%02d", m, s)
    }
}

enum class CaptionStyle(val displayName: String, val badgeColor: Long) {
  HORMOZI_BOLD("Bold Impact", 0xFFFFEA00),
  NEON_GLOW("Neon Cyber", 0xFF00E5FF),
  CLEAN_MINIMAL("Clean Subtitle", 0xFFFFFFFF),
  PUNCH_RED("Red Highlight", 0xFFFF2A55)
}

enum class FramingMode(val displayName: String) {
  CENTER_CROP("9:16 Smart Crop"),
  BLUR_BACKGROUND("Fit + Blurred Background"),
  SPLIT_SCREEN("Split Reaction / Gameplay")
}

data class SubtitlePhrase(
  val relativeSec: Float,
  val text: String,
  val highlightWord: String = ""
)

data class ShortClip(
  val id: String = UUID.randomUUID().toString(),
  val videoId: String,
  val clipIndex: Int,
  val title: String,
  val hookHeadline: String,
  val startSeconds: Int,
  val endSeconds: Int,
  val viralityScore: Int,
  val whyViralReason: String,
  val keyTakeaway: String,
  val suggestedHashtags: List<String>,
  val youtubeShortsDescription: String,
  val sampleSubtitles: List<SubtitlePhrase> = emptyList(),
  val isSaved: Boolean = false,
  val isPosted: Boolean = false
) {
  val durationSeconds: Int
    get() = (endSeconds - startSeconds).coerceIn(0, 30)

  val startTimestampFormatted: String
    get() = formatTime(startSeconds)

  val endTimestampFormatted: String
    get() = formatTime(endSeconds)

  val rangeFormatted: String
    get() = "$startTimestampFormatted - $endTimestampFormatted (${durationSeconds}s)"

  private fun formatTime(totalSec: Int): String {
    val safe = totalSec.coerceAtLeast(0)
    return String.format("%02d:%02d", safe / 60, safe % 60)
  }
}

data class RetentionPoint(
  val timeFraction: Float,
  val retentionPercent: Int,
  val isPeak: Boolean = false,
  val associatedClipIndex: Int? = null
)

data class VideoPreset(
  val title: String,
  val channel: String,
  val url: String,
  val durationSeconds: Int,
  val views: String,
  val categoryEmoji: String,
  val tag: String
)
