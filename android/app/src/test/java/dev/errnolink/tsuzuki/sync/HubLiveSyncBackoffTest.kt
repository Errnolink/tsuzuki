package dev.errnolink.tsuzuki.sync

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class HubLiveSyncBackoffTest {

    @Test
    fun `retry delays spread across the backoff window instead of one fixed value`() {
        val delays = (1..100).map { HubLiveSync.retryDelayMs(2, Random(it.toLong() * 13 + 1)) }

        assertTrue(delays.all { it in 2_500L..10_000L }, "every delay stays inside the 2.5s..10s window for two failures")
        assertTrue(delays.toSet().size > 50, "separate clients must not land on the same retry delay")
    }

    @Test
    fun `retry delay never exceeds the sixty second cap however many failures pile up`() {
        val delays = (1..100).map { HubLiveSync.retryDelayMs(40, Random(it.toLong())) }

        assertTrue(delays.all { it in 2_500L..60_000L }, "full jitter is bounded by the 60s cap")
    }
}
