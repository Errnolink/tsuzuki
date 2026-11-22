package dev.errnolink.tsuzuki.ui.history

import dev.errnolink.tsuzuki.reader.ReaderOrigins
import dev.errnolink.tsuzuki.reader.readerOrigin
import eu.kanade.presentation.util.formatChapterNumber
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.ContextMenu
import dev.errnolink.tsuzuki.designsystem.ContextMenuAction
import dev.errnolink.tsuzuki.designsystem.ContinueButton
import dev.errnolink.tsuzuki.designsystem.ContinueButtonSize
import dev.errnolink.tsuzuki.data.library.chapterPageProgress
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.Heart
import dev.errnolink.tsuzuki.designsystem.SelectionMark
import dev.errnolink.tsuzuki.designsystem.Trash2
import dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing
import dev.errnolink.tsuzuki.designsystem.selectionFill
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import dev.errnolink.tsuzuki.preference.TsuzukiPreferences
import dev.errnolink.tsuzuki.util.formatTime
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.insetDivider
import dev.errnolink.tsuzuki.ui.motion.heroFlightLabel
import dev.errnolink.tsuzuki.ui.shell.ShellCover

internal fun historyChapterLabel(number: Double, name: String?): String {
    if (number.isFinite() && number >= 0) return "Ch. ${formatChapterNumber(number)}"
    return name?.takeIf { it.isNotBlank() } ?: "Chapter"
}

internal fun historyEmptySearching(searchQuery: String?): Boolean = !searchQuery.isNullOrEmpty()

@Composable
fun HistoryItem(
    history: HistoryWithRelations,
    onClickCover: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onClickDelete: () -> Unit,
    onClickFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean,
    selectionMode: Boolean = false,
    readProgress: String?,
    usePanoramaCover: Boolean,
    pageTotal: Int? = null,
    coverRatio: MutableFloatState = remember { mutableFloatStateOf(1f) },
    shape: CornerBasedShape = RoundedCornerShape(TsuzukiCorners.group),
    divider: Boolean = false,
) {
    val haptics = TsuzukiTheme.haptics
    val colors = TsuzukiTheme.colors
    val longClick = {
        haptics.lift()
        onLongClick()
    }
    var rowBounds by remember { mutableStateOf<Rect?>(null) }
    var buttonBounds by remember { mutableStateOf<Rect?>(null) }
    Row(
        modifier = modifier.fillMaxWidth()
            .readerOrigin()
            .onGloballyPositioned { rowBounds = it.boundsInWindow() }
            .clip(shape)
            .background(colors.grouped)
            .selectionFill(selected)
            .semantics { this.selected = selected }
            .combinedClickable(
                onClick = {
                    ReaderOrigins.markActive(rowBounds)
                    onClick()
                },
                onLongClick = longClick,
            )
            .then(if (divider) Modifier.insetDivider(72.dp) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TsuzukiTheme.spacing.medium),
    ) {
        if (selectionMode) {
            SelectionMark(selected = selected, Modifier.size(24.dp))
        }
        ContextMenu(
            label = history.title,
            actions = listOf(
                ContextMenuAction(
                    if (history.coverData.isMangaFavorite) "Remove from library" else "Add to library",
                    icon = { Icon(TsuzukiIcons.Heart, null, tint = colors.text) },
                    destructive = history.coverData.isMangaFavorite,
                    onClick = onClickFavorite,
                ),
                ContextMenuAction("Remove history", icon = { Icon(TsuzukiIcons.Trash2, null, tint = colors.destructive) }, destructive = true, onClick = onClickDelete),
            ),
            onClick = onClickCover,
        ) {
            ShellCover(history.coverData, history.title, Modifier.width(48.dp), fixedRatio = if (usePanoramaCover) null else 2f / 3f, hero = true)
        }
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            TsuzukiText(
                history.title,
                Modifier.heroFlightLabel(history.mangaId),
                style = TsuzukiTheme.typography.headline,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (history.read && readProgress == null) colors.secondary else colors.text,
            )
            TsuzukiText(
                text = historyChapterLabel(history.chapterNumber, null),
                style = TsuzukiTheme.typography.footnote,
                color = colors.secondary,
            )
            if (readProgress != null) {
                TsuzukiText(
                    readProgress,
                    style = TsuzukiTheme.typography.caption1,
                    color = colors.secondary,
                )
            }
            val context = LocalContext.current
            val timeFormat by remember { Injekt.get<TsuzukiPreferences>().timeFormat() }.collectAsState()
            val is24Hour = remember(context) { android.text.format.DateFormat.is24HourFormat(context) }
            val time = remember(history.readAt, timeFormat, is24Hour) {
                history.readAt?.let { formatTime(it, timeFormat, is24Hour) }
            }
            if (time != null) {
                TsuzukiText(time, style = TsuzukiTheme.typography.caption1, color = colors.secondary)
            }
        }
        ContinueButton(
            progress = chapterPageProgress(history.lastPageRead, pageTotal, history.read),
            onClick = {
                ReaderOrigins.markActive(buttonBounds)
                onClick()
            },
            modifier = Modifier
                .readerOrigin()
                .onGloballyPositioned { buttonBounds = it.boundsInWindow() },
            size = ContinueButtonSize.Compact,
            contentDescription = "Resume",
        )
    }
}

