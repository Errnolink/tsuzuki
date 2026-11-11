package dev.errnolink.tsuzuki.ui.details

import eu.kanade.presentation.manga.components.ChapterDownloadAction
import eu.kanade.presentation.util.formatChapterNumber
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import dev.errnolink.tsuzuki.designsystem.Bookmark
import dev.errnolink.tsuzuki.designsystem.BookmarkFilled
import dev.errnolink.tsuzuki.designsystem.Check
import dev.errnolink.tsuzuki.designsystem.Download
import dev.errnolink.tsuzuki.designsystem.Trash2
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.X
import dev.errnolink.tsuzuki.designsystem.ChevronDown
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.download.model.Download
import me.saket.swipe.SwipeableActionsBox
import tachiyomi.domain.library.service.LibraryPreferences

/**
 * In-progress progress text ("Page X" / "Page X of Y") and bar fraction for a chapter row.
 * [lastPageRead] follows the domain convention (0-based last viewed page); null hides progress.
 */
data class ChapterRowProgress(val label: String, val fraction: Float?)

internal fun chapterRowProgress(lastPageRead: Long?, pageTotal: Int?): ChapterRowProgress? {
    val read = lastPageRead?.takeIf { it > 0 } ?: return null
    if (pageTotal == null || pageTotal <= 0) return ChapterRowProgress("Page ${read + 1}", null)
    val fraction = ((read + 1).coerceAtMost(pageTotal.toLong()).toFloat() / pageTotal).coerceIn(0f, 1f)
    return ChapterRowProgress("Page ${read + 1} of $pageTotal", fraction)
}

internal val chapterNameNumberPrefix = Regex("""^(?i)ch(apter)?\.?\s*([0-9]+(\.[0-9]+)?)\b(.*)$""")

internal fun chapterRowTitle(name: String, number: Double, displayByNumber: Boolean): String {
    if (displayByNumber) return "Chapter ${formatChapterNumber(number)}"
    val match = chapterNameNumberPrefix.find(name) ?: return name
    return "Ch. ${formatChapterNumber(match.groupValues[2].toDouble())}${match.groupValues[4]}"
}

internal fun chapterRowSubtitle(
    sourceName: String?,
    unavailable: Boolean,
    languageLabel: String?,
    progress: ChapterRowProgress?,
    date: String?,
    scanlator: String?,
): String = listOfNotNull(
    sourceName?.takeIf { it.isNotBlank() },
    if (unavailable) "Unavailable" else null,
    languageLabel,
    if (unavailable) null else progress?.label,
    date,
    scanlator?.takeIf { it.isNotBlank() },
).joinToString(" · ")

