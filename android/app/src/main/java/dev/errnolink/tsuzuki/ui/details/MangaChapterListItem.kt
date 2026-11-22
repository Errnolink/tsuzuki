package dev.errnolink.tsuzuki.ui.details

import eu.kanade.presentation.manga.components.ChapterDownloadAction
import eu.kanade.presentation.util.formatChapterNumber
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import androidx.compose.runtime.Immutable
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.Bookmark
import dev.errnolink.tsuzuki.designsystem.BookmarkFilled
import dev.errnolink.tsuzuki.designsystem.Check
import dev.errnolink.tsuzuki.designsystem.ChevronDown
import dev.errnolink.tsuzuki.designsystem.Download as DownloadIcon
import dev.errnolink.tsuzuki.designsystem.Trash2
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.minimumTouchTarget
import dev.errnolink.tsuzuki.designsystem.X
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import androidx.compose.ui.draw.clip
import eu.kanade.tachiyomi.data.download.model.Download
import dev.errnolink.tsuzuki.designsystem.SwipeActionSpec
import dev.errnolink.tsuzuki.designsystem.SwipeEdge
import dev.errnolink.tsuzuki.designsystem.SwipeRow
import tachiyomi.domain.library.service.LibraryPreferences

/**
 * In-progress progress text for a chapter row: Kasane's "p. N" ([lastPageRead] follows the domain
 * convention — 0-based last viewed page); null hides progress.
 */
data class ChapterRowProgress(val label: String)

/** The localized words the pure chapter-row builders compose from; resolved once per screen. */
@Immutable
data class ChapterRowStrings(
    val pageProgressPattern: String,
    val chapterNumberPattern: String,
    val chapterShortPattern: String,
    val unavailable: String,
    val markAsRead: String,
    val markAsUnread: String,
    val bookmark: String,
    val unbookmark: String,
    val cancelDownload: String,
    val deleteDownload: String,
    val download: String,
)

@Composable
fun chapterRowStrings(): ChapterRowStrings = ChapterRowStrings(
    pageProgressPattern = stringResource(MR.strings.tsuzuki_page_short),
    chapterNumberPattern = stringResource(MR.strings.tsuzuki_chapter_n),
    chapterShortPattern = stringResource(MR.strings.tsuzuki_ch_short),
    unavailable = stringResource(MR.strings.tsuzuki_unavailable),
    markAsRead = stringResource(MR.strings.action_mark_as_read),
    markAsUnread = stringResource(MR.strings.action_mark_as_unread),
    bookmark = stringResource(MR.strings.tsuzuki_bookmark),
    unbookmark = stringResource(MR.strings.tsuzuki_unbookmark),
    cancelDownload = stringResource(MR.strings.tsuzuki_cancel_download),
    deleteDownload = stringResource(MR.strings.tsuzuki_delete_download),
    download = stringResource(MR.strings.action_download),
)

internal fun chapterRowProgress(lastPageRead: Long?, pageTotal: Int?, strings: ChapterRowStrings): ChapterRowProgress? {
    val read = lastPageRead?.takeIf { it > 0 } ?: return null
    return ChapterRowProgress(strings.pageProgressPattern.format(read + 1))
}

internal val chapterNameNumberPrefix = Regex("""^(?i)ch(apter)?\.?\s*([0-9]+(\.[0-9]+)?)\b(.*)$""")

internal fun chapterRowTitle(name: String, number: Double, displayByNumber: Boolean, strings: ChapterRowStrings): String {
    if (displayByNumber) return strings.chapterNumberPattern.format(formatChapterNumber(number))
    val match = chapterNameNumberPrefix.find(name) ?: return name
    return strings.chapterShortPattern.format(formatChapterNumber(match.groupValues[2].toDouble())) + match.groupValues[4]
}

/** The meta line under the title: scanlator · date · progress ("Unavailable" replaces progress). */
internal fun chapterRowMeta(
    scanlator: String?,
    date: String?,
    progress: ChapterRowProgress?,
    unavailable: Boolean,
    unavailableLabel: String,
): String = listOfNotNull(
    scanlator?.takeIf { it.isNotBlank() },
    date,
    if (unavailable) unavailableLabel else progress?.label,
).joinToString(" · ")

internal fun swipeActionLabel(
    action: LibraryPreferences.ChapterSwipeAction,
    read: Boolean,
    bookmark: Boolean,
    downloadState: Download.State,
    strings: ChapterRowStrings,
): String = when (action) {
    LibraryPreferences.ChapterSwipeAction.ToggleRead -> if (!read) strings.markAsRead else strings.markAsUnread
    LibraryPreferences.ChapterSwipeAction.ToggleBookmark -> if (!bookmark) strings.bookmark else strings.unbookmark
    LibraryPreferences.ChapterSwipeAction.Download -> when (downloadState) {
        Download.State.QUEUE, Download.State.DOWNLOADING -> strings.cancelDownload
        Download.State.DOWNLOADED -> strings.deleteDownload
        else -> strings.download
    }
    LibraryPreferences.ChapterSwipeAction.Disabled -> ""
}

/** Icon and panel colour of a configured swipe action; shared by the current and follow swipe looks. */
internal data class SwipeActionVisual(
    val label: String,
    val icon: ImageVector,
    val background: Color,
)

