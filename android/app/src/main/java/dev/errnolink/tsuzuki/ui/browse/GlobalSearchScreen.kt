package dev.errnolink.tsuzuki.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.Check
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.rememberTopBarSearch
import dev.errnolink.tsuzuki.ui.shell.CoverRowSkeleton
import dev.errnolink.tsuzuki.ui.shell.ShellAction
import dev.errnolink.tsuzuki.ui.shell.ShellCaption
import dev.errnolink.tsuzuki.ui.shell.ShellContentScaffold
import dev.errnolink.tsuzuki.ui.shell.ShellCover
import dev.errnolink.tsuzuki.ui.shell.ShellTopAction
import eu.kanade.presentation.browse.components.SourceIcon
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.browse.BulkFavoriteScreenModel
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchItemResult
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SearchScreenModel
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SourceFilter
import kotlinx.collections.immutable.ImmutableMap
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover

enum class GlobalLibraryFilter {
    ALL,
    IN_LIBRARY,
    NOT_IN_LIBRARY,
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
    onClickItem: (Manga) -> Unit,
    onLongClickItem: (Manga) -> Unit,
    bulkFavoriteScreenModel: BulkFavoriteScreenModel,
    hasPinnedSources: Boolean,
) {
    val bulkFavoriteState by bulkFavoriteScreenModel.state.collectAsState()

    var libraryFilter by remember { mutableStateOf(GlobalLibraryFilter.ALL) }

    val search = if (bulkFavoriteState.selectionMode) {
        null
    } else {
        rememberTopBarSearch(
            query = state.searchQuery.orEmpty(),
            onQueryChange = onChangeSearchQuery,
            placeholder = "Title or MangaDex link",
            onSearch = { onSearch(state.searchQuery.orEmpty()) },
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
        navigateUp = navigateUp,
        actions = if (bulkFavoriteState.selectionMode) {
            listOf(
                ShellTopAction(
                    "Select all",
                    Icons.Outlined.SelectAll,
                    {
                        state.filteredItems.values.filterIsInstance<SearchItemResult.Success>()
                            .flatMap { it.result }.forEach(bulkFavoriteScreenModel::select)
                    },
                ),
                ShellTopAction(
                    "Invert selection",
                    Icons.Outlined.FlipToBack,
                    {
                        bulkFavoriteScreenModel.reverseSelection(
                            state.filteredItems.values.filterIsInstance<SearchItemResult.Success>().flatMap { it.result },
                        )
                    },
                ),
                ShellTopAction("Done", Icons.Outlined.Close, bulkFavoriteScreenModel::toggleSelectionMode),
            )
        } else {
            listOf(ShellTopAction("Select manga", Icons.Outlined.Checklist, bulkFavoriteScreenModel::toggleSelectionMode))
        },
        search = search,
        header = {
            if (bulkFavoriteState.selectionMode) {
                PillButton("Add to library", bulkFavoriteScreenModel::addFavorite, Modifier.padding(horizontal = 20.dp))
            } else {
                // Chips: All sources / In library / Not in library
                LazyRow(
                    flingBehavior = dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        val selected = libraryFilter == GlobalLibraryFilter.ALL
                        val colors = TsuzukiTheme.colors
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (selected) colors.text else colors.grouped)
                                .clickable { libraryFilter = GlobalLibraryFilter.ALL }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            TsuzukiText(
                                "All sources",
                                style = TsuzukiTheme.typography.subhead.copy(fontWeight = FontWeight.SemiBold),
                                color = if (selected) colors.background else colors.secondary,
                            )
                        }
                    }
                    item {
                        val selected = libraryFilter == GlobalLibraryFilter.IN_LIBRARY
                        val colors = TsuzukiTheme.colors
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (selected) colors.text else colors.grouped)
                                .clickable { libraryFilter = GlobalLibraryFilter.IN_LIBRARY }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            TsuzukiText(
                                "In library",
                                style = TsuzukiTheme.typography.subhead.copy(fontWeight = FontWeight.SemiBold),
                                color = if (selected) colors.background else colors.secondary,
                            )
                        }
                    }
                    item {
                        val selected = libraryFilter == GlobalLibraryFilter.NOT_IN_LIBRARY
                        val colors = TsuzukiTheme.colors
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (selected) colors.text else colors.grouped)
                                .clickable { libraryFilter = GlobalLibraryFilter.NOT_IN_LIBRARY }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            TsuzukiText(
                                "Not in library",
                                style = TsuzukiTheme.typography.subhead.copy(fontWeight = FontWeight.SemiBold),
                                color = if (selected) colors.background else colors.secondary,
                            )
                        }
                    }
                }
            }
        },
    ) { paddingValues ->
        GlobalSearchContent(
            items = state.filteredItems,
            contentPadding = paddingValues,
            libraryFilter = libraryFilter,
            getManga = getManga,
            onClickSource = onClickSource,
            onClickItem = onClickItem,
            onLongClickItem = onLongClickItem,
            selection = bulkFavoriteState.selection,
            fromSourceId = fromSourceId,
        )
    }
}

