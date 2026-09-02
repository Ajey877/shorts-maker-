package com.example.ui.components

import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.model.CaptionStyle
import com.example.model.FramingMode
import com.example.model.ShortClip
import com.example.model.YouTubeVideoInfo
import kotlinx.coroutines.delay

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
  val context = LocalContext.current
  val sourceUri = videoInfo?.url?.takeIf { it.startsWith("content://") || it.startsWith("file://") }
  var playerPosition by remember { mutableStateOf(playbackPositionSec) }

  val player = remember(sourceUri, clip.startSeconds, clip.endSeconds) {
    if (sourceUri == null) null else ExoPlayer.Builder(context).build().apply {
      val end = maxOf(clip.endSeconds, clip.startSeconds + 1)
      setMediaItem(
        MediaItem.Builder()
          .setUri(Uri.parse(sourceUri))
          .setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
              .setStartPositionMs(clip.startSeconds.coerceAtLeast(0) * 1000L)
              .setEndPositionMs(end * 1000L)
              .build()
          )
          .build()
      )
      prepare()
      repeatMode = Player.REPEAT_MODE_ONE
      playWhenReady = isPlaying
    }
  }

  DisposableEffect(player) {
    onDispose { player?.release() }
  }

  LaunchedEffect(isPlaying, player) {
    player?.let { if (isPlaying) it.play() else it.pause() }
  }

  LaunchedEffect(playbackPositionSec, player) {
    if (player != null && kotlin.math.abs(player.currentPosition - playbackPositionSec * 1000L) > 800L) {
      player.seekTo((playbackPositionSec * 1000L).toLong().coerceAtLeast(0L))
    }
  }

  LaunchedEffect(player) {
    while (player != null) {
      playerPosition = (player.currentPosition / 1000f).coerceAtLeast(0f)
      onSeek(playerPosition.coerceAtMost(clip.durationSeconds.toFloat()))
      delay(250)
    }
  }

  Column(modifier = modifier.fillMaxWidth()) {
    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(20.dp),
      color = MaterialTheme.colorScheme.surface
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("9:16 SHORTS • REAL VIDEO PREVIEW", fontWeight = FontWeight.Black)
            Text(
              "${clip.startTimestampFormatted} - ${clip.endTimestampFormatted} (${clip.durationSeconds}s)",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          IconButton(onClick = { onFramingModeChanged(framingMode) }) {
            Icon(Icons.Default.Tune, contentDescription = "Framing")
          }
        }

        Spacer(Modifier.height(12.dp))
        Box(
          modifier = Modifier
            .fillMaxWidth(0.85f)
            .aspectRatio(9f / 16f)
            .align(Alignment.CenterHorizontally)
            .clip(RoundedCornerShape(18.dp))
        ) {
          if (player != null) {
            AndroidView(
              factory = { viewContext ->
                PlayerView(viewContext).apply {
                  useController = true
                  this.player = player
                }
              },
              update = { it.player = player },
              modifier = Modifier.fillMaxSize()
            )
          } else {
            Surface(Modifier.fillMaxSize(), color = Color.Black) {
              Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                  "Import a local video to enable real playback.\nA YouTube page URL is not a playable media stream.",
                  color = Color.White,
                  textAlign = TextAlign.Center,
                  modifier = Modifier.padding(24.dp)
                )
              }
            }
          }

          val currentSubtitle = clip.sampleSubtitles.lastOrNull { it.relativeSec <= playerPosition }
            ?: clip.sampleSubtitles.firstOrNull()
          if (currentSubtitle != null) {
            Surface(
              modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
              color = Color.Black.copy(alpha = 0.75f),
              shape = RoundedCornerShape(8.dp)
            ) {
              Text(
                currentSubtitle.text,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
              )
            }
          }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = onTogglePlayPause) {
            Icon(
              if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
              contentDescription = if (isPlaying) "Pause" else "Play"
            )
          }
          Slider(
            value = playbackPositionSec.coerceIn(0f, clip.durationSeconds.toFloat().coerceAtLeast(1f)),
            onValueChange = onSeek,
            valueRange = 0f..clip.durationSeconds.toFloat().coerceAtLeast(1f),
            modifier = Modifier.weight(1f)
          )
          Spacer(Modifier.width(6.dp))
          Text("${playbackPositionSec.toInt()}s / ${clip.durationSeconds}s", style = MaterialTheme.typography.labelSmall)
        }
      }
    }
  }
}
