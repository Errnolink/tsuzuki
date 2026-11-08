package dev.errnolink.tsuzuki.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReaderTransitionTest {
    private val screen = Rect(0f, 0f, 1080f, 2376f)
    private val row = Rect(60f, 1500f, 1020f, 1640f)
    private val corner = 48f

    @Test
    fun `the reader card grows gradually from the tapped element instead of jumping and lands exactly`() {
        assertEquals(0f, criticalSpring(0f), 1e-4f)
        assertTrue(criticalSpring(0.1f) < 0.3f, "card covered ${criticalSpring(0.1f)} of the way in the first tenth")
        assertEquals(1f, criticalSpring(1f), 1e-4f)
        var previous = 0f
        for (step in 1..50) {
            val value = criticalSpring(step / 50f)
            assertTrue(value >= previous && value <= 1f, "overshoot or reversal at step $step")
            previous = value
        }
    }

    @Test
    fun `the reader starts as the tapped row and ends filling the screen with square corners`() {
        val start = readerPose(row, screen, 0f, corner)
        assertEquals(row, start.clip)
        assertEquals(corner, start.radius, 1e-3f)
        assertEquals(0f, start.contentAlpha, 1e-3f)

        val end = readerPose(row, screen, 1f, corner)
        assertEquals(screen, end.clip)
        assertEquals(0f, end.radius, 1e-3f)
        assertEquals(1f, end.scale, 1e-3f)
        assertEquals(1f, end.contentAlpha, 1e-3f)
    }

    @Test
    fun `the growing card never leaves the space between the row and the screen edges`() {
        var previousWidth = 0f
        for (step in 0..20) {
            val clip = readerPose(row, screen, step / 20f, corner).clip
            assertTrue(clip.left >= screen.left && clip.right <= screen.right && clip.top >= screen.top && clip.bottom <= screen.bottom)
            assertTrue(clip.width >= previousWidth, "card shrank while opening at step $step")
            previousWidth = clip.width
        }
    }

    @Test
    fun `the tapped element is the origin even when it sits inside a larger registered element`() {
        val card = Rect(20f, 1200f, 1060f, 1900f)
        val button = Rect(700f, 1700f, 1000f, 1800f)
        assertEquals(button, ReaderOrigins.pickFrom(listOf(card, button), Offset(800f, 1750f)))
        assertEquals(card, ReaderOrigins.pickFrom(listOf(card, button), Offset(100f, 1300f)))
        assertNull(ReaderOrigins.pickFrom(listOf(card, button), Offset(10f, 10f)))
        assertNull(ReaderOrigins.pickFrom(listOf(card, button), null))
    }
}
