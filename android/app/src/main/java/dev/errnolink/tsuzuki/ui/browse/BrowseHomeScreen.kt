package dev.errnolink.tsuzuki.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.designsystem.Bookmark
import dev.errnolink.tsuzuki.designsystem.Check
import dev.errnolink.tsuzuki.designsystem.Chip
import dev.errnolink.tsuzuki.designsystem.ChevronRight
import dev.errnolink.tsuzuki.designsystem.MenuButton
import dev.errnolink.tsuzuki.designsystem.House
import dev.errnolink.tsuzuki.designsystem.IconButton
import dev.errnolink.tsuzuki.designsystem.Layers
import dev.errnolink.tsuzuki.designsystem.ListChecks
import dev.errnolink.tsuzuki.designsystem.Pin
import dev.errnolink.tsuzuki.designsystem.Plus
import dev.errnolink.tsuzuki.designsystem.Rss
import dev.errnolink.tsuzuki.designsystem.RefreshCw
import dev.errnolink.tsuzuki.designsystem.rememberMorphingSearchState
import dev.errnolink.tsuzuki.designsystem.TopBarSearch
import dev.errnolink.tsuzuki.designsystem.Search
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.SectionHeader
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiSize
import dev.errnolink.tsuzuki.ui.shell.CoverTile
import dev.errnolink.tsuzuki.ui.shell.ShellError
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.mangadex.MangaDexDestination
import dev.errnolink.tsuzuki.mangadex.openMangaDexDestination
import dev.errnolink.tsuzuki.ui.shell.ShellList
import dev.errnolink.tsuzuki.ui.shell.ShellRefresh
import dev.errnolink.tsuzuki.ui.shell.ShellTopAction
import dev.errnolink.tsuzuki.ui.shell.ShellCover
import eu.kanade.presentation.browse.components.SourceIcon
import dev.errnolink.tsuzuki.designsystem.EmptyState
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.ui.shell.CoverRowSkeleton
import dev.errnolink.tsuzuki.ui.shell.FeedSectionSkeleton
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreenModel.Listing
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import dev.errnolink.tsuzuki.ui.motion.openTitle
import eu.kanade.tachiyomi.util.system.LocaleHelper
import exh.source.MANGADEX_IDS
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.source.model.Source as DomainSource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

internal fun browseShelfCardWidthDp(maxWidthDp: Float, spacingDp: Float = 12f): Float =
    ((maxWidthDp - 20f) / 2.2f - spacingDp).coerceIn(112f, 180f)

internal fun browseListColumnWidthDp(maxWidthDp: Float, spacingDp: Float = 12f): Float =
    ((maxWidthDp - 20f) / 1.45f - spacingDp).coerceIn(200f, 320f)

