package dev.errnolink.tsuzuki.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.luminance
import dev.errnolink.tsuzuki.designsystem.M3Motion
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.designsystem.Check
import dev.errnolink.tsuzuki.designsystem.CheckCheck
import dev.errnolink.tsuzuki.designsystem.ChevronDown
import dev.errnolink.tsuzuki.designsystem.ChevronUp
import dev.errnolink.tsuzuki.designsystem.Download
import dev.errnolink.tsuzuki.designsystem.Play
import dev.errnolink.tsuzuki.designsystem.Plus
import dev.errnolink.tsuzuki.designsystem.RefreshCw
import dev.errnolink.tsuzuki.designsystem.Search
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.PullDownConfirmSheet
import dev.errnolink.tsuzuki.designsystem.Banner
import dev.errnolink.tsuzuki.designsystem.EmptyState
import dev.errnolink.tsuzuki.designsystem.NoticeTone
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.ui.shell.FeedSectionSkeleton
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.mangadex.feedDayLabel
import dev.errnolink.tsuzuki.ui.details.ContinueActionColor
import dev.errnolink.tsuzuki.ui.details.getSwipeAction
import dev.errnolink.tsuzuki.ui.details.swipeAction
import dev.errnolink.tsuzuki.ui.history.FullHistoryScreen
import dev.errnolink.tsuzuki.ui.shell.ShellAction
import dev.errnolink.tsuzuki.ui.shell.ShellCover
import dev.errnolink.tsuzuki.ui.shell.ShellList
import dev.errnolink.tsuzuki.ui.shell.ShellTopAction
import dev.errnolink.tsuzuki.ui.shell.shellBottomPadding
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import kotlinx.coroutines.flow.collectLatest
import me.saket.swipe.SwipeableActionsBox
import tachiyomi.domain.library.service.LibraryPreferences
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class UnifiedFeedScreen : Screen() {

    @Composable
    override fun Content() {
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    val screenModel = rememberScreenModel { UnifiedFeedScreenModel() }
    val state by screenModel.state.collectAsState()
    var confirmMarkReadDay by remember { mutableStateOf<LocalDate?>(null) }
    var confirmMarkReadGroups by remember { mutableStateOf<List<FeedTitleGroup>>(emptyList()) }

    LaunchedEffect(Unit) {
        screenModel.navigation.collectLatest { nav ->
            when (nav) {
                is FeedNavigation.Title -> navigator.push(MangaScreen(nav.id))
                is FeedNavigation.Chapter -> context.startActivity(
                    ReaderActivity.newIntent(context, nav.mangaId, nav.chapterId),
                )
            }
        }
    }

    ShellList(
        title = "Feed",
        navigateUp = null,
        itemSpacing = 0.dp,
        actions = listOf(ShellTopAction("Refresh", TsuzukiIcons.RefreshCw, screenModel::refreshAll)),
    ) {
        item(key = "up-next") {
            if (state.upNext.isNotEmpty()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TsuzukiText(
                            "UP NEXT",
                            style = TsuzukiTheme.typography.caption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                            color = TsuzukiTheme.colors.secondary,
                        )
                        Box(
                            Modifier.heightIn(min = 44.dp).clickable { navigator.push(FullHistoryScreen()) },
                            contentAlignment = Alignment.Center,
                        ) {
                            TsuzukiText(
                                "History",
                                style = TsuzukiTheme.typography.subhead.copy(fontWeight = FontWeight.SemiBold),
                                color = TsuzukiTheme.colors.accent,
                            )
                        }
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        flingBehavior = dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior(),
                    ) {
                        items(state.upNext, key = { it.mangaId }) { card ->
                            UpNextCardItem(
                                card = card,
                                onClick = {
                                    context.startActivity(
                                        ReaderActivity.newIntent(context, card.mangaId, card.chapterId),
                                    )
                                },
                            )
                        }
                    }
                }
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
                                Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFFF6740)),
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

        if (state.isLoading) {
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
            item(key = "day-header-$day") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TsuzukiText(
                        feedDayLabel(day).uppercase(),
                        style = TsuzukiTheme.typography.caption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                        color = TsuzukiTheme.colors.secondary,
                    )
                    Box(
                        Modifier.heightIn(min = 44.dp).clickable {
                            confirmMarkReadDay = day
                            confirmMarkReadGroups = groups
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        TsuzukiText(
                            "Mark all read",
                            style = TsuzukiTheme.typography.footnote.copy(fontWeight = FontWeight.SemiBold),
                            color = TsuzukiTheme.colors.accent,
                        )
                    }
                }
            }

            items(groups, key = { "$day-${it.groupKey}" }) { group ->
                val expanded = state.expandedGroups.contains(group.groupKey)
                FeedTitleRow(
                    group = group,
                    expanded = expanded,
                    onToggleExpand = { screenModel.toggleExpand(group.groupKey) },
                    onClickCover = { screenModel.openTitle(group) },
                    onClickReadOldest = { screenModel.openOldestUnread(group) },
                    onClickMarkRead = { screenModel.markGroupAsRead(group) },
                    onClickAdd = { screenModel.addFollowToLibrary(group) },
                    onSwipeMarkRead = { screenModel.markGroupAsRead(group) },
                    onSwipeDownload = { group.chapters.firstOrNull()?.let { screenModel.downloadChapter(it) } },
                    onChapterClick = { ch -> screenModel.openChapter(group, ch) },
                    onChapterDownload = { ch -> screenModel.downloadChapter(ch) },
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
private fun UpNextCardItem(
    card: UpNextCard,
    onClick: () -> Unit,
) {
    val colors = TsuzukiTheme.colors
    val accent = colors.accent
    val seededColor = remember(card.mangaId) {
        tachiyomi.domain.manga.model.MangaCover.dominantCoverColorMap[card.mangaId]?.first?.let { Color(it) }
    }
    val playColor = remember(seededColor, accent) {
        seededColor?.let { ContinueActionColor.forCover(it, accent) } ?: accent
    }

    Box(
        modifier = Modifier
            .width(286.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(colors.grouped)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShellCover(
                cover = card.coverData,
                title = card.mangaTitle,
                modifier = Modifier.width(62.dp).height(88.dp).clip(RoundedCornerShape(10.dp)),
                fixedRatio = 62f / 88f,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TsuzukiText(
                    card.mangaTitle,
                    style = TsuzukiTheme.typography.subhead.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = colors.text,
                )
                val subtitle = if (card.pageTotal != null && card.pageTotal > 0) {
                    "${card.chapterName} · page ${(card.lastPageRead + 1).coerceAtMost(card.pageTotal.toLong())} of ${card.pageTotal}"
                } else {
                    card.chapterName
                }
                TsuzukiText(
                    subtitle,
                    style = TsuzukiTheme.typography.caption1,
                    color = colors.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (card.progressFraction != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colors.separator),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(card.progressFraction)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(colors.accent),
                        )
                    }
                }
            }
            Box(
                modifier = Modifier.size(48.dp).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(playColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        TsuzukiIcons.Play,
                        contentDescription = "Resume",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
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
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
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
                .background(Color(0xFFFF6740)),
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

@Composable
private fun FeedTitleRow(
    group: FeedTitleGroup,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onClickCover: () -> Unit,
    onClickReadOldest: () -> Unit,
    onClickMarkRead: () -> Unit,
    onClickAdd: () -> Unit,
    onSwipeMarkRead: () -> Unit,
    onSwipeDownload: () -> Unit,
    onChapterClick: (FeedChapterItem) -> Unit,
    onChapterDownload: (FeedChapterItem) -> Unit,
) {
    val colors = TsuzukiTheme.colors
    val swipeStart = swipeAction(
        label = "Mark read",
        icon = TsuzukiIcons.CheckCheck,
        background = colors.accent,
        onSwipe = onSwipeMarkRead,
    )
    val swipeEnd = swipeAction(
        label = "Download",
        icon = TsuzukiIcons.Download,
        background = colors.accent,
        onSwipe = onSwipeDownload,
    )

    SwipeableActionsBox(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp).clip(RoundedCornerShape(16.dp)),
        startActions = listOf(swipeStart),
        endActions = listOf(swipeEnd),
        swipeThreshold = 56.dp,
        backgroundUntilSwipeThreshold = colors.grouped,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (expanded) colors.grouped else Color.Transparent)
                .padding(vertical = 4.dp),
        ) {
            val expandLabel = stringResource(if (expanded) MR.strings.tsuzuki_collapse else MR.strings.tsuzuki_expand)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (group.chapters.size > 1) onToggleExpand() else onClickReadOldest()
                    }
                    .semantics {
                        if (group.chapters.size > 1) {
                            stateDescription = expandLabel
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 44.dp, height = 62.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .clickable(onClick = onClickCover),
                ) {
                    val cover = tachiyomi.domain.manga.model.MangaCover(
                        mangaId = group.mangaId,
                        sourceId = group.sourceId,
                        isMangaFavorite = group.isLibrary,
                        ogUrl = group.coverUrl,
                        lastModified = group.coverLastModified,
                    )
                    ShellCover(
                        cover = cover,
                        title = group.mangaTitle,
                        modifier = Modifier.fillMaxSize(),
                        fixedRatio = 44f / 62f,
                    )
                    if (group.newCount > 1) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(3.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(colors.accent)
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        ) {
                            TsuzukiText(
                                group.newCount.toString(),
                                style = TsuzukiTheme.typography.caption2.copy(fontWeight = FontWeight.ExtraBold),
                                color = Color.White,
                            )
                        }
                    }
                    if (group.isLibrary && group.mangaDexId != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(3.dp)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF35C27A)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(TsuzukiIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    TsuzukiText(
                        group.mangaTitle,
                        style = TsuzukiTheme.typography.subhead.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = colors.text,
                    )
                    val chaptersSummary = if (group.chapters.size > 1) {
                        val oldest = group.chapters.minByOrNull { it.chapterNumber }?.chapterNumber
                        val newest = group.chapters.maxByOrNull { it.chapterNumber }?.chapterNumber
                        if (oldest != null && newest != null && oldest >= 0 && newest >= 0) {
                            "Ch. ${oldest.toString().removeSuffix(".0")}–${newest.toString().removeSuffix(".0")}"
                        } else {
                            "${group.chapters.size} new chapters"
                        }
                    } else {
                        group.chapters.firstOrNull()?.chapterName.orEmpty()
                    }
                    TsuzukiText(
                        chaptersSummary,
                        style = TsuzukiTheme.typography.caption1,
                        color = colors.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!group.isLibrary) {
                        Row(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFF6740).copy(alpha = 0.16f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFFF6740)))
                            TsuzukiText(
                                stringResource(MR.strings.tsuzuki_not_in_library),
                                style = TsuzukiTheme.typography.caption2.copy(fontWeight = FontWeight.Bold),
                                color = if (colors.background.luminance() < 0.5f) Color(0xFFFF9A7F) else Color(0xFF943414),
                            )
                        }
                    }
                }

                if (!group.isLibrary) {
                    Box(
                        modifier = Modifier.size(48.dp).clickable(onClick = onClickAdd),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(colors.grouped)
                                .heightIn(min = 40.dp)
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(TsuzukiIcons.Plus, contentDescription = stringResource(MR.strings.tsuzuki_add), tint = colors.text, modifier = Modifier.size(13.dp))
                            TsuzukiText(stringResource(MR.strings.tsuzuki_add), style = TsuzukiTheme.typography.caption1.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                } else if (group.chapters.size > 1) {
                    Icon(
                        if (expanded) TsuzukiIcons.ChevronUp else TsuzukiIcons.ChevronDown,
                        contentDescription = "Expand",
                        tint = colors.secondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = if (TsuzukiTheme.motion.reduced) fadeIn(M3Motion.reducedFade()) else expandVertically() + fadeIn(),
                exit = if (TsuzukiTheme.motion.reduced) fadeOut(M3Motion.reducedFade()) else shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 56.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
                ) {
                    group.chapters.forEach { ch ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onChapterClick(ch) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (!ch.isRead) colors.accent else colors.separator),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                TsuzukiText(
                                    ch.chapterName,
                                    style = TsuzukiTheme.typography.footnote.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (!ch.isRead) colors.text else colors.secondary,
                                )
                                val meta = buildList {
                                    if (ch.pageCount != null && ch.pageCount > 0) add("${ch.pageCount} pages")
                                    if (ch.scanlator?.isNotBlank() == true) add(ch.scanlator)
                                }.joinToString(" · ")
                                if (meta.isNotBlank()) {
                                    TsuzukiText(meta, style = TsuzukiTheme.typography.caption2, color = colors.secondary)
                                }
                            }
                            Box(
                                modifier = Modifier.size(48.dp).clickable { onChapterDownload(ch) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    TsuzukiIcons.Download,
                                    contentDescription = "Download",
                                    tint = colors.secondary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val oldest = group.oldestUnreadChapter
                        val readLabel = oldest?.chapterNumber?.takeIf { it >= 0 }
                            ?.let { "Read Ch. ${it.toString().removeSuffix(".0")}" }
                            ?: "Read oldest"
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(colors.accent)
                                .clickable(onClick = onClickReadOldest)
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(TsuzukiIcons.Play, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                            TsuzukiText(readLabel, style = TsuzukiTheme.typography.caption1.copy(fontWeight = FontWeight.Bold), color = Color.White)
                        }

                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(colors.separator)
                                .clickable(onClick = onClickMarkRead)
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(TsuzukiIcons.Check, contentDescription = null, tint = colors.text, modifier = Modifier.size(13.dp))
                            TsuzukiText(
                                "Mark ${group.newCount} read",
                                style = TsuzukiTheme.typography.caption1.copy(fontWeight = FontWeight.Bold),
                                color = colors.text,
                            )
                        }
                    }
                }
            }
        }
    }
}
