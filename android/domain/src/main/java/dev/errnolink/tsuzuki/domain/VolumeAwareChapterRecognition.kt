package dev.errnolink.tsuzuki.domain

import tachiyomi.domain.chapter.model.Chapter

/**
 * Provides volume-only fallback parsing when no chapter number exists and fills single
 * unrecognized gaps between consecutive numbered neighbouring chapters.
 */
object VolumeAwareChapterRecognition {

    private val volumeOnlyRegex = Regex("""\b(?:vol(?:ume)?|v)\.?\s*([0-9]+(?:\.[0-9]+)?)\b""")
    private val chapterPrefixRegex = Regex("""\b(?:ch(?:apter)?|ep(?:isode)?)\.?\s*[0-9]+""")

    fun parseVolumeFallback(mangaTitle: String, chapterName: String): Double? {
        val cleaned = chapterName.lowercase().replace(mangaTitle.lowercase(), "").trim()
        if (chapterPrefixRegex.containsMatchIn(cleaned)) return null
        val match = volumeOnlyRegex.find(cleaned) ?: return null
        return match.groupValues[1].toDoubleOrNull()
    }

    fun resolveWithNeighbours(chapters: List<Chapter>): List<Chapter> {
        if (chapters.size < 3) return chapters
        return chapters.mapIndexed { index, chapter ->
            if (chapter.chapterNumber != -1.0 || index == 0 || index == chapters.lastIndex) {
                chapter
            } else {
                val prev = chapters[index - 1].chapterNumber
                val next = chapters[index + 1].chapterNumber
                val interpolated = when {
                    prev >= 0.0 && next >= 0.0 && next - prev == 2.0 -> prev + 1.0
                    prev >= 0.0 && next >= 0.0 && prev - next == 2.0 -> next + 1.0
                    else -> null
                }
                if (interpolated != null) chapter.copy(chapterNumber = interpolated) else chapter
            }
        }
    }
}
