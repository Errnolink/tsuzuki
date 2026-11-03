package dev.errnolink.tsuzuki.ui.updates

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UpdatesContentStateTest {

    @Test
    fun `initial load shows loading`() {
        assertEquals(UpdatesContentState.Loading, updatesContentState(isLoading = true, hasItems = false, hasModels = true))
        assertEquals(UpdatesContentState.Loading, updatesContentState(isLoading = true, hasItems = false, hasModels = false))
    }

    @Test
    fun `no updates shows empty state`() {
        assertEquals(UpdatesContentState.Empty, updatesContentState(isLoading = false, hasItems = false, hasModels = false))
    }

    @Test
    fun `items present but models not yet computed keeps loading instead of flash of empty`() {
        assertEquals(UpdatesContentState.Loading, updatesContentState(isLoading = false, hasItems = true, hasModels = false))
    }

    @Test
    fun `computed models render content`() {
        assertEquals(UpdatesContentState.Content, updatesContentState(isLoading = false, hasItems = true, hasModels = true))
    }
}
