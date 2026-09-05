package com.example.data.intelligence

import com.example.data.media.TimedSubtitle
import com.example.model.ShortClip
import com.example.model.YouTubeVideoInfo

/**
 * Boundary for clip-selection providers. Implementations must only use supplied source data.
 * Network/LLM providers can be added later without changing the editor UI.
 */
interface ClipIntelligenceProvider {
  suspend fun findCandidates(
    videoInfo: YouTubeVideoInfo,
    transcript: List<TimedSubtitle>
  ): List<ShortClip>
}
