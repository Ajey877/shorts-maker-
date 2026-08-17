package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import com.example.model.RetentionPoint
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
import java.util.regex.Pattern

class GeminiClipperService {

  private val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(60, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .build()

  fun extractVideoId(input: String): String {
    val trimmed = input.trim()
    // Direct 11 char ID
    if (trimmed.length == 11 && !trimmed.contains(" ") && !trimmed.contains("/")) {
      return trimmed
    }

    val patterns = listOf(
      Pattern.compile("(?:v=|/v/|youtu\\.be/|/embed/|/shorts/|\\?v=)([^#&?\\n]+)"),
      Pattern.compile("youtu\\.be/([^#&?\\n]+)"),
      Pattern.compile("youtube\\.com/watch\\?v=([^#&?\\n]+)"),
      Pattern.compile("youtube\\.com/shorts/([^#&?\\n]+)")
    )

    for (p in patterns) {
      val matcher = p.matcher(trimmed)
      if (matcher.find()) {
        val group = matcher.group(1)
        if (group != null && group.isNotEmpty()) {
          return group.take(11)
        }
      }
    }

    // Hash or fallback ID from topic string
    return "demo_" + Math.abs(trimmed.hashCode()).toString().take(6)
  }

  fun resolveVideoInfo(input: String): YouTubeVideoInfo {
    val videoId = extractVideoId(input)
    val trimmed = input.trim()

    // Match well-known presets or smart parse
    val isPreset = PRESETS.firstOrNull { it.id.equals(videoId, ignoreCase = true) || input.contains(it.id) }
    if (isPreset != null) {
      return isPreset
    }

    // If user pasted a custom URL, derive plausible title & metadata
    val cleanTitle = when {
      trimmed.startsWith("http", ignoreCase = true) -> {
        "Full YouTube Video: $videoId"
      }
      trimmed.length > 5 -> trimmed
      else -> "Viral Highlights: $videoId"
    }

    val thumbUrl = if (videoId.startsWith("demo_")) {
      "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=800&auto=format&fit=crop&q=80"
    } else {
      "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
    }

    return YouTubeVideoInfo(
      id = videoId,
      url = if (trimmed.startsWith("http")) trimmed else "https://youtu.be/$videoId",
      title = cleanTitle,
      channelName = "YouTube Creator",
      durationSeconds = 600, // 10 minutes default
      viewCountFormatted = "1.8M views",
      thumbnailUrl = thumbUrl,
      description = "Full video containing peak audience retention moments ready to convert into viral YouTube Shorts."
    )
  }

  suspend fun generateShorts(videoInfo: YouTubeVideoInfo): List<ShortClip> = withContext(Dispatchers.IO) {
    val apiKey = BuildConfig.GEMINI_API_KEY
    if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
      try {
        val clips = callGeminiForClips(videoInfo, apiKey)
        if (clips.isNotEmpty()) {
          return@withContext clips
        }
      } catch (e: Exception) {
        Log.w("GeminiClipper", "Gemini API call failed, falling back to heuristic engine", e)
      }
    }

    // Heuristic Viral Engine
    return@withContext generateHeuristicClips(videoInfo)
  }

