package dev.errnolink.tsuzuki.perf

import java.io.File
import java.util.Locale

class PerfBucketAggregator {

    private val frameLock = Any()
    private var frameCount = 0
    private var jankCount = 0
    private var worstFrameMs = 0.0
    private var worstUiMs = 0.0
    private var worstDrawMs = 0.0

    private val looperLock = Any()
    private var slowMessageCount = 0
    private var slowMessageTotalMs = 0L

    private val coilLock = Any()
    private var coilCompletedCount = 0
    private var coilMemoryHits = 0
    private var coilDiskHits = 0
    private var coilNetworkHits = 0
    private var coilTotalFetchMs = 0L
    private var coilTotalDecodeMs = 0L
    private var coilInFlightCount = 0
    private val coilDurations = mutableListOf<Long>()
    private var lastDecodedWidth = 0
    private var lastDecodedHeight = 0
    private var lastViewWidth: Int? = null
    private var lastViewHeight: Int? = null

    private val ratioLock = Any()
    private var ratioWritesCount = 0

    fun recordFrame(totalMs: Double, uiMs: Double, drawMs: Double, frameBudgetMs: Double) {
        synchronized(frameLock) {
            frameCount++
            val isJank = totalMs > 16.7 && totalMs > (2.0 * frameBudgetMs)
            if (isJank) {
                jankCount++
            }
            if (totalMs > worstFrameMs) {
                worstFrameMs = totalMs
                worstUiMs = uiMs
                worstDrawMs = drawMs
            }
        }
    }

    fun recordSlowMessage(durationMs: Long) {
        synchronized(looperLock) {
            slowMessageCount++
            slowMessageTotalMs += durationMs
        }
    }

    fun recordCoilSuccess(
        source: String,
        durationMs: Long,
        fetchMs: Long,
        decodeMs: Long,
        decodedW: Int,
        decodedH: Int,
        viewW: Int?,
        viewH: Int?,
        inFlight: Int,
    ) {
        synchronized(coilLock) {
            coilCompletedCount++
            when (source.uppercase(Locale.US)) {
                "MEMORY", "MEMORY_CACHE" -> coilMemoryHits++
                "DISK", "DISK_CACHE" -> coilDiskHits++
                "NETWORK" -> coilNetworkHits++
                else -> coilDiskHits++
            }
            coilDurations.add(durationMs)
            coilTotalFetchMs += fetchMs
            coilTotalDecodeMs += decodeMs
            lastDecodedWidth = decodedW
            lastDecodedHeight = decodedH
            lastViewWidth = viewW
            lastViewHeight = viewH
            coilInFlightCount = inFlight
        }
    }

    fun updateCoilInFlight(inFlight: Int) {
        synchronized(coilLock) {
            coilInFlightCount = inFlight
        }
    }

    fun recordRatioWrite() {
        synchronized(ratioLock) {
            ratioWritesCount++
        }
    }

    data class Snapshot(
        val second: Int,
        val frames: Int,
        val jank: Int,
        val worstMs: Double,
        val worstUiMs: Double,
        val worstDrawMs: Double,
        val slowMsgs: Int,
        val slowMsgsTotalMs: Long,
        val coilLoaded: Int,
        val coilMem: Int,
        val coilDisk: Int,
        val coilNet: Int,
        val coilInFlight: Int,
        val p50LatencyMs: Long,
        val p90LatencyMs: Long,
        val avgFetchMs: Long,
        val avgDecodeMs: Long,
        val sampleSize: String?,
        val ratioWrites: Int,
        val isScrollActive: Boolean,
    ) {
        fun format(): String {
            val framePart = String.format(
                Locale.US,
                "frames=%d jank=%d worst=%.1fms (ui=%.1fms, draw=%.1fms)",
                frames,
                jank,
                worstMs,
                worstUiMs,
                worstDrawMs,
            )
            val looperPart = String.format(
                Locale.US,
                "looper >50ms: %d (%dms)",
                slowMsgs,
                slowMsgsTotalMs,
            )
            val coilPart = if (second <= 20) {
                if (sampleSize != null) {
                    String.format(
                        Locale.US,
                        "coil: %d loaded (mem=%d, disk=%d, net=%d, inflight=%d, p50=%dms, p90=%dms, fetch=%dms, dec=%dms, size=%s)",
                        coilLoaded,
                        coilMem,
                        coilDisk,
                        coilNet,
                        coilInFlight,
                        p50LatencyMs,
                        p90LatencyMs,
                        avgFetchMs,
                        avgDecodeMs,
                        sampleSize,
                    )
                } else {
                    String.format(
                        Locale.US,
                        "coil: %d loaded (mem=%d, disk=%d, net=%d, inflight=%d, p50=%dms, p90=%dms)",
                        coilLoaded,
                        coilMem,
                        coilDisk,
                        coilNet,
                        coilInFlight,
                        p50LatencyMs,
                        p90LatencyMs,
                    )
                }
            } else {
                String.format(Locale.US, "coil: inflight=%d", coilInFlight)
            }
            val ratioPart = String.format(Locale.US, "ratios=%d writes", ratioWrites)
            val scrollPart = if (isScrollActive) "scroll: active" else "scroll: idle"
            return String.format(
                Locale.US,
                "[TsuzukiPerf] [Bucket %ds] %s | %s | %s | %s | %s",
                second,
                framePart,
                looperPart,
                coilPart,
                ratioPart,
                scrollPart,
            )
        }
    }

