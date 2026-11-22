// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): eu/kanade/tachiyomi/data/coil/ReaderPageFetcher.kt
// (keyers live in dev.errnolink.tsuzuki.reader.engine.model since C1; carries Neko #3525, #3527)
package dev.errnolink.tsuzuki.reader.engine.decode

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.os.Build
import android.util.LruCache
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import dev.errnolink.tsuzuki.reader.engine.GlLimits
import dev.errnolink.tsuzuki.reader.engine.model.ReaderPage
import dev.errnolink.tsuzuki.reader.engine.model.ReaderPageSplit
import eu.kanade.tachiyomi.source.model.Page
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import okio.buffer
import okio.source
import tachiyomi.core.common.util.system.ImageUtil
import tachiyomi.decoder.ImageDecoder

class ReaderPageFetcher(private val page: ReaderPage, private val options: Options) : Fetcher {

    override suspend fun fetch(): FetchResult = coroutineScope {
        var streamFn = page.stream
        if (streamFn == null) {
            val loader = page.chapter.pageLoader
            val loadJob =
                if (loader != null && page.status == Page.State.Queue) {
                    launch(Dispatchers.IO) { loader.loadPage(page) }
                } else {
                    null
                }
            try {
                page.statusFlow.first {
                    (it == Page.State.Ready && page.stream != null) || it is Page.State.Error
                }
            } finally {
                loadJob?.cancel()
            }
            streamFn = page.stream
        }

        val actualStream = streamFn ?: error("Page stream not available for page ${page.index}")
        val source = actualStream().source().buffer()
        SourceFetchResult(
            source = ImageSource(source = source, fileSystem = options.fileSystem),
            mimeType = null,
            dataSource = DataSource.MEMORY,
        )
    }

