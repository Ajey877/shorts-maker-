package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.remote.GeminiClipperService
import com.example.model.CaptionStyle
import com.example.model.FramingMode
import com.example.model.RetentionPoint
import com.example.model.ShortClip
import com.example.model.YouTubeVideoInfo
import com.example.ui.components.ClipCard
import com.example.ui.components.RetentionHeatmapView
import com.example.ui.components.ShortsStudioPlayer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
  urlInput: String,
  clipCount: Int,
  clipLength: Int,
  isAnalyzing: Boolean,
  analysisStatusText: String,
  transcriptAvailable: Boolean,
  currentVideo: YouTubeVideoInfo?,
  clips: List<ShortClip>,
  selectedClip: ShortClip?,
  retentionPoints: List<RetentionPoint>,
  isPlaying: Boolean,
  playbackPositionSec: Float,
  captionStyle: CaptionStyle,
  framingMode: FramingMode,
  customHookHeadline: String,
  previewUrl: String?,
  savedClipsCount: Int,
  onUrlInputChanged: (String) -> Unit,
  onClipCountChanged: (Int) -> Unit,
  onClipLengthChanged: (Int) -> Unit,
  onAnalyzeClicked: () -> Unit,
  onLoadPreset: (String) -> Unit,
  onSelectClip: (ShortClip) -> Unit,
  onSetPlaying: (Boolean) -> Unit,
  onSetPlaybackPosition: (Float) -> Unit,
  onCaptionStyleChanged: (CaptionStyle) -> Unit,
  onFramingModeChanged: (FramingMode) -> Unit,
  onHookHeadlineChanged: (String) -> Unit,
  onTrimUpdated: (Int, Int) -> Unit,
  onSaveClip: () -> Unit,
  onCopyMetadata: (ShortClip) -> Unit,
  onUploadShorts: (ShortClip) -> Unit,
  onShareClip: (ShortClip) -> Unit,
  onOpenLibrary: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current

  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      CenterAlignedTopAppBar(
        title = {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ClipMint", fontWeight = FontWeight.Bold)
            Text("AI Shorts Studio", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        },
        navigationIcon = {
          Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.padding(start = 12.dp)
          ) {
            Icon(Icons.Default.SmartDisplay, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(8.dp))
          }
        },
        actions = {
          IconButton(onClick = onOpenLibrary) {
            Icon(Icons.Default.Bookmarks, contentDescription = "Open library")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
      )
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .imePadding()
        .padding(innerPadding)
        .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.extraLarge
      ) {
        Column(Modifier.padding(16.dp)) {
          Text("Create a Short", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
          Text(
            "Paste a YouTube link. ClipMint finds strong moments, prepares captions, and opens them in the editor.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
          )

          Spacer(Modifier.height(12.dp))
          OutlinedTextField(
            value = urlInput,
            onValueChange = onUrlInputChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
            trailingIcon = {
              Row {
                if (urlInput.isNotEmpty()) {
                  IconButton(onClick = { onUrlInputChanged("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear URL")
                  }
                }
                IconButton(onClick = {
                  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                  val pasted = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString().orEmpty()
                  if (pasted.isNotBlank()) onUrlInputChanged(pasted)
                }) {
                  Icon(Icons.Default.ContentPaste, contentDescription = "Paste URL")
                }
              }
            },
            label = { Text("YouTube URL") },
            placeholder = { Text("https://youtube.com/watch?v=…") },
            supportingText = { Text("Use a full YouTube URL for live analysis.") },
            shape = MaterialTheme.shapes.large
          )

          Spacer(Modifier.height(8.dp))
          Text("Output", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
          Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            FilterChip(selected = clipCount == 3, onClick = { onClipCountChanged(3) }, label = { Text("3 Shorts") })
            FilterChip(selected = clipCount == 4, onClick = { onClipCountChanged(4) }, label = { Text("4 Shorts") })
            FilterChip(selected = clipLength == 15, onClick = { onClipLengthChanged(15) }, label = { Text("15 sec") })
            FilterChip(selected = clipLength == 30, onClick = { onClipLengthChanged(30) }, label = { Text("30 sec") })
          }

          Spacer(Modifier.height(10.dp))
          Button(
            onClick = onAnalyzeClicked,
            enabled = !isAnalyzing && urlInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors()
          ) {
            if (isAnalyzing) {
              CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
              Spacer(Modifier.width(8.dp))
              Text("Analyzing…")
            } else {
              Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Text("Find the best Shorts")
            }
          }

          if (isAnalyzing) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Text(analysisStatusText.ifBlank { "Working…" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }

          Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            AssistChip(onClick = {}, label = { Text("Free / self-hostable") }, leadingIcon = { Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp)) })
            GeminiClipperService.PRESETS.take(2).forEach { preset ->
              AssistChip(onClick = { onLoadPreset(preset.url) }, label = { Text(preset.title.take(20) + "…") })
            }
          }
        }
      }

      currentVideo?.let { video ->
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = MaterialTheme.shapes.large) {
          Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
              model = ImageRequest.Builder(context).data(video.thumbnailUrl).crossfade(true).build(),
              contentDescription = "Video thumbnail",
              modifier = Modifier.size(88.dp, 56.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
              Text(video.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
              Text("${video.channelName} • ${video.formattedDuration}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AssistChip(onClick = {}, label = { Text(if (transcriptAvailable) "Transcript" else "Fallback") })
          }
        }
      }

      if (clips.isNotEmpty()) {
        RetentionHeatmapView(
          retentionPoints = retentionPoints,
          clips = clips,
          selectedClip = selectedClip,
          totalDurationSeconds = currentVideo?.durationSeconds ?: 600,
          onSelectClip = onSelectClip
        )

        Text("Suggested Shorts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          clips.forEach { clip ->
            FilterChip(
              selected = selectedClip?.id == clip.id,
              onClick = { onSelectClip(clip) },
              label = { Text("Short ${clip.clipIndex} • ${clip.durationSeconds}s") }
            )
          }
        }
      }

      selectedClip?.let { clip ->
        HorizontalDivider()
        ShortsStudioPlayer(
          clip = clip,
          videoInfo = currentVideo,
          previewUrl = previewUrl,
          isPlaying = isPlaying,
          playbackPositionSec = playbackPositionSec,
          captionStyle = captionStyle,
          framingMode = framingMode,
          customHookHeadline = customHookHeadline,
          onPlayingChanged = onSetPlaying,
          onPlaybackPositionChanged = onSetPlaybackPosition,
          onCaptionStyleChanged = onCaptionStyleChanged,
          onFramingModeChanged = onFramingModeChanged,
          onHookHeadlineChanged = onHookHeadlineChanged,
          onTrimUpdated = onTrimUpdated
        )

        ClipCard(
          clip = clip,
          isSelected = true,
          onSelect = {},
          onCopyMetadata = { onCopyMetadata(clip) },
          onSaveClip = onSaveClip,
          onUploadShorts = { onUploadShorts(clip) },
          onShare = { onShareClip(clip) }
        )
      }
    }
  }
}
