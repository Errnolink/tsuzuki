// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): eu/kanade/tachiyomi/ui/reader/viewer/MissingChapters.kt
package dev.errnolink.tsuzuki.reader.engine.viewer

import dev.errnolink.tsuzuki.reader.engine.model.ReaderChapter
import kotlin.math.floor
import tachiyomi.domain.chapter.model.Chapter

private val pattern = Regex("""\d+""")

fun hasMissingChapters(
    higherReaderChapter: ReaderChapter?,
    lowerReaderChapter: ReaderChapter?,
): Boolean {
    if (higherReaderChapter == null || lowerReaderChapter == null) return false
    return hasMissingChapters(higherReaderChapter.chapter, lowerReaderChapter.chapter)
}

fun hasMissingChapters(higherChapter: Chapter?, lowerChapter: Chapter?): Boolean {
    if (higherChapter == null || lowerChapter == null) return false
    // Check if name contains a number that is potential chapter number
    if (!pattern.containsMatchIn(higherChapter.name) || !pattern.containsMatchIn(lowerChapter.name))
        return false
    // Check if potential chapter number was recognized as chapter number
    if (!higherChapter.isRecognizedNumber || !lowerChapter.isRecognizedNumber) return false
    return hasMissingChapters(higherChapter.chapterNumber, lowerChapter.chapterNumber)
}

fun hasMissingChapters(higherChapterNumber: Double, lowerChapterNumber: Double): Boolean {
    if (higherChapterNumber < 0.0 || lowerChapterNumber < 0.0) return false
    return calculateChapterDifference(higherChapterNumber, lowerChapterNumber) > 0f
}

fun calculateChapterDifference(
    higherReaderChapter: ReaderChapter?,
    lowerReaderChapter: ReaderChapter?,
): Float {
    if (higherReaderChapter == null || lowerReaderChapter == null) return 0f
    return calculateChapterDifference(higherReaderChapter.chapter, lowerReaderChapter.chapter)
}

fun calculateChapterDifference(higherChapter: Chapter?, lowerChapter: Chapter?): Float {
    if (higherChapter == null || lowerChapter == null) return 0f
    // Check if name contains a number that is potential chapter number
    if (!pattern.containsMatchIn(higherChapter.name) || !pattern.containsMatchIn(lowerChapter.name))
        return 0f
    // Check if potential chapter number was recognized as chapter number
    if (!higherChapter.isRecognizedNumber || !lowerChapter.isRecognizedNumber) return 0f
    return calculateChapterDifference(higherChapter.chapterNumber, lowerChapter.chapterNumber)
}

fun calculateChapterDifference(higherChapterNumber: Double, lowerChapterNumber: Double): Float {
    if (higherChapterNumber < 0.0 || lowerChapterNumber < 0.0) return 0f
    return (floor(higherChapterNumber) - floor(lowerChapterNumber) - 1.0).toFloat()
}
