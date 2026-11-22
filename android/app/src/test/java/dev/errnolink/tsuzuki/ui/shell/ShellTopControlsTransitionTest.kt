package dev.errnolink.tsuzuki.ui.shell

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShellTopControlsTransitionTest {

    @Test
    fun `push or pop settle drops stale origin immediately on settle frame`() {
        // Before LaunchedEffect(moving) runs, shown != to and armed == null; without justSettled
        // the bar would render 1 frame at morph = 0 (showing the old screen's top bar).
        val state = resolveTopRowMorph(
            moving = false,
            justSettled = true,
            tabJustSettled = false,
            progress = 1f,
            tabMorph = 1f,
            hasDiff = true,
            reduced = false,
            armedMatches = false,
            swapValue = 1f,
        )
        assertFalse(state.showFrom)
        assertEquals(1f, state.morph, 0.0001f)
    }

    @Test
    fun `tab switch settle drops stale origin immediately without 1-frame flash`() {
        val state = resolveTopRowMorph(
            moving = false,
            justSettled = false,
            tabJustSettled = true,
            progress = 1f,
            tabMorph = 1f,
            hasDiff = true,
            reduced = false,
            armedMatches = false,
            swapValue = 1f,
        )
        assertFalse(state.showFrom)
        assertEquals(1f, state.morph, 0.0001f)
    }

    @Test
    fun `in-place declaration swap while settled still animates smoothly from zero to swapValue`() {
        val armedPending = resolveTopRowMorph(
            moving = false,
            justSettled = false,
            tabJustSettled = false,
            progress = 1f,
            tabMorph = 1f,
            hasDiff = true,
            reduced = false,
            armedMatches = false,
            swapValue = 1f,
        )
        assertTrue(armedPending.showFrom)
        assertEquals(0f, armedPending.morph, 0.0001f)

        val animating = resolveTopRowMorph(
            moving = false,
            justSettled = false,
            tabJustSettled = false,
            progress = 1f,
            tabMorph = 1f,
            hasDiff = true,
            reduced = false,
            armedMatches = true,
            swapValue = 0.45f,
        )
        assertTrue(animating.showFrom)
        assertEquals(0.45f, animating.morph, 0.0001f)
    }

    @Test
    fun `navigation motion drives morph directly from progress`() {
        val state = resolveTopRowMorph(
            moving = true,
            justSettled = false,
            tabJustSettled = false,
            progress = 0.62f,
            tabMorph = 1f,
            hasDiff = true,
            reduced = false,
            armedMatches = false,
            swapValue = 1f,
        )
        assertTrue(state.showFrom)
        assertEquals(0.62f, state.morph, 0.0001f)
    }
}
