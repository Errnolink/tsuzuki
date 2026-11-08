package dev.errnolink.tsuzuki.ui.shell

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HeroTimingTest {

    private fun openProgress(millis: Float): Float =
        HeroOpenEasing.transform((millis / HeroOpenMillis).coerceIn(0f, 1f))

    private fun closeProgress(millis: Float, duration: Int): Float =
        1f - HeroCloseEasing.transform((millis / duration).coerceIn(0f, 1f))

    private fun firstMillisReaching(progress: (Float) -> Float, level: Float): Float {
        var t = 0f
        while (progress(t) < level) t += 1f
        return t
    }

    @Test
    fun `open travels long enough to read as motion and still moves on the first frame`() {
        val ninety = firstMillisReaching(::openProgress, 0.9f)
        assertTrue(ninety in 150f..230f, "90% of the open reached at $ninety ms")
        assertTrue(openProgress(8.3f) > 0.03f, "first 120 Hz frame barely moved: ${openProgress(8.3f)}")
        assertEquals(1f, openProgress(HeroOpenMillis.toFloat()), 1e-3f)
    }

    @Test
    fun `open drops the tile image during the first third of travel`() {
        val third = firstMillisReaching(::openProgress, FlyingCoverGone)
        assertTrue(third < HeroOpenMillis / 3f, "tile image still showing at $third ms")
        assertEquals(1f, flyingCoverAlpha(openProgress(0f)), 1e-3f)
    }

    @Test
    fun `close shows the page first and becomes the cover within the first third`() {
        val duration = heroCloseMillis(remaining = 1f, speed = 0f)
        assertEquals(0f, heroPopCrossfade(closeProgress(0f, duration)), 1e-3f)
        assertEquals(1f, heroPopCrossfade(closeProgress(duration / 3f, duration)), 1e-3f)
        var previous = 0f
        var t = 0f
        while (t <= duration) {
            val fade = heroPopCrossfade(closeProgress(t, duration))
            assertTrue(fade >= previous - 1e-4f, "crossfade went back at $t ms")
            previous = fade
            t += 4f
        }
    }

    @Test
    fun `close keeps a settling tail instead of stopping hard`() {
        val duration = heroCloseMillis(remaining = 1f, speed = 0f)
        val lastQuarter = closeProgress(duration * 0.75f, duration)
        assertTrue(lastQuarter > 0f && lastQuarter < 0.05f, "tail at 75%: $lastQuarter")
        assertEquals(0f, closeProgress(duration.toFloat(), duration), 1e-4f)
    }

    @Test
    fun `background blur and dim lead the geometry and clear at rest`() {
        assertEquals(0f, heroBackdrop(0f), 1e-4f)
        assertEquals(1f, heroBackdrop(1f), 1e-4f)
        var previous = 0f
        for (step in 0..20) {
            val p = step / 20f
            val level = heroBackdrop(p)
            assertTrue(level >= p - 1e-4f, "backdrop trails geometry at $p")
            assertTrue(level >= previous - 1e-4f, "backdrop not monotonic at $p")
            previous = level
        }
        assertTrue(heroBackdrop(1f, drag = 1f) < heroBackdrop(1f), "dragging the card should reveal the page beneath")
    }

    @Test
    fun `a flick closes fast, a slow release closes relaxed, and speed in between scales smoothly`() {
        val slow = heroCloseMillis(remaining = 1f, speed = heroGestureSpeed(150f))
        val medium = heroCloseMillis(remaining = 1f, speed = heroGestureSpeed(1100f))
        val flick = heroCloseMillis(remaining = 1f, speed = heroGestureSpeed(3500f))
        assertTrue(slow >= 350, "slow release closed in $slow ms")
        assertTrue(flick <= 170, "flick closed in $flick ms")
        assertTrue(medium in (flick + 1) until slow, "medium $medium not between $flick and $slow")
        var previous = Int.MAX_VALUE
        var v = 0f
        while (v <= 4000f) {
            val ms = heroCloseMillis(remaining = 1f, speed = heroGestureSpeed(v))
            assertTrue(ms <= previous, "faster gesture closed slower at $v dp/s")
            previous = ms
            v += 50f
        }
    }

    @Test
    fun `back button closes relaxed unless it follows the landing right away`() {
        assertEquals(0f, heroButtonBackSpeed(5_000L))
        assertTrue(heroButtonBackSpeed(300L) > 0f)
        val quick = heroCloseMillis(1f, heroButtonBackSpeed(300L))
        val calm = heroCloseMillis(1f, heroButtonBackSpeed(5_000L))
        assertTrue(quick < calm, "quick back $quick vs calm back $calm")
    }

    @Test
    fun `pull distance needed to dismiss shrinks as the pull gets faster`() {
        val range = 1000f
        assertEquals(0f, heroDismissAmount(-200f, range))
        assertTrue(heroDismissAmount(100f, range) < heroDismissAmount(400f, range))
        assertEquals(1f, heroDismissAmount(5000f, range))
        assertTrue(!heroDismissCommits(heroDismissAmount(200f, range), 100f), "a slow short pull dismissed")
        assertTrue(heroDismissCommits(heroDismissAmount(400f, range), 100f), "a slow long pull did not dismiss")
        assertTrue(!heroDismissCommits(heroDismissAmount(120f, range), 600f), "a gentle 12% pull dismissed")
        assertTrue(heroDismissCommits(heroDismissAmount(80f, range), 3000f), "a downward flick did not dismiss")
        assertTrue(!heroDismissCommits(heroDismissAmount(400f, range), -1500f), "pulling back up still dismissed")
    }

    private companion object {
        const val FlyingCoverGone = 0.3f
    }
}
