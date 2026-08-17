package com.example.data.repository

import com.example.data.local.ClipDao
import com.example.data.local.ClipEntity
import com.example.data.remote.GeminiClipperService
import com.example.model.RetentionPoint
import com.example.model.ShortClip
import com.example.model.YouTubeVideoInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ShortsRepository(
  private val clipDao: ClipDao,
  private val clipperService: GeminiClipperService = GeminiClipperService()
) {

  val savedClips: Flow<List<ClipEntity>> = clipDao.getAllClips()
  val postedClips: Flow<List<ClipEntity>> = clipDao.getPostedClips()

  fun isClipSaved(clipId: String): Flow<Boolean> = clipDao.isClipSavedFlow(clipId)

  fun resolveVideo(input: String): YouTubeVideoInfo {
    return clipperService.resolveVideoInfo(input)
  }

  suspend fun generateShortsForVideo(videoInfo: YouTubeVideoInfo): List<ShortClip> {
    return clipperService.generateShorts(videoInfo)
  }

  fun getRetentionCurve(clips: List<ShortClip>): List<RetentionPoint> {
    return clipperService.generateRetentionCurve(clips)
  }

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
