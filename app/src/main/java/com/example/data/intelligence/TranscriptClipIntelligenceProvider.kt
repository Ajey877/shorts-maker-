package com.example.data.intelligence

import com.example.data.media.TimedSubtitle
import com.example.model.ShortClip
import com.example.model.YouTubeVideoInfo
import kotlin.math.roundToInt

/**
 * Offline transcript-based selector. This is not an AI model and never claims audience data.
 * It scores windows using only the language present in the supplied transcript.
 */
class TranscriptClipIntelligenceProvider : ClipIntelligenceProvider {
  override suspend fun findCandidates(
    videoInfo: YouTubeVideoInfo,
    transcript: List<TimedSubtitle>
  ): List<ShortClip> {
    if (videoInfo.durationSeconds < 10 || transcript.isEmpty()) return emptyList()

    val usable = transcript
      .filter { it.endMs > it.startMs && it.startMs >= 0L }
      .sortedBy { it.startMs }
    if (usable.isEmpty()) return emptyList()

    val maxEndMs = videoInfo.durationSeconds * 1000L
    val candidates = mutableListOf<Candidate>()
    usable.forEachIndexed { index, cue ->
      val start = cue.startMs.coerceIn(0L, maxEndMs)
      val targetEnd = (start + 30_000L).coerceAtMost(maxEndMs)
      val minEnd = (start + 10_000L).coerceAtMost(maxEndMs)
      if (targetEnd - start < 10_000L) return@forEachIndexed

      val window = usable.drop(index).takeWhile { it.startMs < targetEnd && it.endMs > start }
      val text = window.joinToString(" ") { it.text }.trim()
      if (text.isBlank()) return@forEachIndexed

      val score = scoreText(text, window.size)
      candidates += Candidate(start, targetEnd, score, text)
      if (minEnd > start && targetEnd - start > 10_000L) {
        // Keep the candidate at the natural transcript start; do not invent a second timestamp.
      }
    }

    val selected = candidates
      .sortedWith(compareByDescending<Candidate> { it.score }.thenBy { it.startMs })
      .fold(mutableListOf<Candidate>()) { acc, candidate ->
        if (acc.none { overlaps(it, candidate) }) acc += candidate
        if (acc.size == 4) return@fold acc
        acc
      }
      .sortedBy { it.startMs }

    return selected.mapIndexed { index, candidate ->
      val startSec = (candidate.startMs / 1000L).toInt()
      val endSec = (candidate.endMs / 1000L).toInt().coerceAtMost(videoInfo.durationSeconds)
      ShortClip(
        videoId = videoInfo.id,
        clipIndex = index + 1,
        title = "Transcript Candidate ${index + 1}",
        hookHeadline = "",
        startSeconds = startSec,
        endSeconds = endSec,
        viralityScore = candidate.score,
        whyViralReason = "Offline transcript score based on supplied words and cue density; not measured audience retention.",
        keyTakeaway = candidate.text.take(180),
        suggestedHashtags = listOf("#Shorts"),
        youtubeShortsDescription = "Transcript-based editing candidate created locally in ClipMint.",
        sampleSubtitles = emptyList()
      )
    }
  }

  private fun scoreText(text: String, cueCount: Int): Int {
    val words = text.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.isEmpty()) return 0
    val signalWords = setOf(
      "why", "how", "secret", "mistake", "never", "always", "best", "worst",
      "truth", "problem", "solution", "because", "actually", "important", "surprising",
      "first", "second", "finally", "but", "however", "learned", "lesson"
    )
    val questions = text.count { it == '?' }
    val exclamations = text.count { it == '!' }
    val signals = words.count { it in signalWords }
    val density = (cueCount * 3).coerceAtMost(24)
    return (35 + signals * 4 + questions * 5 + exclamations * 3 + density).coerceIn(1, 99)
  }

  private fun overlaps(a: Candidate, b: Candidate): Boolean {
    return a.startMs < b.endMs && b.startMs < a.endMs
  }

  private data class Candidate(
    val startMs: Long,
    val endMs: Long,
    val score: Int,
    val text: String
  )
}
