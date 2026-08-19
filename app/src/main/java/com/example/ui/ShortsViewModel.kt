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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShortsUiState(
  val urlInput: String = "",
  val clipCount: Int = 4,
  val clipLength: Int = 30,
  val isAnalyzing: Boolean = false,
  val analysisStatusText: String = "",
  val transcriptAvailable: Boolean = false,
  val currentVideo: YouTubeVideoInfo? = null,
  val clips: List<ShortClip> = emptyList(),
  val selectedClip: ShortClip? = null,
  val retentionPoints: List<RetentionPoint> = emptyList(),
  val isPlaying: Boolean = false,
  val playbackPositionSec: Float = 0f,
  val captionStyle: CaptionStyle = CaptionStyle.HORMOZI_BOLD,
  val framingMode: FramingMode = FramingMode.CENTER_CROP,
  val customHookHeadline: String = "",
  val activeTab: Int = 0,
  val showUploadDialog: Boolean = false,
  val bannerNotification: String? = null
)

class ShortsViewModel(application: Application) : AndroidViewModel(application) {
  private val repository: ShortsRepository
  private val _uiState = MutableStateFlow(ShortsUiState())
  val uiState: StateFlow<ShortsUiState> = _uiState.asStateFlow()
  val savedClips: StateFlow<List<ClipEntity>>

