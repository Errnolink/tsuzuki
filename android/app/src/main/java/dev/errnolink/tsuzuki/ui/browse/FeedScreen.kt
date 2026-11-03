package dev.errnolink.tsuzuki.ui.browse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.errnolink.tsuzuki.designsystem.Banner
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.NoticeTone
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.SearchField
import dev.errnolink.tsuzuki.ui.shell.FeedSectionSkeleton
import dev.errnolink.tsuzuki.ui.shell.ShellEmpty
import dev.errnolink.tsuzuki.ui.shell.ShellRefresh
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.browse.feed.FeedScreenState
import eu.kanade.presentation.browse.key
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource

import eu.kanade.presentation.browse.FeedItemUI

@Composable
fun FeedScreen(
    state: FeedScreenState,
    contentPadding: PaddingValues,
    onClickSavedSearch: (SavedSearch, Source) -> Unit,
    onClickSource: (Source) -> Unit,
    onLongClickFeed: (FeedItemUI) -> Unit,
    onClickManga: (Manga) -> Unit,
    onLongClickManga: (Manga) -> Unit,
    selection: List<Manga>,
    onRefresh: () -> Unit,
    getMangaState: @Composable (Manga) -> State<Manga>,
) {
    ShellRefresh(state.isLoadingItems, !state.isLoadingItems, onRefresh) {
        LazyColumn(flingBehavior = dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior(), contentPadding = contentPadding) {
            when {
                state.isLoading -> {
                    item("feed-loading") {
                        Column {
                            FeedSectionSkeleton()
                            FeedSectionSkeleton()
                        }
                    }
                }
                state.isEmpty -> item("feed-empty") {
                    ShellEmpty("Your catalogs, together", stringResource(SYMR.strings.feed_tab_empty))
                }
                else -> items(state.items.orEmpty(), key = { it.feed.key }) { item ->
                    GlobalSearchResultItem(
                        title = item.title,
                        subtitle = item.subtitle,
                        onLongClick = { onLongClickFeed(item) },
                        onClick = {
                            if (item.savedSearch != null && item.source != null) onClickSavedSearch(item.savedSearch, item.source)
                            else item.source?.let(onClickSource)
                        },
                    ) {
                        FeedItem(item, getMangaState, onClickManga, onLongClickManga, selection)
                    }
                }
            }
        }
    }
}

@Composable
fun FeedItem(
    item: FeedItemUI,
    getMangaState: @Composable (Manga) -> State<Manga>,
    onClickManga: (Manga) -> Unit,
    onLongClickManga: (Manga) -> Unit,
    selection: List<Manga>,
) {
    val results = item.results
    when {
        results == null -> GlobalSearchLoadingResultItem()
        results.isEmpty() -> Banner(
            stringResource(MR.strings.no_results_found),
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            tone = NoticeTone.Neutral,
        )
        else -> GlobalSearchCardRow(results, getMangaState, onClickManga, onLongClickManga, selection)
    }
}

@Composable
fun FeedAddDialog(sources: ImmutableList<Source>, onDismiss: () -> Unit, onClickAdd: (Source?) -> Unit) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Long?>(null) }
    val matches = remember(sources, query) { sources.filter { it.name.contains(query, true) || it.id.toString() == query } }
    DetentSheet(true, stringResource(SYMR.strings.feed), onDismiss) {
        SearchField(query, { query = it }, {}, placeholder = "Search sources")
        GroupedRow("All sources", value = if (selected == null) "Selected" else null, onClick = { selected = null })
        matches.forEach { source ->
            GroupedRow(source.name, value = if (source.id == selected) "Selected" else null, onClick = { selected = source.id })
        }
        PillButton(stringResource(MR.strings.action_ok), { onClickAdd(sources.firstOrNull { it.id == selected }) })
    }
}

@Composable
fun FeedAddSearchDialog(
    source: Source,
    savedSearches: ImmutableList<SavedSearch?>,
    onDismiss: () -> Unit,
    onClickAdd: (Source, SavedSearch?) -> Unit,
) {
    var selected by remember(savedSearches) { mutableStateOf<Int?>(null) }
    DetentSheet(true, source.name, onDismiss) {
        savedSearches.forEachIndexed { index, search ->
            GroupedRow(
                title = search?.name ?: stringResource(if (source.supportsLatest) MR.strings.latest else MR.strings.popular),
                value = if (selected == index) "Selected" else null,
                onClick = { selected = index },
            )
        }
        PillButton(stringResource(MR.strings.action_ok), {
            selected?.let { onClickAdd(source, savedSearches[it]) }
        }, enabled = selected != null)
    }
}
