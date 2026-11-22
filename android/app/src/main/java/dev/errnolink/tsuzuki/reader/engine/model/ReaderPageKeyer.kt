// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): eu/kanade/tachiyomi/data/coil/ReaderPageFetcher.kt (ReaderPageKeyer, ReaderPageSplitKeyer)
package dev.errnolink.tsuzuki.reader.engine.model

import coil3.key.Keyer
import coil3.request.Options

class ReaderPageKeyer : Keyer<ReaderPage> {
    override fun key(data: ReaderPage, options: Options): String {
        val chapterKey =
            data.chapter.chapter.id.takeIf { it > 0 } ?: data.chapter.chapter.url.hashCode()
        return "reader_page_${chapterKey}_${data.index}"
    }
}

class ReaderPageSplitKeyer : Keyer<ReaderPageSplit> {
    override fun key(data: ReaderPageSplit, options: Options): String {
        val chapterKey =
            data.page.chapter.chapter.id.takeIf { it > 0 } ?: data.page.chapter.chapter.url.hashCode()
        return "reader_split_${chapterKey}_${data.page.index}_${data.topOffset}"
    }
}