    class Factory : Fetcher.Factory<ReaderPage> {
        override fun create(
            data: ReaderPage,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher {
            return ReaderPageFetcher(data, options)
        }
    }
}

/**
 * Size limit of [ReaderPageSplitFetcher]'s cache of full page decodes, and the budget for one
 * decode. LruCache.put evicts a value bigger than the limit right after adding it, so a bigger
 * decode would be repeated for every later slice of the page.
 */
internal fun fallbackBitmapCacheMaxBytes(maxMemory: Long = Runtime.getRuntime().maxMemory()): Int =
    (maxMemory / 4).coerceAtMost(256L * 1024 * 1024).toInt()

/**
 * Power-of-two sample size that keeps an ARGB_8888 decode of the whole page within
 * [fallbackBitmapCacheMaxBytes].
 */
internal fun fallbackDecodeSampleSize(
    imageWidth: Int,
    imageHeight: Int,
    maxMemory: Long = Runtime.getRuntime().maxMemory(),
): Int {
    val maxPixels = fallbackBitmapCacheMaxBytes(maxMemory) / 4

    var sampleSize = 1
    while (
        sampledDimension(imageWidth, sampleSize) * sampledDimension(imageHeight, sampleSize) >
            maxPixels
    ) {
        sampleSize *= 2
    }
    return sampleSize
}

/** Upper bound on the byte count of an ARGB_8888 decode of the whole page at [sampleSize]. */
internal fun fallbackDecodeBytes(imageWidth: Int, imageHeight: Int, sampleSize: Int): Long =
    sampledDimension(imageWidth, sampleSize) * sampledDimension(imageHeight, sampleSize) * 4

/**
 * Runs [decode] at [sampleSize]. When it returns null or throws [OutOfMemoryError], runs
 * [beforeRetry] with that error (null for a null result) and tries once more at double the sample
 * size, and returns that result. Other throwables, and an [OutOfMemoryError] from the second try,
 * propagate.
 */
internal fun <T : Any> decodeOrRetrySmaller(
    sampleSize: Int,
    beforeRetry: (outOfMemory: OutOfMemoryError?) -> Unit,
    decode: (sampleSize: Int) -> T?,
): T? {
    val outOfMemory =
        try {
            decode(sampleSize)?.let {
                return it
            }
            null
        } catch (e: OutOfMemoryError) {
            e
        }
    beforeRetry(outOfMemory)
    return decode(sampleSize * 2)
}

// Round up. BitmapFactory rounds a sampled GIF to the nearest pixel, so a 2305x65535 GIF
// decodes to 1153x32768 at sample size 2.
private fun sampledDimension(size: Int, sampleSize: Int): Long =
    (size.toLong() + sampleSize - 1) / sampleSize

/**
 * Calculates the smallest power-of-two sample size that keeps the decoded region within canvas
 * limits.
 */
internal fun calculateRegionSampleSize(
    regionWidth: Int,
    regionHeight: Int,
    maxDim: Int = GlLimits.maxCanvasTextureSize,
    maxBytes: Long = GlLimits.MAX_CANVAS_BITMAP_BYTES,
): Int {
    var sampleSize = 1
    while (
        (regionWidth / sampleSize) > maxDim ||
            (regionHeight / sampleSize) > maxDim ||
            ((regionWidth.toLong() / sampleSize) * (regionHeight.toLong() / sampleSize) * 4L) >
                maxBytes
    ) {
        sampleSize *= 2
    }
    return sampleSize
}

/**
 * Calculates the scale factor to bring a fallback slice within canvas texture dimension and byte
 * limits.
 */
internal fun calculateFallbackSliceScale(
    cropWidth: Int,
    cropHeight: Int,
    bytesPerPixel: Long,
    maxDim: Int = GlLimits.maxCanvasTextureSize,
    maxBytes: Long = GlLimits.MAX_CANVAS_BITMAP_BYTES,
): Float {
    val rawBytes = cropWidth.toLong() * cropHeight.toLong() * bytesPerPixel
    if (rawBytes <= maxBytes && cropWidth <= maxDim && cropHeight <= maxDim) {
        return 1f
    }
    return minOf(
            maxDim.toFloat() / cropWidth.toFloat(),
            maxDim.toFloat() / cropHeight.toFloat(),
            sqrt(maxBytes.toDouble() / rawBytes.toDouble()).toFloat(),
        )
        .coerceIn(0f, 1f)
}

class ReaderPageSplitFetcher(private val split: ReaderPageSplit, private val options: Options) :
    Fetcher {

    companion object {
        private class CachedDecodedImage(
            val bitmap: Bitmap,
            val originalWidth: Int,
            val originalHeight: Int,
        )

        private class CachedBytes(val bytes: ByteArray, val retryGeneration: Int)

        private val maxCacheSizeBytes =
            (Runtime.getRuntime().maxMemory() / 16)
                .coerceIn(16L * 1024 * 1024, 64L * 1024 * 1024)
                .toInt()

        private val maxBitmapCacheSizeBytes = fallbackBitmapCacheMaxBytes()

        private val rawBytesCache =
            object : LruCache<String, CachedBytes>(maxCacheSizeBytes) {
                override fun sizeOf(key: String, value: CachedBytes): Int = value.bytes.size
            }

        private val fallbackBitmapCache =
            object : LruCache<String, CachedDecodedImage>(maxBitmapCacheSizeBytes) {
                override fun sizeOf(key: String, value: CachedDecodedImage): Int =
                    value.bitmap.byteCount
            }

        private val activeFetches = SharedWork<ByteArray>()
        private val activeDecodes = SharedFullDecodes<CachedDecodedImage>()
        private val regionDecoders = SharedRegionDecoders<BitmapRegionDecoder> { it.recycle() }

        fun clearCache() {
            // clear() leaves running work alone because the next reader can already be waiting on
            // it when the old reader's destroy calls this. Clearing the shared work before the
            // evictions makes that work skip its put, so it cannot refill the caches.
            activeFetches.clear()
            activeDecodes.clear()
            rawBytesCache.evictAll()
            fallbackBitmapCache.evictAll()
        }

        private fun performFullDecode(imageBytes: ByteArray, pageIndex: Int): CachedDecodedImage? {
            // BitmapFactory decodes AVIF with the platform AV1 codec, and the AOSP software one
            // (c2.android.av1-dav1d) rejects frames wider or taller than 4096 px, so a tall AVIF
            // page decodes to null. Formats the platform BitmapFactory cannot read use the bundled
            // decoder instead, as they do in TachiyomiImageDecoder.
            val imageType = ImageUtil.findImageType(imageBytes.inputStream())
            val needsNativeDecoder =
                imageType == ImageUtil.ImageType.AVIF ||
                    imageType == ImageUtil.ImageType.HEIF ||
                    imageType == ImageUtil.ImageType.JXL
            return if (needsNativeDecoder) {
                nativeFullDecode(imageBytes, pageIndex)
            } else {
                platformFullDecode(imageBytes, pageIndex)
            }
        }

        private fun nativeFullDecode(imageBytes: ByteArray, pageIndex: Int): CachedDecodedImage? {
            try {
                val decoder = ImageDecoder.newInstance(imageBytes.inputStream())
                if (decoder == null || decoder.width <= 0 || decoder.height <= 0) {
                    decoder?.recycle()
                    logcat(LogPriority.ERROR) {
                        "Cannot fallback decode region: native decoder could not open page $pageIndex"
                    }
                    return null
                }

                val imageWidth = decoder.width
                val imageHeight = decoder.height
                val sampleSize = fallbackDecodeSampleSize(imageWidth, imageHeight)
                trimCacheForFullDecode(imageWidth, imageHeight, sampleSize)
                // decode() throws OutOfMemoryError when it runs out of memory, but a failed
                // allocation inside the AV1 or HEVC decoder still comes back as null, like bad
                // data. Either failure gets a second try at double the sample size, and after an
                // OutOfMemoryError the fallback caches are cleared first. The second try shrinks
                // the output buffer and bitmap to a quarter, but AVIF and HEIF still decode at
                // full size first. A page that fails twice is left alone until Retry.
                val full =
                    try {
                        decodeOrRetrySmaller(
                            sampleSize = sampleSize,
                            beforeRetry = { outOfMemory ->
                                if (outOfMemory != null) {
                                    logcat(LogPriority.WARN) {
                                        "Native fallback decode ran out of memory for page $pageIndex ($imageWidth x $imageHeight, sample size $sampleSize), trying sample size ${sampleSize * 2}"
                                    }
                                    fallbackBitmapCache.evictAll()
                                    rawBytesCache.evictAll()
                                } else {
                                    logcat(LogPriority.WARN) {
                                        "Native fallback decode returned no bitmap for page $pageIndex ($imageWidth x $imageHeight, sample size $sampleSize), trying sample size ${sampleSize * 2}"
                                    }
                                }
                            },
                            decode = { decoder.decode(sampleSize = it) },
                        )
                    } finally {
                        decoder.recycle()
                    }
                if (full == null) {
                    logcat(LogPriority.ERROR) {
                        "Native fallback decode returned no bitmap for page $pageIndex ($imageWidth x $imageHeight, sample size ${sampleSize * 2})"
                    }
                    return null
                }
                return CachedDecodedImage(full, imageWidth, imageHeight)
            } catch (e: OutOfMemoryError) {
                // Covers newInstance, which gets no second try (JXL decodes the whole image
                // there), and the retry.
                logcat(LogPriority.ERROR) {
                    "OutOfMemoryError during native fallback decode for page $pageIndex"
                }
                fallbackBitmapCache.evictAll()
                rawBytesCache.evictAll()
                return null
            }
        }

        private fun platformFullDecode(imageBytes: ByteArray, pageIndex: Int): CachedDecodedImage? {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, boundsOptions)
            val imageWidth = boundsOptions.outWidth
            val imageHeight = boundsOptions.outHeight

            if (imageWidth <= 0 || imageHeight <= 0) {
                logcat(LogPriority.ERROR) {
                    "Cannot fallback decode region: invalid image dimensions ($imageWidth x $imageHeight)"
                }
                return null
            }

            val sampleSize = fallbackDecodeSampleSize(imageWidth, imageHeight)
            trimCacheForFullDecode(imageWidth, imageHeight, sampleSize)

            val decodeOptions =
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }

            return try {
                val full =
                    BitmapFactory.decodeByteArray(
                        imageBytes,
                        0,
                        imageBytes.size,
                        decodeOptions,
                    )
                if (full != null) {
                    CachedDecodedImage(full, imageWidth, imageHeight)
                } else {
                    logcat(LogPriority.ERROR) {
                        "BitmapFactory returned no bitmap for page $pageIndex ($imageWidth x $imageHeight)"
                    }
                    null
                }
            } catch (e: OutOfMemoryError) {
                logcat(LogPriority.ERROR) {
                    "OutOfMemoryError during fallback decode for page $pageIndex"
                }
                fallbackBitmapCache.evictAll()
                rawBytesCache.evictAll()
                try {
                    val fallbackOptions =
                        BitmapFactory.Options().apply {
                            inSampleSize = (sampleSize * 2).coerceAtLeast(2)
                            inPreferredConfig = Bitmap.Config.RGB_565
                        }
                    val full =
                        BitmapFactory.decodeByteArray(
                            imageBytes,
                            0,
                            imageBytes.size,
                            fallbackOptions,
                        )
                    if (full != null) {
                        CachedDecodedImage(full, imageWidth, imageHeight)
                    } else {
                        null
                    }
                } catch (e2: Throwable) {
                    logcat(LogPriority.ERROR) {
                        "Secondary OOM during fallback decode for page $pageIndex"
                    }
                    null
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) {
                    "Unexpected error during fallback decode for page $pageIndex"
                }
                null
            }
        }

