// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): eu/kanade/tachiyomi/ui/reader/viewer/webtoon/WebtoonScrollGatingPolicy.kt
package dev.errnolink.tsuzuki.reader.engine.viewer.webtoon

/**
 * Policy determining whether a page selection event across chapters should be dispatched. Strictly
 * gates adjacent chapter transitions on active user scrolling to prevent passive preload prepend
 * race conditions (Issue #3379).
 */
object WebtoonScrollGatingPolicy {

    fun shouldDispatchPageSelection(
        activeChapterId: Long?,
        candidateChapterId: Long?,
        isScrollInProgress: Boolean,
    ): Boolean {
        val isAdjacentChapter = activeChapterId != null && candidateChapterId != activeChapterId
        return !isAdjacentChapter || isScrollInProgress
    }
}
