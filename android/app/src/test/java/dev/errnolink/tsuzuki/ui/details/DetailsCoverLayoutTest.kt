package dev.errnolink.tsuzuki.ui.details

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DetailsCoverLayoutTest {
    private val maxWidth = 300.dp
    private val maxHeight = 450.dp

    @Test
    fun `the cover keeps its own shape inside the header box`() {
        for (ratio in floatArrayOf(2f / 3f, 0.7f, 1f, 1.4f, 0.5f)) {
            val size = detailsCoverSize(ratio, maxWidth, maxHeight)
            assertEquals(ratio, size.width / size.height, 1e-3f, "ratio $ratio")
            assertTrue(size.width <= maxWidth && size.height <= maxHeight, "ratio $ratio overflows: $size")
            assertTrue(size.width == maxWidth || size.height == maxHeight, "ratio $ratio does not fill the box: $size")
        }
    }

    @Test
    fun `wide covers use the full width and tall covers the full height`() {
        assertEquals(maxWidth, detailsCoverSize(1.2f, maxWidth, maxHeight).width)
        assertEquals(maxHeight, detailsCoverSize(0.5f, maxWidth, maxHeight).height)
    }

    @Test
    fun `an unknown or broken ratio falls back to a portrait cover and extremes are tamed`() {
        assertEquals(2f / 3f, detailsCoverRatio(null), 1e-6f)
        assertEquals(2f / 3f, detailsCoverRatio(Float.NaN), 1e-6f)
        assertEquals(2f / 3f, detailsCoverRatio(0f), 1e-6f)
        assertTrue(detailsCoverRatio(0.1f) > 0.1f)
        assertTrue(detailsCoverRatio(10f) < 10f)
    }
}
