package com.example.data.media

/** A subtitle entry with absolute source-video timing. */
data class TimedSubtitle(
  val startMs: Long,
  val endMs: Long,
  val text: String
)

/** Parses common SRT and WebVTT subtitle files without inventing timing or text. */
object TranscriptParser {
  private val srtTime = Regex("(\\d{2}):(\\d{2}):(\\d{2})[,.](\\d{3})")
  private val vttTime = Regex("(?:(\\d{2}):)?(\\d{2}):(\\d{2})[.](\\d{3})")

  fun parse(content: String): List<TimedSubtitle> {
    val normalized = content.removePrefix("\\uFEFF").replace("\\r\\n", "\\n").replace("\\r", "\\n")
    if (normalized.isBlank()) return emptyList()
    val blocks = normalized.split(Regex("\\n\\s*\\n"))
    return blocks.mapNotNull { parseBlock(it) }
      .filter { it.endMs > it.startMs && it.text.isNotBlank() }
      .sortedBy { it.startMs }
  }

  private fun parseBlock(block: String): TimedSubtitle? {
    val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.isEmpty() || lines.first().equals("WEBVTT", true) || lines.first().startsWith("NOTE", true)) return null
    val timingIndex = lines.indexOfFirst { it.contains(" --> ") }
    if (timingIndex < 0 || timingIndex + 1 >= lines.size) return null
    val timing = lines[timingIndex].substringBefore(" --> ") to lines[timingIndex].substringAfter(" --> ").substringBefore(" ")
    val start = parseTime(timing.first) ?: return null
    val end = parseTime(timing.second) ?: return null
    val text = lines.drop(timingIndex + 1).joinToString(" ")
      .replace(Regex("<[^>]+>"), "")
      .trim()
    return TimedSubtitle(start, end, text)
  }

  private fun parseTime(value: String): Long? {
    srtTime.matchEntire(value)?.let { m ->
      return m.groupValues[1].toLong() * 3_600_000L +
        m.groupValues[2].toLong() * 60_000L +
        m.groupValues[3].toLong() * 1_000L +
        m.groupValues[4].toLong()
    }
    vttTime.matchEntire(value)?.let { m ->
      val hours = m.groupValues[1].takeIf { it.isNotEmpty() }?.toLong() ?: 0L
      return hours * 3_600_000L +
        m.groupValues[2].toLong() * 60_000L +
        m.groupValues[3].toLong() * 1_000L +
        m.groupValues[4].toLong()
    }
    return null
  }
}
