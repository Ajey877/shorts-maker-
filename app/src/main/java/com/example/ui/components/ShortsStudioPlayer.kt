package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.CaptionStyle
import com.example.model.FramingMode
import com.example.model.ShortClip
import com.example.model.YouTubeVideoInfo

@Composable
fun ShortsStudioPlayer(
  clip: ShortClip,
  videoInfo: YouTubeVideoInfo?,
  isPlaying: Boolean,
  playbackPositionSec: Float,
  captionStyle: CaptionStyle,
  framingMode: FramingMode,
  customHookHeadline: String,
  onTogglePlayPause: () -> Unit,
  onSeek: (Float) -> Unit,
  onCaptionStyleChanged: (CaptionStyle) -> Unit,
  onFramingModeChanged: (FramingMode) -> Unit,
  onHookHeadlineChanged: (String) -> Unit,
  onTrimUpdated: (Int, Int) -> Unit,
  modifier: Modifier = Modifier
) {
  var showTrimControls by remember { mutableStateOf(false) }
  val context = LocalContext.current
  val totalDuration = clip.durationSeconds.toFloat().coerceAtLeast(1f)
  val progress = (playbackPositionSec / totalDuration).coerceIn(0f, 1f)
  val currentSubtitle = clip.sampleSubtitles.lastOrNull { it.relativeSec <= playbackPositionSec }
    ?: clip.sampleSubtitles.firstOrNull()
  val primary = MaterialTheme.colorScheme.primary

  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.extraLarge,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    tonalElevation = 1.dp,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
  ) {
    Column(Modifier.padding(16.dp)) {
      Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(Modifier.weight(1f)) {
          Text("Short preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          Text(
            "${clip.startTimestampFormatted} – ${clip.endTimestampFormatted} • ${clip.durationSeconds}s",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        IconButton(onClick = { showTrimControls = !showTrimControls }) {
          Icon(Icons.Default.Tune, contentDescription = "Edit trim")
        }
      }

      Spacer(Modifier.height(12.dp))

      Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(Modifier.padding(12.dp)) {
          Text(
            "Preview uses the source thumbnail. Final export renders the actual video.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
          )

          Box(
            modifier = Modifier
              .fillMaxWidth(0.82f)
              .aspectRatio(9f / 16f)
              .align(Alignment.CenterHorizontally)
              .clip(MaterialTheme.shapes.large)
              .background(MaterialTheme.colorScheme.scrim)
              .clickable { onTogglePlayPause() }
          ) {
            val thumbUrl = videoInfo?.thumbnailUrl ?: "https://img.youtube.com/vi/${clip.videoId}/hqdefault.jpg"
            when (framingMode) {
              FramingMode.CENTER_CROP -> {
                AsyncImage(
                  model = ImageRequest.Builder(context).data(thumbUrl).crossfade(true).build(),
                  contentDescription = "Short preview",
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize()
                )
              }
              FramingMode.BLUR_BACKGROUND -> {
                AsyncImage(
                  model = ImageRequest.Builder(context).data(thumbUrl).crossfade(true).build(),
                  contentDescription = null,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize().blur(18.dp)
                )
                AsyncImage(
                  model = ImageRequest.Builder(context).data(thumbUrl).crossfade(true).build(),
                  contentDescription = "Centered preview",
                  contentScale = ContentScale.Fit,
                  modifier = Modifier.fillMaxWidth().align(Alignment.Center)
                )
              }
              FramingMode.SPLIT_SCREEN -> {
                Column(Modifier.fillMaxSize()) {
                  AsyncImage(
                    model = ImageRequest.Builder(context).data(thumbUrl).crossfade(true).build(),
                    contentDescription = "Top preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                  )
                  Surface(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                  ) {
                    Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                      Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(32.dp))
                      Text("Audio track", style = MaterialTheme.typography.labelMedium)
                    }
                  }
                }
              }
            }

            Surface(
              modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
              shape = MaterialTheme.shapes.medium,
              color = MaterialTheme.colorScheme.inverseSurface,
              contentColor = MaterialTheme.colorScheme.inverseOnSurface,
              tonalElevation = 2.dp
            ) {
              Text(
                text = customHookHeadline.ifBlank { clip.hookHeadline },
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
              )
            }

            currentSubtitle?.let { subtitle ->
              Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 34.dp, start = 12.dp, end = 12.dp),
                shape = MaterialTheme.shapes.medium,
                color = when (captionStyle) {
                  CaptionStyle.HORMOZI_BOLD -> MaterialTheme.colorScheme.inverseSurface
                  CaptionStyle.NEON_GLOW -> MaterialTheme.colorScheme.secondaryContainer
                  CaptionStyle.PUNCH_RED -> MaterialTheme.colorScheme.primaryContainer
                  CaptionStyle.CLEAN_MINIMAL -> MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f)
                },
                contentColor = when (captionStyle) {
                  CaptionStyle.HORMOZI_BOLD -> MaterialTheme.colorScheme.inverseOnSurface
                  CaptionStyle.NEON_GLOW -> MaterialTheme.colorScheme.onSecondaryContainer
                  CaptionStyle.PUNCH_RED -> MaterialTheme.colorScheme.onPrimaryContainer
                  CaptionStyle.CLEAN_MINIMAL -> MaterialTheme.colorScheme.inverseOnSurface
                }
              ) {
                Text(
                  text = if (captionStyle == CaptionStyle.PUNCH_RED) subtitle.text.uppercase() else subtitle.text,
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  textAlign = TextAlign.Center,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
              }
            }

            Box(
              Modifier
                .fillMaxWidth()
                .height(4.dp)
                .align(Alignment.BottomCenter)
                .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
              Box(
                Modifier
                  .fillMaxWidth(progress)
                  .height(4.dp)
                  .background(primary)
              )
            }

            if (!isPlaying) {
              Surface(
                modifier = Modifier.size(56.dp).align(Alignment.Center),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                tonalElevation = 3.dp
              ) {
                IconButton(onClick = onTogglePlayPause, modifier = Modifier.fillMaxSize()) {
                  Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(34.dp))
                }
              }
            }
          }
        }
      }

      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onTogglePlayPause) {
          Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play"
          )
        }
        Slider(
          value = playbackPositionSec,
          onValueChange = onSeek,
          valueRange = 0f..totalDuration,
          colors = SliderDefaults.colors(),
          modifier = Modifier.weight(1f)
        )
        Text(
          String.format("%02.0fs / %02ds", playbackPositionSec, clip.durationSeconds),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(start = 6.dp)
        )
      }

      AnimatedVisibility(showTrimControls) {
        Surface(
          modifier = Modifier.fillMaxWidth(),
          shape = MaterialTheme.shapes.large,
          color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
          Column(Modifier.padding(12.dp)) {
            Text("Fine-tune trim", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
              Text("Start ${clip.startTimestampFormatted}", style = MaterialTheme.typography.bodySmall)
              Row {
                FilterChip(selected = false, onClick = { onTrimUpdated((clip.startSeconds - 1).coerceAtLeast(0), clip.endSeconds) }, label = { Text("−1s") })
                Spacer(Modifier.width(6.dp))
                FilterChip(selected = false, onClick = { onTrimUpdated(clip.startSeconds + 1, clip.endSeconds) }, label = { Text("+1s") })
              }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
              Text("End ${clip.endTimestampFormatted}", style = MaterialTheme.typography.bodySmall)
              Row {
                FilterChip(selected = false, onClick = { onTrimUpdated(clip.startSeconds, (clip.endSeconds - 1).coerceAtLeast(clip.startSeconds + 10)) }, label = { Text("−1s") })
                Spacer(Modifier.width(6.dp))
                FilterChip(selected = false, onClick = { onTrimUpdated(clip.startSeconds, (clip.endSeconds + 1).coerceAtMost(clip.startSeconds + 30)) }, label = { Text("+1s") })
              }
            }
          }
        }
      }

      Spacer(Modifier.height(12.dp))
      Text("Caption style", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CaptionStyle.entries.forEach { style ->
          FilterChip(
            selected = captionStyle == style,
            onClick = { onCaptionStyleChanged(style) },
            label = { Text(style.displayName, fontSize = 11.sp) },
            modifier = Modifier.weight(1f)
          )
        }
      }

      Spacer(Modifier.height(8.dp))
      Text("Framing", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FramingMode.entries.forEach { mode ->
          FilterChip(
            selected = framingMode == mode,
            onClick = { onFramingModeChanged(mode) },
            label = { Text(mode.displayName, fontSize = 11.sp) },
            modifier = Modifier.weight(1f)
          )
        }
      }

      Spacer(Modifier.height(8.dp))
      OutlinedTextField(
        value = customHookHeadline,
        onValueChange = onHookHeadlineChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Hook headline") },
        placeholder = { Text("Add a short hook") },
        singleLine = true
      )
    }
  }
}
