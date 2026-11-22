package dev.errnolink.tsuzuki.designsystem

import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * One selection fraction drives the whole chip (round 10, B5): the widening, the corners, the pad
 * and the check that draws itself are one continuous motion, and deselecting reverses the same
 * curve — the old chip animated each piece on its own animation and read as steps.
 */
class ChipSelectionGeometryTest {

    private val minHeight = 40.dp
    private val basePad = 12.dp

    @Test
    fun `rest and selected ends of the curve are the chip's two looks`() {
        val rest = chipSelectionGeometry(0f, minHeight, basePad)
        assertEquals(chipRestRadius(minHeight), rest.corner)
        assertEquals(0.dp, rest.checkSlot)
        assertEquals(basePad, rest.padStart)
        assertEquals(0f, rest.checkDraw)

        val on = chipSelectionGeometry(1f, minHeight, basePad)
        assertEquals(ChipSelectedRadius, on.corner)
        assertEquals(ChipCheckSize, on.checkSlot)
        assertEquals(10.dp, on.padStart)
        assertEquals(1f, on.checkDraw)
    }

    @Test
    fun `the chip widens monotonically - no steps`() {
        var previousSlot = chipSelectionGeometry(0f, minHeight, basePad).checkSlot
        var previousCorner = chipSelectionGeometry(0f, minHeight, basePad).corner.value
        var f = 0.05f
        while (f <= 1f) {
            val g = chipSelectionGeometry(f, minHeight, basePad)
            assertTrue(g.checkSlot.value >= previousSlot.value, "checkSlot never shrinks while selecting at f=$f")
            assertTrue(g.corner.value <= previousCorner, "the corner only tightens at f=$f")
            previousSlot = g.checkSlot
            previousCorner = g.corner.value
            f += 0.05f
        }
    }

    @Test
    fun `the check draws itself only after the slot has begun to open`() {
        assertEquals(0f, chipSelectionGeometry(0.25f, minHeight, basePad).checkDraw)
        assertTrue(chipSelectionGeometry(0.3f, minHeight, basePad).checkDraw > 0f)
        assertTrue(chipSelectionGeometry(0.3f, minHeight, basePad).checkDraw < 1f)
        assertEquals(1f, chipSelectionGeometry(1f, minHeight, basePad).checkDraw)
    }

    @Test
    fun `the curve is a pure function of the fraction, so deselecting reverses it`() {
        // The same fraction gives the same geometry whichever direction the spring is running:
        // the chip narrows on the same curve it widened on.
        for (f in listOf(0.1f, 0.35f, 0.5f, 0.75f, 0.9f)) {
            assertEquals(
                chipSelectionGeometry(f, minHeight, basePad),
                chipSelectionGeometry(f, minHeight, basePad),
            )
        }
    }

    @Test
    fun `a tri-state chip keeps its tighter pad at both ends`() {
        // TriStateChip passes horizontalPadding 10 when active; the lerp must not widen it.
        val active = chipSelectionGeometry(1f, 32.dp, 10.dp)
        assertEquals(10.dp, active.padStart)
        val inactive = chipSelectionGeometry(0f, 32.dp, 12.dp)
        assertEquals(12.dp, inactive.padStart)
    }

    @Test
    fun `chip selection spring settles monotonically without horizontal overshoot`() {
        val spec = KasaneSprings.selectionCheck<Float>()
        val vectorized = spec.vectorize(Float.VectorConverter)
        val initial = androidx.compose.animation.core.AnimationVector1D(0f)
        val target = androidx.compose.animation.core.AnimationVector1D(1f)
        val zeroVelocity = androidx.compose.animation.core.AnimationVector1D(0f)
        val durationNanos = vectorized.getDurationNanos(initial, target, zeroVelocity)
        var prev = 0f
        var t = 0L
        val step = 10_000_000L
        while (t <= durationNanos) {
            val v = vectorized.getValueFromNanos(t, initial, target, zeroVelocity).value
            assertTrue(v in -0.001f..1.001f, "Chip selection fraction overshot bounds at t=$t: $v")
            assertTrue(v + 0.001f >= prev, "Chip selection fraction reversed/shook at t=$t: $prev -> $v")
            prev = v
            t += step
        }
    }
}
