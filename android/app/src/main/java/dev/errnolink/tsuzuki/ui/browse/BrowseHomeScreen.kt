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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import dev.errnolink.tsuzuki.designsystem.ChevronDown
import dev.errnolink.tsuzuki.designsystem.ChevronRight
import dev.errnolink.tsuzuki.designsystem.House
import dev.errnolink.tsuzuki.designsystem.IconButton
import dev.errnolink.tsuzuki.designsystem.Layers
import dev.errnolink.tsuzuki.designsystem.ListChecks
import dev.errnolink.tsuzuki.designsystem.Pin
import dev.errnolink.tsuzuki.designsystem.Plus
import dev.errnolink.tsuzuki.designsystem.Rss
import dev.errnolink.tsuzuki.designsystem.RefreshCw
import dev.errnolink.tsuzuki.designsystem.Search
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.mangadex.MangaDexDestination
import dev.errnolink.tsuzuki.mangadex.openMangaDexDestination
import dev.errnolink.tsuzuki.ui.shell.ShellList
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
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.util.system.LocaleHelper
import exh.source.MANGADEX_IDS
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.source.model.Source as DomainSource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun BrowseHomeScreen(tab: Tab, discoverOnly: Boolean) {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = tab.rememberScreenModel { BrowseHomeScreenModel() }
    val state by screenModel.state.collectAsState()
    val feedScreenModel = tab.rememberScreenModel { eu.kanade.tachiyomi.ui.browse.feed.FeedScreenModel() }
    val feedState by feedScreenModel.state.collectAsState()

    var showSourcesSheet by remember { mutableStateOf(false) }

    ShellList(
        title = "Browse",
        itemSpacing = 0.dp,
        actions = listOf(ShellTopAction("Sources", TsuzukiIcons.Layers, { showSourcesSheet = true })),
    ) {
        item(key = "search-bar") {
            val sourceNames = remember(state.pinnedSources) {
                if (state.pinnedSources.isEmpty()) {
                    "every source"
                } else {
                    state.pinnedSources.take(3).joinToString(", ") { it.name }
                }
            }
            BrowseFakeSearchBar(
                placeholder = "Search $sourceNames",
                onClick = { navigator.push(GlobalSearchScreen()) },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }

        item(key = "source-chips") {
            val chipsState = remember(state.pinnedSources) { LazyListState() }
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
                    BrowseAllSourcesChip(onClick = { showSourcesSheet = true })
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
                    onClick = { navigator.push(MangaScreen(it.id, true)) },
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
                    onClick = { navigator.push(MangaScreen(it.id, true)) },
                )
            }
        }

        if (state.loadError && !state.isLoadingPopular && !state.isLoadingRecent && state.popularManga.isEmpty() && state.recentManga.isEmpty()) {
            item(key = "browse-error") {
                EmptyState(
                    title = "Could not load titles",
                    description = "Check your connection and try again.",
                    icon = { Icon(TsuzukiIcons.RefreshCw, null, Modifier.size(36.dp), tint = TsuzukiTheme.colors.secondary) },
                    action = {
                        PillButton("Retry", { screenModel.retryShelves() })
                    },
                )
            }
        } else if (!state.isLoadingPopular && !state.isLoadingRecent && state.popularManga.isEmpty() && state.recentManga.isEmpty() && !isMangaDex) {
            item(key = "browse-empty") {
                EmptyState(
                    title = "No titles available",
                    description = "This source did not return any titles.",
                    icon = { Icon(TsuzukiIcons.Search, null, Modifier.size(36.dp), tint = TsuzukiTheme.colors.secondary) },
                )
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
                            onClick = { navigator.push(MangaScreen(it.id, true)) },
                        )
                    }
                }
            }
        }

        if (!discoverOnly && state.allSources.isNotEmpty()) {
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
private fun BrowseFakeSearchBar(
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TsuzukiTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.grouped)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            TsuzukiIcons.Search,
            null,
            Modifier.size(18.dp),
            tint = colors.secondary,
        )
        TsuzukiText(
            placeholder,
            style = TsuzukiTheme.typography.body,
            color = colors.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BrowseSourceChip(
    source: DomainSource,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = TsuzukiTheme.colors
    val bg = if (selected) colors.text else colors.grouped
    val fg = if (selected) colors.background else colors.secondary

    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(bg)
                .heightIn(min = 40.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SourceIcon(source = source, modifier = Modifier.size(16.dp))
            TsuzukiText(
                source.visualName,
                style = TsuzukiTheme.typography.subhead.copy(fontWeight = FontWeight.SemiBold),
                color = fg,
            )
        }
    }
}

@Composable
private fun BrowseAllSourcesChip(
    onClick: () -> Unit,
) {
    val colors = TsuzukiTheme.colors
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(colors.grouped)
                .heightIn(min = 40.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TsuzukiText(
                "All sources",
                style = TsuzukiTheme.typography.subhead.copy(fontWeight = FontWeight.SemiBold),
                color = colors.secondary,
            )
            Icon(
                TsuzukiIcons.ChevronDown,
                null,
                Modifier.size(14.dp),
                tint = colors.secondary,
            )
        }
    }
}

@Composable
private fun BrowseSectionHeader(
    title: String,
    onSeeAll: (() -> Unit)? = null,
) {
    val colors = TsuzukiTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = onSeeAll != null) { onSeeAll?.invoke() }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TsuzukiText(
            title,
            style = TsuzukiTheme.typography.footnote.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
            color = colors.secondary,
        )
        if (onSeeAll != null) {
            Icon(
                TsuzukiIcons.ChevronRight,
                null,
                Modifier.size(14.dp),
                tint = colors.secondary,
            )
        }
    }
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
            val columnWidth = (maxWidth - 20.dp) / 1.45f - spacing
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
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(66.dp)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            ShellCover(
                cover = manga.asMangaCover(),
                title = manga.title,
                fixedRatio = 2f / 3f,
                modifier = Modifier.fillMaxSize(),
            )
        }
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
        val cardWidth = (maxWidth - 20.dp) / 2.2f - spacing
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(width * 1.5f)
                .clip(RoundedCornerShape(10.dp)),
        ) {
            ShellCover(
                cover = manga.asMangaCover(),
                title = manga.title,
                fixedRatio = 2f / 3f,
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
            .clip(RoundedCornerShape(14.dp))
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClickSource(src) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SourceIcon(source = src, modifier = Modifier.size(32.dp))
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        TsuzukiText(
                            src.visualName,
                            style = TsuzukiTheme.typography.callout.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (item.hasUpdate) {
                            BrowseUpdateTag()
                        }
                    }
                    TsuzukiText(
                        LocaleHelper.getSourceDisplayName(src.lang, context),
                        style = TsuzukiTheme.typography.footnote,
                        color = colors.secondary,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { onClickPin(src) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
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
            }
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
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFFFC94A).copy(alpha = 0.2f))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        TsuzukiText(
            "UPDATE",
            style = TsuzukiTheme.typography.caption2.copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
            color = Color(0xFFFFC94A),
        )
    }
}
