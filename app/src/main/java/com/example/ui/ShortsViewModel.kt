package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.ClipEntity
import com.example.data.media.ClipExportService
import com.example.data.remote.GeminiClipperService
import com.example.data.repository.ShortsRepository
import com.example.model.CaptionStyle
import com.example.model.FramingMode
import com.example.model.RetentionPoint
import com.example.model.ShortClip
import com.example.model.YouTubeVideoInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File


data class ShortsUiState(
  val urlInput: String = "",
  val isAnalyzing: Boolean = false,
  val analysisStatusText: String = "Import a video you own to begin.",
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
  val isExporting: Boolean = false,
  val exportedClipUri: Uri? = null,
  val bannerNotification: String? = null
)

class ShortsViewModel(application: Application) : AndroidViewModel(application) {
  private val repository: ShortsRepository
  private val exportService = ClipExportService(application)
  private val _uiState = MutableStateFlow(ShortsUiState())
  val uiState: StateFlow<ShortsUiState> = _uiState.asStateFlow()
  val savedClips: StateFlow<List<ClipEntity>>

  init {
    val db = AppDatabase.getDatabase(application)
    repository = ShortsRepository(db.clipDao(), GeminiClipperService())
    savedClips = repository.savedClips.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
  }

  fun onUrlInputChanged(newUrl: String) = _uiState.update { it.copy(urlInput = newUrl) }
  fun loadPreset(url: String) { _uiState.update { it.copy(urlInput = url) }; analyzeVideo(url) }

  fun loadLocalVideo(uri: Uri) {
    viewModelScope.launch {
      val retriever = MediaMetadataRetriever()
      try {
        retriever.setDataSource(getApplication<Application>(), uri)
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val durationSec = (durationMs / 1000L).toInt().coerceAtLeast(1)
        val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: "Imported video"
        val video = YouTubeVideoInfo(id = "local_${uri.hashCode().toUInt().toString(16)}", url = uri.toString(), title = title, channelName = "Local file", durationSeconds = durationSec, viewCountFormatted = "Local media", thumbnailUrl = "", description = "Imported local media", isLocalMedia = true)
        _uiState.update { it.copy(urlInput = uri.toString(), currentVideo = video, clips = emptyList(), selectedClip = null, retentionPoints = emptyList(), playbackPositionSec = 0f, isPlaying = false, exportedClipUri = null, analysisStatusText = "Video imported. Analyze it to create candidate clips.") }
      } catch (e: Exception) { showNotification("Could not read this video file.") }
      finally { retriever.release() }
    }
  }

  fun analyzeVideo(urlOrQuery: String = _uiState.value.urlInput) {
    if (urlOrQuery.isBlank()) return
    viewModelScope.launch {
      _uiState.update { it.copy(isAnalyzing = true, analysisStatusText = "Reading source media...") }
      try {
        val videoInfo = if (_uiState.value.currentVideo?.isLocalMedia == true && _uiState.value.currentVideo?.url == urlOrQuery) _uiState.value.currentVideo!! else repository.resolveVideo(urlOrQuery)
        _uiState.update { it.copy(currentVideo = videoInfo, analysisStatusText = if (videoInfo.isLocalMedia) "Finding safe candidate segments from the local video..." else "YouTube page loaded as metadata only. Import the source video for real editing.") }
        val generatedClips = repository.generateShortsForVideo(videoInfo)
        val firstClip = generatedClips.firstOrNull()
        _uiState.update { it.copy(isAnalyzing = false, clips = generatedClips, selectedClip = firstClip, retentionPoints = repository.getRetentionCurve(generatedClips), customHookHeadline = firstClip?.hookHeadline ?: "", playbackPositionSec = 0f, isPlaying = false, exportedClipUri = null, analysisStatusText = if (videoInfo.isLocalMedia) "Candidate segments ready. These are timeline candidates, not measured audience retention." else "Metadata-only mode. Import a local source to preview and export real video.") }
      } catch (e: Exception) { _uiState.update { it.copy(isAnalyzing = false, analysisStatusText = "Analysis failed: ${e.message ?: "unknown error"}") } }
    }
  }

  fun selectClip(clip: ShortClip) = _uiState.update { it.copy(selectedClip = clip, customHookHeadline = clip.hookHeadline, playbackPositionSec = 0f, isPlaying = false, exportedClipUri = null) }
  fun setCaptionStyle(style: CaptionStyle) = _uiState.update { it.copy(captionStyle = style) }
  fun setFramingMode(mode: FramingMode) = _uiState.update { it.copy(framingMode = mode) }
  fun onCustomHookChanged(hook: String) = _uiState.update { it.copy(customHookHeadline = hook) }
  fun togglePlayPause() = _uiState.update { it.copy(isPlaying = !it.isPlaying) }
  fun seekTo(relativeSec: Float) { val clip = _uiState.value.selectedClip ?: return; _uiState.update { it.copy(playbackPositionSec = relativeSec.coerceIn(0f, clip.durationSeconds.toFloat())) } }

