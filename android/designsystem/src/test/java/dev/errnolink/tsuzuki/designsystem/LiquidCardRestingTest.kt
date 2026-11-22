package dev.errnolink.tsuzuki.designsystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * DET-03 as a pure function: the card's resting bottom inset. One movement each way — the card
 * aims at the keyboard's published target while it rises, then follows the animated inset (so an
 * IME-only dismiss rides down smoothly instead of dropping to the nav bar in one frame), and the
 * drain freezes wherever it sits.
 */
class LiquidCardRestingTest {

    private val nav = 63
    private val imeFull = 860

    @Test
    fun `while the keyboard rises the card stays at its published target`() {
        // Live inset mid-animation (300) below the target (860): the resting place is the target,
        // so the pour lands at the final spot as one movement.
        assertEquals(imeFull, liquidRestingBottom(liveImePx = 300, imeTargetPx = imeFull, navBottomPx = nav, frozenPx = -1))
    }

    @Test
    fun `once arrived the card sits at the keyboard`() {
        assertEquals(imeFull, liquidRestingBottom(imeFull, imeFull, nav, -1))
    }

    @Test
    fun `an ime-only dismiss follows the animated inset down, never a one-frame drop`() {
        // The back swipe hides the keyboard alone: the target publishes 0 at once, the live
        // inset animates down. The resting place follows it frame by frame...
        var previous = imeFull
        listOf(860, 700, 480, 260, 90, 0).forEach { live ->
            val rest = liquidRestingBottom(live, 0, nav, -1)
            assertTrue(rest <= previous, "resting bottom rose while the IME hid (live=$live)")
            assertTrue(rest >= nav, "resting bottom fell below the nav bar (live=$live)")
            previous = rest
        }
        // ...and settles above the nav bar, not at the pre-fix instant drop.
        assertEquals(nav, liquidRestingBottom(0, 0, nav, -1))
    }

    @Test
    fun `the drain freezes wherever the card sits`() {
        assertEquals(480, liquidRestingBottom(liveImePx = 0, imeTargetPx = 0, navBottomPx = nav, frozenPx = 480))
    }

    @Test
    fun `with no keyboard the card docks above the nav bar`() {
        assertEquals(nav, liquidRestingBottom(0, 0, nav, -1))
        // A hardware keyboard can arrive with a stale target of 0 mid-frame; live wins ties.
        assertEquals(120, liquidRestingBottom(120, 0, nav, -1))
    }
}
