package dev.errnolink.tsuzuki.designsystem

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.min

class MenuMorphPlacementTest {

    private val window = IntSize(width = 1080, height = 2400)
    private val bounds = IntRect(0, 0, window.width, window.height)
    private val menu = IntSize(width = 600, height = 400)
    private val margin = 8

    private fun place(anchor: IntRect, popupSize: IntSize = menu) =
        computeMorphPlacement(anchor, bounds, window, popupSize, margin)

    @Test
    fun `a trigger in the left third keeps its left edge`() {
        val anchor = IntRect(40, 100, 140, 180)
        val placement = place(anchor)
        assertEquals(MenuAlign.Start, placement.align)
        assertEquals(anchor.left, placement.offset.x)
        // the menu covers its trigger
        assertEquals(anchor.top, placement.offset.y)
        assertEquals(MenuGrow.Down, placement.grow)
    }

    @Test
    fun `a trigger in the right third keeps its right edge`() {
        val anchor = IntRect(900, 100, 1000, 180)
        val placement = place(anchor)
        assertEquals(MenuAlign.End, placement.align)
        assertEquals(anchor.right, placement.offset.x + menu.width)
    }

    @Test
    fun `a trigger in the middle third centres the menu on itself`() {
        val anchor = IntRect(450, 100, 630, 180)
        val placement = place(anchor)
        assertEquals(MenuAlign.Center, placement.align)
        assertEquals(anchor.center.x, placement.offset.x + menu.width / 2)
    }

    @Test
    fun `grows down whenever it fits and up only when the trigger sits too low`() {
        val top = place(IntRect(450, 100, 630, 180))
        assertEquals(MenuGrow.Down, top.grow)
        assertEquals(100, top.offset.y)
        // A chapter-row icon below the middle with room under it opens down over the list.
        val middle = place(IntRect(900, 1500, 1000, 1580))
        assertEquals(MenuGrow.Down, middle.grow)
        assertEquals(1500, middle.offset.y)
        val low = place(IntRect(450, 2100, 630, 2200))
        assertEquals(MenuGrow.Up, low.grow)
        assertEquals(2200, low.offset.y + menu.height)
    }

    @Test
    fun `flips up when growing down would not fit but up does`() {
        val anchor = IntRect(450, 1210, 630, 1290)
        val tall = IntSize(600, 1200)
        val placement = place(anchor, tall)
        assertEquals(MenuGrow.Up, placement.grow)
        assertEquals(1290, placement.offset.y + tall.height)
    }

    @Test
    fun `clamps to the margin but may sit as close to an edge as its trigger`() {
        val anchor = IntRect(2, 100, 102, 180)
        val placement = place(anchor)
        assertTrue(placement.offset.x >= anchor.left, "menu was pushed further in than its trigger")
        assertTrue(placement.offset.x <= anchor.left + margin)
        assertEquals(anchor.top, placement.offset.y)
    }

    @Test
    fun `a menu taller than the bounds is capped to scroll inside`() {
        val anchor = IntRect(450, 100, 630, 180)
        val giant = IntSize(600, 3000)
        val placement = place(anchor, giant)
        assertEquals(window.height - 2 * margin, placement.maxHeightPx)
        // it still covers its trigger
        assertTrue(placement.offset.y <= anchor.top)
        assertTrue(placement.offset.y + giant.height >= anchor.bottom)
    }

    @Test
    fun `an explicit override grows where it is told`() {
        val bottom = IntRect(450, 2100, 630, 2200)
        val up = computeMorphPlacement(bottom, bounds, window, menu, margin, grow = MenuGrow.Up)
        assertEquals(bottom.bottom, up.offset.y + menu.height)
        val rightSide = IntRect(700, 100, 800, 180)
        val end = computeMorphPlacement(rightSide, bounds, window, menu, margin, align = MenuAlign.End)
        assertEquals(rightSide.right, end.offset.x + menu.width)
    }

    @Test
    fun `a menu wider than the screen still lands inside the margins`() {
        val anchor = IntRect(500, 100, 580, 180)
        val wide = IntSize(2000, 200)
        val placement = place(anchor, wide)
        // pinned at the safe edge instead of crashing or drifting out of bounds
        assertEquals(margin, placement.offset.x)
        assertTrue(placement.offset.y <= anchor.top)
    }

    @Test
    fun `a whole capsule anchor places the menu on the capsule's edges`() {
        // Library's [filter][more] capsule: 104 wide. The menu must line up with its edges for
        // BOTH buttons, not with the button the popup is composed in.
        val capsule = IntRect(940, 60, 1044, 108)
        val placement = place(capsule, popupSize = IntSize(840, 1200))
        assertEquals(MenuAlign.End, placement.align)
        assertEquals(capsule.right, placement.offset.x + 840)
        assertEquals(capsule.top, placement.offset.y)
    }
}

class LiquidMorphTest {

