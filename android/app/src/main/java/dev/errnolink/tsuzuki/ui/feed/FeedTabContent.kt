package dev.errnolink.tsuzuki.ui.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.errnolink.tsuzuki.preference.FeedLayout
import dev.errnolink.tsuzuki.preference.TsuzukiPreferences
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun FeedTabContent() {
    val tsuzukiPreferences = remember { Injekt.get<TsuzukiPreferences>() }
    val feedLayout by tsuzukiPreferences.feedLayout().collectAsState()

    if (feedLayout == FeedLayout.NEW) {
        UnifiedFeedScreen().Content()
    } else {
        ClassicFeedContent()
    }
}
