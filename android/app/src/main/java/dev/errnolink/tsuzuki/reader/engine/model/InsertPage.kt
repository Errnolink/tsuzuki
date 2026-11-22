// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): eu/kanade/tachiyomi/ui/reader/model/InsertPage.kt
package dev.errnolink.tsuzuki.reader.engine.model

class InsertPage(parent: ReaderPage) : ReaderPage(parent.index, parent.url, parent.imageUrl) {

    override var chapter: ReaderChapter = parent.chapter

    init {
        fullPage = true
        firstHalf = false
        stream = parent.stream
        status = State.Ready
    }
}
