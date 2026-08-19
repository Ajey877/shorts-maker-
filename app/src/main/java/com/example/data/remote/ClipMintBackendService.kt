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

class ClipMintBackendService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val baseUrl: String
        get() = BuildConfig.BACKEND_BASE_URL.trimEnd('/')

    suspend fun analyze(url: String, clipCount: Int = 4, clipLength: Int = 30): BackendAnalysis = withContext(Dispatchers.IO) {
        if (baseUrl.contains("REPLACE", ignoreCase = true)) {
            throw IllegalStateException("ClipMint backend URL is not configured in this build.")
        }
        val body = JSONObject().apply {
            put("url", url)
            put("clipCount", clipCount)
            put("clipLength", clipLength)
        }.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url("$baseUrl/api/analyze").post(body).build()
        try {
            client.newCall(request).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val serverMessage = runCatching { JSONObject(responseText).optString("error") }.getOrNull().orEmpty()
                    val detail = if (serverMessage.isNotBlank()) serverMessage else "HTTP ${response.code}"
                    throw IllegalStateException("Backend analysis failed: $detail")
                }
                val json = JSONObject(responseText)
                val videoJson = json.getJSONObject("video")
                val video = YouTubeVideoInfo(
                    id = videoJson.getString("id"), url = videoJson.getString("url"),
                    title = videoJson.optString("title", "YouTube video"),
                    channelName = videoJson.optString("channelName", "YouTube creator"),
                    durationSeconds = videoJson.optInt("durationSeconds", 600), viewCountFormatted = "",
                    thumbnailUrl = videoJson.optString("thumbnailUrl", "https://img.youtube.com/vi/${videoJson.getString("id")}/hqdefault.jpg"), description = ""
                )
                val clipsJson = json.optJSONArray("clips") ?: JSONArray()
                if (clipsJson.length() == 0) throw IllegalStateException("Backend returned no usable Shorts.")
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
                        startSeconds = c.optInt("startSeconds", 0), endSeconds = c.optInt("endSeconds", 15), viralityScore = c.optInt("viralityScore", 0),
                        whyViralReason = c.optString("whyViralReason", "Selected from transcript signals."),
                        keyTakeaway = c.optString("keyTakeaway", "Standalone short-form moment."),
                        suggestedHashtags = tags.ifEmpty { listOf("#Shorts", "#youtubeshorts") },
                        youtubeShortsDescription = c.optString("youtubeShortsDescription", "Generated Short"), sampleSubtitles = subs
                    ))
                }
                BackendAnalysis(video, clips)
            }
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException("Could not reach ClipMint backend: ${e.message ?: "network error"}", e)
        }
    }

    fun enqueueDownload(context: Context, videoUrl: String, clip: ShortClip): Long? {
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
                val request = Request.Builder().url(downloadUrl).header("Accept", "video/mp4").header("Cache-Control", "no-cache").build()
                client.newCall(request).execute().use { response ->
                    val body = response.body
                    if (!response.isSuccessful || body == null) {
                        val responseText = body?.string().orEmpty()
                        val serverMessage = runCatching { JSONObject(responseText).optString("error") }.getOrNull().orEmpty()
                        val detail = if (serverMessage.isNotBlank()) serverMessage else "HTTP ${response.code}"
                        throw IllegalStateException("Render failed: $detail")
                    }
                    val fileName = "short-${clip.clipIndex}-${clip.startSeconds}-${clip.endSeconds}.mp4"
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
                            resolver.openOutputStream(uri)?.use { output -> body.byteStream().use { input -> input.copyTo(output, DEFAULT_BUFFER_SIZE) } }
                                ?: throw IllegalStateException("Could not open the Downloads file")
                            values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0); resolver.update(uri, values, null, null)
                        } catch (e: Exception) { resolver.delete(uri, null, null); throw e }
                    } else {
                        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "ClipMint")
                        if (!dir.exists()) dir.mkdirs()
                        val file = File(dir, fileName)
                        FileOutputStream(file).use { output -> body.byteStream().use { input -> input.copyTo(output, DEFAULT_BUFFER_SIZE) } }
                        android.media.MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("video/mp4"), null)
                    }
                    Handler(Looper.getMainLooper()).post { Toast.makeText(context, "Short #${clip.clipIndex} saved to Downloads/ClipMint", Toast.LENGTH_LONG).show() }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post { Toast.makeText(context, "Download failed: ${e.message ?: "unknown error"}", Toast.LENGTH_LONG).show() }
            }
        }
        return jobId
    }

    data class BackendAnalysis(val video: YouTubeVideoInfo, val clips: List<ShortClip>)
}
