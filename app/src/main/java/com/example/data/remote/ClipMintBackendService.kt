package com.example.data.remote

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.example.BuildConfig
import com.example.model.ShortClip
import com.example.model.SubtitlePhrase
import com.example.model.YouTubeVideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ClipMintBackendService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val baseUrl: String
        get() = BuildConfig.BACKEND_BASE_URL.trimEnd('/')

    suspend fun analyze(url: String, clipCount: Int = 4, clipLength: Int = 30): BackendAnalysis? = withContext(Dispatchers.IO) {
        if (baseUrl.contains("REPLACE", ignoreCase = true)) return@withContext null

        val body = JSONObject().apply {
            put("url", url)
            put("clipCount", clipCount)
            put("clipLength", clipLength)
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$baseUrl/api/analyze")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val json = JSONObject(response.body?.string() ?: return@withContext null)
                val videoJson = json.getJSONObject("video")
                val video = YouTubeVideoInfo(
                    id = videoJson.getString("id"),
                    url = videoJson.getString("url"),
                    title = videoJson.optString("title", "YouTube video"),
                    channelName = videoJson.optString("channelName", "YouTube creator"),
                    durationSeconds = videoJson.optInt("durationSeconds", 600),
                    viewCountFormatted = "",
                    thumbnailUrl = videoJson.optString("thumbnailUrl", "https://img.youtube.com/vi/${videoJson.getString("id")}/hqdefault.jpg"),
                    description = ""
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
                    clips.add(
                        ShortClip(
                            id = c.optString("id", "backend-${i + 1}"),
                            videoId = video.id,
                            clipIndex = c.optInt("clipIndex", i + 1),
                            title = c.optString("title", "Viral Short #${i + 1}"),
                            hookHeadline = c.optString("hookHeadline", "WATCH THIS"),
                            startSeconds = c.optInt("startSeconds", 0),
                            endSeconds = c.optInt("endSeconds", 15),
                            viralityScore = c.optInt("viralityScore", 90),
                            whyViralReason = c.optString("whyViralReason", "Distinct segment from the source video."),
                            keyTakeaway = c.optString("keyTakeaway", "Standalone short-form moment."),
                            suggestedHashtags = tags.ifEmpty { listOf("#Shorts", "#viral", "#youtubeshorts") },
                            youtubeShortsDescription = c.optString("youtubeShortsDescription", "Generated Short"),
                            sampleSubtitles = subs
                        )
                    )
                }
                BackendAnalysis(video, clips)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun enqueueDownload(context: Context, videoUrl: String, clip: ShortClip): Long? {
        if (baseUrl.contains("REPLACE", ignoreCase = true)) return null
        val downloadUrl = Uri.parse("$baseUrl/api/render").buildUpon()
            .appendQueryParameter("url", videoUrl)
            .appendQueryParameter("start", clip.startSeconds.toString())
            .appendQueryParameter("end", clip.endSeconds.toString())
            .build()

        val request = DownloadManager.Request(downloadUrl)
            .setTitle("Short #${clip.clipIndex} • ${clip.title}")
            .setDescription("ClipMint is rendering your ${clip.durationSeconds}s Short")
            .setMimeType("video/mp4")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "ClipMint/short-${clip.clipIndex}-${clip.startSeconds}-${clip.endSeconds}.mp4")

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return manager.enqueue(request)
    }

    data class BackendAnalysis(
        val video: YouTubeVideoInfo,
        val clips: List<ShortClip>
    )
}
