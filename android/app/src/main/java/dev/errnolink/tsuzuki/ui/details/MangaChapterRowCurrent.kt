package dev.errnolink.tsuzuki.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import dev.errnolink.tsuzuki.designsystem.SwipeActionSpec
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.Bookmark
import dev.errnolink.tsuzuki.designsystem.SelectionMark
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.selectionFill
import eu.kanade.presentation.manga.components.ChapterDownloadAction
import eu.kanade.tachiyomi.data.download.model.Download
import me.saket.swipe.SwipeAction
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

internal fun getSwipeAction(
    action: LibraryPreferences.ChapterSwipeAction,
    read: Boolean,
    bookmark: Boolean,
    downloadState: Download.State,
    accent: Color,
    destructive: Color,
    strings: ChapterRowStrings,
    onSwipe: () -> Unit,
): SwipeActionSpec? {
    val visual = swipeActionVisual(action, read, bookmark, downloadState, accent, destructive, strings) ?: return null
    return SwipeActionSpec(
        icon = visual.icon,
        label = visual.label,
        background = visual.background,
        onClick = onSwipe,
        // The configured action is the full-swipe action and runs in place (mark read/bookmark
        // toggle; the download action resolves from the menu-equivalent swipe handler).
        full = true,
        keep = true,
    )
}

internal fun swipeAction(
    label: String,
    onSwipe: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    background: Color,
    isUndo: Boolean = false,
): SwipeAction {
    return SwipeAction(
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

/** Room the leading unread dot takes, so read and unread titles align and separators share one inset. */
internal val ChapterDotSlot = 18.dp

/** The current chapter's soft accent wash over its grouped row. */
internal val CurrentChapterFill = 0.12f

/**
 * The Kasane grouped chapter row: title 17 sp medium on one line, meta 13 sp secondary
 * (scanlator · date · progress), a leading 8 dp accent dot for unread chapters and a 32 dp
 * quiet download circle. It lives inside the inset grouped container the list draws around it.
 */
@Composable
internal fun CurrentChapterRowContent(
    title: String,
    date: String?,
    scanlator: String?,
    read: Boolean,
    bookmark: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    downloadIndicatorEnabled: Boolean,
    downloadStateProvider: () -> Download.State,
    downloadProgressProvider: () -> Int,
    onDownloadClick: ((ChapterDownloadAction) -> Unit)?,
    unavailable: Boolean,
    isCurrent: Boolean,
    nested: Boolean,
    otherGroups: Int,
    showDivider: Boolean = true,
    progress: ChapterRowProgress?,
    actions: (@Composable () -> Unit)?,
    chapterSwipeStartAction: LibraryPreferences.ChapterSwipeAction,
    chapterSwipeEndAction: LibraryPreferences.ChapterSwipeAction,
    onChapterSwipe: (LibraryPreferences.ChapterSwipeAction) -> Unit,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
) {
    val colors = TsuzukiTheme.colors
    val strings = chapterRowStrings()
    val readingNowLabel = stringResource(MR.strings.tsuzuki_reading_now)
    val startPad = if (nested) 40.dp else 16.dp
    Column(
        Modifier.background(colors.grouped)
            .selectionFill(selected)
            .drawBehind { if (isCurrent) drawRect(colors.accent.copy(alpha = CurrentChapterFill)) }
            .semantics {
                this.selected = selected
                if (isCurrent) stateDescription = readingNowLabel
                customActions = listOfNotNull(
                    swipeActionLabel(chapterSwipeStartAction, read, bookmark, downloadStateProvider(), strings).takeIf { chapterSwipeStartAction != LibraryPreferences.ChapterSwipeAction.Disabled }
                        ?.let { label -> CustomAccessibilityAction(label) { onChapterSwipe(chapterSwipeStartAction); true } },
                    swipeActionLabel(chapterSwipeEndAction, read, bookmark, downloadStateProvider(), strings).takeIf { chapterSwipeEndAction != LibraryPreferences.ChapterSwipeAction.Disabled }
                        ?.let { label -> CustomAccessibilityAction(label) { onChapterSwipe(chapterSwipeEndAction); true } },
                )
            }
            .combinedClickable(
                role = Role.Button,
                onClickLabel = stringResource(MR.strings.tsuzuki_open_chapter),
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(
                start = startPad,
                end = 10.dp,
                top = 10.dp,
                bottom = 10.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (selectionMode) {
                SelectionMark(selected = selected, Modifier.size(24.dp))
            }
            if (!selectionMode) {
                // 8 dp accent dot for unread; read rows keep the slot empty so titles align.
                Box(Modifier.size(ChapterDotSlot), contentAlignment = Alignment.Center) {
                    if (!read) {
                        Box(Modifier.size(8.dp).background(colors.accent, CircleShape))
                    }
                }
            }
            Column(
                Modifier.weight(1f).alpha(when { unavailable -> 0.45f; else -> 1f }),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                TsuzukiText(
                    title,
                    style = TsuzukiTheme.typography.body.copy(fontWeight = FontWeight.Medium),
                    color = if (read) colors.secondary else colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TsuzukiText(
                        chapterRowMeta(scanlator, date, progress, unavailable, strings.unavailable),
                        Modifier.weight(1f, fill = false),
                        style = TsuzukiTheme.typography.footnote,
                        color = colors.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (otherGroups > 0) {
                        TsuzukiText(
                            " +$otherGroups",
                            style = TsuzukiTheme.typography.footnote,
                            color = colors.secondary,
                            maxLines = 1,
                        )
                    }
                }
            }
            if (bookmark) Icon(TsuzukiIcons.Bookmark, stringResource(MR.strings.tsuzuki_bookmarked), Modifier.size(16.dp), tint = colors.accent)
            actions?.invoke()
            ChapterDownloadIndicator(
                enabled = downloadIndicatorEnabled && onDownloadClick != null,
                downloadStateProvider = downloadStateProvider,
                downloadProgressProvider = downloadProgressProvider,
                onClick = { onDownloadClick?.invoke(it) },
                quiet = true,
            )
        }
        if (showDivider) {
            Spacer(
                Modifier
                    .padding(start = startPad + if (selectionMode) 24.dp + 10.dp else ChapterDotSlot + 10.dp)
                    .fillMaxWidth()
                    .height(TsuzukiTheme.spacing.hairline)
                    .background(colors.separator),
            )
        }
    }
}
