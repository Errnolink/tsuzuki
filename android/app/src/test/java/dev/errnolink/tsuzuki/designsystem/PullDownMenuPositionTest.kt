package dev.errnolink.tsuzuki.designsystem

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PullDownMenuPositionTest {

    private val windowSize = IntSize(width = 1080, height = 2400)
    private val popupSize = IntSize(width = 600, height = 400)
    private val margin = 16
    private val gap = 6

    @Test
    fun `menu opens below anchor when room below fits content`() {
        // Anchor near top
        val anchor = IntRect(left = 900, top = 100, right = 1000, bottom = 180)
        val result = computeMenuPosition(
            anchorBounds = anchor,
            windowSize = windowSize,
            isLtr = true,
            popupSize = popupSize,
            marginPx = margin,
            verticalGapPx = gap,
        )

        assertFalse(result.isAbove)
        assertEquals(anchor.bottom + gap, result.offset.y)
        assertEquals(0f, result.origin.pivotFractionY)
    }

    @Test
    fun `menu opens above anchor when space below is tight but space above fits`() {
        // Anchor near bottom (y = 2250 to 2320, space below is 80 - 16 = 64 < 400)
        val anchor = IntRect(left = 900, top = 2250, right = 1000, bottom = 2320)
        val result = computeMenuPosition(
            anchorBounds = anchor,
            windowSize = windowSize,
            isLtr = true,
            popupSize = popupSize,
            marginPx = margin,
            verticalGapPx = gap,
        )

        assertTrue(result.isAbove)
        assertEquals(anchor.top - popupSize.height - gap, result.offset.y)
        assertEquals(1f, result.origin.pivotFractionY)
    }

    @Test
    fun `a menu that grows out of its button covers the button and starts from its exact bounds`() {
        val below = IntRect(left = 940, top = 100, right = 1040, bottom = 180)
        val belowResult = computeMenuPosition(
            anchorBounds = below,
            windowSize = windowSize,
            isLtr = true,
            popupSize = popupSize,
            marginPx = margin,
            verticalGapPx = gap,
            overlapAnchor = true,
        )
        assertFalse(belowResult.isAbove)
        assertEquals(below.top, belowResult.offset.y)
        assertEquals(below.right, belowResult.offset.x + popupSize.width)
        assertEquals(IntRect(500, 0, 600, 80), belowResult.anchorInPopup)

        val above = IntRect(left = 900, top = 2250, right = 1000, bottom = 2320)
        val aboveResult = computeMenuPosition(
            anchorBounds = above,
            windowSize = windowSize,
            isLtr = true,
            popupSize = popupSize,
            marginPx = margin,
            verticalGapPx = gap,
            overlapAnchor = true,
        )
        assertTrue(aboveResult.isAbove)
        assertEquals(above.bottom, aboveResult.offset.y + popupSize.height)
        assertEquals(popupSize.height, aboveResult.anchorInPopup.bottom)
    }

    @Test
    fun `menu aligns right edge with anchor right edge on right half of screen`() {
        // Anchor right = 1040, anchor center = 990 > 540
        val anchor = IntRect(left = 940, top = 100, right = 1040, bottom = 180)
        val result = computeMenuPosition(
            anchorBounds = anchor,
            windowSize = windowSize,
            isLtr = true,
            popupSize = popupSize,
            marginPx = margin,
            verticalGapPx = gap,
        )

        // Menu right edge (offset.x + width) should equal anchor.right (1040)
        assertEquals(1040 - popupSize.width, result.offset.x)
        assertEquals(1040, result.offset.x + popupSize.width)
        // Transform origin X should scale from the trigger corner (near 1.0)
        assertTrue(result.origin.pivotFractionX > 0.8f)
    }

    @Test
    fun `menu aligns left edge with anchor left edge on left half of screen`() {
        // Anchor left = 40, anchor center = 90 < 540
        val anchor = IntRect(left = 40, top = 100, right = 140, bottom = 180)
        val result = computeMenuPosition(
            anchorBounds = anchor,
            windowSize = windowSize,
            isLtr = true,
            popupSize = popupSize,
            marginPx = margin,
            verticalGapPx = gap,
        )

        assertEquals(40, result.offset.x)
        // Transform origin X should scale from the trigger corner (near 0.0)
        assertTrue(result.origin.pivotFractionX < 0.2f)
    }

    @Test
    fun `menu clamps inside window margins when anchor is at extreme screen boundary`() {
        // Anchor right = 1080 (flush with screen edge)
        val anchor = IntRect(left = 1020, top = 50, right = 1080, bottom = 110)
        val result = computeMenuPosition(
            anchorBounds = anchor,
            windowSize = windowSize,
            isLtr = true,
            popupSize = popupSize,
            marginPx = margin,
            verticalGapPx = gap,
        )

        // Clamped to window width - popupSize.width - margin
        val expectedMaxX = windowSize.width - popupSize.width - margin
        assertEquals(expectedMaxX, result.offset.x)
        assertTrue(result.offset.x >= margin)
        assertTrue(result.offset.x + popupSize.width <= windowSize.width - margin)
    }

    @Test
    fun `max available height accurately reflects remaining vertical space without overflow`() {
        val anchor = IntRect(left = 500, top = 2050, right = 600, bottom = 2120)
        val result = computeMenuPosition(
            anchorBounds = anchor,
            windowSize = windowSize,
            isLtr = true,
            popupSize = popupSize,
            marginPx = margin,
            verticalGapPx = gap,
        )

        assertTrue(result.isAbove)
        assertEquals(2034, result.maxAvailableHeightPx)
    }

    @Test
    fun `glued menu opens upward from a bottom anchor and caps to the space above`() {
        val bottomAnchor = IntRect(left = 900, top = 2300, right = 1000, bottom = 2384)
        val tallPopup = IntSize(width = 600, height = 1200)
        val result = computeMenuPosition(
            anchorBounds = bottomAnchor,
            windowSize = windowSize,
            isLtr = true,
            popupSize = tallPopup,
            marginPx = margin,
            verticalGapPx = gap,
            glueToAnchor = true,
        )

        assertTrue(result.isAbove)
        val spaceAbove = bottomAnchor.top - margin
        assertEquals(spaceAbove, result.maxAvailableHeightPx)
        assertEquals(bottomAnchor.top - tallPopup.height - gap, result.offset.y)
        assertEquals(1f, result.origin.pivotFractionY)
    }

    @Test
    fun `glued menu taller than both sides stays attached to the anchor with capped height`() {
        val bottomAnchor = IntRect(left = 900, top = 2300, right = 1000, bottom = 2384)
        val oversizedPopup = IntSize(width = 600, height = 3000)
        val result = computeMenuPosition(
            anchorBounds = bottomAnchor,
            windowSize = windowSize,
            isLtr = true,
            popupSize = oversizedPopup,
            marginPx = margin,
            verticalGapPx = gap,
            glueToAnchor = true,
        )

        assertTrue(result.isAbove)
        val spaceAbove = bottomAnchor.top - margin
        assertEquals(spaceAbove, result.maxAvailableHeightPx)
        assertEquals(margin, result.offset.y)
        assertEquals(1f, result.origin.pivotFractionY)
    }

    @Test
    fun `glued menu still opens below the anchor when the content fits under it`() {
        val topAnchor = IntRect(left = 900, top = 100, right = 1000, bottom = 180)
        val result = computeMenuPosition(
            anchorBounds = topAnchor,
            windowSize = windowSize,
            isLtr = true,
            popupSize = popupSize,
            marginPx = margin,
            verticalGapPx = gap,
            glueToAnchor = true,
        )

        assertFalse(result.isAbove)
        assertEquals(topAnchor.bottom + gap, result.offset.y)
        assertEquals(0f, result.origin.pivotFractionY)
    }

    @Test
    fun `an overlap offset pulls the menu toward its button whether it opens above or below`() {
        val overlap = IntOffset(24, -56)
        val low = IntRect(left = 900, top = 2250, right = 1000, bottom = 2320)
        val above = computeMenuPosition(low, windowSize, isLtr = true, popupSize, margin, gap, offsetPx = overlap)
        assertTrue(above.isAbove)
        assertTrue(above.offset.y + popupSize.height > low.top - gap, "menu above drifted away: bottom ${above.offset.y + popupSize.height}")
        assertTrue(above.offset.x + popupSize.width <= low.right, "right-aligned menu overshot its button")

        val high = IntRect(left = 900, top = 100, right = 1000, bottom = 180)
        val below = computeMenuPosition(high, windowSize, isLtr = true, popupSize, margin, gap, offsetPx = overlap)
        assertFalse(below.isAbove)
        assertTrue(below.offset.y < high.bottom + gap, "menu below drifted away: top ${below.offset.y}")
    }

    @Test
    fun `right-to-left menus mirror the side rule instead of always aligning right`() {
        val startSide = IntRect(left = 40, top = 100, right = 140, bottom = 180)
        val result = computeMenuPosition(startSide, windowSize, isLtr = false, popupSize, margin, gap)
        assertEquals(startSide.left, result.offset.x)
    }
}
