package dev.errnolink.tsuzuki.ui.reader

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Updates the "Return to p.N" anchor after a non-adjacent navigation jump.
 *
 * Keeps any existing anchor while the user continues jumping around, clears it when returning to
 * the origin page, and only creates a new anchor once a jump spans at least [threshold] pages.
 */
fun recordPageJumpOrigin(
    currentOriginPage: Int?,
    fromPage: Int,
    toPage: Int,
    threshold: Int = 3,
): Int? {
    if (toPage <= 0 || fromPage <= 0) return currentOriginPage
    if (currentOriginPage != null) {
        return if (toPage == currentOriginPage) null else currentOriginPage
    }
    return if (abs(toPage - fromPage) >= threshold) fromPage else null
}

/** Normalized horizontal slice `[startFraction, startFraction + widthFraction]` inside `0f..1f`. */
data class StoryboardWindow(
    val startFraction: Float,
    val widthFraction: Float,
)

/**
 * Computes the Webtoon storyboard viewport indicator window inside `[0f, 1f]` so the active
 * region stays bounded inside the ribbon at both ends.
 */
fun computeStoryboardViewportWindow(
    progress: Float,
    viewportFraction: Float,
): StoryboardWindow {
    val width = viewportFraction.coerceIn(0.12f, 0.42f)
    val start = progress.coerceIn(0f, 1f) * (1f - width)
    return StoryboardWindow(startFraction = start, widthFraction = width)
}

/**
 * Selects up to [maxSamples] evenly distributed 0-based page indices spanning `[0, totalPages - 1]`
 * for the Webtoon storyboard ribbon.
 */
fun computeStoryboardSampleIndices(
    totalPages: Int,
    maxSamples: Int = 12,
): List<Int> {
    if (totalPages <= 0 || maxSamples <= 0) return emptyList()
    if (totalPages <= maxSamples) return (0 until totalPages).toList()
    if (maxSamples == 1) return listOf(0)
    val last = totalPages - 1
    return (0 until maxSamples).map { slot ->
        ((slot.toFloat() / (maxSamples - 1)) * last).roundToInt().coerceIn(0, last)
    }
}

/** Maps a horizontal fraction `0f..1f` along the storyboard ribbon to a 0-based page index. */
fun resolveWebtoonTargetPageFromFraction(fraction: Float, totalPages: Int): Int {
    if (totalPages <= 1) return 0
    return (fraction.coerceIn(0f, 1f) * (totalPages - 1)).roundToInt().coerceIn(0, totalPages - 1)
}

/** Current page index and intra-page scroll offset `[0f, 1f]` for the zoomed Webtoon minimap. */
data class WebtoonMinimapPosition(
    val centerPageIndex: Int,
    val inPageFraction: Float,
    val effectiveProgress: Float,
)

fun resolveWebtoonMinimapPosition(
    currentPage: Int,
    totalPages: Int,
    progress: Float,
): WebtoonMinimapPosition {
    val safeTotal = totalPages.coerceAtLeast(1)
    val clampedPageIdx = (currentPage - 1).coerceIn(0, safeTotal - 1)
    val effectiveProgress = if (progress > 0f || currentPage <= 1) {
        progress.coerceIn(0f, 1f)
    } else {
        (clampedPageIdx.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f)
    }
    val exactPos = (effectiveProgress * safeTotal).coerceIn(0f, (safeTotal - 0.0001f).coerceAtLeast(0f))
    val centerIdx = exactPos.toInt().coerceIn(0, safeTotal - 1)
    val inPage = (exactPos - centerIdx).coerceIn(0f, 1f)
    return WebtoonMinimapPosition(centerIdx, inPage, effectiveProgress)
}

data class VerticalViewportRibbonLayout(
    val stackOffsetYPx: Float,
    val lensTopPx: Float,
    val lensHeightPx: Float,
)

/**
 * Computes vertical stack translation and zoomed viewport lens position inside a vertical strip of
 * [stripHeightPx] across arbitrary surrounding [pageHeightsPx] where [currentSlotIndex] marks
 * the active page. Continuous across page boundaries.
 */