@Composable
internal fun GlobalSearchContent(
    items: ImmutableMap<Source, SearchItemResult>,
    contentPadding: PaddingValues,
    libraryFilter: GlobalLibraryFilter,
    getManga: @Composable (Manga) -> State<Manga>,
    onClickSource: (Source) -> Unit,
    onClickItem: (Manga) -> Unit,
    onLongClickItem: (Manga) -> Unit,
    selection: List<Manga>,
    fromSourceId: Long? = null,
) {
    val colors = TsuzukiTheme.colors
    LazyColumn(
        flingBehavior = dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior(),
        contentPadding = contentPadding,
    ) {
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
                                SourceIcon(source = tachiyomi.domain.source.model.Source(source.id, source.lang, source.name, supportsLatest = false, isStub = false), modifier = Modifier.size(20.dp))
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
                        val filteredResults = when (libraryFilter) {
                            GlobalLibraryFilter.ALL -> result.result
                            GlobalLibraryFilter.IN_LIBRARY -> result.result.filter { it.favorite }
                            GlobalLibraryFilter.NOT_IN_LIBRARY -> result.result.filter { !it.favorite }
                        }

                        if (result.result.isEmpty() || filteredResults.isEmpty()) {
                            // Collapsed single line row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onClickSource(source) }
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                SourceIcon(source = tachiyomi.domain.source.model.Source(source.id, source.lang, source.name, supportsLatest = false, isStub = false), modifier = Modifier.size(18.dp))
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
                            // Section header + results row
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
                                        SourceIcon(source = tachiyomi.domain.source.model.Source(source.id, source.lang, source.name, supportsLatest = false, isStub = false), modifier = Modifier.size(20.dp))
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
                                    flingBehavior = dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior(),
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    items(filteredResults, key = { it.id }) { manga ->
                                        B4MangaCoverCard(
                                            manga = manga,
                                            onClick = { onClickItem(manga) },
                                            onLongClick = { onLongClickItem(manga) },
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
                                .clickable { onClickSource(source) }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SourceIcon(source = tachiyomi.domain.source.model.Source(source.id, source.lang, source.name, supportsLatest = false, isStub = false), modifier = Modifier.size(18.dp))
                            TsuzukiText(
                                sourceName,
                                style = TsuzukiTheme.typography.subhead.copy(fontWeight = FontWeight.SemiBold),
                            )
                            TsuzukiText(
                                "error",
                                style = TsuzukiTheme.typography.footnote,
                                color = colors.destructive,
                            )
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
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(104.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .width(104.dp)
                .height(148.dp)
                .clip(RoundedCornerShape(10.dp)),
        ) {
            ShellCover(
                cover = manga.asMangaCover(),
                title = manga.title,
                modifier = Modifier.fillMaxSize(),
            )
            if (manga.favorite) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.78f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        TsuzukiIcons.Check,
                        null,
                        Modifier.size(12.dp),
                        tint = Color(0xFF7EE0A5),
                    )
                }
            }
        }
        TsuzukiText(
            manga.title,
            style = TsuzukiTheme.typography.caption1.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.heightIn(min = 32.dp),
        )
    }
}
