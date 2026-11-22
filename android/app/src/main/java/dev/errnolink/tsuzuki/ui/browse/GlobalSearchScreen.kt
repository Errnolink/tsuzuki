package dev.errnolink.tsuzuki.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.ArrowLeftRight
import dev.errnolink.tsuzuki.designsystem.Chip
import dev.errnolink.tsuzuki.designsystem.CircleCheck
import dev.errnolink.tsuzuki.designsystem.ListChecks
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.Search
import dev.errnolink.tsuzuki.designsystem.SelectionMark
import dev.errnolink.tsuzuki.designsystem.ToastHostState
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.X
import dev.errnolink.tsuzuki.designsystem.rememberTopBarSearch
import dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior
import dev.errnolink.tsuzuki.ui.shell.CoverRowSkeleton
import dev.errnolink.tsuzuki.ui.shell.CoverTile
import dev.errnolink.tsuzuki.ui.shell.ShellContentScaffold
import dev.errnolink.tsuzuki.ui.shell.ShellEmpty
import dev.errnolink.tsuzuki.ui.shell.ShellLoading
import dev.errnolink.tsuzuki.ui.shell.ShellTopAction
import eu.kanade.domain.chapter.interactor.SetReadStatus
import eu.kanade.presentation.browse.components.SourceIcon
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchItemResult
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchScreenModel
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SourceFilter
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

enum class GlobalLibraryFilter {
    ALL,
    IN_LIBRARY,
    NOT_IN_LIBRARY,
}

internal enum class GlobalSearchEmptyKind {
    NONE,
    NO_QUERY,
    NO_SOURCES,
    SEARCHING_FILTERED,
    NO_RESULTS,
    NO_LIBRARY_FILTER_MATCHES,
}

internal fun filterGlobalSearchMangas(
    results: List<Manga>,
    libraryFilter: GlobalLibraryFilter,
): List<Manga> = when (libraryFilter) {
    GlobalLibraryFilter.ALL -> results
    GlobalLibraryFilter.IN_LIBRARY -> results.filter { it.favorite }
    GlobalLibraryFilter.NOT_IN_LIBRARY -> results.filter { !it.favorite }
}

internal fun visibleGlobalSearchMangas(
    items: Map<Source, SearchItemResult>,
    libraryFilter: GlobalLibraryFilter,
): List<Manga> = items.values
    .filterIsInstance<SearchItemResult.Success>()
    .flatMap { filterGlobalSearchMangas(it.result, libraryFilter) }

internal fun resolveGlobalSearchEmptyKind(
    searchQuery: String?,
    allItems: Map<Source, SearchItemResult>,
    filteredItems: Map<Source, SearchItemResult>,
    libraryFilter: GlobalLibraryFilter,
    onlyShowHasResults: Boolean,
    visibleResultCount: Int,
): GlobalSearchEmptyKind {
    if (searchQuery.isNullOrBlank() && allItems.isEmpty()) return GlobalSearchEmptyKind.NO_QUERY
    if (allItems.isEmpty()) return GlobalSearchEmptyKind.NO_SOURCES
    if (filteredItems.isEmpty() && onlyShowHasResults) {
        return if (allItems.values.any { it is SearchItemResult.Loading }) {
            GlobalSearchEmptyKind.SEARCHING_FILTERED
        } else {
            GlobalSearchEmptyKind.NO_RESULTS
        }
    }
    if (filteredItems.values.any { it is SearchItemResult.Loading || it is SearchItemResult.Error }) {
        return GlobalSearchEmptyKind.NONE
    }
    if (visibleResultCount == 0) {
        val hadAnyUnfilteredResult = filteredItems.values
            .filterIsInstance<SearchItemResult.Success>()
            .any { it.result.isNotEmpty() }
        return if (libraryFilter != GlobalLibraryFilter.ALL && hadAnyUnfilteredResult) {
            GlobalSearchEmptyKind.NO_LIBRARY_FILTER_MATCHES
        } else {
            GlobalSearchEmptyKind.NO_RESULTS
        }
    }
    return GlobalSearchEmptyKind.NONE
}

