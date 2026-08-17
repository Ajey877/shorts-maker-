package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.ShortClip
import com.example.model.SubtitlePhrase

@Entity(tableName = "saved_clips")
data class ClipEntity(
  @PrimaryKey val id: String,
  val videoId: String,
  val videoTitle: String,
  val channelName: String,
  val thumbnailUrl: String,
  val clipIndex: Int,
  val title: String,
  val hookHeadline: String,
  val startSeconds: Int,
  val endSeconds: Int,
  val viralityScore: Int,
  val whyViralReason: String,
  val keyTakeaway: String,
  val suggestedHashtagsCsv: String,
  val youtubeShortsDescription: String,
  val subtitlesJson: String, // simple formatted subtitle lines
  val isPosted: Boolean = false,
  val createdAt: Long = System.currentTimeMillis()
) {
  fun toDomain(): ShortClip {
    val hashtags = suggestedHashtagsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val subs = subtitlesJson.split(";;").mapNotNull { line ->
      val parts = line.split("||")
      if (parts.size >= 2) {
        val relSec = parts[0].toFloatOrNull() ?: 0f
        val text = parts[1]
        val hl = parts.getOrNull(2) ?: ""
        SubtitlePhrase(relSec, text, hl)
      } else null
    }

    return ShortClip(
      id = id,
      videoId = videoId,
      clipIndex = clipIndex,
      title = title,
      hookHeadline = hookHeadline,
      startSeconds = startSeconds,
      endSeconds = endSeconds,
      viralityScore = viralityScore,
      whyViralReason = whyViralReason,
      keyTakeaway = keyTakeaway,
      suggestedHashtags = hashtags,
      youtubeShortsDescription = youtubeShortsDescription,
      sampleSubtitles = subs,
      isSaved = true,
      isPosted = isPosted
    )
  }

  companion object {
    fun fromDomain(
      clip: ShortClip,
      videoTitle: String,
      channelName: String,
      thumbnailUrl: String
    ): ClipEntity {
      val hashtagsCsv = clip.suggestedHashtags.joinToString(",")
      val subsJson = clip.sampleSubtitles.joinToString(";;") {
        "${it.relativeSec}||${it.text}||${it.highlightWord}"
      }

      return ClipEntity(
        id = clip.id,
        videoId = clip.videoId,
        videoTitle = videoTitle,
        channelName = channelName,
        thumbnailUrl = thumbnailUrl,
        clipIndex = clip.clipIndex,
        title = clip.title,
        hookHeadline = clip.hookHeadline,
        startSeconds = clip.startSeconds,
        endSeconds = clip.endSeconds,
        viralityScore = clip.viralityScore,
        whyViralReason = clip.whyViralReason,
        keyTakeaway = clip.keyTakeaway,
        suggestedHashtagsCsv = hashtagsCsv,
        youtubeShortsDescription = clip.youtubeShortsDescription,
        subtitlesJson = subsJson,
        isPosted = clip.isPosted
      )
    }
  }
}
