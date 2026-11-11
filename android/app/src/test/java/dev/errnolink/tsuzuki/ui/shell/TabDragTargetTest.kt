package dev.errnolink.tsuzuki.ui.shell

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TabDragTargetTest {

    private val tabStepPx = 200f

    @Test
    fun `a small drag from tab 2 keeps tab 2 selected`() {
        assertEquals(
            2,
            tabDragTarget(selectedIndex = 2, dragTotalPx = 0.2f * tabStepPx, tabStepPx = tabStepPx, releaseVelocityPx = 0f, tabCount = 5),
        )
    }

    @Test
    fun `a majority drag from tab 2 selects tab 3`() {
        assertEquals(
            3,
            tabDragTarget(selectedIndex = 2, dragTotalPx = 0.6f * tabStepPx, tabStepPx = tabStepPx, releaseVelocityPx = 0f, tabCount = 5),
        )
    }

    @Test
    fun `a small drag with a strong fling selects tab 3`() {
        assertEquals(
            3,
            tabDragTarget(selectedIndex = 2, dragTotalPx = 0.3f * tabStepPx, tabStepPx = tabStepPx, releaseVelocityPx = 1800f, tabCount = 5),
        )
    }

    @Test
    fun `a drag far past the last tab clamps to the last tab`() {
        assertEquals(
            4,
            tabDragTarget(selectedIndex = 2, dragTotalPx = 10f * tabStepPx, tabStepPx = tabStepPx, releaseVelocityPx = 0f, tabCount = 5),
        )
    }

    @Test
    fun `a zero step size keeps the selected index`() {
        assertEquals(
            2,
            tabDragTarget(selectedIndex = 2, dragTotalPx = 3f * tabStepPx, tabStepPx = 0f, releaseVelocityPx = 5000f, tabCount = 5),
        )
    }
}
