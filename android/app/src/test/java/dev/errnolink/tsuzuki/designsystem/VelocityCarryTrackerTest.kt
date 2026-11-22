package dev.errnolink.tsuzuki.designsystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class VelocityCarryTrackerTest {
    private var now = 1_000_000_000L
    private fun tracker() = VelocityCarryTracker(freshWindowMs = 60f) { now }
    private fun advance(ms: Long) { now += ms * 1_000_000L }

    private fun pacedSwipe(tracker: VelocityCarryTracker, steps: Int = 20, stepPx: Float = 30f, stepMillis: Long = 3L): Float {
        tracker.onStart(0f)
        var position = 0f
        repeat(steps) {
            advance(stepMillis)
            position += stepPx
            tracker.onMove(position)
        }
        return position
    }

    @Test
    fun `fast swipe with zero tracked velocity carries the gesture speed`() {
        val tracker = tracker()
        val end = pacedSwipe(tracker)
        val release = tracker.resolveReleaseVelocity(0f, end)
        assertTrue(release > 1000f, "carried $release")
    }

    @Test
    fun `hold longer than the freshness window decays sampled speed and stale carry`() {
        val tracker = tracker()
        val end = pacedSwipe(tracker)
        assertTrue(tracker.recentSampledVelocity() > 0f)
        advance(400)
        assertEquals(0f, tracker.recentSampledVelocity())
        val release = tracker.resolveReleaseVelocity(0f, end)
        assertTrue(release < 3000f, "stale carry $release")
        assertTrue(tracker.recentSampledVelocity() == 0f)
    }

    @Test
    fun `strong tracked finger velocity wins over the sample`() {
        val tracker = tracker()
        val end = pacedSwipe(tracker, steps = 10, stepPx = 15f, stepMillis = 6L)
        val release = tracker.resolveReleaseVelocity(1500f, end)
        assertTrue(release >= 1500f, "tracked lost: $release")
    }

    @Test
    fun `negative direction gestures keep their sign`() {
        val tracker = tracker()
        tracker.onStart(0f)
        var position = 0f
        repeat(12) {
            advance(3L)
            position -= 60f
            tracker.onMove(position)
        }
        val release = tracker.resolveReleaseVelocity(0f, position)
        assertTrue(release < -1000f, "carried $release")
        assertTrue(tracker.recentSampledVelocity() < 0f)
    }

    @Test
    fun `freshness window gates the sampled speed`() {
        val tracker = tracker()
        tracker.onStart(0f)
        tracker.onMove(200f)
        advance(5)
        tracker.onMove(400f)
        assertTrue(tracker.recentSampledVelocity() != 0f)
        advance(90)
        assertEquals(0f, tracker.recentSampledVelocity())
    }

    @Test
    fun `slow constant drag falls back to the whole gesture average`() {
        val tracker = tracker()
        tracker.onStart(0f)
        var position = 0f
        repeat(5) {
            advance(20)
            position += 2f
            tracker.onMove(position)
        }
        advance(70)
        val release = tracker.resolveReleaseVelocity(0f, position)
        assertTrue(release in 30f..150f, "average fallback $release")
    }

    @Test
    fun `identical timestamps and repeated positions never divide by zero`() {
        val tracker = tracker()
        tracker.onStart(0f)
        repeat(1000) { tracker.onMove(0f) }
        assertEquals(0f, tracker.recentSampledVelocity())
        assertEquals(0f, tracker.resolveReleaseVelocity(0f, 0f))
        assertTrue(tracker.recentSampledVelocity().isFinite())
    }

    @Test
    fun `reuse without reset starts a clean gesture`() {
        val tracker = tracker()
        pacedSwipe(tracker, steps = 12, stepPx = 60f)
        tracker.reset()
        assertEquals(0f, tracker.recentSampledVelocity())
        val end = pacedSwipe(tracker, steps = 10, stepPx = 15f, stepMillis = 6L)
        val release = tracker.resolveReleaseVelocity(0f, end)
        assertTrue(release > 200f, "second gesture dead: $release")
        assertTrue(release < 30000f, "previous gesture leaked: $release")
    }

    @Test
    fun `move without start initializes implicitly`() {
        val tracker = tracker()
        tracker.onMove(50f)
        advance(5)
        tracker.onMove(150f)
        val release = tracker.resolveReleaseVelocity(0f, 150f)
        assertTrue(release > 0f)
        tracker.reset()
        assertEquals(0f, tracker.resolveReleaseVelocity(0f, 0f))
    }
}
