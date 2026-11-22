package dev.errnolink.tsuzuki.ui.feed

import androidx.compose.runtime.Composable
import java.time.LocalDate

@Composable
internal fun FeedTimelineDayHeader(
    day: LocalDate,
    unreadCount: Int,
    canMarkRead: Boolean,
    pinned: Boolean,
    onMarkRead: () -> Unit,
) {
    FeedTimelineDayHeaderCurrent(
        day = day,
        unreadCount = unreadCount,
        canMarkRead = canMarkRead,
        pinned = pinned,
        onMarkRead = onMarkRead,
    )
}

@Composable
internal fun FeedTimelineCard(
    group: FeedTitleGroup,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onClickTitle: () -> Unit,
    onClickChapter: (FeedChapterItem) -> Unit,
    onMarkChapterRead: (FeedChapterItem) -> Unit,
    onDownloadChapter: (FeedChapterItem) -> Unit,
    onSwipeMarkRead: () -> Unit,
    onSwipeDownload: () -> Unit,
) {
    FeedTimelineCardCurrent(
        group = group,
        expanded = expanded,
        onToggleExpand = onToggleExpand,
        onClickTitle = onClickTitle,
        onClickChapter = onClickChapter,
        onMarkChapterRead = onMarkChapterRead,
        onDownloadChapter = onDownloadChapter,
        onSwipeMarkRead = onSwipeMarkRead,
        onSwipeDownload = onSwipeDownload,
    )
}
