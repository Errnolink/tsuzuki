package dev.errnolink.tsuzuki.ui.shell

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class TsuzukiTabBarTest {

    private fun runUntilIdle(motion: TsuzukiPillMotion, dt: Float = 1f / 120f, maxFrames: Int = 600): Float {
        var time = 0.0
        var frames = 0
        while (frames < maxFrames) {
            time += dt
            frames++
            if (!motion.tick(dt, time)) break
        }
        return (time - dt).toFloat()
    }

    @Test
    fun `pill initial state is idle and flat`() {
        val motion = TsuzukiPillMotion(scope = null, initialIndex = 0)
        assertEquals(0, motion.index)
        assertEquals(0f, motion.position)
        assertEquals(0f, motion.liftX)
        assertEquals(0f, motion.liftY)
        assertEquals(0f, motion.deviation)
        assertTrue(motion.isFlat)
        assertFalse(motion.isLifted)
    }

    @Test
    fun `pill animateTo transitions to lifted state and starts travel`() {
        val motion = TsuzukiPillMotion(scope = null, initialIndex = 0)
        motion.stepDp = 60f
        motion.animateTo(1)

        assertEquals(1, motion.index)
        assertFalse(motion.isFlat)
        assertTrue(motion.isLifted)

        var time = 0.0
        val dt = 1.0 / 60.0
        for (frame in 0 until 5) {
            time += dt
            motion.tick(dt.toFloat(), time)
        }

        assertTrue(motion.position > 0f)
        assertTrue(motion.liftX > 0f)
        assertTrue(motion.liftY > 0f)
        assertTrue(motion.travelProgress() in 0f..1f)
    }

    @Test
    fun `pill travel for one adjacent step settles in band with barely any overshoot`() {
        val motion = TsuzukiPillMotion(scope = null, initialIndex = 0)
        motion.stepDp = 60f
        motion.animateTo(1)

        var time = 0.0
        val dt = 1f / 240f
        var candidate = -1f
        var survived = 0
        var settleSeconds = -1f
        var peak = 0f
        var frames = 0
        while (frames < 600) {
            time += dt
            frames++
            val active = motion.tick(dt, time)
            peak = maxOf(peak, motion.position)
            if (abs(motion.position - 1f) < 0.02f) {
                survived++
                if (candidate < 0f) candidate = time.toFloat()
                if (settleSeconds < 0f && survived >= 24) settleSeconds = candidate
            } else {
                survived = 0
                candidate = -1f
            }
            if (!active) break
        }
        assertTrue(settleSeconds in 0.20f..0.34f, "settle at $settleSeconds")
        assertTrue(peak < 1.02f, "overshoot $peak")
        assertEquals(1f, motion.position, 1e-3f)
    }

    @Test
    fun `pill lift peaks in band then dips below rest before settling flat`() {
        val motion = TsuzukiPillMotion(scope = null, initialIndex = 0)
        motion.stepDp = 60f
        motion.animateTo(1)

        var peakX = 0f
        var peakY = 0f
        var minX = 0f
        var minY = 0f
        var time = 0.0
        val dt = 1f / 240f
        var frames = 0
        while (frames < 600) {
            time += dt
            frames++
            val active = motion.tick(dt, time)
            peakX = maxOf(peakX, motion.liftX)
            peakY = maxOf(peakY, motion.liftY)
            minX = minOf(minX, motion.liftX)
            minY = minOf(minY, motion.liftY)
            if (!active) break
        }

        assertTrue(peakX in 1.10f..1.14f, "width peak $peakX")
        assertTrue(peakY in 1.04f..1.07f, "height peak $peakY")
        assertTrue(minX < -0.02f, "width undershoot $minX")
        assertTrue(minY < -0.005f, "height undershoot $minY")
        assertTrue(time < 1.2, "settle $time")
        assertTrue(motion.isFlat)
        assertEquals(0f, motion.liftX, 1e-4f)
        assertEquals(0f, motion.liftY, 1e-4f)
        assertEquals(1f, motion.position, 1e-3f)
    }

    @Test
    fun `pill reduced motion snaps immediately with no lift`() {
        val motion = TsuzukiPillMotion(scope = null, initialIndex = 0)
        motion.reduceMotion = true
        motion.stepDp = 60f
        motion.animateTo(3)

        val active = motion.tick(1f / 60f, 1.0 / 60.0)

        assertFalse(active)
        assertEquals(3, motion.index)
        assertEquals(3f, motion.position)
        assertTrue(motion.isFlat)
        assertFalse(motion.isLifted)
        assertEquals(0f, motion.liftX)
        assertEquals(0f, motion.liftY)
        assertEquals(0f, motion.deviation)
    }

    @Test
    fun `pill reduced motion drag tracks the finger exactly`() {
        val motion = TsuzukiPillMotion(scope = null, initialIndex = 1)
        motion.reduceMotion = true
        motion.startDrag()
        assertFalse(motion.isLifted)
        motion.dragTo(2.5f)
        motion.tick(1f / 60f, 1.0 / 60.0)
        assertEquals(2.5f, motion.position)
        motion.release(1)
        motion.tick(1f / 60f, 2.0 / 60.0)
        assertEquals(1f, motion.position)
        assertTrue(motion.isFlat)
    }

    @Test
    fun `pill animateTo same index is no-op`() {
        val motion = TsuzukiPillMotion(scope = null, initialIndex = 2)
        motion.animateTo(2)
        assertTrue(motion.isFlat)
        assertFalse(motion.isLifted)
        assertEquals(2f, motion.position)
    }

    @Test
    fun `pill retargets mid-flight smoothly keeping velocity`() {
        val motion = TsuzukiPillMotion(scope = null, initialIndex = 0)
        motion.stepDp = 60f
        motion.animateTo(2)

        var time = 0.0
        val dt = 1f / 60f
        var previous = 0f
        var previousDelta = 0f
        for (frame in 0 until 3) {
            time += dt
            motion.tick(dt, time)
            previousDelta = abs(motion.position - previous)
            previous = motion.position
        }
        assertTrue(motion.isLifted)
        previous = motion.position

        motion.animateTo(4)
        assertEquals(4, motion.index)
        assertTrue(motion.isLifted)

        val deltaBefore = previousDelta
        time += dt
        motion.tick(dt, time)
        val deltaAtRetarget = abs(motion.position - previous)
        previous = motion.position

        var frames = 0
        var maxJump = deltaAtRetarget
        while (frames < 300 && motion.tick(dt, time + dt)) {
            time += dt
            frames++
            val delta = abs(motion.position - previous)
            maxJump = maxOf(maxJump, delta)
            previous = motion.position
            previousDelta = delta
        }
        assertTrue(deltaAtRetarget <= deltaBefore + 0.2f, "teleport at retarget: $deltaAtRetarget after $deltaBefore")
        assertTrue(maxJump < 0.6f, "jump $maxJump")
        assertTrue(previousDelta < 0.05f, "tail motion $previousDelta")
        assertTrue(motion.isFlat)
        assertEquals(4f, motion.position, 0.01f)
    }

    @Test
    fun `pill release clamps extreme velocities to the tuned maximum`() {
        fun trace(velocity: Float): List<Float> {
            val motion = TsuzukiPillMotion(scope = null, initialIndex = 0)
            motion.stepDp = 60f
            motion.startDrag()
            motion.dragTo(0.5f)
            motion.release(3, velocity)
            val samples = mutableListOf<Float>()
            var time = 0.0
            val dt = 1f / 120f
            var frames = 0
            while (frames < 400 && motion.tick(dt, time + dt)) {
                time += dt
                frames++
                samples += motion.position
            }
            samples += motion.position
            return samples
        }

        val clamped = trace(1000f)
        val tuned = trace(25f)
        assertEquals(tuned.size, clamped.size)
        for (i in tuned.indices) {
            assertEquals(tuned[i], clamped[i], 1e-3f, "frame $i")
        }
        assertEquals(3f, clamped.last(), 0.01f)
    }

    @Test
    fun `pill release with no velocity settles as a plain tap`() {
        val motion = TsuzukiPillMotion(scope = null, initialIndex = 0)
        motion.stepDp = 60f
        motion.startDrag()
        motion.dragTo(0.4f)
        motion.release(1, 0f)
        runUntilIdle(motion)
        assertTrue(motion.isFlat)
        assertEquals(1f, motion.position, 0.01f)
        assertEquals(0f, motion.deviation, 0.01f)
    }

    @Test
    fun `pill goes idle and stays idle after settling`() {
        val motion = TsuzukiPillMotion(scope = null, initialIndex = 0)
        motion.stepDp = 60f
        motion.animateTo(2)
        runUntilIdle(motion)
        assertFalse(motion.tick(1f / 60f, 99.0))
        assertFalse(motion.tick(1f / 60f, 99.1))
        assertTrue(motion.isFlat)
        assertEquals(2f, motion.position, 1e-4f)
    }

    @Test
    fun `one long frame integrates like fixed substeps`() {
        fun run(frameDt: Float, frames: Int): TsuzukiPillMotion {
            val motion = TsuzukiPillMotion(scope = null, initialIndex = 0)
            motion.stepDp = 60f
            motion.animateTo(1)
            var time = 0.0
            repeat(frames) {
                time += frameDt
                motion.tick(frameDt, time)
            }
            return motion
        }

        val long = run(0.5f, 1)
        val fine = run(1f / 200f, 100)
        assertFalse(long.position.isNaN())
        assertTrue(abs(long.position) < 10f)
        assertEquals(fine.position, long.position, 0.01f)
    }

    @Test
    fun `squash follows travel direction and stays clamped`() {
        val motion = TsuzukiPillMotion(scope = null, initialIndex = 0)
        motion.stepDp = 60f
        motion.animateTo(1)

        var time = 0.0
        val dt = 1f / 240f
        var peak = 0f
        var trough = 0f
        var frames = 0
        while (frames < 600) {
            time += dt
            frames++
            val active = motion.tick(dt, time)
            peak = maxOf(peak, motion.deviation)
            trough = minOf(trough, motion.deviation)
            assertTrue(abs(motion.deviation) <= 0.10f + 1e-3f, "deviation ${motion.deviation}")
            if (!active) break
        }
        assertTrue(peak > 0.002f, "launch squash $peak")
        assertTrue(trough < -0.001f, "brake squash $trough")
    }

    @Test
    fun `squash stays quiet at constant drag speed and is disabled without step size`() {
        val silent = TsuzukiPillMotion(scope = null, initialIndex = 0)
        silent.startDrag()
        var time = 0.0
        val dt = 1f / 60f
        var position = 0f
        repeat(40) {
            time += dt
            position += 0.01f
            silent.dragTo(position)
            silent.tick(dt, time)
        }
        assertTrue(abs(silent.deviation) < 0.01f, "constant speed deviation ${silent.deviation}")

        val disabled = TsuzukiPillMotion(scope = null, initialIndex = 0)
        disabled.stepDp = 0f
        disabled.animateTo(1)
        repeat(30) {
            time += dt
            disabled.tick(dt, time)
        }
        assertEquals(0f, disabled.deviation)
    }

    @Test
    fun `pill liveSize math matches rest and lifted geometry`() {
        val motion = TsuzukiPillMotion(scope = null, initialIndex = 0)
        val rest = Size(60f, 40f)
        val lifted = Size(75f, 50f)

        val sizeAtRest = motion.liveSize(rest, lifted)
        assertEquals(60f, sizeAtRest.width, 0.001f)
        assertEquals(40f, sizeAtRest.height, 0.001f)

        motion.stepDp = 60f
        motion.animateTo(1)
        motion.tick(1f / 120f, 1.0 / 120.0)
        val live = motion.liveSize(rest, lifted)
        val lift = (motion.liftX + motion.liftY) / 2f
        assertEquals(60f + 15f * lift, live.width / (1f + motion.deviation), 0.05f)
        assertTrue(live.height >= 40f)
    }

    @Test
    fun `pill center calculation handles ltr and rtl`() {
        val ltrCenter = calculatePillCenter(
            position = 1f,
            tabStepPx = 60f,
            tabWidthPx = 50f,
            isRtl = false,
            rowWidthPx = 300f,
        )
        assertEquals(85f, ltrCenter, 0.001f)

        val rtlCenter = calculatePillCenter(
            position = 1f,
            tabStepPx = 60f,
            tabWidthPx = 50f,
            isRtl = true,
            rowWidthPx = 300f,
        )
        assertEquals(215f, rtlCenter, 0.001f)
    }

    @Test
    fun `accessory visibility rules enforce inline state and no occlusion`() {
        assertFalse(isTabBarAccessoryVisible(isInline = false, hasAccessory = true))
        assertTrue(isTabBarAccessoryVisible(isInline = true, hasAccessory = true))
        assertFalse(isTabBarAccessoryVisible(isInline = true, hasAccessory = false))
        assertFalse(isTabBarAccessoryVisible(isInline = true, hasAccessory = true, imeVisible = true))
        assertFalse(isTabBarAccessoryVisible(isInline = true, hasAccessory = true, searchActive = true))
        assertFalse(isTabBarAccessoryVisible(isInline = false, hasAccessory = true, imeVisible = true))
    }

    @Test
    fun `clearance values maintain parity across expanded and inline states`() {
        assertEquals(54.dp, TsuzukiTabBarClearance.BarHeight)
        assertEquals(8.dp, TsuzukiTabBarClearance.OuterVerticalPadding)
        assertEquals(70.dp, TsuzukiTabBarClearance.Clearance)

        val expandedClearance = TsuzukiTabBarClearance.BarHeight + TsuzukiTabBarClearance.OuterVerticalPadding * 2
        val inlineClearance = TsuzukiTabBarClearance.BarHeight + TsuzukiTabBarClearance.OuterVerticalPadding * 2
        assertEquals(expandedClearance, inlineClearance)
        assertEquals(0.dp, expandedClearance - inlineClearance)
    }

    @Test
    fun `morph never lays expanded content narrower than its natural width`() {
        val padLeft = 8f
        val padRight = 8f
        val expandedWidth = 1000f
        val collapsedWidth = 180f
        val collapsedIconCenterX = 40f
        val iconLiftY = 6f
        val tabCount = 3

        fun iconCapsuleX(geometry: TabBarMorphGeometry, selectedIndex: Int, isRtl: Boolean): Float {
            val inner = geometry.contentBoxWidthPx - padLeft - padRight
            val slot = inner / tabCount
            val centerInBox = padLeft + if (isRtl) inner - (selectedIndex + 0.5f) * slot else (selectedIndex + 0.5f) * slot
            return geometry.contentBoxOffset.x + centerInBox
        }

        for (selectedIndex in 0 until tabCount) {
            for (isRtl in booleanArrayOf(false, true)) {
                var previousCapsule = Float.MAX_VALUE
                var progress = 0f
                while (progress <= 1.0001f) {
                    val geometry = tabBarMorphGeometry(
                        progress = progress,
                        expandedWidthPx = expandedWidth,
                        collapsedWidthPx = collapsedWidth,
                        contentPaddingLeftPx = padLeft,
                        contentPaddingRightPx = padRight,
                        tabCount = tabCount,
                        selectedIndex = selectedIndex,
                        isRtl = isRtl,
                        collapsedIconCenterXPx = collapsedIconCenterX,
                        iconLiftYPx = iconLiftY,
                    )

                    assertEquals(expandedWidth, geometry.contentBoxWidthPx, 0.001f)
                    assertTrue(
                        geometry.capsuleWidthPx in collapsedWidth..expandedWidth + 0.001f,
                        "capsule ${geometry.capsuleWidthPx} at $progress",
                    )
                    assertTrue(geometry.capsuleWidthPx <= previousCapsule + 0.001f)
                    previousCapsule = geometry.capsuleWidthPx

                    val iconX = iconCapsuleX(geometry, selectedIndex, isRtl)
                    assertTrue(iconX >= 0f, "icon left of capsule at $progress: $iconX")
                    assertTrue(iconX <= geometry.capsuleWidthPx, "icon right of capsule at $progress: $iconX")

                    if (progress <= 0.001f) {
                        assertEquals(0f, geometry.contentBoxOffset.x, 0.001f)
                        assertEquals(0f, geometry.contentBoxOffset.y, 0.001f)
                        assertEquals(1f, geometry.tabsAlpha)
                        assertEquals(0f, geometry.collapsedAlpha)
                    }
                    if (progress >= 0.999f) {
                        val iconRest = padLeft + if (isRtl) {
                            (expandedWidth - padLeft - padRight) * (1f - (selectedIndex + 0.5f) / tabCount)
                        } else {
                            (expandedWidth - padLeft - padRight) * ((selectedIndex + 0.5f) / tabCount)
                        }
                        assertEquals(collapsedIconCenterX, iconX, 0.001f)
                        assertEquals(collapsedIconCenterX - iconRest, geometry.contentBoxOffset.x, 0.001f)
                        assertEquals(iconLiftY, geometry.contentBoxOffset.y, 0.001f)
                        assertEquals(0f, geometry.tabsAlpha)
                        assertEquals(1f, geometry.collapsedAlpha, 1e-3f)
                    }
                    if (progress > 0.001f && progress < 0.4f + 1e-6f) {
                        assertEquals(1f, geometry.tabsAlpha, 1e-3f, "tabs fading before 40% at $progress")
                    }
                    if (progress >= 0.6f && progress < 0.999f) {
                        assertEquals(0f, geometry.tabsAlpha, "tabs visible past 60% at $progress")
                        assertTrue(geometry.collapsedAlpha > 0f, "chrome not started past 60% at $progress")
                        assertEquals(0f, geometry.expandedAlpha, "indicator visible past 60% at $progress")
                    }
                    if (progress > 0.45f && progress < 0.55f) {
                        assertTrue(geometry.tabsAlpha in 0.01f..0.99f, "no fade ramp at $progress")
                        assertEquals(0f, geometry.collapsedAlpha, "early chrome at $progress")
                    }
                    progress += 0.05f
                }
            }
        }
    }
}
