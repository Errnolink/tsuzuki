package dev.errnolink.tsuzuki.ui.browse

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.FlipToBack
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.rememberTopBarSearch
import dev.errnolink.tsuzuki.ui.shell.ShellAction
import dev.errnolink.tsuzuki.ui.shell.ShellActionSheet
import dev.errnolink.tsuzuki.ui.shell.ShellContentScaffold
import dev.errnolink.tsuzuki.ui.shell.FeedSectionSkeleton
import dev.errnolink.tsuzuki.ui.shell.ShellSheetAction
import dev.errnolink.tsuzuki.ui.shell.ShellTopAction
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.browse.SourceFeedUI
import eu.kanade.presentation.browse.components.bulkSelectionButton
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.BulkSelectionToolbar
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.model.FeedSavedSearch
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.plus


@Composable
fun SourceFeedScreen(
    name: String,
    isLoading: Boolean,
    items: ImmutableList<SourceFeedUI>,
    hasFilters: Boolean,
    onFabClick: () -> Unit,
    onClickBrowse: () -> Unit,
    onClickLatest: () -> Unit,
    onClickSavedSearch: (SavedSearch) -> Unit,
    // KMK -->
    // onClickDelete: (FeedSavedSearch) -> Unit,
    onLongClickFeed: (SourceFeedUI.SourceSavedSearch) -> Unit,
    // KMK <--
    onClickManga: (Manga) -> Unit,
    onClickSearch: (String) -> Unit,
    searchQuery: String?,
    onSearchQueryChange: (String?) -> Unit,
    getMangaState: @Composable (Manga) -> State<Manga>,
    // KMK -->
    navigateUp: () -> Unit,
    onWebViewClick: (() -> Unit)?,
    onToggleIncognito: () -> Unit,
    onSourceSettingClick: (() -> Unit?)?,
    onSortFeedClick: (() -> Unit)?,
    onLongClickManga: (Manga) -> Unit,
    bulkFavoriteScreenModel: BulkFavoriteScreenModel,
    // KMK <--
) {
    // KMK -->
    val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()
    // KMK <--

    var showActions by remember { mutableStateOf(false) }
    ShellActionSheet(
        visible = showActions,
        title = name,
        actions = buildList {
            add(ShellSheetAction("Select manga", onClick = bulkFavoriteScreenModel::toggleSelectionMode))
            onWebViewClick?.let { add(ShellSheetAction("Open website", onClick = it)) }
            add(ShellSheetAction("Toggle incognito", onClick = onToggleIncognito))
            onSortFeedClick?.let { add(ShellSheetAction("Sort feed", onClick = it)) }
            onSourceSettingClick?.let { add(ShellSheetAction("Source settings") { it() }) }
        },
        onDismissRequest = { showActions = false },
    )
    val search = if (bulkFavoriteState.selectionMode) {
        null
    } else {
        rememberTopBarSearch(
            query = searchQuery.orEmpty(),
            onQueryChange = onSearchQueryChange,
            placeholder = "Search $name",
            onSearch = { onClickSearch(searchQuery.orEmpty()) },
            onClose = { onSearchQueryChange(null) },
        )
    }
    LaunchedEffect(bulkFavoriteState.selectionMode) {
        if (bulkFavoriteState.selectionMode) {
            search?.state?.collapse()
        } else {
            search?.state?.expand()
        }
    }
    ShellContentScaffold(
        title = if (bulkFavoriteState.selectionMode) "${bulkFavoriteState.selection.size} selected" else name,
        navigateUp = navigateUp,
        actions = if (bulkFavoriteState.selectionMode) {
            listOf(
                ShellTopAction(
                    "Select all",
                    Icons.Outlined.SelectAll,
                    { items.flatMap { it.results.orEmpty() }.forEach(bulkFavoriteScreenModel::select) },
                ),
                ShellTopAction(
                    "Invert selection",
                    Icons.Outlined.FlipToBack,
                    { bulkFavoriteScreenModel.reverseSelection(items.flatMap { it.results.orEmpty() }) },
                ),
                ShellTopAction("Done", Icons.Outlined.Close, bulkFavoriteScreenModel::toggleSelectionMode),
            )
        } else {
            listOf(
                ShellTopAction("Filters and saved searches", Icons.Outlined.FilterList, onFabClick, active = hasFilters),
                ShellTopAction("More", Icons.Outlined.MoreHoriz, { showActions = true }),
            )
        },
        search = search,
        header = {
            if (bulkFavoriteState.selectionMode) {
                PillButton("Add to library", bulkFavoriteScreenModel::addFavorite, Modifier.padding(horizontal = 20.dp))
            }
        },
    ) { paddingValues ->
        if (isLoading) {
            dev.errnolink.tsuzuki.ui.details.SkeletonPulseClock {
                androidx.compose.foundation.layout.Column(Modifier.padding(paddingValues + topSmallPaddingValues)) {
                    FeedSectionSkeleton()
                    FeedSectionSkeleton()
                }
            }
        }
        else SourceFeedList(
            items = items,
            paddingValues = paddingValues,
            getMangaState = getMangaState,
            onClickBrowse = onClickBrowse,
            onClickLatest = onClickLatest,
            onClickSavedSearch = onClickSavedSearch,
            onLongClickFeed = onLongClickFeed,
            onClickManga = onClickManga,
            onLongClickManga = onLongClickManga,
            selection = bulkFavoriteState.selection,
        )
    }
}

