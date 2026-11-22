// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): eu/kanade/tachiyomi/ui/reader/domain/ResolveChapterNavTargetUseCase.kt
package dev.errnolink.tsuzuki.reader.engine.domain

import dev.errnolink.tsuzuki.reader.engine.model.ChapterNavTarget
import dev.errnolink.tsuzuki.reader.engine.model.ReaderChapter

/**
 * Resolves the appropriate navigation target when moving between chapters in reading order. Ensures
 * forward continuous transitions land at the start (page 0), backward transitions land at the end,
 * and initial loads or explicit jumps restore reading progress.
 */
class ResolveChapterNavTargetUseCase {

    operator fun invoke(
        currentChapter: ReaderChapter?,
        selectedChapter: ReaderChapter,
        chapterList: List<ReaderChapter>,
        nextChapter: ReaderChapter? = null,
        prevChapter: ReaderChapter? = null,
    ): ChapterNavTarget {
        if (currentChapter == null || currentChapter.chapter.id == selectedChapter.chapter.id) {
            return ChapterNavTarget.Resume
        }

        val isForward =
            when {
                nextChapter != null && selectedChapter.chapter.id == nextChapter.chapter.id -> true
                prevChapter != null && selectedChapter.chapter.id == prevChapter.chapter.id -> false
                else -> {
                    val currentIndex = chapterList.indexOfFirst {
                        it.chapter.id == currentChapter.chapter.id
                    }
                    val newIndex = chapterList.indexOfFirst {
                        it.chapter.id == selectedChapter.chapter.id
                    }
                    if (currentIndex != -1 && newIndex != -1) {
                        newIndex > currentIndex
                    } else {
                        selectedChapter.chapter.chapterNumber >
                            currentChapter.chapter.chapterNumber
                    }
                }
            }

        return if (isForward) {
            ChapterNavTarget.Start
        } else {
            ChapterNavTarget.End
        }
    }
}
