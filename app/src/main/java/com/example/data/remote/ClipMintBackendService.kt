package com.example.data.remote

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import com.example.BuildConfig
import com.example.model.ShortClip
import com.example.model.SubtitlePhrase
import com.example.model.YouTubeVideoInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class ClipMintBackendService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(240, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val baseUrl: String
        get() = BuildConfig.BACKEND_BASE_URL.trimEnd('/')

    fun previewUrl(videoUrl: String, clip: ShortClip): String {
        return Uri.parse("$baseUrl/api/preview").buildUpon()
            .appendQueryParameter("url", videoUrl)
            .appendQueryParameter("start", clip.startSeconds.toString())
            .appendQueryParameter("end", clip.endSeconds.toString())
            .build().toString()
    }

    suspend fun analyze(url: String, clipCount: Int = 4, clipLength: Int = 30): BackendAnalysis? = withContext(Dispatchers.IO) {
        if (baseUrl.contains("REPLACE", ignoreCase = true)) return@withContext null
        val body = JSONObject().apply { put("url", url); put("clipCount", clipCount); put("clipLength", clipLength) }
            .toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url("$baseUrl/api/analyze").post(body).build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val json = JSONObject(response.body?.string() ?: return@withContext null)
                val videoJson = json.getJSONObject("video")
                val video = YouTubeVideoInfo(
                    id = videoJson.getString("id"), url = videoJson.getString("url"),
                    title = videoJson.optString("title", "YouTube video"),
                    channelName = videoJson.optString("channelName", "YouTube creator"),
                    durationSeconds = videoJson.optInt("durationSeconds", 600), viewCountFormatted = "",
                    thumbnailUrl = videoJson.optString("thumbnailUrl", "https://img.youtube.com/vi/${videoJson.getString("id")}/hqdefault.jpg"), description = ""
                )
                val clipsJson = json.optJSONArray("clips") ?: JSONArray()
                val clips = mutableListOf<ShortClip>()
                for (i in 0 until clipsJson.length()) {
                    val c = clipsJson.getJSONObject(i)
                    val subs = mutableListOf<SubtitlePhrase>()
                    val subArray = c.optJSONArray("sampleSubtitles") ?: JSONArray()
                    for (j in 0 until subArray.length()) {
                        val s = subArray.getJSONObject(j)
                        subs.add(SubtitlePhrase(s.optDouble("relativeSec", 0.5).toFloat(), s.optString("text", ""), s.optString("highlightWord", "")))
                    }
                    val tags = mutableListOf<String>()
                    val tagArray = c.optJSONArray("suggestedHashtags") ?: JSONArray()
                    for (j in 0 until tagArray.length()) tags.add(tagArray.optString(j))
                    clips.add(ShortClip(
                        id = c.optString("id", "backend-${i + 1}"), videoId = video.id, clipIndex = c.optInt("clipIndex", i + 1),
                        title = c.optString("title", "Short #${i + 1}"), hookHeadline = c.optString("hookHeadline", "WATCH THIS"),
                        startSeconds = c.optInt("startSeconds", 0), endSeconds = c.optInt("endSeconds", 15), viralityScore = c.optInt("viralityScore", 90),
                        whyViralReason = c.optString("whyViralReason", "Ranked candidate segment."),
                        keyTakeaway = c.optString("keyTakeaway", "Standalone short-form moment."),
                        suggestedHashtags = tags.ifEmpty { listOf("#Shorts", "#viral", "#youtubeshorts") },
                        youtubeShortsDescription = c.optString("youtubeShortsDescription", "Generated Short"), sampleSubtitles = subs
                    ))
                }
                BackendAnalysis(video, clips, json.optBoolean("transcriptAvailable", false))
            }
        } catch (_: Exception) { null }
    }

    fun enqueueDownload(
        context: Context,
        videoUrl: String,
        clip: ShortClip,
        onProgress: (Float) -> Unit = {},
        onComplete: () -> Unit = {},
        onError: (String) -> Unit = {}
    ): Long? {
        if (baseUrl.contains("REPLACE", ignoreCase = true)) return null
        val downloadUrl = Uri.parse("$baseUrl/api/render").buildUpon()
            .appendQueryParameter("url", videoUrl)
            .appendQueryParameter("start", clip.startSeconds.toString())
            .appendQueryParameter("end", clip.endSeconds.toString())
            .appendQueryParameter("captions", "1")
            .build().toString()
        val jobId = System.currentTimeMillis()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder()
                    .url(downloadUrl)
                    .header("Accept", "video/mp4")
                    .header("Cache-Control", "no-cache")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val serverMessage = response.body?.string()?.take(220).orEmpty()
                        throw IllegalStateException("Server returned HTTP ${response.code}${if (serverMessage.isNotBlank()) ": $serverMessage" else ""}")
                    }
                    val body = response.body ?: throw IllegalStateException("Empty video response from backend")
                    val totalBytes = body.contentLength()
                    var downloaded = 0L
                    val fileName = "clipmint-short-${clip.clipIndex}-${clip.startSeconds}-${clip.endSeconds}.mp4"

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                            put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
                            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ClipMint")
                            put(MediaStore.Downloads.IS_PENDING, 1)
                        }
                        val resolver = context.contentResolver
                        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                            ?: throw IllegalStateException("Android could not create the Downloads file")
                        try {
                            resolver.openOutputStream(uri)?.use { output ->
                                body.byteStream().use { input ->
                                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                    while (true) {
                                        val read = input.read(buffer)
                                        if (read < 0) break
                                        output.write(buffer, 0, read)
                                        downloaded += read
                                        if (totalBytes > 0) postProgress(onProgress, downloaded.toFloat() / totalBytes)
                                    }
                                }
                            } ?: throw IllegalStateException("Could not open the Downloads file")
                            values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0); resolver.update(uri, values, null, null)
                        } catch (e: Exception) {
                            resolver.delete(uri, null, null)
                            throw e
                        }
                    } else {
                        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "ClipMint")
                        if (!dir.exists()) dir.mkdirs()
                        val file = File(dir, fileName)
                        FileOutputStream(file).use { output ->
                            body.byteStream().use { input ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    output.write(buffer, 0, read)
                                    downloaded += read
                                    if (totalBytes > 0) postProgress(onProgress, downloaded.toFloat() / totalBytes)
                                }
                            }
                        }
                        android.media.MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("video/mp4"), null)
                    }

                    Handler(Looper.getMainLooper()).post {
                        onProgress(1f)
                        Toast.makeText(context, "Short #${clip.clipIndex} saved to Downloads/ClipMint", Toast.LENGTH_LONG).show()
                        onComplete()
                    }
                }
            } catch (e: Exception) {
                val message = e.message ?: "Download failed"
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    onError(message)
                }
            }
        }
        return jobId
    }

    fun enqueueBatchDownload(
        context: Context,
        videoUrl: String,
        clips: List<ShortClip>,
        onProgress: (Float) -> Unit = {},
        onComplete: () -> Unit = {},
        onError: (String) -> Unit = {}
    ): Long? {
        if (baseUrl.contains("REPLACE", ignoreCase = true) || clips.isEmpty()) return null
        val batchId = System.currentTimeMillis()
        CoroutineScope(Dispatchers.IO).launch {
            for ((index, clip) in clips.withIndex()) {
                val succeeded = suspendCancellableCoroutine<Boolean> { continuation ->
                    enqueueDownload(
                        context = context,
                        videoUrl = videoUrl,
                        clip = clip,
                        onProgress = { clipProgress ->
                            postProgress(onProgress, ((index + clipProgress) / clips.size).coerceIn(0f, 1f))
                        },
                        onComplete = { if (continuation.isActive) continuation.resume(true) },
                        onError = { if (continuation.isActive) continuation.resume(false) }
                    )
                }
                if (!succeeded) {
                    Handler(Looper.getMainLooper()).post { onError("Batch export stopped at Short #${clip.clipIndex}.") }
                    return@launch
                }
            }
            Handler(Looper.getMainLooper()).post {
                onProgress(1f)
                Toast.makeText(context, "All ${clips.size} Shorts saved to Downloads/ClipMint", Toast.LENGTH_LONG).show()
                onComplete()
            }
        }
        return batchId
    }

    private fun postProgress(onProgress: (Float) -> Unit, value: Float) {
        Handler(Looper.getMainLooper()).post { onProgress(value.coerceIn(0f, 1f)) }
    }

    data class BackendAnalysis(
        val video: YouTubeVideoInfo,
        val clips: List<ShortClip>,
        val transcriptAvailable: Boolean
    )
}
