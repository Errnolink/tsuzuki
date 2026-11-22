package dev.errnolink.tsuzuki.designsystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Kasane's watchDirection (js/components.js): thresholds 24 down, 14 up, top always expands. */
class ScrollDirectionWatcherTest {

    private fun watcher() = ScrollDirectionWatcher(downPx = 24f, upPx = 14f)

    @Test
    fun `continuous down travel past 24 folds`() {
        val w = watcher()
        assertFalse(w.update(12f, atTop = false))
        assertFalse(w.update(10f, atTop = false))
        assertTrue(w.update(3f, atTop = false), "crossing 24 should flip")
        assertEquals(1, w.direction)
    }

    @Test
    fun `reaching exactly the threshold is not enough`() {
        val w = watcher()
        assertFalse(w.update(24f, atTop = false), "Kasane flips strictly past the threshold")
        assertTrue(w.update(1f, atTop = false))
        assertEquals(1, w.direction)
    }

    @Test
    fun `continuous up travel past 14 unfolds`() {
        val w = watcher()
        assertTrue(w.update(-15f, atTop = false))
        assertEquals(-1, w.direction)
    }

    @Test
    fun `a reversal restarts the count`() {
        val w = watcher()
        w.update(20f, atTop = false)
        // 5 up wipes the 20 down; the bar must not fold and must not need only 4 more to unfold
        assertFalse(w.update(-5f, atTop = false))
        assertEquals(0, w.direction)
        assertTrue(w.update(-14f, atTop = false), "a fresh 14 up should flip")
        assertEquals(-1, w.direction)
    }

    @Test
    fun `slow drips below the threshold never flip`() {
        val w = watcher()
        repeat(23) { assertFalse(w.update(1f, atTop = false)) }
        assertEquals(0, w.direction)
    }

    @Test
    fun `reaching the top always expands`() {
        val w = watcher()
        w.update(30f, atTop = false)
        assertEquals(1, w.direction)
        assertTrue(w.update(0f, atTop = true))
        assertEquals(-1, w.direction)
    }

    @Test
    fun `no repeat flip while the direction holds`() {
        val w = watcher()
        assertTrue(w.update(25f, atTop = false))
        assertFalse(w.update(10f, atTop = false))
        assertTrue(w.update(-15f, atTop = false))
        assertFalse(w.update(-5f, atTop = false))
    }

    @Test
    fun `reset clears direction and travel`() {
        val w = watcher()
        w.update(30f, atTop = false)
        w.reset()
        assertEquals(0, w.direction)
        assertFalse(w.update(10f, atTop = false))
    }
}
