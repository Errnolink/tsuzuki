package dev.errnolink.tsuzuki.designsystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ControlsBehaviourTest {

    // slider value from a drag or tap position

    @Test
    fun `slider reads a plain fraction as the nearest integer value`() {
        assertEquals(0, sliderValueAt(0f, 0, 100, 1))
        assertEquals(47, sliderValueAt(0.47f, 0, 100, 1))
        assertEquals(100, sliderValueAt(1f, 0, 100, 1))
        assertEquals(5, sliderValueAt(0.5f, 0, 10, 1))
    }

    @Test
    fun `slider snaps to the progression step and never leaves the range`() {
        assertEquals(50, sliderValueAt(0.47f, 0, 100, 25))
        assertEquals(0, sliderValueAt(0.1f, 0, 100, 25))
        assertEquals(100, sliderValueAt(0.9f, 0, 100, 25))
        assertEquals(17, sliderValueAt(0.5f, 7, 27, 10))
    }

    @Test
    fun `slider clamps out-of-range fractions`() {
        assertEquals(0, sliderValueAt(-3f, 0, 100, 1))
        assertEquals(100, sliderValueAt(2f, 0, 100, 1))
        assertEquals(100, sliderValueAt(9f, 0, 100, 25))
    }

    // drag-to-toggle threshold

    @Test
    fun `switch drag commits only past the midpoint`() {
        assertEquals(false, switchDragCommits(0f))
        assertEquals(false, switchDragCommits(0.2f))
        assertEquals(false, switchDragCommits(0.5f))
        assertTrue(switchDragCommits(0.51f))
        assertTrue(switchDragCommits(1f))
    }

    // segment chosen at a lens drop point (2 dp inset, px frame)

    @Test
    fun `lens drop point lands on the segment under it`() {
        val w = 300f
        val inset = 2f
        assertEquals(0, segmentIndexAt(50f, w, 3, inset, rtl = false))
        assertEquals(0, segmentIndexAt(60f, w, 3, inset, rtl = false))
        assertEquals(1, segmentIndexAt(101f, w, 3, inset, rtl = false))
        assertEquals(2, segmentIndexAt(250f, w, 3, inset, rtl = false))
        assertEquals(2, segmentIndexAt(299f, w, 3, inset, rtl = false))
    }

    @Test
    fun `lens drop point clamps outside the track`() {
        val w = 300f
        val inset = 2f
        assertEquals(0, segmentIndexAt(0f, w, 3, inset, rtl = false))
        assertEquals(0, segmentIndexAt(-20f, w, 3, inset, rtl = false))
        assertEquals(2, segmentIndexAt(300f, w, 3, inset, rtl = false))
        assertEquals(2, segmentIndexAt(999f, w, 3, inset, rtl = false))
        assertEquals(0, segmentIndexAt(0f, w, 1, inset, rtl = false))
        assertEquals(0, segmentIndexAt(300f, w, 1, inset, rtl = false))
    }

    @Test
    fun `segment centres mirror in RTL`() {
        val w = 300f
        val inset = 2f
        assertEquals(2f + (w - 2f * inset) / 3f / 2f, segmentCenterAt(0, w, 3, inset, rtl = false), 0.001f)
        assertEquals(w - 2f - (w - 2f * inset) / 3f / 2f, segmentCenterAt(0, w, 3, inset, rtl = true), 0.001f)
        assertEquals(
            segmentCenterAt(2, w, 3, inset, rtl = false),
            segmentCenterAt(0, w, 3, inset, rtl = true),
            0.001f,
        )
    }

    // the pill chases the finger and rubber-bands past the end centres

    @Test
    fun `follow target is the finger while it stays between the ends`() {
        val first = 50f
        val last = 250f
        assertEquals(150f, lensFollowTarget(150f, first, last, 14f), 0.001f)
        assertEquals(first, lensFollowTarget(first, first, last, 14f), 0.001f)
        assertEquals(last, lensFollowTarget(last, first, last, 14f), 0.001f)
    }

    @Test
    fun `follow target rubber-bands past the ends within the overdrag`() {
        val first = 50f
        val last = 250f
        val over = 14f
        val beyondLast = lensFollowTarget(250f + 20f, first, last, over)
        assertTrue(beyondLast > last && beyondLast < last + over)
        val beforeFirst = lensFollowTarget(50f - 20f, first, last, over)
        assertTrue(beforeFirst < first && beforeFirst > first - over)
    }

    // the lens stretches along the motion with pill speed, never past the cap

    @Test
    fun `lens speed stretch is zero at rest and full at the cap speed`() {
        assertEquals(0f, lensSpeedStretch(0f, 1800f))
        assertEquals(0.5f, lensSpeedStretch(900f, 1800f), 0.0001f)
        assertEquals(1f, lensSpeedStretch(1800f, 1800f))
        assertEquals(1f, lensSpeedStretch(3600f, 1800f))
    }

    @Test
    fun `lens pill spring settles on its target and rests`() {
        val pill = LensPillMotion(0f, scope = null)
        pill.settle(100f)
        var frames = 0
        while (pill.tick(1f / 60f)) {
            frames++
            assertTrue(frames < 600)
        }
        assertEquals(100f, pill.position, 0.01f)
        assertEquals(0f, pill.velocity, 0.01f)
        assertEquals(false, pill.tick(1f / 60f))
    }

    @Test
    fun `lens pill snapTo lands immediately`() {
        val pill = LensPillMotion(0f, scope = null)
        pill.settle(50f)
        pill.snapTo(-10f)
        assertEquals(-10f, pill.position, 0.0001f)
        assertEquals(false, pill.tick(1f / 60f))
    }
}
