package dev.errnolink.tsuzuki.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.GripVertical
import dev.errnolink.tsuzuki.designsystem.IconButton
import dev.errnolink.tsuzuki.designsystem.Trash2
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.ui.shell.ShellContentScaffold
import dev.errnolink.tsuzuki.ui.shell.ShellEmpty
import dev.errnolink.tsuzuki.ui.shell.ShellLoading
import eu.kanade.presentation.browse.SourceFeedUI
import eu.kanade.presentation.browse.key
import eu.kanade.tachiyomi.ui.browse.source.feed.SourceFeedState
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun SourceFeedOrderContent(
    state: SourceFeedState,
    onClickDelete: (FeedSavedSearch) -> Unit,
    onChangeOrder: (FeedSavedSearch, Int) -> Unit,
    navigateUp: (() -> Unit)? = null,
) {
    ShellContentScaffold(
        title = stringResource(KMR.strings.action_sort_feed),
        navigateUp = navigateUp,
    ) { paddingValues ->
        val feeds = state.items
            .filterIsInstance<SourceFeedUI.SourceSavedSearch>()
        when {
            state.isLoading -> ShellLoading("Loading feeds…")
            feeds.isEmpty() -> ShellEmpty(
                "Nothing to sort",
                "Save a search on this source to pin it as a feed here.",
            )
            else -> {
                val lazyListState = rememberLazyListState()
                val feedsState = remember { feeds.toMutableStateList() }
                val reorderableState = rememberReorderableLazyListState(lazyListState, PaddingValues(0.dp)) { from, to ->
                    val item = feedsState.removeAt(from.index)
                    feedsState.add(to.index, item)
                    onChangeOrder(item.feed, to.index)
                }

                LaunchedEffect(feeds) {
                    if (!reorderableState.isAnyItemDragging) {
                        feedsState.clear()
                        feedsState.addAll(feeds)
                    }
                }

                LazyColumn(
                    flingBehavior = dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior(),
                    modifier = Modifier.fillMaxSize(),
                    state = lazyListState,
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = paddingValues.calculateTopPadding() + 8.dp,
                        bottom = paddingValues.calculateBottomPadding() + 8.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        items = feedsState,
                        key = { it.feed.key },
                    ) { feed ->
                        ReorderableItem(reorderableState, feed.feed.key) {
                            FeedOrderRow(
                                modifier = Modifier.animateItem(),
                                title = feed.title,
                                onDelete = { onClickDelete(feed.feed) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReorderableCollectionItemScope.FeedOrderRow(
    title: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TsuzukiTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.grouped)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            TsuzukiIcons.GripVertical,
            contentDescription = null,
            modifier = Modifier
                .padding(8.dp)
                .draggableHandle(),
            tint = colors.secondary,
        )
        TsuzukiText(title, Modifier.weight(1f), maxLines = 2)
        IconButton(stringResource(MR.strings.action_delete), onDelete) {
            Icon(TsuzukiIcons.Trash2, null, Modifier.padding(4.dp), tint = colors.secondary)
        }
    }
}
