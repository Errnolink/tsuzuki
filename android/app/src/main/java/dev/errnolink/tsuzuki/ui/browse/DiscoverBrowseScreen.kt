package dev.errnolink.tsuzuki.ui.browse

import androidx.compose.runtime.Composable
import eu.kanade.presentation.util.Tab

@Composable
fun DiscoverBrowseScreen(tab: Tab) {
    BrowseHomeScreen(tab = tab, discoverOnly = true)
}
