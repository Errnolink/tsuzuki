package dev.errnolink.tsuzuki.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import dev.errnolink.tsuzuki.designsystem.EmptyTextAction
import dev.errnolink.tsuzuki.designsystem.Heart
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme

import dev.errnolink.tsuzuki.ui.shell.MangaCellSkeleton
import dev.errnolink.tsuzuki.ui.shell.ShellCover
import dev.errnolink.tsuzuki.ui.shell.ShellEmpty
import dev.errnolink.tsuzuki.ui.shell.ShellList
import dev.errnolink.tsuzuki.ui.shell.ShellMangaTile
import dev.errnolink.tsuzuki.ui.shell.ShellRefresh
import dev.errnolink.tsuzuki.ui.shell.SourceAppendSkeleton
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.source.Source
import exh.metadata.metadata.RaisedSearchMetadata
import tachiyomi.data.source.NoResultsException
import tachiyomi.domain.storage.service.StorageManager

import kotlinx.coroutines.flow.StateFlow
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.source.model.StubSource
import tachiyomi.source.local.LocalSource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.core.common.storage.displayablePath

@Composable
fun BrowseSourceContent(
    source: Source?,
    mangaList: LazyPagingItems<StateFlow<Pair<Manga, RaisedSearchMetadata?>>>,
    columns: GridCells,

    displayMode: LibraryDisplayMode,
    snackbarHostState: SnackbarHostState,
    contentPadding: PaddingValues,
    onWebViewClick: (() -> Unit)?,
    onHelpClick: (() -> Unit)?,
    onLocalSourceHelpClick: (() -> Unit)?,
    onMangaClick: (Manga) -> Unit,
    onMangaLongClick: (Manga) -> Unit,
    selection: List<Manga>,
) {
    val context = LocalContext.current
    val selectedIds = remember(selection) { selection.mapTo(HashSet()) { it.id } }
    val error = (mangaList.loadState.refresh as? LoadState.Error) ?: (mangaList.loadState.append as? LoadState.Error)
    LaunchedEffect(error) {
        if (error != null && mangaList.itemCount > 0) {
            val result = snackbarHostState.showSnackbar(
                message = with(context) { error.error.formattedMessage },
                actionLabel = context.stringResource(MR.strings.action_retry),
                duration = SnackbarDuration.Indefinite,
            )
            if (result == SnackbarResult.ActionPerformed) mangaList.retry()
        }
    }

    val cells = remember(columns, displayMode) {
        if (displayMode == LibraryDisplayMode.List) StaggeredGridCells.Fixed(1)
        else object : StaggeredGridCells {
            override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): IntArray =
                with(columns) { calculateCrossAxisCellSizes(availableSize, spacing) }.toIntArray()
        }
    }
    ShellRefresh(mangaList.loadState.refresh is LoadState.Loading, selection.isEmpty(), mangaList::refresh) {
        LazyVerticalStaggeredGrid(
            flingBehavior = dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior(),
            columns = cells,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalItemSpacing = 20.dp,
        ) {
            if (mangaList.itemCount == 0) {
                if (mangaList.loadState.refresh is LoadState.Loading) {
                    items(
                        count = 12,
                        key = { "browse-skeleton-$it" },
                        contentType = { "manga-skeleton" },
                    ) { index ->
                        MangaCellSkeleton(displayMode, index)
                    }
                } else {
                    item("source-status", span = StaggeredGridItemSpan.FullLine) {
                        if (source is LocalSource) {
                            val localDir = remember { Injekt.get<StorageManager>().getLocalSourceDirectory() }
                            ShellEmpty(
                                "Your local library is empty",
                                "Create one folder per manga inside ${localDir?.displayablePath ?: "the local source folder"} and put your chapter files or archives in it.",
                            ) {
                                onLocalSourceHelpClick?.let { PillButton("Open the guide", it, prominent = false) }
                            }
                        } else {
                            val failed = error?.takeIf { it.error !is NoResultsException }
                            ShellEmpty(
                                if (failed != null) "Couldn't load this page" else "No results",
                                failed?.let { with(context) { it.error.formattedMessage } } ?: "Try another search or catalog filter.",
                                action = { PillButton("Retry", mangaList::refresh, prominent = false) },
                                secondaryActions = buildList {
                                    onWebViewClick?.let { add(EmptyTextAction("Open website", it)) }
                                    onHelpClick?.let { add(EmptyTextAction("Help", it)) }
                                },
                            )
                        }
                    }
                }
            }
            items(
                count = mangaList.itemCount,
                key = { index -> mangaList.peek(index)?.value?.first?.id ?: "source-placeholder-$index" },
                contentType = { "manga" },
            ) { index ->
                val entry = mangaList[index] ?: return@items
                val pair by entry.collectAsState()
                val manga = pair.first
                val selected = manga.id in selectedIds
                val colors = TsuzukiTheme.colors
                Column(Modifier.combinedClickable(onClick = { onMangaClick(manga) }, onLongClick = { onMangaLongClick(manga) })) {
                    when (displayMode) {
                        LibraryDisplayMode.List -> Row(
                            Modifier.clip(RoundedCornerShape(14.dp))
                                .background(if (selected) colors.selectedFill else colors.grouped, RoundedCornerShape(14.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ShellCover(manga.asMangaCover(), manga.title, Modifier.width(48.dp), fixedRatio = 2f / 3f)
                            TsuzukiText(manga.title, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        LibraryDisplayMode.CoverOnlyGrid -> Box {
                            ShellCover(manga.asMangaCover(), manga.title)
                            if (manga.favorite) {
                                Box(
                                    Modifier.align(Alignment.TopEnd).padding(7.dp).size(24.dp)
                                        .background(colors.elevated, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(TsuzukiIcons.Heart, "In library", Modifier.size(14.dp), tint = colors.accent)
                                }
                            }
                            if (selected) {
                                Box(
                                    Modifier.matchParentSize().background(colors.accent.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        Modifier.size(26.dp).background(colors.accent, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(Icons.Outlined.Check, "Selected", Modifier.size(17.dp), tint = colors.onAccent)
                                    }
                                }
                            }
                        }
                        else -> ShellMangaTile(manga, selected)
                    }
                }
            }
            if (mangaList.loadState.append is LoadState.Loading) {
                item("source-more", span = StaggeredGridItemSpan.FullLine) { SourceAppendSkeleton() }
            }
        }
    }
}

@Composable
internal fun MissingSourceScreen(source: StubSource, navigateUp: () -> Unit) {
    ShellList(source.name, navigateUp = navigateUp) {
        item("missing-source") {
            ShellEmpty("Source unavailable", stringResource(MR.strings.source_not_installed, source.toString()))
        }
    }
}
