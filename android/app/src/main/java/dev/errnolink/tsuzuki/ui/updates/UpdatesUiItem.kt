package dev.errnolink.tsuzuki.ui.updates
import eu.kanade.presentation.updates.UpdatesUiModel
import dev.errnolink.tsuzuki.reader.readerOrigin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import dev.errnolink.tsuzuki.designsystem.ArrowDown
import dev.errnolink.tsuzuki.designsystem.Bookmark
import dev.errnolink.tsuzuki.designsystem.BookmarkFilled
import dev.errnolink.tsuzuki.designsystem.ChevronDown
import dev.errnolink.tsuzuki.designsystem.ChevronUp
import dev.errnolink.tsuzuki.designsystem.CircleAlert
import dev.errnolink.tsuzuki.designsystem.CircleCheck
import dev.errnolink.tsuzuki.designsystem.Download as DownloadIcon
import dev.errnolink.tsuzuki.designsystem.RotateCcw
import dev.errnolink.tsuzuki.designsystem.SelectionMark
import dev.errnolink.tsuzuki.designsystem.Trash2
import dev.errnolink.tsuzuki.designsystem.TsuzukiColors
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.selectionFill
import eu.kanade.tachiyomi.ui.updates.UpdatesItem
import dev.errnolink.tsuzuki.designsystem.ProgressMark
import dev.errnolink.tsuzuki.ui.shell.ShellAction
import dev.errnolink.tsuzuki.ui.shell.ShellCaption
import dev.errnolink.tsuzuki.ui.motion.heroFlightLabel
import dev.errnolink.tsuzuki.ui.shell.ShellCover
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.manga.components.ChapterDownloadAction
import eu.kanade.presentation.manga.components.swipeActionThreshold
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.ui.updates.UpdatesScreenModel.UpdateSelectionOptions
import eu.kanade.tachiyomi.ui.updates.groupByDateAndManga
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.insetDivider
import dev.errnolink.tsuzuki.designsystem.TsuzukiSize
import dev.errnolink.tsuzuki.designsystem.KasaneSprings
import dev.errnolink.tsuzuki.designsystem.SectionHeader
import me.saket.swipe.SwipeAction
import me.saket.swipe.SwipeableActionsBox
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.updates.model.UpdatesWithRelations
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

