package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
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
import com.example.ui.theme.AmberGlow
import com.example.ui.theme.BrightCrimson
import com.example.ui.theme.ElectricYellow
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.YouTubeRed

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
  var isEditingHook by remember { mutableStateOf(false) }

  val context = LocalContext.current
  val totalDuration = clip.durationSeconds.toFloat()
  val progress = if (totalDuration > 0f) (playbackPositionSec / totalDuration).coerceIn(0f, 1f) else 0f

  // Calculate current active subtitle phrase based on playback position
  val currentSubtitle = clip.sampleSubtitles.lastOrNull { it.relativeSec <= playbackPositionSec }
    ?: clip.sampleSubtitles.firstOrNull()

  // Pulsing animation for active audio wave
  val infiniteTransition = rememberInfiniteTransition(label = "wave")
  val wavePulse by infiniteTransition.animateFloat(
    initialValue = 0.6f,
    targetValue = 1.0f,
    animationSpec = infiniteRepeatable(
      animation = tween(400, easing = LinearEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "wavePulse"
  )

  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surface,
    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      // Top Studio Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = YouTubeRed
            ) {
              Text(
                text = "9:16 SHORTS",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Live Shorts Studio",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
          }
          Text(
            text = "${clip.startTimestampFormatted} - ${clip.endTimestampFormatted} (${clip.durationSeconds}s clip)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        IconButton(
          onClick = { showTrimControls = !showTrimControls }
        ) {
          Icon(
            imageVector = Icons.Default.Tune,
            contentDescription = "Fine-tune Trim",
            tint = if (showTrimControls) YouTubeRed else MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // 9:16 Vertical Video Preview Container
      Box(
        modifier = Modifier
          .fillMaxWidth(0.85f)
          .aspectRatio(9f / 16f)
          .align(Alignment.CenterHorizontally)
          .clip(RoundedCornerShape(18.dp))
          .background(Color.Black)
          .border(2.dp, YouTubeRed.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
          .clickable { onTogglePlayPause() }
      ) {
        val thumbUrl = videoInfo?.thumbnailUrl ?: "https://img.youtube.com/vi/${clip.videoId}/hqdefault.jpg"

        // Framing mode rendering
        when (framingMode) {
          FramingMode.CENTER_CROP -> {
            AsyncImage(
              model = ImageRequest.Builder(context)
                .data(thumbUrl)
                .crossfade(true)
                .build(),
              contentDescription = "Shorts Video Frame",
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize()
            )
          }
          FramingMode.BLUR_BACKGROUND -> {
            // Blurred background
            AsyncImage(
              model = ImageRequest.Builder(context)
                .data(thumbUrl)
                .crossfade(true)
                .build(),
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier
                .fillMaxSize()
                .blur(20.dp)
            )
            // Center fit landscape
            AsyncImage(
              model = ImageRequest.Builder(context)
                .data(thumbUrl)
                .crossfade(true)
                .build(),
              contentDescription = "Center Landscape",
              contentScale = ContentScale.Fit,
              modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
            )
          }
          FramingMode.SPLIT_SCREEN -> {
            Column(modifier = Modifier.fillMaxSize()) {
              AsyncImage(
                model = ImageRequest.Builder(context)
                  .data(thumbUrl)
                  .crossfade(true)
                  .build(),
                contentDescription = "Top Video Half",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                  .weight(1f)
                  .fillMaxWidth()
              )
              Box(
                modifier = Modifier
                  .weight(1f)
                  .fillMaxWidth()
                  .background(
                    Brush.verticalGradient(
                      colors = listOf(Color(0xFF1E1015), Color(0xFF0D0D12))
                    )
                  ),
                contentAlignment = Alignment.Center
              ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(36.dp)
                  )
                  Text(
                    text = "AI Voice Track Active",
                    color = NeonCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                  )
                }
              }
            }
          }
        }

        // Top Gradient Shadow & Top Hook Headline Banner
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .background(
              Brush.verticalGradient(
                colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
              )
            )
            .align(Alignment.TopCenter)
            .padding(12.dp)
        ) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = YouTubeRed.copy(alpha = 0.92f),
            modifier = Modifier
              .align(Alignment.TopCenter)
              .shadow(8.dp)
          ) {
            Text(
              text = if (customHookHeadline.isNotBlank()) customHookHeadline else clip.hookHeadline,
              color = Color.White,
              fontSize = 13.sp,
              fontWeight = FontWeight.Black,
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
          }
        }

        // Dynamic Subtitles Overlay in Center-Bottom of 9:16 Screen
        if (currentSubtitle != null) {
          Box(
            modifier = Modifier
              .align(Alignment.BottomCenter)
              .padding(bottom = 60.dp, start = 16.dp, end = 16.dp)
          ) {
            when (captionStyle) {
              CaptionStyle.HORMOZI_BOLD -> {
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = Color.Black.copy(alpha = 0.85f),
                  border = androidx.compose.foundation.BorderStroke(1.5.dp, ElectricYellow)
                ) {
                  Text(
                    text = currentSubtitle.text,
                    color = ElectricYellow,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                  )
                }
              }
              CaptionStyle.NEON_GLOW -> {
                Surface(
                  shape = RoundedCornerShape(10.dp),
                  color = Color(0xFF001524).copy(alpha = 0.9f),
                  border = androidx.compose.foundation.BorderStroke(2.dp, NeonCyan)
                ) {
                  Text(
                    text = currentSubtitle.text,
                    color = NeonCyan,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                  )
                }
              }
              CaptionStyle.PUNCH_RED -> {
                Surface(
                  shape = RoundedCornerShape(6.dp),
                  color = BrightCrimson,
                  shadowElevation = 6.dp
                ) {
                  Text(
                    text = currentSubtitle.text.uppercase(),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                  )
                }
              }
              CaptionStyle.CLEAN_MINIMAL -> {
                Text(
                  text = currentSubtitle.text,
                  color = Color.White,
                  fontSize = 17.sp,
                  fontWeight = FontWeight.Bold,
                  textAlign = TextAlign.Center,
                  modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                )
              }
            }
          }
        }

        // Bottom Progress Bar inside video
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(Color.White.copy(alpha = 0.3f))
            .align(Alignment.BottomCenter)
        ) {
          Box(
            modifier = Modifier
              .fillMaxWidth(progress)
              .height(4.dp)
              .background(YouTubeRed)
          )
        }

        // Center Play / Pause Indicator (shows temporarily when paused)
        if (!isPlaying) {
          Box(
            modifier = Modifier
              .size(54.dp)
              .clip(CircleShape)
              .background(Color.Black.copy(alpha = 0.65f))
              .align(Alignment.Center),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.PlayArrow,
              contentDescription = "Play",
              tint = Color.White,
              modifier = Modifier.size(36.dp)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Scrubber and Play Controls Bar
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = onTogglePlayPause,
          modifier = Modifier.size(36.dp)
        ) {
          Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = YouTubeRed
          )
        }

        Slider(
          value = playbackPositionSec,
          onValueChange = { onSeek(it) },
          valueRange = 0f..totalDuration.coerceAtLeast(1f),
          colors = SliderDefaults.colors(
            thumbColor = YouTubeRed,
            activeTrackColor = YouTubeRed,
            inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
          ),
          modifier = Modifier.weight(1f)
        )

        Text(
          text = String.format("%02.0fs / %02ds", playbackPositionSec, clip.durationSeconds),
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(start = 6.dp)
        )
      }

      // Fine-Tune Trim Controls (Expandable)
      AnimatedVisibility(visible = showTrimControls) {
        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Text(
              text = "✂️ Fine-Tune Clip Timestamps (10s - 30s)",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "Start: ${clip.startTimestampFormatted} (${clip.startSeconds}s)",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Row {
                Surface(
                  onClick = { onTrimUpdated((clip.startSeconds - 1).coerceAtLeast(0), clip.endSeconds) },
                  shape = RoundedCornerShape(6.dp),
                  color = MaterialTheme.colorScheme.surface,
                  modifier = Modifier.padding(end = 4.dp)
                ) {
                  Text("-1s", fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Surface(
                  onClick = { onTrimUpdated(clip.startSeconds + 1, clip.endSeconds) },
                  shape = RoundedCornerShape(6.dp),
                  color = MaterialTheme.colorScheme.surface
                ) {
                  Text("+1s", fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
              }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "End: ${clip.endTimestampFormatted} (${clip.endSeconds}s)",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Row {
                Surface(
                  onClick = { onTrimUpdated(clip.startSeconds, (clip.endSeconds - 1).coerceAtLeast(clip.startSeconds + 10)) },
                  shape = RoundedCornerShape(6.dp),
                  color = MaterialTheme.colorScheme.surface,
                  modifier = Modifier.padding(end = 4.dp)
                ) {
                  Text("-1s", fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Surface(
                  onClick = { onTrimUpdated(clip.startSeconds, (clip.endSeconds + 1).coerceAtMost(clip.startSeconds + 30)) },
                  shape = RoundedCornerShape(6.dp),
                  color = MaterialTheme.colorScheme.surface
                ) {
                  Text("+1s", fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Customization Controls: Caption Styles
      Text(
        text = "Caption Overlay Style",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
      )

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        CaptionStyle.entries.forEach { style ->
          FilterChip(
            selected = captionStyle == style,
            onClick = { onCaptionStyleChanged(style) },
            label = { Text(style.displayName, fontSize = 11.sp) },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
              selectedLabelColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.weight(1f)
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Framing Modes Selector
      Text(
        text = "Video Framing Mode",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
      )

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        FramingMode.entries.forEach { mode ->
          FilterChip(
            selected = framingMode == mode,
            onClick = { onFramingModeChanged(mode) },
            label = { Text(mode.displayName, fontSize = 11.sp) },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
              selectedLabelColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.weight(1f)
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Editable Hook Headline input
      OutlinedTextField(
        value = customHookHeadline,
        onValueChange = onHookHeadlineChanged,
        label = { Text("Top Hook Banner Text") },
        placeholder = { Text("e.g. PART 1 • THE CRAZIEST START 🤯") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
      )
    }
  }
}
