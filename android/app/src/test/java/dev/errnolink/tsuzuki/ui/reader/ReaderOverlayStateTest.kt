package dev.errnolink.tsuzuki.ui.reader

import eu.kanade.tachiyomi.ui.reader.ReaderViewModel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReaderOverlayStateTest {
    @Test
    fun `hidden chrome does not receive ten page turns`() = runTest {
        val pages = (0..10).map { ReaderViewModel.State(currentPage = it, currentPageText = "${it + 1}") }
        assertEquals(1, pages.asFlow().readerChromeState().toList().size)
    }

    @Test
    fun `showing chrome receives the latest page and visible slider updates`() = runTest {
        val states = listOf(
            ReaderViewModel.State(currentPage = 0),
            ReaderViewModel.State(currentPage = 10),
            ReaderViewModel.State(currentPage = 10, menuVisible = true),
            ReaderViewModel.State(currentPage = 11, menuVisible = true),
            ReaderViewModel.State(currentPage = 11),
            ReaderViewModel.State(currentPage = 12),
        )
        assertEquals(listOf(0, 10, 11, 11), states.asFlow().readerChromeState().toList().map { it.currentPage })
    }

    @Test
    fun `hidden chrome still receives brightness dialogs and tools`() = runTest {
        val initial = ReaderViewModel.State()
        val states = listOf(
            initial,
            initial.copy(brightnessOverlayValue = -30),
            initial.copy(dialog = ReaderViewModel.Dialog.Settings),
            initial,
            initial.copy(ehUtilsVisible = true),
            initial,
        )
        assertEquals(states, states.asFlow().readerChromeState().toList())
    }
}