internal suspend fun markSelectedMangasRead(
    selection: List<Manga>,
    markMangaRead: suspend (Manga) -> SetReadStatus.Result,
): Int {
    var updated = 0
    selection.distinctBy { it.id }.forEach { manga ->
        if (markMangaRead(manga) is SetReadStatus.Result.Success) {
            updated++
        }
    }
    return updated
}

@Composable
fun GlobalSearchScreen(
    state: SearchScreenModel.State,
    fromSourceId: Long? = null,
    title: String = "Search",
    navigateUp: () -> Unit,
    onChangeSearchQuery: (String?) -> Unit,
    onSearch: (String) -> Unit,
    onChangeSearchFilter: (SourceFilter) -> Unit,
    onToggleResults: () -> Unit,
    getManga: @Composable (Manga) -> State<Manga>,
    onClickSource: (Source) -> Unit,
    onRetrySource: (Source) -> Unit,
    onClickItem: (Manga) -> Unit,
    onLongClickItem: (Manga) -> Unit,
    bulkFavoriteScreenModel: BulkFavoriteScreenModel,
    hasPinnedSources: Boolean,
) {
    val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val toasts = remember { ToastHostState() }
    val setReadStatus = remember { Injekt.get<SetReadStatus>() }
    var markingRead by remember { mutableStateOf(false) }
    var libraryFilter by remember { mutableStateOf(GlobalLibraryFilter.ALL) }

    val search = if (bulkFavoriteState.selectionMode) {
        null
    } else {
        rememberTopBarSearch(
            query = state.searchQuery.orEmpty(),
            onQueryChange = onChangeSearchQuery,
            placeholder = "Title or MangaDex link",
            onSearch = { onSearch(state.searchQuery.orEmpty()) },
            bottomHosted = true,
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
        title = if (bulkFavoriteState.selectionMode) "${bulkFavoriteState.selection.size} selected" else title,
        navigateUp = {
            if (bulkFavoriteState.selectionMode) {
                bulkFavoriteScreenModel.toggleSelectionMode(false)
            } else {
                navigateUp()
            }
        },
        toasts = toasts,
        capContent = false,
        actions = if (bulkFavoriteState.selectionMode) {
            listOf(
                ShellTopAction(
                    "Select all",
                    TsuzukiIcons.ListChecks,
                    {
                        visibleGlobalSearchMangas(state.filteredItems, libraryFilter)
                            .forEach(bulkFavoriteScreenModel::select)
                    },
                ),
                ShellTopAction(
                    "Invert selection",
                    TsuzukiIcons.ArrowLeftRight,
                    {
                        bulkFavoriteScreenModel.reverseSelection(
                            visibleGlobalSearchMangas(state.filteredItems, libraryFilter),
                        )
                    },
                ),
                ShellTopAction("Done", TsuzukiIcons.X, bulkFavoriteScreenModel::toggleSelectionMode),
            )
        } else {
            listOf(ShellTopAction("Select results", TsuzukiIcons.CircleCheck, bulkFavoriteScreenModel::toggleSelectionMode))
        },
        search = search,
        header = {
            if (bulkFavoriteState.selectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val actionEnabled = bulkFavoriteState.selection.isNotEmpty() && !bulkFavoriteState.isRunning && !markingRead
                    PillButton(
                        label = "Add to library",
                        onClick = bulkFavoriteScreenModel::addFavorite,
                        enabled = actionEnabled,
                    )
                    PillButton(
                        label = "Mark as read",
                        onClick = {
                            val targets = bulkFavoriteState.selection.toList()
                            markingRead = true
                            scope.launch {
                                try {
                                    val updated = withContext(Dispatchers.IO) {
                                        markSelectedMangasRead(targets) { manga ->
                                            setReadStatus.await(manga = manga, read = true)
                                        }
                                    }
                                    bulkFavoriteScreenModel.toggleSelectionMode(false)
                                    toasts.show(
                                        if (updated > 0) "Marked $updated as read" else "No unread chapters to mark read",
                                    )
                                } finally {
                                    markingRead = false
                                }
                            }
                        },
                        prominent = false,
                        enabled = actionEnabled,
                    )
                }
            } else {
                LazyRow(
                    flingBehavior = tsuzukiFlingBehavior(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (hasPinnedSources) {
                        item(key = "filter-pinned") {
                            Chip(
                                label = "Pinned",
                                selected = state.sourceFilter == SourceFilter.PinnedOnly,
                                onClick = { onChangeSearchFilter(SourceFilter.PinnedOnly) },
                                minHeight = 36.dp,
                                horizontalPadding = 14.dp,
                            )
                        }
                        item(key = "filter-all-sources") {
                            Chip(
                                label = "All sources",
                                selected = state.sourceFilter == SourceFilter.All,
                                onClick = { onChangeSearchFilter(SourceFilter.All) },
                                minHeight = 36.dp,
                                horizontalPadding = 14.dp,
                            )
                        }
                    } else {
                        item(key = "filter-all-sources") {
                            Chip(
                                label = "All sources",
                                selected = true,
                                onClick = { onChangeSearchFilter(SourceFilter.All) },
                                minHeight = 36.dp,
                                horizontalPadding = 14.dp,
                            )
                        }
                    }
                    item(key = "filter-has-results") {
                        Chip(
                            label = "Has results",
                            selected = state.onlyShowHasResults,
                            onClick = onToggleResults,
                            minHeight = 36.dp,
                            horizontalPadding = 14.dp,
                        )
                    }
                    item(key = "filter-in-library") {
                        Chip(
                            label = "In library",
                            selected = libraryFilter == GlobalLibraryFilter.IN_LIBRARY,
                            onClick = {
                                libraryFilter = if (libraryFilter == GlobalLibraryFilter.IN_LIBRARY) {
                                    GlobalLibraryFilter.ALL
                                } else {
                                    GlobalLibraryFilter.IN_LIBRARY
                                }
                            },
                            minHeight = 36.dp,
                            horizontalPadding = 14.dp,
                        )
                    }
                    item(key = "filter-not-in-library") {
                        Chip(
                            label = "Not in library",
                            selected = libraryFilter == GlobalLibraryFilter.NOT_IN_LIBRARY,
                            onClick = {
                                libraryFilter = if (libraryFilter == GlobalLibraryFilter.NOT_IN_LIBRARY) {
                                    GlobalLibraryFilter.ALL
                                } else {
                                    GlobalLibraryFilter.NOT_IN_LIBRARY
                                }
                            },
                            minHeight = 36.dp,
                            horizontalPadding = 14.dp,
                        )
                    }
                }
            }
        },
    ) { paddingValues ->
        GlobalSearchContent(
            allItems = state.items,
            items = state.filteredItems,
            contentPadding = paddingValues,
            libraryFilter = libraryFilter,
            searchQuery = state.searchQuery,
            onlyShowHasResults = state.onlyShowHasResults,
            hasPinnedSources = hasPinnedSources,
            sourceFilter = state.sourceFilter,
            onChangeSearchFilter = onChangeSearchFilter,
            onToggleResults = onToggleResults,
            onResetLibraryFilter = { libraryFilter = GlobalLibraryFilter.ALL },
            getManga = getManga,
            onClickSource = onClickSource,
            onRetrySource = onRetrySource,
            onClickItem = onClickItem,
            onLongClickItem = { manga ->
                if (fromSourceId == null && !bulkFavoriteState.selectionMode) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    bulkFavoriteScreenModel.toggleSelection(manga)
                } else {
                    onLongClickItem(manga)
                }
            },
            selection = bulkFavoriteState.selection,
            selectionMode = bulkFavoriteState.selectionMode,
            fromSourceId = fromSourceId,
        )
    }
}

