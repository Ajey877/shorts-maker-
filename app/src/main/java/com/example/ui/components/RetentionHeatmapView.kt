package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.model.RetentionPoint
import com.example.model.ShortClip
import com.example.ui.theme.AmberGlow
import com.example.ui.theme.BrightCrimson
import com.example.ui.theme.GeoPurple
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ViralGreen
import com.example.ui.theme.YouTubeRed

@Composable
fun RetentionHeatmapView(
  retentionPoints: List<RetentionPoint>,
  clips: List<ShortClip>,
  selectedClip: ShortClip?,
  totalDurationSeconds: Int,
  onSelectClip: (ShortClip) -> Unit,
  modifier: Modifier = Modifier
) {
  val clipColors = listOf(GeoPurple, BrightCrimson, AmberGlow, ViralGreen)

  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
  ) {
    Column(Modifier.padding(16.dp)) {
      Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Timeline, contentDescription = "Timeline", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
          Spacer(Modifier.width(8.dp))
          Text("Candidate Timeline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
          Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Editor candidates", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }
        }
      }

      Text(
        "These highlighted ranges are timeline candidates, not measured YouTube audience-retention data.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
      )

      Box(
        Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF101015))
          .pointerInput(clips, totalDurationSeconds) {
            detectTapGestures { offset ->
              if (totalDurationSeconds > 0 && clips.isNotEmpty()) {
                val tappedSec = (offset.x / size.width * totalDurationSeconds).toInt()
                clips.minByOrNull { clip -> kotlin.math.abs((clip.startSeconds + clip.endSeconds) / 2 - tappedSec) }?.let(onSelectClip)
              }
            }
          }
      ) {
        Canvas(Modifier.matchParentSize()) {
          if (retentionPoints.isEmpty()) return@Canvas
          val width = size.width
          val height = size.height
          for (i in 1..3) {
            val y = height * (i / 4f)
            drawLine(Color.White.copy(alpha = 0.08f), Offset(0f, y), Offset(width, y), 1.dp.toPx())
          }
          if (totalDurationSeconds > 0) {
            clips.forEachIndexed { index, clip ->
              val startX = clip.startSeconds.toFloat() / totalDurationSeconds * width
              val endX = clip.endSeconds.toFloat() / totalDurationSeconds * width
              val color = clipColors[index % clipColors.size]
              val selected = selectedClip?.id == clip.id
              drawRect(color.copy(alpha = if (selected) 0.35f else 0.15f), Offset(startX, 0f), Size((endX - startX).coerceAtLeast(12.dp.toPx()), height))
              drawRect(color, Offset(startX, 0f), Size((endX - startX).coerceAtLeast(12.dp.toPx()), if (selected) 4.dp.toPx() else 2.dp.toPx()))
            }
          }

          val path = Path()
          val fill = Path()
          retentionPoints.forEachIndexed { index, point ->
            val x = point.timeFraction.coerceIn(0f, 1f) * width
            val y = height * (1f - point.retentionPercent.coerceIn(0, 100) / 100f)
            if (index == 0) {
              path.moveTo(x, y); fill.moveTo(x, height); fill.lineTo(x, y)
            } else {
              val previous = retentionPoints[index - 1]
              val px = previous.timeFraction.coerceIn(0f, 1f) * width
              val py = height * (1f - previous.retentionPercent.coerceIn(0, 100) / 100f)
              val cx = (px + x) / 2f
              path.cubicTo(cx, py, cx, y, x, y)
              fill.cubicTo(cx, py, cx, y, x, y)
            }
          }
          fill.lineTo(width, height); fill.close()
          drawPath(fill, Brush.verticalGradient(listOf(BrightCrimson.copy(alpha = 0.25f), Color.Transparent), 0f, height))
          drawPath(path, Brush.horizontalGradient(listOf(BrightCrimson, AmberGlow, NeonCyan, YouTubeRed)), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        }
      }

      Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        clips.forEachIndexed { index, clip ->
          val color = clipColors[index % clipColors.size]
          val selected = selectedClip?.id == clip.id
          Surface(
            onClick = { onSelectClip(clip) },
            shape = RoundedCornerShape(8.dp),
            color = if (selected) color.copy(alpha = 0.2f) else Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
          ) {
            Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
              Box(Modifier.size(8.dp).clip(CircleShape).background(color))
              Spacer(Modifier.width(4.dp))
              Text("Short #${clip.clipIndex} (${clip.durationSeconds}s)", fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
            }
          }
        }
      }
    }
  }
}