@Composable
fun BrowseHomeScreen(tab: Tab) {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = tab.rememberScreenModel { BrowseHomeScreenModel() }
    val state by screenModel.state.collectAsState()
    val feedScreenModel = tab.rememberScreenModel { eu.kanade.tachiyomi.ui.browse.feed.FeedScreenModel() }
    val feedState by feedScreenModel.state.collectAsState()

    var showSourcesSheet by remember { mutableStateOf(false) }
    val chipsState = rememberLazyListState()

    // N14: every tab root carries the tab bar's search circle (the collapsed bar keeps two
    // controls). Browse's field submits into Global search (round-10 pattern).
    var browseQuery by rememberSaveable { mutableStateOf("") }
    val browseSearchState = rememberMorphingSearchState()
    val browseSearch = TopBarSearch(
        state = browseSearchState,
        query = browseQuery,
        onQueryChange = { browseQuery = it },
        placeholder = "Search every source",
        onSearch = {
            if (browseQuery.isNotBlank()) {
                navigator.push(GlobalSearchScreen(browseQuery))
                // Kasane clears the field on search: the pushed page owns the query now.
                browseSearchState.collapse()
                browseQuery = ""
            }
        },
        onClose = { browseQuery = "" },
    )
    // BRW-01: the inline field is gone; reselecting the Browse tab is the keyboard path to the
    // bottom search field.
    LaunchedEffect(tab.key) {
        HomeScreen.reselectEvents.collect { if (it == tab.key) browseSearch.state.expand() }
    }

    ShellRefresh(
        refreshing = state.isRefreshing,
        enabled = state.selectedSourceId != null && !state.isLoadingSources,
        onRefresh = screenModel::refreshShelves,
    ) {
    ShellList(
        title = "Browse",
        itemSpacing = 0.dp,
        capContent = false,
        search = browseSearch,
        actions = listOf(ShellTopAction("Sources", TsuzukiIcons.Layers, { showSourcesSheet = true })),
    ) {
        item(key = "source-chips") {
            LazyRow(
                state = chipsState,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                flingBehavior = dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior(),
            ) {
                items(state.pinnedSources, key = { it.id }) { src ->
                    val isSelected = src.id == state.selectedSourceId
                    BrowseSourceChip(
                        source = src,
                        selected = isSelected,
                        onClick = { screenModel.selectSource(src.id) },
                    )
                }
                item(key = "all-sources-chip") {
                    BrowseAllSourcesButton(onClick = { showSourcesSheet = true })
                }
            }
        }

        val selectedSource = state.pinnedSources.firstOrNull { it.id == state.selectedSourceId }
            ?: state.allSources.firstOrNull { it.source.id == state.selectedSourceId }?.source
        val isMangaDex = selectedSource != null && (selectedSource.id in MANGADEX_IDS || selectedSource.name.contains("MangaDex", ignoreCase = true))
        if (isMangaDex) {
            item(key = "mangadex-section") {
                BrowseMangaDexTiles(
                    onOpenHome = { openMangaDexDestination(navigator, MangaDexDestination.Home) },
                    onOpenFeed = { openMangaDexDestination(navigator, MangaDexDestination.Feed) },
                    onOpenLists = { openMangaDexDestination(navigator, MangaDexDestination.Lists) },
                )
            }
        }

        if (state.isLoadingPopular) {
            item(key = "popular-skeleton") {
                FeedSectionSkeleton()
            }
        } else if (state.popularManga.isNotEmpty()) {
            item(key = "popular-shelf") {
                BrowseShelf(
                    title = if (isMangaDex) "Popular new titles" else "Popular",
                    onSeeAll = {
                        if (isMangaDex) {
                            openMangaDexDestination(navigator, MangaDexDestination.Home)
                        } else {
                            selectedSource?.let {
                                navigator.push(BrowseSourceScreen(it.id, Listing.Popular.query))
                            }
                        }
                    },
                    shelfKey = state.selectedSourceId,
                    items = state.popularManga,
                    onClick = { openTitle(navigator, it.id, fromSource = true) },
                )
            }
        }

        if (state.isLoadingRecent) {
            item(key = "recent-skeleton") {
                FeedSectionSkeleton()
            }
        } else if (state.recentManga.isNotEmpty()) {
            item(key = "recent-shelf") {
                BrowseListShelf(
                    title = "Recently updated",
                    onSeeAll = {
                        selectedSource?.let {
                            navigator.push(BrowseSourceScreen(it.id, Listing.Latest.query))
                        }
                    },
                    shelfKey = state.selectedSourceId,
                    items = state.recentManga,
                    onClick = { openTitle(navigator, it.id, fromSource = true) },
                )
            }
        }

        if (state.loadError && !state.isLoadingPopular && !state.isLoadingRecent && state.popularManga.isEmpty() && state.recentManga.isEmpty()) {
            item(key = "browse-error") {
                ShellError(
                    title = "Could not load titles",
                    description = "Check your connection and try again.",
                    onRetry = { screenModel.retryShelves() },
                )
            }
        } else if (!state.isLoadingSources && !state.isLoadingPopular && !state.isLoadingRecent && state.popularManga.isEmpty() && state.recentManga.isEmpty() && !isMangaDex) {
            item(key = "browse-empty") {
                if (selectedSource == null) {
                    EmptyState(
                        title = "No sources to browse",
                        description = "Add a source, or trust one you already installed, to see titles here.",
                        icon = { Icon(TsuzukiIcons.Search, null, Modifier.size(36.dp), tint = TsuzukiTheme.colors.secondary) },
                        action = { PillButton("Add sources", { navigator.push(AddSourcesScreen()) }) },
                    )
                } else {
                    EmptyState(
                        title = "No titles available",
                        description = "${selectedSource.name} did not return any titles.",
                        icon = { Icon(TsuzukiIcons.Search, null, Modifier.size(36.dp), tint = TsuzukiTheme.colors.secondary) },
                        action = { PillButton("Retry", { screenModel.retryShelves() }) },
                    )
                }
            }
        }

        val feedItems = feedState.items.orEmpty()
        if (feedItems.isNotEmpty()) {
            item(key = "saved-searches-header") {
                BrowseSectionHeader(
                    title = "Saved searches",
                    onSeeAll = {
                        showSourcesSheet = true
                    },
                )
            }
            items(feedItems, key = { "feed-${it.feed.id}" }) { feedItem ->
                Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Row(
                        Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            TsuzukiIcons.Bookmark,
                            null,
                            Modifier.size(14.dp),
                            tint = TsuzukiTheme.colors.secondary,
                        )
                        TsuzukiText(
                            feedItem.title,
                            style = TsuzukiTheme.typography.subhead.copy(fontWeight = FontWeight.SemiBold),
                        )
                    }
                    val results = feedItem.results.orEmpty()
                    if (results.isNotEmpty()) {
                        BrowseShelfRow(
                            shelfKey = "${feedItem.feed.id}:${state.selectedSourceId}",
                            items = results,
                            onClick = { openTitle(navigator, it.id, fromSource = true) },
                        )
                    }
                }
            }
        }

        if (state.allSources.isNotEmpty()) {
            item(key = "sources-card-header") {
                BrowseSectionHeader(title = "Sources")
            }
            item(key = "sources-card") {
                BrowseInlineSourcesCard(
                    sources = state.allSources,
                    updatesCount = state.extensionUpdatesCount,
                    onClickSource = { src ->
                        navigator.push(BrowseSourceScreen(src.id, Listing.Popular.query))
                    },
                    onClickPin = { screenModel.togglePin(it) },
                    onAddSources = {
                        navigator.push(AddSourcesScreen())
                    },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
        }

        item(key = "bottom-spacer") {
            Spacer(Modifier.height(16.dp))
        }
    }
    }

    if (showSourcesSheet) {
        SourcesSheet(
            visible = showSourcesSheet,
            sources = state.allSources,
            selectedSourceId = state.selectedSourceId,
            onDismiss = { showSourcesSheet = false },
            onSelectSource = { src ->
                screenModel.selectSource(src.id)
                showSourcesSheet = false
            },
            onTogglePin = { screenModel.togglePin(it) },
            onToggleDisable = { screenModel.toggleDisable(it) },
            onUpdateExtension = { screenModel.updateExtension(it) },
            onUninstallExtension = { screenModel.uninstallExtension(it) },
            onAddSources = {
                showSourcesSheet = false
                navigator.push(AddSourcesScreen())
            },
        )
    }
}

