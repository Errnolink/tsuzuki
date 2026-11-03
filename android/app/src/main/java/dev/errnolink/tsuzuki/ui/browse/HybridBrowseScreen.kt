package dev.errnolink.tsuzuki.ui.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import eu.kanade.presentation.util.Tab

@Composable
fun HybridBrowseScreen(tab: Tab) {
    BrowseHomeScreen(tab = tab, discoverOnly = false)
}
