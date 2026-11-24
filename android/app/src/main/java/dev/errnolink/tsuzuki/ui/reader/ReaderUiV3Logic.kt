package dev.errnolink.tsuzuki.ui.reader

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

data class PixelCropBounds(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

/**
 * A long-capture selection over the capture document: pages stacked top to bottom at a width of
 * 1, so a page is `nativeHeight / nativeWidth` tall. [top] and [bottom] are document units,
 * [left] and [right] fractions of the width.
 */
data class CaptureSelection(
    val top: Float,
    val bottom: Float,
    val left: Float = 0f,
    val right: Float = 1f,
) {
    val height: Float get() = bottom - top
    val width: Float get() = right - left
}

enum class CaptureEdge { Top, Bottom, Left, Right }

/** The document top of each page plus the document height as the last entry. */
fun captureDocumentTops(pageAspects: List<Float>): List<Float> =
    pageAspects.runningFold(0f) { acc, aspect -> acc + aspect.coerceAtLeast(0f) }

/**
 * Hit-tests a touch at ([x], [y]) in stage pixels against the selection edges drawn at
 * ([leftPx], [topPx], [rightPx], [bottomPx]). Horizontal edges win where they overlap the sides.
 */
fun hitTestCaptureEdge(
    x: Float,
    y: Float,
    leftPx: Float,
    topPx: Float,
    rightPx: Float,
    bottomPx: Float,
    radiusPx: Float,
): CaptureEdge? {
    val withinX = x in (leftPx - radiusPx)..(rightPx + radiusPx)
    val withinY = y in (topPx - radiusPx)..(bottomPx + radiusPx)
    val candidates = buildList {
        if (withinX && abs(y - topPx) <= radiusPx) add(CaptureEdge.Top to abs(y - topPx))
        if (withinX && abs(y - bottomPx) <= radiusPx) add(CaptureEdge.Bottom to abs(y - bottomPx))
        if (withinY && abs(x - leftPx) <= radiusPx) add(CaptureEdge.Left to abs(x - leftPx) + radiusPx / 2f)
        if (withinY && abs(x - rightPx) <= radiusPx) add(CaptureEdge.Right to abs(x - rightPx) + radiusPx / 2f)
    }
    return candidates.minByOrNull { it.second }?.first
}

/**
 * Moves [edge] of [selection] to [docY] (Top/Bottom, document units) or [fraction] (Left/Right),
 * keeping it inside `[0, documentHeight]` × `[0, 1]` and at least [minHeight] × [minWidth].
 */
fun moveCaptureEdge(
    selection: CaptureSelection,
    edge: CaptureEdge,
    docY: Float,
    fraction: Float,
    documentHeight: Float,
    minHeight: Float,
    minWidth: Float = 0.15f,
): CaptureSelection = when (edge) {
    CaptureEdge.Top -> selection.copy(top = docY.coerceIn(0f, (selection.bottom - minHeight).coerceAtLeast(0f)))
    CaptureEdge.Bottom -> selection.copy(bottom = docY.coerceIn((selection.top + minHeight).coerceAtMost(documentHeight), documentHeight))
    CaptureEdge.Left -> selection.copy(left = fraction.coerceIn(0f, (selection.right - minWidth).coerceAtLeast(0f)))
    CaptureEdge.Right -> selection.copy(right = fraction.coerceIn((selection.left + minWidth).coerceAtMost(1f), 1f))
}

/** One page's part of a selection: [fromFraction]..[toFraction] of that page's height. */
data class CaptureSlice(val pageIndex: Int, val fromFraction: Float, val toFraction: Float, val docHeight: Float)

/** Splits [selection] into the per-page vertical slices it covers, top to bottom. */
fun captureSlices(selection: CaptureSelection, tops: List<Float>): List<CaptureSlice> =
    (0 until tops.size - 1).mapNotNull { i ->
        val pageTop = tops[i]
        val pageBottom = tops[i + 1]
        val from = maxOf(selection.top, pageTop)
        val to = minOf(selection.bottom, pageBottom)
        val h = pageBottom - pageTop
        if (to <= from || h <= 0f) null else CaptureSlice(i, (from - pageTop) / h, (to - pageTop) / h, to - from)
    }

/**
 * Output size of a capture: [preferredWidthPx] wide (the source width under the selection),
 * scaled down so the image is never taller than [maxHeightPx].
 */
fun captureOutputSize(selection: CaptureSelection, preferredWidthPx: Int, maxHeightPx: Int = 16_384): Pair<Int, Int> {
    if (selection.width <= 0f || selection.height <= 0f || preferredWidthPx <= 0) return 0 to 0
    val ratio = selection.height / selection.width
    val width = minOf(preferredWidthPx.toFloat(), maxHeightPx / ratio).coerceAtLeast(1f)
    return width.roundToInt() to (width * ratio).roundToInt().coerceAtLeast(1)
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
