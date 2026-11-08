package dev.errnolink.tsuzuki.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.designsystem.Banner
import dev.errnolink.tsuzuki.designsystem.BrandColors
import dev.errnolink.tsuzuki.designsystem.Calendar
import dev.errnolink.tsuzuki.designsystem.EmptyState
import dev.errnolink.tsuzuki.designsystem.NoticeTone
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.PullDownConfirmSheet
import dev.errnolink.tsuzuki.designsystem.RefreshCw
import dev.errnolink.tsuzuki.designsystem.Search
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.mangadex.feedDayLabel
import dev.errnolink.tsuzuki.ui.history.FullHistoryScreen
import dev.errnolink.tsuzuki.ui.shell.FeedSectionSkeleton
import dev.errnolink.tsuzuki.ui.shell.ShellList
import dev.errnolink.tsuzuki.ui.shell.ShellTopAction
import dev.errnolink.tsuzuki.ui.shell.shellBottomPadding
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import kotlinx.coroutines.flow.collectLatest
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class UnifiedFeedScreen : Screen() {

    override val key: ScreenKey = "UnifiedFeedScreen"

    @Composable
    override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    val screenModel = rememberScreenModel { UnifiedFeedScreenModel() }
    val state by screenModel.state.collectAsState()
    val listState = rememberLazyListState()
    val pinnedDayKey by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val pinned = info.visibleItemsInfo.firstOrNull { item ->
                val key = item.key as? String
                key != null && key.startsWith("day-header-") && item.offset <= info.beforeContentPadding
            }
            pinned?.key
        }
    }
    var confirmMarkReadDay by remember { mutableStateOf<LocalDate?>(null) }
    var confirmMarkReadGroups by remember { mutableStateOf<List<FeedTitleGroup>>(emptyList()) }

    LaunchedEffect(Unit) {
        screenModel.navigation.collectLatest { nav ->
            when (nav) {
                is FeedNavigation.Title -> navigator.push(MangaScreen(nav.id))
                is FeedNavigation.Chapter -> dev.errnolink.tsuzuki.reader.ReaderTransition.open(
                    context,
                    ReaderActivity.newIntent(context, nav.mangaId, nav.chapterId),
                )
            }
        }
    }

    ShellList(
        title = "Feed",
        state = listState,
        navigateUp = null,
        itemSpacing = 0.dp,
        actions = listOf(
            ShellTopAction("Release calendar", TsuzukiIcons.Calendar, { navigator.push(mihon.feature.upcoming.UpcomingScreen()) }),
            ShellTopAction("Refresh", TsuzukiIcons.RefreshCw, screenModel::refreshAll),
        ),
    ) {
        item(key = "up-next") {
            if (state.upNextLoaded && state.upNext.isNotEmpty()) {
                FeedContinueShelf(
                    cards = state.upNext,
                    onOpenHistory = { navigator.push(FullHistoryScreen()) },
                    onContinue = { card ->
                        dev.errnolink.tsuzuki.reader.ReaderTransition.open(
                            context,
                            ReaderActivity.newIntent(context, card.mangaId, card.chapterId),
                        )
                    },
                )
            }
        }

        item(key = "new-chapters-header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TsuzukiText(
                    "NEW CHAPTERS",
                    style = TsuzukiTheme.typography.caption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                    color = TsuzukiTheme.colors.secondary,
                )
                if (state.totalUnreadCount > 0) {
                    TsuzukiText(
                        "${state.totalUnreadCount} unread",
                        style = TsuzukiTheme.typography.footnote.copy(fontWeight = FontWeight.SemiBold),
                        color = TsuzukiTheme.colors.secondary,
                    )
                }
            }
        }

        item(key = "feed-chips") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FeedChipPill(
                    label = "All",
                    count = state.totalUnreadCount.takeIf { it > 0 },
                    selected = state.selectedChip == FeedChip.ALL,
                    onClick = { screenModel.setChip(FeedChip.ALL) },
                )
                FeedChipPill(
                    label = "Library",
                    count = null,
                    selected = state.selectedChip == FeedChip.LIBRARY,
                    onClick = { screenModel.setChip(FeedChip.LIBRARY) },
                )
                if (state.isMangaDexLoggedIn) {
                    FeedChipPill(
                        label = "MangaDex follows",
                        count = null,
                        selected = state.selectedChip == FeedChip.MANGADEX,
                        leadingIcon = {
                            Box(
                                Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(BrandColors.mangaDex),
                            )
                        },
                        onClick = { screenModel.setChip(FeedChip.MANGADEX) },
                    )
                }
                FeedChipPill(
                    label = "Unread",
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
                        message = "Could not update MangaDex follows",
                        tone = NoticeTone.Error,
                        actionLabel = "Retry",
                        onAction = { screenModel.refreshAll() },
                    )
                }
            }
        }

        if (state.isLoading || !state.upNextLoaded) {
            item(key = "feed-skeleton-1") {
                FeedSectionSkeleton(Modifier.padding(top = 8.dp))
            }
            item(key = "feed-skeleton-2") {
                FeedSectionSkeleton()
            }
        } else if (state.dayGroups.isEmpty()) {
            item(key = "feed-empty") {
                if (state.followsError && state.selectedChip == FeedChip.MANGADEX) {
                    EmptyState(
                        title = "Feed unavailable",
                        description = "Could not load your MangaDex follows feed.",
                        icon = { Icon(TsuzukiIcons.RefreshCw, null, Modifier.size(36.dp), tint = TsuzukiTheme.colors.secondary) },
                        action = {
                            PillButton("Retry", { screenModel.refreshAll() })
                        },
                    )
                } else {
                    EmptyState(
                        title = "No new chapters",
                        description = when (state.selectedChip) {
                            FeedChip.LIBRARY -> "No unread updates for titles in your library."
                            FeedChip.MANGADEX -> "No updates from manga you follow on MangaDex."
                            FeedChip.UNREAD -> "No unread chapters in your feed."
                            else -> "New chapters from your library and follows will show up here."
                        },
                        icon = { Icon(TsuzukiIcons.Search, null, Modifier.size(36.dp), tint = TsuzukiTheme.colors.secondary) },
                    )
                }
            }
        }

        state.dayGroups.forEach { (day, groups) ->
            stickyHeader(key = "day-header-$day", contentType = "day-header") {
                FeedTimelineDayHeader(
                    day = day,
                    groupCount = groups.size,
                    chapterCount = groups.sumOf { it.chapters.size },
                    pinned = pinnedDayKey == "day-header-$day",
                    onMarkRead = {
                        confirmMarkReadDay = day
                        confirmMarkReadGroups = groups
                    },
                )
            }

            items(groups, key = { "$day-${it.groupKey}" }, contentType = { "feed-card" }) { group ->
                FeedTimelineCard(
                    group = group,
                    onClickTitle = { screenModel.openTitle(group) },
                    onClickChapter = { ch -> screenModel.openChapter(group, ch) },
                    onSwipeMarkRead = { screenModel.markGroupAsRead(group) },
                    onSwipeDownload = { group.chapters.firstOrNull()?.let { screenModel.downloadChapter(it) } },
                )
            }
        }

        item(key = "feed-bottom-spacer") {
            Spacer(Modifier.height(shellBottomPadding()))
        }
    }
    confirmMarkReadDay?.let { day ->
        PullDownConfirmSheet(
            visible = true,
            title = "Mark all read",
            message = "Mark all chapters for ${feedDayLabel(day)} as read?",
            confirmLabel = "Mark all read",
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
}
}