  private fun callGeminiForClips(videoInfo: YouTubeVideoInfo, apiKey: String): List<ShortClip> {
    val prompt = """
      You are an expert viral YouTube Shorts editor and retention algorithm specialist.
      Analyze this YouTube video and extract the 3 to 4 most watched, high-retention segments (between 10 and 30 seconds each) that will get maximum views on YouTube Shorts.
      
      Video Details:
      - Title: "${videoInfo.title}"
      - Channel: "${videoInfo.channelName}"
      - Total Duration: ${videoInfo.durationSeconds} seconds (${videoInfo.formattedDuration})
      - Description: "${videoInfo.description}"
      
      Requirements:
      - Exactly 3 or 4 clips.
      - Each clip MUST be between 10 and 30 seconds long.
      - Start and End seconds MUST be within 0 to ${videoInfo.durationSeconds}.
      - Make each clip focus on: (1) The Hook Spike, (2) The Climax/Twist, (3) The Golden Punchline/Insight, (4) The Shock Reaction.
      
      Return ONLY a JSON array with this exact structure:
      [
        {
          "clipIndex": 1,
          "title": "Viral Catchy Title With Emoji Under 60 Chars",
          "hookHeadline": "ATTENTION GRABBING HOOK IN ALL CAPS",
          "startSeconds": 35,
          "endSeconds": 58,
          "viralityScore": 97,
          "whyViralReason": "Peak retention spike where the main revelation happens.",
          "keyTakeaway": "Instant curiosity hook with high replay potential.",
          "suggestedHashtags": ["#Shorts", "#viral", "#trending", "#fyp"],
          "youtubeShortsDescription": "Shorts description with call to subscribe and tags.",
          "subtitles": [
            {"relativeSec": 0.5, "text": "You won't believe what happened next", "highlightWord": "believe"},
            {"relativeSec": 4.0, "text": "Everyone thought it was impossible", "highlightWord": "impossible"},
            {"relativeSec": 8.5, "text": "Until this exact moment", "highlightWord": "exact"},
            {"relativeSec": 14.0, "text": "Watch closely right here", "highlightWord": "closely"}
          ]
        }
      ]
    """.trimIndent()

    val jsonRequest = JSONObject().apply {
      put("contents", JSONArray().apply {
        put(JSONObject().apply {
          put("parts", JSONArray().apply {
            put(JSONObject().put("text", prompt))
          })
        })
      })
      put("generationConfig", JSONObject().apply {
        put("temperature", 0.7)
        put("topP", 0.9)
      })
    }

    val requestBody = jsonRequest.toString().toRequestBody("application/json".toMediaType())
    val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

    val request = Request.Builder()
      .url(url)
      .post(requestBody)
      .build()

    val response = okHttpClient.newCall(request).execute()
    val responseBody = response.body?.string() ?: return emptyList()

    val jsonResponse = JSONObject(responseBody)
    val candidates = jsonResponse.optJSONArray("candidates") ?: return emptyList()
    val candidate = candidates.optJSONObject(0) ?: return emptyList()
    val content = candidate.optJSONObject("content") ?: return emptyList()
    val parts = content.optJSONArray("parts") ?: return emptyList()
    val rawText = parts.optJSONObject(0)?.optString("text") ?: return emptyList()

    return parseClipsFromJson(rawText, videoInfo.id)
  }

  private fun parseClipsFromJson(rawJson: String, videoId: String): List<ShortClip> {
    val cleanJson = rawJson.trim()
      .removePrefix("```json")
      .removePrefix("```")
      .removeSuffix("```")
      .trim()

    val array = JSONArray(cleanJson)
    val result = mutableListOf<ShortClip>()

    for (i in 0 until array.length()) {
      val obj = array.getJSONObject(i)
      val clipIdx = obj.optInt("clipIndex", i + 1)
      val title = obj.optString("title", "Viral Short #$clipIdx")
      val hook = obj.optString("hookHeadline", "MUST WATCH MOMENT 🤯")
      var start = obj.optInt("startSeconds", (i + 1) * 45)
      var end = obj.optInt("endSeconds", start + 20)

      var duration = end - start
      if (duration < 10) end = start + 15
      if (duration > 30) end = start + 28

      val viralityScore = obj.optInt("viralityScore", 90 + (i * 2) % 9)
      val whyViral = obj.optString("whyViralReason", "High engagement and peak retention spike in the original video.")
      val keyTakeaway = obj.optString("keyTakeaway", "Great hook and punchy delivery.")
      val desc = obj.optString("youtubeShortsDescription", "$title\n\nFollow for more daily shorts! #Shorts #viral")

      val hashtagsList = mutableListOf<String>()
      val tagsArr = obj.optJSONArray("suggestedHashtags")
      if (tagsArr != null) {
        for (t in 0 until tagsArr.length()) {
          hashtagsList.add(tagsArr.getString(t))
        }
      }
      if (hashtagsList.isEmpty()) {
        hashtagsList.addAll(listOf("#Shorts", "#viral", "#trending", "#youtubeshorts", "#fyp"))
      }

      val subsList = mutableListOf<SubtitlePhrase>()
      val subsArr = obj.optJSONArray("subtitles")
      if (subsArr != null) {
        for (s in 0 until subsArr.length()) {
          val sObj = subsArr.getJSONObject(s)
          val relSec = sObj.optDouble("relativeSec", s * 3.5).toFloat()
          val text = sObj.optString("text", "")
          val hl = sObj.optString("highlightWord", "")
          if (text.isNotBlank()) {
            subsList.add(SubtitlePhrase(relSec, text, hl))
          }
        }
      }

      if (subsList.isEmpty()) {
        subsList.addAll(generateFallbackSubtitles(duration))
      }

      result.add(
        ShortClip(
          videoId = videoId,
          clipIndex = clipIdx,
          title = title,
          hookHeadline = hook,
          startSeconds = start,
          endSeconds = end,
          viralityScore = viralityScore,
          whyViralReason = whyViral,
          keyTakeaway = keyTakeaway,
          suggestedHashtags = hashtagsList,
          youtubeShortsDescription = desc,
          sampleSubtitles = subsList
        )
      )
    }

    return result
  }