        // LruCache evicts on put, after the new bitmap is allocated. Evicting first keeps cached
        // pages plus the new bitmap within the budget when pages decode one at a time.
        private fun trimCacheForFullDecode(imageWidth: Int, imageHeight: Int, sampleSize: Int) {
            val expectedBytes = fallbackDecodeBytes(imageWidth, imageHeight, sampleSize)
            fallbackBitmapCache.trimToSize(
                (fallbackBitmapCache.maxSize() - expectedBytes).coerceAtLeast(0).toInt()
            )
        }

        private fun createRegionDecoder(
            imageBytes: ByteArray,
            pageIndex: Int,
        ): BitmapRegionDecoder? =
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    BitmapRegionDecoder.newInstance(imageBytes, 0, imageBytes.size)
                } else {
                    @Suppress("DEPRECATION")
                    BitmapRegionDecoder.newInstance(imageBytes, 0, imageBytes.size, false)
                }
            } catch (e: Exception) {
                logcat(LogPriority.DEBUG) {
                    "BitmapRegionDecoder not supported for page $pageIndex, falling back to full decode"
                }
                null
            }
    }

    override suspend fun fetch(): FetchResult = coroutineScope {
        val chapterKey =
            split.page.chapter.chapter.id.takeIf { it > 0 }
                ?: split.page.chapter.chapter.url.hashCode()
        val cacheKey = "${chapterKey}_${split.page.index}"
        // Bytes read and decodes that failed before the last Retry are not used, so a retry after
        // the page was downloaded again decodes the new file.
        val retryGeneration = split.page.retryGeneration

        // A page with a cached full decode serves its slices from that decode, without reading
        // the file again.
        val cached = fallbackBitmapCache.get(cacheKey)?.takeUnless { it.bitmap.isRecycled }
        if (cached != null) {
            val slice =
                withContext(Dispatchers.IO) {
                    cropFallbackSlice(cached, split.topOffset, split.splitHeight)
                }
            if (slice != null) {
                return@coroutineScope ImageFetchResult(
                    image = slice.asImage(),
                    isSampled = false,
                    dataSource = DataSource.MEMORY,
                )
            }
        }

        var streamFn = split.page.stream
        if (streamFn == null) {
            val loader = split.page.chapter.pageLoader
            val loadJob =
                if (loader != null && split.page.status == Page.State.Queue) {
                    launch(Dispatchers.IO) { loader.loadPage(split.page) }
                } else {
                    null
                }
            try {
                split.page.statusFlow.first {
                    (it == Page.State.Ready && split.page.stream != null) ||
                        it is Page.State.Error
                }
            } finally {
                loadJob?.cancel()
            }
            streamFn = split.page.stream
        }
        val actualStream =
            streamFn ?: error("Page stream not available for page ${split.page.index}")

        val imageBytes =
            rawBytesCache.get(cacheKey)?.takeIf { it.retryGeneration >= retryGeneration }?.bytes
                ?: activeFetches.await("$cacheKey@$retryGeneration") {
                    actualStream()
                        .use { it.readBytes() }
                        .also {
                            // LruCache.put would evict a file bigger than the cache right away,
                            // with every other entry.
                            if (it.size <= rawBytesCache.maxSize()) {
                                unlessCleared {
                                    rawBytesCache.put(cacheKey, CachedBytes(it, retryGeneration))
                                }
                            }
                        }
                }

        val bitmap =
            withContext(Dispatchers.IO) {
                decodeRegion(
                    imageBytes,
                    split.topOffset,
                    split.splitHeight,
                    cacheKey,
                    retryGeneration,
                )
            }

        if (bitmap != null) {
            ImageFetchResult(
                image = bitmap.asImage(),
                isSampled = false,
                dataSource = DataSource.MEMORY,
            )
        } else {
            error(
                "Failed to decode webtoon slice for page ${split.page.index} at offset ${split.topOffset}"
            )
        }
    }

    private suspend fun decodeRegion(
        imageBytes: ByteArray,
        top: Int,
        height: Int,
        cacheKey: String,
        retryGeneration: Int,
    ): Bitmap? {
        // The create lambda can outlive this fetcher, so it reads no fetcher fields.
        val pageIndex = split.page.index
        val create = { createRegionDecoder(imageBytes, pageIndex) }
        val sliceBitmap =
            regionDecoders.use(imageBytes, create) { decoder ->
                decoder?.let { decodeSlice(it, top, height) }
            }

        return sliceBitmap
            ?: fallbackDecodeRegion(imageBytes, top, height, cacheKey, retryGeneration)
    }

    private fun decodeSlice(decoder: BitmapRegionDecoder, top: Int, height: Int): Bitmap? =
        try {
            val bottom = minOf(decoder.height, top + height)
            if (top < decoder.height && bottom > top) {
                val region = Rect(0, top, decoder.width, bottom)
                val regionWidth = decoder.width
                val regionHeight = bottom - top
                decoder.decodeRegion(
                    region,
                    BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                        inSampleSize = calculateRegionSampleSize(regionWidth, regionHeight)
                    },
                )
            } else {
                null
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) {
                "BitmapRegionDecoder failed during decodeRegion for page ${split.page.index}, slice offset $top"
            }
            null
        }

    private suspend fun fallbackDecodeRegion(
        imageBytes: ByteArray,
        top: Int,
        height: Int,
        cacheKey: String,
        retryGeneration: Int,
    ): Bitmap? {
        val cached = fallbackBitmapCache.get(cacheKey)?.takeUnless { it.bitmap.isRecycled }
        // The decode can outlive this fetcher, so it reads no fetcher fields. A reference to the
        // fetcher would keep its Options and their context alive until the decode ends.
        val pageIndex = split.page.index
        val decoded =
            cached
                ?: activeDecodes.await(cacheKey, retryGeneration) {
                    performFullDecode(imageBytes, pageIndex)?.also {
                        unlessCleared { fallbackBitmapCache.put(cacheKey, it) }
                    }
                }
                ?: return null

        return cropFallbackSlice(decoded, top, height)
    }

    private fun cropFallbackSlice(decoded: CachedDecodedImage, top: Int, height: Int): Bitmap? =
        try {
            val scale = decoded.bitmap.height.toFloat() / decoded.originalHeight.toFloat()
            val scaledTop = (top * scale).toInt().coerceIn(0, decoded.bitmap.height - 1)
            val scaledHeight = (height * scale).toInt().coerceAtLeast(1)
            val cropHeight = minOf(scaledHeight, decoded.bitmap.height - scaledTop)
            val cropWidth = decoded.bitmap.width
            if (cropHeight <= 0 || cropWidth <= 0) {
                null
            } else {
                val bytesPerPixel = if (decoded.bitmap.config == Bitmap.Config.RGB_565) 2L else 4L
                val scaleFactor = calculateFallbackSliceScale(cropWidth, cropHeight, bytesPerPixel)
                if (scaleFactor < 1f) {
                    val matrix = Matrix().apply { postScale(scaleFactor, scaleFactor) }
                    Bitmap.createBitmap(
                        decoded.bitmap,
                        0,
                        scaledTop,
                        cropWidth,
                        cropHeight,
                        matrix,
                        true,
                    )
                } else {
                    Bitmap.createBitmap(
                        decoded.bitmap,
                        0,
                        scaledTop,
                        cropWidth,
                        cropHeight,
                    )
                }
            }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) {
                "Error cropping fallback slice for page ${split.page.index}, offset $top"
            }
            null
        }

    class Factory : Fetcher.Factory<ReaderPageSplit> {
        override fun create(
            data: ReaderPageSplit,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher {
            return ReaderPageSplitFetcher(data, options)
        }
    }
}
