package dev.errnolink.tsuzuki.designsystem

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Kasane toast behaviour: stacking pose, durations, swipe decisions, drag resistance. */
class ToastSpecTest {

    @Test
    fun `collapsed stack tucks twelve and scales five percent a step`() {
        assertEquals(0.dp, ToastSpec.tuck(0))
        assertEquals(12.dp, ToastSpec.tuck(1))
        assertEquals(24.dp, ToastSpec.tuck(2))
        assertEquals(1f, ToastSpec.scale(0))
        assertEquals(0.95f, ToastSpec.scale(1), 1e-6f)
        assertEquals(0.90f, ToastSpec.scale(2), 1e-6f)
    }

    @Test
    fun `only three toasts render while collapsed`() {
        assertTrue(ToastSpec.visible(0))
        assertTrue(ToastSpec.visible(2))
        assertFalse(ToastSpec.visible(3))
        assertFalse(ToastSpec.visible(9))
    }

    @Test
    fun `durations follow the loading and action rules`() {
        assertEquals(4000L, ToastSpec.durationMs(ToastKind.Default, hasAction = false, requested = null))
        assertEquals(6000L, ToastSpec.durationMs(ToastKind.Default, hasAction = true, requested = null))
        assertEquals(6000L, ToastSpec.durationMs(ToastKind.Success, hasAction = true, requested = null))
        assertEquals(0L, ToastSpec.durationMs(ToastKind.Loading, hasAction = false, requested = null))
        assertEquals(0L, ToastSpec.durationMs(ToastKind.Loading, hasAction = true, requested = null))
        assertEquals(8000L, ToastSpec.durationMs(ToastKind.Error, hasAction = true, requested = 8000L))
        assertEquals(2500L, ToastSpec.durationMs(ToastKind.Success, hasAction = false, requested = 2500L))
    }

    @Test
    fun `wrong direction drags resist harder the further they go`() {
        assertEquals(0f, ToastSpec.damp(0f))
        val small = ToastSpec.damp(-10f)
        val large = ToastSpec.damp(-200f)
        assertTrue(small < 10f) // resisted
        assertTrue(large < 200f)
        // the resistance grows: 200 raw loses more than twice what 10 raw loses
        assertTrue(10f - small < 200f - large)
        assertEquals(ToastSpec.damp(30f), -ToastSpec.damp(-30f), 1e-4f)
    }

    @Test
    fun `forty five px or a fling dismisses sideways on a centred stack`() {
        // far enough
        assertTrue(ToastSpec.dismisses(distancePx = 45f, velocityPxPerMs = 0f, awaySign = 1f, horizontal = true))
        assertTrue(ToastSpec.dismisses(distancePx = -45f, velocityPxPerMs = 0f, awaySign = 1f, horizontal = true))
        // too short and too slow stays
        assertFalse(ToastSpec.dismisses(distancePx = 44f, velocityPxPerMs = 0f, awaySign = 1f, horizontal = true))
        assertFalse(ToastSpec.dismisses(distancePx = 10f, velocityPxPerMs = 0.05f, awaySign = 1f, horizontal = true))
        // fling commits even from a short drag
        assertTrue(ToastSpec.dismisses(distancePx = 12f, velocityPxPerMs = 0.12f, awaySign = 1f, horizontal = true))
        assertTrue(ToastSpec.dismisses(distancePx = -12f, velocityPxPerMs = -0.12f, awaySign = 1f, horizontal = true))
        // the fling must point where the drag points
        assertFalse(ToastSpec.dismisses(distancePx = 12f, velocityPxPerMs = -0.12f, awaySign = 1f, horizontal = true))
    }

    @Test
    fun `vertical dismissal only counts toward the screen edge`() {
        // away is down (+1) for a bottom stack: downswipes commit
        assertTrue(ToastSpec.dismisses(distancePx = 50f, velocityPxPerMs = 0f, awaySign = 1f, horizontal = false))
        assertTrue(ToastSpec.dismisses(distancePx = 10f, velocityPxPerMs = 0.2f, awaySign = 1f, horizontal = false))
        // upward pulls never commit, however far or fast
        assertFalse(ToastSpec.dismisses(distancePx = -80f, velocityPxPerMs = 0f, awaySign = 1f, horizontal = false))
        assertFalse(ToastSpec.dismisses(distancePx = -10f, velocityPxPerMs = -0.3f, awaySign = 1f, horizontal = false))
    }
}
