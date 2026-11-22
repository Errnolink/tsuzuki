package dev.errnolink.tsuzuki.designsystem

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Kasane stepper and chip selection behaviour (thresholds, hold timing, check visibility). */
class SelectionBehaviourTest {

    // hold-to-repeat timing

    @Test
    fun `repeats accelerate from 130 ms down to a 45 ms floor`() {
        assertEquals(130, stepperRepeatIntervalMs(0))
        assertEquals(122, stepperRepeatIntervalMs(1))
        assertEquals(106, stepperRepeatIntervalMs(3))
        assertEquals(50, stepperRepeatIntervalMs(10))
        assertEquals(45, stepperRepeatIntervalMs(11))
        assertEquals(45, stepperRepeatIntervalMs(30))
    }

    @Test
    fun `a short press is one step, held presses repeat`() {
        // the repeat only starts at 420 ms; a tap below that steps once on release
        assertEquals(0, heldSteps(withinMs = 419))
        assertEquals(1, heldSteps(withinMs = 420))
        assertEquals(2, heldSteps(withinMs = 420 + 130))
    }

    @Test
    fun `long holds keep accelerating and never stop early`() {
        // ticks at 420, 550(122), 664(114), 770(106), 868(98), 958(90)
        assertEquals(6, heldSteps(withinMs = 960))
        assertTrue(heldSteps(withinMs = 4000) > 20)
    }

    private fun heldSteps(withinMs: Long): Int {
        var t = StepperHoldMs
        var steps = 0
        while (t <= withinMs) {
            steps++
            t += stepperRepeatIntervalMs(steps)
        }
        return steps
    }

    // stepping and bounds

    @Test
    fun `steps clamp at the bounds and report whether they moved`() {
        assertEquals(8, stepperValue(4, +1, 0..64, 4))
        assertEquals(64, stepperValue(62, +1, 0..64, 4))
        assertEquals(64, stepperValue(64, +1, 0..64, 4))
        assertEquals(0, stepperValue(0, -1, 0..64, 4))
        assertEquals(12, stepperValue(16, -1, 0..64, 4))
        assertTrue(stepperCanStep(60, 1, 0..64, 4))
        assertFalse(stepperCanStep(64, 1, 0..64, 4))
        assertFalse(stepperCanStep(0, -1, 0..64, 4))
    }

    // filter chip check

    @Test
    fun `a selected chip with no leading icon grows its own check`() {
        assertTrue(chipShowsCheck(selected = true, hasLeading = false, triState = null))
        assertFalse(chipShowsCheck(selected = false, hasLeading = false, triState = null))
    }

    @Test
    fun `explicit leading icons and tri-state marks replace the check`() {
        assertFalse(chipShowsCheck(selected = true, hasLeading = true, triState = null))
        assertFalse(chipShowsCheck(selected = true, hasLeading = false, triState = ChipTriState.Include))
        assertFalse(chipShowsCheck(selected = true, hasLeading = false, triState = ChipTriState.Exclude))
    }

    @Test
    fun `the selected capsule squares off and the pad tightens`() {
        val rest = chipRestRadius(minHeight = 40.dp)
        assertTrue(ChipSelectedRadius < rest, "selected corner must be tighter than the capsule")
        assertEquals(10.dp, chipSelectedPadStart(selected = true, base = 12.dp))
        assertEquals(12.dp, chipSelectedPadStart(selected = false, base = 12.dp))
    }
}
