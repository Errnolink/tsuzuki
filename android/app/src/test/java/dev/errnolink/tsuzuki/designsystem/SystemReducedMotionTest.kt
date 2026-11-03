package dev.errnolink.tsuzuki.designsystem

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SystemReducedMotionTest {
    @Test
    fun `unset scales default to full motion`() {
        assertFalse(systemReducedMotion(1f, 1f))
    }

    @Test
    fun `explicit zero on either scale means reduced motion`() {
        assertTrue(systemReducedMotion(0f, 1f))
        assertTrue(systemReducedMotion(1f, 0f))
        assertTrue(systemReducedMotion(0f, 0f))
    }

    @Test
    fun `fractional and boosted scales keep motion`() {
        assertFalse(systemReducedMotion(0.5f, 1f))
        assertFalse(systemReducedMotion(1.5f, 2f))
    }
}
