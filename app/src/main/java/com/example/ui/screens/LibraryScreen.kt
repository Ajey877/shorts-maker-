package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.ClipEntity
import com.example.model.ShortClip

@Composable
fun LibraryScreen(
  savedClips: List<ClipEntity>,
  onSelectAndPreviewClip: (ShortClip) -> Unit,
  onCopyMetadata: (ShortClip) -> Unit,
  onOpenYouTubeShorts: (ShortClip) -> Unit,
  onTogglePostedStatus: (String, Boolean) -> Unit,
  onDeleteClip: (String) -> Unit,
  onGoToStudio: () -> Unit,
  modifier: Modifier = Modifier
) {
  var filterIndex by remember { mutableStateOf(0) }
  val filteredList = when (filterIndex) {
    1 -> savedClips.filter { !it.isPosted }
    2 -> savedClips.filter { it.isPosted }
    else -> savedClips
  }
  val context = LocalContext.current

  Column(
    modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(Modifier.weight(1f)) {
        Text("Library", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
          "Keep finished Shorts ready to review and publish.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer
      ) {
        Text(
          "${savedClips.count { it.isPosted }} posted",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
        )
      }
    }

    Row(
      modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      FilterChip(selected = filterIndex == 0, onClick = { filterIndex = 0 }, label = { Text("All ${savedClips.size}") })
      FilterChip(selected = filterIndex == 1, onClick = { filterIndex = 1 }, label = { Text("Ready") })
      FilterChip(selected = filterIndex == 2, onClick = { filterIndex = 2 }, label = { Text("Posted") })
    }

    if (filteredList.isEmpty()) {
      Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
          Box(
            modifier = Modifier.size(72.dp).clip(CircleShape),
            contentAlignment = Alignment.Center
          ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxSize()) {}
            Icon(Icons.Default.BookmarkBorder, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(34.dp))
          }
          Spacer(Modifier.height(16.dp))
          Text("Nothing saved yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          Text(
            "Generate a Short in Studio, then save it here for later.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
          )
          Spacer(Modifier.height(16.dp))
          Button(onClick = onGoToStudio) { Text("Open Studio") }
        }
      }
    } else {
      LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
      ) {
        items(filteredList, key = { it.id }) { entity ->
          val domainClip = entity.toDomain()
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
          ) {
            Column(Modifier.padding(14.dp)) {
              Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                  model = ImageRequest.Builder(context).data(entity.thumbnailUrl).crossfade(true).build(),
                  contentDescription = null,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.size(86.dp, 56.dp).clip(MaterialTheme.shapes.medium)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                  Text(entity.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                  Text("${entity.channelName} • ${domainClip.rangeFormatted}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onDeleteClip(entity.id) }) {
                  Icon(Icons.Default.Delete, contentDescription = "Delete saved clip")
                }
              }

              Spacer(Modifier.height(10.dp))
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                  Text("Score ${entity.viralityScore}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                }
                Surface(shape = MaterialTheme.shapes.small, color = if (entity.isPosted) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest) {
                  Text(if (entity.isPosted) "Posted" else "Ready", style = MaterialTheme.typography.labelSmall, color = if (entity.isPosted) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { onTogglePostedStatus(entity.id, !entity.isPosted) }) {
                  Icon(if (entity.isPosted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, contentDescription = "Toggle posted status")
                }
              }

              Spacer(Modifier.height(10.dp))
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onOpenYouTubeShorts(domainClip) }, modifier = Modifier.weight(1f)) {
                  Icon(Icons.Default.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(5.dp))
                  Text("Post")
                }
                OutlinedButton(onClick = { onSelectAndPreviewClip(domainClip) }) {
                  Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(Modifier.width(5.dp))
                  Text("Studio")
                }
                OutlinedButton(onClick = { onCopyMetadata(domainClip) }) {
                  Icon(Icons.Default.ContentCopy, contentDescription = "Copy metadata", modifier = Modifier.size(16.dp))
                }
              }
            }
          }
        }
      }
    }
  }
}
