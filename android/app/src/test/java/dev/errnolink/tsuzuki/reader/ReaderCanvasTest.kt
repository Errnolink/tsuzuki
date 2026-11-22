package dev.errnolink.tsuzuki.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReaderCanvasTest {
    private val gray = 0xFF202125.toInt()

    @Test
    fun `every reader theme resolves one canvas colour for the veil, the loading state and the page`() {
        assertEquals(0xFFFFFFFF.toInt(), readerCanvasArgb(0, systemDark = false))
        assertEquals(0xFFFFFFFF.toInt(), readerCanvasArgb(0, systemDark = true), "an explicit white stays white in dark mode")
        assertEquals(0xFF000000.toInt(), readerCanvasArgb(1, systemDark = false))
        assertEquals(gray, readerCanvasArgb(2, systemDark = true))
        assertEquals(0xFFFFFFFF.toInt(), readerCanvasArgb(3, systemDark = false))
        assertEquals(gray, readerCanvasArgb(3, systemDark = true))
        assertEquals(0xFF000000.toInt(), readerCanvasArgb(9, systemDark = false))
    }

    @Test
    fun `the canvas dark flag agrees with the canvas colour`() {
        assertFalse(readerCanvasDark(0, systemDark = true))
        assertTrue(readerCanvasDark(1, systemDark = false))
        assertTrue(readerCanvasDark(2, systemDark = false))
        assertFalse(readerCanvasDark(3, systemDark = false))
        assertTrue(readerCanvasDark(3, systemDark = true))
    }
}