@Composable
private fun BrowseSourceChip(
    source: DomainSource,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.heightIn(min = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Chip(
            label = source.visualName,
            selected = selected,
            onClick = onClick,
            leading = {
                SourceIcon(source = source, modifier = Modifier.size(16.dp))
            },
        )
    }
}

@Composable
private fun BrowseAllSourcesButton(
    onClick: () -> Unit,
) {
    MenuButton(label = "All sources", onClick = onClick)
}


@Composable
private fun BrowseSectionHeader(title: String, onSeeAll: (() -> Unit)? = null) {
    SectionHeader(
        title,
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = onSeeAll != null) { onSeeAll?.invoke() }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        titleStyle = TsuzukiTheme.typography.footnote.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
        onAction = onSeeAll,
        actionChevron = onSeeAll != null,
    )
}

@Composable
private fun BrowseShelf(
    title: String,
    onSeeAll: (() -> Unit)?,
    shelfKey: Any?,
    items: List<Manga>,
    onClick: (Manga) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        BrowseSectionHeader(title, onSeeAll)
        BrowseShelfRow(
            shelfKey = shelfKey,
            items = items,
            onClick = onClick,
        )
    }
}

@Composable
private fun BrowseListShelf(
    title: String,
    onSeeAll: (() -> Unit)?,
    shelfKey: Any?,
    items: List<Manga>,
    onClick: (Manga) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        BrowseSectionHeader(title, onSeeAll)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val spacing = 12.dp
            val columnWidth = browseListColumnWidthDp(maxWidth.value, spacing.value).dp
            val state = remember(shelfKey) { LazyListState() }
            LazyRow(
                state = state,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                flingBehavior = rememberSnapFlingBehavior(lazyListState = state),
            ) {
                items(items.chunked(4), key = { it.first().id }) { column ->
                    Column(
                        modifier = Modifier.width(columnWidth),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        column.forEach { manga ->
                            BrowseListShelfRow(manga, onClick)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseListShelfRow(
    manga: Manga,
    onClick: (Manga) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 66.dp)
            .clickable { onClick(manga) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CoverTile(
            cover = manga.asMangaCover(),
            title = manga.title,
            modifier = Modifier.width(44.dp).height(66.dp),
            shape = RoundedCornerShape(8.dp),
        )
        TsuzukiText(
            manga.title,
            style = TsuzukiTheme.typography.subhead.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BrowseShelfRow(
    shelfKey: Any?,
    items: List<Manga>,
    onClick: (Manga) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val spacing = 12.dp
        val cardWidth = browseShelfCardWidthDp(maxWidth.value, spacing.value).dp
        val state = remember(shelfKey) { LazyListState() }
        LazyRow(
            state = state,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            flingBehavior = rememberSnapFlingBehavior(lazyListState = state),
        ) {
            items(items, key = { it.id }) { manga ->
                BrowseShelfCard(
                    manga = manga,
                    width = cardWidth,
                    onClick = { onClick(manga) },
                )
            }
        }
    }
}

@Composable
private fun BrowseShelfCard(
    manga: Manga,
    width: Dp,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(width)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CoverTile(
            cover = manga.asMangaCover(),
            title = manga.title,
            modifier = Modifier.fillMaxWidth().height(width * 1.5f),
            shape = RoundedCornerShape(10.dp),
            inLibraryCheck = manga.favorite,
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

@Composable
private fun BrowseMangaDexTiles(
    onOpenHome: () -> Unit,
    onOpenFeed: () -> Unit,
    onOpenLists: () -> Unit,
) {
    val colors = TsuzukiTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BrowseTile(
            title = "Home",
            icon = TsuzukiIcons.House,
            onClick = onOpenHome,
            modifier = Modifier.weight(1f),
        )
        BrowseTile(
            title = "Follows feed",
            icon = TsuzukiIcons.Rss,
            onClick = onOpenFeed,
            modifier = Modifier.weight(1f),
        )
        BrowseTile(
            title = "My lists",
            icon = TsuzukiIcons.ListChecks,
            onClick = onOpenLists,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BrowseTile(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TsuzukiTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(TsuzukiCorners.field))
            .background(colors.grouped)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = colors.accent)
        TsuzukiText(
            title,
            style = TsuzukiTheme.typography.caption1.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BrowseInlineSourcesCard(
    sources: List<BrowseSourceItem>,
    updatesCount: Int,
    onClickSource: (DomainSource) -> Unit,
    onClickPin: (DomainSource) -> Unit,
    onAddSources: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TsuzukiTheme.colors
    val context = LocalContext.current
    val sorted = remember(sources) {
        sources.sortedByDescending { it.isPinned }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.grouped),
    ) {
        sorted.forEachIndexed { index, item ->
            val src = item.source
            SourceRow(
                title = src.visualName,
                onClick = { onClickSource(src) },
                titleSemibold = true,
                subtitle = LocaleHelper.getSourceDisplayName(src.lang, context),
                leading = { SourceIcon(source = src, modifier = Modifier.size(32.dp)) },
                titleAccessory = { if (item.hasUpdate) BrowseUpdateTag() },
                trailing = {
                    Box(
                        modifier = Modifier
                            .size(TsuzukiSize.touch)
                            .clickable { onClickPin(src) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(TsuzukiSize.iconWell)
                                .clip(CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                TsuzukiIcons.Pin,
                                stringResource(if (item.isPinned) MR.strings.action_unpin else MR.strings.action_pin),
                                Modifier.size(18.dp),
                                tint = if (item.isPinned) colors.accent else colors.secondary.copy(alpha = 0.5f),
                            )
                        }
                    }
                },
            )
            if (index < sorted.lastIndex) {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 58.dp)
                        .height(0.5.dp)
                        .background(colors.separator),
                )
            }
        }

        Spacer(Modifier.fillMaxWidth().height(0.5.dp).background(colors.separator))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onAddSources)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(TsuzukiIcons.Plus, null, Modifier.size(16.dp), tint = colors.accent)
            }
            Column(Modifier.weight(1f)) {
                TsuzukiText(
                    "Add sources",
                    style = TsuzukiTheme.typography.callout.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.accent,
                )
                val installedCount = sources.size
                val updateSub = if (updatesCount > 0) " · $updatesCount updates" else ""
                TsuzukiText(
                    "$installedCount installed$updateSub",
                    style = TsuzukiTheme.typography.footnote,
                    color = colors.secondary,
                )
            }
        }
    }
}

@Composable
internal fun BrowseUpdateTag() {
    val tint = if (TsuzukiTheme.colors.isDark) Color(0xFFFFC94A) else Color(0xFF8A5A00)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFFFC94A).copy(alpha = 0.2f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        TsuzukiText(
            "UPDATE",
            style = TsuzukiTheme.typography.caption2.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
            color = tint,
        )
    }
}
