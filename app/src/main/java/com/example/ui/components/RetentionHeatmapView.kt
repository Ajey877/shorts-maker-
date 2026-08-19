package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.model.RetentionPoint
import com.example.model.ShortClip

@Composable
fun RetentionHeatmapView(
  retentionPoints: List<RetentionPoint>,
  clips: List<ShortClip>,
  selectedClip: ShortClip?,
  totalDurationSeconds: Int,
  onSelectClip: (ShortClip) -> Unit,
  modifier: Modifier = Modifier
) {
  val primary = MaterialTheme.colorScheme.primary
  val secondary = MaterialTheme.colorScheme.secondary
  val tertiary = MaterialTheme.colorScheme.tertiary
  val colors = listOf(primary, secondary, tertiary, MaterialTheme.colorScheme.primaryContainer)

  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.extraLarge,
    color = MaterialTheme.colorScheme.surfaceContainerLow,
    tonalElevation = 1.dp,
    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
  ) {
    Column(Modifier.padding(16.dp)) {
      Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Timeline, contentDescription = null, tint = primary, modifier = Modifier.size(20.dp))
          Spacer(Modifier.width(8.dp))
          Column {
            Text("Selection signal map", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Internal ranking signals — not YouTube retention analytics", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) {
          Row(Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("${clips.size} candidates", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
          }
        }
      }

      Text(
        "Tap a highlighted zone to jump to that candidate in the editor.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
      )

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(112.dp)
          .clip(MaterialTheme.shapes.large)
          .background(MaterialTheme.colorScheme.surfaceContainerHighest)
          .pointerInput(clips, totalDurationSeconds) {
            detectTapGestures { offset ->
              if (totalDurationSeconds > 0 && clips.isNotEmpty()) {
                val tappedSec = ((offset.x / size.width) * totalDurationSeconds).toInt()
                clips.minByOrNull { clip -> kotlin.math.abs(((clip.startSeconds + clip.endSeconds) / 2) - tappedSec) }
                  ?.let(onSelectClip)
              }
            }
          }
      ) {
        Canvas(Modifier.matchParentSize()) {
          val width = size.width
          val height = size.height

          for (i in 1..3) {
            val y = height * (i / 4f)
            drawLine(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), Offset(0f, y), Offset(width, y), 1.dp.toPx())
          }

          clips.forEachIndexed { index, clip ->
            val startX = (clip.startSeconds.toFloat() / totalDurationSeconds.coerceAtLeast(1)).coerceIn(0f, 1f) * width
            val endX = (clip.endSeconds.toFloat() / totalDurationSeconds.coerceAtLeast(1)).coerceIn(0f, 1f) * width
            val color = colors[index % colors.size]
            val selected = selectedClip?.id == clip.id
            drawRect(
              color = color.copy(alpha = if (selected) 0.28f else 0.13f),
              topLeft = Offset(startX, 0f),
              size = Size((endX - startX).coerceAtLeast(12.dp.toPx()), height)
            )
          }

          if (retentionPoints.isNotEmpty()) {
            val path = Path()
            retentionPoints.forEachIndexed { index, point ->
              val x = point.timeFraction.coerceIn(0f, 1f) * width
              val normalized = ((point.retentionPercent - 20) / 80f).coerceIn(0f, 1f)
              val y = height - normalized * (height - 14.dp.toPx())
              if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, primary, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
          }
        }
      }

      Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        clips.forEach { clip ->
          val selected = selectedClip?.id == clip.id
          Surface(
            onClick = { onSelectClip(clip) },
            shape = RoundedCornerShape(10.dp),
            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
            border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.weight(1f)
          ) {
            Row(Modifier.padding(horizontal = 7.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
              Box(Modifier.size(7.dp).clip(CircleShape).background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline))
              Spacer(Modifier.width(4.dp))
              Text("#${clip.clipIndex}", fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
            }
          }
        }
      }
    }
  }
}
