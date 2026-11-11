package dev.errnolink.tsuzuki.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.designsystem.Search
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.ui.history.rememberHistoryFeedContent
import dev.errnolink.tsuzuki.ui.shell.ShellList
import dev.errnolink.tsuzuki.ui.shell.ShellTopAction
import eu.kanade.presentation.util.Screen
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

class ContinueReadingScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val feed = rememberHistoryFeedContent()
        ShellList(
            title = "Continue reading",
            navigateUp = { navigator.pop() },
            snackbar = feed.snackbarHostState,
            itemSpacing = 0.dp,
            search = feed.search,
            actions = listOf(
                ShellTopAction(stringResource(MR.strings.action_search), TsuzukiIcons.Search, feed.search.state::expand),
            ),
            content = feed.content,
        )
    }
}
