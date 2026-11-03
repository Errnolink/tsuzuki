package eu.kanade.presentation.track

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.ContextMenuAction
import dev.errnolink.tsuzuki.designsystem.Ellipsis
import dev.errnolink.tsuzuki.designsystem.GlassMenu
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.IconButton
import dev.errnolink.tsuzuki.designsystem.InsetGroupedList
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import eu.kanade.presentation.track.components.TrackLogoIcon
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.ui.manga.track.TrackItem
import eu.kanade.tachiyomi.util.lang.toLocalDate
import eu.kanade.tachiyomi.util.system.copyToClipboard
import tachiyomi.presentation.core.i18n.stringResource
import java.time.format.DateTimeFormatter

@Composable
fun TrackInfoDialogHome(
    modifier: Modifier = Modifier,
    trackItems: List<TrackItem>,
    dateFormat: DateTimeFormatter,
    onStatusClick: (TrackItem) -> Unit,
    onChapterClick: (TrackItem) -> Unit,
    onScoreClick: (TrackItem) -> Unit,
    onStartDateEdit: (TrackItem) -> Unit,
    onEndDateEdit: (TrackItem) -> Unit,
    onNewSearch: (TrackItem) -> Unit,
    onOpenInBrowser: (TrackItem) -> Unit,
    onRemoved: (TrackItem) -> Unit,
    onCopyLink: (TrackItem) -> Unit,
    onTogglePrivate: (TrackItem) -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        itemsIndexed(trackItems, key = { _, item -> item.tracker.id }) { _, item ->
            if (item.track != null) {
                val track = item.track
                val supportsScoring = item.tracker.getScoreList().isNotEmpty()
                val supportsReadingDates = item.tracker.supportsReadingDates
                val supportsPrivate = item.tracker.supportsPrivateTracking
                val status = item.tracker.getStatus(track.status)
                val chapters = "${track.lastChapterRead.toInt()}".let {
                    val totalChapters = track.totalChapters
                    if (totalChapters > 0) "$it / $totalChapters" else it
                }
                val score = item.tracker.displayScore(track).takeIf { supportsScoring && track.score != 0.0 }
                val startDate = dateFormat.format(track.startDate.toLocalDate())
                    .takeIf { supportsReadingDates && track.startDate != 0L }
                val endDate = dateFormat.format(track.finishDate.toLocalDate())
                    .takeIf { supportsReadingDates && track.finishDate != 0L }
                InsetGroupedList(title = item.tracker.name) {
                    TrackedHeaderRow(
                        tracker = item.tracker,
                        title = track.title,
                        private = track.private,
                        onOpenInBrowser = { onOpenInBrowser(item) },
                        onCopyLink = { onCopyLink(item) },
                        onTogglePrivate = onTogglePrivate.takeIf { supportsPrivate }?.let { { it(item) } },
                    )
                    GroupedRow(
                        "Status",
                        value = status?.let { stringResource(it) } ?: "Not set",
                        chevron = true,
                        onClick = { onStatusClick(item) },
                    )
                    GroupedRow("Chapters", value = chapters, chevron = true, onClick = { onChapterClick(item) })
                    if (supportsScoring) {
                        GroupedRow("Score", value = score ?: "Not set", chevron = true, onClick = { onScoreClick(item) })
                    }
                    if (supportsReadingDates) {
                        GroupedRow("Started", value = startDate ?: "Not set", chevron = true, onClick = { onStartDateEdit(item) })
                        GroupedRow("Finished", value = endDate ?: "Not set", chevron = true, onClick = { onEndDateEdit(item) })
                    }
                    GroupedRow("Search again", chevron = true, onClick = { onNewSearch(item) })
                    GroupedRow(
                        "Remove from ${item.tracker.name}",
                        destructive = true,
                        divider = false,
                        onClick = { onRemoved(item) },
                    )
                }
            } else {
                InsetGroupedList {
                    GroupedRow(
                        "Track on ${item.tracker.name}",
                        leading = { TrackLogoIcon(tracker = item.tracker) },
                        chevron = true,
                        divider = false,
                        onClick = { onNewSearch(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackedHeaderRow(
    tracker: Tracker,
    title: String,
    private: Boolean,
    onOpenInBrowser: () -> Unit,
    onCopyLink: () -> Unit,
    onTogglePrivate: (() -> Unit)?,
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box {
            TrackLogoIcon(
                tracker = tracker,
                onClick = onOpenInBrowser,
                onLongClick = onCopyLink,
            )
            if (private) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(TsuzukiTheme.colors.accent)
                        .semantics { contentDescription = "Tracked privately" },
                )
            }
        }
        TsuzukiText(
            title,
            Modifier.weight(1f).combinedClickable(
                onClick = onOpenInBrowser,
                onLongClick = { context.copyToClipboard(title, title) },
            ),
            style = TsuzukiTheme.typography.headline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box {
            IconButton("More tracking actions", { menuExpanded = true }) {
                androidx.compose.material3.Icon(TsuzukiIcons.Ellipsis, null)
            }
            GlassMenu(
                expanded = menuExpanded,
                label = "Tracking actions",
                onDismissRequest = { menuExpanded = false },
                actions = buildList {
                    add(ContextMenuAction("Open in browser") { onOpenInBrowser() })
                    add(ContextMenuAction("Copy link") { onCopyLink() })
                    if (onTogglePrivate != null) {
                        add(
                            ContextMenuAction(if (private) "Track publicly" else "Track privately") { onTogglePrivate() },
                        )
                    }
                },
            )
        }
    }
}
