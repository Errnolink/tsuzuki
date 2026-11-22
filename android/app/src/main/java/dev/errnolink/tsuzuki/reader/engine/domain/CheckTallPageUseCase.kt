// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): eu/kanade/tachiyomi/ui/reader/domain/CheckTallPageUseCase.kt
package dev.errnolink.tsuzuki.reader.engine.domain

import android.graphics.BitmapFactory
import dev.errnolink.tsuzuki.reader.engine.GlLimits
import dev.errnolink.tsuzuki.reader.engine.model.ReaderPage
import dev.errnolink.tsuzuki.reader.engine.model.ReaderPageSplit
import okio.BufferedSource
import okio.buffer
import okio.source

/**
 * Pure domain interactor that inspects image dimensions and calculates optimal [ReaderPageSplit]
 * slices if an image exceeds OpenGL texture limits or viewport height thresholds.
 */
class CheckTallPageUseCase {

    operator fun invoke(
        page: ReaderPage,
        screenHeight: Int,
        maxTextureSize: Int = GlLimits.maxCanvasTextureSize,
        bytesPerPixel: Long = 4L,
    ): List<ReaderPageSplit>? {
        val precomputed = page.precomputedSplits
        if (precomputed != null) {
            return precomputed.ifEmpty { null }
        }
        val streamFn = page.stream ?: return null
        val options =
            try {
                streamFn().source().buffer().use { extractImageOptions(it) }
            } catch (_: Exception) {
                return null
            }
        return computeSplits(
            page = page,
            outWidth = options.outWidth,
            outHeight = options.outHeight,
            screenHeight = screenHeight,
            maxTextureSize = maxTextureSize,
            bytesPerPixel = bytesPerPixel,
        )
    }

    /** Pure calculation of optimal slice splits given dimensions and maximum texture sizes. */
    fun computeSplits(
        page: ReaderPage,
        outWidth: Int,
        outHeight: Int,
        screenHeight: Int,
        maxTextureSize: Int = GlLimits.maxCanvasTextureSize,
        bytesPerPixel: Long = 4L, // Assuming ARGB_8888, adjust if other configs are possible
    ): List<ReaderPageSplit>? {
        if (outHeight <= 0 || outWidth <= 0) return null
        val displayMaxHeight =
            if (screenHeight > 0) {
                minOf(
                    maxOf(screenHeight * 2, GlLimits.SAFE_CANVAS_BITMAP_DIMENSION),
                    maxTextureSize,
                )
            } else {
                maxTextureSize
            }
        val exceedsCanvasLimit =
            (outWidth.toLong() * outHeight.toLong() * bytesPerPixel) >
                GlLimits.MAX_CANVAS_BITMAP_BYTES
        // A page is eligible for vertical slicing if it matches webtoon strip aspect ratios (>
        // 2.0f),
        // exceeds safe hardware texture dimensions, or exceeds the Android RecordingCanvas 100 MB
        // budget.
        val isTall =
            (outHeight.toFloat() / outWidth.toFloat() > 2f) ||
                (outHeight > maxTextureSize) ||
                exceedsCanvasLimit
        val maxSliceHeightByBytes =
            (GlLimits.MAX_CANVAS_BITMAP_BYTES / (outWidth.toLong() * bytesPerPixel))
                .coerceIn(1L, Int.MAX_VALUE.toLong())
                .toInt()
        val maxSliceHeight = minOf(displayMaxHeight, maxTextureSize, maxSliceHeightByBytes)
        if (!isTall || outHeight <= maxSliceHeight) {
            return null
        }

        val partCount = (outHeight - 1) / maxSliceHeight + 1
        if (partCount <= 1) return null

        val optimalSplitHeight = outHeight / partCount
        val splits = mutableListOf<ReaderPageSplit>()
        for (i in 0 until partCount) {
            val topOffset = i * optimalSplitHeight
            val splitH =
                if (i == partCount - 1) {
                    outHeight - topOffset
                } else {
                    optimalSplitHeight
                }
            val split = ReaderPageSplit(page = page, topOffset = topOffset, splitHeight = splitH)
            split.aspectRatio = outWidth.toFloat() / splitH.toFloat()
            splits.add(split)
        }
        return splits
    }
}

/**
 * Used to check an image's dimensions without loading it in the memory.
 */
internal fun extractImageOptions(imageSource: BufferedSource): BitmapFactory.Options {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeStream(imageSource.peek().inputStream(), null, options)
    return options
}
