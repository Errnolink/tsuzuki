package dev.errnolink.tsuzuki.ui.details

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContinueActionColorTest {

    private val accent = Color(0xFF0091FF)

    @Test
    fun `no dominant falls back to accent`() {
        assertEquals(accent, ContinueActionColor.forCover(null, accent))
    }

    @Test
    fun `transparent dominant falls back to accent`() {
        val washed = Color(0.9f, 0.2f, 0.2f, 0.2f)
        assertEquals(accent, ContinueActionColor.forCover(washed, accent))
    }

    @Test
    fun `low saturation cover falls back to accent`() {
        val grey = Color(0xFF8E8E8E)
        assertEquals(accent, ContinueActionColor.forCover(grey, accent))
        val nearBlack = Color(0xFF101010)
        assertEquals(accent, ContinueActionColor.forCover(nearBlack, accent))
        val nearWhite = Color(0xFFFAFAFA)
        assertEquals(accent, ContinueActionColor.forCover(nearWhite, accent))
    }

    @Test
    fun `vivid cover is darkened to readable white contrast`() {
        val vivid = Color(0xFFE1B000)
        val result = ContinueActionColor.forCover(vivid, accent)
        assertTrue(result.red <= vivid.red && result.green <= vivid.green)
        assertTrue(ContinueActionColor.whiteContrast(result) >= ContinueActionColor.MinContrast)
    }

    @Test
    fun `already dark vivid cover is kept`() {
        val dark = Color(0xFF7A1F3D)
        val result = ContinueActionColor.forCover(dark, accent)
        assertEquals(dark, result)
        assertTrue(ContinueActionColor.whiteContrast(result) >= ContinueActionColor.MinContrast)
    }

    @Test
    fun `every clamped result keeps white at or above four point five`() {
        val covers = listOf(
            Color(0xFF3FA34D), Color(0xFF00BCD4), Color(0xFFFF7043),
            Color(0xFF9575CD), Color(0xFFC0CA33), Color(0xFFE57373),
        )
        covers.forEach { cover ->
            val result = ContinueActionColor.forCover(cover, accent)
            assertTrue(
                ContinueActionColor.whiteContrast(result) >= ContinueActionColor.MinContrast,
                "cover $cover produced $result",
            )
        }
    }
}
