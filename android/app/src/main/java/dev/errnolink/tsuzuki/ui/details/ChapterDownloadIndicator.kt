package dev.errnolink.tsuzuki.ui.details

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import dev.errnolink.tsuzuki.designsystem.CircleCheck
import dev.errnolink.tsuzuki.designsystem.CircleAlert
import dev.errnolink.tsuzuki.designsystem.ProgressRing
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.ContextMenuAction
import dev.errnolink.tsuzuki.designsystem.GlassMenu
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.ArrowDown
import dev.errnolink.tsuzuki.designsystem.Download
import dev.errnolink.tsuzuki.designsystem.Trash2
import dev.errnolink.tsuzuki.designsystem.X
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import eu.kanade.tachiyomi.data.download.model.Download

import eu.kanade.presentation.manga.components.ChapterDownloadAction

@Composable
fun ChapterDownloadIndicator(
    enabled: Boolean,
    downloadStateProvider: () -> Download.State,
    downloadProgressProvider: () -> Int,
    onClick: (ChapterDownloadAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = downloadStateProvider()
    val colors = TsuzukiTheme.colors
    val haptic = LocalHapticFeedback.current
    var showActions by remember { mutableStateOf(false) }
    val progress = downloadProgressProvider().coerceIn(0, 100)
    val label = when (state) {
        Download.State.NOT_DOWNLOADED -> "Download chapter"
        Download.State.QUEUE -> "Download queued"
        Download.State.DOWNLOADING -> "Downloading · $progress%"
        Download.State.DOWNLOADED -> "Downloaded chapter"
        Download.State.ERROR -> "Download failed · Retry"
    }
    Box(
        modifier.size(44.dp).combinedClickable(
            enabled = enabled,
            role = Role.Button,
            onClick = {
                when (state) {
                    Download.State.NOT_DOWNLOADED, Download.State.ERROR -> onClick(ChapterDownloadAction.START)
                    else -> showActions = true
                }
            },
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                showActions = true
            },
        ).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            Download.State.NOT_DOWNLOADED -> Icon(
                TsuzukiIcons.Download, null,
                Modifier.size(IndicatorSize), tint = colors.secondary,
            )
            Download.State.DOWNLOADED -> Icon(
                TsuzukiIcons.CircleCheck, null, Modifier.size(IndicatorSize), tint = colors.secondary,
            )
            Download.State.ERROR -> Icon(
                TsuzukiIcons.CircleAlert, null, Modifier.size(IndicatorSize), tint = colors.destructive,
            )
            Download.State.QUEUE, Download.State.DOWNLOADING -> {
                if (state == Download.State.QUEUE || progress == 0) {
                    ProgressRing(0f, label, Modifier.size(IndicatorSize))
                } else {
                    Canvas(Modifier.size(IndicatorSize)) {
                        drawArc(colors.secondary, -90f, progress * 3.6f, false, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                    }
                }
                Icon(TsuzukiIcons.ArrowDown, null, Modifier.size(16.dp), tint = colors.secondary)
            }
        }
        if (showActions) {
            GlassMenu(
                expanded = true,
                label = label,
                actions = when (state) {
                    Download.State.DOWNLOADED -> listOf(
                        ContextMenuAction(
                            "Delete downloaded chapter",
                            icon = { Icon(TsuzukiIcons.Trash2, null, tint = colors.destructive) },
                            destructive = true,
                        ) { onClick(ChapterDownloadAction.DELETE) },
                    )
                    Download.State.QUEUE, Download.State.DOWNLOADING -> listOf(
                        ContextMenuAction("Cancel download", icon = { Icon(TsuzukiIcons.X, null, tint = colors.text) }) {
                            onClick(ChapterDownloadAction.CANCEL)
                        },
                    )
                    else -> listOf(
                        ContextMenuAction("Download now", icon = { Icon(TsuzukiIcons.ArrowDown, null, tint = colors.text) }) {
                            onClick(ChapterDownloadAction.START_NOW)
                        },
                    )
                },
                onDismissRequest = { showActions = false },
            )
        }
    }
}

internal val IndicatorSize = 26.dp
