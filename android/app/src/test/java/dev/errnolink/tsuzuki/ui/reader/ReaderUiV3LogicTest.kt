package dev.errnolink.tsuzuki.ui.reader

import android.graphics.Bitmap
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReaderUiV3LogicTest {

    @Test
    fun `recordPageJumpOrigin triggers only at or above threshold, preserves anchor across jumps, and clears on return`() {
        // Adjacent page turns stay null
        assertNull(recordPageJumpOrigin(currentOriginPage = null, fromPage = 4, toPage = 5))
        assertNull(recordPageJumpOrigin(currentOriginPage = null, fromPage = 4, toPage = 6))

        // Jumping >= 3 pages forward or backward captures origin
        val forwardOrigin = recordPageJumpOrigin(currentOriginPage = null, fromPage = 4, toPage = 7)
        assertEquals(4, forwardOrigin)

        val backwardOrigin = recordPageJumpOrigin(currentOriginPage = null, fromPage = 12, toPage = 5)
        assertEquals(12, backwardOrigin)

        // Further jumps away from origin retain the initial anchor rather than overwriting it
        assertEquals(4, recordPageJumpOrigin(currentOriginPage = forwardOrigin, fromPage = 7, toPage = 14))
        assertEquals(4, recordPageJumpOrigin(currentOriginPage = forwardOrigin, fromPage = 14, toPage = 5))

        // Jumping back to the exact origin page clears the anchor
        assertNull(recordPageJumpOrigin(currentOriginPage = forwardOrigin, fromPage = 14, toPage = 4))
    }

    @Test
    fun `storyboard sample indices span full chapter monotonically and viewport window stays inside unit range`() {
        // Small chapter preserves every page
        assertEquals(listOf(0, 1, 2, 3, 4), computeStoryboardSampleIndices(totalPages = 5, maxSamples = 12))

        // Large chapter samples evenly from page 0 to last page
        val largeSamples = computeStoryboardSampleIndices(totalPages = 48, maxSamples = 12)
        assertEquals(12, largeSamples.size)
        assertEquals(0, largeSamples.first())
        assertEquals(47, largeSamples.last())
        assertTrue(largeSamples.zipWithNext().all { (a, b) -> a < b })

        // Viewport window never extends past 1f regardless of progress or oversized viewportFraction
        val atEnd = computeStoryboardViewportWindow(progress = 1.4f, viewportFraction = 0.25f)
        assertEquals(0.25f, atEnd.widthFraction, 0.0001f)
        assertEquals(1.0f, atEnd.startFraction + atEnd.widthFraction, 0.0001f)

        val clampedSmall = computeStoryboardViewportWindow(progress = -0.2f, viewportFraction = 0.01f)
        assertEquals(0f, clampedSmall.startFraction, 0.0001f)
        assertEquals(0.12f, clampedSmall.widthFraction, 0.0001f)

        assertEquals(0, resolveWebtoonTargetPageFromFraction(0f, 30))
        assertEquals(29, resolveWebtoonTargetPageFromFraction(1f, 30))
        assertEquals(15, resolveWebtoonTargetPageFromFraction(0.5f, 30))
    }

    @Test
    fun `dragCropRect preserves dimensions when moved past edges, prevents inversion, and maps exact pixels`() {
        val box = NormalizedCropRect(left = 0.6f, top = 0.6f, right = 0.9f, bottom = 0.9f)
        // Moving further bottom-right should slide without squishing width/height
        val moved = dragCropRect(box, CropDragHandle.Move, dxFraction = 0.5f, dyFraction = 0.5f)
        assertEquals(0.3f, moved.width, 0.0001f)
        assertEquals(0.3f, moved.height, 0.0001f)
        assertEquals(1.0f, moved.right, 0.0001f)
        assertEquals(1.0f, moved.bottom, 0.0001f)

        // Resizing left handle past right edge stops at minSpan
        val resizedLeft = dragCropRect(box, CropDragHandle.Left, dxFraction = 0.9f, dyFraction = 0f, minSpan = 0.12f)
        assertEquals(0.12f, resizedLeft.width, 0.0001f)
        assertTrue(resizedLeft.left < resizedLeft.right)

        // Pixel mapping against asymmetrical manga scan dimensions
        val px = mapCropRectToPixels(NormalizedCropRect(0.1f, 0.25f, 0.6f, 0.75f), bitmapWidth = 1000, bitmapHeight = 1600)
        assertEquals(PixelCropBounds(x = 100, y = 400, width = 500, height = 800), px)
    }

    @Test
    fun `lighting warmth color scales alpha monotonically and round-trips safely`() {
        assertEquals(0, lightingWarmthColor(0))
        val c30 = lightingWarmthColor(30)
        val c80 = lightingWarmthColor(80)
        assertTrue(((c80 ushr 24) and 0xFF) > ((c30 ushr 24) and 0xFF))
        assertEquals(255, (c80 ushr 16) and 0xFF)
        assertEquals(166, (c80 ushr 8) and 0xFF)
        assertEquals(72, c80 and 0xFF)
        assertEquals(80, warmthPercentFromColor(c80))
        assertEquals(0, warmthPercentFromColor(0x6600AAFF))

        assertEquals("System", formatBrightnessLabel(0, "System"))
        assertEquals("-45%", formatBrightnessLabel(-45, "System"))
        assertEquals("+60%", formatBrightnessLabel(60, "System"))
    }

    @Test
    fun `cropBitmapSafely copies HARDWARE bitmaps to ARGB_8888, crops pixels, and recycles temporary copy`() {
        val softCopy = mockk<Bitmap>(relaxed = true) {
            every { isRecycled } returns false
            every { width } returns 1000
            every { height } returns 1500
            every { config } returns Bitmap.Config.ARGB_8888
        }
        val hwBitmap = mockk<Bitmap> {
            every { isRecycled } returns false
            every { width } returns 1000
            every { height } returns 1500
            every { config } returns Bitmap.Config.HARDWARE
            every { copy(Bitmap.Config.ARGB_8888, false) } returns softCopy
        }
        val croppedResult = mockk<Bitmap>(relaxed = true)
        mockkStatic(Bitmap::class)
        try {
            every { Bitmap.createBitmap(softCopy, 100, 300, 800, 900) } returns croppedResult
            val result = cropBitmapSafely(
                hwBitmap,
                NormalizedCropRect(left = 0.1f, top = 0.2f, right = 0.9f, bottom = 0.8f),
            )
            assertSame(croppedResult, result)
            verify(exactly = 1) { softCopy.recycle() }
        } finally {
            unmockkStatic(Bitmap::class)
        }
    }

    @Test
    fun `hitTestCropHandle prioritizes corners over edges, detects all edges, interior move, and outside null`() {
        val rect = NormalizedCropRect(left = 0.2f, top = 0.2f, right = 0.8f, bottom = 0.8f)
        val w = 1000f
        val h = 1000f
        val r = 40f

        // Corners win even near edge overlap
        assertEquals(CropDragHandle.TopLeft, hitTestCropHandle(210f, 205f, rect, w, h, r))
        assertEquals(CropDragHandle.TopRight, hitTestCropHandle(795f, 205f, rect, w, h, r))
        assertEquals(CropDragHandle.BottomLeft, hitTestCropHandle(205f, 795f, rect, w, h, r))
        assertEquals(CropDragHandle.BottomRight, hitTestCropHandle(805f, 805f, rect, w, h, r))

        // Edges along midpoints
        assertEquals(CropDragHandle.Top, hitTestCropHandle(500f, 210f, rect, w, h, r))
        assertEquals(CropDragHandle.Bottom, hitTestCropHandle(500f, 790f, rect, w, h, r))
        assertEquals(CropDragHandle.Left, hitTestCropHandle(210f, 500f, rect, w, h, r))
        assertEquals(CropDragHandle.Right, hitTestCropHandle(790f, 500f, rect, w, h, r))

        // Interior triggers Move, outside returns null
        assertEquals(CropDragHandle.Move, hitTestCropHandle(500f, 500f, rect, w, h, r))
        assertNull(hitTestCropHandle(100f, 100f, rect, w, h, r))
    }

    @Test
    fun `vertical webtoon minimap stays bounded and continuous across page seams`() {
        val fallbackPos = resolveWebtoonMinimapPosition(currentPage = 4, totalPages = 10, progress = 0f)
        assertEquals(3, fallbackPos.centerPageIndex)
        assertEquals(0f, fallbackPos.inPageFraction, 0.001f)

        val midPos = resolveWebtoonMinimapPosition(currentPage = 4, totalPages = 10, progress = 0.35f)
        assertEquals(3, midPos.centerPageIndex)
        assertEquals(0.5f, midPos.inPageFraction, 0.001f)

        // End of page k vs start of page k+1 must produce the exact same absolute lens position
        val prevH = 300f
        val currH = 420f
        val nextH = 360f
        val stripH = 640f
        val lensH = 200f
        val endOfPrev = computeVerticalViewportRibbonLayout(
            prevHeightPx = 0f,
            currHeightPx = currH,
            nextHeightPx = nextH,
            inPageFraction = 1f,
            stripHeightPx = stripH,
            lensHeightPx = lensH,
        )
        val startOfNext = computeVerticalViewportRibbonLayout(
            prevHeightPx = currH,
            currHeightPx = nextH,
            nextHeightPx = prevH,
            inPageFraction = 0f,
            stripHeightPx = stripH,
            lensHeightPx = lensH,
        )
        // Absolute position in stack is lensTopPx - stackOffsetYPx
        val absEndOfPrev = endOfPrev.lensTopPx - endOfPrev.stackOffsetYPx
        val absStartOfNext = startOfNext.lensTopPx - startOfNext.stackOffsetYPx
        assertEquals(currH, absEndOfPrev, 0.01f)
        assertEquals(currH, absStartOfNext, 0.01f)

        // Lens stays strictly inside strip bounds at extremes
        val atTop = computeVerticalViewportRibbonLayout(0f, 400f, 400f, 0f, stripH, lensH)
        assertEquals(0f, atTop.lensTopPx, 0.01f)
        val atBottom = computeVerticalViewportRibbonLayout(400f, 400f, 0f, 1f, stripH, lensH)
        assertEquals(stripH - lensH, atBottom.lensTopPx, 0.01f)
    }

    @Test
    fun `multi-page vertical ribbon fills tall strip on page 1 and tracks mid-stack smoothly`() {
        // Seven short pages (~110px each -> 770px total) easily fill a 640px vertical strip even on page 1
        val pageHeights = List(7) { 110f }
        val stripH = 640f
        val lensH = 200f

        val atFirstPageStart = computeMultiPageVerticalRibbonLayout(
            pageHeightsPx = pageHeights,
            currentSlotIndex = 0,
            inPageFraction = 0f,
            stripHeightPx = stripH,
            lensHeightPx = lensH,
        )
        assertEquals(0f, atFirstPageStart.stackOffsetYPx, 0.01f)
        assertEquals(0f, atFirstPageStart.lensTopPx, 0.01f)

        val endOfSlot2 = computeMultiPageVerticalRibbonLayout(
            pageHeightsPx = pageHeights,
            currentSlotIndex = 2,
            inPageFraction = 1f,
            stripHeightPx = stripH,
            lensHeightPx = lensH,
        )
        val startOfSlot3 = computeMultiPageVerticalRibbonLayout(
            pageHeightsPx = pageHeights,
            currentSlotIndex = 3,
            inPageFraction = 0f,
            stripHeightPx = stripH,
            lensHeightPx = lensH,
        )
        assertEquals(
            endOfSlot2.lensTopPx - endOfSlot2.stackOffsetYPx,
            startOfSlot3.lensTopPx - startOfSlot3.stackOffsetYPx,
            0.01f,
        )
    }

    @Test
    fun `tall webtoon strips slice top thumbnail region while normal pages sample proportionally`() {
        val tall = computeTallThumbRegion(outWidth = 720, outHeight = 9000)
        assertNotNull(tall)
        assertEquals(PixelCropBounds(0, 0, 720, 1296), tall)
        assertEquals(1, computeThumbSampleSize(tall!!.width, tall.height, targetWidthPx = 420))

        // Full-strip decode stays reasonably sharp horizontally without hitting bitmap limits
        assertEquals(4, computeThumbSampleSize(outWidth = 720, outHeight = 9000, targetWidthPx = 220, maxHeightPx = 2400))

        // Standard portrait manga page does not crop and samples by column width
        assertNull(computeTallThumbRegion(outWidth = 1200, outHeight = 1800))
        assertEquals(2, computeThumbSampleSize(outWidth = 1200, outHeight = 1800, targetWidthPx = 420))
    }
}
