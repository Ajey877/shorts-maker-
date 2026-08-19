package com.example.work

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class ClipExportWorker(
  appContext: Context,
  workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

  private val client = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(240, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .build()

  override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    val videoUrl = inputData.getString(KEY_VIDEO_URL)?.trim().orEmpty()
    val clipsJson = inputData.getString(KEY_CLIPS).orEmpty()
    val captionStyle = inputData.getString(KEY_CAPTION_STYLE).orEmpty().ifBlank { "HORMOZI_BOLD" }

    if (videoUrl.isBlank() || clipsJson.isBlank()) return@withContext Result.failure()

    val clips = try {
      val array = JSONArray(clipsJson)
      (0 until array.length()).map { index ->
        val obj = array.getJSONObject(index)
        ExportClip(
          clipIndex = obj.optInt("clipIndex", index + 1),
          start = obj.getInt("start"),
          end = obj.getInt("end"),
          hook = obj.optString("hook", "")
        )
      }
    } catch (_: Exception) {
      return@withContext Result.failure()
    }

    if (clips.isEmpty()) return@withContext Result.failure()

    clips.forEachIndexed { index, clip ->
      ensureActive()
      val requestUrl = buildRenderUrl(videoUrl, clip, captionStyle)
      val request = Request.Builder()
        .url(requestUrl)
        .header("Accept", "video/mp4")
        .header("Cache-Control", "no-cache")
        .build()

      val response = try {
        client.newCall(request).execute()
      } catch (_: Exception) {
        return@withContext Result.retry()
      }

      response.use { httpResponse ->
        if (!httpResponse.isSuccessful) {
          return@withContext if (httpResponse.code == 429 || httpResponse.code >= 500) {
            Result.retry()
          } else {
            Result.failure()
          }
        }

        val body = httpResponse.body ?: return@withContext Result.retry()
        val totalBytes = body.contentLength()
        var downloaded = 0L
        val fileName = "clipmint-short-${clip.clipIndex}-${clip.start}-${clip.end}.mp4"

        try {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
              put(MediaStore.Downloads.DISPLAY_NAME, fileName)
              put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
              put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ClipMint")
              put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = applicationContext.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
              ?: throw IllegalStateException("Could not create Downloads file")
            try {
              resolver.openOutputStream(uri)?.use { output ->
                body.byteStream().use { input ->
                  val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                  while (true) {
                    ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    updateProgress(index, clips.size, downloaded, totalBytes)
                  }
                }
              } ?: throw IllegalStateException("Could not open Downloads file")
              values.clear()
              values.put(MediaStore.Downloads.IS_PENDING, 0)
              resolver.update(uri, values, null, null)
            } catch (error: Exception) {
              resolver.delete(uri, null, null)
              throw error
            }
          } else {
            val directory = File(
              Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
              "ClipMint"
            )
            if (!directory.exists()) directory.mkdirs()
            val file = File(directory, fileName)
            FileOutputStream(file).use { output ->
              body.byteStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                  ensureActive()
                  val read = input.read(buffer)
                  if (read < 0) break
                  output.write(buffer, 0, read)
                  downloaded += read
                  updateProgress(index, clips.size, downloaded, totalBytes)
                }
              }
            }
            android.media.MediaScannerConnection.scanFile(
              applicationContext,
              arrayOf(file.absolutePath),
              arrayOf("video/mp4"),
              null
            )
          }
        } catch (_: Exception) {
          return@withContext Result.retry()
        }
      }
    }

    setProgress(androidx.work.workDataOf(KEY_PROGRESS to 100))
    Result.success()
  }

  private fun updateProgress(index: Int, totalClips: Int, downloaded: Long, totalBytes: Long) {
    val clipProgress = if (totalBytes > 0) (downloaded.toDouble() / totalBytes).coerceIn(0.0, 1.0) else 0.0
    val overall = (((index + clipProgress) / totalClips.toDouble()) * 100.0).toInt().coerceIn(0, 99)
    setProgressAsync(androidx.work.workDataOf(KEY_PROGRESS to overall))
  }

  private fun buildRenderUrl(videoUrl: String, clip: ExportClip, captionStyle: String): String {
    val base = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
    val url = UriBuilder("$base/api/render")
      .query("url", videoUrl)
      .query("start", clip.start.toString())
      .query("end", clip.end.toString())
      .query("captions", "1")
      .query("captionStyle", captionStyle)
    if (clip.hook.isNotBlank()) url.query("hook", clip.hook.take(180))
    return url.build()
  }

  private data class ExportClip(
    val clipIndex: Int,
    val start: Int,
    val end: Int,
    val hook: String
  )

  companion object {
    const val KEY_VIDEO_URL = "videoUrl"
    const val KEY_CLIPS = "clips"
    const val KEY_CAPTION_STYLE = "captionStyle"
    const val KEY_PROGRESS = "progress"
  }
}

private class UriBuilder(private val base: String) {
  private val params = mutableListOf<Pair<String, String>>()

  fun query(name: String, value: String): UriBuilder {
    params += name to value
    return this
  }

  fun build(): String = base + params.joinToString(prefix = "?", separator = "&") { (key, value) ->
    "${encode(key)}=${encode(value)}"
  }

  private fun encode(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
}
