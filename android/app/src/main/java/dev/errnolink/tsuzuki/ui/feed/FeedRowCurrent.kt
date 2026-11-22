package dev.errnolink.tsuzuki.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.BrandColors
import dev.errnolink.tsuzuki.designsystem.Check
import dev.errnolink.tsuzuki.designsystem.CheckCheck
import dev.errnolink.tsuzuki.designsystem.ChevronDown
import dev.errnolink.tsuzuki.designsystem.ChevronUp
import dev.errnolink.tsuzuki.designsystem.Download
import dev.errnolink.tsuzuki.designsystem.M3Motion
import dev.errnolink.tsuzuki.designsystem.Play
import dev.errnolink.tsuzuki.designsystem.SwipeActionSpec
import dev.errnolink.tsuzuki.designsystem.SwipeEdge
import dev.errnolink.tsuzuki.designsystem.SwipeRow
import dev.errnolink.tsuzuki.designsystem.TsuzukiColors
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.reader.readerOrigin
import dev.errnolink.tsuzuki.ui.motion.heroFlightLabel
import dev.errnolink.tsuzuki.ui.shell.CoverTile
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal data class FeedSourceTag(val label: String, val background: Color, val foreground: Color)

internal fun feedSourceTag(
    group: FeedTitleGroup,
    colors: TsuzukiColors,
    mergedLabel: String = "Merged",
): FeedSourceTag? = when {
    group.mangaDexId == null -> null
    group.isLibrary -> FeedSourceTag(mergedLabel, colors.elevated, colors.secondary)
    else -> FeedSourceTag("MD", BrandColors.mangaDex.copy(alpha = 0.22f), BrandColors.mangaDex)
}

@Composable
internal fun feedDayText(day: LocalDate): String {
    val today = LocalDate.now()
    return when (day) {
        today -> stringResource(MR.strings.relative_time_today)
        today.minusDays(1) -> stringResource(MR.strings.tsuzuki_yesterday)
        else -> day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
}

internal fun feedDayStickyOffset(pinned: Boolean, clearance: Dp): Dp = if (pinned) clearance else 0.dp

internal fun stickyHeaderPinned(itemOffset: Int, beforeContentPadding: Int): Boolean =
    beforeContentPadding > 0 && itemOffset < beforeContentPadding

@Composable
internal fun FeedTimelineDayHeaderCurrent(
    day: LocalDate,
    unreadCount: Int,
    canMarkRead: Boolean,
    @Suppress("UNUSED_PARAMETER") pinned: Boolean,
    onMarkRead: () -> Unit,
) {
    val colors = TsuzukiTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TsuzukiText(
            feedDayText(day),
            style = TsuzukiTheme.typography.title3.copy(fontWeight = FontWeight.Bold),
            color = colors.text,
        )
        TsuzukiText(
            if (unreadCount > 0) stringResource(MR.strings.tsuzuki_n_new, unreadCount) else stringResource(MR.strings.tsuzuki_caught_up),
            style = TsuzukiTheme.typography.footnote,
            color = colors.secondary,
        )
        Spacer(Modifier.weight(1f))
        if (canMarkRead) {
            Box(
                Modifier
                    .heightIn(min = 44.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onMarkRead)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                TsuzukiText(
                    stringResource(MR.strings.tsuzuki_mark_read),
                    style = TsuzukiTheme.typography.footnote.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.accent,
                )
            }
        }
    }
}

