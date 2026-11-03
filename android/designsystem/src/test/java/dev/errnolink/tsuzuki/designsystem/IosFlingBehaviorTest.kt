package dev.errnolink.tsuzuki.designsystem

import androidx.compose.foundation.gestures.ScrollScope
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.pow

class IosFlingBehaviorTest {
    private class ScriptedScope(private val scrollLimit: Float = Float.MAX_VALUE) : ScrollScope {
        var consumedTotal = 0f
        var scrollCalls = 0

        override fun scrollBy(pixels: Float): Float {
            scrollCalls++
            val room = (scrollLimit - abs(consumedTotal)).coerceAtLeast(0f)
            val consumed = pixels.coerceIn(-room, room)
            consumedTotal += consumed
            return consumed
        }
    }

    private fun frameClock(frameMillis: Long = 16L): suspend (Long) -> Long {
        var t = 0L
        return { _ ->
            t += frameMillis * 1_000_000L
            t
        }
    }

    private suspend fun fling(behavior: IosFlingBehavior, scope: ScriptedScope, velocity: Float): Float =
        with(behavior) { scope.performFling(velocity) }

    @Test
    fun `total glide distance matches the geometric series`() = runBlocking {
        val scope = ScriptedScope()
        val behavior = IosFlingBehavior(frameClock())
        fling(behavior, scope, 3000f)
        val continuousPx = (3000f / 1000f) * IosDecayRatePerMillis / (1f - IosDecayRatePerMillis)
        assertTrue(scope.consumedTotal in (continuousPx * 0.97f)..(continuousPx * 1.06f))
        assertTrue(scope.scrollCalls in 150..220)
    }

    @Test
    fun `velocity fades instead of stopping abruptly`() = runBlocking {
        val scope = ScriptedScope()
        val behavior = IosFlingBehavior(frameClock())
        with(behavior) { scope.performFling(3000f) }
        val frameSeconds = 16f / 1000f
        val discreteRate = IosDecayRatePerMillis.pow(frameSeconds * 1000f)
        var expected = 3000f * frameSeconds
        var sum = 0f
        repeat(scope.scrollCalls) {
            sum += expected
            expected *= discreteRate
        }
        assertEquals(sum, scope.consumedTotal, sum * 0.02f + 1f)
    }

    @Test
    fun `edge hit hands back the velocity at the edge`() = runBlocking {
        val scope = ScriptedScope(scrollLimit = 300f)
        val behavior = IosFlingBehavior(frameClock())
        val leftover = fling(behavior, scope, 3000f)
        assertTrue(leftover in 1900f..2800f)
        assertEquals(300f, scope.consumedTotal, 1f)
    }

    @Test
    fun `negligible velocity is not animated`() = runBlocking {
        val scope = ScriptedScope()
        val behavior = IosFlingBehavior(frameClock())
        val leftover = fling(behavior, scope, 5f)
        assertEquals(5f, leftover)
        assertEquals(0, scope.scrollCalls)
    }

    @Test
    fun `sign is preserved toward the start`() = runBlocking {
        val scope = ScriptedScope(scrollLimit = 200f)
        val behavior = IosFlingBehavior(frameClock())
        val leftover = fling(behavior, scope, -3000f)
        assertTrue(leftover < -1900f)
        assertEquals(-200f, scope.consumedTotal, 1f)
    }

    @Test
    fun `a dropped frame moves one clamped step, not the whole gap`() = runBlocking {
        val clampStep = 3000f * IosFlingMaxFrameSeconds
        val scope = ScriptedScope(scrollLimit = clampStep + 1f)
        val behavior = IosFlingBehavior(frameClock(frameMillis = 600L))
        val leftover = fling(behavior, scope, 3000f)
        assertTrue(leftover > 0f)
        assertTrue(scope.consumedTotal <= clampStep + 1f)
    }
}
