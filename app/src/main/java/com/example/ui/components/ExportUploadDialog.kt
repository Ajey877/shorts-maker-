package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.ShortClip
import com.example.ui.theme.AmberGlow
import com.example.ui.theme.ViralGreen
import com.example.ui.theme.YouTubeRed

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportUploadDialog(
  clip: ShortClip,
  isLocalMedia: Boolean,
  isExporting: Boolean,
  hasExportedClip: Boolean,
  onDismiss: () -> Unit,
  onExport: () -> Unit,
  onShareExported: () -> Unit,
  onCopyMetadata: () -> Unit,
  onOpenYouTubeShorts: () -> Unit,
  onMarkPosted: () -> Unit
) {
  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Surface(
      modifier = Modifier.fillMaxWidth(0.94f).padding(vertical = 24.dp),
      shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
      border = BorderStroke(1.5.dp, YouTubeRed.copy(alpha = 0.5f))
    ) {
      Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(YouTubeRed), contentAlignment = Alignment.Center) {
              Icon(Icons.Default.SmartDisplay, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
              Text("Export & Post", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
              Text("Clip #${clip.clipIndex} • ${clip.durationSeconds}s", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
          IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
        }

        Spacer(Modifier.height(16.dp))

        Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))) {
          Column(Modifier.padding(14.dp)) {
            Text(if (isLocalMedia) "Ready to create the MP4" else "Import a local video to export", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text(
              if (isLocalMedia) "ClipMint will export the selected timestamp range as a real MP4 file."
              else "A YouTube page URL is metadata only and cannot be exported as a video file.",
              fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp
            )
          }
        }

        Spacer(Modifier.height(14.dp))
        Text("Title", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp)) {
          Text(clip.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(10.dp))
        }
        Text("Segment Range", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp)) {
          Text("⏱️ ${clip.rangeFormatted} (${clip.startSeconds}s–${clip.endSeconds}s)", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = AmberGlow, modifier = Modifier.padding(10.dp))
        }
        Text("Hashtags", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp)) {
          clip.suggestedHashtags.forEach { tag ->
            Surface(shape = RoundedCornerShape(6.dp), color = YouTubeRed.copy(alpha = 0.12f)) {
              Text(tag, fontSize = 11.sp, color = YouTubeRed, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
          }
        }

        Button(
          onClick = onExport,
          enabled = isLocalMedia && !isExporting,
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
          shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
        ) {
          if (isExporting) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
          else Icon(Icons.Default.FileDownload, null)
          Spacer(Modifier.width(8.dp))
          Text(if (isExporting) "Exporting MP4…" else if (hasExportedClip) "Export Again" else "Export MP4", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        if (hasExportedClip) {
          Spacer(Modifier.height(8.dp))
          FilledTonalButton(onClick = onShareExported, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Share, null)
            Spacer(Modifier.width(8.dp))
            Text("Share MP4", fontSize = 14.sp, fontWeight = FontWeight.Bold)
          }
        }

        Spacer(Modifier.height(8.dp))
        Button(onClick = { onOpenYouTubeShorts(); onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed, contentColor = Color.White), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
          Icon(Icons.Default.Launch, null); Spacer(Modifier.width(8.dp)); Text("Copy Metadata & Open YouTube Studio", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = onCopyMetadata, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Copy", fontSize = 12.sp)
          }
          FilledTonalButton(onClick = { onMarkPosted(); onDismiss() }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.CheckCircle, null, tint = ViralGreen, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(if (clip.isPosted) "Posted ✓" else "Mark Posted", fontSize = 12.sp)
          }
        }
      }
    }
  }
}
