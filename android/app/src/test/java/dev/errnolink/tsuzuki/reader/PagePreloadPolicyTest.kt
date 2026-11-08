package dev.errnolink.tsuzuki.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PagePreloadPolicyTest {

    private fun fetchedWhileReading(pageCount: Int, previewPages: Int?, viewed: IntRange): Set<Int> =
        viewed.filter { PagePreloadPolicy.mayLoad(it, previewPages) }
            .flatMap { listOf(it) + PagePreloadPolicy.lookahead(it, PagePreloadPolicy.LOOKAHEAD, pageCount, previewPages) }
            .toSet()

    @Test
    fun `reading a page fetches only the next three`() {
        assertEquals(6..8, PagePreloadPolicy.lookahead(5, PagePreloadPolicy.LOOKAHEAD, 30, null))
    }

    @Test
    fun `lookahead stops at the end of the chapter`() {
        assertEquals(29..29, PagePreloadPolicy.lookahead(28, PagePreloadPolicy.LOOKAHEAD, 30, null))
        assertTrue(PagePreloadPolicy.lookahead(29, PagePreloadPolicy.LOOKAHEAD, 30, null).isEmpty())
    }

    @Test
    fun `a preloaded next chapter fetches at most its first two pages even when several are on screen`() {
        val fetched = fetchedWhileReading(pageCount = 40, previewPages = PagePreloadPolicy.NEXT_CHAPTER_PAGES, viewed = 0..5)
        assertEquals(setOf(0, 1), fetched)
    }

    @Test
    fun `once the reader reaches the next chapter it loads normally`() {
        val fetched = fetchedWhileReading(pageCount = 40, previewPages = null, viewed = 0..0)
        assertEquals(setOf(0, 1, 2, 3), fetched)
        assertFalse(PagePreloadPolicy.mayLoad(2, PagePreloadPolicy.NEXT_CHAPTER_PAGES))
        assertTrue(PagePreloadPolicy.mayLoad(2, null))
    }
}
