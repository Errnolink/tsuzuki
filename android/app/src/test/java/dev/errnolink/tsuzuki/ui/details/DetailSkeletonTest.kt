package dev.errnolink.tsuzuki.ui.details

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class DetailSkeletonTest {

    @Test
    fun `pulse period stays inside the 1600 to 2000 ms spec window`() {
        assertTrue(SkeletonPulsePeriodMs in 1600..2000)
    }

    @Test
    fun `pulse fraction stays between the base and crest and loops without jumps`() {
        for (offset in floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            for (step in 0 until 100) {
                val fraction = SkeletonPulseFraction(step / 100f, offset)
                assertTrue(fraction >= 0f && fraction <= 1f, "fraction $fraction at offset $offset")
            }
            val wrapGap = abs(SkeletonPulseFraction(0.99f, offset) - SkeletonPulseFraction(0.01f, offset))
            assertTrue(wrapGap < 0.15f, "pulse must loop smoothly at offset $offset, gap $wrapGap")
        }
    }

    @Test
    fun `pulse crests the placeholder exactly half a cycle after its offset`() {
        for (offset in floatArrayOf(0f, 0.1f, 0.25f, 0.5f, 0.75f, 0.9f)) {
            val crestPhase = (offset + 0.5f).mod(1f)
            assertEquals(1f, SkeletonPulseFraction(crestPhase, offset), 1e-4f)
            assertEquals(0f, SkeletonPulseFraction((crestPhase + 0.5f).mod(1f), offset), 1e-4f)
        }
    }

    @Test
    fun `pulse crest sweeps from top left to bottom right as the clock advances`() {
        val crestPhases = floatArrayOf(0f, 0.1f, 0.25f, 0.5f, 0.75f, 0.9f).map { offset ->
            var bestPhase = 0f
            var bestFraction = -1f
            for (step in 0 until 1000) {
                val phase = step / 1000f
                val fraction = SkeletonPulseFraction(phase, offset)
                if (fraction > bestFraction) {
                    bestFraction = fraction
                    bestPhase = phase
                }
            }
            bestPhase
        }
        assertEquals(1f, SkeletonPulseFraction(crestPhases[0], 0f), 1e-2f)
        var previous = crestPhases[0]
        for (index in 1 until crestPhases.size) {
            var phase = crestPhases[index]
            while (phase < previous) phase += 1f
            assertTrue(phase > previous, "crest phases $crestPhases")
            previous = phase
        }
    }

    @Test
    fun `position offset is the normalised top left to bottom right diagonal`() {
        assertEquals(0f, SkeletonPulsePositionOffset(0f, 0f, 2000f), 1e-4f)
        assertEquals(0.5f, SkeletonPulsePositionOffset(500f, 500f, 2000f), 1e-4f)
        assertEquals(1f, SkeletonPulsePositionOffset(1000f, 1000f, 2000f), 1e-4f)
        assertEquals(0f, SkeletonPulsePositionOffset(10f, 10f, 0f), 1e-4f)
        assertEquals(1f, SkeletonPulsePositionOffset(3000f, 0f, 2000f), 1e-4f)
        assertEquals(0f, SkeletonPulsePositionOffset(-50f, -50f, 2000f), 1e-4f)
    }

    @Test
    fun `highlight is secondary at 20 percent alpha composited over an opaque base`() {
        val overBlack = DetailSkeletonHighlight(base = Color.Black, secondary = Color.White)
        assertEquals(1f, overBlack.alpha, 1e-4f)
        assertEquals(0.2f, overBlack.red, 1e-3f)
        assertEquals(0.2f, overBlack.green, 1e-3f)
        assertEquals(0.2f, overBlack.blue, 1e-3f)

        val base = Color(0xFF1C1D21)
        val secondary = Color(0xFF8BB9FF)
        val themed = DetailSkeletonHighlight(base = base, secondary = secondary)
        assertEquals(secondary.copy(alpha = 0.2f).compositeOver(base), themed)
        assertEquals(1f, themed.alpha, 1e-4f)
        assertNotEquals(base, themed)
        assertNotEquals(secondary, themed)
    }
}
