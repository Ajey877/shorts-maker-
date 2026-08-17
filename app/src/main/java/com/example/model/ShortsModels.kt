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
  val description: String = ""
) {
  val formattedDuration: String
    get() {
      val m = durationSeconds / 60
      val s = durationSeconds % 60
      return String.format("%02d:%02d", m, s)
    }
}

enum class CaptionStyle(val displayName: String, val badgeColor: Long) {
  HORMOZI_BOLD("Hormozi Impact", 0xFFFFEA00), // Electric Yellow on Dark Box
  NEON_GLOW("Neon Cyber", 0xFF00E5FF),      // Neon Cyan with Glow
  CLEAN_MINIMAL("Clean Subtitle", 0xFFFFFFFF), // Crisp White with Drop Shadow
  PUNCH_RED("Viral Red Box", 0xFFFF2A55)     // Crimson Highlight Box
}

enum class FramingMode(val displayName: String) {
  CENTER_CROP("9:16 Smart Crop"),
  BLUR_BACKGROUND("Fit + Blurred Background"),
  SPLIT_SCREEN("Split Reaction / Gameplay")
}

data class SubtitlePhrase(
  val relativeSec: Float, // Relative to clip start
  val text: String,
  val highlightWord: String = ""
)

data class ShortClip(
  val id: String = UUID.randomUUID().toString(),
  val videoId: String,
  val clipIndex: Int, // 1, 2, 3, or 4
  val title: String,
  val hookHeadline: String,
  val startSeconds: Int,
  val endSeconds: Int,
  val viralityScore: Int, // 80 - 99
  val whyViralReason: String,
  val keyTakeaway: String,
  val suggestedHashtags: List<String>,
  val youtubeShortsDescription: String,
  val sampleSubtitles: List<SubtitlePhrase> = emptyList(),
  val isSaved: Boolean = false,
  val isPosted: Boolean = false
) {
  val durationSeconds: Int
    get() = (endSeconds - startSeconds).coerceIn(10, 30)

  val startTimestampFormatted: String
    get() = formatTime(startSeconds)

  val endTimestampFormatted: String
    get() = formatTime(endSeconds)

  val rangeFormatted: String
    get() = "$startTimestampFormatted - $endTimestampFormatted (${durationSeconds}s)"

  private fun formatTime(totalSec: Int): String {
    val m = totalSec / 60
    val s = totalSec % 60
    return String.format("%02d:%02d", m, s)
  }
}

data class RetentionPoint(
  val timeFraction: Float, // 0.0 to 1.0 along video
  val retentionPercent: Int, // 0 to 100
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
