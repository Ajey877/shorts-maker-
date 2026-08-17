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
import androidx.compose.material.icons.filled.TrendingUp
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.RetentionPoint
import com.example.model.ShortClip
import com.example.ui.theme.AmberGlow
import com.example.ui.theme.BrightCrimson
import com.example.ui.theme.GeoPurple
import com.example.ui.theme.GeoPurpleLight
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
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.TrendingUp,
            contentDescription = "Retention Trend",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Audience Retention & Heatmap",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
        }

        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.primaryContainer
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.AutoAwesome,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "4 Peak Viral Zones",
              color = MaterialTheme.colorScheme.primary,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }
      }

      Text(
        text = "Tap on any highlighted peak segment below to preview that ready-to-post 10-30s Short.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
      )

      // The Retention Graph Canvas
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(110.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(Color(0xFF101015))
          .pointerInput(clips, totalDurationSeconds) {
            detectTapGestures { offset ->
              if (totalDurationSeconds > 0 && clips.isNotEmpty()) {
                val tappedFraction = offset.x / size.width
                val tappedSec = (tappedFraction * totalDurationSeconds).toInt()
                // Find closest clip
                val closest = clips.minByOrNull { clip ->
                  val mid = (clip.startSeconds + clip.endSeconds) / 2
                  Math.abs(mid - tappedSec)
                }
                closest?.let { onSelectClip(it) }
              }
            }
          }
      ) {
        Canvas(modifier = Modifier.matchParentSize()) {
          if (retentionPoints.isEmpty()) return@Canvas

          val width = size.width
          val height = size.height

          // Draw Horizontal grid lines
          val gridLineCount = 3
          for (i in 1..gridLineCount) {
            val y = height * (i / (gridLineCount + 1f))
            drawLine(
              color = Color.White.copy(alpha = 0.08f),
              start = Offset(0f, y),
              end = Offset(width, y),
              strokeWidth = 1.dp.toPx()
            )
          }

          // 1. Draw Highlighted Clip Zone spans behind the curve
          if (totalDurationSeconds > 0) {
            clips.forEachIndexed { index, clip ->
              val startFraction = (clip.startSeconds.toFloat() / totalDurationSeconds).coerceIn(0f, 1f)
              val endFraction = (clip.endSeconds.toFloat() / totalDurationSeconds).coerceIn(0f, 1f)

              val startX = startFraction * width
              val endX = (endFraction * width).coerceAtLeast(startX + 18.dp.toPx())
              val zoneColor = clipColors[index % clipColors.size]
              val isSelected = selectedClip?.id == clip.id

              // Background highlight column
              drawRect(
                color = zoneColor.copy(alpha = if (isSelected) 0.35f else 0.15f),
                topLeft = Offset(startX, 0f),
                size = Size(endX - startX, height)
              )

              // Top border indicator
              drawRect(
                color = zoneColor,
                topLeft = Offset(startX, 0f),
                size = Size(endX - startX, if (isSelected) 4.dp.toPx() else 2.dp.toPx())
              )
            }
          }

          // 2. Build Retention Curve Path
          val path = Path()
          val fillPath = Path()

          retentionPoints.forEachIndexed { index, pt ->
            val x = pt.timeFraction * width
            val normalizedRetention = (pt.retentionPercent - 20) / 80f // 20% to 100%
            val y = height - (normalizedRetention * (height - 15.dp.toPx()))

            if (index == 0) {
              path.moveTo(x, y)
              fillPath.moveTo(x, height)
              fillPath.lineTo(x, y)
            } else {
              val prev = retentionPoints[index - 1]
              val prevX = prev.timeFraction * width
              val prevY = height - ((prev.retentionPercent - 20) / 80f * (height - 15.dp.toPx()))
              val cx = (prevX + x) / 2
              path.cubicTo(cx, prevY, cx, y, x, y)
              fillPath.cubicTo(cx, prevY, cx, y, x, y)
            }
          }

          fillPath.lineTo(width, height)
          fillPath.close()

          // Draw gradient fill
          drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
              colors = listOf(
                BrightCrimson.copy(alpha = 0.45f),
                Color.Transparent
              ),
              startY = 0f,
              endY = height
            )
          )

          // Draw curve stroke line
          drawPath(
            path = path,
            brush = Brush.horizontalGradient(
              colors = listOf(BrightCrimson, AmberGlow, NeonCyan, YouTubeRed)
            ),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
          )

          // Draw Peak Star / Marker Dots
          retentionPoints.filter { it.isPeak }.forEach { pt ->
            val x = pt.timeFraction * width
            val normalizedRetention = (pt.retentionPercent - 20) / 80f
            val y = height - (normalizedRetention * (height - 15.dp.toPx()))

            drawCircle(
              color = Color.White,
              radius = 5.dp.toPx(),
              center = Offset(x, y)
            )
            drawCircle(
              color = YouTubeRed,
              radius = 3.5.dp.toPx(),
              center = Offset(x, y)
            )
          }
        }
      }

      // Clip indicator chips underneath heatmap
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        clips.forEachIndexed { index, clip ->
          val isSelected = selectedClip?.id == clip.id
          val color = clipColors[index % clipColors.size]

          Surface(
            onClick = { onSelectClip(clip) },
            shape = RoundedCornerShape(8.dp),
            color = if (isSelected) color.copy(alpha = 0.2f) else Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(
              width = if (isSelected) 1.5.dp else 1.dp,
              color = if (isSelected) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            ),
            modifier = Modifier.padding(horizontal = 2.dp)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(8.dp)
                  .clip(CircleShape)
                  .background(color)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "Short #${clip.clipIndex} (${clip.durationSeconds}s)",
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) color else MaterialTheme.colorScheme.onSurface
              )
            }
          }
        }
      }
    }
  }
}
