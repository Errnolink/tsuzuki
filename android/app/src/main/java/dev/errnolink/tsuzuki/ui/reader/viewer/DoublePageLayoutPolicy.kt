// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): org/nekomanga/presentation/screens/reader/viewer/DoublePageLayoutPolicy.kt
package dev.errnolink.tsuzuki.ui.reader.viewer

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment

/** Type-safe representation of Neko reader image scale preferences. */
enum class ReaderScaleType(val value: Int) {
    FitScreen(1),
    Stretch(2),
    FitWidth(3),
    FitHeight(4),
    OriginalSize(5),
    SmartFit(6);

    companion object {
        fun fromPreference(value: Int): ReaderScaleType {
            return entries.firstOrNull { it.value == value } ?: FitScreen
        }
    }
}

/** Type-safe representation of Neko reader zoom start preferences. */
enum class ZoomStartPosition(val value: Int) {
    Auto(1),
    Left(2),
    Right(3),
    Center(4);

    companion object {
        fun fromPreference(value: Int): ZoomStartPosition {
            return entries.firstOrNull { it.value == value } ?: Auto
        }
    }
}

/** Pre-computed dimensions for a paired double-page spread. */
@Immutable
data class DoublePageDimensions(
    val maxHeight: Float,
    val w1: Float,
    val w2: Float,
    val totalWidth: Float,
)

/** Centralized calculation and placement policy for double-page spreads in Compose Pager. */
object DoublePageLayoutPolicy {

    /** Zoom start preference resolved to an edge; reading direction flips the Auto side. */
    internal enum class ZoomType {
        Left,
        Right,
        Center,
    }

    internal fun resolveZoomType(zoomStart: Int, isRtl: Boolean): ZoomType =
        when (zoomStart) {
            1 -> if (isRtl) ZoomType.Right else ZoomType.Left
            2 -> ZoomType.Left
            3 -> ZoomType.Right
            else -> ZoomType.Center
        }

    /** Calculates normalized composite dimensions for two pages with a gap. */
    fun calculateDimensions(
        fWidth: Float?,
        fHeight: Float?,
        sWidth: Float?,
        sHeight: Float?,
        gapPx: Float,
    ): DoublePageDimensions {
        val hasFirst = fWidth != null && fHeight != null && fWidth > 0f && fHeight > 0f
        val hasSecond = sWidth != null && sHeight != null && sWidth > 0f && sHeight > 0f

        return when {
            hasFirst && hasSecond -> {
                val maxHeight = maxOf(fHeight, sHeight)
                val w1 = fWidth * (maxHeight / fHeight)
                val w2 = sWidth * (maxHeight / sHeight)
                DoublePageDimensions(
                    maxHeight = maxHeight,
                    w1 = w1,
                    w2 = w2,
                    totalWidth = w1 + w2 + gapPx,
                )
            }
            hasFirst ->
                DoublePageDimensions(
                    maxHeight = fHeight,
                    w1 = fWidth,
                    w2 = 0f,
                    totalWidth = fWidth,
                )
            hasSecond ->
                DoublePageDimensions(
                    maxHeight = sHeight,
                    w1 = 0f,
                    w2 = sWidth,
                    totalWidth = sWidth,
                )
            else -> DoublePageDimensions(0f, 0f, 0f, 0f)
        }
    }

    /** Calculates the effective scale factor based on scale type and viewport constraints. */
    fun calculateScale(
        scaleType: ReaderScaleType,
        totalWidth: Float,
        maxHeight: Float,
        viewportWidth: Float,
        viewportHeight: Float,
    ): Float {
        if (totalWidth <= 0f || maxHeight <= 0f || viewportWidth <= 0f || viewportHeight <= 0f) {
            return 1f
        }
        return when (scaleType) {
            ReaderScaleType.FitHeight -> viewportHeight / maxHeight
            ReaderScaleType.FitWidth -> viewportWidth / totalWidth
            ReaderScaleType.OriginalSize -> 1f
            ReaderScaleType.SmartFit -> {
                val contentRatio = totalWidth / maxHeight
                val screenRatio = viewportWidth / viewportHeight
                if (contentRatio > screenRatio) {
                    viewportWidth / totalWidth
                } else {
                    viewportHeight / maxHeight
                }
            }
            ReaderScaleType.FitScreen,
            ReaderScaleType.Stretch ->
                minOf(1f, viewportWidth / totalWidth, viewportHeight / maxHeight)
        }
    }

    /**
     * Determines whether auto-zoom should be applied to a spread. Auto-zoom is only applied to true
     * wide spreads in Fit Screen mode on narrow (phone) viewports.
     */
    fun shouldAutoZoom(
        zoomEnabled: Boolean,
        scaleType: ReaderScaleType,
        isTrueSpread: Boolean,
        viewportWidth: Float,
        viewportHeight: Float,
    ): Boolean {
        if (!zoomEnabled || scaleType != ReaderScaleType.FitScreen) return false
        if (viewportWidth <= 0f || viewportHeight <= 0f) return false
        val isTabletLandscape = (viewportWidth / viewportHeight) >= 1.33f
        return isTrueSpread && !isTabletLandscape
    }

    /** Resolves the default horizontal alignment based on reading direction and zoom start. */
    fun resolveAlignment(
        zoomStart: ZoomStartPosition,
        isRtl: Boolean,
    ): Alignment {
        return when (zoomStart) {
            ZoomStartPosition.Auto ->
                if (isRtl) {
                    Alignment.CenterEnd
                } else {
                    Alignment.CenterStart
                }
            ZoomStartPosition.Left -> Alignment.CenterStart
            ZoomStartPosition.Right -> Alignment.CenterEnd
            ZoomStartPosition.Center -> Alignment.Center
        }
    }

    /**
     * Computes the alignment for the Row container.
     * - If overflowing horizontally and vertically: aligns to reading edge and top.
     * - If overflowing horizontally: aligns to reading edge, vertically centered.
     * - If overflowing vertically: aligns to top, horizontally centered.
     * - If fitting inside viewport: centered both ways.
     */
    fun calculateRowAlignment(
        doublePageAlignment: Alignment,
        renderedWidthPx: Float,
        renderedHeightPx: Float,
        viewportWidthPx: Float,
        viewportHeightPx: Float,
    ): Alignment {
        val isHorizontalOverflow = renderedWidthPx > viewportWidthPx
        val isVerticalOverflow = renderedHeightPx > viewportHeightPx

        return when {
            isHorizontalOverflow && isVerticalOverflow -> {
                when (doublePageAlignment) {
                    Alignment.CenterStart -> Alignment.TopStart
                    Alignment.CenterEnd -> Alignment.TopEnd
                    else -> Alignment.TopCenter
                }
            }
            isHorizontalOverflow -> doublePageAlignment
            isVerticalOverflow -> Alignment.TopCenter
            else -> Alignment.Center
        }
    }
}

internal fun resolveDoublePageAlignment(
    zoomStart: Int,
    isRtl: Boolean,
): Alignment =
    DoublePageLayoutPolicy.resolveAlignment(
        zoomStart = ZoomStartPosition.fromPreference(zoomStart),
        isRtl = isRtl,
    )
