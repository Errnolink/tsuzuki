package dev.errnolink.tsuzuki.ui.updates

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UpcomingCalendarIndexTest {

    @Test
    fun `upcomingDateScrollIndex accounts for optional large title item`() {
        assertEquals(2, upcomingDateScrollIndex(headerIndex = 0, largeTitles = true))
        assertEquals(1, upcomingDateScrollIndex(headerIndex = 0, largeTitles = false))
        assertEquals(6, upcomingDateScrollIndex(headerIndex = 4, largeTitles = true))
        assertEquals(5, upcomingDateScrollIndex(headerIndex = 4, largeTitles = false))
    }
}
