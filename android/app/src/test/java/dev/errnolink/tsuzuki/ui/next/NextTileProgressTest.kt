package dev.errnolink.tsuzuki.ui.next

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NextTileProgressTest {

    @Test
    fun `a title being read shows how much is left and fills the line by chapters read`() {
        val progress = NextTileProgress.of(read = 28, total = 40, unread = 12)
        assertEquals("12 unread · 28 of 40 read", progress.caption)
        assertEquals(0.7f, progress.fraction!!, 0.001f)
    }

    @Test
    fun `a caught up title says so and shows no progress line`() {
        val progress = NextTileProgress.of(read = 85, total = 85, unread = 0)
        assertEquals("Caught up · 85 chapters", progress.caption)
        assertNull(progress.fraction)
    }

    @Test
    fun `an unstarted title shows its size without a progress line`() {
        val progress = NextTileProgress.of(read = 0, total = 1, unread = 1)
        assertEquals("1 chapter", progress.caption)
        assertNull(progress.fraction)
    }

    @Test
    fun `a title without chapters never divides by zero`() {
        val progress = NextTileProgress.of(read = 3, total = 0, unread = 0)
        assertEquals("No chapters yet", progress.caption)
        assertNull(progress.fraction)
    }
}