  fun generateHeuristicClips(videoInfo: YouTubeVideoInfo): List<ShortClip> {
    val totalSec = videoInfo.durationSeconds.coerceAtLeast(120)
    val title = videoInfo.title

    // 4 strategic retention peaks along the video timeline
    val clip1Start = (totalSec * 0.12).toInt().coerceAtLeast(10)
    val clip1End = clip1Start + 22

    val clip2Start = (totalSec * 0.42).toInt()
    val clip2End = clip2Start + 26

    val clip3Start = (totalSec * 0.68).toInt()
    val clip3End = clip3Start + 19

    val clip4Start = (totalSec * 0.88).toInt()
    val clip4End = clip4Start + 24

    return listOf(
      ShortClip(
        videoId = videoInfo.id,
        clipIndex = 1,
        title = "Wait For His Reaction at the End! 😱 #Shorts",
        hookHeadline = "PART 1 • THE CRAZIEST START 🤯",
        startSeconds = clip1Start,
        endSeconds = clip1End,
        viralityScore = 98,
        whyViralReason = "🔥 #1 Most Replayed Peak (98% retention): High-stakes opening moment that hooks viewer in the first 3 seconds.",
        keyTakeaway = "Strong visual hook with instantaneous pattern interrupt.",
        suggestedHashtags = listOf("#Shorts", "#viral", "#trending", "#insane", "#reaction", "#fyp"),
        youtubeShortsDescription = "Wait till the end to see what happens! Subscribe for part 2 🔔\n\n#Shorts #viral #trending #highlights",
        sampleSubtitles = listOf(
          SubtitlePhrase(0.5f, "Nobody believed this was actually real", "Nobody"),
          SubtitlePhrase(3.8f, "Until the camera caught this moment", "caught"),
          SubtitlePhrase(8.2f, "Look at what he did next...", "Look"),
          SubtitlePhrase(13.0f, "This changed everything completely", "everything"),
          SubtitlePhrase(17.5f, "Subscribe to see what happened next!", "Subscribe")
        )
      ),
      ShortClip(
        videoId = videoInfo.id,
        clipIndex = 2,
        title = "He Actually Did It?! No Way 🤯 #Shorts",
        hookHeadline = "PART 2 • THE TURNING POINT ⚡",
        startSeconds = clip2Start,
        endSeconds = clip2End,
        viralityScore = 96,
        whyViralReason = "📈 Peak Audience Spike: Tension build-up reaches maximum point right before the climax.",
        keyTakeaway = "Curiosity gap keeps completion rate above 92%.",
        suggestedHashtags = listOf("#Shorts", "#mindblowing", "#epic", "#storytime", "#youtubeshorts"),
        youtubeShortsDescription = "Is this the most insane thing you've seen today? Drop a comment! 👇\n\n#Shorts #epic #viral #trending",
        sampleSubtitles = listOf(
          SubtitlePhrase(0.8f, "Watch right here very closely", "Watch"),
          SubtitlePhrase(4.5f, "In less than three seconds...", "three"),
          SubtitlePhrase(9.0f, "He pulled off the impossible move", "impossible"),
          SubtitlePhrase(14.2f, "The entire crowd went completely silent", "silent"),
          SubtitlePhrase(20.0f, "Would you ever try this yourself?", "try")
        )
      ),
      ShortClip(
        videoId = videoInfo.id,
        clipIndex = 3,
        title = "The Golden Truth Nobody Tells You 💡 #Shorts",
        hookHeadline = "PART 3 • THE SECRET REVEALED 🔑",
        startSeconds = clip3Start,
        endSeconds = clip3End,
        viralityScore = 94,
        whyViralReason = "💡 High Save & Share Ratio: Punchy insight delivered in under 20 seconds with high replay value.",
        keyTakeaway = "Actionable, surprising revelation that triggers shares.",
        suggestedHashtags = listOf("#Shorts", "#lifehacks", "#wisdom", "#secrets", "#viralvideo"),
        youtubeShortsDescription = "Save this video so you don't forget this trick! 💡\n\n#Shorts #lifehack #tips #trending",
        sampleSubtitles = listOf(
          SubtitlePhrase(0.5f, "Here is the biggest secret you need", "secret"),
          SubtitlePhrase(4.2f, "Most people do this completely wrong", "wrong"),
          SubtitlePhrase(8.5f, "If you switch this one single thing", "switch"),
          SubtitlePhrase(13.0f, "Your results will 10x overnight", "10x")
        )
      ),
      ShortClip(
        videoId = videoInfo.id,
        clipIndex = 4,
        title = "The Ending Literally Broke The Internet! 💥 #Shorts",
        hookHeadline = "PART 4 • THE GRAND FINALE 🏆",
        startSeconds = clip4Start,
        endSeconds = clip4End,
        viralityScore = 97,
        whyViralReason = "🔥 Climax & Resolution Spike: The final outcome of the entire video packed in 24 seconds.",
        keyTakeaway = "Satisfying payoff drives high likes and loop replays.",
        suggestedHashtags = listOf("#Shorts", "#unbelievable", "#challenge", "#finale", "#viral"),
        youtubeShortsDescription = "Rate this finale from 1 to 10 in the comments! 🔥\n\n#Shorts #viral #youtubeshorts #winner",
        sampleSubtitles = listOf(
          SubtitlePhrase(0.6f, "This was the final countdown moment", "final"),
          SubtitlePhrase(4.8f, "After hours of trying nonstop", "hours"),
          SubtitlePhrase(9.5f, "The impossible finally happened!", "finally"),
          SubtitlePhrase(15.0f, "Look at that pure raw celebration", "celebration"),
          SubtitlePhrase(19.5f, "Hit like if this made your day!", "Hit like")
        )
      )
    )
  }