fun computeMultiPageVerticalRibbonLayout(
    pageHeightsPx: List<Float>,
    currentSlotIndex: Int,
    inPageFraction: Float,
    stripHeightPx: Float,
    lensHeightPx: Float,
): VerticalViewportRibbonLayout {
    val clampedStripH = stripHeightPx.coerceAtLeast(1f)
    if (pageHeightsPx.isEmpty()) {
        val effectiveLensH = lensHeightPx.coerceIn(24f, clampedStripH)
        return VerticalViewportRibbonLayout(0f, 0f, effectiveLensH)
    }
    val safeIdx = currentSlotIndex.coerceIn(0, pageHeightsPx.lastIndex)
    val precedingH = pageHeightsPx.take(safeIdx).sumOf { it.coerceAtLeast(0f).toDouble() }.toFloat()
    val currH = pageHeightsPx[safeIdx].coerceAtLeast(0f)
    val totalStackH = pageHeightsPx.sumOf { it.coerceAtLeast(0f).toDouble() }.toFloat().coerceAtLeast(1f)
    val effectiveLensH = lensHeightPx.coerceIn(24f, clampedStripH)
    val maxLensTopInStack = (totalStackH - effectiveLensH).coerceAtLeast(0f)
    val lensTopInStack = (precedingH + inPageFraction.coerceIn(0f, 1f) * currH)
        .coerceIn(0f, maxLensTopInStack)
    val idealScroll = (lensTopInStack + effectiveLensH / 2f) - clampedStripH / 2f
    val scrollY = idealScroll.coerceIn(0f, (totalStackH - clampedStripH).coerceAtLeast(0f))
    val stackOffsetYPx = -scrollY
    val lensTopPx = (lensTopInStack - scrollY).coerceIn(0f, (clampedStripH - effectiveLensH).coerceAtLeast(0f))
    return VerticalViewportRibbonLayout(
        stackOffsetYPx = stackOffsetYPx,
        lensTopPx = lensTopPx,
        lensHeightPx = effectiveLensH,
    )
}

/**
 * Computes the vertical translation of `[prev, curr, next]` zoomed webtoon pages and the active
 * viewport lens position inside a strip of [stripHeightPx]. Continuous across page boundaries.
 */
fun computeVerticalViewportRibbonLayout(
    prevHeightPx: Float,
    currHeightPx: Float,
    nextHeightPx: Float,
    inPageFraction: Float,
    stripHeightPx: Float,
    lensHeightPx: Float,
): VerticalViewportRibbonLayout = computeMultiPageVerticalRibbonLayout(
    pageHeightsPx = listOf(prevHeightPx, currHeightPx, nextHeightPx),
    currentSlotIndex = 1,
    inPageFraction = inPageFraction,
    stripHeightPx = stripHeightPx,
    lensHeightPx = lensHeightPx,
)

private const val WARM_AMBER_R = 255
private const val WARM_AMBER_G = 166
private const val WARM_AMBER_B = 72
private const val WARM_AMBER_MAX_ALPHA = 0.35f

/**
 * Converts a 0..100 warmth percentage into an ARGB tint integer for `ReaderPreferences.colorFilterValue`.
 * Returns `0` when [warmthPercent] <= 0.
 */
fun lightingWarmthColor(warmthPercent: Int): Int {
    val pct = warmthPercent.coerceIn(0, 100)
    if (pct == 0) return 0
    val alpha = ((pct / 100f) * WARM_AMBER_MAX_ALPHA * 255f).roundToInt().coerceIn(1, 255)
    return (alpha shl 24) or (WARM_AMBER_R shl 16) or (WARM_AMBER_G shl 8) or WARM_AMBER_B
}

/**
 * Extracts a 0..100 warmth percentage from a saved filter tint color when it represents the warm
 * amber preset, or `0` when disabled/unrelated.
 */
fun warmthPercentFromColor(argb: Int): Int {
    val a = (argb ushr 24) and 0xFF
    if (a == 0) return 0
    val r = (argb ushr 16) and 0xFF
    val g = (argb ushr 8) and 0xFF
    val b = argb and 0xFF
    val matchesWarmAmber = abs(r - WARM_AMBER_R) <= 4 &&
        abs(g - WARM_AMBER_G) <= 4 &&
        abs(b - WARM_AMBER_B) <= 4
    if (!matchesWarmAmber) return 0
    return ((a / (WARM_AMBER_MAX_ALPHA * 255f)) * 100f).roundToInt().coerceIn(0, 100)
}

fun formatBrightnessLabel(value: Int, systemLabel: String): String = when {
    value == 0 -> systemLabel
    value > 0 -> "+$value%"
    else -> "$value%"
}

/** Normalized crop bounds inside `[0f, 1f]` of the rendered page bitmap. */
data class NormalizedCropRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    companion object {
        val Default = NormalizedCropRect(left = 0.08f, top = 0.22f, right = 0.92f, bottom = 0.72f)
        val FullPage = NormalizedCropRect(left = 0f, top = 0f, right = 1f, bottom = 1f)
    }
}

enum class CropDragHandle {
    Move,
    Top,
    Bottom,
    Left,
    Right,
    TopLeft,
    TopRight,
    BottomLeft,
    BottomRight,
}

