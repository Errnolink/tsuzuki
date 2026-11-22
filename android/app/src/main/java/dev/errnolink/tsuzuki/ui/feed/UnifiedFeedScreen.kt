package dev.errnolink.tsuzuki.ui.feed

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.imageLoader
import dev.errnolink.tsuzuki.designsystem.Banner
import dev.errnolink.tsuzuki.designsystem.BrandColors
import dev.errnolink.tsuzuki.designsystem.Calendar
import dev.errnolink.tsuzuki.designsystem.Chip
import dev.errnolink.tsuzuki.designsystem.GlassContext
import dev.errnolink.tsuzuki.designsystem.GlassMaterial
import dev.errnolink.tsuzuki.designsystem.GlassSurface
import dev.errnolink.tsuzuki.designsystem.KasaneSprings
import dev.errnolink.tsuzuki.designsystem.LargeTitleScaffold
import dev.errnolink.tsuzuki.designsystem.NoticeTone
import dev.errnolink.tsuzuki.designsystem.PullDownConfirmSheet
import dev.errnolink.tsuzuki.designsystem.RefreshCw
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.rememberGlassContext
import dev.errnolink.tsuzuki.designsystem.rememberTopBarSearch
import dev.errnolink.tsuzuki.ui.history.rememberHistoryFeedContent
import dev.errnolink.tsuzuki.ui.motion.openTitle
import dev.errnolink.tsuzuki.ui.shell.DeclareShellTop
import dev.errnolink.tsuzuki.ui.shell.FeedSectionSkeleton
import dev.errnolink.tsuzuki.ui.shell.LocalShellGlass
import dev.errnolink.tsuzuki.ui.shell.ShellError
import dev.errnolink.tsuzuki.ui.shell.ShellRefresh
import dev.errnolink.tsuzuki.ui.shell.ShellToasts
import dev.errnolink.tsuzuki.ui.shell.ShellTopAction
import dev.errnolink.tsuzuki.ui.shell.TabToolRequests
import dev.errnolink.tsuzuki.ui.shell.scrolledUnderFraction
import dev.errnolink.tsuzuki.ui.shell.shellBottomPadding
import dev.errnolink.tsuzuki.ui.shell.statusToastPadding
import dev.errnolink.tsuzuki.ui.states.FeedEmptyState
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.LibraryUpdateStatus
import eu.kanade.tachiyomi.ui.feed.FeedTab
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import kotlinx.coroutines.flow.collectLatest
import mihon.feature.upcoming.UpcomingScreen
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

private const val FeedPreloadAhead = 10

class UnifiedFeedScreen : Screen() {

    override val key: ScreenKey = "UnifiedFeedScreen"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = rememberScreenModel { UnifiedFeedScreenModel() }
        val state by screenModel.state.collectAsState()
        val libraryUpdating by remember { Injekt.get<LibraryUpdateStatus>().isRunning }.collectAsState()
        val isFeedRefreshing = state.isRefreshing || libraryUpdating

        var activeTab by rememberSaveable { mutableStateOf(FeedHostTab.FEED) }
        val pendingSegment = FeedTab.pendingSegment
        LaunchedEffect(pendingSegment) {
            if (pendingSegment >= 0) {
                activeTab = if (pendingSegment == FeedTab.SEGMENT_HISTORY) FeedHostTab.HISTORY else FeedHostTab.FEED
                FeedTab.pendingSegment = -1
            }
        }

        val historyFeed = rememberHistoryFeedContent(backHandlerEnabled = activeTab == FeedHostTab.HISTORY)
        val feedListState = rememberLazyListState()
        val historyListState = rememberLazyListState()

        LaunchedEffect(Unit) {
            HomeScreen.reselectEvents.collect {
                if (it == FeedTab.key) {
                    if (activeTab == FeedHostTab.HISTORY) historyListState.scrollToItem(0) else feedListState.scrollToItem(0)
                }
            }
        }

        var expandState by remember { mutableStateOf(FeedExpandState()) }
        LaunchedEffect(state.dayGroups) {
            val validKeys = state.dayGroups.entries
                .flatMap { (day, groups) ->
                    groups.filter { it.chapters.size > 1 }.map { "$day-${it.groupKey}" }
                }
                .toSet()
            expandState = expandState.prune(validKeys)
        }