internal fun LazyListScope.updatesUiItems(
    uiModels: List<UpdatesUiModel>,
    expandedState: Set<String>,
    collapseToggle: (key: String) -> Unit,
    usePanoramaCover: Boolean,
    selectionMode: Boolean,
    preserveReadingPosition: Boolean,
    onUpdateSelected: (UpdatesItem, UpdateSelectionOptions) -> Unit,
    onClickCover: (UpdatesItem) -> Unit,
    onClickUpdate: (UpdatesItem) -> Unit,
    onDownloadChapter: (List<UpdatesItem>, ChapterDownloadAction) -> Unit,
    updateSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    updateSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    onUpdateSwipe: (UpdatesItem, LibraryPreferences.ChapterSwipeAction) -> Unit,
) {
    val visibleModels = uiModels.filter { model ->
        model !is UpdatesUiModel.Item || model is UpdatesUiModel.Leader ||
            expandedState.contains(model.item.update.groupByDateAndManga())
    }
    itemsIndexed(
        items = visibleModels,
        contentType = { _, it ->
            when (it) {
                is UpdatesUiModel.Header -> "header"
                is UpdatesUiModel.Item -> "item"
            }
        },
        key = { _, it ->
            when (it) {
                is UpdatesUiModel.Header -> "updates-header-${it.hashCode()}"
                is UpdatesUiModel.Item -> "updates-${it.item.update.mangaId}-${it.item.update.chapterId}"
            }
        },
    ) { index, model ->
        val motion = TsuzukiTheme.motion
        val place: FiniteAnimationSpec<IntOffset>? = if (motion.reduced) null else KasaneSprings.tab()
        val fade: FiniteAnimationSpec<Float>? = if (motion.reduced) null else KasaneSprings.tab()
        val animated = Modifier.animateItem(fade, place, fade)
        when (model) {
            is UpdatesUiModel.Header -> UpdatesDateHeader(model, animated)
            is UpdatesUiModel.Item -> {
                val updatesItem = model.item
                val isLeader = model is UpdatesUiModel.Leader
                val isExpanded = expandedState.contains(updatesItem.update.groupByDateAndManga())
                val firstInDay = visibleModels.getOrNull(index - 1) !is UpdatesUiModel.Item
                val lastInDay = visibleModels.getOrNull(index + 1) !is UpdatesUiModel.Item
                    UpdatesUiItem(
                        update = updatesItem.update,
                        shape = RoundedCornerShape(
                            topStart = if (firstInDay) TsuzukiCorners.group else 0.dp,
                            topEnd = if (firstInDay) TsuzukiCorners.group else 0.dp,
                            bottomStart = if (lastInDay) TsuzukiCorners.group else 0.dp,
                            bottomEnd = if (lastInDay) TsuzukiCorners.group else 0.dp,
                        ),
                        divider = !lastInDay,
                        onRead = { if (selectionMode) onUpdateSelected(updatesItem, UpdateSelectionOptions(selected = !updatesItem.selected, fromLongPress = false, isGroup = false, isExpanded = isExpanded)) else onClickUpdate(updatesItem) },
                        selected = updatesItem.selected,
                        selectionMode = selectionMode,
                        readProgress = updatesItem.update.lastPageRead
                            .takeIf {
                                !updatesItem.update.read &&
                                    it > 0L
                            }
                            ?.let { stringResource(MR.strings.chapter_progress, it + 1) },
                        onClick = {
                            if (selectionMode) {
                                onUpdateSelected(
                                    updatesItem,
                                    UpdateSelectionOptions(
                                        selected = !updatesItem.selected,
                                        fromLongPress = false,
                                        isGroup = isLeader && model.isExpandable,
                                        isExpanded = isExpanded,
                                    ),
                                )
                            } else if (isLeader && model.isExpandable) {
                                collapseToggle(updatesItem.update.groupByDateAndManga())
                            } else {
                                onClickUpdate(updatesItem)
                            }
                        },
                        onLongClick = {
                            onUpdateSelected(
                                updatesItem,
                                UpdateSelectionOptions(
                                    selected = !updatesItem.selected,
                                    fromLongPress = true,
                                    isGroup = isLeader && model.isExpandable,
                                    isExpanded = isExpanded,
                                ),
                            )
                        },
                        onClickCover = onClickCover.takeIf { !selectionMode }?.let { { it(updatesItem) } },
                        onDownloadChapter = onDownloadChapter.takeIf { !selectionMode }?.let {
                            { action -> it(listOf(updatesItem), action) }
                        },
                        downloadStateProvider = updatesItem.downloadStateProvider,
                        downloadProgressProvider = updatesItem.downloadProgressProvider,
                        updateSwipeStartAction = updateSwipeStartAction,
                        updateSwipeEndAction = updateSwipeEndAction,
                        onUpdateSwipe = { onUpdateSwipe(updatesItem, it) },
                        isLeader = isLeader,
                        isExpandable = model.isExpandable,
                        expanded = isExpanded,
                        collapseToggle = { collapseToggle(updatesItem.update.groupByDateAndManga()) },
                        modifier = animated,
                    )
            }
        }
    }
}

