package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.CaptionStyle
import com.example.model.FramingMode
import com.example.model.ShortClip
import com.example.model.YouTubeVideoInfo
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun ShortsStudioPlayer(
  clip: ShortClip,
  videoInfo: YouTubeVideoInfo?,
  previewUrl: String?,
  isPlaying: Boolean,
  playbackPositionSec: Float,
  captionStyle: CaptionStyle,
  framingMode: FramingMode,
  customHookHeadline: String,
  onPlayingChanged: (Boolean) -> Unit,
  onPlaybackPositionChanged: (Float) -> Unit,
  onCaptionStyleChanged: (CaptionStyle) -> Unit,
  onFramingModeChanged: (FramingMode) -> Unit,
  onHookHeadlineChanged: (String) -> Unit,
  onTrimUpdated: (Int, Int) -> Unit,
  modifier: Modifier = Modifier
) {
  var showTrimControls by remember { mutableStateOf(false) }
  val context = LocalContext.current
  val duration = clip.durationSeconds.coerceAtLeast(1)
  val currentSubtitle = clip.sampleSubtitles.lastOrNull { it.relativeSec <= playbackPositionSec }
    ?: clip.sampleSubtitles.firstOrNull()

  val exoPlayer = remember(previewUrl) {
    previewUrl?.takeIf { it.startsWith("http") }?.let {
      ExoPlayer.Builder(context).build().apply {
        setMediaItem(MediaItem.fromUri(it))
        repeatMode = Player.REPEAT_MODE_ONE
        prepare()
        playWhenReady = false
      }
    }
  }

  DisposableEffect(exoPlayer) {
    val listener = object : Player.Listener {
      override fun onIsPlayingChanged(isPlayingNow: Boolean) {
        onPlayingChanged(isPlayingNow)
      }
    }
    exoPlayer?.addListener(listener)
    onDispose {
      exoPlayer?.removeListener(listener)
      exoPlayer?.release()
    }
  }

  LaunchedEffect(exoPlayer, isPlaying) {
    exoPlayer ?: return@LaunchedEffect
    if (isPlaying) exoPlayer.play() else exoPlayer.pause()
  }

  LaunchedEffect(exoPlayer, playbackPositionSec) {
    exoPlayer ?: return@LaunchedEffect
    val actual = exoPlayer.currentPosition.coerceAtLeast(0L) / 1000f
    if (abs(actual - playbackPositionSec) > 0.8f) {
      exoPlayer.seekTo((playbackPositionSec * 1000f).toLong().coerceAtLeast(0L))
    }
  }

  LaunchedEffect(exoPlayer, duration) {
    exoPlayer ?: return@LaunchedEffect
    while (true) {
      delay(100)
      val current = (exoPlayer.currentPosition / 1000f).coerceIn(0f, duration.toFloat())
      onPlaybackPositionChanged(current)
    }
  }

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
          Text("Real video preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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

      Spacer(Modifier.height(10.dp))
      Box(
        modifier = Modifier
          .fillMaxWidth(0.82f)
          .aspectRatio(9f / 16f)
          .align(Alignment.CenterHorizontally)
          .clip(MaterialTheme.shapes.large)
          .background(Color.Black)
      ) {
        if (exoPlayer != null) {
          AndroidView(
            factory = { viewContext ->
              PlayerView(viewContext).apply {
                useController = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                resizeMode = resizeModeFor(framingMode)
                this.player = exoPlayer
              }
            },
            update = { playerView ->
              playerView.player = exoPlayer
              playerView.resizeMode = resizeModeFor(framingMode)
            },
            modifier = Modifier.fillMaxSize()
          )
        } else {
          videoInfo?.thumbnailUrl?.let { thumbnail ->
            AsyncImage(
              model = ImageRequest.Builder(context).data(thumbnail).crossfade(true).build(),
              contentDescription = "Video preview thumbnail",
              modifier = Modifier.fillMaxSize()
            )
          }
          Surface(
            modifier = Modifier.align(Alignment.Center).padding(18.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface
          ) {
            Text(
              "Live preview needs the ClipMint backend.",
              style = MaterialTheme.typography.labelLarge,
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(14.dp)
            )
          }
        }

        Surface(
          modifier = Modifier.align(Alignment.TopCenter).padding(10.dp),
          shape = MaterialTheme.shapes.medium,
          color = MaterialTheme.colorScheme.inverseSurface,
          contentColor = MaterialTheme.colorScheme.inverseOnSurface
        ) {
          Text(
            customHookHeadline.ifBlank { clip.hookHeadline },
            fontWeight = FontWeight.Bold,
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
              if (captionStyle == CaptionStyle.PUNCH_RED) subtitle.text.uppercase() else subtitle.text,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
          }
        }

        if (!isPlaying) {
          Surface(
            modifier = Modifier.size(58.dp).align(Alignment.Center),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            tonalElevation = 3.dp
          ) {
            IconButton(onClick = { onPlayingChanged(true) }, modifier = Modifier.fillMaxSize()) {
              Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(34.dp))
            }
          }
        }
      }

      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onPlayingChanged(!isPlaying) }) {
          Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
        }
        Slider(
          value = playbackPositionSec.coerceIn(0f, duration.toFloat()),
          onValueChange = onPlaybackPositionChanged,
          valueRange = 0f..duration.toFloat(),
          modifier = Modifier.weight(1f)
        )
        Text(
          String.format("%02.0fs / %02ds", playbackPositionSec, duration),
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

private fun resizeModeFor(mode: FramingMode): Int = when (mode) {
  FramingMode.BLUR_BACKGROUND -> AspectRatioFrameLayout.RESIZE_MODE_FIT
  FramingMode.SPLIT_SCREEN -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
  FramingMode.CENTER_CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
}