        val density = LocalDensity.current
        LaunchedEffect(feedListState, state.dayGroups) {
            if (state.dayGroups.isEmpty()) return@LaunchedEffect
            val coverSize = coil3.size.Size(
                with(density) { 54.dp.roundToPx() },
                with(density) { 76.dp.roundToPx() },
            )
            val preloaded = mutableSetOf<String>()
            snapshotFlow { feedListState.layoutInfo.visibleItemsInfo.mapTo(HashSet()) { it.key } }.collect { keys ->
                FeedMerger.preloadGroupsAfter(state.dayGroups, keys, ahead = FeedPreloadAhead).forEach { group ->
                    if (preloaded.add("${group.sourceId}:${group.mangaId}:${group.coverUrl}")) {
                        context.imageLoader.enqueue(
                            coil3.request.ImageRequest.Builder(context)
                                .data(
                                    tachiyomi.domain.manga.model.MangaCover(
                                        mangaId = group.mangaId,
                                        sourceId = group.sourceId,
                                        isMangaFavorite = group.isLibrary,
                                        ogUrl = group.coverUrl,
                                        lastModified = group.coverLastModified,
                                    ),
                                )
                                .size(coverSize)
                                .precision(coil3.size.Precision.INEXACT)
                                .build(),
                        )
                    }
                }
            }
        }

        var confirmMarkReadDay by remember { mutableStateOf<LocalDate?>(null) }
        var confirmMarkReadGroups by remember { mutableStateOf<List<FeedTitleGroup>>(emptyList()) }
        var confirmMarkReadAll by remember { mutableStateOf(false) }

        val feedSearch = rememberTopBarSearch(
            query = state.searchQuery.orEmpty(),
            onQueryChange = screenModel::setQuery,
            placeholder = stringResource(MR.strings.tsuzuki_search_feed),
            onClose = { screenModel.setQuery(null) },
        )
        LaunchedEffect(state.searchQuery) {
            if (state.searchQuery == null) feedSearch.state.collapse() else feedSearch.state.expand()
        }
        LaunchedEffect(Unit) {
            TabToolRequests.markAllFeedRead.collect {
                if (markableUnreadCount(state.dayGroups.values.flatten()) > 0) {
                    confirmMarkReadAll = true
                }
            }
        }
        LaunchedEffect(Unit) {
            TabToolRequests.historySearch.collect {
                if (activeTab == FeedHostTab.HISTORY && !historyFeed.selectionMode) {
                    historyFeed.search.state.expand()
                }
            }
        }

        LaunchedEffect(Unit) {
            screenModel.navigation.collectLatest { nav ->
                when (nav) {
                    is FeedNavigation.Title -> openTitle(navigator, nav.id)
                    is FeedNavigation.Chapter -> dev.errnolink.tsuzuki.reader.ReaderTransition.open(
                        context,
                        ReaderActivity.newIntent(context, nav.mangaId, nav.chapterId),
                    )
                }
            }
        }

        val shellGlass = LocalShellGlass.current
        val glass = shellGlass ?: rememberGlassContext()
        val clearance = shellBottomPadding()
        val activeListState = if (activeTab == FeedHostTab.HISTORY) historyListState else feedListState
        val scrolledUnderProvider = remember(activeListState, density) {
            val rampPx = with(density) { 24.dp.toPx() }
            derivedStateOf {
                scrolledUnderFraction(
                    activeListState.firstVisibleItemIndex,
                    activeListState.firstVisibleItemScrollOffset.toFloat(),
                    rampPx,
                )
            }
        }