@Composable
fun SourceFeedList(
    items: ImmutableList<SourceFeedUI>,
    paddingValues: PaddingValues,
    getMangaState: @Composable ((Manga) -> State<Manga>),
    onClickBrowse: () -> Unit,
    onClickLatest: () -> Unit,
    onClickSavedSearch: (SavedSearch) -> Unit,
    // KMK -->
    // onClickDelete: (FeedSavedSearch) -> Unit,
    onLongClickFeed: (SourceFeedUI.SourceSavedSearch) -> Unit,
    // KMK <--
    onClickManga: (Manga) -> Unit,
    // KMK -->
    onLongClickManga: (Manga) -> Unit,
    selection: List<Manga>,
    // KMK <--
) {
    ScrollbarLazyColumn(
        contentPadding = paddingValues + topSmallPaddingValues,
    ) {
        // KMK -->
        items(
            items,
            key = { "source-feed-${it.id}" },
        ) { item ->
            // KMK <--
            GlobalSearchResultItem(
                title =
                // KMK -->
                if (item !is SourceFeedUI.SourceSavedSearch) {
                    stringResource(item.title as StringResource)
                } else {
                    // KMK <--
                    item.title
                },
                subtitle = null,
                onLongClick = if (item is SourceFeedUI.SourceSavedSearch) {
                    {
                        // KMK -->
                        onLongClickFeed(item)
                        // KMK <--
                    }
                } else {
                    null
                },
                onClick = when (item) {
                    is SourceFeedUI.Browse -> onClickBrowse
                    is SourceFeedUI.Latest -> onClickLatest
                    is SourceFeedUI.SourceSavedSearch -> {
                        { onClickSavedSearch(item.savedSearch) }
                    }
                },
            ) {
                SourceFeedItem(
                    item = item,
                    getMangaState = { getMangaState(it) },
                    onClickManga = onClickManga,
                    // KMK -->
                    onLongClickManga = onLongClickManga,
                    selection = selection,
                    // KMK <--
                )
            }
        }
    }
}

@Composable
fun SourceFeedItem(
    item: SourceFeedUI,
    getMangaState: @Composable ((Manga) -> State<Manga>),
    onClickManga: (Manga) -> Unit,
    // KMK -->
    onLongClickManga: (Manga) -> Unit,
    selection: List<Manga>,
    // KMK <--
) {
    val results = item.results
    when {
        results == null -> {
            GlobalSearchLoadingResultItem()
        }
        results.isEmpty() -> {
            GlobalSearchErrorResultItem(message = stringResource(MR.strings.no_results_found))
        }
        else -> {
            GlobalSearchCardRow(
                titles = item.results.orEmpty(),
                getManga = getMangaState,
                onClick = onClickManga,
                // KMK -->
                onLongClick = onLongClickManga,
                selection = selection,
                // KMK <--
            )
        }
    }
}