internal fun swipeActionVisual(
    action: LibraryPreferences.ChapterSwipeAction,
    read: Boolean,
    bookmark: Boolean,
    downloadState: Download.State,
    accent: Color,
    destructive: Color,
    strings: ChapterRowStrings,
): SwipeActionVisual? {
    if (action == LibraryPreferences.ChapterSwipeAction.Disabled) return null
    return SwipeActionVisual(
        label = swipeActionLabel(action, read, bookmark, downloadState, strings),
        icon = when (action) {
            LibraryPreferences.ChapterSwipeAction.ToggleRead -> if (!read) TsuzukiIcons.Check else TsuzukiIcons.X
            LibraryPreferences.ChapterSwipeAction.ToggleBookmark -> if (!bookmark) TsuzukiIcons.Bookmark else TsuzukiIcons.BookmarkFilled
            LibraryPreferences.ChapterSwipeAction.Download -> when (downloadState) {
                Download.State.NOT_DOWNLOADED, Download.State.ERROR -> TsuzukiIcons.DownloadIcon
                Download.State.QUEUE, Download.State.DOWNLOADING -> TsuzukiIcons.X
                Download.State.DOWNLOADED -> TsuzukiIcons.Trash2
            }
            LibraryPreferences.ChapterSwipeAction.Disabled -> return null
        },
        background = when (action) {
            LibraryPreferences.ChapterSwipeAction.Download -> when (downloadState) {
                Download.State.QUEUE, Download.State.DOWNLOADING, Download.State.DOWNLOADED -> destructive
                else -> accent
            }
            else -> accent
        },
    )
}

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
    selectionMode: Boolean = false,
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
    showDivider: Boolean = true,
) {
    val colors = TsuzukiTheme.colors
    val strings = chapterRowStrings()
    val state = downloadStateProvider()
    val progress = remember(lastPageRead, pageTotal, strings) { chapterRowProgress(lastPageRead, pageTotal, strings) }
    val swipeStart = remember(chapterSwipeStartAction, read, bookmark, state, colors, strings) {
        listOfNotNull(
            getSwipeAction(chapterSwipeStartAction, read, bookmark, state, colors.accent, colors.destructive, strings) { onChapterSwipe(chapterSwipeStartAction) },
        )
    }
    val swipeEnd = remember(chapterSwipeEndAction, read, bookmark, state, colors, strings) {
        listOfNotNull(
            getSwipeAction(chapterSwipeEndAction, read, bookmark, state, colors.accent, colors.destructive, strings) { onChapterSwipe(chapterSwipeEndAction) },
        )
    }
    val rowContent: @Composable () -> Unit = {
        CurrentChapterRowContent(
            title = title, date = date,
            scanlator = listOfNotNull(sourceName, scanlator)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
                .ifEmpty { null },
            read = read, bookmark = bookmark, selected = selected, selectionMode = selectionMode,
            downloadIndicatorEnabled = downloadIndicatorEnabled,
            downloadStateProvider = downloadStateProvider,
            downloadProgressProvider = downloadProgressProvider,
            onDownloadClick = onDownloadClick,
            unavailable = unavailable,
            isCurrent = isCurrent, nested = nested, otherGroups = otherGroups,
            showDivider = showDivider,
            progress = progress, actions = actions,
            chapterSwipeStartAction = chapterSwipeStartAction, chapterSwipeEndAction = chapterSwipeEndAction,
            onChapterSwipe = onChapterSwipe,
            onLongClick = onLongClick, onClick = onClick,
        )
    }
    SwipeRow(
        swipeStart,
        modifier = modifier.clip(shape),
        edge = SwipeEdge.Start,
        surface = TsuzukiTheme.colors.grouped,
    ) {
        SwipeRow(
            swipeEnd,
            edge = SwipeEdge.End,
            surface = TsuzukiTheme.colors.grouped,
        ) {
            rowContent()
        }
    }
}

@Composable
fun ChapterGroupToggle(open: Boolean, alternates: Int, onToggle: () -> Unit) {
    val rotation by androidx.compose.animation.core.animateFloatAsState(
        if (open) 180f else 0f,
        if (TsuzukiTheme.motion.reduced) androidx.compose.animation.core.snap() else dev.errnolink.tsuzuki.designsystem.KasaneSprings.tab(),
        label = "chapter group chevron",
    )
    val expandedLabel = stringResource(MR.strings.tsuzuki_expanded)
    val collapsedLabel = stringResource(MR.strings.tsuzuki_collapsed)
    val hideLabel = stringResource(MR.strings.tsuzuki_hide_other_groups)
    val showLabel = pluralStringResource(MR.plurals.tsuzuki_other_groups, alternates, alternates)
    Box(
        Modifier
            .minimumTouchTarget()
            .size(40.dp)
            .clip(CircleShape)
            .combinedClickable(onClick = onToggle)
            .semantics {
                stateDescription = if (open) expandedLabel else collapsedLabel
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            TsuzukiIcons.ChevronDown,
            if (open) hideLabel else showLabel,
            Modifier.size(18.dp).graphicsLayer { rotationZ = rotation },
            tint = TsuzukiTheme.colors.secondary,
        )
    }
}
