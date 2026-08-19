package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.remote.ClipMintBackendService
import com.example.ui.ShortsViewModel
import com.example.ui.components.ExportUploadDialog
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  private val viewModel: ShortsViewModel by viewModels()
  private val backendService = ClipMintBackendService()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme(darkTheme = isSystemInDarkTheme(), dynamicColor = true) {
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val savedClips by viewModel.savedClips.collectAsStateWithLifecycle()
        val context = LocalContext.current
        var isRendering by remember { mutableStateOf(false) }
        var renderingBatch by remember { mutableStateOf(false) }
        var renderProgress by remember { mutableFloatStateOf(0f) }

        val selectedClip = uiState.selectedClip
        val currentVideo = uiState.currentVideo
        val previewUrl = if (selectedClip != null && currentVideo != null) {
          backendService.previewUrl(currentVideo.url, selectedClip)
        } else null

        Scaffold(
          modifier = Modifier.fillMaxSize(),
          bottomBar = {
            NavigationBar(
              modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
              containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
              NavigationBarItem(
                selected = uiState.activeTab == 0,
                onClick = { viewModel.setActiveTab(0) },
                icon = { Icon(if (uiState.activeTab == 0) Icons.Filled.SmartDisplay else Icons.Outlined.SmartDisplay, contentDescription = "Studio") },
                label = { Text("Studio", fontWeight = if (uiState.activeTab == 0) FontWeight.Bold else FontWeight.Normal) }
              )
              NavigationBarItem(
                selected = uiState.activeTab == 1,
                onClick = { viewModel.setActiveTab(1) },
                icon = { Icon(if (uiState.activeTab == 1) Icons.Filled.Bookmarks else Icons.Outlined.Bookmarks, contentDescription = "Library") },
                label = { Text("Library", fontWeight = if (uiState.activeTab == 1) FontWeight.Bold else FontWeight.Normal) }
              )
              NavigationBarItem(
                selected = uiState.activeTab == 2,
                onClick = { viewModel.setActiveTab(2) },
                icon = { Icon(if (uiState.activeTab == 2) Icons.Filled.Settings else Icons.Outlined.Settings, contentDescription = "Settings") },
                label = { Text("Settings", fontWeight = if (uiState.activeTab == 2) FontWeight.Bold else FontWeight.Normal) }
              )
            }
          }
        ) { innerPadding ->
          Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when (uiState.activeTab) {
              0 -> HomeScreen(
                urlInput = uiState.urlInput,
                clipCount = uiState.clipCount,
                clipLength = uiState.clipLength,
                isAnalyzing = uiState.isAnalyzing,
                analysisStatusText = uiState.analysisStatusText,
                transcriptAvailable = uiState.transcriptAvailable,
                currentVideo = currentVideo,
                clips = uiState.clips,
                selectedClip = selectedClip,
                retentionPoints = uiState.retentionPoints,
                isPlaying = uiState.isPlaying,
                playbackPositionSec = uiState.playbackPositionSec,
                captionStyle = uiState.captionStyle,
                framingMode = uiState.framingMode,
                customHookHeadline = uiState.customHookHeadline,
                previewUrl = previewUrl,
                savedClipsCount = savedClips.size,
                onUrlInputChanged = viewModel::onUrlInputChanged,
                onClipCountChanged = viewModel::setClipCount,
                onClipLengthChanged = viewModel::setClipLength,
                onAnalyzeClicked = viewModel::analyzeVideo,
                onLoadPreset = viewModel::loadPreset,
                onSelectClip = viewModel::selectClip,
                onSetPlaying = viewModel::setPlaying,
                onSetPlaybackPosition = viewModel::setPlaybackPosition,
                onCaptionStyleChanged = viewModel::setCaptionStyle,
                onFramingModeChanged = viewModel::setFramingMode,
                onHookHeadlineChanged = viewModel::onCustomHookChanged,
                onTrimUpdated = viewModel::updateClipTrim,
                onSaveClip = viewModel::saveCurrentClip,
                onCopyMetadata = { clip -> viewModel.copyShortsMetadata(context, clip) },
                onUploadShorts = { clip ->
                  viewModel.selectClip(clip)
                  viewModel.setUploadDialogVisible(true)
                },
                onShareClip = { clip -> viewModel.shareShortsClip(context, clip) },
                onExportAll = {
                  if (currentVideo != null && uiState.clips.isNotEmpty() && !isRendering) {
                    isRendering = true
                    renderingBatch = true
                    renderProgress = 0f
                    val batchId = backendService.enqueueBatchDownload(
                      context = context,
                      videoUrl = currentVideo.url,
                      clips = uiState.clips.take(uiState.clipCount),
                      onProgress = { renderProgress = it },
                      onComplete = { isRendering = false; renderingBatch = false; renderProgress = 1f },
                      onError = { message ->
                        isRendering = false
                        renderingBatch = false
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                      }
                    )
                    if (batchId == null) {
                      isRendering = false
                      renderingBatch = false
                      Toast.makeText(context, "Batch export is not available.", Toast.LENGTH_LONG).show()
                    }
                  }
                },
                onOpenLibrary = { viewModel.setActiveTab(1) }
              )
              1 -> LibraryScreen(
                savedClips = savedClips,
                onSelectAndPreviewClip = { clip ->
                  viewModel.selectClip(clip)
                  viewModel.setActiveTab(0)
                },
                onCopyMetadata = { clip -> viewModel.copyShortsMetadata(context, clip) },
                onOpenYouTubeShorts = { clip ->
                  viewModel.selectClip(clip)
                  viewModel.setUploadDialogVisible(true)
                },
                onTogglePostedStatus = viewModel::togglePostedStatus,
                onDeleteClip = viewModel::deleteSavedClip,
                onGoToStudio = { viewModel.setActiveTab(0) }
              )
              2 -> SettingsScreen()
            }

            if (uiState.activeTab == 0 && selectedClip != null && currentVideo != null && !isRendering) {
              FloatingActionButton(
                onClick = {
                  isRendering = true
                  renderingBatch = false
                  renderProgress = 0f
                  val jobId = backendService.enqueueDownload(
                    context = context,
                    videoUrl = currentVideo.url,
                    clip = selectedClip,
                    onProgress = { renderProgress = it },
                    onComplete = { isRendering = false; renderProgress = 1f },
                    onError = { isRendering = false }
                  )
                  if (jobId == null) {
                    isRendering = false
                    Toast.makeText(context, "Render service is not configured.", Toast.LENGTH_LONG).show()
                  }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
              ) {
                Icon(Icons.Default.Download, contentDescription = "Export selected Short")
              }
            }

            if (isRendering) {
              Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp, vertical = 12.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                tonalElevation = 3.dp
              ) {
                Column(Modifier.padding(14.dp)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text(
                      if (renderingBatch) "Exporting all Shorts…" else "Rendering Short…",
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.SemiBold,
                      modifier = Modifier.padding(start = 8.dp)
                    )
                    Text(" ${(renderProgress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 8.dp))
                  }
                  LinearProgressIndicator(progress = renderProgress, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                  Text("Keep ClipMint open until export finishes.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
              }
            }

            uiState.bannerNotification?.let { msg ->
              Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                tonalElevation = 3.dp
              ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                  Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
                }
              }
            }

            if (uiState.showUploadDialog && selectedClip != null) {
              ExportUploadDialog(
                clip = selectedClip,
                onDismiss = { viewModel.setUploadDialogVisible(false) },
                onCopyMetadata = { viewModel.copyShortsMetadata(context, selectedClip) },
                onOpenYouTubeShorts = { viewModel.openYouTubeShortsUpload(context, selectedClip) },
                onMarkPosted = { viewModel.togglePostedStatus(selectedClip.id, true) }
              )
            }
          }
        }
      }
    }
  }
}
