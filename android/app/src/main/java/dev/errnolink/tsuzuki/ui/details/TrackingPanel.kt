package dev.errnolink.tsuzuki.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.Plus
import dev.errnolink.tsuzuki.designsystem.TsuzukiChevron
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import eu.kanade.presentation.track.components.TrackLogoIcon
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.ui.manga.track.TrackItem
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.model.Track
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class TrackingPanelState(
    val trackItems: List<TrackItem>,
    val loading: Boolean,
)

@Composable
fun rememberTrackingPanelState(mangaId: Long): TrackingPanelState {
    val trackerManager = remember { Injekt.get<TrackerManager>() }
    val getTracks = remember { Injekt.get<GetTracks>() }
    var items by remember(mangaId) { mutableStateOf(emptyList<TrackItem>()) }
    var loading by remember(mangaId) { mutableStateOf(true) }
    LaunchedEffect(mangaId) {
        getTracks.subscribe(mangaId)
            .catch { logcat(LogPriority.ERROR, it) }
            .distinctUntilChanged()
            .collect { tracks ->
                items = trackerManager.loggedInTrackers()
                    .map { service -> TrackItem(tracks.find { it.trackerId == service.id }, service) }
                loading = false
            }
    }
    return TrackingPanelState(items, loading)
}

/** Linked trackers in stable order; unlinked logged-in trackers stay behind the Add tracker row. */
internal fun trackingPanelRows(items: List<TrackItem>): List<TrackItem> =
    items.filter { it.track != null }

/** "Ch. 12 / 248" style summary; null when the track has no progress data yet. */
internal fun trackingPanelChapterLabel(track: Track): String? = when {
    track.lastChapterRead <= 0f && track.totalChapters <= 0 -> null
    track.totalChapters > 0 -> "Ch. ${track.lastChapterRead.toInt()} / ${track.totalChapters}"
    else -> "Ch. ${track.lastChapterRead.toInt()}"
}

/**
 * Compact tracker list for the details action panel: one row per linked tracker
 * (logo, name, "Ch. N · status"), then an "Add tracker" row. Renders bare rows —
 * the host supplies the surrounding panel chrome.
 */
@Composable
fun TrackingPanel(
    state: TrackingPanelState,
    onOpenTracker: (TrackItem) -> Unit,
    onAddTracker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = remember(state.trackItems) { trackingPanelRows(state.trackItems) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (state.loading) {
            repeat(2) {
                ShimmerBox(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(TsuzukiCorners.group)),
                )
            }
            return@Column
        }
        if (rows.none { it.track != null }) {
            TsuzukiText(
                "No linked trackers yet",
                Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                TsuzukiTheme.typography.subhead,
                TsuzukiTheme.colors.secondary,
            )
        }
        rows.forEach { item ->
            val track = item.track
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(TsuzukiCorners.group))
                    .clickable(onClick = { onOpenTracker(item) })
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TrackLogoIcon(tracker = item.tracker)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    TsuzukiText(item.tracker.name, style = TsuzukiTheme.typography.callout, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TsuzukiText(
                        if (track != null) {
                            val chapter = trackingPanelChapterLabel(track)
                            val status = item.tracker.getStatus(track.status)?.let { stringResource(it) }
                            listOfNotNull(chapter, status).joinToString(" · ").ifEmpty { "Not started" }
                        } else {
                            "Not linked"
                        },
                        style = TsuzukiTheme.typography.caption1,
                        color = TsuzukiTheme.colors.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TsuzukiChevron()
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(TsuzukiCorners.group))
                .clickable(onClick = onAddTracker)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(28.dp).background(TsuzukiTheme.colors.elevated, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(TsuzukiIcons.Plus, null, Modifier.size(16.dp), tint = TsuzukiTheme.colors.text)
            }
            TsuzukiText("Add tracker", style = TsuzukiTheme.typography.callout, modifier = Modifier.weight(1f))
        }
    }
}
