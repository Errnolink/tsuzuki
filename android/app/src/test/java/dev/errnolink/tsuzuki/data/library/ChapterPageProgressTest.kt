package dev.errnolink.tsuzuki.data.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ChapterPageProgressTest {

    @Test
    fun `read chapters are fully progressed even when the total is unknown`() {
        assertEquals(1f, chapterPageProgress(lastPageRead = 5, totalPages = null, read = true))
        assertEquals(1f, chapterPageProgress(lastPageRead = 0, totalPages = 24, read = true))
    }

    @Test
    fun `unknown or non positive totals draw no ring`() {
        assertNull(chapterPageProgress(lastPageRead = 2, totalPages = null, read = false))
        assertNull(chapterPageProgress(lastPageRead = 2, totalPages = 0, read = false))
        assertNull(chapterPageProgress(lastPageRead = 2, totalPages = -3, read = false))
    }

    @Test
    fun `known unstarted chapters draw the empty track`() {
        assertEquals(0f, chapterPageProgress(lastPageRead = 0, totalPages = 24, read = false))
    }

    @Test
    fun `page index two of twenty four is three twenty fourths`() {
        assertEquals(3f / 24f, chapterPageProgress(lastPageRead = 2, totalPages = 24, read = false))
    }

    @Test
    fun `page indexes beyond the total clamp to full`() {
        assertEquals(1f, chapterPageProgress(lastPageRead = 30, totalPages = 24, read = false))
    }
}
