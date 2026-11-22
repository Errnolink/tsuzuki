package dev.errnolink.tsuzuki.ui.details

import androidx.compose.foundation.shape.CircleShape
import dev.errnolink.tsuzuki.designsystem.CircleAlert
import dev.errnolink.tsuzuki.designsystem.ProgressMark
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import dev.errnolink.tsuzuki.designsystem.CircleCheck
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.ContextMenuAction
import dev.errnolink.tsuzuki.designsystem.minimumTouchTarget
import dev.errnolink.tsuzuki.designsystem.GlassMenu
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.ArrowDown
import dev.errnolink.tsuzuki.designsystem.Download
import dev.errnolink.tsuzuki.designsystem.Trash2
import dev.errnolink.tsuzuki.designsystem.X
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import eu.kanade.tachiyomi.data.download.model.Download

import eu.kanade.presentation.manga.components.ChapterDownloadAction

@Composable
fun ChapterDownloadIndicator(
    enabled: Boolean,
    downloadStateProvider: () -> Download.State,
    downloadProgressProvider: () -> Int,
    onClick: (ChapterDownloadAction) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    /** The grouped chapter row's control: a 32 dp quiet circle (no glass, surface-card raised). */
    quiet: Boolean = false,
) {
    val state = downloadStateProvider()
    val colors = TsuzukiTheme.colors
    val haptics = TsuzukiTheme.haptics
    var showActions by remember { mutableStateOf(false) }
    val progress = downloadProgressProvider().coerceIn(0, 100)
    val boxSize = when { quiet -> 32.dp; compact -> 28.dp; else -> 44.dp }
    val iconSize = when { quiet -> 16.dp; compact -> 17.dp; else -> IndicatorSize }
    val innerArrowSize = when { quiet -> 9.dp; compact -> 10.dp; else -> 16.dp }
    val label = when (state) {
        Download.State.NOT_DOWNLOADED -> stringResource(MR.strings.tsuzuki_download_chapter)
        Download.State.QUEUE -> stringResource(MR.strings.tsuzuki_download_queued)
        Download.State.DOWNLOADING -> stringResource(MR.strings.tsuzuki_downloading_percent, progress)
        Download.State.DOWNLOADED -> stringResource(MR.strings.tsuzuki_downloaded_chapter)
        Download.State.ERROR -> stringResource(MR.strings.tsuzuki_download_failed_retry)
    }
    Box(
        modifier
            .minimumTouchTarget()
            .combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    when (state) {
                        Download.State.NOT_DOWNLOADED, Download.State.ERROR -> onClick(ChapterDownloadAction.START)
                        else -> showActions = true
                    }
                },
                onLongClick = {
                    haptics.lift()
                    showActions = true
                },
            ).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        // the painted circle keeps its 28-44 dp size inside the 48 dp hit box
        Box(
            Modifier
                .size(boxSize)
                .then(
                    if (quiet) {
                        Modifier.shadow(2.dp, CircleShape, clip = false).background(colors.elevated, CircleShape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
        when (state) {
            Download.State.NOT_DOWNLOADED -> Icon(
                TsuzukiIcons.Download, null,
                Modifier.size(iconSize), tint = colors.secondary,
            )
            Download.State.DOWNLOADED -> Icon(
                TsuzukiIcons.CircleCheck, null, Modifier.size(iconSize), tint = if (compact || quiet) colors.accent else colors.secondary,
            )
            Download.State.ERROR -> Icon(
                TsuzukiIcons.CircleAlert, null, Modifier.size(iconSize), tint = colors.destructive,
            )
            Download.State.QUEUE, Download.State.DOWNLOADING -> {
                if (state == Download.State.QUEUE || progress == 0) {
                    ProgressMark(0f, label, Modifier.size(iconSize))
                } else {
                    Canvas(Modifier.size(iconSize)) {
                        drawArc(colors.secondary, -90f, progress * 3.6f, false, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                    }
                }
                Icon(TsuzukiIcons.ArrowDown, null, Modifier.size(innerArrowSize), tint = colors.secondary)
            }
        }
        }
        if (showActions) {
            GlassMenu(
                expanded = true,
                label = label,
                actions = when (state) {
                    Download.State.DOWNLOADED -> listOf(
                        ContextMenuAction(
                            stringResource(MR.strings.tsuzuki_delete_downloaded_chapter),
                            icon = { Icon(TsuzukiIcons.Trash2, null, tint = colors.destructive) },
                            destructive = true,
                        ) { onClick(ChapterDownloadAction.DELETE) },
                    )
                    Download.State.QUEUE, Download.State.DOWNLOADING -> listOf(
                        ContextMenuAction(stringResource(MR.strings.tsuzuki_cancel_download), icon = { Icon(TsuzukiIcons.X, null, tint = colors.text) }) {
                            onClick(ChapterDownloadAction.CANCEL)
                        },
                    )
                    else -> listOf(
                        ContextMenuAction(stringResource(MR.strings.tsuzuki_download_now), icon = { Icon(TsuzukiIcons.ArrowDown, null, tint = colors.text) }) {
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
