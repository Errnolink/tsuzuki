package dev.errnolink.tsuzuki.ui.motion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StackPoseTest {

    private val w = 1080f

    @Test
    fun `at rest the moving page is settled and the scrim is fully drawn`() {
        val pose = stackPose(1f, w, rtl = false)
        assertEquals(0f, pose.frontX, 1e-4f)
        assertEquals(-0.25f * w, pose.backX, 1e-4f)
        assertEquals(0.32f, pose.scrim, 1e-4f)
    }

    @Test
    fun `a page fully off screen sits at the full width over an undisturbed layer`() {
        val pose = stackPose(0f, w, rtl = false)
        assertEquals(w, pose.frontX, 1e-4f)
        assertEquals(0f, pose.backX, 1e-4f)
        assertEquals(0f, pose.scrim, 1e-4f)
    }

    @Test
    fun `the front offset falls monotonically as the page arrives`() {
        var previous = Float.MAX_VALUE
        var p = 0f
        while (p <= 1f) {
            val x = stackPose(p, w, rtl = false).frontX
            assertTrue(x < previous, "frontX rose at progress $p")
            previous = x
            p += 0.05f
        }
    }

    @Test
    fun `right to left layouts mirror both offsets`() {
        for (step in 0..20) {
            val p = step / 20f
            val ltr = stackPose(p, w, rtl = false)
            val rtl = stackPose(p, w, rtl = true)
            assertEquals(ltr.frontX, -rtl.frontX, 1e-4f)
            assertEquals(ltr.backX, -rtl.backX, 1e-4f)
            assertEquals(ltr.scrim, rtl.scrim, 1e-4f)
        }
    }

    @Test
    fun `the gesture preview tracks to half the travel and starts at the full offset`() {
        assertEquals(0.5f, stackGestureProgress(1f, 1f), 1e-4f)
        assertEquals(1f, stackGestureProgress(1f, 0f), 1e-4f)
    }

    @Test
    fun `a gesture interrupting a push never pulls the page past where the push was`() {
        var p = 0f
        while (p <= 1f) {
            val progress = stackGestureProgress(0.6f, p)
            assertTrue(progress <= 0.6f + 1e-4f, "interrupted push preview exceeded the push base at $p")
            p += 0.05f
        }
    }
}
