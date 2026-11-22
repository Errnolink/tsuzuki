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
        assertTrue(criticalSpring(0.1f) < 0.45f, "card covered ${criticalSpring(0.1f)} of the way in the first tenth")
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
    fun `the canvas colour fills the growing card before the reader shows and the reader lands sharp`() {
        val start = readerPose(row, screen, 0f, corner)
        assertEquals(0f, start.veilAlpha, 1e-3f, "the real button must show when the reader lands back in it")

        val early = readerPose(row, screen, 0.06f, corner)
        assertEquals(1f, early.veilAlpha, 1e-3f)
        assertEquals(0f, early.contentAlpha, 1e-3f)
        assertEquals(1f, early.contentBlur, 1e-3f)

        val revealed = readerPose(row, screen, 0.45f, corner)
        assertEquals(1f, revealed.contentAlpha, 1e-3f)

        val end = readerPose(row, screen, 1f, corner)
        assertEquals(0f, end.veilAlpha, 1e-3f)
        assertEquals(0f, end.contentBlur, 1e-3f)

        var previousContent = 0f
        for (step in 5..40) {
            val pose = readerPose(row, screen, step / 40f, corner)
            assertTrue(pose.contentAlpha >= previousContent - 1e-4f, "reader faded out while opening at step $step")
            assertTrue(pose.veilAlpha > 0f || pose.contentAlpha > 0.9f, "an empty frame at step $step")
            previousContent = pose.contentAlpha
        }
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

    @Test
    fun `a predictive back drag reverses the open from wherever the finger leaves it`() {
        assertEquals(1f, readerBackPose(0f), 1e-4f, "the resting reader is untouched until the gesture starts")
        val mid = readerBackPose(0.5f)
        assertTrue(mid in 0.5f..0.9f, "half the gesture reads as a card on its way back, not a sliver")
        assertEquals(readerBackPose(1f), readerBackPose(1.4f), 1e-4f, "the mapping clamps past the full gesture")
        var previous = 1f
        for (step in 0..20) {
            val value = readerBackPose(step / 20f)
            assertTrue(value <= previous, "the reader grew while backing out at step $step")
            previous = value
        }
        val halfPose = readerPose(row, screen, readerBackPose(0.5f), corner)
        assertTrue(halfPose.clip.width in row.width..screen.width, "the scrubbed card stays between the origin and the screen")
    }

    @Test
    fun `a touch inside no origin falls back to the registered fallback and a stale touch opens plainly`() {
        val cover = Rect(20f, 100f, 380f, 680f)
        val row = Rect(400f, 900f, 1000f, 1000f)
        ReaderOrigins.putFallback("test-fallback", cover)
        try {
            assertEquals(cover, ReaderOrigins.pick(Offset(800f, 950f)))
            ReaderOrigins.put("test-row", row)
            assertEquals(row, ReaderOrigins.pick(Offset(800f, 950f)))
            assertNull(ReaderOrigins.pick(null))
        } finally {
            ReaderOrigins.remove("test-row")
            ReaderOrigins.removeFallback("test-fallback")
        }
        assertNull(ReaderOrigins.pick(Offset(800f, 950f)))
    }

    @Test
    fun `markActive overrides touch lookup within its freshness window, consumes once, and expires cleanly`() {
        val resumeRect = Rect(700f, 600f, 950f, 680f)
        val fallbackRect = Rect(20f, 100f, 380f, 680f)
        ReaderOrigins.putFallback("test-fallback", fallbackRect)
        try {
            ReaderOrigins.markActive(resumeRect, nowMillis = 1000L)
            assertEquals(resumeRect, ReaderOrigins.pick(touch = null, nowMillis = 1200L))
            // Consumed on first pick; next pick without touch returns null
            assertNull(ReaderOrigins.pick(touch = null, nowMillis = 1250L))

            // Expired active origin is ignored and falls back normally
            ReaderOrigins.markActive(resumeRect, nowMillis = 1000L)
            assertEquals(fallbackRect, ReaderOrigins.pick(touch = Offset(10f, 10f), nowMillis = 1700L))
        } finally {
            ReaderOrigins.clearActive()
            ReaderOrigins.removeFallback("test-fallback")
        }
    }
}
