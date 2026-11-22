package dev.errnolink.tsuzuki.ui.shell

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Round 12 (review video 1): exactly one search field per screen. A declaration is hosted either
 * by the bottom dock or by the top row, never both — the double mount was the mid-screen capsule
 * (the two hosts captured each other's trigger bounds on the same state).
 */
class SearchHostTest {

    @Test
    fun `tab roots host at the bottom`() {
        assertTrue(searchHostsAtBottom(bottomHosted = false, isTabRoot = true))
    }

    @Test
    fun `bottom-hosted pushed pages host at the bottom too`() {
        assertTrue(searchHostsAtBottom(bottomHosted = true, isTabRoot = false))
    }

    @Test
    fun `ordinary pushed pages keep the top row`() {
        assertFalse(searchHostsAtBottom(bottomHosted = false, isTabRoot = false))
    }

    @Test
    fun `bottom hosting wins on a tab root as well`() {
        assertTrue(searchHostsAtBottom(bottomHosted = true, isTabRoot = true))
    }
}
