package dev.errnolink.tsuzuki.ui.feed

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.errnolink.tsuzuki.designsystem.BrandColors
import dev.errnolink.tsuzuki.designsystem.CheckCheck
import dev.errnolink.tsuzuki.designsystem.Download
import dev.errnolink.tsuzuki.designsystem.M3Motion
import dev.errnolink.tsuzuki.designsystem.Play
import dev.errnolink.tsuzuki.designsystem.TsuzukiColors
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.TsuzukiTopBar
import dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior
import dev.errnolink.tsuzuki.mangadex.feedDayLabel
import dev.errnolink.tsuzuki.reader.readerOrigin
import dev.errnolink.tsuzuki.ui.details.swipeAction
import dev.errnolink.tsuzuki.ui.shell.ShellCover
import me.saket.swipe.SwipeableActionsBox
import tachiyomi.domain.manga.model.MangaCover
import java.time.LocalDate

private val FeedRailX = 23.dp
private val FeedRailStroke = 2.dp
private val FeedConnectorLength = 14.dp
private val FeedCardIndent = 40.dp
private val FeedCardBottomSpacing = 10.dp

internal data class FeedSourceTag(val label: String, val background: Color, val foreground: Color)

internal fun feedSourceTag(group: FeedTitleGroup, colors: TsuzukiColors): FeedSourceTag? = when {
    group.mangaDexId == null -> null
    group.isLibrary -> FeedSourceTag("Merged", colors.elevated, colors.secondary)
    else -> FeedSourceTag("MD", BrandColors.mangaDex.copy(alpha = 0.22f), BrandColors.mangaDex)
}

internal fun feedDayStickyOffset(pinned: Boolean, clearance: Dp): Dp = if (pinned) clearance else 0.dp

@Composable
internal fun FeedContinueShelf(
    cards: List<UpNextCard>,
    onOpenHistory: () -> Unit,
    onContinue: (UpNextCard) -> Unit,
) {
    val colors = TsuzukiTheme.colors
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TsuzukiText(
                "CONTINUE",
                style = TsuzukiTheme.typography.caption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                color = colors.secondary,
            )
            Box(
                Modifier.heightIn(min = 44.dp).clickable(onClick = onOpenHistory),
                contentAlignment = Alignment.Center,
            ) {
                TsuzukiText(
                    "History",
                    style = TsuzukiTheme.typography.footnote.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.accent,
                )
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Bottom,
            flingBehavior = tsuzukiFlingBehavior(),
        ) {
            itemsIndexed(cards, key = { _, card -> "continue-${card.mangaId}" }) { index, card ->
                FeedContinueTile(card = card, lead = index == 0, onClick = { onContinue(card) })
            }
        }
    }
}

private val FeedContinueCoverWidth = 120.dp

@Composable
private fun FeedContinueTile(
    card: UpNextCard,
    lead: Boolean,
    onClick: () -> Unit,
) {
    val colors = TsuzukiTheme.colors
    Column(Modifier.width(FeedContinueCoverWidth).readerOrigin().clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp)),
        ) {
            ShellCover(
                cover = card.coverData,
                title = card.mangaTitle,
                modifier = Modifier.fillMaxWidth(),
            )
            if (lead) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(TsuzukiIcons.Play, contentDescription = null, tint = Color(0xFF111111), modifier = Modifier.size(11.dp))
                    TsuzukiText(
                        card.chapterName,
                        style = TsuzukiTheme.typography.caption1.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF111111),
                        maxLines = 1,
                    )
                }
            }
        }
        TsuzukiText(
            card.mangaTitle,
            modifier = Modifier.padding(top = 8.dp),
            style = TsuzukiTheme.typography.subhead.copy(fontWeight = FontWeight.Bold),
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TsuzukiText(
            if (lead) continueSubtitle(card) else card.chapterName,
            modifier = Modifier.padding(top = 2.dp),
            style = TsuzukiTheme.typography.caption1,
            color = colors.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        FeedProgressBar(card.progressFraction, Modifier.padding(top = 6.dp))
    }
}

internal fun continueSubtitle(card: UpNextCard): String =
    if (card.pageTotal != null && card.pageTotal > 0) {
        "page ${(card.lastPageRead + 1).coerceAtMost(card.pageTotal.toLong())} of ${card.pageTotal}"
    } else {
        card.chapterName
    }

@Composable
private fun FeedProgressBar(fraction: Float?, modifier: Modifier = Modifier) {
    val colors = TsuzukiTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(colors.separator),
    ) {
        Box(
            Modifier
                .fillMaxWidth((fraction ?: 0f).coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(colors.accent),
        )
    }
}