        val historySelectionActive = activeTab == FeedHostTab.HISTORY && historyFeed.selectionMode
        DeclareShellTop(
            title = if (historySelectionActive) stringResource(MR.strings.tsuzuki_n_selected, historyFeed.selectionCount) else "",
            glass = glass,
            navigateUp = if (historySelectionActive) historyFeed.onExitSelection else null,
            actions = if (activeTab == FeedHostTab.HISTORY) {
                historyFeed.actions
            } else {
                listOf(
                    ShellTopAction(stringResource(MR.strings.tsuzuki_release_calendar), TsuzukiIcons.Calendar, { navigator.push(UpcomingScreen()) }),
                    ShellTopAction(stringResource(MR.strings.tsuzuki_refresh), TsuzukiIcons.RefreshCw, screenModel::refreshAll),
                )
            },
            search = if (activeTab == FeedHostTab.HISTORY) {
                if (historyFeed.selectionMode) null else historyFeed.search
            } else {
                feedSearch
            },
            inlineTitle = { if (historySelectionActive) 1f else 0f },
            edge = false,
            scrolledUnderFraction = { scrolledUnderProvider.value },
            leadingKey = if (historySelectionActive) null else "feed-mode-capsule",
            leadingContent = if (historySelectionActive) {
                null
            } else {
                {
                    FeedModeCapsule(
                        selected = activeTab,
                        glass = glass,
                        onSelect = { activeTab = it },
                    )
                }
            },
        )

