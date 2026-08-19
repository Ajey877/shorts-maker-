package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun HomeScreen(
  urlInput: String,
  isAnalyzing: Boolean,
  analysisStatusText: String,
  currentVideo: YouTubeVideoInfo?,
  clips: List<ShortClip>,
  selectedClip: ShortClip?,
  retentionPoints: List<RetentionPoint>,
  isPlaying: Boolean,
  playbackPositionSec: Float,
  captionStyle: CaptionStyle,
  framingMode: FramingMode,
  customHookHeadline: String,
  savedClipsCount: Int,
  onUrlInputChanged: (String) -> Unit,
  onAnalyzeClicked: () -> Unit,
  onLoadPreset: (String) -> Unit,
  onSelectClip: (ShortClip) -> Unit,
  onTogglePlayPause: () -> Unit,
  onSeek: (Float) -> Unit,
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
  val scrollState = rememberScrollState()

  Scaffold(
    modifier = modifier.fillMaxSize().imePadding(),
    topBar = {
      TopAppBar(
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
              modifier = Modifier.size(36.dp),
              shape = RoundedCornerShape(10.dp),
              color = MaterialTheme.colorScheme.primaryContainer
            ) {
              Icon(
                imageVector = Icons.Default.SmartDisplay,
                contentDescription = "ClipMint",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(8.dp)
              )
            }
            Spacer(Modifier.width(10.dp))
            Column {
              Text("ClipMint", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
              Text("AI Shorts Studio", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        },
        actions = {
          AssistChip(
            onClick = onOpenLibrary,
            label = { Text("Saved $savedClipsCount") },
            leadingIcon = { Icon(Icons.Default.Bookmarks, contentDescription = null, modifier = Modifier.size(16.dp)) },
            colors = AssistChipDefaults.assistChipColors(
              containerColor = MaterialTheme.colorScheme.secondaryContainer,
              labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
              leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            border = null
          )
          Spacer(Modifier.width(8.dp))
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background,
          titleContentColor = MaterialTheme.colorScheme.onBackground
        )
      )
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .verticalScroll(scrollState)
        .navigationBarsPadding()
        .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
      ) {
        Column(Modifier.padding(16.dp)) {
          Text("Create Shorts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          Spacer(Modifier.height(4.dp))
          Text(
            "Paste a YouTube video link. ClipMint will find candidate moments and prepare Shorts.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(Modifier.height(12.dp))

          OutlinedTextField(
            value = urlInput,
            onValueChange = onUrlInputChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("YouTube URL") },
            placeholder = { Text("https://www.youtube.com/watch?v=...") },
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
            supportingText = { Text("Use a full YouTube URL for live backend analysis.") }
          )

          Spacer(Modifier.height(10.dp))

          Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            GeminiClipperService.PRESETS.forEach { preset ->
              AssistChip(
                onClick = { onLoadPreset(preset.url) },
                label = { Text(preset.title.take(20), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = { Icon(Icons.Default.SmartDisplay, contentDescription = null, modifier = Modifier.size(16.dp)) }
              )
            }
          }

          Spacer(Modifier.height(12.dp))

          Button(
            onClick = onAnalyzeClicked,
            modifier = Modifier.fillMaxWidth(),
            enabled = urlInput.isNotBlank() && !isAnalyzing,
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
              Text("Find best Shorts")
            }
          }

          if (isAnalyzing) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Text(
              analysisStatusText.ifBlank { "Working…" },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      if (currentVideo != null) {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
          shape = MaterialTheme.shapes.large
        ) {
          Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
              model = ImageRequest.Builder(context).data(currentVideo.thumbnailUrl).crossfade(true).build(),
              contentDescription = "Video thumbnail",
              contentScale = ContentScale.Crop,
              modifier = Modifier.size(92.dp, 58.dp).clip(MaterialTheme.shapes.medium)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
              Text(currentVideo.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
              Spacer(Modifier.height(3.dp))
              Text(currentVideo.channelName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text(currentVideo.formattedDuration, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
      }

      if (retentionPoints.isNotEmpty() && clips.isNotEmpty()) {
        RetentionHeatmapView(
          retentionPoints = retentionPoints,
          clips = clips,
          selectedClip = selectedClip,
          totalDurationSeconds = currentVideo?.durationSeconds ?: 600,
          onSelectClip = onSelectClip
        )
      }

      if (clips.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Suggested Shorts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          Text(
            "Select a segment to preview and customize it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            clips.forEach { clip ->
              val selected = selectedClip?.id == clip.id
              AssistChip(
                onClick = { onSelectClip(clip) },
                label = { Text("Short ${clip.clipIndex} • ${clip.durationSeconds}s") },
                leadingIcon = {
                  Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                  )
                },
                colors = AssistChipDefaults.assistChipColors(
                  containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                  labelColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
              )
            }
          }
        }
      }

      if (selectedClip != null) {
        HorizontalDivider()
        ShortsStudioPlayer(
          clip = selectedClip,
          videoInfo = currentVideo,
          isPlaying = isPlaying,
          playbackPositionSec = playbackPositionSec,
          captionStyle = captionStyle,
          framingMode = framingMode,
          customHookHeadline = customHookHeadline,
          onTogglePlayPause = onTogglePlayPause,
          onSeek = onSeek,
          onCaptionStyleChanged = onCaptionStyleChanged,
          onFramingModeChanged = onFramingModeChanged,
          onHookHeadlineChanged = onHookHeadlineChanged,
          onTrimUpdated = onTrimUpdated
        )

        ClipCard(
          clip = selectedClip,
          isSelected = true,
          onSelect = {},
          onCopyMetadata = { onCopyMetadata(selectedClip) },
          onSaveClip = onSaveClip,
          onUploadShorts = { onUploadShorts(selectedClip) },
          onShare = { onShareClip(selectedClip) }
        )

        Spacer(Modifier.height(24.dp))
      }
    }
  }
}
