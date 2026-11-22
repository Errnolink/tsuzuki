package dev.errnolink.tsuzuki.ui.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.errnolink.tsuzuki.designsystem.ToastHostState
import dev.errnolink.tsuzuki.ui.shell.ShellContentScaffold
import dev.errnolink.tsuzuki.ui.shell.TabContent
import dev.errnolink.tsuzuki.ui.shell.shellToolbarActions
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun BrowseTabWrapper(tab: TabContent, onBackPressed: (() -> Unit)? = null) {
    val toasts = remember { ToastHostState() }
    ShellContentScaffold(
        title = stringResource(tab.titleRes),
        navigateUp = onBackPressed,
        toasts = toasts,
        actions = shellToolbarActions(tab.actions),
    ) { paddingValues ->
        tab.content(paddingValues, toasts)
    }
}