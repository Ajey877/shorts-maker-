package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ShortClip

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClipCard(
  clip: ShortClip,
  isSelected: Boolean,
  onSelect: () -> Unit,
  onCopyMetadata: () -> Unit,
  onSaveClip: () -> Unit,
  onUploadShorts: () -> Unit,
  onShare: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    onClick = onSelect,
    modifier = modifier.fillMaxWidth(),
    shape = MaterialTheme.shapes.extraLarge,
    colors = CardDefaults.cardColors(
      containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainerLow
    ),
    border = BorderStroke(
      width = if (isSelected) 2.dp else 1.dp,
      color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp)
  ) {
    Column(Modifier.padding(16.dp)) {
      Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Surface(
            shape = MaterialTheme.shapes.small,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
          ) {
            Text(
              "SHORT #${clip.clipIndex}",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
            )
          }
          Spacer(Modifier.width(8.dp))
          FilterChip(
            selected = false,
            onClick = {},
            label = { Text("Selection ${clip.viralityScore}", fontSize = 11.sp) },
            leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp)) }
          )
        }
        Text(clip.rangeFormatted, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }

      Spacer(Modifier.height(10.dp))
      Text(clip.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

      Surface(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest
      ) {
        Column(Modifier.padding(12.dp)) {
          Text("Suggested hook", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Spacer(Modifier.height(4.dp))
          Text(clip.hookHeadline, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
      }

      Text(clip.whyViralReason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Spacer(Modifier.height(8.dp))
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        clip.suggestedHashtags.take(5).forEach { tag ->
          FilterChip(selected = false, onClick = {}, label = { Text(tag, fontSize = 10.sp) })
        }
      }

      Spacer(Modifier.height(14.dp))
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onUploadShorts, modifier = Modifier.weight(1f)) {
          Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text("Post")
        }
        OutlinedButton(onClick = onCopyMetadata) {
          Icon(Icons.Default.ContentCopy, contentDescription = "Copy metadata", modifier = Modifier.size(16.dp))
        }
        OutlinedButton(onClick = onSaveClip) {
          Icon(if (clip.isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, contentDescription = "Save clip", modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = onShare) { Icon(Icons.Default.Share, contentDescription = "Share") }
      }
    }
  }
}
