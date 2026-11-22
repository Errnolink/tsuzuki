package dev.errnolink.tsuzuki.ui.details

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import dev.errnolink.tsuzuki.designsystem.SkeletonSpec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The skeleton spec the title-page loading screens draw with (Kasane's recipe, pure numbers). */
class DetailSkeletonTest {

    @Test
    fun `tone is eleven percent text composited over an opaque surface`() {
        val tone = SkeletonSpec.tone(Color.White, Color.Black)
        assertEquals(Color.White.copy(alpha = 0.11f).compositeOver(Color.Black), tone)
        assertEquals(1f, tone.alpha, 1e-4f)
    }

    @Test
    fun `sweep and pulse periods stay inside Kasane's windows`() {
        assertTrue(SkeletonSpec.SWEEP_MS in 1500..1700) // Kasane: 1.6 s sweep
        assertTrue(SkeletonSpec.PULSE_MS in 1700..1900) // reduce motion: slow pulse
    }

    @Test
    fun `highlight lifts the tone without going fully opaque`() {
        val tone = SkeletonSpec.tone(Color.White, Color(0xFF1C1D21))
        val highlight = SkeletonSpec.highlight(tone)
        assertEquals(lerp(tone, Color.White, 0.35f).copy(alpha = 0.9f), highlight)
        assertEquals(0.9f, highlight.alpha, 0.01f) // colours quantise to 8-bit channels
        assertNotEquals(tone, highlight)
    }

    @Test
    fun `chapter title fractions stay varied and under one`() {
        val fractions = (0 until 8).map { DetailSkeletonChapterTitleFraction(it) }
        fractions.forEach { assertTrue(it in 0.3f..0.8f) }
        assertTrue(fractions.toSet().size >= 4)
    }
}
