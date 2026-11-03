package dev.errnolink.tsuzuki.designsystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class DetentSheetGeometryTest {
    private val height = 800f
    private val grid = 8f

    @Test
    fun `quantize snaps to grid and preserves order`() {
        assertEquals(0f, DetentSheetGeometry.quantize(3f, grid))
        assertEquals(8f, DetentSheetGeometry.quantize(6f, grid))
        assertEquals(8f, DetentSheetGeometry.quantize(8f, grid))
        assertEquals(64f, DetentSheetGeometry.quantize(63f, grid))
    }

    @Test
    fun `detent offsets are flush medium half and fit clamped`() {
        assertEquals(0f, DetentSheetGeometry.detentOffset(SheetDetent.Large, height, 0f))
        assertEquals(height / 2f, DetentSheetGeometry.detentOffset(SheetDetent.Medium, height, 0f))
        assertEquals(height - 500f, DetentSheetGeometry.detentOffset(SheetDetent.FitContent, height, 500f))
    }

    @Test
    fun `dismissing sheet vetoes re-settle even when target drifted`() {
        val drifted = height - 300f
        assertTrue(DetentSheetGeometry.shouldSkipSettle(dismissing = true, isAnimating = true, currentTargetPx = height, targetPx = drifted, gridPx = grid))
        assertTrue(DetentSheetGeometry.shouldSkipSettle(dismissing = true, isAnimating = false, currentTargetPx = height, targetPx = drifted, gridPx = grid))
    }

    @Test
    fun `non dismissing sheet settles when target drifts past grid`() {
        assertFalse(DetentSheetGeometry.shouldSkipSettle(dismissing = false, isAnimating = true, currentTargetPx = height, targetPx = height - 300f, gridPx = grid))
        assertTrue(DetentSheetGeometry.shouldSkipSettle(dismissing = false, isAnimating = true, currentTargetPx = height, targetPx = height - 4f, gridPx = grid))
        assertFalse(DetentSheetGeometry.shouldSkipSettle(dismissing = false, isAnimating = false, currentTargetPx = height, targetPx = height, gridPx = grid))
    }

    @Test
    fun `fit content below floor clamps to quarter viewport`() {
        val offset = DetentSheetGeometry.detentOffset(SheetDetent.FitContent, height, 100f)
        assertEquals(height - height * 0.25f, offset)
    }

    @Test
    fun `short fit sheet stays smaller than medium`() {
        val offset = DetentSheetGeometry.detentOffset(SheetDetent.FitContent, height, 300f)
        assertEquals(height - 300f, offset)
        assertTrue(offset > height / 2f)
    }

    @Test
    fun `fit content taller than large clamps to large`() {
        val offset = DetentSheetGeometry.detentOffset(SheetDetent.FitContent, height, 2000f)
        assertEquals(0f, offset)
    }

    @Test
    fun `short fit sheet rests at its fit height after release`() {
        val rest = DetentSheetGeometry.detentOffset(SheetDetent.FitContent, height, 300f)
        val settle = DetentSheetGeometry.releaseTarget(
            offsetPx = rest + 40f,
            velocityPx = 0f,
            heightPx = height,
            flingThresholdPx = 800f,
            restPx = rest,
        )
        assertEquals(rest, settle.target)
        assertFalse(settle.dismiss)
    }

    @Test
    fun `strong downward fling from upper half closes and dismisses`() {
        val settle = DetentSheetGeometry.releaseTarget(
            offsetPx = height * 0.5f,
            velocityPx = 1000f,
            heightPx = height,
            flingThresholdPx = 800f,
            restPx = height / 2f,
        )
        assertEquals(height, settle.target)
        assertTrue(settle.dismiss)
    }

    @Test
    fun `strong upward fling from small sheet rests at medium`() {
        val settle = DetentSheetGeometry.releaseTarget(
            offsetPx = height * 0.7f,
            velocityPx = -1000f,
            heightPx = height,
            flingThresholdPx = 800f,
            restPx = height * 0.4f,
        )
        assertEquals(height / 2f, settle.target)
        assertFalse(settle.dismiss)
    }

    @Test
    fun `strong upward fling from large sheet re-expands`() {
        val settle = DetentSheetGeometry.releaseTarget(
            offsetPx = height * 0.2f,
            velocityPx = -1000f,
            heightPx = height,
            flingThresholdPx = 800f,
            restPx = height / 2f,
        )
        assertEquals(0f, settle.target)
        assertFalse(settle.dismiss)
    }

    @Test
    fun `slow drag to lower quarter rests at fit detent`() {
        val settle = DetentSheetGeometry.releaseTarget(
            offsetPx = height * 0.65f,
            velocityPx = 0f,
            heightPx = height,
            flingThresholdPx = 800f,
            restPx = height * 0.75f,
        )
        assertEquals(height * 0.75f, settle.target)
        assertFalse(settle.dismiss)
    }

    @Test
    fun `slow drag near top re-expands to large`() {
        val settle = DetentSheetGeometry.releaseTarget(
            offsetPx = height * 0.1f,
            velocityPx = 0f,
            heightPx = height,
            flingThresholdPx = 800f,
            restPx = height / 2f,
        )
        assertEquals(0f, settle.target)
        assertFalse(settle.dismiss)
    }

    @Test
    fun `settle gate allows at rest and blocks subgrid retargets`() {
        assertTrue(DetentSheetGeometry.shouldSettle(isAnimating = false, currentTargetPx = 100f, targetPx = 100f, gridPx = grid))
        assertFalse(DetentSheetGeometry.shouldSettle(isAnimating = true, currentTargetPx = 100f, targetPx = 104f, gridPx = grid))
        assertTrue(DetentSheetGeometry.shouldSettle(isAnimating = true, currentTargetPx = 100f, targetPx = 109f, gridPx = grid))
    }

    @Test
    fun `quantized fit height keeps target stable across re-measures`() {
        val raw1 = 480f
        val raw2 = 483f
        val q1 = DetentSheetGeometry.quantize(raw1, grid)
        val q2 = DetentSheetGeometry.quantize(raw2, grid)
        assertEquals(q1, q2)
        val o1 = DetentSheetGeometry.detentOffset(SheetDetent.FitContent, height, q1)
        val o2 = DetentSheetGeometry.detentOffset(SheetDetent.FitContent, height, q2)
        assertEquals(0f, abs(o1 - o2))
    }
}