        Box(Modifier.fillMaxSize()) {
            if (activeTab == FeedHostTab.HISTORY) {
                LargeTitleScaffold(
                    title = "",
                    state = historyListState,
                    glassContext = glass,
                    largeTitles = false,
                    bottomPadding = clearance,
                    itemSpacing = 0.dp,
                    content = historyFeed.content,
                )
                ShellToasts(historyFeed.toasts, Modifier.align(Alignment.BottomCenter).statusToastPadding())
            } else {
                ShellRefresh(
                    refreshing = isFeedRefreshing,
                    onRefresh = screenModel::refreshAll,
                ) {
                    LargeTitleScaffold(
                        title = "",
                        state = feedListState,
                        glassContext = glass,
                        largeTitles = false,
                        bottomPadding = clearance,
                        itemSpacing = 0.dp,
                    ) {
                        item(key = "feed-chips") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 18.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FeedChipPill(
                                    label = stringResource(MR.strings.all),
                                    count = state.totalUnreadCount.takeIf { it > 0 },
                                    selected = state.selectedChip == FeedChip.ALL,
                                    onClick = { screenModel.setChip(FeedChip.ALL) },
                                )
                                FeedChipPill(
                                    label = stringResource(MR.strings.label_library),
                                    count = null,
                                    selected = state.selectedChip == FeedChip.LIBRARY,
                                    onClick = { screenModel.setChip(FeedChip.LIBRARY) },
                                )
                                if (state.isMangaDexLoggedIn) {
                                    FeedChipPill(
                                        label = stringResource(MR.strings.tsuzuki_mangadex_follows),
                                        count = null,
                                        selected = state.selectedChip == FeedChip.MANGADEX,
                                        leadingIcon = {
                                            Box(
                                                Modifier
                                                    .size(14.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(BrandColors.mangaDex),
                                            )
                                        },
                                        onClick = { screenModel.setChip(FeedChip.MANGADEX) },
                                    )
                                }
                                FeedChipPill(
                                    label = stringResource(MR.strings.tsuzuki_unread),
                                    count = null,
                                    selected = state.selectedChip == FeedChip.UNREAD,
                                    onClick = { screenModel.setChip(FeedChip.UNREAD) },
                                )
                            }
                        }

                        if (state.selectedChip == FeedChip.MANGADEX && state.isMangaDexLoggedIn) {
                            item(key = "md-banner") {
                                MangaDexFollowsBanner(
                                    username = state.mangaDexUsername ?: "MangaDex",
                                    lastChecked = state.mangaDexLastChecked,
                                )
                            }
                        }
                        if (state.followsError) {
                            item(key = "follows-error") {
                                Box(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                                    Banner(
                                        message = stringResource(MR.strings.tsuzuki_mangadex_follows_update_error),
                                        tone = NoticeTone.Error,
                                        actionLabel = stringResource(MR.strings.action_retry),
                                        onAction = { screenModel.refreshAll() },
                                    )
                                }
                            }
                        }

                        if (state.isLoading) {
                            item(key = "feed-skeleton-1") {
                                FeedSectionSkeleton(Modifier.padding(top = 8.dp))
                            }
                            item(key = "feed-skeleton-2") {
                                FeedSectionSkeleton(Modifier)
                            }
                        } else if (state.loadError) {
                            item(key = "feed-error") {
                                ShellError(
                                    title = stringResource(MR.strings.tsuzuki_feed_load_error_title),
                                    description = stringResource(MR.strings.tsuzuki_feed_load_error_desc),
                                    onRetry = { screenModel.retryObservations() },
                                )
                            }
                        } else if (state.dayGroups.isEmpty()) {
                            item(key = "feed-empty") {
                                val currentQuery = state.searchQuery
                                if (currentQuery != null) {
                                    FeedEmptyState(stringResource(MR.strings.tsuzuki_feed_search_empty, currentQuery))
                                } else if (state.followsError && state.selectedChip == FeedChip.MANGADEX) {
                                    ShellError(
                                        title = stringResource(MR.strings.tsuzuki_feed_unavailable_title),
                                        description = stringResource(MR.strings.tsuzuki_feed_mangadex_error_desc),
                                        onRetry = { screenModel.refreshAll() },
                                    )
                                } else {
                                    FeedEmptyState(
                                        when (state.selectedChip) {
                                            FeedChip.LIBRARY -> stringResource(MR.strings.tsuzuki_feed_empty_library)
                                            FeedChip.MANGADEX -> stringResource(MR.strings.tsuzuki_feed_empty_mangadex)
                                            FeedChip.UNREAD -> stringResource(MR.strings.tsuzuki_feed_empty_unread)
                                            else -> stringResource(MR.strings.tsuzuki_feed_empty_all)
                                        },
                                    )
                                }
                            }
                        }

                        state.dayGroups.forEach { (day, groups) ->
                            val dayMarkable = markableUnreadCount(groups)
                            val dayUnreadTotal = groups.sumOf { it.unreadCount }
                            item(key = "day-header-$day", contentType = "day-header") {
                                FeedTimelineDayHeader(
                                    day = day,
                                    unreadCount = dayUnreadTotal,
                                    canMarkRead = dayMarkable > 0,
                                    pinned = false,
                                    onMarkRead = {
                                        if (dayMarkable > 0) {
                                            confirmMarkReadDay = day
                                            confirmMarkReadGroups = groups
                                        }
                                    },
                                )
                            }
                            itemsIndexed(
                                groups,
                                key = { _, group -> "$day-${group.groupKey}" },
                                contentType = { _, _ -> "feed-card" },
                            ) { _, group ->
                                val cardKey = "$day-${group.groupKey}"
                                val expanded = expandState.isExpanded(cardKey, group.chapters.size)
                                FeedTimelineCard(
                                    group = group,
                                    expanded = expanded,
                                    onToggleExpand = {
                                        expandState = expandState.toggle(cardKey, group.chapters.size)
                                    },
                                    onClickTitle = { screenModel.openTitle(group) },
                                    onClickChapter = { ch -> screenModel.openChapter(group, ch) },
                                    onMarkChapterRead = { ch -> screenModel.markChapterAsRead(ch) },
                                    onDownloadChapter = { ch -> screenModel.downloadChapter(ch) },
                                    onSwipeMarkRead = { screenModel.markGroupAsRead(group) },
                                    onSwipeDownload = {
                                        downloadTargetChapter(group)?.let { screenModel.downloadChapter(it) }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        confirmMarkReadDay?.let { day ->
            val markAllReadLabel = stringResource(MR.strings.tsuzuki_mark_all_read)
            PullDownConfirmSheet(
                visible = true,
                title = markAllReadLabel,
                message = stringResource(MR.strings.tsuzuki_mark_all_read_for_day, feedDayText(day)),
                confirmLabel = markAllReadLabel,
                onConfirm = {
                    confirmMarkReadGroups.forEach { screenModel.markGroupAsRead(it) }
                    confirmMarkReadDay = null
                    confirmMarkReadGroups = emptyList()
                },
                onDismissRequest = {
                    confirmMarkReadDay = null
                    confirmMarkReadGroups = emptyList()
                },
            )
        }
        if (confirmMarkReadAll) {
            val actionable = markableUnreadCount(state.dayGroups.values.flatten())
            val markAllReadLabel = stringResource(MR.strings.tsuzuki_mark_all_read)
            PullDownConfirmSheet(
                visible = true,
                title = markAllReadLabel,
                message = when (actionable) {
                    0 -> stringResource(MR.strings.tsuzuki_feed_empty_unread)
                    1 -> stringResource(MR.strings.tsuzuki_mark_one_unread_confirm)
                    else -> stringResource(MR.strings.tsuzuki_mark_n_unread_confirm, actionable)
                },
                confirmLabel = markAllReadLabel,
                onConfirm = {
                    state.dayGroups.values.flatten().forEach { screenModel.markGroupAsRead(it) }
                    confirmMarkReadAll = false
                },
                onDismissRequest = { confirmMarkReadAll = false },
            )
        }
    }
}

@Composable
private fun FeedModeCapsule(
    selected: FeedHostTab,
    glass: GlassContext?,
    onSelect: (FeedHostTab) -> Unit,
) {
    val colors = TsuzukiTheme.colors
    val motion = TsuzukiTheme.motion
    GlassSurface(
        context = glass,
        shape = CircleShape,
        material = GlassMaterial.Thick,
        modifier = Modifier
            .width(184.dp)
            .height(48.dp),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(4.dp),
        ) {
            val halfWidth = maxWidth / 2
            val targetX = if (selected == FeedHostTab.FEED) 0.dp else halfWidth
            val pillOffset by animateDpAsState(
                targetValue = targetX,
                animationSpec = if (motion.reduced) snap() else KasaneSprings.tab(),
                label = "feed-mode-lens",
            )
            val lensFill = if (colors.isDark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.08f)
            Box(
                Modifier
                    .offset(x = pillOffset)
                    .width(halfWidth)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(lensFill),
            )
            Row(Modifier.fillMaxSize()) {
                FeedHostTab.entries.forEach { tab ->
                    val active = selected == tab
                    val label = if (tab == FeedHostTab.FEED) {
                        stringResource(MR.strings.tsuzuki_feed)
                    } else {
                        stringResource(MR.strings.label_recent_manga)
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .clickable(role = Role.Tab) { onSelect(tab) }
                            .semantics { this.selected = active },
                        contentAlignment = Alignment.Center,
                    ) {
                        TsuzukiText(
                            label,
                            style = TsuzukiTheme.typography.subhead.copy(
                                fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                            ),
                            color = if (active) colors.text else colors.secondary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedChipPill(
    label: String,
    count: Int?,
    selected: Boolean,
    leadingIcon: (@Composable RowScope.() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val colors = TsuzukiTheme.colors
    val countBg = colors.accent
    val countFg = colors.onAccent
    Box(
        modifier = Modifier.heightIn(min = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Chip(
            label = label,
            selected = selected,
            onClick = onClick,
            leading = leadingIcon,
            horizontalPadding = 13.dp,
            textStyle = TsuzukiTheme.typography.footnote.copy(fontWeight = FontWeight.SemiBold),
            trailing = if (count != null && count > 0) {
                {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(countBg)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        TsuzukiText(
                            count.toString(),
                            style = TsuzukiTheme.typography.caption1.copy(fontWeight = FontWeight.ExtraBold),
                            color = countFg,
                        )
                    }
                }
            } else {
                null
            },
        )
    }
}

@Composable
private fun MangaDexFollowsBanner(
    username: String,
    lastChecked: Instant?,
) {
    val colors = TsuzukiTheme.colors
    val elapsedMinutes = remember(lastChecked) {
        lastChecked?.let { Duration.between(it, Instant.now()).toMinutes() }
    }
    val relativeTime = when {
        elapsedMinutes == null -> stringResource(MR.strings.tsuzuki_recently)
        elapsedMinutes < 1 -> stringResource(MR.strings.tsuzuki_just_now_lower)
        else -> stringResource(MR.strings.tsuzuki_min_ago, elapsedMinutes)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.grouped)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(BrandColors.mangaDex),
        )
        Column {
            TsuzukiText(
                stringResource(MR.strings.tsuzuki_signed_in_as, username),
                style = TsuzukiTheme.typography.subhead.copy(fontWeight = FontWeight.Bold),
                color = colors.text,
            )
            TsuzukiText(
                stringResource(MR.strings.tsuzuki_follows_feed_checked, relativeTime),
                style = TsuzukiTheme.typography.caption1,
                color = colors.secondary,
            )
        }
    }
}
