// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): app/src/test/java/eu/kanade/tachiyomi/data/coil/CropBordersTransformationTest.kt
package dev.errnolink.tsuzuki.reader.engine.decode

import android.graphics.Bitmap
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CropBordersTransformationTest {

    private fun createPixelReader(
        width: Int,
        height: Int,
        pixelProvider: (x: Int, y: Int) -> Int,
    ): PixelRowColReader {
        return object : PixelRowColReader {
            override fun readRow(y: Int, width: Int, pixels: IntArray) {
                for (x in 0 until width) {
                    pixels[x] = pixelProvider(x, y)
                }
            }

            override fun readCol(x: Int, height: Int, pixels: IntArray) {
                for (y in 0 until height) {
                    pixels[y] = pixelProvider(x, y)
                }
            }
        }
    }

    @Test
    fun `calculateCropBounds trims monotone white margins on all sides`() {
        val width = 100
        val height = 100
        val white = 0xFFFFFFFF.toInt()
        val art = 0xFF555555.toInt()

        // 10px left/right margin, 15px top/bottom margin
        val reader =
            createPixelReader(width, height) { x, y ->
                if (x < 10 || x >= 90 || y < 15 || y >= 85) white else art
            }

        val bounds =
            CropBordersTransformation.calculateCropBounds(
                width = width,
                height = height,
                reader = reader,
                cropTopBottom = true,
                tolerance = 15,
            )

        assertNotNull(bounds)
        assertEquals(10, bounds!!.left)
        assertEquals(15, bounds.top)
        assertEquals(90, bounds.right)
        assertEquals(85, bounds.bottom)
        assertEquals(80, bounds.width)
        assertEquals(70, bounds.height)
    }

    @Test
    fun `calculateCropBounds with cropTopBottom false only trims horizontal borders in webtoon mode`() {
        val width = 100
        val height = 100
        val white = 0xFFFFFFFF.toInt()
        val art = 0xFF555555.toInt()

        val reader =
            createPixelReader(width, height) { x, y ->
                if (x < 12 || x >= 88 || y < 10 || y >= 90) white else art
            }

        val bounds =
            CropBordersTransformation.calculateCropBounds(
                width = width,
                height = height,
                reader = reader,
                cropTopBottom = false,
                tolerance = 15,
            )

        assertNotNull(bounds)
        assertEquals(12, bounds!!.left)
        assertEquals(0, bounds.top) // Top is NOT cropped
        assertEquals(88, bounds.right)
        assertEquals(100, bounds.bottom) // Bottom is NOT cropped
    }

    @Test
    fun `calculateCropBounds with black borders trims all sides`() {
        val width = 100
        val height = 100
        val black = 0xFF000000.toInt()
        val art = 0xFFDDDDDD.toInt()

        val reader =
            createPixelReader(width, height) { x, y ->
                if (x < 8 || x >= 92 || y < 12 || y >= 88) black else art
            }

        val bounds =
            CropBordersTransformation.calculateCropBounds(
                width = width,
                height = height,
                reader = reader,
                cropTopBottom = true,
                tolerance = 15,
            )

        assertNotNull(bounds)
        assertEquals(8, bounds!!.left)
        assertEquals(12, bounds.top)
        assertEquals(92, bounds.right)
        assertEquals(88, bounds.bottom)
    }

    @Test
    fun `calculateCropBounds with vintage aged cream paper crops margins`() {
        val width = 100
        val height = 100
        // Cream / aged paper color (~0.84 luminance)
        val agedPaper = 0xFFD8D8D0.toInt()
        val art = 0xFF222222.toInt()

        val reader =
            createPixelReader(width, height) { x, y ->
                if (x < 10 || x >= 90 || y < 10 || y >= 90) agedPaper else art
            }

        val bounds =
            CropBordersTransformation.calculateCropBounds(
                width = width,
                height = height,
                reader = reader,
                cropTopBottom = true,
                tolerance = 15,
            )

        assertNotNull(bounds)
        assertEquals(10, bounds!!.left)
        assertEquals(10, bounds.top)
        assertEquals(90, bounds.right)
        assertEquals(90, bounds.bottom)
    }

    @Test
    fun `calculateCropBounds with midtone borders does not crop`() {
        val width = 100
        val height = 100
        // 50% gray midtone
        val gray = 0xFF808080.toInt()
        val art = 0xFF222222.toInt()

        val reader =
            createPixelReader(width, height) { x, y ->
                if (x < 10 || x >= 90 || y < 10 || y >= 90) gray else art
            }

        val bounds =
            CropBordersTransformation.calculateCropBounds(
                width = width,
                height = height,
                reader = reader,
                cropTopBottom = true,
                tolerance = 15,
            )

        assertNotNull(bounds)
        assertEquals(0, bounds!!.left)
        assertEquals(0, bounds.top)
        assertEquals(100, bounds.right)
        assertEquals(100, bounds.bottom)
    }

    @Test
    fun `calculateCropBounds with alternating horizontal bands halts at row 1 and preserves artwork`() {
        val width = 100
        val height = 100
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()

        // Page has alternating black and white bands (e.g. speed lines or striped art cover)
        val reader = createPixelReader(width, height) { _, y -> if (y % 2 == 0) black else white }

        val bounds =
            CropBordersTransformation.calculateCropBounds(
                width = width,
                height = height,
                reader = reader,
                cropTopBottom = true,
                tolerance = 15,
            )

        assertNotNull(bounds)
        // Row 0 is black, but Row 1 is white. The scan must halt at row 1, NOT consume 25% of the
        // page!
        assertEquals(1, bounds!!.top)
    }

    @Test
    fun `calculateCropBounds with artwork touching edges does not crop`() {
        val width = 100
        val height = 100
        val art = 0xFF555555.toInt()

        val reader = createPixelReader(width, height) { _, _ -> art }

        val bounds =
            CropBordersTransformation.calculateCropBounds(
                width = width,
                height = height,
                reader = reader,
                cropTopBottom = true,
                tolerance = 15,
            )

        assertNotNull(bounds)
        assertEquals(0, bounds!!.left)
        assertEquals(0, bounds.top)
        assertEquals(100, bounds.right)
        assertEquals(100, bounds.bottom)
    }

    @Test
    fun `calculateCropBounds with non-uniform edge pixel row stops immediately`() {
        val width = 100
        val height = 100
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        val art = 0xFF555555.toInt()

        // Row 0 has a black pixel in the middle of white border (e.g. scanner speck or text)
        val reader =
            createPixelReader(width, height) { x, y ->
                if (y == 0 && x == 50) black
                else if (x < 10 || x >= 90 || y < 15 || y >= 85) white else art
            }

        val bounds =
            CropBordersTransformation.calculateCropBounds(
                width = width,
                height = height,
                reader = reader,
                cropTopBottom = true,
                tolerance = 15,
            )

        assertNotNull(bounds)
        assertEquals(0, bounds!!.top) // Stopped at row 0 because of non-uniform pixel
        assertEquals(10, bounds.left)
        assertEquals(90, bounds.right)
        assertEquals(85, bounds.bottom)
    }

    @Test
    fun `calculateCropBounds with transparent borders crops transparent margins`() {
        val width = 100
        val height = 100
        val transparent = 0x00000000
        val art = 0xFF555555.toInt()

        val reader =
            createPixelReader(width, height) { x, y ->
                if (x < 14 || x >= 86 || y < 10 || y >= 90) transparent else art
            }

        val bounds =
            CropBordersTransformation.calculateCropBounds(
                width = width,
                height = height,
                reader = reader,
                cropTopBottom = true,
                tolerance = 15,
            )

        assertNotNull(bounds)
        assertEquals(14, bounds!!.left)
        assertEquals(10, bounds.top)
        assertEquals(86, bounds.right)
        assertEquals(90, bounds.bottom)
    }

    @Test
    fun `calculateCropBounds with small image under 10px returns null`() {
        val reader = createPixelReader(8, 8) { _, _ -> 0xFFFFFFFF.toInt() }

        val bounds =
            CropBordersTransformation.calculateCropBounds(
                width = 8,
                height = 8,
                reader = reader,
                cropTopBottom = true,
            )

        assertNull(bounds)
    }

    @Test
    fun `calculateCropBounds caps scanning at 25 percent of dimensions`() {
        val width = 100
        val height = 100
        val white = 0xFFFFFFFF.toInt()

        // Entire image is white margin
        val reader = createPixelReader(width, height) { _, _ -> white }

        val bounds =
            CropBordersTransformation.calculateCropBounds(
                width = width,
                height = height,
                reader = reader,
                cropTopBottom = true,
                tolerance = 15,
            )

        assertNotNull(bounds)
        // 25% of 100 is 25
        assertEquals(25, bounds!!.left)
        assertEquals(25, bounds.top)
        assertEquals(75, bounds.right)
        assertEquals(75, bounds.bottom)
    }

    @Test
    fun `bitmap overload with a page under 10px returns null without pixel reads`() {
        // The Bitmap adapter branches on the bitmap dimensions before any getPixels call, which is
        // observable on the JVM where android.graphics methods are stubbed: a synthetic 8x8
        // bitmap must yield no crop rectangle.
        val bitmap =
            mockk<Bitmap> {
                every { width } returns 8
                every { height } returns 8
            }

        assertNull(CropBordersTransformation.calculateCropBounds(bitmap, cropTopBottom = true))
    }

    @Test
    fun `cacheKey separates crop configurations`() {
        val t1 = CropBordersTransformation(cropTopBottom = true, tolerance = 15)
        val t2 = CropBordersTransformation(cropTopBottom = false, tolerance = 15)
        val t3 = CropBordersTransformation(cropTopBottom = true, tolerance = 25)

        assertNotEquals(t1.cacheKey, t2.cacheKey)
        assertNotEquals(t1.cacheKey, t3.cacheKey)
        assertNotEquals(t2.cacheKey, t3.cacheKey)
    }

    @Test
    fun `pixelLuminance treats white black and transparent margins as expected`() {
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        val transparent = 0x00FFFFFF

        assertEquals(1.0f, CropBordersTransformation.pixelLuminance(white), 0.01f)
        assertEquals(0.0f, CropBordersTransformation.pixelLuminance(black), 0.01f)
        assertEquals(1.0f, CropBordersTransformation.pixelLuminance(transparent), 0.01f)
    }
}
