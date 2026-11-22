package dev.errnolink.tsuzuki.ui.shell

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The bottom search dock's pure geometry: the full-width field's landing rect (design 2B — the tab
 * bar is gone while searching, so the field spans the row) and how the × circle arrives and leaves.
 */
class BottomSearchDockGeometryTest {

    private val row = Rect(0f, 0f, 1080f, 54f)
    private val origin = Offset.Zero

    @Test
    fun `the field spans the row from its leading edge on every page`() {
        val field = bottomSearchFieldRect(
            row = row,
            hostOrigin = origin,
            rowHeightPx = 54f,
            gapPx = 8f,
            fieldHeightPx = 54f,
        )
        assertEquals(0f, field.left, 0.01f)
        assertEquals(1080f - 62f, field.right, 0.01f)
    }

    @Test
    fun `the field fills the row height and ends before the trailing circle`() {
        val field = bottomSearchFieldRect(
            row = row,
            hostOrigin = origin,
            rowHeightPx = 54f,
            gapPx = 8f,
            fieldHeightPx = 54f,
        )
        assertEquals(0f, field.top, 0.01f)
        assertEquals(54f, field.bottom, 0.01f)
        assertTrue(field.right < row.right - 54f, "field clears the trailing × slot")
    }

    @Test
    fun `row coordinates are mapped into the host's space`() {
        val field = bottomSearchFieldRect(
            row = Rect(100f, 200f, 1180f, 254f),
            hostOrigin = Offset(100f, 200f),
            rowHeightPx = 54f,
            gapPx = 8f,
            fieldHeightPx = 54f,
        )
        assertEquals(0f, field.left, 0.01f)
        assertEquals(0f, field.top, 0.01f)
    }

    @Test
    fun `the close circle waits for the shape then scales in from point six`() {
        assertEquals(0f, searchCloseCircleAppear(0f, opening = true), 0.001f)
        assertEquals(0f, searchCloseCircleAppear(0.35f, opening = true), 0.001f)
        assertTrue(searchCloseCircleAppear(0.6f, opening = true) in 0.01f..0.99f)
        assertEquals(1f, searchCloseCircleAppear(0.85f, opening = true), 0.001f)
        assertEquals(1f, searchCloseCircleAppear(1f, opening = true), 0.001f)
        assertEquals(0.6f, searchCloseCircleScale(0f, opening = true), 0.001f)
        assertEquals(1f, searchCloseCircleScale(1f, opening = true), 0.001f)
    }

    @Test
    fun `on close the circle scales out with the field content`() {
        assertEquals(1f, searchCloseCircleAppear(1f, opening = false), 0.001f)
        assertTrue(searchCloseCircleAppear(0.9f, opening = false) in 0.01f..0.99f)
        assertEquals(0f, searchCloseCircleAppear(0.4f, opening = false), 0.001f)
        assertEquals(0f, searchCloseCircleAppear(0f, opening = false), 0.001f)
    }
}
