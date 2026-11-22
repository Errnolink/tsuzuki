// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): app/src/test/java/eu/kanade/tachiyomi/util/system/GLUtilTest.kt (+#3525)
package dev.errnolink.tsuzuki.reader.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GlLimitsTest {

    @Test
    fun `maxCanvasTextureSize stays within the safe canvas dimension and the minimum default`() {
        assertTrue(GlLimits.maxCanvasTextureSize <= GlLimits.SAFE_CANVAS_BITMAP_DIMENSION)
        assertTrue(GlLimits.maxCanvasTextureSize >= 2048)
    }

    @Test
    fun `square ARGB_8888 bitmap at maxCanvasTextureSize does not exceed MAX_CANVAS_BITMAP_BYTES`() {
        val maxDim = GlLimits.maxCanvasTextureSize.toLong()
        val maxBytes = GlLimits.canvasBitmapBytes(maxDim.toInt(), maxDim.toInt())
        assertTrue(maxBytes <= GlLimits.MAX_CANVAS_BITMAP_BYTES) {
            "Max canvas bitmap bytes ($maxBytes) must not exceed MAX_CANVAS_BITMAP_BYTES (${GlLimits.MAX_CANVAS_BITMAP_BYTES})"
        }
    }

    @Test
    fun `canvas byte count pads the row like a hardware bitmap`() {
        // The Android 11 crash: a 5368x4881 page drawn with a 5376 pixel row.
        assertEquals(104_961_024L, GlLimits.canvasBitmapBytes(5368, 4881))
        assertEquals(104_857_600L, GlLimits.canvasBitmapBytes(5120, 5120))
        assertEquals(2L * 128 * 10 * 8, GlLimits.canvasBitmapBytes(129, 10, 8))
    }

    @Test
    fun `page over the padded canvas budget is caught where the unpadded one would slip through`() {
        // Neko #3525: 5100x5140 takes 104,856,000 bytes unpadded, which would pass a naive
        // width * height * 4 check but overflows the canvas once the row is padded to 5120.
        val unpadded = 5100L * 5140 * 4
        val padded = GlLimits.canvasBitmapBytes(5100, 5140)
        assertTrue(unpadded <= GlLimits.MAX_CANVAS_BITMAP_BYTES)
        assertTrue(padded > GlLimits.MAX_CANVAS_BITMAP_BYTES)
    }
}
