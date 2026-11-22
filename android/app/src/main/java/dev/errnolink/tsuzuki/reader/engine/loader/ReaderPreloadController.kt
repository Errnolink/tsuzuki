// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): eu/kanade/tachiyomi/ui/reader/loader/ReaderPreloadController.kt
package dev.errnolink.tsuzuki.reader.engine.loader

import dev.errnolink.tsuzuki.reader.engine.model.ReaderChapter
import dev.errnolink.tsuzuki.reader.engine.model.ReaderPage
import dev.errnolink.tsuzuki.reader.engine.model.ReaderPageSplit
import dev.errnolink.tsuzuki.reader.engine.model.ReaderUiItem
import kotlinx.coroutines.flow.StateFlow

/**
 * Headless preload controller managing two-tier execution pipeline (network -> disk cache -> memory
 * RAM), prioritized task reordering, tall-page pre-splitting, and lifecycle bounds outside Compose.
 */
interface ReaderPreloadController {
    val state: StateFlow<ReaderPreloadState>

    var onPageSplit: ((ReaderPage, List<ReaderPageSplit>) -> Unit)?

    /**
     * Updates the current viewport position, recalculating priorities for both disk download and
     * memory decode pipelines.
     */
    fun onPositionChanged(
        currentIndex: Int,
        items: List<ReaderUiItem>,
        preloadAmount: Int,
        isRtl: Boolean,
        isWebtoon: Boolean,
    )

    /** Preloads adjacent chapters when approaching chapter boundaries. */
    fun requestPreloadChapter(chapter: ReaderChapter)

    /** Manually triggers a retry for a page that failed to preload. */
    fun retryPage(item: ReaderUiItem)

    /** Clears all in-flight jobs and cached memory disposables. */
    fun release()
}
