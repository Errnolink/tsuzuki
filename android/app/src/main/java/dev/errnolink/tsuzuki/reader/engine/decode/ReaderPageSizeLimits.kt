// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): org/nekomanga/presentation/screens/reader/viewer/ReaderPageImageSource.kt (Neko #3542)
package dev.errnolink.tsuzuki.reader.engine.decode

import coil3.request.ImageRequest
import coil3.request.maxBitmapSize
import coil3.size.Size as CoilSize
import dev.errnolink.tsuzuki.reader.engine.GlLimits

/**
 * Caps a reader page request at a [maxTextureSize] square and at [GlLimits.MAX_CANVAS_BITMAP_BYTES].
 * This replaces Coil's default maxBitmapSize of 4096 px, so these are the only caps. Without the
 * square, a page taller than the texture size draws blank as a hardware bitmap. Without the byte
 * limit, a page over it is too large for the canvas.
 *
 * The square goes in maxBitmapSize and the target size stays original. Before a transformation,
 * Coil draws an image that is not a bitmap, such as an animated GIF, into a new bitmap scaled to
 * fit the target size, so a square target would scale a small page up to the texture size and make
 * crop-borders or rotation run on a huge frame (Neko #3542).
 */
fun ImageRequest.Builder.readerPageSizeLimits(maxTextureSize: Int): ImageRequest.Builder =
    size(CoilSize.ORIGINAL)
        .maxBitmapSize(CoilSize(maxTextureSize, maxTextureSize))
        .maxBitmapBytes(GlLimits.MAX_CANVAS_BITMAP_BYTES)
