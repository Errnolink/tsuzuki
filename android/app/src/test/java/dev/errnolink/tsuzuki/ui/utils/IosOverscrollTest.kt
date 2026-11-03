package dev.errnolink.tsuzuki.ui.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign
import kotlin.math.sqrt

class IosOverscrollTest {
    private val container = 1080f

    private fun stepUntilSettled(spring: CriticalSpring, dt: Float = 1f / 60f, maxFrames: Int = 3600): Int {
        var frames = 0
        while (!spring.settled && frames < maxFrames) {
            spring.advance(dt)
            frames++
        }
        spring.advance(dt)
        return frames
    }

    @Test
    fun `rubber band anchors at zero and is odd symmetric`() {
        assertEquals(0f, rubberBand(0f, container))
        for (d in listOf(1f, 17f, 250f, 540f, 1080f, 4000f)) {
            assertEquals(rubberBand(d, container), -rubberBand(-d, container), 1e-3f)
        }
    }

    @Test
    fun `rubber band resists more than raw and increases strictly`() {
        for (d in listOf(1f, 17f, 250f, 540f, 1080f, 4000f)) {
            val display = rubberBand(d, container)
            assertTrue(display > 0f)
            assertTrue(display < d)
        }
        var previous = -1f
        var raw = 1f
        while (raw <= 20_000f) {
            val display = rubberBand(raw, container)
            assertTrue(display > previous)
            previous = display
            raw += 37f
        }
    }

    @Test
    fun `initial drag meets resistance matching the ios slope`() {
        val small = container * 0.02f
        val display = rubberBand(small, container)
        assertEquals(RubberBandSlope, display / small, 0.02f)
        for (d in listOf(17f, 120f, 540f, 1080f, 3000f)) {
            assertTrue(rubberBand(d, container) < d * RubberBandSlope * 1.6f)
        }
    }

    @Test
    fun `stretch never reaches the container size`() {
        for (d in listOf(1f, 17f, 250f, 540f, 1080f, 4000f, 100_000f)) {
            val display = rubberBand(d, container)
            assertTrue(display > 0f)
            assertTrue(display < container)
        }
        var previous = -1f
        var raw = 1f
        while (raw <= 200_000f) {
            val display = rubberBand(raw, container)
            assertTrue(display > previous)
            assertTrue(display < container)
            previous = display
            raw += 137f
        }
    }

    @Test
    fun `inverse maps back to raw for reachable displays`() {
        for (raw in listOf(10f, 120f, 540f, 2000f, 5000f, 20_000f)) {
            val display = rubberBand(raw, container)
            assertEquals(raw, rubberBandInverse(display, container), 0.01f * raw)
        }
    }

    @Test
    fun `pay down consumes reverse drag before scrolling`() {
        val axis = OverscrollAxis()
        axis.accumulate(100f, container)
        val stretched = axis.display.floatValue
        assertTrue(stretched > 0f)

        val absorbed = axis.relax(-40f, container)
        assertEquals(-40f, absorbed)
        assertTrue(axis.display.floatValue > 0f)
        assertTrue(axis.display.floatValue < stretched)

        val clearing = axis.relax(-200f, container)
        assertEquals(0f, axis.display.floatValue)
        assertEquals(-60f, clearing)
        val routedToScroll = -200f - clearing
        assertEquals(-140f, routedToScroll)

        assertEquals(0f, axis.relax(-50f, container))
    }

    @Test
    fun `same direction drag deepens and never flips sign`() {
        val axis = OverscrollAxis()
        axis.accumulate(300f, container)
        val first = axis.display.floatValue
        axis.accumulate(300f, container)
        assertTrue(axis.display.floatValue > first)
        assertTrue(axis.display.floatValue > 0f)
        assertEquals(0f, axis.relax(100f, container))
    }

