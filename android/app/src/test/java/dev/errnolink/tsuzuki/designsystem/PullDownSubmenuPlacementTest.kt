package dev.errnolink.tsuzuki.designsystem

import androidx.compose.ui.unit.IntRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PullDownSubmenuPlacementTest {

    private val window = IntRect(left = 0, top = 0, right = 1080, bottom = 2400)

    private fun place(rowTop: Int, naturalHeight: Int, parentWidth: Int = 300, windowBounds: IntRect = window) =
        computeSubmenuPlacement(
            rowBounds = IntRect(left = 0, top = rowTop, right = parentWidth, bottom = rowTop + 48),
            parentWidthPx = parentWidth,
            naturalHeightPx = naturalHeight,
            windowBounds = windowBounds,
            liftPx = 12,
            marginPx = 16,
        )

    @Test
    fun `submenu opens in place at exactly the parent width`() {
        val placement = place(rowTop = 600, naturalHeight = 200)
        assertEquals(300, placement.width)
        assertEquals(0, placement.x)
    }

    @Test
    fun `submenu never exceeds a window narrower than its natural content`() {
        val narrow = IntRect(left = 0, top = 0, right = 320, bottom = 2400)
        val placement = place(rowTop = 600, naturalHeight = 200, parentWidth = 300, windowBounds = narrow)
        assertTrue(placement.x + placement.width <= narrow.right)
    }

    @Test
    fun `card header lands slightly above the tapped row`() {
        assertEquals(588, place(rowTop = 600, naturalHeight = 200).y)
    }

    @Test
    fun `card shifts up when it would overflow the bottom of the window`() {
        val placement = place(rowTop = 1900, naturalHeight = 600)
        assertTrue(placement.y + placement.height <= window.bottom - 16)
        assertTrue(placement.y < 1900)
    }

    @Test
    fun `card is capped to window height and keeps top margin`() {
        val placement = place(rowTop = 600, naturalHeight = 4000)
        assertEquals(window.height - 32, placement.height)
        assertEquals(16, placement.y)
    }
}