  init {
    val db = AppDatabase.getDatabase(application)
    repository = ShortsRepository(db.clipDao(), GeminiClipperService())
    savedClips = repository.savedClips.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList()
    )
  }

  fun onUrlInputChanged(newUrl: String) {
    _uiState.update { it.copy(urlInput = newUrl) }
  }

  fun setClipCount(count: Int) {
    _uiState.update { it.copy(clipCount = count.coerceIn(3, 4)) }
  }

  fun setClipLength(length: Int) {
    _uiState.update { it.copy(clipLength = if (length == 15) 15 else 30) }
  }

  fun loadPreset(url: String) {
    _uiState.update { it.copy(urlInput = url) }
    analyzeVideo(url)
  }

  fun analyzeVideo(urlOrQuery: String = _uiState.value.urlInput) {
    val input = urlOrQuery.trim()
    if (input.isBlank()) {
      showNotification("Paste a YouTube URL first.")
      return
    }

    viewModelScope.launch {
      val count = _uiState.value.clipCount
      val length = _uiState.value.clipLength
      _uiState.update {
        it.copy(
          isAnalyzing = true,
          analysisStatusText = "Connecting to ClipMint…",
          bannerNotification = null,
          clips = emptyList(),
          selectedClip = null,
          currentVideo = null,
          retentionPoints = emptyList()
        )
      }

      try {
        delay(150)
        _uiState.update { it.copy(analysisStatusText = "Reading video metadata…") }
        val result = repository.analyzeVideo(input, count, length)
          ?: throw IllegalStateException("Couldn't analyze this YouTube URL. Make sure the backend is online and the URL is valid.")

        _uiState.update {
          it.copy(
            currentVideo = result.video,
            transcriptAvailable = result.transcriptAvailable,
            analysisStatusText = if (result.transcriptAvailable) "Ranking transcript moments…" else "Finding safe clip ranges…"
          )
        }
        delay(250)

        val generatedClips = result.clips.take(count)
        if (generatedClips.isEmpty()) throw IllegalStateException("No usable Shorts were found in this video.")

        val retention = repository.getRetentionCurve(generatedClips)
        val firstClip = generatedClips.first()
        _uiState.update {
          it.copy(
            isAnalyzing = false,
            currentVideo = result.video,
            clips = generatedClips,
            selectedClip = firstClip,
            retentionPoints = retention,
            customHookHeadline = firstClip.hookHeadline,
            playbackPositionSec = 0f,
            isPlaying = false,
            analysisStatusText = ""
          )
        }
      } catch (error: Exception) {
        _uiState.update {
          it.copy(
            isAnalyzing = false,
            analysisStatusText = "",
            currentVideo = null,
            clips = emptyList(),
            selectedClip = null,
            retentionPoints = emptyList(),
            transcriptAvailable = false,
            isPlaying = false
          )
        }
        showNotification(error.message ?: "Could not analyze this YouTube video.")
      }
    }
  }

  fun selectClip(clip: ShortClip) {
    _uiState.update {
      it.copy(
        selectedClip = clip,
        customHookHeadline = clip.hookHeadline,
        playbackPositionSec = 0f,
        isPlaying = false
      )
    }
  }

  fun setPlaybackPosition(seconds: Float) {
    val duration = _uiState.value.selectedClip?.durationSeconds?.toFloat() ?: return
    _uiState.update { it.copy(playbackPositionSec = seconds.coerceIn(0f, duration)) }
  }

  fun setPlaying(playing: Boolean) {
    _uiState.update { it.copy(isPlaying = playing) }
  }

  fun setCaptionStyle(style: CaptionStyle) { _uiState.update { it.copy(captionStyle = style) } }
  fun setFramingMode(mode: FramingMode) { _uiState.update { it.copy(framingMode = mode) } }
  fun onCustomHookChanged(hook: String) { _uiState.update { it.copy(customHookHeadline = hook) } }

  fun updateClipTrim(newStart: Int, newEnd: Int) {
    val currentClip = _uiState.value.selectedClip ?: return
    val start = newStart.coerceAtLeast(0)
    val duration = (newEnd - newStart).coerceIn(10, 30)
    val updatedClip = currentClip.copy(startSeconds = start, endSeconds = start + duration)
    _uiState.update {
      it.copy(
        selectedClip = updatedClip,
        clips = it.clips.map { item -> if (item.id == updatedClip.id) updatedClip else item },
        playbackPositionSec = 0f,
        isPlaying = false
      )
    }
  }

  fun saveCurrentClip() {
    val clip = _uiState.value.selectedClip ?: return
    val video = _uiState.value.currentVideo ?: return
    viewModelScope.launch {
      repository.saveClip(clip, video)
      showNotification("Saved to ClipMint Library.")
    }
  }

  fun deleteSavedClip(clipId: String) {
    viewModelScope.launch { repository.deleteSavedClip(clipId); showNotification("Clip removed from library.") }
  }

  fun togglePostedStatus(clipId: String, isPosted: Boolean) {
    viewModelScope.launch {
      repository.setPostedStatus(clipId, isPosted)
      showNotification(if (isPosted) "Marked as posted." else "Marked as ready to post.")
    }
  }

  fun setActiveTab(tabIndex: Int) { _uiState.update { it.copy(activeTab = tabIndex) } }
  fun setUploadDialogVisible(visible: Boolean) { _uiState.update { it.copy(showUploadDialog = visible) } }

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
    clipboard.setPrimaryClip(ClipData.newPlainText("ClipMint metadata", textToCopy))
    Toast.makeText(context, "Metadata copied.", Toast.LENGTH_SHORT).show()
  }

  fun openYouTubeShortsUpload(context: Context, clip: ShortClip) {
    copyShortsMetadata(context, clip)
    try {
      context.startActivity(Intent(Intent.ACTION_VIEW).apply {
        data = Uri.parse("https://www.youtube.com/upload")
        setPackage("com.google.android.youtube")
      })
    } catch (_: Exception) {
      try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://studio.youtube.com")))
      } catch (_: Exception) {
        Toast.makeText(context, "Could not open YouTube.", Toast.LENGTH_SHORT).show()
      }
    }
  }

  fun shareShortsClip(context: Context, clip: ShortClip) {
    val shareBody = """
      ClipMint Short

      Title: ${clip.title}
      Segment: ${clip.rangeFormatted}
      Hook: "${clip.hookHeadline}"
      Score: ${clip.viralityScore}
      Tags: ${clip.suggestedHashtags.joinToString(" ")}

      ${clip.youtubeShortsDescription}
    """.trimIndent()
    context.startActivity(Intent.createChooser(Intent().apply {
      action = Intent.ACTION_SEND
      putExtra(Intent.EXTRA_TEXT, shareBody)
      type = "text/plain"
    }, "Share Short"))
  }

  private fun showNotification(message: String) {
    viewModelScope.launch {
      _uiState.update { it.copy(bannerNotification = message) }
      delay(3500)
      _uiState.update { it.copy(bannerNotification = null) }
    }
  }
}
