package dev.errnolink.tsuzuki.ui.motion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The dismiss decision: the page follows a downward drag over 60 % of its height, and release
 * dismisses past the travel threshold or with a flick, never on an upward-moving finger alone.
 */
class ZoomDismissTest {

    private val pageHeight = 2400f

    @Test
    fun `drag travel maps the finger over sixty percent of the page and clamps`() {
        assertEquals(0f, zoomDismissTravel(0f, pageHeight), 1e-4f)
        assertEquals(1f, zoomDismissTravel(pageHeight * ZoomDismissSpan, pageHeight), 1e-4f)
        assertEquals(0f, zoomDismissTravel(-80f, pageHeight), 1e-4f)
        assertEquals(1f, zoomDismissTravel(pageHeight * 4f, pageHeight), 1e-4f)
        assertEquals(0f, zoomDismissTravel(100f, 0f), 1e-4f)
    }

    @Test
    fun `travel grows monotonically with the finger`() {
        var previous = 0f
        var dy = 0f
        while (dy <= pageHeight * ZoomDismissSpan) {
            val travel = zoomDismissTravel(dy, pageHeight)
            assertTrue(travel >= previous, "travel went back at $dy px")
            previous = travel
            dy += 24f
        }
    }

    @Test
    fun `a slow release dismisses only past the travel threshold`() {
        assertFalse(zoomDismissCommits(zoomDismissTravel(ZoomDismissCommit * pageHeight * ZoomDismissSpan - 24f, pageHeight), 0f))
        assertTrue(zoomDismissCommits(zoomDismissTravel(ZoomDismissCommit * pageHeight * ZoomDismissSpan, pageHeight), 0f))
    }

    @Test
    fun `a downward flick dismisses from any claimed travel`() {
        assertTrue(zoomDismissCommits(0.01f, ZoomFlickVelocityDps))
        assertFalse(zoomDismissCommits(0.01f, ZoomFlickVelocityDps - 1f))
    }

    @Test
    fun `an upward-moving finger never commits on velocity alone`() {
        assertFalse(zoomDismissCommits(0.1f, -ZoomFlickVelocityDps * 4f))
        assertTrue(
            zoomDismissCommits(ZoomDismissCommit + 0.1f, -ZoomFlickVelocityDps),
            "well past the threshold the page goes home even while the finger drifts back up",
        )
    }

    @Test
    fun `the flight carries the finger velocity backwards into the spring`() {
        // the drag-to-dismiss release passes its travel span as the distance to the tile
        val span = pageHeight * ZoomDismissSpan
        val speed = 600f
        val velocity = zoomReleaseVelocity(speed, span)
        assertTrue(velocity < 0f, "release velocity must drive progress toward the tile")
        assertEquals(speed / span, -velocity, 1e-5f)
        assertEquals(0f, zoomReleaseVelocity(speed, 0f))
        assertEquals(-0.5f, zoomReleaseVelocity(720f, span), 1e-4f)
    }
}
