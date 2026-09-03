package com.example.data.media

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.CanvasOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.example.model.CaptionStyle
import com.example.model.ShortClip
import java.io.File
import java.util.UUID

@OptIn(UnstableApi::class)
class ClipExportService(private val context: Context) {
  companion object {
    private const val OUTPUT_WIDTH = 1080
    private const val OUTPUT_HEIGHT = 1920
  }

  fun export(
    sourceUri: Uri,
    clip: ShortClip,
    captionStyle: CaptionStyle,
    hookHeadline: String,
    onCompleted: (File) -> Unit,
    onError: (Throwable) -> Unit
  ) {
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

    val verticalCrop = Presentation.createForWidthAndHeight(
      OUTPUT_WIDTH,
      OUTPUT_HEIGHT,
      Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
    )

    val overlay = createCaptionOverlay(clip, captionStyle, hookHeadline)
    val edited = EditedMediaItem.Builder(mediaItem)
      .setEffects(Effects(emptyList(), listOf(verticalCrop, OverlayEffect(listOf(overlay)))))
      .build()

    val transformer = Transformer.Builder(context)
      .addListener(object : Transformer.Listener {
        override fun onCompleted(
          composition: androidx.media3.transformer.Composition,
          exportResult: ExportResult
        ) {
          if (output.exists() && output.length() > 0L) {
            onCompleted(output)
          } else {
            onError(IllegalStateException("Media3 completed without producing a valid MP4"))
          }
        }

        override fun onError(
          composition: androidx.media3.transformer.Composition,
          exportResult: ExportResult,
          exportException: ExportException
        ) {
          output.delete()
          onError(exportException)
        }
      })
      .build()

    try {
      transformer.start(edited, output.absolutePath)
    } catch (e: Exception) {
      output.delete()
      onError(e)
    }
  }

  private fun createCaptionOverlay(
    clip: ShortClip,
    style: CaptionStyle,
    hookHeadline: String
  ): CanvasOverlay {
    val hook = hookHeadline.trim().takeIf { it.isNotBlank() && it != "EDIT THIS HOOK" }
    return object : CanvasOverlay(false) {
      private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
      }
      private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        color = android.graphics.Color.BLACK
        style = Paint.Style.STROKE
      }

      init {
        setCanvasSize(OUTPUT_WIDTH, OUTPUT_HEIGHT)
      }

      override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
        val timeSec = presentationTimeUs / 1_000_000f
        if (hook != null) {
          drawOutlinedText(canvas, hook, OUTPUT_WIDTH / 2f, 210f, 72f, 12f, style.badgeColor.toInt())
        }

        val subtitle = clip.sampleSubtitles.lastOrNull { it.relativeSec <= timeSec }
        if (subtitle != null && subtitle.text.isNotBlank()) {
          drawOutlinedText(canvas, subtitle.text, OUTPUT_WIDTH / 2f, 1640f, 62f, 10f, style.badgeColor.toInt())
        }
      }

      private fun drawOutlinedText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        size: Float,
        strokeWidth: Float,
        color: Int
      ) {
        fill.textSize = size
        fill.color = color
        stroke.textSize = size
        stroke.strokeWidth = strokeWidth
        canvas.drawText(text, x, y, stroke)
        canvas.drawText(text, x, y, fill)
      }
    }
  }
}