data class PixelCropBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

/** Clamps a normalized crop rectangle inside `[0f, 1f]` with at least [minSpan] along each axis. */
fun clampNormalizedCropRect(
    rect: NormalizedCropRect,
    minSpan: Float = 0.12f,
): NormalizedCropRect {
    val span = minSpan.coerceIn(0.02f, 0.9f)
    val left = rect.left.coerceIn(0f, 1f - span)
    val top = rect.top.coerceIn(0f, 1f - span)
    val right = rect.right.coerceIn(left + span, 1f)
    val bottom = rect.bottom.coerceIn(top + span, 1f)
    return NormalizedCropRect(left, top, right, bottom)
}

/** Applies a normalized delta `(dxFraction, dyFraction)` to [handle] while keeping the box valid. */
fun dragCropRect(
    rect: NormalizedCropRect,
    handle: CropDragHandle,
    dxFraction: Float,
    dyFraction: Float,
    minSpan: Float = 0.12f,
): NormalizedCropRect {
    val clamped = clampNormalizedCropRect(rect, minSpan)
    val span = minSpan.coerceIn(0.02f, 0.9f)
    return when (handle) {
        CropDragHandle.Move -> {
            val w = clamped.width
            val h = clamped.height
            val newLeft = (clamped.left + dxFraction).coerceIn(0f, (1f - w).coerceAtLeast(0f))
            val newTop = (clamped.top + dyFraction).coerceIn(0f, (1f - h).coerceAtLeast(0f))
            NormalizedCropRect(newLeft, newTop, newLeft + w, newTop + h)
        }
        CropDragHandle.Top -> clamped.copy(top = (clamped.top + dyFraction).coerceIn(0f, clamped.bottom - span))
        CropDragHandle.Bottom -> clamped.copy(bottom = (clamped.bottom + dyFraction).coerceIn(clamped.top + span, 1f))
        CropDragHandle.Left -> clamped.copy(left = (clamped.left + dxFraction).coerceIn(0f, clamped.right - span))
        CropDragHandle.Right -> clamped.copy(right = (clamped.right + dxFraction).coerceIn(clamped.left + span, 1f))
        CropDragHandle.TopLeft -> clamped.copy(
            left = (clamped.left + dxFraction).coerceIn(0f, clamped.right - span),
            top = (clamped.top + dyFraction).coerceIn(0f, clamped.bottom - span),
        )
        CropDragHandle.TopRight -> clamped.copy(
            right = (clamped.right + dxFraction).coerceIn(clamped.left + span, 1f),
            top = (clamped.top + dyFraction).coerceIn(0f, clamped.bottom - span),
        )
        CropDragHandle.BottomLeft -> clamped.copy(
            left = (clamped.left + dxFraction).coerceIn(0f, clamped.right - span),
            bottom = (clamped.bottom + dyFraction).coerceIn(clamped.top + span, 1f),
        )
        CropDragHandle.BottomRight -> clamped.copy(
            right = (clamped.right + dxFraction).coerceIn(clamped.left + span, 1f),
            bottom = (clamped.bottom + dyFraction).coerceIn(clamped.top + span, 1f),
        )
    }
}

/**
 * Hit-tests a pointer touch in stationary container pixel coordinates against the crop box
 * corners, edges, or interior so handles never self-oscillate when the box resizes.
 */
fun hitTestCropHandle(
    touchX: Float,
    touchY: Float,
    rect: NormalizedCropRect,
    containerWidth: Float,
    containerHeight: Float,
    hitRadiusPx: Float,
): CropDragHandle? {
    if (containerWidth <= 0f || containerHeight <= 0f) return null
    val left = rect.left * containerWidth
    val top = rect.top * containerHeight
    val right = rect.right * containerWidth
    val bottom = rect.bottom * containerHeight
    val radius = hitRadiusPx.coerceAtLeast(1f)
    val radiusSq = radius * radius

    val corners = listOf(
        CropDragHandle.TopLeft to ((touchX - left) * (touchX - left) + (touchY - top) * (touchY - top)),
        CropDragHandle.TopRight to ((touchX - right) * (touchX - right) + (touchY - top) * (touchY - top)),
        CropDragHandle.BottomLeft to ((touchX - left) * (touchX - left) + (touchY - bottom) * (touchY - bottom)),
        CropDragHandle.BottomRight to ((touchX - right) * (touchX - right) + (touchY - bottom) * (touchY - bottom)),
    )
    val nearestCorner = corners.filter { it.second <= radiusSq }.minByOrNull { it.second }
    if (nearestCorner != null) return nearestCorner.first

    val edges = buildList {
        if (touchX in (left - radius)..(right + radius)) {
            val topDist = abs(touchY - top)
            if (topDist <= radius) add(CropDragHandle.Top to topDist)
            val bottomDist = abs(touchY - bottom)
            if (bottomDist <= radius) add(CropDragHandle.Bottom to bottomDist)
        }
        if (touchY in (top - radius)..(bottom + radius)) {
            val leftDist = abs(touchX - left)
            if (leftDist <= radius) add(CropDragHandle.Left to leftDist)
            val rightDist = abs(touchX - right)
            if (rightDist <= radius) add(CropDragHandle.Right to rightDist)
        }
    }
    val nearestEdge = edges.minByOrNull { it.second }
    if (nearestEdge != null) return nearestEdge.first

    return if (touchX in left..right && touchY in top..bottom) CropDragHandle.Move else null
}

