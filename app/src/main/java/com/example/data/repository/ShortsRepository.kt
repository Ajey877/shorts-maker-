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

  fun resolveVideo(input: String): YouTubeVideoInfo = clipperService.resolveVideoInfo(input)

  suspend fun analyzeVideo(input: String): BackendAnalysisResult? {
    val url = input.trim()
    if (url.isBlank()) return null
    val backend = backendService.analyze(url, 4, 30) ?: return null
    return BackendAnalysisResult(backend.video, backend.clips)
  }

  suspend fun generateShortsForVideo(videoInfo: YouTubeVideoInfo): List<ShortClip> {
    val backend = backendService.analyze(videoInfo.url, 4, 30)
    if (!backend?.clips.isNullOrEmpty()) return backend!!.clips
    return clipperService.generateShorts(videoInfo)
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
