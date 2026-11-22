// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): eu/kanade/tachiyomi/util/system/GLUtil.kt
// (carries the Neko #3525 hardware-row padding)
package dev.errnolink.tsuzuki.reader.engine

import eu.kanade.tachiyomi.util.system.GLUtil

/**
 * Canvas and texture limits the reader split math needs. Reuses Tsuzuki's device texture probe and
 * adds the RecordingCanvas budget from Neko.
 */
object GlLimits {

    private const val IMAGE_MAX_BITMAP_DIMENSION = 2048

    /**
     * Android HWUI [android.graphics.RecordingCanvas] throws [RuntimeException] ("Canvas:
     * trying to draw too large(...) bitmap") if any drawn bitmap exceeds 100 MB (104,857,600
     * bytes).
     */
    const val MAX_CANVAS_BITMAP_BYTES: Long = 100L * 1024 * 1024

    /**
     * Pixels a HARDWARE bitmap row is assumed to be padded to. Its byte count, which
     * [android.graphics.RecordingCanvas] checks up to Android 14, uses the graphics buffer's
     * row stride. Crash reports showed rows padded to a multiple of 64 pixels (5368 drawn as
     * 5376, 8016 as 8064); 128 leaves a margin for other GPUs.
     */
    const val HARDWARE_ROW_ALIGNMENT: Int = 128

    /**
     * Bytes a [width] x [height] bitmap can count against [MAX_CANVAS_BITMAP_BYTES] when it is
     * a HARDWARE bitmap, with the row padded to [HARDWARE_ROW_ALIGNMENT].
     */
    fun canvasBitmapBytes(width: Int, height: Int, bytesPerPixel: Int = 4): Long {
        val alignment = HARDWARE_ROW_ALIGNMENT.toLong()
        val paddedWidth = (width + alignment - 1) / alignment * alignment
        return paddedWidth * height * bytesPerPixel
    }

    /**
     * Safe maximum dimension for bitmaps rendered onto a hardware-accelerated Canvas. Clamped
     * to at most [SAFE_CANVAS_BITMAP_DIMENSION] (4096) so that a square ARGB_8888 bitmap
     * requires at most 4096 x 4096 x 4 = 64 MB, safely below [MAX_CANVAS_BITMAP_BYTES] (100
     * MB).
     */
    const val SAFE_CANVAS_BITMAP_DIMENSION: Int = 4096

    val maxCanvasTextureSize: Int by lazy {
        minOf(maxTextureSize, SAFE_CANVAS_BITMAP_DIMENSION)
    }

    /**
     * Raw device texture limit. Reader page requests use this as their box together with the
     * [MAX_CANVAS_BITMAP_BYTES] cap (Neko #3490), so tall pages decode sharper than the 4096 px
     * square while staying drawable.
     */
    val maxTextureSize: Int by lazy {
        try {
            GLUtil.DEVICE_TEXTURE_LIMIT
        } catch (_: Throwable) {
            IMAGE_MAX_BITMAP_DIMENSION
        }
    }
}
