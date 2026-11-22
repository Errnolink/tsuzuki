// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): app/src/test/java/org/nekomanga/presentation/screens/reader/viewer/ReaderPageImageSourceTest.kt (Neko #3542)
package dev.errnolink.tsuzuki.reader.engine.decode

import android.content.Context
import coil3.request.ImageRequest
import coil3.request.maxBitmapSize
import coil3.size.Size
import dev.errnolink.tsuzuki.reader.engine.GlLimits
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReaderPageSizeLimitsTest {

    @Test
    fun `page size limits keep the original target size so small pages are not scaled up`() =
        kotlinx.coroutines.test.runTest {
            // Before a transformation, Coil draws a non-bitmap image such as an animated GIF into
            // a bitmap scaled to the target size. The target must stay original, so crop borders
            // and rotation run on the natural frame instead of a texture-sized blow-up that
            // crashes.
            val limited =
                ImageRequest.Builder(mockk<Context>(relaxed = true))
                    .data("page_0")
                    .readerPageSizeLimits(maxTextureSize = 16383)
                    .build()

            assertEquals(Size.ORIGINAL, limited.sizeResolver.size())
        }

    @Test
    fun `page size limits cap pages at the texture size and the canvas byte limit`() {
        val limited =
            ImageRequest.Builder(mockk<Context>(relaxed = true))
                .data("page_0")
                .readerPageSizeLimits(maxTextureSize = 16383)
                .build()

        assertEquals(Size(16383, 16383), limited.maxBitmapSize)
        assertEquals(
            GlLimits.MAX_CANVAS_BITMAP_BYTES.toString(),
            limited.memoryCacheKeyExtras[maxBitmapBytesMemoryCacheKey],
        )
    }
}