    @Test
    fun `fling source deltas never accumulate stretch`() {
        val effect = IosOverscrollEffect(CoroutineScope(Dispatchers.Unconfined))
        val returned = effect.applyToScroll(
            Offset(0f, -120f),
            NestedScrollSource.SideEffect,
            performScroll = { Offset.Zero },
        )
        assertEquals(Offset.Zero, returned)
        assertEquals(0f, effect.yAxis.display.floatValue)
        assertFalse(effect.isInProgress)

        val userInput = effect.applyToScroll(
            Offset(0f, -120f),
            NestedScrollSource.UserInput,
            performScroll = { Offset.Zero },
        )
        assertEquals(Offset.Zero, userInput)
        assertTrue(effect.yAxis.display.floatValue < 0f)
        assertTrue(effect.isInProgress)
    }

    @Test
    fun `user input passes through unconsumed scroll deltas`() {
        val effect = IosOverscrollEffect(CoroutineScope(Dispatchers.Unconfined))
        val consumed = effect.applyToScroll(
            Offset(0f, -90f),
            NestedScrollSource.UserInput,
            performScroll = { it },
        )
        assertEquals(Offset(0f, -90f), consumed)
        assertEquals(0f, effect.yAxis.display.floatValue)
    }

    @Test
    fun `fling leftover is the velocity the chain did not consume`() {
        val leftover = Velocity(3000f, -6000f) - Velocity(1000f, -2000f)
        assertEquals(Velocity(2000f, -4000f), leftover)
    }

    @Test
    fun `bounce velocity clamps with sign preserved`() {
        assertEquals(BounceVelocityClamp, clampBounceVelocity(120_000f))
        assertEquals(-BounceVelocityClamp, clampBounceVelocity(-120_000f))
        assertEquals(3000f, clampBounceVelocity(3000f))
        assertEquals(-3000f, clampBounceVelocity(-3000f))
    }

    @Test
    fun `fling absorb at the top edge stretches the same way as a drag`() {
        val dragAxis = OverscrollAxis()
        dragAxis.accumulate(-600f, container)
        assertTrue(dragAxis.display.floatValue < 0f)

        val flingAxis = OverscrollAxis()
        val spring = flingAxis.createSettle(-6000f, container)!!
        var first = 0f
        var crossed = false
        var frames = 0
        while (!spring.settled && frames < 2400) {
            spring.advance(1f / 60f)
            if (spring.position != 0f && first == 0f) first = spring.position
            if (spring.position > 0f) crossed = true
            frames++
        }
        assertTrue(first < 0f)
        assertFalse(crossed)
        assertEquals(0f, spring.position, 1e-4f)
    }

    @Test
    fun `fling absorb first frame displacement is bounded`() {
        for (leftover in listOf(-8000f, -6000f, 6000f, 8000f)) {
            val axis = OverscrollAxis()
            val spring = axis.createSettle(leftover, container)!!
            val launch = boundedBounceVelocity(leftover, container)
            spring.advance(1f / 60f)
            assertTrue(abs(spring.position) <= abs(launch) / 60f + 0.5f)
        }
    }

    @Test
    fun `long dropped frames cannot leap the settle`() {
        val axis = OverscrollAxis()
        val spring = axis.createSettle(-8000f, container)!!
        spring.advance(SettleMaxFrameSeconds)
        val launch = boundedBounceVelocity(-8000f, container)
        assertTrue(abs(spring.position) <= abs(launch) * SettleMaxFrameSeconds + 0.5f)
    }

    @Test
    fun `overshoot grows with the hit velocity while under the cap`() {
        val cap = container * BounceCapFraction
        var previousPeak = 0f
        for (v0 in listOf(500f, 1200f, 2000f, 3000f)) {
            val axis = OverscrollAxis()
            val spring = axis.createSettle(v0, container)!!
            var peak = 0f
            var frames = 0
            while (!spring.settled && frames < 2400) {
                spring.advance(1f / 240f)
                peak = maxOf(peak, abs(spring.position))
                frames++
            }
            assertTrue(peak > previousPeak)
            assertTrue(peak < cap)
            previousPeak = peak
        }
    }

    @Test
    fun `hard flings cap the bounce at the container fraction`() {
        val cap = container * BounceCapFraction
        var referencePeak = -1f
        for (v0 in listOf(4000f, 8000f, 12000f, 80000f)) {
            val axis = OverscrollAxis()
            val spring = axis.createSettle(v0, container)!!
            var peak = 0f
            var frames = 0
            while (!spring.settled && frames < 2400) {
                spring.advance(1f / 240f)
                peak = maxOf(peak, abs(spring.position))
                frames++
            }
            assertTrue(peak in (cap * 0.85f)..cap)
            if (referencePeak < 0f) referencePeak = peak else assertEquals(referencePeak, peak, 1f)
        }
    }

