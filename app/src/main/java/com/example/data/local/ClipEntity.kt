package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.ShortClip
import com.example.model.SubtitlePhrase
import org.json.JSONArray
import org.json.JSONObject

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
  val subtitlesJson: String,
  val isPosted: Boolean = false,
  val createdAt: Long = System.currentTimeMillis()
) {
  fun toDomain(): ShortClip {
    val hashtags = suggestedHashtagsCsv.split(",")
      .map { it.trim() }
      .filter { it.isNotEmpty() }

    val subs = runCatching {
      val array = JSONArray(subtitlesJson)
      buildList {
        for (i in 0 until array.length()) {
          val obj = array.optJSONObject(i) ?: continue
          val text = obj.optString("text")
          if (text.isNotBlank()) {
            add(
              SubtitlePhrase(
                relativeSec = obj.optDouble("relativeSec", 0.0).toFloat(),
                text = text,
                highlightWord = obj.optString("highlightWord")
              )
            )
          }
        }
      }
    }.getOrElse { emptyList() }

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
      val subtitles = JSONArray()
      clip.sampleSubtitles.forEach { subtitle ->
        subtitles.put(
          JSONObject().apply {
            put("relativeSec", subtitle.relativeSec)
            put("text", subtitle.text)
            put("highlightWord", subtitle.highlightWord)
          }
        )
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
        suggestedHashtagsCsv = clip.suggestedHashtags.joinToString(","),
        youtubeShortsDescription = clip.youtubeShortsDescription,
        subtitlesJson = subtitles.toString(),
        isPosted = clip.isPosted
      )
    }
  }
}
