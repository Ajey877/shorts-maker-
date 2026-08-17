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

  suspend fun generateShortsForVideo(videoInfo: YouTubeVideoInfo): List<ShortClip> {
    // Use the real backend when it is configured. It returns non-overlapping timestamps
    // from different parts of the source video so the four outputs are genuinely different.
    val backend = backendService.analyze(videoInfo.url, 4, 30)
    if (!backend?.clips.isNullOrEmpty()) return backend!!.clips
    return clipperService.generateShorts(videoInfo)
  }

  fun getRetentionCurve(clips: List<ShortClip>): List<RetentionPoint> =
    clipperService.generateRetentionCurve(clips)

  suspend fun saveClip(clip: ShortClip, videoInfo: YouTubeVideoInfo) {
    val entity = ClipEntity.fromDomain(
      clip = clip,
      videoTitle = videoInfo.title,
      channelName = videoInfo.channelName,
      thumbnailUrl = videoInfo.thumbnailUrl
    )
    clipDao.insertClip(entity)
  }

  suspend fun deleteSavedClip(clipId: String) {
    clipDao.deleteClipById(clipId)
  }

  suspend fun setPostedStatus(clipId: String, isPosted: Boolean) {
    clipDao.updatePostedStatus(clipId, isPosted)
  }
}
