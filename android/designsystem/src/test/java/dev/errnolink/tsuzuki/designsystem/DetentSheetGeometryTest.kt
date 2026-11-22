package dev.errnolink.tsuzuki.designsystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DetentSheetGeometryTest {

    private val viewport = 2000f
    private val fling = 1600f

    @Test
    fun `a picker with tall content opens at 60 percent`() {
        val heights = DetentSheetGeometry.sheetHeights(SheetKind.Picker, contentPx = 2000f, viewportPx = viewport, keyboardVisible = false)
        assertEquals(1200f, heights.openHeightPx)
        assertTrue(heights.draggableToFull)
    }

    @Test
    fun `a picker with short content opens at the 25 percent floor`() {
        val heights = DetentSheetGeometry.sheetHeights(SheetKind.Picker, contentPx = 300f, viewportPx = viewport, keyboardVisible = false)
        assertEquals(500f, heights.openHeightPx)
        assertTrue(heights.draggableToFull)
    }

    @Test
    fun `a picker with mid content opens at content height`() {
        val heights = DetentSheetGeometry.sheetHeights(SheetKind.Picker, contentPx = 900f, viewportPx = viewport, keyboardVisible = false)
        assertEquals(900f, heights.openHeightPx)
    }

    @Test
    fun `a confirm sheet never opens taller than 40 percent and cannot reach full`() {
        val heights = DetentSheetGeometry.sheetHeights(SheetKind.Confirm, contentPx = 3000f, viewportPx = viewport, keyboardVisible = false)
        assertEquals(800f, heights.openHeightPx)
        assertFalse(heights.draggableToFull)
    }

    @Test
    fun `a confirm sheet opens at content height when it fits`() {
        val heights = DetentSheetGeometry.sheetHeights(SheetKind.Confirm, contentPx = 300f, viewportPx = viewport, keyboardVisible = false)
        assertEquals(300f, heights.openHeightPx)
    }

    @Test
    fun `browse list and live settings open at half the viewport`() {
        val browse = DetentSheetGeometry.sheetHeights(SheetKind.BrowseList, contentPx = 4000f, viewportPx = viewport, keyboardVisible = false)
        assertEquals(1000f, browse.openHeightPx)
        assertTrue(browse.draggableToFull)
        val live = DetentSheetGeometry.sheetHeights(SheetKind.LiveSettings, contentPx = 4000f, viewportPx = viewport, keyboardVisible = false)
        assertEquals(1000f, live.openHeightPx)
        assertTrue(live.draggableToFull)
    }

    @Test
    fun `a browse list opens full when the keyboard is up`() {
        val heights = DetentSheetGeometry.sheetHeights(SheetKind.BrowseList, contentPx = 4000f, viewportPx = viewport, keyboardVisible = true)
        assertEquals(2000f, heights.openHeightPx)
    }

    @Test
    fun `heights never exceed the viewport`() {
        SheetKind.entries.forEach { kind ->
            val heights = DetentSheetGeometry.sheetHeights(kind, contentPx = 9000f, viewportPx = viewport, keyboardVisible = false)
            assertTrue(heights.openHeightPx <= viewport, "${kind.name} opens taller than the viewport")
        }
    }

    @Test
    fun `a release high above the opening height settles at full`() {
        val settle = DetentSheetGeometry.releaseTarget(
            offsetPx = 200f,
            velocityPx = 0f,
            heightPx = viewport,
            flingThresholdPx = fling,
            restPx = 800f,
        )
        assertEquals(0f, settle.target)
        assertFalse(settle.dismiss)
    }

    @Test
    fun `a slow release just below the opening height returns to it`() {
        val settle = DetentSheetGeometry.releaseTarget(
            offsetPx = 900f,
            velocityPx = 0f,
            heightPx = viewport,
            flingThresholdPx = fling,
            restPx = 800f,
        )
        assertEquals(800f, settle.target)
        assertFalse(settle.dismiss)
    }

    @Test
    fun `a downward fling from the opening height dismisses`() {
        val settle = DetentSheetGeometry.releaseTarget(
            offsetPx = 800f,
            velocityPx = 5000f,
            heightPx = viewport,
            flingThresholdPx = fling,
            restPx = 800f,
        )
        assertTrue(settle.dismiss)
    }

    @Test
    fun `a gentle downward release from the opening height returns to it`() {
        val settle = DetentSheetGeometry.releaseTarget(
            offsetPx = 800f,
            velocityPx = 2500f,
            heightPx = viewport,
            flingThresholdPx = fling,
            restPx = 800f,
        )
        assertEquals(800f, settle.target)
        assertFalse(settle.dismiss)
    }

    @Test
    fun `an upward fling from the bottom settles at full`() {
        val settle = DetentSheetGeometry.releaseTarget(
            offsetPx = 1800f,
            velocityPx = -5000f,
            heightPx = viewport,
            flingThresholdPx = fling,
            restPx = 800f,
        )
        assertEquals(1000f, settle.target)
        assertFalse(settle.dismiss)
    }

    @Test
    fun `the scrim is full at the resting offset and clear when dismissed`() {
        // A half-screen sheet in a 2000px viewport rests at offset 1000.
        assertEquals(1f, DetentSheetGeometry.scrimReveal(offsetPx = 1000f, restPx = 1000f, viewportPx = viewport))
        assertEquals(0f, DetentSheetGeometry.scrimReveal(offsetPx = 2000f, restPx = 1000f, viewportPx = viewport))
    }

    @Test
    fun `the scrim falls monotonically as the sheet leaves its rest`() {
        var previous = 1f
        (1000..2000 step 100).forEach { offset ->
            val reveal = DetentSheetGeometry.scrimReveal(offset.toFloat(), 1000f, viewport)
            assertTrue(reveal <= previous + 1e-6f, "reveal rose at offset $offset")
            previous = reveal
        }
    }

    @Test
    fun `the scrim stays full above the resting offset and never inverts while dragging away`() {
        // Dragging up from rest (toward full) holds full strength...
        assertEquals(1f, DetentSheetGeometry.scrimReveal(offsetPx = 400f, restPx = 1000f, viewportPx = viewport))
        // ...and the first frame of a drag from rest is continuous, not a jump (WP4.3: the old
        // offset/viewport formula read 0.5 at the half detent the sheet had just settled at).
        assertTrue(DetentSheetGeometry.scrimReveal(offsetPx = 1008f, restPx = 1000f, viewportPx = viewport) > 0.98f)
        assertTrue(DetentSheetGeometry.scrimReveal(offsetPx = 1200f, restPx = 1000f, viewportPx = viewport) > 0.79f)
    }

    @Test
    fun `a degenerate or unknown rest offset gives no scrim`() {
        assertEquals(0f, DetentSheetGeometry.scrimReveal(offsetPx = 0f, restPx = Float.NaN, viewportPx = viewport))
        assertEquals(0f, DetentSheetGeometry.scrimReveal(offsetPx = 1000f, restPx = viewport, viewportPx = viewport))
    }
}
