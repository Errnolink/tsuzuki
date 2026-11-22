package dev.errnolink.tsuzuki.ui.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.designsystem.PullDownAction
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.ArrowUpDown
import dev.errnolink.tsuzuki.designsystem.Pause
import dev.errnolink.tsuzuki.designsystem.Play
import dev.errnolink.tsuzuki.designsystem.Trash2
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.KasaneSprings
import dev.errnolink.tsuzuki.ui.settings.ConfirmSheet
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
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
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
                model.reorder { live ->
                    val movedKey = queue.getOrNull(fromIndex)?.chapter?.id
                    val targetKey = queue.getOrNull(toIndex)?.chapter?.id
                    val movedIndex = live.indexOfFirst { it.chapter.id == movedKey }
                    val targetIndex = live.indexOfFirst { it.chapter.id == targetKey }
                    if (movedIndex < 0 || targetIndex < 0 || movedIndex == targetIndex) {
                        null
                    } else {
                        live.toMutableList().apply { add(targetIndex, removeAt(movedIndex)) }
                    }
                }
            }
        }
        SkeletonCrossfade(
            loading = !loaded,
            skeleton = {
                ShellList(title = "Downloads", navigateUp = navigator::pop, itemSpacing = 0.dp) {
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
                    itemSpacing = 0.dp,
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
                                    PullDownAction("Newest uploads", onClick = { model.reorder { it.sortedWithinSources { d -> d.chapter.dateUpload }.reversedWithinSources() } }),
                                    PullDownAction("Oldest uploads", onClick = { model.reorder { it.sortedWithinSources { d -> d.chapter.dateUpload } } }),
                                    PullDownAction("Chapter number ascending", onClick = { model.reorder { it.sortedWithinSources { d -> d.chapter.chapterNumber } } }),
                                    PullDownAction("Chapter number descending", onClick = { model.reorder { it.sortedWithinSources { d -> d.chapter.chapterNumber }.reversedWithinSources() } }),
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
                    Row(
                        Modifier.padding(horizontal = TsuzukiTheme.spacing.gutter, vertical = TsuzukiTheme.spacing.tiny),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TsuzukiText(
                            "${queue.size} chapters · ${if (running) "Downloading" else "Paused"}",
                            Modifier,
                            TsuzukiTheme.typography.subhead,
                            TsuzukiTheme.colors.secondary,
                        )
                    }
                }
                groups.forEach { group ->
                    item("download-source-${group.id}") {
                        TsuzukiText(
                            group.name,
                            Modifier.padding(
                                start = TsuzukiTheme.spacing.gutter,
                                end = TsuzukiTheme.spacing.gutter,
                                top = TsuzukiTheme.spacing.medium,
                                bottom = TsuzukiTheme.spacing.small,
                            ),
                            TsuzukiTheme.typography.sectionHeader,
                            TsuzukiTheme.colors.secondary,
                        )
                    }
                    val groupSubItems = group.subItems
                    items(count = groupSubItems.size, key = { "download-${groupSubItems[it].download.chapter.id}" }, contentType = { "download" }) { index ->
                        val download = groupSubItems[index].download
                        ReorderableItem(reorderable, "download-${download.chapter.id}") {
                            val motion = TsuzukiTheme.motion
                            val rowModifier = Modifier.animateItem(
                                if (motion.reduced) null else KasaneSprings.tab(),
                                if (motion.reduced) null else KasaneSprings.tab(),
                                if (motion.reduced) null else KasaneSprings.tab(),
                            )
                            CurrentDownloadRow(
                                modifier = rowModifier,
                                download = download,
                                running = running,
                                onOpen = { model.showManga(download.manga.id) },
                                onMoveTop = { model.reorder { it.moveDownload(download, true) } },
                                onMoveBottom = { model.reorder { it.moveDownload(download, false) } },
                                onMoveSeriesTop = { model.reorder { it.partition { d -> d.manga.id == download.manga.id }.let { p -> p.first + p.second } } },
                                onMoveSeriesBottom = { model.reorder { it.partition { d -> d.manga.id == download.manga.id }.let { p -> p.second + p.first } } },
                                onRetry = model::startDownloads,
                                onCancelChapter = { model.cancel(listOf(download)) },
                                onCancelSeries = { model.cancel(queue.filter { it.manga.id == download.manga.id }) },
                                onMoveUp = { model.reorder { it.moveBy(download, -1) } },
                                onMoveDown = { model.reorder { it.moveBy(download, 1) } },
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
            ConfirmSheet(
                visible = true,
                title = stringResource(MR.strings.action_cancel_all),
                message = "Remove ${queue.size} queued chapters. Completed downloads are kept.",
                confirmLabel = stringResource(MR.strings.action_cancel_all),
                destructive = true,
                onConfirm = { model.clearQueue() },
                onDismissRequest = { clearing = false },
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
