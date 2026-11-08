package dev.errnolink.tsuzuki.designsystem

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HeaderScrolledAwayTest {
    private val titleBottom = 170

    @Test
    fun `the small title stays hidden while any part of the large title is below the bar`() {
        assertFalse(headerScrolledAway(headerTop = 0, headerBottom = titleBottom, firstVisibleIndex = 0, headerIndex = 0))
        assertFalse(headerScrolledAway(headerTop = -85, headerBottom = titleBottom, firstVisibleIndex = 0, headerIndex = 0))
        assertFalse(headerScrolledAway(headerTop = -169, headerBottom = titleBottom, firstVisibleIndex = 0, headerIndex = 0))
    }

    @Test
    fun `the small title appears once the large title's bottom edge passes under the bar`() {
        assertTrue(headerScrolledAway(headerTop = -170, headerBottom = titleBottom, firstVisibleIndex = 0, headerIndex = 0))
        assertTrue(headerScrolledAway(headerTop = -400, headerBottom = titleBottom, firstVisibleIndex = 0, headerIndex = 0))
    }

    @Test
    fun `a header recycled out of the list counts as gone, and one not yet laid out does not`() {
        assertTrue(headerScrolledAway(headerTop = null, headerBottom = 0, firstVisibleIndex = 3, headerIndex = 0))
        assertFalse(headerScrolledAway(headerTop = null, headerBottom = 0, firstVisibleIndex = 0, headerIndex = 0))
    }
}
