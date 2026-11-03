package dev.errnolink.tsuzuki.ui.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.errnolink.tsuzuki.preference.BrowseLayout
import dev.errnolink.tsuzuki.preference.TsuzukiPreferences
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun BrowseTabContent(tab: eu.kanade.presentation.util.Tab) {
    val tsuzukiPreferences = remember { Injekt.get<TsuzukiPreferences>() }
    val layout by tsuzukiPreferences.browseLayout().collectAsState()

    when (layout) {
        BrowseLayout.CLASSIC -> BrowseTab.ClassicContent()
        BrowseLayout.DISCOVER -> DiscoverBrowseScreen(tab)
        BrowseLayout.HYBRID -> HybridBrowseScreen(tab)
    }
}
