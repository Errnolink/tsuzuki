package dev.errnolink.tsuzuki.ui.manga.track

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import dev.errnolink.tsuzuki.designsystem.CoverImage
import dev.errnolink.tsuzuki.designsystem.ContextMenu
import dev.errnolink.tsuzuki.designsystem.ContextMenuAction
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.SearchField
import dev.errnolink.tsuzuki.designsystem.SheetDetent
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.rememberCoverArrivalState
import dev.errnolink.tsuzuki.ui.shell.ShellEmpty
import dev.errnolink.tsuzuki.ui.shell.ShellLoading
import eu.kanade.presentation.track.components.TrackLogoIcon
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.util.system.openInBrowser
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.QuerySanitizer.sanitize
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class TrackerSearchScreen(
    private val mangaId: Long,
    private val initialQuery: String,
    private val currentUrl: String?,
    private val serviceId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val tracker = remember(serviceId) { Injekt.get<TrackerManager>().get(serviceId) }
        LaunchedEffect(tracker) {
            if (tracker == null) navigator.pop()
        }
        if (tracker == null) return
        val screenModel = rememberScreenModel {
            Model(
                mangaId = mangaId,
                currentUrl = currentUrl,
                initialQuery = initialQuery,
                tracker = tracker,
            )
        }
        val state by screenModel.state.collectAsState()
        var query by rememberSaveable { mutableStateOf(initialQuery) }
        val onConfirmSelection: (Boolean) -> Unit = f@{ private: Boolean ->
            val selected = state.selected ?: return@f
            selected.private = private
            screenModel.registerTracking(selected)
            navigator.pop()
        }
        DetentSheet(
            true,
            tracker.name,
            navigator::pop,
            initialDetent = SheetDetent.Large,
            scrollContent = false,
        ) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                onSearch = { screenModel.trackingSearch(query) },
                placeholder = stringResource(MR.strings.action_search_hint),
            )
            val result = state.queryResult
            when {
                result == null -> ShellLoading()
                else -> {
                    val availableTracks = result.getOrNull()
                    if (availableTracks != null) {
                        if (availableTracks.isEmpty()) {
                            ShellEmpty(stringResource(MR.strings.no_results_found), "")
                        } else {
                            LazyColumn(
                                flingBehavior = dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior(),
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 4.dp),
                            ) {
                                items(
                                    items = availableTracks,
                                    key = { "tracker-search-${it.hashCode()}" },
                                ) { track ->
                                    SearchResultRow(
                                        trackSearch = track,
                                        tracker = tracker,
                                        selected = track == state.selected,
                                        onClick = { screenModel.updateSelection(track) },
                                    )
                                }
                            }
                        }
                    } else {
                        ShellEmpty(
                            result.exceptionOrNull()?.message ?: stringResource(MR.strings.unknown_error),
                            "",
                        )
                    }
                }
            }
            if (state.selected != null) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PillButton(
                        stringResource(MR.strings.action_track),
                        { onConfirmSelection(false) },
                        modifier = Modifier.weight(1f),
                    )
                    if (screenModel.supportsPrivateTracking) {
                        PillButton(stringResource(MR.strings.action_toggle_private_on), { onConfirmSelection(true) })
                    }
                }
            }
        }
    }

    private class Model(
        private val mangaId: Long,
        private val currentUrl: String? = null,
        initialQuery: String,
        private val tracker: Tracker,
    ) : StateScreenModel<Model.State>(State()) {

        val supportsPrivateTracking = tracker.supportsPrivateTracking

        init {
            // Run search on first launch
            if (initialQuery.isNotBlank()) {
                trackingSearch(initialQuery)
            }
        }

        fun trackingSearch(query: String) {
            screenModelScope.launch {
                // To show loading state
                mutableState.update { it.copy(queryResult = null, selected = null) }

                val result = withIOContext {
                    try {
                        val results = tracker.search(query.sanitize())
                        Result.success(results)
                    } catch (e: Throwable) {
                        Result.failure(e)
                    }
                }
                mutableState.update { oldState ->
                    oldState.copy(
                        queryResult = result,
                        selected = result.getOrNull()?.find { it.tracking_url == currentUrl },
                    )
                }
            }
        }

        fun registerTracking(item: TrackSearch) {
            screenModelScope.launchNonCancellable { tracker.register(item, mangaId) }
        }

        fun updateSelection(selected: TrackSearch) {
            mutableState.update { it.copy(selected = selected) }
        }

        data class State(
            val queryResult: Result<List<TrackSearch>>? = null,
            val selected: TrackSearch? = null,
        )
    }
}

@Composable
private fun SearchResultRow(
    trackSearch: TrackSearch,
    tracker: Tracker,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val colors = TsuzukiTheme.colors
    val type = trackSearch.publishing_type.lowercase().replaceFirstChar { it.uppercase() }
    val status = trackSearch.publishing_status.lowercase().replaceFirstChar { it.uppercase() }
    val description = trackSearch.summary.trim()
    val credits = (trackSearch.authors + trackSearch.artists).distinct().joinToString()
    ContextMenu(
        label = trackSearch.title,
        actions = listOf(
            ContextMenuAction(stringResource(MR.strings.action_copy_to_clipboard)) {
                scope.launch {
                    clipboard.setClipEntry(
                        ClipData.newPlainText(trackSearch.title, trackSearch.title).toClipEntry(),
                    )
                }
            },
            ContextMenuAction(stringResource(MR.strings.action_open_in_browser)) {
                val url = trackSearch.tracking_url
                if (url.isNotBlank()) {
                    context.openInBrowser(url)
                }
            },
        ),
        onClick = onClick,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(TsuzukiCorners.group))
                .background(colors.grouped)
                .border(2.dp, if (selected) colors.selectedFill else Color.Transparent, RoundedCornerShape(TsuzukiCorners.group))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                SearchCover(url = trackSearch.cover_url)
                Spacer(Modifier.width(12.dp))
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    TsuzukiText(
                        trackSearch.title,
                        style = TsuzukiTheme.typography.headline,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (credits.isNotBlank()) {
                        TsuzukiText(
                            credits,
                            style = TsuzukiTheme.typography.footnote,
                            color = colors.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (type.isNotBlank()) {
                        ResultDetail(stringResource(MR.strings.track_type), type)
                    }
                    if (trackSearch.start_date.isNotBlank()) {
                        ResultDetail(stringResource(MR.strings.label_started), trackSearch.start_date)
                    }
                    if (status.isNotBlank()) {
                        ResultDetail(stringResource(MR.strings.track_status), status)
                    }
                    if (trackSearch.score != -1.0) {
                        ResultDetail(stringResource(MR.strings.score), trackSearch.score.toString())
                    }
                }
                TrackLogoIcon(tracker)
            }
            if (description.isNotBlank()) {
                TsuzukiText(
                    description,
                    style = TsuzukiTheme.typography.footnote,
                    color = colors.secondary,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ResultDetail(title: String, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TsuzukiText(
            title,
            style = TsuzukiTheme.typography.footnote,
            color = TsuzukiTheme.colors.secondary,
            maxLines = 1,
        )
        TsuzukiText(
            text,
            style = TsuzukiTheme.typography.footnote,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SearchCover(url: String) {
    val arrivals = rememberCoverArrivalState()
    var loaded by remember(url) { mutableStateOf(false) }
    CoverImage(
        imageKey = url,
        aspectRatio = 2f / 3f,
        loaded = loaded,
        arrivals = arrivals,
        modifier = Modifier.height(96.dp),
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            onSuccess = { loaded = true },
            onError = { loaded = true },
        )
    }
}