@Composable
internal fun FeedTimelineDayHeader(
    day: LocalDate,
    groupCount: Int,
    chapterCount: Int,
    pinned: Boolean,
    onMarkRead: () -> Unit,
) {
    val colors = TsuzukiTheme.colors
    val motion = TsuzukiTheme.motion
    val clearance = TsuzukiTopBar.bodyHeightDp.dp
    val offset by animateDpAsState(
        targetValue = feedDayStickyOffset(pinned, clearance),
        animationSpec = if (motion.reduced) snap() else tween(M3Motion.Short4, easing = M3Motion.Standard),
        label = "feed-day-sticky",
    )
    val isToday = day == LocalDate.now()
    Box(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val bottom = offset.toPx() + size.height
                drawRect(colors.background, size = Size(size.width, bottom))
                drawLine(
                    colors.separator,
                    start = Offset(FeedRailX.toPx(), 0f),
                    end = Offset(FeedRailX.toPx(), bottom),
                    strokeWidth = FeedRailStroke.toPx(),
                )
            },
    ) {
        Row(
            modifier = Modifier
                .offset(y = offset)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                if (isToday) {
                    Box(
                        Modifier.size(24.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(16.dp).clip(CircleShape).background(colors.accent))
                    }
                } else {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(colors.secondary))
                }
            }
            TsuzukiText(
                feedDayLabel(day),
                modifier = Modifier.padding(start = 8.dp),
                style = TsuzukiTheme.typography.title3.copy(fontWeight = FontWeight.Bold),
                color = colors.text,
            )
            TsuzukiText(
                "$groupCount titles · $chapterCount ch.",
                modifier = Modifier.padding(start = 10.dp),
                style = TsuzukiTheme.typography.caption1,
                color = colors.secondary,
            )
            Box(Modifier.weight(1f))
            Box(
                Modifier.heightIn(min = 44.dp).clickable(onClick = onMarkRead),
                contentAlignment = Alignment.Center,
            ) {
                TsuzukiText(
                    "Mark read",
                    style = TsuzukiTheme.typography.footnote.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.accent,
                )
            }
        }
    }
}

@Composable
internal fun FeedTimelineCard(
    group: FeedTitleGroup,
    onClickTitle: () -> Unit,
    onClickChapter: (FeedChapterItem) -> Unit,
    onSwipeMarkRead: () -> Unit,
    onSwipeDownload: () -> Unit,
) {
    val colors = TsuzukiTheme.colors
    val faded = group.unreadCount == 0
    val sourceTag = feedSourceTag(group, colors)
    Box(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    colors.separator,
                    start = Offset(FeedRailX.toPx(), 0f),
                    end = Offset(FeedRailX.toPx(), size.height),
                    strokeWidth = FeedRailStroke.toPx(),
                )
                val centerY = (size.height - FeedCardBottomSpacing.toPx()) / 2f
                drawLine(
                    colors.separator,
                    start = Offset(FeedRailX.toPx(), centerY),
                    end = Offset((FeedRailX + FeedConnectorLength).toPx(), centerY),
                    strokeWidth = FeedRailStroke.toPx(),
                )
            },
    ) {
        SwipeableActionsBox(
            modifier = Modifier
                .padding(start = FeedCardIndent, end = 16.dp, bottom = FeedCardBottomSpacing)
                .clip(RoundedCornerShape(18.dp))
                .alpha(if (faded) 0.5f else 1f),
            startActions = listOf(
                swipeAction(
                    label = "Mark read",
                    icon = TsuzukiIcons.CheckCheck,
                    background = colors.accent,
                    onSwipe = onSwipeMarkRead,
                ),
            ),
            endActions = listOf(
                swipeAction(
                    label = "Download",
                    icon = TsuzukiIcons.Download,
                    background = colors.accent,
                    onSwipe = onSwipeDownload,
                ),
            ),
            swipeThreshold = 56.dp,
            backgroundUntilSwipeThreshold = colors.grouped,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .readerOrigin()
                    .background(colors.grouped)
                    .clickable(onClick = onClickTitle)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ShellCover(
                    cover = MangaCover(
                        mangaId = group.mangaId,
                        sourceId = group.sourceId,
                        isMangaFavorite = group.isLibrary,
                        ogUrl = group.coverUrl,
                        lastModified = group.coverLastModified,
                    ),
                    title = group.mangaTitle,
                    modifier = Modifier.width(56.dp).height(80.dp).clip(RoundedCornerShape(10.dp)),
                    fixedRatio = 56f / 80f,
                )
                Column(Modifier.weight(1f)) {
                    TsuzukiText(
                        group.mangaTitle,
                        style = TsuzukiTheme.typography.subhead.copy(fontWeight = FontWeight.Bold),
                        color = colors.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    FlowRow(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        feedChapterChips(group.chapters).forEach { chip ->
                            FeedChapterChipPill(
                                chip = chip,
                                onClick = chip.chapter?.let { target -> { onClickChapter(target) } } ?: onClickTitle,
                            )
                        }
                    }
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (group.unreadCount > 0) {
                        Box(
                            Modifier.clip(CircleShape).background(colors.accent).padding(horizontal = 7.dp, vertical = 3.dp),
                        ) {
                            TsuzukiText(
                                group.unreadCount.toString(),
                                style = TsuzukiTheme.typography.caption2.copy(fontWeight = FontWeight.ExtraBold),
                                color = colors.onAccent,
                            )
                        }
                    }
                    if (sourceTag != null) {
                        Box(
                            Modifier.clip(RoundedCornerShape(6.dp)).background(sourceTag.background).padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            TsuzukiText(
                                sourceTag.label,
                                style = TsuzukiTheme.typography.caption2.copy(fontWeight = FontWeight.Bold),
                                color = sourceTag.foreground,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedChapterChipPill(
    chip: FeedChapterChip,
    onClick: () -> Unit,
) {
    val colors = TsuzukiTheme.colors
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (chip.unread) colors.accent.copy(alpha = 0.2f) else colors.elevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        TsuzukiText(
            chip.label,
            style = TsuzukiTheme.typography.caption1.copy(fontWeight = FontWeight.SemiBold),
            color = if (chip.unread) colors.accent else colors.secondary,
            maxLines = 1,
        )
    }
}