@Composable
internal fun FeedTimelineCardCurrent(
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
    val colors = TsuzukiTheme.colors
    val motion = TsuzukiTheme.motion
    val sourceTag = feedSourceTag(group, colors, mergedLabel = stringResource(MR.strings.tsuzuki_merged))
    val metaSubtitle = feedGroupMetaSubtitle(group)
    val caps = feedCardSwipeCapabilities(group)
    val lead = leadChapter(group.chapters)
    val downloadTarget = downloadTargetChapter(group)
    val isMulti = group.chapters.size > 1
    val peekDepth = stackPeekDepth(group.chapters.size, expanded)
    val currentSwipeMarkRead by rememberUpdatedState(onSwipeMarkRead)
    val currentSwipeDownload by rememberUpdatedState(onSwipeDownload)
    val markReadLabel = stringResource(MR.strings.tsuzuki_mark_read)
    val downloadLabel = stringResource(MR.strings.tsuzuki_download)
    val downloadChapterLabel = stringResource(MR.strings.tsuzuki_download_chapter)
    val readChapterLabel = stringResource(MR.strings.tsuzuki_read_chapter)

    val startActions = if (caps.canMarkRead) {
        listOf(
            SwipeActionSpec(
                icon = TsuzukiIcons.CheckCheck,
                label = markReadLabel,
                background = colors.accent,
                onClick = { currentSwipeMarkRead() },
                full = true,
                keep = true,
            ),
        )
    } else {
        emptyList()
    }
    val endActions = if (caps.canDownload) {
        listOf(
            SwipeActionSpec(
                icon = TsuzukiIcons.Download,
                label = downloadLabel,
                background = colors.accent,
                onClick = { currentSwipeDownload() },
                full = true,
                keep = true,
            ),
        )
    } else {
        emptyList()
    }

    val cardShape = RoundedCornerShape(20.dp)
    val cardAlpha = if (group.unreadCount == 0) 0.56f else 1f
    val deckLipShape = RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)
    val deckDeepLipShape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp)
            .alpha(cardAlpha),
    ) {
        SwipeRow(
            actions = startActions,
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape),
            edge = SwipeEdge.Start,
            surface = colors.grouped,
        ) {
            SwipeRow(
                actions = endActions,
                modifier = Modifier.fillMaxWidth(),
                edge = SwipeEdge.End,
                surface = colors.grouped,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.grouped)
                        .border(1.dp, colors.separator, cardShape),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isMulti) {
                                    onToggleExpand()
                                } else if (lead != null) {
                                    onClickChapter(lead)
                                } else {
                                    onClickTitle()
                                }
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.clickable(onClick = onClickTitle)) {
                            CoverTile(
                                cover = MangaCover(
                                    mangaId = group.mangaId,
                                    sourceId = group.sourceId,
                                    isMangaFavorite = group.isLibrary,
                                    ogUrl = group.coverUrl,
                                    lastModified = group.coverLastModified,
                                ),
                                title = group.mangaTitle,
                                modifier = Modifier
                                    .width(54.dp)
                                    .height(76.dp),
                                shape = RoundedCornerShape(11.dp),
                                fixedRatio = 54f / 76f,
                            )
                            if (group.unreadCount > 1) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .clip(CircleShape)
                                        .background(colors.accent)
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    TsuzukiText(
                                        group.unreadCount.toString(),
                                        style = TsuzukiTheme.typography.caption2.copy(fontWeight = FontWeight.ExtraBold),
                                        color = colors.onAccent,
                                    )
                                }
                            }
                        }
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            TsuzukiText(
                                group.mangaTitle,
                                Modifier.heroFlightLabel(group.mangaId),
                                style = TsuzukiTheme.typography.subhead.copy(fontWeight = FontWeight.Bold),
                                color = colors.text,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (metaSubtitle != null || sourceTag != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    if (metaSubtitle != null) {
                                        TsuzukiText(
                                            metaSubtitle,
                                            Modifier.weight(1f, fill = false),
                                            style = TsuzukiTheme.typography.caption1,
                                            color = colors.secondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (sourceTag != null) {
                                        Box(
                                            Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(sourceTag.background)
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        ) {
                                            TsuzukiText(
                                                sourceTag.label,
                                                style = TsuzukiTheme.typography.caption2.copy(fontWeight = FontWeight.Bold),
                                                color = sourceTag.foreground,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                            }
                            if (isMulti) {
                                Row(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (expanded) colors.accent else colors.accent.copy(alpha = 0.16f))
                                        .clickable(role = Role.Button, onClick = onToggleExpand)
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    TsuzukiText(
                                        if (expanded) {
                                            stringResource(MR.strings.tsuzuki_collapse)
                                        } else {
                                            stringResource(MR.strings.tsuzuki_n_chapters_count, group.chapters.size)
                                        },
                                        style = TsuzukiTheme.typography.caption1.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (expanded) colors.onAccent else colors.accent,
                                        maxLines = 1,
                                    )
                                    Icon(
                                        if (expanded) TsuzukiIcons.ChevronUp else TsuzukiIcons.ChevronDown,
                                        contentDescription = null,
                                        tint = if (expanded) colors.onAccent else colors.accent,
                                        modifier = Modifier.size(13.dp),
                                    )
                                }
                            } else if (lead != null) {
                                TsuzukiText(
                                    feedChapterRowLabel(lead),
                                    style = TsuzukiTheme.typography.caption1.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (lead.isRead) colors.secondary else colors.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(colors.elevated)
                                    .alpha(if (downloadTarget != null) 1f else 0.32f)
                                    .clickable(enabled = downloadTarget != null, role = Role.Button) {
                                        downloadTarget?.let(onDownloadChapter)
                                    }
                                    .semantics { contentDescription = downloadChapterLabel },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    TsuzukiIcons.Download,
                                    contentDescription = null,
                                    tint = colors.secondary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            Box(
                                Modifier
                                    .readerOrigin()
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(colors.accent)
                                    .clickable(role = Role.Button) {
                                        if (lead != null) onClickChapter(lead) else onClickTitle()
                                    }
                                    .semantics { contentDescription = readChapterLabel },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    TsuzukiIcons.Play,
                                    contentDescription = null,
                                    tint = colors.onAccent,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = isMulti && expanded,
                        enter = if (motion.reduced) {
                            EnterTransition.None
                        } else {
                            expandVertically(tween(M3Motion.Short4)) + fadeIn(tween(M3Motion.Short3))
                        },
                        exit = if (motion.reduced) {
                            ExitTransition.None
                        } else {
                            shrinkVertically(tween(M3Motion.Short4)) + fadeOut(tween(M3Motion.Short2))
                        },
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Spacer(
                                Modifier
                                    .fillMaxWidth()
                                    .height(TsuzukiTheme.spacing.hairline)
                                    .background(colors.separator),
                            )
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                group.chapters.forEach { chapter ->
                                    FeedDrawerChapterRow(
                                        chapter = chapter,
                                        onOpen = { onClickChapter(chapter) },
                                        onMarkRead = { onMarkChapterRead(chapter) },
                                        onDownload = { onDownloadChapter(chapter) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (peekDepth >= 1) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .height(7.dp)
                    .clip(deckLipShape)
                    .background(colors.grouped.copy(alpha = 0.72f))
                    .border(1.dp, colors.separator, deckLipShape),
            )
        }
        if (peekDepth >= 2) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(6.dp)
                    .clip(deckDeepLipShape)
                    .background(colors.grouped.copy(alpha = 0.46f))
                    .border(1.dp, colors.separator, deckDeepLipShape),
            )
        }
    }
}

@Composable
private fun FeedDrawerChapterRow(
    chapter: FeedChapterItem,
    onOpen: () -> Unit,
    onMarkRead: () -> Unit,
    onDownload: () -> Unit,
) {
    val colors = TsuzukiTheme.colors
    val markReadEnabled = canMarkChapterRead(chapter)
    val downloadEnabled = canDownloadChapter(chapter)
    val markReadLabel = stringResource(MR.strings.tsuzuki_mark_read)
    val downloadChapterLabel = stringResource(MR.strings.tsuzuki_download_chapter)
    val readChapterLabel = stringResource(MR.strings.tsuzuki_read_chapter)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.elevated)
            .alpha(if (chapter.isRead) 0.56f else 1f)
            .clickable(onClick = onOpen)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            TsuzukiText(
                feedChapterRowLabel(chapter),
                style = TsuzukiTheme.typography.footnote.copy(fontWeight = FontWeight.SemiBold),
                color = if (chapter.isRead) colors.secondary else colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val scanlator = chapter.scanlator?.takeIf { it.isNotBlank() }
            if (scanlator != null) {
                TsuzukiText(
                    scanlator,
                    style = TsuzukiTheme.typography.caption2,
                    color = colors.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(colors.grouped)
                .alpha(if (markReadEnabled) 1f else 0.32f)
                .clickable(enabled = markReadEnabled, role = Role.Button, onClick = onMarkRead)
                .semantics { contentDescription = markReadLabel },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                TsuzukiIcons.Check,
                contentDescription = null,
                tint = if (chapter.isRead) colors.accent else colors.secondary,
                modifier = Modifier.size(14.dp),
            )
        }
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(colors.grouped)
                .alpha(if (downloadEnabled) 1f else 0.32f)
                .clickable(enabled = downloadEnabled, role = Role.Button, onClick = onDownload)
                .semantics { contentDescription = downloadChapterLabel },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                TsuzukiIcons.Download,
                contentDescription = null,
                tint = colors.secondary,
                modifier = Modifier.size(14.dp),
            )
        }
        Box(
            Modifier
                .readerOrigin()
                .size(32.dp)
                .clip(CircleShape)
                .background(colors.accent.copy(alpha = 0.16f))
                .clickable(role = Role.Button, onClick = onOpen)
                .semantics { contentDescription = readChapterLabel },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                TsuzukiIcons.Play,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}
