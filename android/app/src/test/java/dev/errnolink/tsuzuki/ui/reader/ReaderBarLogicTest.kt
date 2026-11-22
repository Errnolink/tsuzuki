package dev.errnolink.tsuzuki.ui.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReaderBarLogicTest {

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Track/fill geometry math
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `calculateSliderFraction computes correct proportions in LTR`() {
        val range = 1..10
        assertEquals(0f, calculateSliderFraction(1, range, reversed = false))
        assertEquals(1f, calculateSliderFraction(10, range, reversed = false))
        assertEquals(0.5f, calculateSliderFraction(5, 1..9, reversed = false), 0.001f)
    }

    @Test
    fun `calculateSliderFraction inverts proportions in RTL`() {
        val range = 1..10
        assertEquals(1f, calculateSliderFraction(1, range, reversed = true))
        assertEquals(0f, calculateSliderFraction(10, range, reversed = true))
        assertEquals(0.5f, calculateSliderFraction(5, 1..9, reversed = true), 0.001f)
    }

    @Test
    fun `calculateSliderFraction safely handles single page and out of bounds`() {
        val single = 1..1
        assertEquals(0f, calculateSliderFraction(1, single, reversed = false))
        assertEquals(1f, calculateSliderFraction(1, single, reversed = true))

        val range = 1..10
        assertEquals(0f, calculateSliderFraction(-5, range, reversed = false))
        assertEquals(1f, calculateSliderFraction(25, range, reversed = false))
        assertEquals(1f, calculateSliderFraction(-5, range, reversed = true))
        assertEquals(0f, calculateSliderFraction(25, range, reversed = true))
    }

    @Test
    fun `calculateSliderValueAt translates touch position in LTR`() {
        val range = 1..10
        val width = 200f
        val pad = 10f // track spans 10f to 190f (180f wide)

        // At or before start padding
        assertEquals(1, calculateSliderValueAt(10f, width, pad, range, reversed = false))
        assertEquals(1, calculateSliderValueAt(0f, width, pad, range, reversed = false))

        // At or after end padding
        assertEquals(10, calculateSliderValueAt(190f, width, pad, range, reversed = false))
        assertEquals(10, calculateSliderValueAt(250f, width, pad, range, reversed = false))

        // In exact midpoint (10 + 90 = 100f -> fraction 0.5 -> 1 + 0.5 * 9 = 5.5 -> 6)
        assertEquals(6, calculateSliderValueAt(100f, width, pad, range, reversed = false))
    }

    @Test
    fun `calculateSliderValueAt translates touch position in RTL`() {
        val range = 1..10
        val width = 200f
        val pad = 10f

        // At start padding in RTL is the highest page
        assertEquals(10, calculateSliderValueAt(10f, width, pad, range, reversed = true))
        assertEquals(10, calculateSliderValueAt(0f, width, pad, range, reversed = true))

        // At end padding in RTL is page 1
        assertEquals(1, calculateSliderValueAt(190f, width, pad, range, reversed = true))
        assertEquals(1, calculateSliderValueAt(250f, width, pad, range, reversed = true))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Page-chip and chapter text formatting
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `page count formatting stays numeric`() {
        assertEquals("1 / 1", formatPageCountText(1, 1))
        assertEquals("12 / 29", formatPageCountText(12, 29))
    }

    @Test
    fun `formatChapterShortName formats integer and decimal chapters`() {
        assertEquals("Ch. 1", formatChapterShortName(1.0, isRecognized = true, name = "First", chapterShortPattern = "Ch. %1\$s"))
        assertEquals("Ch. 12", formatChapterShortName(12.0, isRecognized = true, name = "A Long Night", chapterShortPattern = "Ch. %1\$s"))
        assertEquals("Ch. 12.5", formatChapterShortName(12.5, isRecognized = true, name = "Extra", chapterShortPattern = "Ch. %1\$s"))
        assertEquals("Kap. 12", formatChapterShortName(12.0, isRecognized = true, name = "A Long Night", chapterShortPattern = "Kap. %1\$s"))
    }

    @Test
    fun `formatChapterShortName falls back to name for unrecognized chapters`() {
        assertEquals("Prologue", formatChapterShortName(-1.0, isRecognized = false, name = "Prologue", chapterShortPattern = "Ch. %1\$s"))
        assertEquals("Oneshot", formatChapterShortName(0.0, isRecognized = false, name = "Oneshot", chapterShortPattern = "Ch. %1\$s"))
        assertNull(formatChapterShortName(-1.0, isRecognized = false, name = "   ", chapterShortPattern = "Ch. %1\$s"))
        assertNull(formatChapterShortName(-1.0, isRecognized = false, name = null, chapterShortPattern = "Ch. %1\$s"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Carousel neighbour state mapping
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `resolveCarouselNeighbours maps LTR and RTL orders`() {
        val labels = ChapterLabels(previous = "Ch. 11", current = "Ch. 12", next = "Ch. 13")

        val ltr = resolveCarouselNeighbours(labels, isRtl = false)
        assertEquals("Ch. 11", ltr.left.label)
        assertFalse(ltr.left.isNext)
        assertEquals("Ch. 13", ltr.right.label)
        assertTrue(ltr.right.isNext)

        val rtl = resolveCarouselNeighbours(labels, isRtl = true)
        assertEquals("Ch. 13", rtl.left.label)
        assertTrue(rtl.left.isNext)
        assertEquals("Ch. 11", rtl.right.label)
        assertFalse(rtl.right.isNext)
    }

    @Test
    fun `resolveCarouselNeighbours handles absent neighbours`() {
        val labelsNoPrev = ChapterLabels(previous = null, current = "Ch. 1", next = "Ch. 2")
        val ltr = resolveCarouselNeighbours(labelsNoPrev, isRtl = false)
        assertNull(ltr.left.label)
        assertEquals("Ch. 2", ltr.right.label)

        val rtl = resolveCarouselNeighbours(labelsNoPrev, isRtl = true)
        assertEquals("Ch. 2", rtl.left.label)
        assertNull(rtl.right.label)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Arrow enable and RTL action mapping
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `resolveLeftArrow and resolveRightArrow map correctly in LTR`() {
        // Both enabled
        val left = resolveLeftArrow(isRtl = false, enabledPrevious = true, enabledNext = false, previousLabel = "Previous chapter", nextLabel = "Next chapter")
        assertEquals("Previous chapter", left.contentDescription)
        assertTrue(left.enabled)
        assertFalse(left.isNext)

        val right = resolveRightArrow(isRtl = false, enabledPrevious = false, enabledNext = true, previousLabel = "Previous chapter", nextLabel = "Next chapter")
        assertEquals("Next chapter", right.contentDescription)
        assertTrue(right.enabled)
        assertTrue(right.isNext)

        // Neither enabled
        val leftDisabled = resolveLeftArrow(isRtl = false, enabledPrevious = false, enabledNext = true, previousLabel = "Previous chapter", nextLabel = "Next chapter")
        assertFalse(leftDisabled.enabled)

        val rightDisabled = resolveRightArrow(isRtl = false, enabledPrevious = true, enabledNext = false, previousLabel = "Previous chapter", nextLabel = "Next chapter")
        assertFalse(rightDisabled.enabled)
    }

    @Test
    fun `resolveLeftArrow and resolveRightArrow swap actions and descriptions in RTL`() {
        // In RTL, left arrow triggers next chapter, right arrow triggers previous chapter
        val left = resolveLeftArrow(isRtl = true, enabledPrevious = false, enabledNext = true, previousLabel = "Previous chapter", nextLabel = "Next chapter")
        assertEquals("Next chapter", left.contentDescription)
        assertTrue(left.enabled)
        assertTrue(left.isNext)

        val right = resolveRightArrow(isRtl = true, enabledPrevious = true, enabledNext = false, previousLabel = "Previous chapter", nextLabel = "Next chapter")
        assertEquals("Previous chapter", right.contentDescription)
        assertTrue(right.enabled)
        assertFalse(right.isNext)

        // When opposite is disabled
        val leftDisabled = resolveLeftArrow(isRtl = true, enabledPrevious = true, enabledNext = false, previousLabel = "Previous chapter", nextLabel = "Next chapter")
        assertFalse(leftDisabled.enabled)

        val rightDisabled = resolveRightArrow(isRtl = true, enabledPrevious = false, enabledNext = true, previousLabel = "Previous chapter", nextLabel = "Next chapter")
        assertFalse(rightDisabled.enabled)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Bar-style gating on navBarType
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `resolveEffectiveBarStyle gates styles on Bottom navigation only`() {
        // Bottom preserves preference
        assertEquals(ReaderBarStyle.Classic, resolveEffectiveBarStyle(ReaderBarStyle.Classic, NavBarType.Bottom))
        assertEquals(ReaderBarStyle.Scrubber, resolveEffectiveBarStyle(ReaderBarStyle.Scrubber, NavBarType.Bottom))
        assertEquals(ReaderBarStyle.Capsule, resolveEffectiveBarStyle(ReaderBarStyle.Capsule, NavBarType.Bottom))

        // Vertical nav always forces Classic
        assertEquals(ReaderBarStyle.Classic, resolveEffectiveBarStyle(ReaderBarStyle.Scrubber, NavBarType.VerticalLeft))
        assertEquals(ReaderBarStyle.Classic, resolveEffectiveBarStyle(ReaderBarStyle.Capsule, NavBarType.VerticalLeft))
        assertEquals(ReaderBarStyle.Classic, resolveEffectiveBarStyle(ReaderBarStyle.Scrubber, NavBarType.VerticalRight))
        assertEquals(ReaderBarStyle.Classic, resolveEffectiveBarStyle(ReaderBarStyle.Capsule, NavBarType.VerticalRight))
        assertEquals(ReaderBarStyle.Classic, resolveEffectiveBarStyle(ReaderBarStyle.Classic, NavBarType.VerticalRight))
    }

    @org.junit.jupiter.api.Test
    fun `chrome shows the page as current over total and nothing before pages load`() {
        org.junit.jupiter.api.Assertions.assertEquals("9 / 30", readerPageCount(9, 30))
        org.junit.jupiter.api.Assertions.assertEquals("30 / 30", readerPageCount(30, 30))
        org.junit.jupiter.api.Assertions.assertEquals(null, readerPageCount(-1, 30))
        org.junit.jupiter.api.Assertions.assertEquals(null, readerPageCount(1, 0))
    }
}
