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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.designsystem.ContextMenuAction
import dev.errnolink.tsuzuki.designsystem.GlassMenu
import dev.errnolink.tsuzuki.designsystem.PullDownAction
import dev.errnolink.tsuzuki.designsystem.ProgressRing
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.ArrowDown
import dev.errnolink.tsuzuki.designsystem.ArrowUp
import dev.errnolink.tsuzuki.designsystem.ArrowUpDown
import dev.errnolink.tsuzuki.designsystem.BookOpen
import dev.errnolink.tsuzuki.designsystem.ChevronsUpDown
import dev.errnolink.tsuzuki.designsystem.Ellipsis
import dev.errnolink.tsuzuki.designsystem.GripVertical
import dev.errnolink.tsuzuki.designsystem.Pause
import dev.errnolink.tsuzuki.designsystem.Play
import dev.errnolink.tsuzuki.designsystem.RefreshCw
import dev.errnolink.tsuzuki.designsystem.Trash2
import dev.errnolink.tsuzuki.designsystem.X
import dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.insetDivider
import dev.errnolink.tsuzuki.ui.settings.SettingsActionButton
import dev.errnolink.tsuzuki.ui.settings.SettingsAlert
import dev.errnolink.tsuzuki.ui.shell.ShellAction
import dev.errnolink.tsuzuki.ui.shell.ShellCover
import dev.errnolink.tsuzuki.ui.shell.ShellEmpty
import dev.errnolink.tsuzuki.ui.shell.ShellList
import dev.errnolink.tsuzuki.ui.shell.ShellTopAction
import dev.errnolink.tsuzuki.ui.shell.DownloadRowSkeleton
import dev.errnolink.tsuzuki.ui.shell.SkeletonCaption
import dev.errnolink.tsuzuki.ui.shell.SkeletonCrossfade
import dev.errnolink.tsuzuki.ui.shell.SkeletonLine
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.ui.download.DownloadQueueScreenModel
import kotlinx.coroutines.flow.flowOf
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.domain.manga.model.asMangaCover
object DownloadQueueScreen : Screen() {
    private fun readResolve(): Any = DownloadQueueScreen

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { DownloadQueueScreenModel(navigator = navigator) }
        val groups by model.state.collectAsState()
        val loaded by model.loaded.collectAsState()
        val running by model.isDownloaderRunning.collectAsState()
        val queue = remember(groups) { groups.flatMap { group -> group.subItems.map { it.download } } }
        var clearing by remember { mutableStateOf(false) }
        val listState = rememberLazyListState()
        val reorderable = rememberReorderableLazyListState(listState) { from, to ->
            val fromIndex = queue.indexOfFirst { "download-${it.chapter.id}" == from.key }
            val toIndex = queue.indexOfFirst { "download-${it.chapter.id}" == to.key }
            if (fromIndex >= 0 && toIndex >= 0 && queue[fromIndex].source.id == queue[toIndex].source.id) {
                model.reorder(queue.toMutableList().apply { add(toIndex, removeAt(fromIndex)) })
            }
        }
        SkeletonCrossfade(
            loading = !loaded,
            skeleton = {
                ShellList(title = "Downloads", navigateUp = navigator::pop, itemSpacing = TsuzukiTheme.spacing.medium) {
                    item("download-skeleton-summary") {
                        SkeletonLine(0.32f, 14.dp, Modifier.padding(horizontal = TsuzukiTheme.spacing.gutter, vertical = TsuzukiTheme.spacing.tiny))
                    }
                    item("download-skeleton-header") {
                        SkeletonCaption(TsuzukiTheme.spacing.gutter, 0.4f)
                    }
                    items(count = 5, key = { "download-skeleton-$it" }, contentType = { "skeleton" }) { index ->
                        DownloadRowSkeleton(
                            shape = RoundedCornerShape(
                                topStart = if (index == 0) TsuzukiCorners.group else 0.dp,
                                topEnd = if (index == 0) TsuzukiCorners.group else 0.dp,
                                bottomStart = if (index == 4) TsuzukiCorners.group else 0.dp,
                                bottomEnd = if (index == 4) TsuzukiCorners.group else 0.dp,
                            ),
                            divider = index < 4,
                        )
                    }
                }
            },
            content = {
                ShellList(
                    title = "Downloads",
                    state = listState,
                    navigateUp = navigator::pop,
                    itemSpacing = TsuzukiTheme.spacing.medium,
                    actions = if (queue.isNotEmpty()) {
                        listOf(
                            ShellTopAction(
                                if (running) "Pause downloads" else "Resume downloads",
                                if (running) TsuzukiIcons.Pause else TsuzukiIcons.Play,
                                { if (running) model.pauseDownloads() else model.startDownloads() },
                            ),
                            ShellTopAction(
                                "Sort downloads",
                                TsuzukiIcons.ArrowUpDown,
                                {},
                                menuTitle = "Sort downloads",
                                menu = listOf(
                                    PullDownAction("Newest uploads", onClick = { model.reorder(queue.sortedWithinSources { it.chapter.dateUpload }.reversedWithinSources()) }),
                                    PullDownAction("Oldest uploads", onClick = { model.reorder(queue.sortedWithinSources { it.chapter.dateUpload }) }),
                                    PullDownAction("Chapter number ascending", onClick = { model.reorder(queue.sortedWithinSources { it.chapter.chapterNumber }) }),
                                    PullDownAction("Chapter number descending", onClick = { model.reorder(queue.sortedWithinSources { it.chapter.chapterNumber }.reversedWithinSources()) }),
                                ),
                            ),
                            ShellTopAction("Cancel all downloads", TsuzukiIcons.Trash2, { clearing = true }),
                        )
                    } else {
                        emptyList()
                    },
                ) {
                    when {
                        queue.isEmpty() -> item("downloads-empty") {
                    ShellEmpty("Ready for offline reading", "Downloaded chapters stay on this device. Add chapters from a manga’s chapter list.")
                }
                else -> {
                item("download-summary") {
                    TsuzukiText(
                        "${queue.size} chapters · ${if (running) "Downloading" else "Paused"}",
                        Modifier.padding(horizontal = TsuzukiTheme.spacing.gutter, vertical = TsuzukiTheme.spacing.tiny),
                        TsuzukiTheme.typography.subhead,
                        TsuzukiTheme.colors.secondary,
                    )
                }
                groups.forEach { group ->
                    item("download-source-${group.id}") {
                        TsuzukiText(
                            group.name,
                            Modifier.padding(horizontal = TsuzukiTheme.spacing.gutter, vertical = TsuzukiTheme.spacing.small),
                            TsuzukiTheme.typography.sectionHeader,
                            TsuzukiTheme.colors.secondary,
                        )
                    }
                    val groupSubItems = group.subItems
                    items(count = groupSubItems.size, key = { "download-${groupSubItems[it].download.chapter.id}" }, contentType = { "download" }) { index ->
                        val download = groupSubItems[index].download
                        ReorderableItem(reorderable, "download-${download.chapter.id}") {
                            val motion = TsuzukiTheme.motion
                            DownloadRow(
                                modifier = Modifier.animateItem(
                                    if (motion.reduced) null else motion.tabSpring(),
                                    if (motion.reduced) null else motion.tabSpring(),
                                    if (motion.reduced) null else motion.tabSpring(),
                                ),
                                download = download,
                                running = running,
                                onOpen = { model.showManga(download.manga.id) },
                                onMoveTop = { model.reorder(queue.moveDownload(download, true)) },
                                onMoveBottom = { model.reorder(queue.moveDownload(download, false)) },
                                onMoveSeriesTop = { model.reorder(queue.partition { it.manga.id == download.manga.id }.let { it.first + it.second }) },
                                onMoveSeriesBottom = { model.reorder(queue.partition { it.manga.id == download.manga.id }.let { it.second + it.first }) },
                                onRetry = model::startDownloads,
                                onCancelChapter = { model.cancel(listOf(download)) },
                                onCancelSeries = { model.cancel(queue.filter { it.manga.id == download.manga.id }) },
                                onMoveUp = { model.reorder(queue.moveBy(download, -1)) },
                                onMoveDown = { model.reorder(queue.moveBy(download, 1)) },
                                dragHandle = Modifier.draggableHandle(),
                                shape = RoundedCornerShape(
                                    topStart = if (index == 0) TsuzukiCorners.group else 0.dp,
                                    topEnd = if (index == 0) TsuzukiCorners.group else 0.dp,
                                    bottomStart = if (index == groupSubItems.lastIndex) TsuzukiCorners.group else 0.dp,
                                    bottomEnd = if (index == groupSubItems.lastIndex) TsuzukiCorners.group else 0.dp,
                                ),
                                divider = index < groupSubItems.lastIndex,
                            )
                        }
                    }
                }
                }
                    }
                }
            },
        )
        if (clearing) {
            val colors = TsuzukiTheme.colors
            SettingsAlert(
                onDismissRequest = { clearing = false },
                title = { TsuzukiText(stringResource(MR.strings.action_cancel_all), style = TsuzukiTheme.typography.headline) },
                text = {
                    TsuzukiText(
                        "Remove ${queue.size} queued chapters. Completed downloads are kept.",
                        style = TsuzukiTheme.typography.callout,
                        color = colors.secondary,
                    )
                },
                confirmButton = {
                    SettingsActionButton(onClick = { clearing = false; model.clearQueue() }, destructive = true) {
                        TsuzukiText(stringResource(MR.strings.action_cancel_all), color = colors.destructive)
                    }
                },
                dismissButton = {
                    SettingsActionButton(onClick = { clearing = false }) {
                        TsuzukiText(stringResource(MR.strings.action_cancel), color = colors.accent)
                    }
                },
            )
        }
    }
}

