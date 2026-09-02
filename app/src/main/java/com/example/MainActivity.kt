package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.ShortsViewModel
import com.example.ui.components.ExportUploadDialog
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.theme.BrightCrimson
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  private val viewModel: ShortsViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme(darkTheme = true) {
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        val savedClips by viewModel.savedClips.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val videoPicker = rememberLauncherForActivityResult(
          contract = ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
          uri?.let {
            try {
              contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
              // Some providers do not support persistable permissions; the current grant is still usable.
            }
            viewModel.loadLocalVideo(it)
          }
        }

        Scaffold(
          modifier = Modifier.fillMaxSize(),
          bottomBar = {
            NavigationBar(
              containerColor = MaterialTheme.colorScheme.surface,
              contentColor = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
              NavigationBarItem(
                selected = uiState.activeTab == 0,
                onClick = { viewModel.setActiveTab(0) },
                icon = { Icon(if (uiState.activeTab == 0) Icons.Filled.SmartDisplay else Icons.Outlined.SmartDisplay, contentDescription = "Studio") },
                label = { Text("Shorts Studio", fontWeight = if (uiState.activeTab == 0) FontWeight.Bold else FontWeight.Normal) },
                colors = NavigationBarItemDefaults.colors(
                  selectedIconColor = MaterialTheme.colorScheme.primary,
                  selectedTextColor = MaterialTheme.colorScheme.primary,
                  indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                  unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                  unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
              )
              NavigationBarItem(
                selected = uiState.activeTab == 1,
                onClick = { viewModel.setActiveTab(1) },
                icon = { Icon(if (uiState.activeTab == 1) Icons.Filled.Bookmarks else Icons.Outlined.Bookmarks, contentDescription = "Library") },
                label = { Text("Saved (${savedClips.size})", fontWeight = if (uiState.activeTab == 1) FontWeight.Bold else FontWeight.Normal) },
                colors = NavigationBarItemDefaults.colors(
                  selectedIconColor = MaterialTheme.colorScheme.primary,
                  selectedTextColor = MaterialTheme.colorScheme.primary,
                  indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                  unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                  unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
              )
            }
          }
        ) { innerPadding ->
          Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when (uiState.activeTab) {
              0 -> HomeScreen(
                urlInput = uiState.urlInput,
                isAnalyzing = uiState.isAnalyzing,
                analysisStatusText = uiState.analysisStatusText,
                currentVideo = uiState.currentVideo,
                clips = uiState.clips,
                selectedClip = uiState.selectedClip,
                retentionPoints = uiState.retentionPoints,
                isPlaying = uiState.isPlaying,
                playbackPositionSec = uiState.playbackPositionSec,
                captionStyle = uiState.captionStyle,
                framingMode = uiState.framingMode,
                customHookHeadline = uiState.customHookHeadline,
                savedClipsCount = savedClips.size,
                onUrlInputChanged = viewModel::onUrlInputChanged,
                onAnalyzeClicked = { viewModel.analyzeVideo() },
                onLoadPreset = viewModel::loadPreset,
                onSelectClip = viewModel::selectClip,
                onTogglePlayPause = viewModel::togglePlayPause,
                onSeek = viewModel::seekTo,
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
                onOpenLibrary = { viewModel.setActiveTab(1) }
              )
              1 -> LibraryScreen(
                savedClips = savedClips,
                onSelectAndPreviewClip = { clip -> viewModel.selectClip(clip); viewModel.setActiveTab(0) },
                onCopyMetadata = { clip -> viewModel.copyShortsMetadata(context, clip) },
                onOpenYouTubeShorts = { clip -> viewModel.selectClip(clip); viewModel.setUploadDialogVisible(true) },
                onTogglePostedStatus = viewModel::togglePostedStatus,
                onDeleteClip = viewModel::deleteSavedClip,
                onGoToStudio = { viewModel.setActiveTab(0) }
              )
            }

            if (uiState.activeTab == 0) {
              FloatingActionButton(
                onClick = { videoPicker.launch(arrayOf("video/*")) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
              ) {
                Icon(Icons.Filled.SmartDisplay, contentDescription = "Import video")
              }
            }

            AnimatedVisibility(
              visible = uiState.bannerNotification != null,
              enter = slideInVertically { -it } + fadeIn(),
              exit = slideOutVertically { -it } + fadeOut(),
              modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp, start = 20.dp, end = 20.dp)
            ) {
              uiState.bannerNotification?.let { msg ->
                Surface(shape = RoundedCornerShape(12.dp), color = BrightCrimson, shadowElevation = 8.dp) {
                  Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(msg, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                  }
                }
              }
            }

            if (uiState.showUploadDialog && uiState.selectedClip != null) {
              ExportUploadDialog(
                clip = uiState.selectedClip!!,
                onDismiss = { viewModel.setUploadDialogVisible(false) },
                onCopyMetadata = { viewModel.copyShortsMetadata(context, uiState.selectedClip!!) },
                onOpenYouTubeShorts = { viewModel.openYouTubeShortsUpload(context, uiState.selectedClip!!) },
                onMarkPosted = { viewModel.togglePostedStatus(uiState.selectedClip!!.id, true) }
              )
            }
          }
        }
      }
    }
  }
}
