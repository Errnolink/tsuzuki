package dev.errnolink.tsuzuki.ui.history

import dev.errnolink.tsuzuki.reader.readerOrigin
import eu.kanade.presentation.util.formatChapterNumber
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
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
import dev.errnolink.tsuzuki.designsystem.Check
import dev.errnolink.tsuzuki.designsystem.Heart
import dev.errnolink.tsuzuki.designsystem.Trash2
import dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing
import tachiyomi.domain.history.model.HistoryWithRelations
import java.text.DateFormat
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.insetDivider
import dev.errnolink.tsuzuki.ui.shell.ShellCover

internal fun historyChapterLabel(number: Double, name: String?): String {
    if (number.isFinite() && number > 0) return "Ch. ${formatChapterNumber(number)}"
    return name?.takeIf { it.isNotBlank() } ?: "Chapter"
}

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
    readProgress: String?,
    usePanoramaCover: Boolean,
    pageTotal: Int? = null,
    coverRatio: MutableFloatState = remember { mutableFloatStateOf(1f) },
    shape: CornerBasedShape = RoundedCornerShape(TsuzukiCorners.group),
    divider: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    val colors = TsuzukiTheme.colors
    val longClick = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onLongClick()
    }
    Row(
        modifier = modifier.fillMaxWidth()
            .readerOrigin()
            .clip(shape)
            .background(if (selected) colors.selectedFill else colors.grouped)
            .combinedClickable(onClick = onClick, onLongClick = longClick)
            .then(if (divider) Modifier.insetDivider(72.dp) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TsuzukiTheme.spacing.medium),
    ) {
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
            ShellCover(history.coverData, history.title, Modifier.width(48.dp), fixedRatio = if (usePanoramaCover) null else 2f / 3f)
        }
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            TsuzukiText(
                history.title,
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
            val time = remember(history.readAt) { history.readAt?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(it) } }
            if (time != null) {
                TsuzukiText(time, style = TsuzukiTheme.typography.caption1, color = colors.secondary)
            }
        }
        val buttonShape = CircleShape
        if (selected) {
            Box(
                Modifier
                    .size(48.dp)
                    .clickable(onClick = onClick)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Selected"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(buttonShape)
                        .background(colors.accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(TsuzukiIcons.Check, null, Modifier.size(20.dp), tint = colors.accent)
                }
            }
        } else {
            ContinueButton(
                progress = chapterPageProgress(history.lastPageRead, pageTotal, history.read),
                onClick = onClick,
                size = ContinueButtonSize.Compact,
                contentDescription = "Resume",
            )
        }
    }
}

