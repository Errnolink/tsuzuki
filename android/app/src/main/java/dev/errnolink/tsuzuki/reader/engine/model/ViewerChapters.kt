// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): eu/kanade/tachiyomi/ui/reader/model/ViewerChapters.kt
package dev.errnolink.tsuzuki.reader.engine.model

import logcat.LogPriority
import logcat.logcat

data class ViewerChapters(
    val currChapter: ReaderChapter,
    val prevChapter: ReaderChapter?,
    val nextChapter: ReaderChapter?,
) {

    fun ref() {
        logcat(LogPriority.DEBUG) { "ref viewer chapters" }
        currChapter.ref()
        prevChapter?.ref()
        nextChapter?.ref()
    }

    fun unref() {
        logcat(LogPriority.DEBUG) { "unref viewer chapters" }
        currChapter.unref()
        prevChapter?.unref()
        nextChapter?.unref()
    }
}