  fun updateClipTrim(newStart: Int, newEnd: Int) {
    val current = _uiState.value.selectedClip ?: return
    val sourceDuration = _uiState.value.currentVideo?.durationSeconds ?: return
    if (sourceDuration < 2) return
    val start = newStart.coerceIn(0, sourceDuration - 1)
    val requestedEnd = newEnd.coerceIn(start + 1, sourceDuration)
    val end = if (requestedEnd - start < 10) (start + 10).coerceAtMost(sourceDuration) else requestedEnd.coerceAtMost(start + 30)
    if (end <= start) return
    val updated = current.copy(startSeconds = start, endSeconds = end)
    _uiState.update { state -> state.copy(selectedClip = updated, clips = state.clips.map { if (it.id == updated.id) updated else it }, playbackPositionSec = 0f, exportedClipUri = null) }
  }

  fun exportCurrentClip() {
    val video = _uiState.value.currentVideo ?: return
    val clip = _uiState.value.selectedClip ?: return
    if (!video.isLocalMedia) { showNotification("Import a local video before exporting."); return }
    if (_uiState.value.isExporting) return
    _uiState.update { it.copy(isExporting = true, exportedClipUri = null, bannerNotification = "Exporting MP4…") }
    exportService.export(Uri.parse(video.url), clip,
      onCompleted = { file ->
        _uiState.update { it.copy(isExporting = false, exportedClipUri = Uri.fromFile(file), bannerNotification = "MP4 exported successfully.") }
      },
      onError = { error ->
        _uiState.update { it.copy(isExporting = false, exportedClipUri = null, bannerNotification = "Export failed: ${error.message ?: "unknown error"}") }
      }
    )
  }

  fun shareExportedClip(context: Context) {
    val uri = _uiState.value.exportedClipUri ?: run { showNotification("Export the clip first."); return }
    runCatching { context.startActivity(Intent.createChooser(Intent().apply { action = Intent.ACTION_SEND; putExtra(Intent.EXTRA_STREAM, uri); type = "video/mp4"; addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Share ClipMint MP4")) }
      .onFailure { showNotification("Could not open the share sheet.") }
  }

  fun saveCurrentClip() { val clip = _uiState.value.selectedClip ?: return; val video = _uiState.value.currentVideo ?: return; viewModelScope.launch { repository.saveClip(clip, video); showNotification("Saved to ClipMint Library.") } }
  fun deleteSavedClip(clipId: String) = viewModelScope.launch { repository.deleteSavedClip(clipId); showNotification("Clip removed from library.") }
  fun togglePostedStatus(clipId: String, isPosted: Boolean) = viewModelScope.launch { repository.setPostedStatus(clipId, isPosted); showNotification(if (isPosted) "Marked as posted." else "Marked as ready.") }
  fun setActiveTab(tabIndex: Int) = _uiState.update { it.copy(activeTab = tabIndex) }
  fun setUploadDialogVisible(visible: Boolean) = _uiState.update { it.copy(showUploadDialog = visible) }

  fun copyShortsMetadata(context: Context, clip: ShortClip) {
    val text = buildString { appendLine(clip.title); appendLine(); appendLine(clip.youtubeShortsDescription); appendLine(); appendLine(clip.suggestedHashtags.joinToString(" ")); appendLine(); appendLine("Timestamp range: ${clip.rangeFormatted}") }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("ClipMint Metadata", text))
    Toast.makeText(context, "Metadata copied", Toast.LENGTH_SHORT).show()
  }

  fun openYouTubeShortsUpload(context: Context, clip: ShortClip) {
    copyShortsMetadata(context, clip)
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://studio.youtube.com"))) }.onFailure { Toast.makeText(context, "Could not open YouTube Studio", Toast.LENGTH_SHORT).show() }
  }

  fun shareShortsClip(context: Context, clip: ShortClip) {
    val exported = _uiState.value.exportedClipUri
    if (exported != null) { shareExportedClip(context); return }
    val body = "${clip.title}\n\n${clip.youtubeShortsDescription}\n\n${clip.suggestedHashtags.joinToString(" ")}\n\n${clip.rangeFormatted}"
    context.startActivity(Intent.createChooser(Intent().apply { action = Intent.ACTION_SEND; putExtra(Intent.EXTRA_TEXT, body); type = "text/plain" }, "Share ClipMint metadata"))
  }

  private fun showNotification(message: String) {
    _uiState.update { it.copy(bannerNotification = message) }
    viewModelScope.launch { kotlinx.coroutines.delay(2500); _uiState.update { it.copy(bannerNotification = null) } }
  }
}
