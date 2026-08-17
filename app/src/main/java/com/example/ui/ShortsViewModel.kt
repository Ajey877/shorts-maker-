package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.ClipEntity
import com.example.data.remote.GeminiClipperService
import com.example.data.repository.ShortsRepository
import com.example.model.CaptionStyle
import com.example.model.FramingMode
import com.example.model.RetentionPoint
import com.example.model.ShortClip
import com.example.model.YouTubeVideoInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ShortsUiState(
  val urlInput: String = "https://www.youtube.com/watch?v=0e3GPea1Tyg",
  val isAnalyzing: Boolean = false,
  val analysisStatusText: String = "",
  val currentVideo: YouTubeVideoInfo? = null,
  val clips: List<ShortClip> = emptyList(),
  val selectedClip: ShortClip? = null,
  val retentionPoints: List<RetentionPoint> = emptyList(),
  val isPlaying: Boolean = true,
  val playbackPositionSec: Float = 0f,
  val captionStyle: CaptionStyle = CaptionStyle.HORMOZI_BOLD,
  val framingMode: FramingMode = FramingMode.CENTER_CROP,
  val customHookHeadline: String = "",
  val activeTab: Int = 0, // 0 = Studio / Generator, 1 = Library
  val showUploadDialog: Boolean = false,
  val bannerNotification: String? = null
)

class ShortsViewModel(application: Application) : AndroidViewModel(application) {

  private val repository: ShortsRepository

  private val _uiState = MutableStateFlow(ShortsUiState())
  val uiState: StateFlow<ShortsUiState> = _uiState.asStateFlow()

  val savedClips: StateFlow<List<ClipEntity>>

  private var playbackJob: Job? = null

  init {
    val db = AppDatabase.getDatabase(application)
    repository = ShortsRepository(db.clipDao(), GeminiClipperService())

    savedClips = repository.savedClips.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList()
    )

