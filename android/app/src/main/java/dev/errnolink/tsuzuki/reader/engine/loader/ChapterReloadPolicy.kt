// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): eu/kanade/tachiyomi/ui/reader/loader/ChapterLoader.kt (Neko #3523)
package dev.errnolink.tsuzuki.reader.engine.loader

import dev.errnolink.tsuzuki.reader.engine.model.ReaderChapter

/**
 * Decides whether a chapter the reader is about to (re)load has been downloaded since it was loaded
 * over HTTP, so its state can reset and the reload picks the download loader.
 *
 * Asks the download cache first, then confirms on disk: a cache entry whose file is gone must not
 * count, or every preload resets the chapter while each reload picks the HTTP loader again —
 * an endless next-chapter reload loop.
 */
class ChapterReloadPolicy(
    private val isDownloadedWithCache: (ReaderChapter) -> Boolean,
    private val isDownloadedOnDisk: (ReaderChapter) -> Boolean,
) {

    /**
     * True when [chapter] was loaded over HTTP ([isHttpLoaded]) and has been downloaded since. The
     * disk check runs only after the cache says downloaded.
     */
    fun isDownloadedSinceHttpLoad(chapter: ReaderChapter, isHttpLoaded: Boolean): Boolean {
        if (!isHttpLoaded) {
            return false
        }
        return isDownloadedWithCache(chapter) && isDownloadedOnDisk(chapter)
    }
}