@Composable
fun MangaChapterListItem(
    title: String,
    date: String?,
    lastPageRead: Long?,
    pageTotal: Int?,
    scanlator: String?,
    // SY -->
    sourceName: String?,
    // SY <--
    read: Boolean,
    bookmark: Boolean,
    selected: Boolean,
    downloadIndicatorEnabled: Boolean,
    downloadStateProvider: () -> Download.State,
    downloadProgressProvider: () -> Int,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onDownloadClick: ((ChapterDownloadAction) -> Unit)?,
    onChapterSwipe: (LibraryPreferences.ChapterSwipeAction) -> Unit,
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = RoundedCornerShape(TsuzukiCorners.cover),
    actions: (@Composable () -> Unit)? = null,
    unavailable: Boolean = false,
    languageLabel: String? = null,
    isCurrent: Boolean = false,
    nested: Boolean = false,
    otherGroups: Int = 0,
) {
    val colors = TsuzukiTheme.colors
    val state = downloadStateProvider()
    val progress = remember(lastPageRead, pageTotal) { chapterRowProgress(lastPageRead, pageTotal) }
    val inProgress = !read && progress != null
    val swipeStart = remember(chapterSwipeStartAction, read, bookmark, state, colors) {
        getSwipeAction(chapterSwipeStartAction, read, bookmark, state, colors.accent, colors.destructive) { onChapterSwipe(chapterSwipeStartAction) }
    }
    val swipeEnd = remember(chapterSwipeEndAction, read, bookmark, state, colors) {
        getSwipeAction(chapterSwipeEndAction, read, bookmark, state, colors.accent, colors.destructive) { onChapterSwipe(chapterSwipeEndAction) }
    }
    SwipeableActionsBox(
        modifier = modifier.clip(shape),
        startActions = listOfNotNull(swipeStart), endActions = listOfNotNull(swipeEnd),
        swipeThreshold = swipeActionThreshold, backgroundUntilSwipeThreshold = colors.grouped,
    ) {
        Column(
            Modifier.background(if (selected) colors.accent.copy(alpha = 0.12f) else colors.grouped)
                .drawBehind { if (isCurrent) drawRect(colors.accent, size = Size(3.dp.toPx(), size.height)) }
                .semantics {
                    this.selected = selected
                    if (isCurrent) stateDescription = "Reading now"
                    customActions = listOfNotNull(
                        swipeStart?.let {
                            CustomAccessibilityAction(
                                swipeActionLabel(chapterSwipeStartAction, read, bookmark, state),
                            ) { onChapterSwipe(chapterSwipeStartAction); true }
                        },
                        swipeEnd?.let {
                            CustomAccessibilityAction(
                                swipeActionLabel(chapterSwipeEndAction, read, bookmark, state),
                            ) { onChapterSwipe(chapterSwipeEndAction); true }
                        },
                    )
                }
                .combinedClickable(
                    role = androidx.compose.ui.semantics.Role.Button,
                    onClickLabel = stringResource(MR.strings.tsuzuki_open_chapter),
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(
                    start = if (nested) 32.dp else 16.dp,
                    end = 6.dp,
                    top = TsuzukiTheme.spacing.small,
                    bottom = TsuzukiTheme.spacing.small,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TsuzukiTheme.spacing.tiny),
            ) {
                Column(
                    Modifier.weight(1f).alpha(when { unavailable -> 0.45f; read -> 0.6f; else -> 1f }),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TsuzukiTheme.spacing.small)) {
                        if (selected) {
                            Icon(TsuzukiIcons.Check, "Selected", Modifier.size(18.dp), tint = colors.accent)
                        } else if (!read) {
                            Box(Modifier.size(7.dp).background(colors.accent, CircleShape))
                        }
                        TsuzukiText(title, style = TsuzukiTheme.typography.callout, color = colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TsuzukiText(
                            chapterRowSubtitle(sourceName, unavailable, languageLabel, progress, date, scanlator),
                            Modifier.weight(1f, fill = false),
                            style = TsuzukiTheme.typography.caption1,
                            color = colors.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (otherGroups > 0) {
                            TsuzukiText(
                                " +$otherGroups",
                                style = TsuzukiTheme.typography.caption1,
                                color = colors.secondary,
                                maxLines = 1,
                            )
                        }
                    }
                    if (inProgress) {
                        val fraction = progress?.fraction
                        if (fraction != null) {
                            Box(
                                Modifier
                                    .padding(top = 3.dp)
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .background(colors.elevated, RoundedCornerShape(1.5.dp)),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(fraction)
                                        .fillMaxHeight()
                                        .background(colors.accent, RoundedCornerShape(1.5.dp)),
                                )
                            }
                        }
                    }
                }
                if (bookmark) Icon(TsuzukiIcons.Bookmark, "Bookmarked", Modifier.size(16.dp), tint = colors.accent)
                actions?.invoke()
                ChapterDownloadIndicator(
                    enabled = downloadIndicatorEnabled && onDownloadClick != null,
                    downloadStateProvider = downloadStateProvider,
                    downloadProgressProvider = downloadProgressProvider,
                    onClick = { onDownloadClick?.invoke(it) },
                )
            }
            Spacer(Modifier.padding(start = if (nested) 32.dp else 16.dp).fillMaxWidth().height(TsuzukiTheme.spacing.hairline).background(colors.separator))
        }
    }
}

