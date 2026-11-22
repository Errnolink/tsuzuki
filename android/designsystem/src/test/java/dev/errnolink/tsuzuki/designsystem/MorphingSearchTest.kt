package dev.errnolink.tsuzuki.designsystem

import androidx.compose.ui.geometry.Rect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Kasane's search transition (tab-bar.css `.is-searching`) and its close-button rule. */
class MorphingSearchTest {

    private val from = Rect(800f, 10f, 848f, 58f) // a 48×48 circle in the row
    private val to = Rect(16f, 10f, 1000f, 58f) // the field, same row and height

    @Test
    fun `the flight is a capsule of the circle's height all the way`() {
        for (t in 0..10) {
            val frame = searchMorphFrame(t / 10f, opening = true, from, to)
            assertEquals(48f, frame.rect.height, 0.01f, "height at ${t / 10f}")
            assertEquals(24f, frame.cornerRadius, 0.01f, "radius at ${t / 10f}")
        }
    }

    @Test
    fun `it starts on the circle and lands on the field without overshooting`() {
        assertEquals(from, searchMorphFrame(0f, opening = true, from, to).rect)
        assertEquals(to, searchMorphFrame(1f, opening = true, from, to).rect)
        assertEquals(to, searchMorphFrame(1.05f, opening = true, from, to).rect)
    }

    @Test
    fun `content waits for the shape then fades in while opening`() {
        assertEquals(0f, searchMorphFrame(0.3f, opening = true, from, to).contentAlpha)
        assertTrue(searchMorphFrame(0.6f, opening = true, from, to).contentAlpha in 0.1f..0.9f)
        assertEquals(1f, searchMorphFrame(1f, opening = true, from, to).contentAlpha)
    }

    @Test
    fun `content drains across the close, earlier than it arrives`() {
        assertEquals(1f, searchMorphFrame(1f, opening = false, from, to).contentAlpha)
        assertEquals(0f, searchMorphFrame(0.4f, opening = false, from, to).contentAlpha)
        assertTrue(
            searchMorphFrame(0.9f, opening = false, from, to).contentAlpha <
                searchMorphFrame(0.9f, opening = true, from, to).contentAlpha,
            "closing content leaves earlier than it arrives",
        )
    }

    @Test
    fun `close button clears text when there is text and closes when empty`() {
        assertEquals(SearchCloseAction.Clear, searchCloseAction(hasText = true))
        assertEquals(SearchCloseAction.Close, searchCloseAction(hasText = false))
    }

    @Test
    fun `flight travels every edge from the button to the field`() {
        val frame = searchMorphFrame(0.5f, opening = true, from, to)
        assertEquals((from.left + to.left) / 2f, frame.rect.left, 1e-4f)
        assertEquals((from.right + to.right) / 2f, frame.rect.right, 1e-4f)
        assertEquals((from.top + to.top) / 2f, frame.rect.top, 1e-4f)
        assertEquals((from.bottom + to.bottom) / 2f, frame.rect.bottom, 1e-4f)
    }

    @Test
    fun `flight icon centers in both 48dp and 54dp triggers at rest and lands at field leading inset`() {
        val iconSize = 22f
        val fieldLeading = 14f
        assertEquals(13f, searchFlightIconOffsetPx(48f, iconSize, fieldLeading, 0f), 1e-4f)
        assertEquals(16f, searchFlightIconOffsetPx(54f, iconSize, fieldLeading, 0f), 1e-4f)
        assertEquals(fieldLeading, searchFlightIconOffsetPx(48f, iconSize, fieldLeading, 1f), 1e-4f)
        assertEquals(fieldLeading, searchFlightIconOffsetPx(54f, iconSize, fieldLeading, 1f), 1e-4f)
    }
}
