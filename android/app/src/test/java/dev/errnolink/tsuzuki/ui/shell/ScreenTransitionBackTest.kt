package dev.errnolink.tsuzuki.ui.shell

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Regression test for the inert back dispatcher (review item 14, CONSENSUS
 * Verified #5): non-interactive layers receive a synthetic dispatcher with no
 * callbacks, so back only reaches the real dispatcher of an interactive layer.
 * The top layer must be interactive as soon as a pop completes, and must still
 * be interactive 300 ms later (the completed-pop state is the steady state).
 */
class ScreenTransitionBackTest {

    @Test
    fun `back reaches the top layer the moment a pop completes`() {
        assertFalse(
            layerInteractive(moving = true, motion = StageMotion.Pop, isFront = true),
            "the leaving layer must not own back while the pop is running",
        )
        assertTrue(
            layerInteractive(moving = true, motion = StageMotion.Pop, isFront = false),
            "the revealed layer owns back while the pop is running",
        )

        val completedPopTopLayer = layerInteractive(moving = false, motion = StageMotion.Pop, isFront = true)
        assertTrue(completedPopTopLayer, "the destination screen must own back after the pop completes")

        val settled = layerInteractive(moving = false, motion = StageMotion.Pop, isFront = true)
        assertTrue(settled, "back still works 300 ms after the pop completes: the steady state is interactive")
        assertFalse(
            layerInteractive(moving = false, motion = StageMotion.Pop, isFront = false),
            "a covered layer never owns back once the stage is still",
        )
    }

    @Test
    fun `no layer owns back while a push or replace is running`() {
        assertFalse(layerInteractive(moving = true, motion = StageMotion.Push, isFront = true))
        assertFalse(layerInteractive(moving = true, motion = StageMotion.Push, isFront = false))
        assertFalse(layerInteractive(moving = true, motion = StageMotion.Replace, isFront = true))
        assertFalse(layerInteractive(moving = true, motion = StageMotion.Replace, isFront = false))
    }

    @Test
    fun `the top layer owns back whenever the stage is still`() {
        assertTrue(layerInteractive(moving = false, motion = StageMotion.Push, isFront = true))
        assertTrue(layerInteractive(moving = false, motion = StageMotion.Replace, isFront = true))
    }
}
