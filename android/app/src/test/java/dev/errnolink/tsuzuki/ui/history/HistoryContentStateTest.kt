package dev.errnolink.tsuzuki.ui.history

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HistoryContentStateTest {

    @Test
    fun `initial load shows loading`() {
        assertEquals(HistoryContentState.Loading, historyContentState(isLoading = true, hasEntries = false, hasModels = true))
        assertEquals(HistoryContentState.Loading, historyContentState(isLoading = true, hasEntries = false, hasModels = false))
    }

    @Test
    fun `settled empty history shows empty state`() {
        assertEquals(HistoryContentState.Empty, historyContentState(isLoading = false, hasEntries = false, hasModels = false))
    }

    @Test
    fun `entries present but models not yet computed keeps loading instead of blank screen`() {
        assertEquals(HistoryContentState.Loading, historyContentState(isLoading = false, hasEntries = true, hasModels = false))
    }

    @Test
    fun `computed models render content`() {
        assertEquals(HistoryContentState.Content, historyContentState(isLoading = false, hasEntries = true, hasModels = true))
    }
}
