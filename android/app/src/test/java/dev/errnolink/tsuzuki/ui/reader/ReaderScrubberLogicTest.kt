package dev.errnolink.tsuzuki.ui.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReaderScrubberLogicTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Haptic tick decisions while dragging: a light tick per page, a stronger
    // one at the first and last page, nothing when the page does not change.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `scrubber ticks lightly on every inner page`() {
        assertEquals(ScrubTick.Page, scrubberTick(5, lastPage = 4, totalPages = 22))
        assertEquals(ScrubTick.Page, scrubberTick(2, lastPage = 3, totalPages = 22))
    }

    @Test
    fun `scrubber ticks stronger at the first and last page`() {
        assertEquals(ScrubTick.Edge, scrubberTick(1, lastPage = 2, totalPages = 22))
        assertEquals(ScrubTick.Edge, scrubberTick(22, lastPage = 21, totalPages = 22))
        assertEquals(ScrubTick.Edge, scrubberTick(1, lastPage = null, totalPages = 1))
    }

    @Test
    fun `scrubber stays silent while the page holds or the chapter is empty`() {
        assertEquals(ScrubTick.None, scrubberTick(5, lastPage = 5, totalPages = 22))
        assertEquals(ScrubTick.None, scrubberTick(5, lastPage = null, totalPages = 0))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Drag position to page, including RTL, through the functions the scrubber
    // actually uses (whole-page snapping, track padding, reading-direction flip).
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `drag position lands on whole pages in LTR`() {
        val range = 1..23
        // track 8..192 inside a 200 px canvas: quarter, half, three quarters
        assertEquals(6, calculateSliderValueAt(50f, 200f, 8f, range, reversed = false))
        assertEquals(12, calculateSliderValueAt(100f, 200f, 8f, range, reversed = false))
        assertEquals(18, calculateSliderValueAt(150f, 200f, 8f, range, reversed = false))
    }

    @Test
    fun `drag position flips to the reading direction in RTL`() {
        val range = 1..23
        // the left edge is the last page in RTL, the right edge is page one
        assertEquals(23, calculateSliderValueAt(8f, 200f, 8f, range, reversed = true))
        assertEquals(12, calculateSliderValueAt(100f, 200f, 8f, range, reversed = true))
        assertEquals(1, calculateSliderValueAt(192f, 200f, 8f, range, reversed = true))
    }

    @Test
    fun `page and fraction round-trip in both directions`() {
        val range = 1..40
        for (page in range) {
            val forward = calculateSliderFraction(page, range, reversed = false)
            assertEquals(page, calculateSliderValueAt(8f + forward * 184f, 200f, 8f, range, reversed = false))
            val backward = calculateSliderFraction(page, range, reversed = true)
            assertEquals(page, calculateSliderValueAt(8f + backward * 184f, 200f, 8f, range, reversed = true))
        }
    }

    @Test
    fun `bubble stands over the thumb of an off-centre track and clamps at the edges`() {
        val container = 400f
        val bubble = 80f
        assertEquals(64f + 8f + 0.5f * 284f - 40f, scrubBubbleLeftPx(64f, 300f, 8f, 0.5f, container, bubble), 0.001f)
        assertEquals(0f, scrubBubbleLeftPx(10f, 300f, 8f, 0f, container, bubble), 0.001f)
        assertEquals(container - bubble, scrubBubbleLeftPx(90f, 310f, 8f, 1f, container, bubble), 0.001f)
    }

    @Test
    fun `scrubber rests where a drag to the same page lands, LTR and RTL`() {
        // currentPage from the view model is 1-based: page 1 of 9 and 9 of 9
        for (rtl in listOf(false, true)) {
            for (current in listOf(1, 5, 9)) {
                val rest = scrubberRestPage(current, 9)
                assertEquals(current, rest)
                val x = 8f + calculateSliderFraction(rest, 1..9, rtl) * 184f
                assertEquals(current, calculateSliderValueAt(x, 200f, 8f, 1..9, rtl))
            }
        }
        assertEquals(1f, calculateSliderFraction(scrubberRestPage(9, 9), 1..9, reversed = false))
        assertEquals(0f, calculateSliderFraction(scrubberRestPage(9, 9), 1..9, reversed = true))
    }

    @Test
    fun `status line shows the page the reader is on and a full pill on the last page`() {
        assertEquals("9 / 9", readerPageCount(9, 9))
        assertEquals(1f, readerProgressFraction(9, 9))
        assertEquals(1f / 9, readerProgressFraction(1, 9), 0.0001f)
    }
}