@Composable
internal fun GlobalSearchContent(
    allItems: ImmutableMap<Source, SearchItemResult>,
    items: ImmutableMap<Source, SearchItemResult>,
    contentPadding: PaddingValues,
    libraryFilter: GlobalLibraryFilter,
    searchQuery: String?,
    onlyShowHasResults: Boolean,
    hasPinnedSources: Boolean,
    sourceFilter: SourceFilter,
    onChangeSearchFilter: (SourceFilter) -> Unit,
    onToggleResults: () -> Unit,
    onResetLibraryFilter: () -> Unit,
    getManga: @Composable (Manga) -> State<Manga>,
    onClickSource: (Source) -> Unit,
    onRetrySource: (Source) -> Unit,
    onClickItem: (Manga) -> Unit,
    onLongClickItem: (Manga) -> Unit,
    selection: List<Manga>,
    selectionMode: Boolean = selection.isNotEmpty(),
    fromSourceId: Long? = null,
) {
    val colors = TsuzukiTheme.colors
    val selectedIds = remember(selection) { selection.mapTo(HashSet()) { it.id } }
    val visibleCount = visibleGlobalSearchMangas(items, libraryFilter).size
    val emptyKind = resolveGlobalSearchEmptyKind(
        searchQuery = searchQuery,
        allItems = allItems,
        filteredItems = items,
        libraryFilter = libraryFilter,
        onlyShowHasResults = onlyShowHasResults,
        visibleResultCount = visibleCount,
    )

    LazyColumn(
        flingBehavior = tsuzukiFlingBehavior(),
        contentPadding = contentPadding,
    ) {
        when (emptyKind) {
            GlobalSearchEmptyKind.NO_QUERY -> {
                item(key = "global-search-empty") {
                    ShellEmpty(
                        title = "Search every source",
                        description = "Enter a title or MangaDex link to search across your sources.",
                        icon = TsuzukiIcons.Search,
                    )
                }
            }
            GlobalSearchEmptyKind.NO_SOURCES -> {
                item(key = "global-search-empty") {
                    ShellEmpty(
                        title = "No sources to search",
                        description = if (hasPinnedSources && sourceFilter == SourceFilter.PinnedOnly) {
                            "Switch to All sources or pin a source to search."
                        } else {
                            "Enable a source or add an extension to search."
                        },
                        icon = TsuzukiIcons.Search,
                    ) {
                        if (hasPinnedSources && sourceFilter == SourceFilter.PinnedOnly) {
                            PillButton("Search all sources", { onChangeSearchFilter(SourceFilter.All) }, prominent = false)
                        }
                    }
                }
            }
            GlobalSearchEmptyKind.SEARCHING_FILTERED -> {
                item(key = "global-search-loading") {
                    ShellLoading("Searching sources…")
                }
            }
            GlobalSearchEmptyKind.NO_RESULTS -> {
                item(key = "global-search-empty") {
                    ShellEmpty(
                        title = "No results",
                        description = "No sources matched \"${searchQuery.orEmpty()}\".",
                        icon = TsuzukiIcons.Search,
                    ) {
                        if (hasPinnedSources && sourceFilter == SourceFilter.PinnedOnly) {
                            PillButton("Search all sources", { onChangeSearchFilter(SourceFilter.All) }, prominent = false)
                        } else if (onlyShowHasResults) {
                            PillButton("Show all sources", onToggleResults, prominent = false)
                        }
                    }
                }
            }
            GlobalSearchEmptyKind.NO_LIBRARY_FILTER_MATCHES -> {
                item(key = "global-search-empty") {
                    ShellEmpty(
                        title = "No matching titles",
                        description = "No results match the current library filter.",
                        icon = TsuzukiIcons.Search,
                    ) {
                        PillButton("Show all results", onResetLibraryFilter, prominent = false)
                    }
                }
            }
            GlobalSearchEmptyKind.NONE -> {
                items.forEach { (source, result) ->
                    val sourceName = if (source.id == fromSourceId) "▶ ${source.name}" else source.name
                    item(key = "global-search-${source.id}") {
                        when (result) {
                            SearchItemResult.Loading -> {
                                Column(Modifier.padding(vertical = 8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        SourceIcon(
                                            source = tachiyomi.domain.source.model.Source(source.id, source.lang, source.name, supportsLatest = false, isStub = false),
                                            modifier = Modifier.size(20.dp),
                                        )
                                        TsuzukiText(
                                            sourceName,
                                            style = TsuzukiTheme.typography.callout.copy(fontWeight = FontWeight.Bold),
                                        )
                                        TsuzukiText(
                                            "searching…",
                                            style = TsuzukiTheme.typography.footnote,
                                            color = colors.secondary,
                                        )
                                    }
                                    CoverRowSkeleton()
                                }
                            }
                            is SearchItemResult.Success -> {
                                val sourceMangas = if (libraryFilter == GlobalLibraryFilter.ALL) {
                                    result.result
                                } else {
                                    result.result.map { manga -> key(manga.id) { getManga(manga).value } }
                                }
                                val filteredResults = filterGlobalSearchMangas(sourceMangas, libraryFilter)

                                if (filteredResults.isEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onClickSource(source) }
                                            .padding(horizontal = 20.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        SourceIcon(
                                            source = tachiyomi.domain.source.model.Source(source.id, source.lang, source.name, supportsLatest = false, isStub = false),
                                            modifier = Modifier.size(18.dp),
                                        )
                                        TsuzukiText(
                                            sourceName,
                                            style = TsuzukiTheme.typography.subhead.copy(fontWeight = FontWeight.SemiBold),
                                        )
                                        TsuzukiText(
                                            "no results",
                                            style = TsuzukiTheme.typography.footnote,
                                            color = colors.secondary,
                                        )
                                    }
                                } else {
                                    Column(Modifier.padding(vertical = 8.dp)) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 20.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                SourceIcon(
                                                    source = tachiyomi.domain.source.model.Source(source.id, source.lang, source.name, supportsLatest = false, isStub = false),
                                                    modifier = Modifier.size(20.dp),
                                                )
                                                TsuzukiText(
                                                    sourceName,
                                                    style = TsuzukiTheme.typography.callout.copy(fontWeight = FontWeight.Bold),
                                                )
                                                TsuzukiText(
                                                    "${filteredResults.size} results",
                                                    style = TsuzukiTheme.typography.footnote,
                                                    color = colors.secondary,
                                                )
                                            }
                                            TsuzukiText(
                                                "See all",
                                                style = TsuzukiTheme.typography.subhead.copy(fontWeight = FontWeight.SemiBold),
                                                color = colors.accent,
                                                modifier = Modifier.clickable { onClickSource(source) },
                                            )
                                        }

                                        LazyRow(
                                            flingBehavior = tsuzukiFlingBehavior(),
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(horizontal = 20.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            items(filteredResults, key = { it.id }) { manga ->
                                                val liveManga by getManga(manga)
                                                B4MangaCoverCard(
                                                    manga = liveManga,
                                                    selected = liveManga.id in selectedIds,
                                                    selectionMode = selectionMode,
                                                    onClick = { onClickItem(liveManga) },
                                                    onLongClick = { onLongClickItem(liveManga) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            is SearchItemResult.Error -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    SourceIcon(
                                        source = tachiyomi.domain.source.model.Source(source.id, source.lang, source.name, supportsLatest = false, isStub = false),
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Column(Modifier.weight(1f).clickable { onClickSource(source) }) {
                                        TsuzukiText(
                                            sourceName,
                                            style = TsuzukiTheme.typography.subhead.copy(fontWeight = FontWeight.SemiBold),
                                        )
                                        TsuzukiText(
                                            result.throwable.message?.takeIf { it.isNotBlank() } ?: "Search failed",
                                            style = TsuzukiTheme.typography.footnote,
                                            color = colors.secondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    PillButton("Retry", { onRetrySource(source) }, prominent = false, tinted = true)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun B4MangaCoverCard(
    manga: Manga,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(104.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CoverTile(
            cover = manga.asMangaCover(),
            title = manga.title,
            modifier = Modifier.width(104.dp).height(148.dp),
            shape = RoundedCornerShape(10.dp),
            fixedRatio = null,
            inLibraryCheck = manga.favorite,
            overlay = if (selectionMode) {
                {
                    SelectionMark(
                        selected = selected,
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                    )
                }
            } else {
                null
            },
        )
        TsuzukiText(
            manga.title,
            style = TsuzukiTheme.typography.caption1.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.heightIn(min = 32.dp),
        )
    }
}