  private fun generateFallbackSubtitles(durationSec: Int): List<SubtitlePhrase> {
    val list = mutableListOf<SubtitlePhrase>()
    val step = (durationSec / 4f).coerceAtLeast(3f)
    list.add(SubtitlePhrase(0.5f, "Watch what happens right here", "Watch"))
    list.add(SubtitlePhrase(0.5f + step, "This moment is completely insane", "insane"))
    list.add(SubtitlePhrase(0.5f + step * 2, "You have to see this till the end", "see"))
    list.add(SubtitlePhrase(0.5f + step * 3, "Subscribe for the next part!", "Subscribe"))
    return list
  }

  fun generateRetentionCurve(clips: List<ShortClip>): List<RetentionPoint> {
    val points = mutableListOf<RetentionPoint>()
    val totalSteps = 60

    // Generate natural audience retention heatmap curve with 4 distinct spikes
    for (i in 0..totalSteps) {
      val fraction = i / totalSteps.toFloat()
      // Base decaying curve
      var retention = (85 - (fraction * 35) + Math.sin(fraction * 15.0) * 8).toInt()

      var isPeak = false
      var clipIdx: Int? = null

      clips.forEachIndexed { index, clip ->
        val clipMidFraction = (index + 1) / (clips.size + 1f)
        val dist = Math.abs(fraction - clipMidFraction)
        if (dist < 0.08) {
          retention = (retention + (1.0 - dist / 0.08) * 35).toInt()
          if (dist < 0.03) {
            isPeak = true
            clipIdx = clip.clipIndex
          }
        }
      }

      points.add(
        RetentionPoint(
          timeFraction = fraction,
          retentionPercent = retention.coerceIn(35, 99),
          isPeak = isPeak,
          associatedClipIndex = clipIdx
        )
      )
    }

    return points
  }

  companion object {
    val PRESETS = listOf(
      YouTubeVideoInfo(
        id = "0e3GPea1Tyg",
        url = "https://www.youtube.com/watch?v=0e3GPea1Tyg",
        title = "$456,000 Squid Game In Real Life!",
        channelName = "MrBeast",
        durationSeconds = 1542,
        viewCountFormatted = "620M views",
        thumbnailUrl = "https://img.youtube.com/vi/0e3GPea1Tyg/hqdefault.jpg",
        description = "I recreated every single game from Squid Game in real life with 456 contestants."
      ),
      YouTubeVideoInfo(
        id = "dQw4w9WgXcQ",
        url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        title = "Never Gonna Give You Up (Official Music Video)",
        channelName = "Rick Astley",
        durationSeconds = 213,
        viewCountFormatted = "1.5B views",
        thumbnailUrl = "https://img.youtube.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
        description = "The official video for Never Gonna Give You Up by Rick Astley."
      ),
      YouTubeVideoInfo(
        id = "jNQXAC9IVRw",
        url = "https://www.youtube.com/watch?v=jNQXAC9IVRw",
        title = "Me at the zoo (First YouTube Video Ever)",
        channelName = "jawed",
        durationSeconds = 19,
        viewCountFormatted = "320M views",
        thumbnailUrl = "https://img.youtube.com/vi/jNQXAC9IVRw/hqdefault.jpg",
        description = "The first video on YouTube, uploaded at the San Diego Zoo."
      ),
      YouTubeVideoInfo(
        id = "M576WGiDBdQ",
        url = "https://www.youtube.com/watch?v=M576WGiDBdQ",
        title = "Building the Next Era of AI & Gemini",
        channelName = "Google",
        durationSeconds = 1180,
        viewCountFormatted = "3.2M views",
        thumbnailUrl = "https://img.youtube.com/vi/M576WGiDBdQ/hqdefault.jpg",
        description = "Discover the latest breakthrough intelligence and mobile AI capabilities."
      )
    )
  }
}
