package com.example.data.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.example.model.ShortClip
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class ClipExportService(private val context: Context) {
  suspend fun export(sourceUri: Uri, clip: ShortClip): File = suspendCancellableCoroutine { continuation ->
    val outputDir = context.getExternalFilesDir("clips") ?: context.cacheDir
    outputDir.mkdirs()
    val output = File(outputDir, "ClipMint_${clip.clipIndex}_${UUID.randomUUID()}.mp4")
    val mediaItem = MediaItem.Builder()
      .setUri(sourceUri)
      .setClippingConfiguration(
        MediaItem.ClippingConfiguration.Builder()
          .setStartPositionMs(clip.startSeconds.coerceAtLeast(0) * 1000L)
          .setEndPositionMs(clip.endSeconds.coerceAtLeast(clip.startSeconds + 1) * 1000L)
          .build()
      ).build()
    val edited = EditedMediaItem.Builder(mediaItem).build()
    val transformer = Transformer.Builder(context)
      .addListener(object : Transformer.Listener {
        override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: ExportResult) {
          if (continuation.isActive) continuation.resume(output)
        }
        override fun onError(composition: androidx.media3.transformer.Composition, exportResult: ExportResult, exportException: ExportException) {
          output.delete()
          if (continuation.isActive) continuation.resumeWithException(exportException)
        }
      }).build()
    continuation.invokeOnCancellation { runCatching { transformer.cancel() }; output.delete() }
    try { transformer.start(edited, output.absolutePath) }
    catch (e: Exception) { output.delete(); if (continuation.isActive) continuation.resumeWithException(e) }
  }
}