@Composable
private fun UpdatesDateHeader(header: UpdatesUiModel.Header, modifier: Modifier = Modifier) {
    SectionHeader(
        relativeDateText(header.date),
        modifier.fillMaxWidth().padding(start = 32.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
        titleStyle = TsuzukiTheme.typography.footnote,
        titleColor = TsuzukiTheme.colors.text,
        titleTrailing = { ShellCaption("${header.mangaCount}") },
    )
}

@Composable
private fun UpdatesUiItem(
    update: UpdatesWithRelations,
    selected: Boolean,
    selectionMode: Boolean,
    shape: CornerBasedShape,
    divider: Boolean,
    onRead: () -> Unit,
    readProgress: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onClickCover: (() -> Unit)?,
    onDownloadChapter: ((ChapterDownloadAction) -> Unit)?,
    downloadStateProvider: () -> Download.State,
    downloadProgressProvider: () -> Int,
    updateSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    updateSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    onUpdateSwipe: (LibraryPreferences.ChapterSwipeAction) -> Unit,
    isLeader: Boolean,
    isExpandable: Boolean,
    expanded: Boolean,
    collapseToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = TsuzukiTheme.haptics
    val colors = TsuzukiTheme.colors
    val motion = TsuzukiTheme.motion
    val downloadState = downloadStateProvider()
    val swipeStart = shellSwipeAction(colors, updateSwipeStartAction, update.read, update.bookmark, downloadState) {
        onUpdateSwipe(updateSwipeStartAction)
    }
    val swipeEnd = shellSwipeAction(colors, updateSwipeEndAction, update.read, update.bookmark, downloadState) {
        onUpdateSwipe(updateSwipeEndAction)
    }
    SwipeableActionsBox(
        modifier = modifier,
        startActions = if (onDownloadChapter == null || motion.reduced) emptyList() else listOfNotNull(swipeStart),
        endActions = if (onDownloadChapter == null || motion.reduced) emptyList() else listOfNotNull(swipeEnd),
        swipeThreshold = swipeActionThreshold,
        backgroundUntilSwipeThreshold = Color.Transparent,
    ) {
        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = 20.dp)
                .readerOrigin()
                .clip(shape)
                .background(colors.grouped)
                .selectionFill(selected)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptics.lift()
                        onLongClick()
                    },
                )
            .then(if (divider) Modifier.insetDivider(64.dp) else Modifier)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (selectionMode) {
                SelectionMark(selected = selected, Modifier.size(24.dp))
            }
            if (isLeader) {
                Box(
                    Modifier.width(48.dp).clickable(enabled = onClickCover != null) { onClickCover?.invoke() },
                    contentAlignment = Alignment.Center,
                ) {
                    ShellCover(
                        update.coverData,
                        update.mangaTitle,
                        Modifier.width(40.dp),
                        fixedRatio = 2f / 3f,
                        hero = true,
                    )
                }
            } else {
                Spacer(Modifier.width(48.dp))
            }
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (isLeader) {
                    TsuzukiText(
                        text = update.mangaTitle,
                        modifier = Modifier.heroFlightLabel(update.mangaId),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TsuzukiTheme.typography.headline.copy(color = if (update.read) colors.secondary else colors.text),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!update.read) {
                        Box(Modifier.padding(end = 6.dp).size(7.dp).background(colors.accent, CircleShape))
                    }
                    if (update.bookmark) {
                        Icon(
                            TsuzukiIcons.BookmarkFilled,
                            contentDescription = stringResource(MR.strings.action_filter_bookmarked),
                            modifier = Modifier.padding(end = 4.dp).size(14.dp),
                            tint = colors.accent,
                        )
                    }
                    TsuzukiText(
                        text = update.chapterName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TsuzukiTheme.typography.subhead.copy(color = if (update.read) colors.secondary else colors.text),
                        modifier = Modifier.weight(1f, fill = false).clickable(onClick = onRead),
                    )
                    if (readProgress != null) {
                        ShellCaption(readProgress, Modifier.padding(start = 6.dp))
                    }
                }
            }
            if (isLeader && isExpandable) {
                Box(
                    Modifier.size(TsuzukiSize.touch).clickable(onClick = collapseToggle),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (expanded) TsuzukiIcons.ChevronUp else TsuzukiIcons.ChevronDown,
                        if (expanded) "Collapse chapters" else "Expand chapters",
                        Modifier.size(16.dp),
                        tint = colors.secondary,
                    )
                }
            }
            ShellDownloadIndicator(
                enabled = onDownloadChapter != null,
                downloadStateProvider = downloadStateProvider,
                downloadProgressProvider = downloadProgressProvider,
                onClick = { onDownloadChapter?.invoke(it) },
            )
        }
    }
}