    private val panel = Size(600f, 400f)
    private val pinTopRight = Offset(1f, 0f)

    // A capsule trigger in the panel's top-right corner (a top-right [filter][more] capsule).
    private val topRight = Rect(496f, 0f, 600f, 88f)

    private fun frame(t: Float, opening: Boolean = true) =
        liquidFrame(t, topRight, panel, pinTopRight, 56f, opening = opening)

    @Test
    fun `the first frame is the trigger itself, showing its own pixels`() {
        val f = frame(0f)
        assertEquals(topRight, f.body)
        assertEquals(liquidTriggerRadius(topRight), f.bodyRadius, 0.5f)
        assertEquals(1f, f.triggerAlpha, 1e-4f)
        assertEquals(0f, f.contentAlpha, 1e-4f)
    }

    @Test
    fun `the landed frame is the menu with crisp content and no trace of the trigger`() {
        val f = frame(1f)
        assertEquals(Rect(0f, 0f, panel.width, panel.height), f.body)
        assertEquals(56f, f.bodyRadius, 0.01f)
        assertEquals(1f, f.contentAlpha, 1e-4f)
        assertEquals(0f, f.contentBlur, 1e-4f)
        assertEquals(1f, f.contentScale, 1e-4f)
        assertEquals(0f, f.triggerAlpha, 1e-4f)
        assertEquals(0f, f.contentOffset.getDistance(), 1e-3f)
    }

    @Test
    fun `the height leads and the width trails`() {
        for (t in listOf(0.1f, 0.3f, 0.5f, 0.8f)) {
            val f = frame(t)
            val w = (f.body.width - topRight.width) / (panel.width - topRight.width)
            val h = (f.body.height - topRight.height) / (panel.height - topRight.height)
            assertTrue(h > w, "t=$t: height $h ahead of width $w")
        }
    }

    @Test
    fun `content is revealed at full size from the shared corner, never zoomed`() {
        for (t in listOf(0.25f, 0.4f, 0.6f, 0.9f)) {
            val f = frame(t)
            val cornerX = pinTopRight.x * panel.width + f.contentOffset.x
            val cornerY = pinTopRight.y * panel.height + f.contentOffset.y
            assertEquals(f.body.right, cornerX, 0.5f, "t=$t")
            assertEquals(f.body.top, cornerY, 0.5f, "t=$t")
            assertEquals(1f, f.contentScale, 1e-4f, "t=$t")
        }
    }

    @Test
    fun `the button's icon fades as it starts to grow and the rows arrive blurred`() {
        assertEquals(0f, frame(0.1f).triggerAlpha, 1e-4f, "icon gone early")
        assertEquals(0f, frame(0.1f).contentAlpha, 1e-4f, "rows not yet")
        assertTrue(frame(0.3f).contentAlpha > 0f && frame(0.3f).contentBlur > 0.5f, "rows arrive blurred")
        assertTrue(frame(0.8f).contentBlur > 0f, "still sharpening near the landing")
    }

    @Test
    fun `mid-flight the glass is a drop, fully round on its short side`() {
        val f = frame(0.4f)
        val shortHalf = min(f.body.width, f.body.height) / 2f
        assertEquals(shortHalf, f.bodyRadius, 0.5f)
    }

    @Test
    fun `a closing liquid that undershoots squeezes the button about its own centre`() {
        val f = frame(-0.08f, opening = false)
        assertTrue(f.body.width < topRight.width && f.body.height < topRight.height, "smaller than the button: ${f.body}")
        assertEquals(topRight.center.x, f.body.center.x, 0.01f)
        assertEquals(topRight.center.y, f.body.center.y, 0.01f)
        assertTrue(f.squeeze < 1f && f.triggerAlpha == 1f, "the button's own pixels squeeze with it")
    }

    @Test
    fun `closing retraces the opening shape, and the rows leave before the glass shrinks`() {
        for (t in listOf(1f, 0.7f, 0.4f, 0.12f, 0f)) {
            val open = frame(t, opening = true)
            val close = frame(t, opening = false)
            assertEquals(open.body, close.body, "t=$t")
            assertEquals(open.bodyRadius, close.bodyRadius, 1e-4f, "t=$t")
        }
        assertEquals(0f, frame(0.7f, opening = false).contentAlpha, 1e-4f, "rows gone early in the close")
        assertTrue(frame(0.25f, opening = false).triggerAlpha > 0f, "the button re-forms in the shrinking glass")
        assertEquals(1f, frame(0.05f, opening = false).triggerAlpha, 1e-4f)
        assertEquals(0f, frame(0f, opening = false).lit, 1e-4f, "no press light when landing")
    }

    @Test
    fun `the landing overshoot grows the layer and settles back`() {
        assertTrue(frame(1.02f).layerScale > 1f)
        assertEquals(1f, frame(1f).layerScale, 1e-4f)
    }
}
