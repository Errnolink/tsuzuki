// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): app/src/test/java/eu/kanade/tachiyomi/data/coil/RotateWidePageTransformationTest.kt
package dev.errnolink.tsuzuki.reader.engine.decode

import android.graphics.Bitmap
import coil3.size.Size as CoilSize
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RotateWidePageTransformationTest {

    @Test
    fun `shouldRotate returns true when rotateWide is true and width is greater than height`() {
        val result =
            RotateWidePageTransformation.shouldRotate(
                width = 1920,
                height = 1080,
                rotateWide = true,
            )
        assertTrue(result)
    }

    @Test
    fun `shouldRotate returns false when rotateWide is false`() {
        val result =
            RotateWidePageTransformation.shouldRotate(
                width = 1920,
                height = 1080,
                rotateWide = false,
            )
        assertFalse(result)
    }

    @Test
    fun `shouldRotate returns false when image is tall or portrait`() {
        val result =
            RotateWidePageTransformation.shouldRotate(
                width = 1080,
                height = 1920,
                rotateWide = true,
            )
        assertFalse(result)
    }

    @Test
    fun `shouldRotate returns false when image is square`() {
        val result =
            RotateWidePageTransformation.shouldRotate(
                width = 1000,
                height = 1000,
                rotateWide = true,
            )
        assertFalse(result)
    }

    @Test
    fun `shouldRotate returns false when aspect ratio is at or below MIN_WIDE_RATIO threshold`() {
        // 1001x1000 is 1.001 (near square) -> false
        assertFalse(RotateWidePageTransformation.shouldRotate(1001, 1000, true))
        // 1050x1000 is exactly 1.05 (MIN_WIDE_RATIO boundary) -> false
        assertFalse(RotateWidePageTransformation.shouldRotate(1050, 1000, true))
    }

    @Test
    fun `shouldRotate returns true when aspect ratio strictly exceeds MIN_WIDE_RATIO threshold`() {
        // 1051x1000 is 1.051 (> 1.05f) -> true
        assertTrue(RotateWidePageTransformation.shouldRotate(1051, 1000, true))
        // 1100x1000 is 1.10 -> true
        assertTrue(RotateWidePageTransformation.shouldRotate(1100, 1000, true))
    }

    @Test
    fun `shouldRotate boundary returns false when width is less than height`() {
        assertFalse(RotateWidePageTransformation.shouldRotate(1000, 1001, true))
    }

    @Test
    fun `shouldRotate handles 1x1 image as not wide`() {
        assertFalse(RotateWidePageTransformation.shouldRotate(1, 1, true))
    }

    @Test
    fun `shouldRotate returns true for ultra-high-resolution double spreads`() {
        assertTrue(RotateWidePageTransformation.shouldRotate(8000, 4500, true))
    }

    @Test
    fun `shouldRotate returns false when dimensions are zero or negative`() {
        assertFalse(RotateWidePageTransformation.shouldRotate(0, 100, true))
        assertFalse(RotateWidePageTransformation.shouldRotate(100, 0, true))
        assertFalse(RotateWidePageTransformation.shouldRotate(-100, 50, true))
        assertFalse(RotateWidePageTransformation.shouldRotate(100, -50, true))
        assertFalse(RotateWidePageTransformation.shouldRotate(-100, -100, true))
    }

    @Test
    fun `rotationDegrees returns 90f when reverse is false`() {
        assertEquals(90f, RotateWidePageTransformation.rotationDegrees(reverse = false), 0f)
    }

    @Test
    fun `rotationDegrees returns -90f when reverse is true`() {
        assertEquals(-90f, RotateWidePageTransformation.rotationDegrees(reverse = true), 0f)
    }

    @Test
    fun `given rotateWide true and portrait viewport when evaluating shouldRotateForViewport then returns true`() {
        assertTrue(
            RotateWidePageTransformation.shouldRotateForViewport(
                rotateWide = true,
                viewportWidth = 1080f,
                viewportHeight = 1920f,
            )
        )
    }

    @Test
    fun `given rotateWide true and landscape viewport when evaluating shouldRotateForViewport then returns false`() {
        assertFalse(
            RotateWidePageTransformation.shouldRotateForViewport(
                rotateWide = true,
                viewportWidth = 1920f,
                viewportHeight = 1080f,
            )
        )
    }

    @Test
    fun `given rotateWide true and square viewport when evaluating shouldRotateForViewport then returns false`() {
        assertFalse(
            RotateWidePageTransformation.shouldRotateForViewport(
                rotateWide = true,
                viewportWidth = 1080f,
                viewportHeight = 1080f,
            )
        )
    }

    @Test
    fun `given rotateWide false and portrait viewport when evaluating shouldRotateForViewport then returns false`() {
        assertFalse(
            RotateWidePageTransformation.shouldRotateForViewport(
                rotateWide = false,
                viewportWidth = 1080f,
                viewportHeight = 1920f,
            )
        )
    }

    @Test
    fun `given invalid or non-positive viewport dimensions when evaluating shouldRotateForViewport then returns false`() {
        assertFalse(RotateWidePageTransformation.shouldRotateForViewport(true, 0f, 1920f))
        assertFalse(RotateWidePageTransformation.shouldRotateForViewport(true, 1080f, 0f))
        assertFalse(RotateWidePageTransformation.shouldRotateForViewport(true, -100f, 500f))
        assertFalse(RotateWidePageTransformation.shouldRotateForViewport(true, 500f, -100f))
        assertFalse(RotateWidePageTransformation.shouldRotateForViewport(true, 0f, 0f))
    }

    // region Transform Short-Circuit Tests

    @Test
    fun `given recycled bitmap when transform called then returns input instance directly without rotating`() =
        runTest {
            val transformation = RotateWidePageTransformation(rotateWide = true)
            val mockBitmap =
                mockk<Bitmap> {
                    every { width } returns 2000
                    every { height } returns 1000
                    every { isRecycled } returns true
                }

            val result = transformation.transform(mockBitmap, CoilSize.ORIGINAL)

            assertSame(mockBitmap, result)
        }

    @Test
    fun `given portrait bitmap when transform called then returns input instance directly without rotating`() =
        runTest {
            val transformation = RotateWidePageTransformation(rotateWide = true)
            val mockBitmap =
                mockk<Bitmap> {
                    every { width } returns 1080
                    every { height } returns 1920
                    every { isRecycled } returns false
                }

            val result = transformation.transform(mockBitmap, CoilSize.ORIGINAL)

            assertSame(mockBitmap, result)
        }

    @Test
    fun `given square bitmap when transform called then returns input instance directly without rotating`() =
        runTest {
            val transformation = RotateWidePageTransformation(rotateWide = true)
            val mockBitmap =
                mockk<Bitmap> {
                    every { width } returns 1000
                    every { height } returns 1000
                    every { isRecycled } returns false
                }

            val result = transformation.transform(mockBitmap, CoilSize.ORIGINAL)

            assertSame(mockBitmap, result)
        }

    @Test
    fun `given aspect ratio at or below MIN_WIDE_RATIO when transform called then returns input instance directly without rotating`() =
        runTest {
            val transformation = RotateWidePageTransformation(rotateWide = true)
            val mockBitmap =
                mockk<Bitmap> {
                    every { width } returns 1050
                    every { height } returns 1000
                    every { isRecycled } returns false
                }

            val result = transformation.transform(mockBitmap, CoilSize.ORIGINAL)

            assertSame(mockBitmap, result)
        }

    @Test
    fun `given rotateWide false and wide bitmap when transform called then returns input instance directly without rotating`() =
        runTest {
            val transformation = RotateWidePageTransformation(rotateWide = false)
            val mockBitmap =
                mockk<Bitmap> {
                    every { width } returns 2000
                    every { height } returns 1000
                    every { isRecycled } returns false
                }

            val result = transformation.transform(mockBitmap, CoilSize.ORIGINAL)

            assertSame(mockBitmap, result)
        }

    @Test
    fun `given hardware bitmap when copy fails then returns original input without crashing`() =
        runTest {
            // Neko #3542 intent: a HARDWARE bitmap that cannot be copied must not take the
            // rotation path (Bitmap.createBitmap on a HARDWARE bitmap throws).
            val transformation = RotateWidePageTransformation(rotateWide = true)
            val mockBitmap =
                mockk<Bitmap> {
                    every { width } returns 2000
                    every { height } returns 1000
                    every { isRecycled } returns false
                    every { config } returns Bitmap.Config.HARDWARE
                    every { copy(Bitmap.Config.ARGB_8888, false) } returns null
                }

            val result = transformation.transform(mockBitmap, CoilSize.ORIGINAL)

            assertSame(mockBitmap, result)
        }

    // endregion
}