@Composable
private fun FeedChipPill(
    label: String,
    count: Int?,
    selected: Boolean,
    leadingIcon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val colors = TsuzukiTheme.colors
    val bg = if (selected) colors.text else colors.grouped
    val fg = if (selected) colors.background else colors.secondary
    val countBg = if (selected) colors.background else colors.accent
    val countFg = if (selected) colors.text else Color.White

    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = androidx.compose.ui.semantics.Role.Tab, onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(bg)
                .heightIn(min = 40.dp)
                .padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (leadingIcon != null) {
                leadingIcon()
            }
            TsuzukiText(
                label,
                style = TsuzukiTheme.typography.footnote.copy(fontWeight = FontWeight.SemiBold),
                color = fg,
                maxLines = 1,
            )
            if (count != null && count > 0) {
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
        }
    }
}

@Composable
private fun MangaDexFollowsBanner(
    username: String,
    lastChecked: Instant?,
) {
    val colors = TsuzukiTheme.colors
    val relativeTime = remember(lastChecked) {
        lastChecked?.let {
            val mins = Duration.between(it, Instant.now()).toMinutes()
            if (mins < 1) "just now" else "${mins} min ago"
        } ?: "recently"
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
                "Signed in as $username",
                style = TsuzukiTheme.typography.subhead.copy(fontWeight = FontWeight.Bold),
                color = colors.text,
            )
            TsuzukiText(
                "Follows feed · checked $relativeTime",
                style = TsuzukiTheme.typography.caption1,
                color = colors.secondary,
            )
        }
    }
}