/** Maps [rect] into concrete `[x, y, width, height]` pixel bounds inside a bitmap of `(bitmapWidth, bitmapHeight)`. */
fun mapCropRectToPixels(
    rect: NormalizedCropRect,
    bitmapWidth: Int,
    bitmapHeight: Int,
): PixelCropBounds {
    if (bitmapWidth <= 0 || bitmapHeight <= 0) return PixelCropBounds(0, 0, 0, 0)
    val clamped = clampNormalizedCropRect(rect, minSpan = 0.01f)
    val x = (clamped.left * bitmapWidth).roundToInt().coerceIn(0, bitmapWidth - 1)
    val y = (clamped.top * bitmapHeight).roundToInt().coerceIn(0, bitmapHeight - 1)
    val right = (clamped.right * bitmapWidth).roundToInt().coerceIn(x + 1, bitmapWidth)
    val bottom = (clamped.bottom * bitmapHeight).roundToInt().coerceIn(y + 1, bitmapHeight)
    return PixelCropBounds(
        x = x,
        y = y,
        width = right - x,
        height = bottom - y,
    )
}

/**
 * Returns top region bounds to slice tall vertical Webtoon strips (`outHeight > outWidth * maxAspectRatio`)
 * so Overview / paged thumbnails render crisp comic tops instead of tiny blurry slivers, or `null`
 * when normal full-page scaling suffices.
 */
fun computeTallThumbRegion(
    outWidth: Int,
    outHeight: Int,
    maxAspectRatio: Float = 2.2f,
    targetAspectRatio: Float = 1.8f,
): PixelCropBounds? {
    if (outWidth <= 0 || outHeight <= 0) return null
    if (outHeight <= outWidth * maxAspectRatio) return null
    val cropHeight = (outWidth * targetAspectRatio).roundToInt().coerceIn(1, outHeight)
    return PixelCropBounds(x = 0, y = 0, width = outWidth, height = cropHeight)
}

/**
 * Computes bitmap decode `inSampleSize` from [targetWidthPx] while bounding decoded height below
 * [maxHeightPx] so tall Webtoon strips remain crisp horizontally without exhausting bitmap memory.
 */
fun computeThumbSampleSize(
    outWidth: Int,
    outHeight: Int,
    targetWidthPx: Int,
    maxHeightPx: Int = 2400,
): Int {
    if (outWidth <= 0 || outHeight <= 0 || targetWidthPx <= 0) return 1
    val byTarget = if (outHeight >= outWidth * 1.35f) {
        outWidth / targetWidthPx
    } else {
        maxOf(outWidth, outHeight) / targetWidthPx
    }
    val byHeightCap = if (maxHeightPx > 0 && outHeight > maxHeightPx) {
        (outHeight + maxHeightPx - 1) / maxHeightPx
    } else {
        1
    }
    return maxOf(1, maxOf(byTarget, byHeightCap))
}

/**
 * Extracts a cropped software `ARGB_8888` bitmap safely, copying `Bitmap.Config.HARDWARE` sources
 * to software before cropping and recycling any temporary copy in `finally`.
 */
fun cropBitmapSafely(
    source: Bitmap,
    rect: NormalizedCropRect,
): Bitmap? {
    if (source.isRecycled || source.width <= 0 || source.height <= 0) return null
    val bounds = mapCropRectToPixels(rect, source.width, source.height)
    if (bounds.width <= 0 || bounds.height <= 0) return null
    val needsSoftwareCopy = source.config == Bitmap.Config.HARDWARE
    val working = if (needsSoftwareCopy) {
        source.copy(Bitmap.Config.ARGB_8888, false) ?: return null
    } else {
        source
    }
    return try {
        Bitmap.createBitmap(working, bounds.x, bounds.y, bounds.width, bounds.height)
    } finally {
        if (needsSoftwareCopy && working !== source && !working.isRecycled) {
            working.recycle()
        }
    }
}
