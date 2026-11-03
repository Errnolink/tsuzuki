package dev.errnolink.tsuzuki.perf

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class PerfBucketAggregatorTest {

    @Test
    fun `frame metrics aggregation records counts, jank threshold, and worst frame breakdown`() {
        val aggregator = PerfBucketAggregator()
        val budget60Hz = 1000.0 / 60.0

        aggregator.recordFrame(totalMs = 15.0, uiMs = 8.0, drawMs = 7.0, frameBudgetMs = budget60Hz)
        aggregator.recordFrame(totalMs = 35.0, uiMs = 25.0, drawMs = 10.0, frameBudgetMs = budget60Hz)
        aggregator.recordFrame(totalMs = 20.0, uiMs = 12.0, drawMs = 8.0, frameBudgetMs = budget60Hz)

        val snapshot = aggregator.snapshotAndReset(second = 1, isScrollActive = false)

        assertEquals(3, snapshot.frames)
        assertEquals(1, snapshot.jank)
        assertEquals(35.0, snapshot.worstMs, 0.001)
        assertEquals(25.0, snapshot.worstUiMs, 0.001)
        assertEquals(10.0, snapshot.worstDrawMs, 0.001)
        assertFalse(snapshot.isScrollActive)
    }

    @Test
    fun `looper contention accumulates slow message count and total duration`() {
        val aggregator = PerfBucketAggregator()

        aggregator.recordSlowMessage(durationMs = 60L)
        aggregator.recordSlowMessage(durationMs = 120L)

        val snapshot = aggregator.snapshotAndReset(second = 2, isScrollActive = true)

        assertEquals(2, snapshot.slowMsgs)
        assertEquals(180L, snapshot.slowMsgsTotalMs)
        assertTrue(snapshot.isScrollActive)
    }

    @Test
    fun `coil metrics correctly categorizes data sources and averages durations`() {
        val aggregator = PerfBucketAggregator()

        aggregator.recordCoilSuccess(
            source = "MEMORY",
            durationMs = 6L,
            fetchMs = 2L,
            decodeMs = 4L,
            decodedW = 300,
            decodedH = 450,
            viewW = 150,
            viewH = 225,
            inFlight = 5,
        )
        aggregator.recordCoilSuccess(
            source = "DISK",
            durationMs = 30L,
            fetchMs = 18L,
            decodeMs = 12L,
            decodedW = 300,
            decodedH = 450,
            viewW = 150,
            viewH = 225,
            inFlight = 4,
        )
        aggregator.recordCoilSuccess(
            source = "NETWORK",
            durationMs = 120L,
            fetchMs = 100L,
            decodeMs = 20L,
            decodedW = 300,
            decodedH = 450,
            viewW = 150,
            viewH = 225,
            inFlight = 3,
        )
        aggregator.recordRatioWrite()
        aggregator.recordRatioWrite()

        val snapshot = aggregator.snapshotAndReset(second = 3, isScrollActive = false)

        assertEquals(3, snapshot.coilLoaded)
        assertEquals(1, snapshot.coilMem)
        assertEquals(1, snapshot.coilDisk)
        assertEquals(1, snapshot.coilNet)
        assertEquals(3, snapshot.coilInFlight)
        assertEquals(30L, snapshot.p50LatencyMs)
        assertEquals(120L, snapshot.p90LatencyMs)
        assertEquals(2, snapshot.ratioWrites)
        assertEquals((2L + 18L + 100L) / 3L, snapshot.avgFetchMs)
        assertEquals((4L + 12L + 20L) / 3L, snapshot.avgDecodeMs)
        assertEquals("300x450 vs 150x225", snapshot.sampleSize)
    }

    @Test
    fun `snapshot formatting contains all bucket components`() {
        val aggregator = PerfBucketAggregator()
        aggregator.recordFrame(totalMs = 16.0, uiMs = 10.0, drawMs = 6.0, frameBudgetMs = 16.6667)
        aggregator.recordSlowMessage(75L)
        aggregator.recordCoilSuccess(
            source = "DISK",
            durationMs = 25L,
            fetchMs = 10L,
            decodeMs = 8L,
            decodedW = 300,
            decodedH = 400,
            viewW = 150,
            viewH = 200,
            inFlight = 2,
        )
        aggregator.recordRatioWrite()

        val snapshot = aggregator.snapshotAndReset(second = 1, isScrollActive = true)
        val formatted = snapshot.format()

        assertTrue(formatted.startsWith("[TsuzukiPerf] [Bucket 1s]"))
        assertTrue(formatted.contains("frames=1 jank=0"))
        assertTrue(formatted.contains("looper >50ms: 1 (75ms)"))
        assertTrue(formatted.contains("coil: 1 loaded"))
        assertTrue(formatted.contains("scroll: active"))
        assertTrue(formatted.contains("ratios=1 writes"))
    }

    @Test
    fun `pruneOldLogs leaves exactly requested count of newest files`() {
        val aggregator = PerfBucketAggregator()
        val tempDir = File.createTempFile("perf_test", "").apply {
            delete()
            mkdir()
        }
        try {
            val files = (1..14).map { index ->
                val name = String.format("launch-20261011-%02d0000.log", index)
                File(tempDir, name).apply { writeText("log $index") }
            }

            val toPrune = aggregator.pruneOldLogs(files, keepCount = 10)

            assertEquals(4, toPrune.size)
            assertEquals("launch-20261011-010000.log", toPrune[0].name)
            assertEquals("launch-20261011-040000.log", toPrune[3].name)

            val remaining = files - toPrune.toSet()
            assertEquals(10, remaining.size)
            assertEquals("launch-20261011-050000.log", remaining.first().name)
            assertEquals("launch-20261011-140000.log", remaining.last().name)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `pruneOldLogs returns empty when file count is within limit`() {
        val aggregator = PerfBucketAggregator()
        val files = (1..8).map { index ->
            File("launch-20261011-$index.log")
        }

        val toPrune = aggregator.pruneOldLogs(files, keepCount = 10)
        assertTrue(toPrune.isEmpty())
    }
}
