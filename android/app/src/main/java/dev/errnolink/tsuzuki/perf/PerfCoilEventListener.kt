package dev.errnolink.tsuzuki.perf

import android.os.SystemClock
import coil3.EventListener
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.SuccessResult
import coil3.size.Dimension
import coil3.size.Size

class PerfCoilEventListener(
    private val request: ImageRequest,
) : EventListener() {

    private var startUptime = 0L
    private var fetchStartUptime = 0L
    private var fetchEndUptime = 0L
    private var decodeStartUptime = 0L
    private var decodeEndUptime = 0L
    private var resolvedViewWidth: Int? = null
    private var resolvedViewHeight: Int? = null

    override fun onStart(request: ImageRequest) {
        startUptime = SystemClock.uptimeMillis()
        PerfTimelineTracer.onCoilRequestStarted()
    }

    override fun resolveSizeEnd(request: ImageRequest, size: Size) {
        val w = (size.width as? Dimension.Pixels)?.px
        val h = (size.height as? Dimension.Pixels)?.px
        resolvedViewWidth = w
        resolvedViewHeight = h
    }

    override fun fetchStart(request: ImageRequest, fetcher: Fetcher, options: Options) {
        fetchStartUptime = SystemClock.uptimeMillis()
    }

    override fun fetchEnd(request: ImageRequest, fetcher: Fetcher, options: Options, result: FetchResult?) {
        fetchEndUptime = SystemClock.uptimeMillis()
    }

    override fun decodeStart(request: ImageRequest, decoder: Decoder, options: Options) {
        decodeStartUptime = SystemClock.uptimeMillis()
    }

    override fun decodeEnd(request: ImageRequest, decoder: Decoder, options: Options, result: DecodeResult?) {
        decodeEndUptime = SystemClock.uptimeMillis()
    }

    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
        val now = SystemClock.uptimeMillis()
        val durationMs = if (startUptime > 0L && now >= startUptime) {
            now - startUptime
        } else {
            0L
        }
        val fetchMs = if (fetchStartUptime > 0L && fetchEndUptime >= fetchStartUptime) {
            fetchEndUptime - fetchStartUptime
        } else {
            0L
        }
        val decodeMs = if (decodeStartUptime > 0L && decodeEndUptime >= decodeStartUptime) {
            decodeEndUptime - decodeStartUptime
        } else {
            0L
        }
        PerfTimelineTracer.onCoilRequestSuccess(
            source = result.dataSource.name,
            durationMs = durationMs,
            fetchMs = fetchMs,
            decodeMs = decodeMs,
            decodedW = result.image.width,
            decodedH = result.image.height,
            viewW = resolvedViewWidth,
            viewH = resolvedViewHeight,
        )
    }

    override fun onError(request: ImageRequest, result: ErrorResult) {
        PerfTimelineTracer.onCoilRequestFinished()
    }

    override fun onCancel(request: ImageRequest) {
        PerfTimelineTracer.onCoilRequestFinished()
    }

    class Factory : EventListener.Factory {
        override fun create(request: ImageRequest): EventListener {
            return if (PerfTimelineTracer.isEnabled) {
                PerfCoilEventListener(request)
            } else {
                EventListener.NONE
            }
        }
    }
}
