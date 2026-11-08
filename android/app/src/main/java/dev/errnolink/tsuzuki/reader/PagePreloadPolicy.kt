package dev.errnolink.tsuzuki.reader

object PagePreloadPolicy {
    const val LOOKAHEAD = 3
    const val NEXT_CHAPTER_PAGES = 2

    fun lookahead(pageIndex: Int, lookahead: Int, pageCount: Int, previewPages: Int?): IntRange {
        val last = minOf(pageIndex + lookahead, pageCount - 1, (previewPages ?: pageCount) - 1)
        return (pageIndex + 1)..last
    }

    fun mayLoad(pageIndex: Int, previewPages: Int?): Boolean = previewPages == null || pageIndex < previewPages
}