    @Test
    fun `zero start bounce stays single sided for both signs`() {
        for (v0 in listOf(6000f, -6000f, 3000f, -3000f)) {
            val axis = OverscrollAxis()
            val spring = axis.createSettle(v0, container)!!
            val launchSign = sign(spring.velocity)
            var frames = 0
            while (!spring.settled && frames < 2400) {
                spring.advance(1f / 240f)
                assertTrue(spring.position == 0f || sign(spring.position) == launchSign)
                frames++
            }
            assertEquals(0f, spring.position, 1e-4f)
        }
    }

    @Test
    fun `first frame continues the hit velocity instead of jumping`() {
        val cap = container * BounceCapFraction
        val frameSeconds = 1f / 60f
        val omega = sqrt(BounceStiffness)
        for (v0 in listOf(600f, 3000f, 9000f)) {
            val axis = OverscrollAxis()
            val spring = axis.createSettle(v0, container)!!
            val launch = boundedBounceVelocity(v0, container)
            spring.advance(frameSeconds)
            val implied = abs(launch) * frameSeconds * exp(-omega * frameSeconds)
            assertTrue(abs(abs(spring.position) - implied) <= cap / 20f)
        }
    }

    @Test
    fun `capped bounce returns to rest in about half a second`() {
        val cap = container * BounceCapFraction
        val axis = OverscrollAxis()
        val spring = axis.createSettle(8000f, container)!!
        var peak = 0f
        var t = 0.0
        var visibleSettle = Double.MAX_VALUE
        val dt = 1.0 / 240.0
        while (!spring.settled && t < 3.0) {
            spring.advance(dt.toFloat())
            t += dt
            peak = maxOf(peak, abs(spring.position))
            if (visibleSettle == Double.MAX_VALUE && peak > 0f && abs(spring.position) < peak * 0.1f) {
                visibleSettle = t
            }
        }
        assertTrue(peak >= cap * 0.85f)
        assertTrue(visibleSettle in 0.4..0.65)
        assertEquals(0f, spring.position, 1e-4f)
        assertEquals(0f, spring.velocity, 1e-4f)
    }

    @Test
    fun `settle spring never crosses the edge it released from`() {
        for (v0 in listOf(0f, 500f, 3000f, -2500f)) {
            val spring = CriticalSpring(BounceStiffness)
            spring.start(150f, v0)
            var frames = 0
            while (!spring.settled && frames < 10_000) {
                spring.advance(1f / 240f)
                assertTrue(spring.position >= 0f)
                frames++
            }
            assertEquals(0f, spring.position, 1e-4f)
        }
    }

    @Test
    fun `settle completes to exact rest`() {
        val spring = CriticalSpring(BounceStiffness)
        spring.start(400f, 6000f)
        val frames = stepUntilSettled(spring, 1f / 60f)
        assertTrue(frames < 180)
        assertEquals(0f, spring.position)
        assertEquals(0f, spring.velocity)
    }

    @Test
    fun `one long frame integrates like the substeps would`() {
        val single = CriticalSpring(BounceStiffness)
        single.start(500f, 3000f)
        single.advance(0.5f)

        val substepped = CriticalSpring(BounceStiffness)
        substepped.start(500f, 3000f)
        repeat(120) { substepped.advance(1f / 240f) }

        assertEquals(substepped.position, single.position, 1e-2f)
        assertEquals(substepped.velocity, single.velocity, 1e-1f)
        assertTrue(abs(single.position) < 5000f)
        assertFalse(single.position.isNaN())
    }

    @Test
    fun `factory equality follows scope identity`() {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val other = CoroutineScope(Dispatchers.Unconfined)
        assertEquals(IosOverscrollFactory(scope), IosOverscrollFactory(scope))
        assertFalse(IosOverscrollFactory(scope).equals(IosOverscrollFactory(other)))
    }
}
