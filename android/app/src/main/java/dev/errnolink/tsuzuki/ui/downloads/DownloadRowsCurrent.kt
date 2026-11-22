package dev.errnolink.tsuzuki.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.ContextMenuAction
import dev.errnolink.tsuzuki.designsystem.GlassMenu
import dev.errnolink.tsuzuki.designsystem.ProgressMark
import dev.errnolink.tsuzuki.designsystem.SwipeActionSpec
import dev.errnolink.tsuzuki.designsystem.SwipeEdge
import dev.errnolink.tsuzuki.designsystem.SwipeRow
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.ArrowDown
import dev.errnolink.tsuzuki.designsystem.ArrowUp
import dev.errnolink.tsuzuki.designsystem.BookOpen
import dev.errnolink.tsuzuki.designsystem.ChevronsUpDown
import dev.errnolink.tsuzuki.designsystem.Ellipsis
import dev.errnolink.tsuzuki.designsystem.GripVertical
import dev.errnolink.tsuzuki.designsystem.RefreshCw
import dev.errnolink.tsuzuki.designsystem.X
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.insetDivider
import dev.errnolink.tsuzuki.designsystem.TsuzukiSize
import dev.errnolink.tsuzuki.ui.shell.ShellAction
import dev.errnolink.tsuzuki.ui.shell.ShellCover
import eu.kanade.tachiyomi.data.download.model.Download
import kotlinx.coroutines.flow.flowOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.domain.manga.model.asMangaCover

@Composable
internal fun CurrentDownloadRow(
    download: Download,
    running: Boolean,
    onOpen: () -> Unit,
    onMoveTop: () -> Unit,
    onMoveBottom: () -> Unit,
    onMoveSeriesTop: () -> Unit,
    onMoveSeriesBottom: () -> Unit,
    onRetry: () -> Unit,
    onCancelChapter: () -> Unit,
    onCancelSeries: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    dragHandle: Modifier,
    shape: CornerBasedShape,
    divider: Boolean,
    modifier: Modifier = Modifier,
) {
    val status by download.statusFlow.collectAsState()
    val progressFlow = remember(download, status) {
        if (status == Download.State.DOWNLOADING) download.progressFlow else flowOf(download.progress)
    }
    val progress by progressFlow.collectAsState(initial = download.progress)
    val colors = TsuzukiTheme.colors
    var actionsVisible by remember { mutableStateOf(false) }
    val swipeActions = remember(download, onMoveTop, onCancelChapter) {
        downloadSwipeSpecs(onMoveTop, onCancelChapter).map { spec ->
            SwipeActionSpec(
                icon = if (spec.label == "Move to top") TsuzukiIcons.ArrowUp else TsuzukiIcons.X,
                label = spec.label,
                background = if (spec.label == "Move to top") colors.accent else colors.destructive,
                onClick = spec.onClick,
                full = spec.label == "Move to top",
                keep = spec.label == "Move to top",
            )
        }
    }
    SwipeRow(swipeActions, edge = SwipeEdge.End) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = TsuzukiTheme.spacing.gutter).clip(shape)
            .background(colors.grouped)
            .clickable(onClick = onOpen)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction("Move up") { onMoveUp(); true },
                    CustomAccessibilityAction("Move down") { onMoveDown(); true },
                    CustomAccessibilityAction("Move to top") { onMoveTop(); true },
                    CustomAccessibilityAction("Move to bottom") { onMoveBottom(); true },
                )
            }
            .then(if (divider) Modifier.insetDivider(56.dp) else Modifier)
            .padding(TsuzukiTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TsuzukiTheme.spacing.medium),
    ) {
        Box(Modifier.size(TsuzukiSize.touch), contentAlignment = Alignment.Center) {
            Icon(
                TsuzukiIcons.GripVertical,
                stringResource(MR.strings.tsuzuki_reorder_download),
                dragHandle.padding(12.dp),
                tint = colors.secondary,
            )
        }
        Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) {
            ShellCover(download.manga.asMangaCover(), download.manga.title, Modifier.width(40.dp), fixedRatio = 2f / 3f, hero = false)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            TsuzukiText(download.chapter.name, style = TsuzukiTheme.typography.callout, maxLines = 1, overflow = TextOverflow.Ellipsis)
            TsuzukiText(download.manga.title, style = TsuzukiTheme.typography.footnote, color = colors.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            TsuzukiText(
                when (status) {
                    Download.State.ERROR -> "Download failed · Retry available"
                    Download.State.DOWNLOADING -> "${download.downloadedImages}/${download.pages?.size ?: 0} pages · $progress%"
                    Download.State.DOWNLOADED -> "Downloaded"
                    else -> if (running) "Queued" else "Paused"
                },
                style = TsuzukiTheme.typography.caption1,
                color = if (status == Download.State.ERROR) colors.destructive else colors.secondary,
            )
        }
        if (status == Download.State.DOWNLOADING) ProgressMark(progress / 100f, "Download progress")
        Box {
            ShellAction("Download actions", TsuzukiIcons.Ellipsis, { actionsVisible = true })
            GlassMenu(
                actionsVisible,
                download.chapter.name,
                listOf(
                    ContextMenuAction("Show manga", group = 0, icon = { Icon(TsuzukiIcons.BookOpen, null, tint = colors.text) }, onClick = onOpen),
                    ContextMenuAction("Move to top", group = 1, icon = { Icon(TsuzukiIcons.ArrowUp, null, tint = colors.text) }, onClick = onMoveTop),
                    ContextMenuAction("Move to bottom", group = 1, icon = { Icon(TsuzukiIcons.ArrowDown, null, tint = colors.text) }, onClick = onMoveBottom),
                    ContextMenuAction("Move series to top", group = 2, icon = { Icon(TsuzukiIcons.ChevronsUpDown, null, tint = colors.text) }, onClick = onMoveSeriesTop),
                    ContextMenuAction("Move series to bottom", group = 2, icon = { Icon(TsuzukiIcons.ChevronsUpDown, null, tint = colors.text) }, onClick = onMoveSeriesBottom),
                    ContextMenuAction("Retry downloads", group = 3, icon = { Icon(TsuzukiIcons.RefreshCw, null, tint = colors.text) }, onClick = onRetry),
                    ContextMenuAction("Cancel chapter", group = 4, icon = { Icon(TsuzukiIcons.X, null, tint = colors.destructive) }, destructive = true, onClick = onCancelChapter),
                    ContextMenuAction("Cancel series", group = 4, icon = { Icon(TsuzukiIcons.X, null, tint = colors.destructive) }, destructive = true, onClick = onCancelSeries),
                ),
                onDismissRequest = { actionsVisible = false },
            )
        }
    }
    }
}

internal class DownloadSwipeSpec(
    val label: String,
    val onClick: () -> Unit,
)

internal fun downloadSwipeSpecs(
    moveToTop: () -> Unit,
    cancelChapter: () -> Unit,
): List<DownloadSwipeSpec> = listOf(
    DownloadSwipeSpec("Move to top", moveToTop),
    DownloadSwipeSpec("Cancel chapter", cancelChapter),
)
