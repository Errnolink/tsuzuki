package dev.errnolink.tsuzuki.ui.reader

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReaderChromeColorTest {
    @Test
    fun `an accent that does not read on the glass falls back to the label colour`() {
        val ink = Color(0xFF191A20)
        val lightGlass = Color(0xFFF6F6FA)
        // a light accent on light glass gives way to the ink; a dark accent stays
        assertEquals(ink, inkOnGlass(Color(0xFFBBD4FF), ink, lightGlass))
        assertEquals(Color(0xFF205DC2), inkOnGlass(Color(0xFF205DC2), ink, lightGlass))
        // and the same rule holds on the dark glass
        val darkInk = Color(0xFFF5F5F7)
        val darkGlass = Color(0xFF24252B)
        assertEquals(darkInk, inkOnGlass(Color(0xFF3A3B44), darkInk, darkGlass))
        assertEquals(Color(0xFF8BB9FF), inkOnGlass(Color(0xFF8BB9FF), darkInk, darkGlass))
    }

    @Test
    fun `the glass fill composites the tint over the page it floats on`() {
        val white = Color.White
        val tint = Color(0xFFF6F6FA)
        assertEquals(white, glassFillOver(tint, white, 0f))
        assertEquals(tint, glassFillOver(tint, white, 1f))
        val half = glassFillOver(tint, white, 0.5f)
        assertTrue(half.red > tint.red && half.red < white.red, "halfway between page and tint")
    }
}
