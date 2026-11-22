package dev.errnolink.tsuzuki.ui.shell

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * N15: the collapsed state is derived — only sustained down-scroll keeps it; any fresh context
 * (an open keyboard search here; tab change, sheet close, screen return and scroll top are the
 * shell's force-expands) reads as expanded.
 */
class TabBarCollapseDerivationTest {

    @Test
    fun `only sustained down-scroll keeps the bar collapsed`() {
        assertTrue(shellTabBarCollapsed(scrollFolded = true, searchActive = false))
    }

    @Test
    fun `an open search expands the bar so its close returns labels from the first frame`() {
        assertFalse(shellTabBarCollapsed(scrollFolded = true, searchActive = true))
    }

    @Test
    fun `an unfolded bar stays unfolded`() {
        assertFalse(shellTabBarCollapsed(scrollFolded = false, searchActive = false))
    }
}

/** N17: the travelling pill dims a label it crosses instead of slicing through the word. */
class TabLabelUnderPillTest {

    @Test
    fun `labels are untouched at rest`() {
        assertEquals(1f, tabLabelAlphaUnderPill(pillPosition = 1f, tabIndex = 0, selectedIndex = 1), 0.001f)
        assertEquals(1f, tabLabelAlphaUnderPill(pillPosition = 2f, tabIndex = 0, selectedIndex = 2), 0.001f)
    }

    @Test
    fun `a crossed label dims and recovers continuously`() {
        val far = tabLabelAlphaUnderPill(pillPosition = 0f, tabIndex = 1, selectedIndex = 0)
        val mid = tabLabelAlphaUnderPill(pillPosition = 0.5f, tabIndex = 1, selectedIndex = 0)
        val on = tabLabelAlphaUnderPill(pillPosition = 1f, tabIndex = 1, selectedIndex = 0)
        assertEquals(1f, far, 0.001f)
        assertTrue(mid < far && mid > on)
        assertEquals(0.55f, on, 0.001f)
    }

    @Test
    fun `the selected tab rides on the pill and never dims`() {
        assertEquals(1f, tabLabelAlphaUnderPill(pillPosition = 0.4f, tabIndex = 1, selectedIndex = 1), 0.001f)
    }
}
