package com.example.data.repository

import com.example.data.local.ClipDao
import com.example.data.local.ClipEntity
import com.example.data.remote.ClipMintBackendService
import com.example.data.remote.GeminiClipperService
import com.example.model.RetentionPoint
import com.example.model.ShortClip
import com.example.model.YouTubeVideoInfo
import kotlinx.coroutines.flow.Flow

class ShortsRepository(
  private val clipDao: ClipDao,
  private val clipperService: GeminiClipperService = GeminiClipperService(),
  private val backendService: ClipMintBackendService = ClipMintBackendService()
) {
  val savedClips: Flow<List<ClipEntity>> = clipDao.getAllClips()
  val postedClips: Flow<List<ClipEntity>> = clipDao.getPostedClips()

  fun isClipSaved(clipId: String): Flow<Boolean> = clipDao.isClipSavedFlow(clipId)

  suspend fun analyzeVideo(input: String): BackendAnalysisResult? {
    val url = input.trim()
    if (url.isBlank()) return null

    backendService.analyze(url, 4, 30)?.let { backend ->
      if (backend.clips.isNotEmpty()) return BackendAnalysisResult(backend.video, backend.clips)
    }

    // Samples remain usable without a running backend. Real YouTube URLs do not
    // fall back to fabricated metadata or fake timestamps.
    val preset = GeminiClipperService.PRESETS.firstOrNull { preset ->
      preset.url == url || url.contains(preset.id, ignoreCase = true)
    } ?: return null

    return BackendAnalysisResult(
      video = preset,
      clips = clipperService.generateShorts(preset)
    )
  }

  fun getRetentionCurve(clips: List<ShortClip>): List<RetentionPoint> =
    clipperService.generateRetentionCurve(clips)

  suspend fun saveClip(clip: ShortClip, videoInfo: YouTubeVideoInfo) {
    clipDao.insertClip(
      ClipEntity.fromDomain(
        clip = clip,
        videoTitle = videoInfo.title,
        channelName = videoInfo.channelName,
        thumbnailUrl = videoInfo.thumbnailUrl
      )
    )
  }

  suspend fun deleteSavedClip(clipId: String) = clipDao.deleteClipById(clipId)

  suspend fun setPostedStatus(clipId: String, isPosted: Boolean) =
    clipDao.updatePostedStatus(clipId, isPosted)

  data class BackendAnalysisResult(
    val video: YouTubeVideoInfo,
    val clips: List<ShortClip>
  )
}