@Composable
fun ChapterGroupToggle(open: Boolean, alternates: Int, onToggle: () -> Unit) {
    val rotation by androidx.compose.animation.core.animateFloatAsState(
        if (open) 180f else 0f,
        if (TsuzukiTheme.motion.reduced) androidx.compose.animation.core.snap() else TsuzukiTheme.motion.tabSpring(),
        label = "chapter group chevron",
    )
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .combinedClickable(onClick = onToggle)
            .semantics {
                stateDescription = if (open) "Expanded" else "Collapsed"
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            TsuzukiIcons.ChevronDown,
            if (open) "Hide other groups" else "Show $alternates other ${if (alternates == 1) "group" else "groups"}",
            Modifier.size(18.dp).graphicsLayer { rotationZ = rotation },
            tint = TsuzukiTheme.colors.secondary,
        )
    }
}

internal fun swipeActionLabel(
    action: LibraryPreferences.ChapterSwipeAction,
    read: Boolean,
    bookmark: Boolean,
    downloadState: Download.State,
): String = when (action) {
    LibraryPreferences.ChapterSwipeAction.ToggleRead -> if (!read) "Mark as read" else "Mark as unread"
    LibraryPreferences.ChapterSwipeAction.ToggleBookmark -> if (!bookmark) "Bookmark" else "Remove bookmark"
    LibraryPreferences.ChapterSwipeAction.Download -> when (downloadState) {
        Download.State.QUEUE, Download.State.DOWNLOADING -> "Cancel download"
        Download.State.DOWNLOADED -> "Delete download"
        else -> "Download"
    }
    LibraryPreferences.ChapterSwipeAction.Disabled -> ""
}

internal fun getSwipeAction(
    action: LibraryPreferences.ChapterSwipeAction,
    read: Boolean,
    bookmark: Boolean,
    downloadState: Download.State,
    accent: Color,
    destructive: Color,
    onSwipe: () -> Unit,
): me.saket.swipe.SwipeAction? {
    if (action == LibraryPreferences.ChapterSwipeAction.Disabled) return null
    val label = swipeActionLabel(action, read, bookmark, downloadState)
    return when (action) {
        LibraryPreferences.ChapterSwipeAction.ToggleRead -> swipeAction(
            label,
            icon = if (!read) TsuzukiIcons.Check else TsuzukiIcons.X,
            background = accent,
            isUndo = read,
            onSwipe = onSwipe,
        )
        LibraryPreferences.ChapterSwipeAction.ToggleBookmark -> swipeAction(
            label,
            icon = if (!bookmark) TsuzukiIcons.Bookmark else TsuzukiIcons.BookmarkFilled,
            background = accent,
            isUndo = bookmark,
            onSwipe = onSwipe,
        )
        LibraryPreferences.ChapterSwipeAction.Download -> swipeAction(
            label,
            icon = when (downloadState) {
                Download.State.NOT_DOWNLOADED, Download.State.ERROR -> TsuzukiIcons.Download
                Download.State.QUEUE, Download.State.DOWNLOADING -> TsuzukiIcons.X
                Download.State.DOWNLOADED -> TsuzukiIcons.Trash2
            },
            background = when (downloadState) {
                Download.State.QUEUE, Download.State.DOWNLOADING, Download.State.DOWNLOADED -> destructive
                else -> accent
            },
            onSwipe = onSwipe,
        )
        LibraryPreferences.ChapterSwipeAction.Disabled -> null
    }
}

internal fun swipeAction(
    label: String,
    onSwipe: () -> Unit,
    icon: ImageVector,
    background: Color,
    isUndo: Boolean = false,
): me.saket.swipe.SwipeAction {
    return me.saket.swipe.SwipeAction(
        icon = {
            Icon(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .size(IndicatorSize),
                imageVector = icon,
                tint = Color.White,
                contentDescription = label,
            )
        },
        background = background,
        onSwipe = onSwipe,
        isUndo = isUndo,
    )
}

internal val swipeActionThreshold = 56.dp