@Composable
private fun DownloadRow(
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
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(
                TsuzukiIcons.GripVertical,
                stringResource(MR.strings.tsuzuki_reorder_download),
                dragHandle.padding(12.dp),
                tint = colors.secondary,
            )
        }
        Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) {
            ShellCover(download.manga.asMangaCover(), download.manga.title, Modifier.width(40.dp), fixedRatio = 2f / 3f)
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
        if (status == Download.State.DOWNLOADING) ProgressRing(progress / 100f, "Download progress")
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

private fun <T : Comparable<T>> List<Download>.sortedWithinSources(selector: (Download) -> T): List<Download> =
    groupBy { it.source.id }.values.flatMap { it.sortedBy(selector) }

private fun List<Download>.reversedWithinSources(): List<Download> = groupBy { it.source.id }.values.flatMap { it.reversed() }

private fun List<Download>.moveDownload(download: Download, first: Boolean): List<Download> = groupBy { it.source.id }.values.flatMap { group ->
    if (group.first().source.id != download.source.id) group
    else group.filterNot { it.chapter.id == download.chapter.id }.let { if (first) listOf(download) + it else it + download }
}

private fun List<Download>.moveBy(download: Download, delta: Int): List<Download> = groupBy { it.source.id }.values.flatMap { group ->
    val index = group.indexOfFirst { it.chapter.id == download.chapter.id }
    if (index < 0) {
        group
    } else {
        val target = (index + delta).coerceIn(0, group.lastIndex)
        if (target == index) group else group.toMutableList().apply { add(target, removeAt(index)) }
    }
}
