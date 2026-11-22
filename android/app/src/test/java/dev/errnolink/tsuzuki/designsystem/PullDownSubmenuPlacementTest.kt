package dev.errnolink.tsuzuki.designsystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PullDownSubmenuPlacementTest {

    private val menuHeight = 600f
    private val inset = 12f

    @Test
    fun `the panel unrolls from its own row when it fits`() {
        val frame = inlineSubmenuFrame(rowTop = 100f, naturalHeight = 300f, menuHeight = menuHeight, insetPx = inset)
        assertEquals(100f, frame.top)
        assertEquals(300f, frame.height)
    }

    @Test
    fun `a panel that would run past the menu slides up just enough`() {
        val frame = inlineSubmenuFrame(rowTop = 400f, naturalHeight = 300f, menuHeight = menuHeight, insetPx = inset)
        assertEquals(menuHeight - inset, frame.top + frame.height)
        assertEquals(300f, frame.height)
    }

    @Test
    fun `a panel taller than the menu is capped inside it`() {
        val frame = inlineSubmenuFrame(rowTop = 200f, naturalHeight = 4000f, menuHeight = menuHeight, insetPx = inset)
        assertEquals(inset, frame.top)
        assertEquals(menuHeight - 2f * inset, frame.height)
    }
}
