package dev.errnolink.tsuzuki.ui.details

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContinueActionColorTest {

    private val accent = Color(0xFF0091FF)

    @Test
    fun `no dominant falls back to a readable accent`() {
        val result = ContinueActionColor.forCover(null, accent)
        assertTrue(ContinueActionColor.whiteContrast(result) >= ContinueActionColor.MinContrast)
    }

    @Test
    fun `transparent dominant falls back to a readable accent`() {
        val washed = Color(0.9f, 0.2f, 0.2f, 0.2f)
        val result = ContinueActionColor.forCover(washed, accent)
        assertTrue(ContinueActionColor.whiteContrast(result) >= ContinueActionColor.MinContrast)
    }

    @Test
    fun `low saturation cover falls back to a readable accent`() {
        val grey = Color(0xFF8E8E8E)
        val nearBlack = Color(0xFF101010)
        val nearWhite = Color(0xFFFAFAFA)
        for (cover in listOf(grey, nearBlack, nearWhite)) {
            val result = ContinueActionColor.forCover(cover, accent)
            assertTrue(
                ContinueActionColor.whiteContrast(result) >= ContinueActionColor.MinContrast,
                "cover $cover produced $result",
            )
        }
    }

    @Test
    fun `every shipped accent fallback keeps white at or above four point five`() {
        val accents = listOf(
            Color(0xFF8BB9FF), Color(0xFF205DC2), Color(0xFF0091FF), Color(0xFF0088FF),
        )
        val grey = Color(0xFF8C8680)
        for (candidate in accents) {
            val fromMissing = ContinueActionColor.forCover(null, candidate)
            assertTrue(
                ContinueActionColor.whiteContrast(fromMissing) >= ContinueActionColor.MinContrast,
                "accent $candidate produced $fromMissing",
            )
            val fromGrey = ContinueActionColor.forCover(grey, candidate)
            assertTrue(
                ContinueActionColor.whiteContrast(fromGrey) >= ContinueActionColor.MinContrast,
                "grey cover with accent $candidate produced $fromGrey",
            )
        }
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

    @Test
    fun `cover colours used as text reach four point five on dark and light pages`() {
        val covers = listOf(Color(0xFF0B4F6C), Color(0xFF1A1A40), Color(0xFFFFE066), Color(0xFF8BB9FF), Color(0xFF7F7F7F))
        listOf(Color(0xFF101114), Color(0xFFF2F2F7)).forEach { page ->
            covers.forEach { cover ->
                val text = ContinueActionColor.readableOn(cover, page)
                assertTrue(ContinueActionColor.contrast(text, page) >= 4.5f, "cover $cover on $page produced $text")
            }
        }
    }

    @Test
    fun `an already readable colour is kept as is`() {
        val page = Color(0xFF101114)
        val light = Color(0xFF8BB9FF)
        assertEquals(light, ContinueActionColor.readableOn(light, page))
    }
}
