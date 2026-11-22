package dev.errnolink.tsuzuki.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.ui.history.rememberHistoryFeedContent
import dev.errnolink.tsuzuki.ui.shell.ShellList
import eu.kanade.presentation.util.Screen
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

class ContinueReadingScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val feed = rememberHistoryFeedContent()
        ShellList(
            title = stringResource(MR.strings.tsuzuki_continue_reading),
            navigateUp = { navigator.pop() },
            toasts = feed.toasts,
            itemSpacing = 0.dp,
            search = feed.search,
            content = feed.content,
        )
    }
}