    // Load initial default video
    loadPreset(GeminiClipperService.PRESETS[0].url)
  }

  fun onUrlInputChanged(newUrl: String) {
    _uiState.update { it.copy(urlInput = newUrl) }
  }

  fun loadPreset(url: String) {
    _uiState.update { it.copy(urlInput = url) }
    analyzeVideo(url)
  }

  fun analyzeVideo(urlOrQuery: String = _uiState.value.urlInput) {
    if (urlOrQuery.isBlank()) return

    viewModelScope.launch {
      _uiState.update {
        it.copy(
          isAnalyzing = true,
          analysisStatusText = "Connecting to YouTube & parsing video stream..."
        )
      }

      delay(300)
      val videoInfo = repository.resolveVideo(urlOrQuery)

      _uiState.update {
        it.copy(
          currentVideo = videoInfo,
          analysisStatusText = "AI scanning audience retention heatmap & most-replayed spikes..."
        )
      }

      delay(600)
      _uiState.update {
        it.copy(
          analysisStatusText = "Extracting top 3-4 viral 10-30s highlight hooks with Gemini..."
        )
      }

      val generatedClips = repository.generateShortsForVideo(videoInfo)
      val retention = repository.getRetentionCurve(generatedClips)

      val firstClip = generatedClips.firstOrNull()

      _uiState.update {
        it.copy(
          isAnalyzing = false,
          currentVideo = videoInfo,
          clips = generatedClips,
          selectedClip = firstClip,
          retentionPoints = retention,
          customHookHeadline = firstClip?.hookHeadline ?: "",
          playbackPositionSec = 0f,
          isPlaying = true
        )
      }

      startPlaybackLoop()
    }
  }

  fun selectClip(clip: ShortClip) {
    _uiState.update {
      it.copy(
        selectedClip = clip,
        customHookHeadline = clip.hookHeadline,
        playbackPositionSec = 0f,
        isPlaying = true
      )
    }
    startPlaybackLoop()
  }

  fun setCaptionStyle(style: CaptionStyle) {
    _uiState.update { it.copy(captionStyle = style) }
  }

  fun setFramingMode(mode: FramingMode) {
    _uiState.update { it.copy(framingMode = mode) }
  }

  fun onCustomHookChanged(hook: String) {
    _uiState.update { it.copy(customHookHeadline = hook) }
  }

  fun togglePlayPause() {
    val newPlaying = !_uiState.value.isPlaying
    _uiState.update { it.copy(isPlaying = newPlaying) }
    if (newPlaying) {
      startPlaybackLoop()
    } else {
      playbackJob?.cancel()
    }
  }

  fun seekTo(relativeSec: Float) {
    val clip = _uiState.value.selectedClip ?: return
    val clamped = relativeSec.coerceIn(0f, clip.durationSeconds.toFloat())
    _uiState.update { it.copy(playbackPositionSec = clamped) }
  }

  fun updateClipTrim(newStart: Int, newEnd: Int) {
    val currentClip = _uiState.value.selectedClip ?: return
    val clampedDuration = (newEnd - newStart).coerceIn(10, 30)
    val actualEnd = newStart + clampedDuration

    val updatedClip = currentClip.copy(
      startSeconds = newStart,
      endSeconds = actualEnd
    )

    val updatedList = _uiState.value.clips.map {
      if (it.id == updatedClip.id) updatedClip else it
    }

    _uiState.update {
      it.copy(
        selectedClip = updatedClip,
        clips = updatedList,
        playbackPositionSec = 0f
      )
    }
  }

  private fun startPlaybackLoop() {
    playbackJob?.cancel()
    playbackJob = viewModelScope.launch {
      while (isActive) {
        delay(100)
        val state = _uiState.value
        if (state.isPlaying && state.selectedClip != null) {
          val duration = state.selectedClip.durationSeconds.toFloat()
          var nextPos = state.playbackPositionSec + 0.1f
          if (nextPos >= duration) {
            nextPos = 0f // Loop seamlessly
          }
          _uiState.update { it.copy(playbackPositionSec = nextPos) }
        }
      }
    }
  }

  fun saveCurrentClip() {
    val clip = _uiState.value.selectedClip ?: return
    val video = _uiState.value.currentVideo ?: return

    viewModelScope.launch {
      repository.saveClip(clip, video)
      showNotification("Saved \"${clip.title}\" to Shorts Library! 💾")
    }
  }

  fun deleteSavedClip(clipId: String) {
    viewModelScope.launch {
      repository.deleteSavedClip(clipId)
      showNotification("Clip removed from library.")
    }
  }

  fun togglePostedStatus(clipId: String, isPosted: Boolean) {
    viewModelScope.launch {
      repository.setPostedStatus(clipId, isPosted)
      showNotification(if (isPosted) "Marked as Posted to YouTube Shorts! 🎉" else "Marked as Ready to Post.")
    }
  }

  fun setActiveTab(tabIndex: Int) {
    _uiState.update { it.copy(activeTab = tabIndex) }
  }

  fun setUploadDialogVisible(visible: Boolean) {
    _uiState.update { it.copy(showUploadDialog = visible) }
  }

  fun copyShortsMetadata(context: Context, clip: ShortClip) {
    val textToCopy = buildString {
      appendLine(clip.title)
      appendLine()
      appendLine(clip.youtubeShortsDescription)
      appendLine()
      appendLine(clip.suggestedHashtags.joinToString(" "))
      appendLine()
      appendLine("Timestamp range: ${clip.rangeFormatted}")
    }

    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clipData = ClipData.newPlainText("Shorts Metadata", textToCopy)
    clipboard.setPrimaryClip(clipData)

    Toast.makeText(context, "Copied Title, Description & Hashtags to Clipboard! 📋", Toast.LENGTH_SHORT).show()
  }

  fun openYouTubeShortsUpload(context: Context, clip: ShortClip) {
    // 1. Copy metadata to clipboard automatically so the user can immediately paste in YouTube
    copyShortsMetadata(context, clip)

    // 2. Launch YouTube app or YouTube Upload intent
    try {
      val youtubeIntent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse("https://www.youtube.com/upload")
        setPackage("com.google.android.youtube")
      }
      context.startActivity(youtubeIntent)
    } catch (e: Exception) {
      // Fallback to web browser or generic upload
      try {
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://studio.youtube.com"))
        context.startActivity(webIntent)
      } catch (ex: Exception) {
        Toast.makeText(context, "Could not open YouTube app.", Toast.LENGTH_SHORT).show()
      }
    }
  }

  fun shareShortsClip(context: Context, clip: ShortClip) {
    val shareBody = """
      🎬 Ready-to-Post YouTube Short from ShortsCut:
      
      📌 Title: ${clip.title}
      ⏱️ Segment: ${clip.rangeFormatted}
      🎯 Viral Hook: "${clip.hookHeadline}"
      🔥 Virality Score: ${clip.viralityScore}% Peak Retention
      
      🏷️ Tags: ${clip.suggestedHashtags.joinToString(" ")}
      
      📝 Description:
      ${clip.youtubeShortsDescription}
    """.trimIndent()

    val sendIntent = Intent().apply {
      action = Intent.ACTION_SEND
      putExtra(Intent.EXTRA_TEXT, shareBody)
      type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, "Share YouTube Short Clip")
    context.startActivity(shareIntent)
  }

  private fun showNotification(message: String) {
    viewModelScope.launch {
      _uiState.update { it.copy(bannerNotification = message) }
      delay(2800)
      _uiState.update { it.copy(bannerNotification = null) }
    }
  }

  override fun onCleared() {
    super.onCleared()
    playbackJob?.cancel()
  }
}
