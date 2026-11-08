package eu.kanade.tachiyomi.ui.home

import eu.kanade.domain.ui.model.NavigationStyle
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.feed.FeedTab
import eu.kanade.tachiyomi.ui.history.HistoryTab
import eu.kanade.tachiyomi.ui.library.LibraryTab
import eu.kanade.tachiyomi.ui.more.MoreTab
import eu.kanade.tachiyomi.ui.updates.UpdatesTab
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NavigationTabsTest {

    private fun tabs(vararg expected: Tab) = expected.toList()

    @Test
    fun `Simple mode shows Library Feed Browse`() {
        assertEquals(tabs(LibraryTab, FeedTab, BrowseTab), navigationTabs(NavigationStyle.Simple))
    }

    @Test
    fun `Classic mode shows the stock tab composition`() {
        assertEquals(
            tabs(LibraryTab, UpdatesTab, HistoryTab, BrowseTab, MoreTab),
            navigationTabs(NavigationStyle.Classic),
        )
    }

    @Test
    fun `updates notification opens Feed tab Updates segment in Simple mode`() {
        val style = NavigationStyle.Simple
        val requested: HomeScreen.Tab = HomeScreen.Tab.Updates
        assertEquals(FeedTab, openTabBottomTab(requested, style))
        assertEquals(FeedTab.SEGMENT_UPDATES, openTabFeedSegment(requested, style))
    }

    @Test
    fun `history request opens Feed tab History segment in Simple mode`() {
        val style = NavigationStyle.Simple
        val requested: HomeScreen.Tab = HomeScreen.Tab.History
        assertEquals(FeedTab, openTabBottomTab(requested, style))
        assertEquals(FeedTab.SEGMENT_HISTORY, openTabFeedSegment(requested, style))
    }

    @Test
    fun `updates and history land on their own tabs in Classic mode`() {
        val style = NavigationStyle.Classic
        assertEquals(UpdatesTab, openTabBottomTab(HomeScreen.Tab.Updates, style))
        assertEquals(HistoryTab, openTabBottomTab(HomeScreen.Tab.History, style))
        assertNull(openTabFeedSegment(HomeScreen.Tab.Updates, style))
        assertNull(openTabFeedSegment(HomeScreen.Tab.History, style))
    }

    @Test
    fun `downloads and update error routes keep the current tab in Simple mode`() {
        val style = NavigationStyle.Simple
        assertNull(openTabBottomTab(HomeScreen.Tab.More(toDownloads = true), style))
        assertNull(openTabBottomTab(HomeScreen.Tab.More(toDownloads = false, toLibraryUpdateErrors = true), style))
        assertEquals(MoreTab, openTabBottomTab(HomeScreen.Tab.More(toDownloads = true), NavigationStyle.Classic))
    }

    @Test
    fun `library and browse requests are mode independent`() {
        for (style in NavigationStyle.entries) {
            assertEquals(LibraryTab, openTabBottomTab(HomeScreen.Tab.Library(), style))
            assertEquals(LibraryTab, openTabBottomTab(HomeScreen.Tab.Library(42L), style))
            assertEquals(BrowseTab, openTabBottomTab(HomeScreen.Tab.Browse(), style))
            assertEquals(BrowseTab, openTabBottomTab(HomeScreen.Tab.Browse(true), style))
            assertNull(openTabFeedSegment(HomeScreen.Tab.Browse(), style))
        }
    }

    @Test
    fun `mode switch always leaves a valid fallback because Library is in both compositions`() {
        for (style in NavigationStyle.entries) {
            assertTrue(LibraryTab in navigationTabs(style))
            assertTrue(navigationTabs(style).isNotEmpty())
        }
        assertTrue(FeedTab !in navigationTabs(NavigationStyle.Classic))
    }
}
