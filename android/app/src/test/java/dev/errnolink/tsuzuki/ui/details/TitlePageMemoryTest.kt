package dev.errnolink.tsuzuki.ui.details

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * N12: the last values a title page resolved make the next open instant — recall returns exactly
 * what was remembered, a refresh replaces (not duplicates), and the map forgets the oldest titles
 * instead of growing. Pure.
 */
class TitlePageMemoryTest {

    @Test
    fun `remembered values come back on recall`() {
        TitlePageMemory.remember(7L, 31, "Start reading", true)
        val known = TitlePageMemory.recall(7L)!!
        assertEquals(31, known.chapterCount)
        assertEquals("Start reading", known.continueLabel)
        assertEquals(true, known.continueEnabled)
    }

    @Test
    fun `unknown titles recall nothing`() {
        assertNull(TitlePageMemory.recall(Long.MIN_VALUE))
    }

    @Test
    fun `a refresh replaces the previous values`() {
        TitlePageMemory.remember(1L, 10, "Continue · Ch. 2", true)
        TitlePageMemory.remember(1L, 11, "Continue · Ch. 3", true)
        assertEquals(11, TitlePageMemory.recall(1L)!!.chapterCount)
        assertEquals("Continue · Ch. 3", TitlePageMemory.recall(1L)!!.continueLabel)
    }

    @Test
    fun `the oldest titles are forgotten past capacity`() {
        for (id in 0L until 128L) TitlePageMemory.remember(id, id.toInt(), null, false)
        TitlePageMemory.remember(128L, 128, "Start reading", true)
        assertNull(TitlePageMemory.recall(0L))
        assertEquals(128, TitlePageMemory.recall(128L)!!.chapterCount)
    }
}
