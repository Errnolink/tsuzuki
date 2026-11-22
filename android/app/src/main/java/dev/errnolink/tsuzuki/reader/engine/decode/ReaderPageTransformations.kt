package dev.errnolink.tsuzuki.reader.engine.decode

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.createBitmap
import coil3.size.Size
import coil3.transform.Transformation

class SplitDoublePageVerticalTransformation(
    private val invert: Boolean = false,
) : Transformation() {

    override val cacheKey: String = "${this::class.java.name}-$invert"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        if (input.width <= input.height || input.width < 2 || input.isRecycled) return input
        val src = input.toSoftwareCopyIfNeeded() ?: return input
        return try {
            val halfWidth = src.width / 2
            val height = src.height
            val upperLeft = invert
            val firstSrc = if (upperLeft) Rect(0, 0, halfWidth, height) else Rect(halfWidth, 0, src.width, height)
            val secondSrc = if (upperLeft) Rect(halfWidth, 0, src.width, height) else Rect(0, 0, halfWidth, height)
            val outWidth = maxOf(firstSrc.width(), secondSrc.width())
            val config = src.config?.takeIf { it != Bitmap.Config.HARDWARE } ?: Bitmap.Config.ARGB_8888
            val out = createBitmap(outWidth, height * 2, config)
            val canvas = Canvas(out)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(src, firstSrc, Rect(0, 0, firstSrc.width(), height), paint)
            canvas.drawBitmap(src, secondSrc, Rect(0, height, secondSrc.width(), height * 2), paint)
            out
        } finally {
            if (src !== input && !src.isRecycled) src.recycle()
        }
    }
}

class WidePageCenterMarginTransformation(
    private val marginPx: Int,
    private val gutterColor: Int,
) : Transformation() {

    override val cacheKey: String = "${this::class.java.name}-$marginPx-$gutterColor"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        if (marginPx <= 0 || input.width <= input.height || input.width < 2 || input.isRecycled) return input
        val src = input.toSoftwareCopyIfNeeded() ?: return input
        return try {
            val halfWidth = src.width / 2
            val height = src.height
            val config = src.config?.takeIf { it != Bitmap.Config.HARDWARE } ?: Bitmap.Config.ARGB_8888
            val out = createBitmap(src.width + marginPx, height, config)
            val canvas = Canvas(out)
            canvas.drawColor(gutterColor)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(src, Rect(0, 0, halfWidth, height), Rect(0, 0, halfWidth, height), paint)
            canvas.drawBitmap(
                src,
                Rect(halfWidth, 0, src.width, height),
                Rect(halfWidth + marginPx, 0, src.width + marginPx, height),
                paint,
            )
            out
        } finally {
            if (src !== input && !src.isRecycled) src.recycle()
        }
    }
}

private fun Bitmap.toSoftwareCopyIfNeeded(): Bitmap? {
    if (isRecycled) return null
    return if (config == Bitmap.Config.HARDWARE) {
        copy(Bitmap.Config.ARGB_8888, false)
    } else {
        this
    }
}

fun chooseSmartBackgroundColor(bitmap: Bitmap, fallback: Color): Color {
    if (bitmap.isRecycled) return fallback
    val width = bitmap.width
    val height = bitmap.height
    if (width < 4 || height < 4) return fallback
    val readable = bitmap.toSoftwareCopyIfNeeded() ?: return fallback
    return try {
        val stepX = maxOf(1, width / 16)
        val stepY = maxOf(1, height / 16)
        var darkCount = 0
        var lightCount = 0
        var samples = 0
        for (x in 0 until width step stepX) {
            for (y in listOf(0, height - 1)) {
                val c = readable.getPixel(x, y)
                val alpha = (c ushr 24) and 0xFF
                if (alpha < 200) continue
                val r = (c ushr 16) and 0xFF
                val g = (c ushr 8) and 0xFF
                val b = c and 0xFF
                val luma = 0.299 * r + 0.587 * g + 0.114 * b
                if (luma < 64.0) darkCount++ else if (luma > 192.0) lightCount++
                samples++
            }
        }
        for (y in 0 until height step stepY) {
            for (x in listOf(0, width - 1)) {
                val c = readable.getPixel(x, y)
                val alpha = (c ushr 24) and 0xFF
                if (alpha < 200) continue
                val r = (c ushr 16) and 0xFF
                val g = (c ushr 8) and 0xFF
                val b = c and 0xFF
                val luma = 0.299 * r + 0.587 * g + 0.114 * b
                if (luma < 64.0) darkCount++ else if (luma > 192.0) lightCount++
                samples++
            }
        }
        if (samples == 0) return fallback
        when {
            darkCount * 2 >= samples -> Color.Black
            lightCount * 2 >= samples -> Color.White
            else -> fallback
        }
    } catch (_: Exception) {
        fallback
    } finally {
        if (readable !== bitmap && !readable.isRecycled) readable.recycle()
    }
}
