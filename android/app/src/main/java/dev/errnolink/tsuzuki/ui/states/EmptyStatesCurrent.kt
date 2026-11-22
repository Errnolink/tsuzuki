package dev.errnolink.tsuzuki.ui.states

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.EmptyState
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.Search
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.ui.shell.ShellEmpty
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun CategoryEmptyCurrent(onBrowse: () -> Unit, modifier: Modifier = Modifier) {
    ShellEmpty(
        stringResource(MR.strings.tsuzuki_empty_library_title),
        stringResource(MR.strings.tsuzuki_empty_library_desc),
    ) {
        PillButton(stringResource(MR.strings.tsuzuki_browse_manga), onBrowse)
    }
}

@Composable
internal fun InCategoryEmptyCurrent(modifier: Modifier = Modifier) {
    ShellEmpty(
        stringResource(MR.strings.tsuzuki_empty_category_title),
        stringResource(MR.strings.tsuzuki_empty_category_desc),
    )
}

@Composable
internal fun SearchEmptyCurrent(
    query: String?,
    inGrid: Boolean,
    modifier: Modifier = Modifier,
) {
    ShellEmpty(
        stringResource(MR.strings.tsuzuki_no_matching_manga),
        when {
            inGrid -> stringResource(MR.strings.tsuzuki_empty_search_in_grid_desc)
            !query.isNullOrEmpty() -> stringResource(MR.strings.tsuzuki_empty_library_search_matches, query)
            else -> stringResource(MR.strings.tsuzuki_no_matching_manga_desc)
        },
    )
}

@Composable
internal fun FeedEmptyCurrent(description: String, modifier: Modifier = Modifier) {
    EmptyState(
        stringResource(MR.strings.tsuzuki_feed_empty_title),
        description,
        icon = { Icon(TsuzukiIcons.Search, null, Modifier.size(36.dp), tint = TsuzukiTheme.colors.secondary) },
    )
}

@Composable
internal fun HistoryEmptyCurrent(searching: Boolean, modifier: Modifier = Modifier) {
    ShellEmpty(
        stringResource(MR.strings.tsuzuki_history_empty_title),
        if (searching) {
            stringResource(MR.strings.tsuzuki_history_search_empty_desc)
        } else {
            stringResource(MR.strings.tsuzuki_history_empty_desc)
        },
    )
}
