package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.example.ui.theme.AmberGlow
import com.example.ui.theme.BrightCrimson
import com.example.ui.theme.GeoLavender
import com.example.ui.theme.GeoPurple
import com.example.ui.theme.GeoPurpleLight
import com.example.ui.theme.ViralGreen
import com.example.ui.theme.YouTubeRed

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

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(scrollState)
      .padding(horizontal = 16.dp, vertical = 8.dp)
  ) {
    // Top App Header
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
              Brush.linearGradient(listOf(GeoPurple, BrightCrimson))
            ),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.SmartDisplay,
            contentDescription = "ShortsCut Logo",
            tint = Color.White,
            modifier = Modifier.size(24.dp)
          )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "ShortsCut",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = MaterialTheme.colorScheme.primaryContainer
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Default.AutoAwesome,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                  text = "Gemini AI",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary
                )
              }
            }
          }
          Text(
            text = "Full Video to 3-4 Ready Shorts",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      // Library Badge Button
      Surface(
        onClick = onOpenLibrary,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            imageVector = Icons.Default.Bookmarks,
            contentDescription = "Library",
            tint = AmberGlow,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "Saved ($savedClipsCount)",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }
    }

    // Input URL Card
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(18.dp),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface
      ),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Text(
          text = "Paste YouTube Video Link",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
          value = urlInput,
          onValueChange = onUrlInputChanged,
          placeholder = { Text("https://youtube.com/watch?v=... or search title") },
          leadingIcon = {
            Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = YouTubeRed)
          },
          trailingIcon = {
            Row {
              if (urlInput.isNotEmpty()) {
                IconButton(onClick = { onUrlInputChanged("") }) {
                  Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                }
              }
              IconButton(
                onClick = {
                  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                  val clip = clipboard.primaryClip
                  if (clip != null && clip.itemCount > 0) {
                    val pasted = clip.getItemAt(0).text.toString()
                    onUrlInputChanged(pasted)
                  }
                }
              ) {
                Icon(imageVector = Icons.Default.ContentPaste, contentDescription = "Paste", tint = YouTubeRed)
              }
            }
          },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Quick Preset Chips
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Text(
            text = "Try Samples:",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterVertically)
          )
          GeminiClipperService.PRESETS.forEach { preset ->
            Surface(
              onClick = { onLoadPreset(preset.url) },
              shape = RoundedCornerShape(8.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
              Text(
                text = "${preset.channelName}: ${preset.title.take(18)}...",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // AI Scan Action Button
        Button(
          onClick = onAnalyzeClicked,
          enabled = !isAnalyzing && urlInput.isNotBlank(),
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
          ),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          if (isAnalyzing) {
            CircularProgressIndicator(
              color = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.size(18.dp),
              strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Analyzing Video Retention...", fontWeight = FontWeight.Bold)
          } else {
            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Extract 3-4 Most Watched Shorts", fontSize = 14.sp, fontWeight = FontWeight.Bold)
          }
        }

        // Analysis progress status text
        AnimatedVisibility(visible = isAnalyzing) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = BrightCrimson.copy(alpha = 0.12f),
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 10.dp)
          ) {
            Row(
              modifier = Modifier.padding(10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = BrightCrimson,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = analysisStatusText,
                fontSize = 12.sp,
                color = BrightCrimson,
                fontWeight = FontWeight.Medium
              )
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Video Overview Card (If loaded)
    if (currentVideo != null) {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
      ) {
        Row(
          modifier = Modifier.padding(12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .size(width = 90.dp, height = 55.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(Color.Black)
          ) {
            AsyncImage(
              model = ImageRequest.Builder(context)
                .data(currentVideo.thumbnailUrl)
                .crossfade(true)
                .build(),
              contentDescription = "Video Thumbnail",
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize()
            )
            Surface(
              shape = RoundedCornerShape(4.dp),
              color = Color.Black.copy(alpha = 0.8f),
              modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(3.dp)
            ) {
              Text(
                text = currentVideo.formattedDuration,
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
              )
            }
          }

          Spacer(modifier = Modifier.width(12.dp))

          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = currentVideo.title,
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "${currentVideo.channelName} • ${currentVideo.viewCountFormatted}",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))
    }

    // Audience Retention Heatmap Component
    if (retentionPoints.isNotEmpty() && clips.isNotEmpty()) {
      RetentionHeatmapView(
        retentionPoints = retentionPoints,
        clips = clips,
        selectedClip = selectedClip,
        totalDurationSeconds = currentVideo?.durationSeconds ?: 600,
        onSelectClip = onSelectClip
      )

      Spacer(modifier = Modifier.height(16.dp))
    }

    // Clip Selection Tabs (Clip 1, Clip 2, Clip 3, Clip 4)
    if (clips.isNotEmpty()) {
      Text(
        text = "🎯 Ready-to-Post Shorts (Top 4 Viral Segments)",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
      )

      Spacer(modifier = Modifier.height(8.dp))

      ScrollableTabRow(
        selectedTabIndex = (selectedClip?.clipIndex?.minus(1))?.coerceAtLeast(0) ?: 0,
        edgePadding = 0.dp,
        containerColor = Color.Transparent,
        divider = {},
        indicator = { tabPositions ->
          val idx = (selectedClip?.clipIndex?.minus(1))?.coerceIn(0, tabPositions.size - 1) ?: 0
          TabRowDefaults.SecondaryIndicator(
            modifier = Modifier.tabIndicatorOffset(tabPositions[idx]),
            color = MaterialTheme.colorScheme.primary
          )
        }
      ) {
        clips.forEach { clip ->
          val isSelected = selectedClip?.id == clip.id
          Tab(
            selected = isSelected,
            onClick = { onSelectClip(clip) },
            text = {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = "Short #${clip.clipIndex}",
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(4.dp))
                Surface(
                  shape = RoundedCornerShape(6.dp),
                  color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ) {
                  Text(
                    text = "${clip.durationSeconds}s",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                  )
                }
              }
            }
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))
    }

    // 9:16 Shorts Studio Player & Live Preview
    if (selectedClip != null) {
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

      Spacer(modifier = Modifier.height(14.dp))

      // Clip Details & Ready-to-Post Metadata Card
      ClipCard(
        clip = selectedClip,
        isSelected = true,
        onSelect = {},
        onCopyMetadata = { onCopyMetadata(selectedClip) },
        onSaveClip = onSaveClip,
        onUploadShorts = { onUploadShorts(selectedClip) },
        onShare = { onShareClip(selectedClip) }
      )

      Spacer(modifier = Modifier.height(20.dp))
    }
  }
}
