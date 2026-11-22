package dev.errnolink.tsuzuki.designsystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DetentSheetKindTest {
    private val viewport = 800f

    private fun heights(kind: SheetKind, content: Float, keyboard: Boolean = false) =
        DetentSheetGeometry.sheetHeights(kind, content, viewport, keyboard)

    @Test
    fun `confirm opens at content capped at forty percent`() {
        val small = heights(SheetKind.Confirm, 200f)
        assertEquals(200f, small.openHeightPx)
        assertFalse(small.draggableToFull)
        val tall = heights(SheetKind.Confirm, 500f)
        assertEquals(viewport * DetentSheetGeometry.ConfirmShare, tall.openHeightPx)
    }

    @Test
    fun `picker opens at content capped at sixty percent and may drag to full`() {
        val short = heights(SheetKind.Picker, 400f)
        assertEquals(400f, short.openHeightPx)
        assertTrue(short.draggableToFull)
        val tall = heights(SheetKind.Picker, 700f)
        assertEquals(viewport * DetentSheetGeometry.PickerShare, tall.openHeightPx)
    }

    @Test
    fun `live settings opens at half viewport regardless of content`() {
        val heights = heights(SheetKind.LiveSettings, 100f)
        assertEquals(viewport * DetentSheetGeometry.KindHalfShare, heights.openHeightPx)
        assertTrue(heights.draggableToFull)
    }

    @Test
    fun `browse list opens at half and full when the keyboard is up`() {
        val resting = heights(SheetKind.BrowseList, 300f)
        assertEquals(viewport * DetentSheetGeometry.KindHalfShare, resting.openHeightPx)
        val typing = heights(SheetKind.BrowseList, 300f, keyboard = true)
        assertEquals(viewport, typing.openHeightPx)
    }

    @Test
    fun `free keeps content height and content never exceeds viewport`() {
        assertEquals(300f, heights(SheetKind.Free, 300f).openHeightPx)
        assertEquals(viewport, heights(SheetKind.Free, 5000f).openHeightPx)
    }
}
