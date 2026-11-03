package dev.errnolink.tsuzuki.ui.history

import androidx.compose.runtime.Composable
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.history.HistoryTab

class FullHistoryScreen : Screen() {

    @Composable
    override fun Content() {
        HistoryTab.Content()
    }
}
