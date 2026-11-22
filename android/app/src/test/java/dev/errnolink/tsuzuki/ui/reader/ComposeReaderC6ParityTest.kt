package dev.errnolink.tsuzuki.ui.reader

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.reader.TurnEdge
import dev.errnolink.tsuzuki.reader.engine.decode.chooseSmartBackgroundColor
import dev.errnolink.tsuzuki.reader.engine.viewer.ViewerNavigation.NavigationRegion
import dev.errnolink.tsuzuki.reader.engine.viewer.shrinkEdgeCoord
import dev.errnolink.tsuzuki.ui.reader.viewer.calculateLandscapeZoomScale
import dev.errnolink.tsuzuki.ui.reader.viewer.calculateWebtoonAspectHorizontalPadding
import dev.errnolink.tsuzuki.ui.reader.viewer.dispatchNavigation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.WebtoonScaleType
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ComposeReaderC6ParityTest {

    @Test
    fun `shrinkEdgeCoord shrinks interior thresholds toward outer edges and leaves boundaries unchanged`() {
        assertEquals(0.33f, shrinkEdgeCoord(0.33f, smaller = false), 0.0001f)
        assertEquals(0f, shrinkEdgeCoord(0f, smaller = true), 0.0001f)
        assertEquals(1f, shrinkEdgeCoord(1f, smaller = true), 0.0001f)
        assertEquals(0.5f, shrinkEdgeCoord(0.5f, smaller = true), 0.0001f)
        assertEquals(0.2475f, shrinkEdgeCoord(0.33f, smaller = true), 0.0001f)
        assertEquals(0.745f, shrinkEdgeCoord(0.66f, smaller = true), 0.0001f)
    }

    @Test
    fun `resolveDoublePageGap returns gutter only when double page center margin is active and uncropped`() {
        assertEquals(0, resolveDoublePageGap(PagerConfig.CenterMarginType.NONE, cropBorders = false))
        assertEquals(6, resolveDoublePageGap(PagerConfig.CenterMarginType.DOUBLE_PAGE_CENTER_MARGIN, cropBorders = false))
        assertEquals(0, resolveDoublePageGap(PagerConfig.CenterMarginType.WIDE_PAGE_CENTER_MARGIN, cropBorders = false))
        assertEquals(6, resolveDoublePageGap(PagerConfig.CenterMarginType.DOUBLE_AND_WIDE_CENTER_MARGIN, cropBorders = false))
        assertEquals(0, resolveDoublePageGap(PagerConfig.CenterMarginType.DOUBLE_AND_WIDE_CENTER_MARGIN, cropBorders = true))
    }

    @Test
    fun `resolveWebtoonScaleRatio gates continuous vertical aspect scaling behind longStripGapSmartScale`() {
        assertEquals(WebtoonScaleType.R16_9.ratio, resolveWebtoonScaleRatio(hasGaps = false, smartScale = false, scaleType = WebtoonScaleType.R16_9), 0.0001f)
        assertEquals(0f, resolveWebtoonScaleRatio(hasGaps = true, smartScale = false, scaleType = WebtoonScaleType.R16_9), 0.0001f)
        assertEquals(WebtoonScaleType.R16_9.ratio, resolveWebtoonScaleRatio(hasGaps = true, smartScale = true, scaleType = WebtoonScaleType.R16_9), 0.0001f)
        assertEquals(0f, resolveWebtoonScaleRatio(hasGaps = false, smartScale = true, scaleType = WebtoonScaleType.FIT), 0.0001f)
    }

    @Test
    fun `calculateWebtoonAspectHorizontalPadding centers wide screens to desired ratio and stays zero on narrow screens`() {
        assertEquals(0.dp, calculateWebtoonAspectHorizontalPadding(400.dp, 800.dp, WebtoonScaleType.R16_9.ratio))
        val padding = calculateWebtoonAspectHorizontalPadding(1000.dp, 800.dp, WebtoonScaleType.R4_3.ratio)
        assertEquals(200f, padding.value, 0.01f)
    }

    @Test
    fun `calculateLandscapeZoomScale distinguishes fit and double modes`() {
        assertEquals(2f, calculateLandscapeZoomScale(scaleDouble = true, boundsHeight = 600f, viewportHeight = 1200f), 0.001f)
        assertEquals(2.5f, calculateLandscapeZoomScale(scaleDouble = false, boundsHeight = 400f, viewportHeight = 1000f), 0.001f)
        assertEquals(3f, calculateLandscapeZoomScale(scaleDouble = false, boundsHeight = 200f, viewportHeight = 1000f), 0.001f)
        assertEquals(1f, calculateLandscapeZoomScale(scaleDouble = false, boundsHeight = 0f, viewportHeight = 1000f), 0.001f)
    }

    @Test
    fun `resolveTapTurnEdge maps horizontal and vertical tap positions to screen edges`() {
        assertEquals(TurnEdge.Left, resolveTapTurnEdge(horizontalPager = true, xFraction = 0.2f, yFraction = 0.8f))
        assertEquals(TurnEdge.Right, resolveTapTurnEdge(horizontalPager = true, xFraction = 0.8f, yFraction = 0.2f))
        assertEquals(TurnEdge.Top, resolveTapTurnEdge(horizontalPager = false, xFraction = 0.1f, yFraction = 0.3f))
        assertEquals(TurnEdge.Bottom, resolveTapTurnEdge(horizontalPager = false, xFraction = 0.9f, yFraction = 0.7f))
    }

    @Test
    fun `dispatchNavigation reports tap turn coordinates for navigation regions and skips menu`() {
        var recorded: Triple<Boolean, Float, Float>? = null
        dispatchNavigation(
            action = NavigationRegion.MENU,
            isRtl = false,
            menuVisible = false,
            onToggleMenu = {},
            onNavigateAdjacent = {},
            xFraction = 0.5f,
            yFraction = 0.5f,
            onTapTurn = { f, x, y -> recorded = Triple(f, x, y) },
        )
        assertNull(recorded)

        dispatchNavigation(
            action = NavigationRegion.LEFT,
            isRtl = true,
            menuVisible = false,
            onToggleMenu = {},
            onNavigateAdjacent = {},
            xFraction = 0.15f,
            yFraction = 0.65f,
            onTapTurn = { f, x, y -> recorded = Triple(f, x, y) },
        )
        assertEquals(Triple(true, 0.15f, 0.65f), recorded)
    }

    @Test
    fun `chooseSmartBackgroundColor copies HARDWARE bitmaps safely and recycles temporary copy`() {
        val softCopy = mockk<Bitmap>(relaxed = true) {
            every { isRecycled } returns false
            every { config } returns Bitmap.Config.ARGB_8888
            every { getPixel(any(), any()) } returns 0xFF101010.toInt()
        }
        val hwBitmap = mockk<Bitmap> {
            every { isRecycled } returns false
            every { width } returns 32
            every { height } returns 32
            every { config } returns Bitmap.Config.HARDWARE
            every { getPixel(any(), any()) } throws IllegalStateException("HARDWARE bitmap")
            every { copy(Bitmap.Config.ARGB_8888, false) } returns softCopy
        }

        val result = chooseSmartBackgroundColor(hwBitmap, Color.Gray)

        assertEquals(Color.Black, result)
        verify(exactly = 1) { softCopy.recycle() }
    }

    @Test
    fun `chooseSmartBackgroundColor falls back on recycled bitmap or null HARDWARE copy and detects white edges`() {
        val recycled = mockk<Bitmap> {
            every { isRecycled } returns true
        }
        assertEquals(Color.Gray, chooseSmartBackgroundColor(recycled, Color.Gray))

        val hwCopyFailed = mockk<Bitmap> {
            every { isRecycled } returns false
            every { width } returns 32
            every { height } returns 32
            every { config } returns Bitmap.Config.HARDWARE
            every { copy(Bitmap.Config.ARGB_8888, false) } returns null
        }
        assertEquals(Color.Gray, chooseSmartBackgroundColor(hwCopyFailed, Color.Gray))

        val lightSoftware = mockk<Bitmap> {
            every { isRecycled } returns false
            every { width } returns 32
            every { height } returns 32
            every { config } returns Bitmap.Config.ARGB_8888
            every { getPixel(any(), any()) } returns 0xFFF8F8F8.toInt()
        }
        assertEquals(Color.White, chooseSmartBackgroundColor(lightSoftware, Color.Gray))
    }
}