    fun snapshotAndReset(second: Int, isScrollActive: Boolean): Snapshot {
        val fCount: Int
        val jCount: Int
        val wMs: Double
        val wUiMs: Double
        val wDrawMs: Double
        synchronized(frameLock) {
            fCount = frameCount
            jCount = jankCount
            wMs = worstFrameMs
            wUiMs = worstUiMs
            wDrawMs = worstDrawMs
            frameCount = 0
            jankCount = 0
            worstFrameMs = 0.0
            worstUiMs = 0.0
            worstDrawMs = 0.0
        }

        val lCount: Int
        val lTotalMs: Long
        synchronized(looperLock) {
            lCount = slowMessageCount
            lTotalMs = slowMessageTotalMs
            slowMessageCount = 0
            slowMessageTotalMs = 0L
        }

        val cLoaded: Int
        val cMem: Int
        val cDisk: Int
        val cNet: Int
        val cInFlight: Int
        val p50: Long
        val p90: Long
        val avgFetch: Long
        val avgDec: Long
        val sizeStr: String?
        synchronized(coilLock) {
            cLoaded = coilCompletedCount
            cMem = coilMemoryHits
            cDisk = coilDiskHits
            cNet = coilNetworkHits
            cInFlight = coilInFlightCount
            if (coilDurations.isNotEmpty()) {
                val sorted = coilDurations.sorted()
                p50 = sorted[sorted.size / 2]
                val p90Index = (sorted.size * 9) / 10
                p90 = sorted[p90Index.coerceAtMost(sorted.size - 1)]
            } else {
                p50 = 0L
                p90 = 0L
            }
            avgFetch = if (cLoaded > 0) coilTotalFetchMs / cLoaded else 0L
            avgDec = if (cLoaded > 0) coilTotalDecodeMs / cLoaded else 0L
            sizeStr = if (lastDecodedWidth > 0 && lastDecodedHeight > 0) {
                if (lastViewWidth != null && lastViewHeight != null) {
                    "${lastDecodedWidth}x${lastDecodedHeight} vs ${lastViewWidth}x${lastViewHeight}"
                } else {
                    "${lastDecodedWidth}x${lastDecodedHeight}"
                }
            } else {
                null
            }
            coilCompletedCount = 0
            coilMemoryHits = 0
            coilDiskHits = 0
            coilNetworkHits = 0
            coilDurations.clear()
            coilTotalFetchMs = 0L
            coilTotalDecodeMs = 0L
        }

        val rWrites: Int
        synchronized(ratioLock) {
            rWrites = ratioWritesCount
            ratioWritesCount = 0
        }

        return Snapshot(
            second = second,
            frames = fCount,
            jank = jCount,
            worstMs = wMs,
            worstUiMs = wUiMs,
            worstDrawMs = wDrawMs,
            slowMsgs = lCount,
            slowMsgsTotalMs = lTotalMs,
            coilLoaded = cLoaded,
            coilMem = cMem,
            coilDisk = cDisk,
            coilNet = cNet,
            coilInFlight = cInFlight,
            p50LatencyMs = p50,
            p90LatencyMs = p90,
            avgFetchMs = avgFetch,
            avgDecodeMs = avgDec,
            sampleSize = sizeStr,
            ratioWrites = rWrites,
            isScrollActive = isScrollActive,
        )
    }

    fun pruneOldLogs(files: List<File>, keepCount: Int = 10): List<File> {
        if (files.size <= keepCount) return emptyList()
        val sorted = files.sortedWith(compareBy({ it.name }, { it.lastModified() }))
        val deleteCount = files.size - keepCount
        return sorted.take(deleteCount)
    }
}