@Composable
private fun ShellDownloadIndicator(
    enabled: Boolean,
    downloadStateProvider: () -> Download.State,
    downloadProgressProvider: () -> Int,
    onClick: (ChapterDownloadAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TsuzukiTheme.colors
    val state = downloadStateProvider()
    val progress = downloadProgressProvider()
    Box(modifier.size(TsuzukiSize.touch), contentAlignment = Alignment.Center) {
        val onClickLabel = when (state) {
            Download.State.NOT_DOWNLOADED -> stringResource(MR.strings.manga_download)
            Download.State.QUEUE, Download.State.DOWNLOADING -> stringResource(MR.strings.action_cancel)
            Download.State.DOWNLOADED -> stringResource(MR.strings.action_delete)
            Download.State.ERROR -> stringResource(MR.strings.action_retry)
        }
        val tapAction = when (state) {
            Download.State.NOT_DOWNLOADED, Download.State.ERROR -> ChapterDownloadAction.START
            Download.State.QUEUE, Download.State.DOWNLOADING -> ChapterDownloadAction.CANCEL
            Download.State.DOWNLOADED -> ChapterDownloadAction.DELETE
        }
        val tint = when (state) {
            Download.State.NOT_DOWNLOADED -> colors.secondary
            Download.State.QUEUE, Download.State.DOWNLOADING -> colors.accent
            Download.State.DOWNLOADED -> colors.success
            Download.State.ERROR -> colors.destructive
        }
        Box(
            Modifier.size(TsuzukiSize.touch).combinedClickable(enabled = enabled, onClickLabel = onClickLabel, onClick = { onClick(tapAction) }),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                Download.State.QUEUE, Download.State.DOWNLOADING -> {
                    val indeterminate = state == Download.State.QUEUE || progress == 0
                    Box(contentAlignment = Alignment.Center) {
                        ProgressMark(if (indeterminate) 0f else progress / 100f, onClickLabel)
                        Icon(TsuzukiIcons.ArrowDown, null, Modifier.size(11.dp), tint = tint)
                    }
                }
                Download.State.DOWNLOADED -> Icon(TsuzukiIcons.Trash2, onClickLabel, Modifier.size(23.dp), tint = colors.secondary)
                Download.State.ERROR -> Icon(TsuzukiIcons.CircleAlert, onClickLabel, Modifier.size(23.dp), tint = tint)
                Download.State.NOT_DOWNLOADED -> Icon(TsuzukiIcons.DownloadIcon, onClickLabel, Modifier.size(23.dp), tint = tint)
            }
        }
    }
}

private fun shellSwipeAction(
    colors: TsuzukiColors,
    action: LibraryPreferences.ChapterSwipeAction,
    read: Boolean,
    bookmark: Boolean,
    downloadState: Download.State,
    onSwipe: () -> Unit,
): SwipeAction? {
    val icon: ImageVector = when (action) {
        LibraryPreferences.ChapterSwipeAction.ToggleRead -> if (!read) TsuzukiIcons.CircleCheck else TsuzukiIcons.RotateCcw
        LibraryPreferences.ChapterSwipeAction.ToggleBookmark ->
            if (!bookmark) TsuzukiIcons.Bookmark else TsuzukiIcons.BookmarkFilled
        LibraryPreferences.ChapterSwipeAction.Download -> when (downloadState) {
            Download.State.NOT_DOWNLOADED, Download.State.ERROR -> TsuzukiIcons.DownloadIcon
            Download.State.QUEUE, Download.State.DOWNLOADING, Download.State.DOWNLOADED -> TsuzukiIcons.Trash2
        }
        LibraryPreferences.ChapterSwipeAction.Disabled -> return null
    }
    return SwipeAction(
        icon = {
            Icon(
                modifier = Modifier.padding(horizontal = 24.dp).size(22.dp),
                imageVector = icon,
                tint = colors.onAccent,
                contentDescription = null,
            )
        },
        background = colors.accent,
        onSwipe = onSwipe,
    )
}
