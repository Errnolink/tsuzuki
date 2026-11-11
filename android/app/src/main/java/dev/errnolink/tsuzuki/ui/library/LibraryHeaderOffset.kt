package dev.errnolink.tsuzuki.ui.library

internal fun libraryHeaderOffset(firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int, maxOffsetPx: Int): Int =
    if (firstVisibleItemIndex > 0) {
        maxOffsetPx
    } else {
        firstVisibleItemScrollOffset.coerceIn(0, maxOffsetPx)
    }

internal fun libraryHeaderOffsetDuringSwipe(currentPageOffsetPx: Int, neighbourPageOffsetPx: Int, pageOffsetFraction: Float): Float {
    val travelled = kotlin.math.abs(pageOffsetFraction).coerceIn(0f, 1f)
    return currentPageOffsetPx + (neighbourPageOffsetPx - currentPageOffsetPx) * travelled
}
