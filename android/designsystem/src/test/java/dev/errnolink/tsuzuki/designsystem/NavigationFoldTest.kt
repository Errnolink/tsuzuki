package dev.errnolink.tsuzuki.designsystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The boundary between Compose's nested-scroll convention and Kasane's fold watcher
 * (NAV-03): Compose reports a finger moving up (reading down the list) as a NEGATIVE
 * consumed.y; the watcher wants positive = travel down the content.
 */
class NavigationFoldTest {
    @Test
    fun `reading down the list is positive travel for the watcher`() {
        assertEquals(30f, scrollTravelDownPx(-30f))
        assertEquals(-30f, scrollTravelDownPx(30f), 0f)
        assertEquals(0f, scrollTravelDownPx(0f), 0f)
    }

    @Test
    fun `a watcher fed compose deltas folds on reading down and unfolds on scrolling up`() {
        val w = ScrollDirectionWatcher(downPx = 24f, upPx = 14f)
        // A finger travelling up 30 px (Compose consumed.y = -30) folds the bar.
        assertTrue(w.update(scrollTravelDownPx(-30f), atTop = false))
        assertEquals(1, w.direction)
        // A finger travelling down 20 px (Compose consumed.y = +20) unfolds it.
        assertTrue(w.update(scrollTravelDownPx(20f), atTop = false))
        assertEquals(-1, w.direction)
    }

    @Test
    fun `at top comes from the unconsumed remainder so a fling counts too`() {
        // Leftover finger-down travel means the list cannot scroll further toward its start.
        assertTrue(listAtTop(availableY = 12f))
        assertFalse(listAtTop(availableY = 0f))
        // Reading down always leaves a non-positive remainder.
        assertFalse(listAtTop(availableY = -8f))
    }

    @Test
    fun `a flip that arrives while the lock holds is applied on a later frame`() {
        // Frame 1: the watcher flips to fold, but the morph lock is still armed.
        val blocked = navigationFoldFrame(
            flipped = true,
            watcherDirection = 1,
            pendingDirection = 0,
            lockExpired = false,
        )
        assertEquals(NavigationFoldDecision(applyDirection = 0, pendingDirection = 1), blocked)
        // Frame 2: no new flip; the lock has expired and the direction held — apply it now.
        val retried = navigationFoldFrame(
            flipped = false,
            watcherDirection = 1,
            pendingDirection = 1,
            lockExpired = true,
        )
        assertEquals(NavigationFoldDecision(applyDirection = 1, pendingDirection = 0), retried)
    }

    @Test
    fun `a pending flip whose direction reversed is dropped`() {
        val stale = navigationFoldFrame(
            flipped = false,
            watcherDirection = -1,
            pendingDirection = 1,
            lockExpired = true,
        )
        assertEquals(NavigationFoldDecision(applyDirection = 0, pendingDirection = 0), stale)
    }

    @Test
    fun `a fresh flip with a free lock applies at once and clears the pending`() {
        val applied = navigationFoldFrame(
            flipped = true,
            watcherDirection = -1,
            pendingDirection = 1,
            lockExpired = true,
        )
        assertEquals(NavigationFoldDecision(applyDirection = -1, pendingDirection = 0), applied)
    }

    @Test
    fun `an idle frame with nothing pending decides nothing`() {
        assertNull(
            navigationFoldFrame(
                flipped = false,
                watcherDirection = 1,
                pendingDirection = 0,
                lockExpired = true,
            ),
        )
        // A pending flip while the lock still holds keeps waiting.
        assertNull(
            navigationFoldFrame(
                flipped = false,
                watcherDirection = 1,
                pendingDirection = 1,
                lockExpired = false,
            ),
        )
    }
}
