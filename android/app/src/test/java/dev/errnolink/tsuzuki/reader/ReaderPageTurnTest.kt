package dev.errnolink.tsuzuki.reader

import dev.errnolink.tsuzuki.reader.engine.viewer.ViewerNavigation.NavigationRegion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ReaderPageTurnTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Tap regions to turn direction: sides follow the reading direction, the
    // menu region never turns, and the glow edge follows the tapped side.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `regions map to directions with the reading order`() {
        assertEquals(1, regionDirection(regionKind(NavigationRegion.NEXT), rightToLeft = false))
        assertEquals(-1, regionDirection(regionKind(NavigationRegion.PREV), rightToLeft = false))
        // on a right-to-left pager the left side is forward
        assertEquals(1, regionDirection(regionKind(NavigationRegion.LEFT), rightToLeft = true))
        assertEquals(-1, regionDirection(regionKind(NavigationRegion.RIGHT), rightToLeft = true))
        assertEquals(-1, regionDirection(regionKind(NavigationRegion.LEFT), rightToLeft = false))
        assertEquals(1, regionDirection(regionKind(NavigationRegion.RIGHT), rightToLeft = false))
        assertNull(regionDirection(regionKind(NavigationRegion.MENU), rightToLeft = true))
    }
}
