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
  val urlInput: String = "",
  val isAnalyzing: Boolean = false,
  val analysisStatusText: String = "Paste a YouTube URL to create Shorts",
  val currentVideo: YouTubeVideoInfo? = null,
  val clips: List<ShortClip> = emptyList(),
  val selectedClip: ShortClip? = null,
  val retentionPoints: List<RetentionPoint> = emptyList(),
  val isPlaying: Boolean = true,
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
  private var playbackJob: Job? = null

  init {
    val db = AppDatabase.getDatabase(application)
    repository = ShortsRepository(db.clipDao(), GeminiClipperService())
    savedClips = repository.savedClips.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
  }

  fun onUrlInputChanged(newUrl: String) = _uiState.update { it.copy(urlInput = newUrl) }

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
      _uiState.update { it.copy(isAnalyzing = true, analysisStatusText = "Connecting to ClipMint backend…") }
      try {
        val analysis = repository.analyzeVideo(input)
          ?: throw IllegalStateException("ClipMint backend did not return an analysis. Check the backend URL and try again.")
        if (analysis.clips.isEmpty()) throw IllegalStateException("No usable Shorts were returned.")
        val videoInfo = analysis.video
        val clips = analysis.clips
        val first = clips.first()
        _uiState.update {
          it.copy(
            isAnalyzing = false,
            analysisStatusText = "Found ${clips.size} Shorts from the transcript",
            currentVideo = videoInfo,
            clips = clips,
            selectedClip = first,
            retentionPoints = emptyList(),
            customHookHeadline = first.hookHeadline,
            playbackPositionSec = 0f,
            isPlaying = true
          )
        }
        startPlaybackLoop()
      } catch (e: Exception) {
        val message = e.message ?: "unknown error"
        _uiState.update {
          it.copy(
            isAnalyzing = false,
            currentVideo = null,
            clips = emptyList(),
            selectedClip = null,
            retentionPoints = emptyList(),
            analysisStatusText = "Could not process this YouTube video."
          )
        }
        showNotification(message)
      }
    }
  }

  fun selectClip(clip: ShortClip) {
    _uiState.update { it.copy(selectedClip = clip, customHookHeadline = clip.hookHeadline, playbackPositionSec = 0f, isPlaying = true) }
    startPlaybackLoop()
  }

  fun setCaptionStyle(style: CaptionStyle) = _uiState.update { it.copy(captionStyle = style) }
  fun setFramingMode(mode: FramingMode) = _uiState.update { it.copy(framingMode = mode) }
  fun onCustomHookChanged(hook: String) = _uiState.update { it.copy(customHookHeadline = hook) }

  fun togglePlayPause() {
    val newPlaying = !_uiState.value.isPlaying
    _uiState.update { it.copy(isPlaying = newPlaying) }
    if (newPlaying) startPlaybackLoop() else playbackJob?.cancel()
  }

  fun seekTo(relativeSec: Float) {
    val clip = _uiState.value.selectedClip ?: return
    _uiState.update { it.copy(playbackPositionSec = relativeSec.coerceIn(0f, clip.durationSeconds.toFloat())) }
  }

  fun updateClipTrim(newStart: Int, newEnd: Int) {
    val current = _uiState.value.selectedClip ?: return
    val duration = (newEnd - newStart).coerceIn(10, 30)
    val updated = current.copy(startSeconds = newStart.coerceAtLeast(0), endSeconds = newStart.coerceAtLeast(0) + duration)
    _uiState.update { state -> state.copy(selectedClip = updated, clips = state.clips.map { if (it.id == updated.id) updated else it }, playbackPositionSec = 0f) }
  }

  private fun startPlaybackLoop() {
    playbackJob?.cancel()
    playbackJob = viewModelScope.launch {
      while (isActive) {
        delay(100)
        val state = _uiState.value
        if (state.isPlaying && state.selectedClip != null) {
          val duration = state.selectedClip.durationSeconds.toFloat().coerceAtLeast(1f)
          val next = if (state.playbackPositionSec + 0.1f >= duration) 0f else state.playbackPositionSec + 0.1f
          _uiState.update { it.copy(playbackPositionSec = next) }
        }
      }
    }
  }

  fun saveCurrentClip() {
    val clip = _uiState.value.selectedClip ?: return
    val video = _uiState.value.currentVideo ?: return
    viewModelScope.launch { repository.saveClip(clip, video); showNotification("Saved to Shorts Library.") }
  }

  fun deleteSavedClip(clipId: String) = viewModelScope.launch { repository.deleteSavedClip(clipId); showNotification("Clip removed from library.") }
  fun togglePostedStatus(clipId: String, isPosted: Boolean) = viewModelScope.launch { repository.setPostedStatus(clipId, isPosted); showNotification(if (isPosted) "Marked as posted." else "Marked as ready to post.") }
  fun setActiveTab(tabIndex: Int) = _uiState.update { it.copy(activeTab = tabIndex) }
  fun setUploadDialogVisible(visible: Boolean) = _uiState.update { it.copy(showUploadDialog = visible) }

  fun copyShortsMetadata(context: Context, clip: ShortClip) {
    val text = buildString {
      appendLine(clip.title)
      appendLine()
      appendLine(clip.youtubeShortsDescription)
      appendLine()
      appendLine(clip.suggestedHashtags.joinToString(" "))
      appendLine()
      appendLine("Timestamp range: ${clip.rangeFormatted}")
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Shorts Metadata", text))
    Toast.makeText(context, "Copied Shorts metadata.", Toast.LENGTH_SHORT).show()
  }

  fun openYouTubeShortsUpload(context: Context, clip: ShortClip) {
    copyShortsMetadata(context, clip)
    try {
      context.startActivity(Intent(Intent.ACTION_VIEW).apply { data = Uri.parse("https://www.youtube.com/upload"); setPackage("com.google.android.youtube") })
    } catch (_: Exception) {
      try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://studio.youtube.com"))) }
      catch (_: Exception) { Toast.makeText(context, "Could not open YouTube.", Toast.LENGTH_SHORT).show() }
    }
  }

  fun shareShortsClip(context: Context, clip: ShortClip) {
    val body = """
      🎬 ClipMint Short

      📌 ${clip.title}
      ⏱️ ${clip.rangeFormatted}
      🎯 ${clip.hookHeadline}
      📊 AI selection score: ${clip.viralityScore}/100

      🏷️ ${clip.suggestedHashtags.joinToString(" ")}

      ${clip.youtubeShortsDescription}
    """.trimIndent()
    context.startActivity(Intent.createChooser(Intent().apply { action = Intent.ACTION_SEND; putExtra(Intent.EXTRA_TEXT, body); type = "text/plain" }, "Share ClipMint Short"))
  }

  private fun showNotification(message: String) {
    viewModelScope.launch {
      _uiState.update { it.copy(bannerNotification = message) }
      delay(2800)
      _uiState.update { it.copy(bannerNotification = null) }
    }
  }

  override fun onCleared() {
    playbackJob?.cancel()
    super.onCleared()
  }
}
