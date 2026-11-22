package dev.errnolink.tsuzuki.designsystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Kasane swipe-row behaviour: resistance, the full-swipe threshold, fling commits and releases. */
class SwipeRowSpecTest {
    private val actionsWidth = 160f
    private val rowWidth = 400f

    @Test
    fun `rows follow the finger one to one until the actions run out`() {
        assertEquals(-80f, SwipeRowSpec.resisted(-80f, actionsWidth, hasFull = true))
        assertEquals(-160f, SwipeRowSpec.resisted(-160f, actionsWidth, hasFull = true))
        assertEquals(0f, SwipeRowSpec.resisted(30f, actionsWidth, hasFull = true)) // never reveals rightward
    }

    @Test
    fun `past the actions the row resists, gently with a full action and hard without`() {
        val resisted = SwipeRowSpec.resisted(-240f, actionsWidth, hasFull = true)
        assertEquals(-160f - 80f * SwipeRowSpec.RESIST_FULL, resisted)
        val plain = SwipeRowSpec.resisted(-240f, actionsWidth, hasFull = false)
        assertEquals(-160f - 80f * SwipeRowSpec.RESIST_PLAIN, plain)
        assertTrue(resisted < plain) // with a full action the row keeps travelling
    }

    @Test
    fun `the threshold haptic arms at fifty eight percent of the row`() {
        assertFalse(SwipeRowSpec.armed(-231f, rowWidth)) // 57.75 %
        assertTrue(SwipeRowSpec.armed(-232f, rowWidth)) // 58 %
        assertFalse(SwipeRowSpec.armed(-300f, 0f)) // unmeasured rows never arm
    }

    @Test
    fun `a full swipe past the threshold commits the full action`() {
        val x = SwipeRowSpec.resisted(-400f, actionsWidth, hasFull = true)
        assertTrue(SwipeRowSpec.commits(x, 0f, actionsWidth, rowWidth, hasFull = true))
        assertFalse(SwipeRowSpec.commits(x, 0f, actionsWidth, rowWidth, hasFull = false))
    }

    @Test
    fun `a strong fling commits when the reveal is already most of the way`() {
        val x = -100f // past 60 % of the 160 px actions
        assertTrue(SwipeRowSpec.commits(x, -0.9f, actionsWidth, rowWidth, hasFull = true))
        // a fling from a shallow reveal stays a reveal
        assertFalse(SwipeRowSpec.commits(-40f, -0.9f, actionsWidth, rowWidth, hasFull = true))
        // the fling must pull in the leaving direction
        assertFalse(SwipeRowSpec.commits(x, 0.9f, actionsWidth, rowWidth, hasFull = true))
    }

    @Test
    fun `releases open past half the actions and flings decide otherwise`() {
        assertTrue(SwipeRowSpec.openOnRelease(-90f, 0f, actionsWidth))
        assertFalse(SwipeRowSpec.openOnRelease(-70f, 0f, actionsWidth))
        assertTrue(SwipeRowSpec.openOnRelease(-40f, -0.6f, actionsWidth)) // fling open
        assertFalse(SwipeRowSpec.openOnRelease(-100f, 0.6f, actionsWidth)) // fling closed
    }
}
