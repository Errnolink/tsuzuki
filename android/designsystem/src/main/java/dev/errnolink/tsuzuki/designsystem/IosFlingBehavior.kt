package dev.errnolink.tsuzuki.designsystem

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import kotlin.math.abs
import kotlin.math.pow

val LocalIosFling = staticCompositionLocalOf<FlingBehavior?> { null }

const val IosDecayRatePerMillis = 0.998f
const val IosFlingStopPxPerSecond = 8f
const val IosFlingMaxFrameSeconds = 1f / 30f

class IosFlingBehavior(
    private val nextFrameNanos: suspend (Long) -> Long = { withFrameNanos { it } },
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        var velocity = initialVelocity
        if (abs(velocity) <= IosFlingStopPxPerSecond) return velocity
        var lastNanos = 0L
        while (abs(velocity) > IosFlingStopPxPerSecond) {
            val now = nextFrameNanos(lastNanos)
            val dtSeconds = if (lastNanos == 0L) {
                0f
            } else {
                ((now - lastNanos) / 1e9f).coerceIn(0f, IosFlingMaxFrameSeconds)
            }
            lastNanos = now
            if (dtSeconds <= 0f) continue
            val delta = velocity * dtSeconds
            val consumed = scrollBy(delta)
            if (abs(delta - consumed) > 0.5f) return velocity
            velocity *= IosDecayRatePerMillis.pow(dtSeconds * 1000f)
        }
        return 0f
    }
}

@Composable
fun rememberIosFlingBehavior(): IosFlingBehavior = remember { IosFlingBehavior() }

@Composable
fun tsuzukiFlingBehavior(): FlingBehavior = LocalIosFling.current ?: rememberIosFlingBehavior()
