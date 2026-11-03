package eu.kanade.tachiyomi.data.sync

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CapabilityRecheckTest {

    @Test
    fun `reprobe required when never probed before`() {
        val now = 100_000_000L
        assertTrue(shouldReprobeCapabilities(lastProbedAt = 0L, now = now))
        assertTrue(shouldReprobeCapabilities(lastProbedAt = -1L, now = now))
    }

    @Test
    fun `reprobe not required when recently probed within 24 hours`() {
        val lastProbed = 100_000_000L
        val withinWindow = lastProbed + (12 * 60 * 60 * 1000L) // 12 hours later
        assertFalse(shouldReprobeCapabilities(lastProbedAt = lastProbed, now = withinWindow))
    }

    @Test
    fun `reprobe required when cached list is older than 24 hours`() {
        val lastProbed = 100_000_000L
        val past24h = lastProbed + (24 * 60 * 60 * 1000L) // exactly 24 hours
        val past25h = lastProbed + (25 * 60 * 60 * 1000L) // 25 hours
        assertTrue(shouldReprobeCapabilities(lastProbedAt = lastProbed, now = past24h))
        assertTrue(shouldReprobeCapabilities(lastProbedAt = lastProbed, now = past25h))
    }

    @Test
    fun `reprobe triggered on unknown hub seq jump`() {
        val lastProbed = 100_000_000L
        val withinWindow = lastProbed + 1000L // 1 second later

        // Normal sequential advance (e.g. 10 items) does not trigger reprobe
        assertFalse(
            shouldReprobeCapabilities(
                lastProbedAt = lastProbed,
                now = withinWindow,
                lastCursor = 100L,
                responseCursor = 110L,
                seqJumpThreshold = 1000L,
            ),
        )

        // Large jump (>= seqJumpThreshold, e.g. 100 -> 1200) triggers reprobe
        assertTrue(
            shouldReprobeCapabilities(
                lastProbedAt = lastProbed,
                now = withinWindow,
                lastCursor = 100L,
                responseCursor = 1200L,
                seqJumpThreshold = 1000L,
            ),
        )
    }
}
