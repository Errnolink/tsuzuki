package eu.kanade.tachiyomi.ui.home

import eu.kanade.domain.ui.model.NavigationStyle
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.feed.FeedTab
import eu.kanade.tachiyomi.ui.history.HistoryTab
import eu.kanade.tachiyomi.ui.library.LibraryTab
import eu.kanade.tachiyomi.ui.more.MoreTab
import eu.kanade.tachiyomi.ui.updates.UpdatesTab

val MangaDexTabs: List<Tab> = listOf(LibraryTab, FeedTab, BrowseTab)

val ClassicTabs: List<Tab> = listOf(LibraryTab, UpdatesTab, HistoryTab, BrowseTab, MoreTab)

fun navigationTabs(style: NavigationStyle): List<Tab> = when (style) {
    NavigationStyle.MangaDex -> MangaDexTabs
    NavigationStyle.Classic -> ClassicTabs
}

fun openTabBottomTab(requested: HomeScreen.Tab, style: NavigationStyle): Tab? = when (requested) {
    is HomeScreen.Tab.Library -> LibraryTab
    HomeScreen.Tab.Updates -> if (style == NavigationStyle.MangaDex) FeedTab else UpdatesTab
    HomeScreen.Tab.History -> if (style == NavigationStyle.MangaDex) FeedTab else HistoryTab
    is HomeScreen.Tab.Browse -> BrowseTab
    is HomeScreen.Tab.More -> if (style == NavigationStyle.MangaDex) null else MoreTab
}

fun openTabFeedSegment(requested: HomeScreen.Tab, style: NavigationStyle): Int? {
    if (style != NavigationStyle.MangaDex) return null
    return when (requested) {
        HomeScreen.Tab.Updates -> FeedTab.SEGMENT_UPDATES
        HomeScreen.Tab.History -> FeedTab.SEGMENT_HISTORY
        else -> null
    }
}
