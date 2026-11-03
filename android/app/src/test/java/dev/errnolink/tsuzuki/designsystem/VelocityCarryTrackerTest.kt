package dev.errnolink.tsuzuki.designsystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class VelocityCarryTrackerTest {
    private fun pacedSwipe(tracker: VelocityCarryTracker, steps: Int = 20, stepPx: Float = 30f, stepMillis: Long = 3L): Float {
        tracker.onStart(0f)
        var position = 0f
        repeat(steps) {
            Thread.sleep(stepMillis)
            position += stepPx
            tracker.onMove(position)
        }
        return position
    }

    @Test
    fun `fast swipe with zero tracked velocity carries the gesture speed`() {
        val tracker = VelocityCarryTracker()
        val end = pacedSwipe(tracker)
        val release = tracker.resolveReleaseVelocity(0f, end)
        assertTrue(release > 1000f, "carried $release")
    }

    @Test
    fun `hold longer than the freshness window decays sampled speed and stale carry`() {
        val tracker = VelocityCarryTracker(freshWindowMs = 60f)
        val end = pacedSwipe(tracker)
        assertTrue(tracker.recentSampledVelocity() > 0f)
        Thread.sleep(400)
        assertEquals(0f, tracker.recentSampledVelocity())
        val release = tracker.resolveReleaseVelocity(0f, end)
        assertTrue(release < 3000f, "stale carry $release")
        assertTrue(tracker.recentSampledVelocity() == 0f)
    }

    @Test
    fun `strong tracked finger velocity wins over the sample`() {
        val tracker = VelocityCarryTracker()
        val end = pacedSwipe(tracker, steps = 10, stepPx = 15f, stepMillis = 6L)
        val release = tracker.resolveReleaseVelocity(1500f, end)
        assertTrue(release >= 1500f, "tracked lost: $release")
    }

    @Test
    fun `negative direction gestures keep their sign`() {
        val tracker = VelocityCarryTracker()
        tracker.onStart(0f)
        var position = 0f
        repeat(12) {
            Thread.sleep(3L)
            position -= 60f
            tracker.onMove(position)
        }
        val release = tracker.resolveReleaseVelocity(0f, position)
        assertTrue(release < -1000f, "carried $release")
        assertTrue(tracker.recentSampledVelocity() < 0f)
    }

    @Test
    fun `freshness window gates the sampled speed`() {
        val tracker = VelocityCarryTracker(freshWindowMs = 60f)
        tracker.onStart(0f)
        tracker.onMove(200f)
        tracker.onMove(400f)
        assertTrue(tracker.recentSampledVelocity() != 0f)
        Thread.sleep(90)
        assertEquals(0f, tracker.recentSampledVelocity())
    }

    @Test
    fun `slow constant drag falls back to the whole gesture average`() {
        val tracker = VelocityCarryTracker(freshWindowMs = 60f)
        tracker.onStart(0f)
        var position = 0f
        repeat(5) {
            Thread.sleep(20)
            position += 2f
            tracker.onMove(position)
        }
        Thread.sleep(70)
        val release = tracker.resolveReleaseVelocity(0f, position)
        assertTrue(release in 30f..150f, "average fallback $release")
    }

    @Test
    fun `identical timestamps and repeated positions never divide by zero`() {
        val tracker = VelocityCarryTracker()
        tracker.onStart(0f)
        repeat(1000) { tracker.onMove(0f) }
        assertEquals(0f, tracker.recentSampledVelocity())
        assertEquals(0f, tracker.resolveReleaseVelocity(0f, 0f))
        assertTrue(tracker.recentSampledVelocity().isFinite())
    }

    @Test
    fun `reuse without reset starts a clean gesture`() {
        val tracker = VelocityCarryTracker()
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
        val tracker = VelocityCarryTracker()
        tracker.onMove(50f)
        tracker.onMove(150f)
        val release = tracker.resolveReleaseVelocity(0f, 150f)
        assertTrue(release > 0f)
        tracker.reset()
        assertEquals(0f, tracker.resolveReleaseVelocity(0f, 0f))
    }
}
