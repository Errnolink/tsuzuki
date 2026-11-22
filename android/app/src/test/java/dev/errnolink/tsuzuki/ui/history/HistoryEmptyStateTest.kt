package dev.errnolink.tsuzuki.ui.history

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HistoryEmptyStateTest {

    @Test
    fun `an active query counts as searching`() {
        assertTrue(historyEmptySearching("nen"))
        assertTrue(historyEmptySearching("  "))
    }

    @Test
    fun `no query shows the intro copy`() {
        assertFalse(historyEmptySearching(null))
        assertFalse(historyEmptySearching(""))
    }
}
