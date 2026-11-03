package eu.kanade.tachiyomi.ui.manga.track

import android.app.Application
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.ui.details.ShimmerBox
import dev.errnolink.tsuzuki.ui.manga.track.TrackChapterSelectorScreen as TsuzukiTrackChapterSelectorScreen
import dev.errnolink.tsuzuki.ui.manga.track.TrackDateRemoverScreen as TsuzukiTrackDateRemoverScreen
import dev.errnolink.tsuzuki.ui.manga.track.TrackDateSelectorScreen as TsuzukiTrackDateSelectorScreen
import dev.errnolink.tsuzuki.ui.manga.track.TrackScoreSelectorScreen as TsuzukiTrackScoreSelectorScreen
import dev.errnolink.tsuzuki.ui.manga.track.TrackStatusSelectorScreen as TsuzukiTrackStatusSelectorScreen
import dev.errnolink.tsuzuki.ui.manga.track.TrackerRemoveScreen as TsuzukiTrackerRemoveScreen
import dev.errnolink.tsuzuki.ui.manga.track.TrackerSearchScreen as TsuzukiTrackerSearchScreen
import eu.kanade.domain.track.interactor.RefreshTracks
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.track.TrackInfoDialogHome
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.source.online.all.MergedSource
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import exh.metadata.metadata.base.TrackerIdMetadata
import exh.source.MERGED_SOURCE_ID
import exh.source.getMainSource
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.interactor.GetFlatMetadataById
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.GetMergedReferencesById
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.model.Track
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

data class TrackInfoDialogHomeScreen(
    private val mangaId: Long,
    private val mangaTitle: String,
    private val sourceId: Long,
    private val embedded: Boolean = false,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { Model(mangaId, sourceId) }

        if (embedded) {
            TrackInfoHomeBody(
                modifier = Modifier.fillMaxSize().padding(top = 4.dp),
                screenModel = screenModel,
                mangaTitle = mangaTitle,
            )
        } else {
            Box(Modifier.fillMaxSize().background(TsuzukiTheme.colors.background)) {
                dev.errnolink.tsuzuki.ui.shell.DeclareShellTop(
                    title = "Tracking",
                    navigateUp = navigator::pop,
                )
                TrackInfoHomeBody(
                    modifier = Modifier.fillMaxSize().padding(top = topInsetPadding()),
                    screenModel = screenModel,
                    mangaTitle = mangaTitle,
                )
            }
        }
    }

    @Composable
    private fun TrackInfoHomeBody(
        modifier: Modifier = Modifier,
        screenModel: Model,
        mangaTitle: String,
    ) {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val dateFormat = remember { UiPreferences.dateFormat(Injekt.get<UiPreferences>().dateFormat().get()) }
        val state by screenModel.state.collectAsState()

        if (state.isLoading) {
            TrackingSkeleton(modifier.fillMaxWidth().padding(horizontal = 20.dp))
        } else {
            TrackInfoDialogHome(
                modifier = modifier,
                    trackItems = state.trackItems,
                    dateFormat = dateFormat,
                    onStatusClick = {
                        navigator.push(
                            TrackStatusSelectorScreen(
                                track = it.track!!,
                                serviceId = it.tracker.id,
                            ),
                        )
                    },
                    onChapterClick = {
                        navigator.push(
                            TrackChapterSelectorScreen(
                                track = it.track!!,
                                serviceId = it.tracker.id,
                            ),
                        )
                    },
                    onScoreClick = {
                        navigator.push(
                            TrackScoreSelectorScreen(
                                track = it.track!!,
                                serviceId = it.tracker.id,
                            ),
                        )
                    },
                    onStartDateEdit = {
                        navigator.push(
                            TrackDateSelectorScreen(
                                track = it.track!!,
                                serviceId = it.tracker.id,
                                start = true,
                            ),
                        )
                    },
                    onEndDateEdit = {
                        navigator.push(
                            TrackDateSelectorScreen(
                                track = it.track!!,
                                serviceId = it.tracker.id,
                                start = false,
                            ),
                        )
                    },
                    onNewSearch = {
                        if (it.tracker is EnhancedTracker) {
                            screenModel.registerEnhancedTracking(it)
                        } else {
                            // SY -->
                            screenModel.newSearch(navigator, it, mangaTitle)
                            // SY <--
                        }
                    },
                    onOpenInBrowser = { openTrackerInBrowser(context, it) },
                    onRemoved = {
                        navigator.push(
                            TrackerRemoveScreen(
                                mangaId = mangaId,
                                track = it.track!!,
                                serviceId = it.tracker.id,
                            ),
                        )
                    },
                    onCopyLink = { context.copyTrackerLink(it) },
                    onTogglePrivate = screenModel::togglePrivate,
                )
        }
    }

    @Composable
    private fun topInsetPadding(): Dp =
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 56.dp

    /**
     * Opens registered tracker url in browser
     */
    private fun openTrackerInBrowser(context: Context, trackItem: TrackItem) {
        val url = trackItem.track?.remoteUrl ?: return
        if (url.isNotBlank()) {
            context.openInBrowser(url)
        }
    }

    private fun Context.copyTrackerLink(trackItem: TrackItem) {
        val url = trackItem.track?.remoteUrl ?: return
        if (url.isNotBlank()) {
            copyToClipboard(url, url)
        }
    }

    private class Model(
        private val mangaId: Long,
        private val sourceId: Long,
        private val getTracks: GetTracks = Injekt.get(),
        // SY -->
        private val trackerManager: TrackerManager = Injekt.get(),
        private val trackPreferences: TrackPreferences = Injekt.get(),
        // SY <--
        // KMK -->
        private val sourceManager: SourceManager = Injekt.get(),
        // KMK <--
    ) : StateScreenModel<Model.State>(State()) {
        // KMK -->
        private val getFlatMetadataById: GetFlatMetadataById by injectLazy()
        private val getMangaById: GetManga by injectLazy()
        private val getMergedReferencesById: GetMergedReferencesById by injectLazy()
        // KMK <--

        init {
            screenModelScope.launch {
                refreshTrackers()
            }

            screenModelScope.launch {
                getTracks.subscribe(mangaId)
                    .catch { logcat(LogPriority.ERROR, it) }
                    .distinctUntilChanged()
                    .map { it.mapToTrackItem() }
                    .collectLatest { trackItems -> mutableState.update { it.copy(trackItems = trackItems) } }
            }
        }

        // KMK -->
        private suspend fun getMangaForTracking(item: TrackItem): Manga? {
            if (sourceId != MERGED_SOURCE_ID) {
                return getMangaById.await(mangaId)
            }
            item.tracker as EnhancedTracker
            val references = getMergedReferencesById.await(mangaId)
            return references.distinctBy { it.mangaSourceId }.firstNotNullOfOrNull { ref ->
                sourceManager.get(ref.mangaSourceId)
                    ?.takeIf(item.tracker::accept)
                    ?.let { ref.mangaId?.let { mangaId -> getMangaById.await(mangaId) } }
            }
        }
        // KMK <--

        fun registerEnhancedTracking(item: TrackItem) {
            item.tracker as EnhancedTracker
            screenModelScope.launchNonCancellable {
                val manga = getMangaForTracking(item) ?: return@launchNonCancellable
                try {
                    val matchResult = item.tracker.match(manga) ?: throw Exception()
                    item.tracker.register(matchResult, mangaId)
                } catch (_: Exception) {
                    withUIContext { Injekt.get<Application>().toast(MR.strings.error_no_match) }
                }
            }
        }

        // SY -->
        fun newSearch(navigator: Navigator, item: TrackItem, mangaTitle: String) {
            screenModelScope.launchNonCancellable {
                if (trackPreferences.resolveUsingSourceMetadata().get()) {
                    // Check if the tracker id is contained in the metadata
                    val result = getTrackerIdFromMetadata(item.tracker.id)
                    if (result != null) {
                        mutableState.update { it.copy(isLoading = true) }

                        // Try to register tracking by id
                        val success = registerTrackingById(item.tracker.id, result)

                        mutableState.update { it.copy(isLoading = false) }

                        if (success) {
                            // Return on success
                            return@launchNonCancellable
                        }
                    }
                }

                // Open search screen
                navigator.push(
                    TrackerSearchScreen(
                        mangaId = mangaId,
                        initialQuery = item.track?.title ?: mangaTitle,
                        currentUrl = item.track?.remoteUrl,
                        serviceId = item.tracker.id,
                    ),
                )
            }
        }

        suspend fun getTrackerIdFromMetadata(trackerId: Long): String? {
            try {
                val metadataSource = sourceManager.get(sourceId)
                    ?.getMainSource<MetadataSource<*, *>>() ?: return null

                return getFlatMetadataById.await(mangaId)?.run {
                    // Use 'raise' to dynamically obtain the specific metadata type and then attempt to cast
                    raise(metadataSource.metaClass) as? TrackerIdMetadata
                }?.let { metadata ->
                    when (trackerId) {
                        trackerManager.aniList.id -> metadata.anilistId
                        trackerManager.kitsu.id -> metadata.kitsuId
                        trackerManager.myAnimeList.id -> metadata.myAnimeListId
                        trackerManager.mangaUpdates.id -> metadata.mangaUpdatesId
                        else -> null
                    }
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e) { "Failed to get tracker ID from metadata" }
                return null
            }
        }

        suspend fun registerTrackingById(trackerId: Long, remoteId: String): Boolean {
            trackerManager.get(trackerId)?.let { tracker ->
                try {
                    tracker.searchById(remoteId)?.let { track ->
                        tracker.register(track, mangaId)
                        return true
                    }
                } catch (e: Throwable) {
                    logcat(LogPriority.ERROR, e) { "Failed to register tracking by id" }
                }
            }
            return false
        }
        // SY <--

        private suspend fun refreshTrackers() {
            val refreshTracks = Injekt.get<RefreshTracks>()
            val context = Injekt.get<Application>()

            refreshTracks.await(mangaId)
                .filter { it.first != null }
                .forEach { (track, e) ->
                    logcat(LogPriority.ERROR, e) {
                        "Failed to refresh track data mangaId=$mangaId for service ${track!!.id}"
                    }
                    withUIContext {
                        context.toast(
                            context.stringResource(
                                MR.strings.track_error,
                                track!!.name,
                                e.message ?: "",
                            ),
                        )
                    }
                }
        }

        fun togglePrivate(item: TrackItem) {
            screenModelScope.launchNonCancellable {
                item.tracker.setRemotePrivate(item.track!!.toDbTrack(), !item.track.private)
            }
        }

        private suspend fun List<Track>.mapToTrackItem(): List<TrackItem> {
            val loggedInTrackers = trackerManager.loggedInTrackers()
            val source = sourceManager.getOrStub(sourceId)
            return loggedInTrackers
                // Map to TrackItem
                .map { service -> TrackItem(find { it.trackerId == service.id }, service) }
                // Show only if the service supports this manga's source
                // KMK -->
                .let { trackers ->
                    val sources = if (source is MergedSource) {
                        sourceManager.getMergedSources(mangaId)
                    } else {
                        listOf(source)
                    }
                    trackers.filter { (it.tracker as? EnhancedTracker)?.accept(sources) ?: true }
                }
            // KMK <--
        }

        @Immutable
        data class State(
            val trackItems: List<TrackItem> = emptyList(),
            // SY -->
            val isLoading: Boolean = false,
            // SY <--
        )
    }
}

private data class TrackStatusSelectorScreen(
    private val track: Track,
    private val serviceId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        TsuzukiTrackStatusSelectorScreen(track, serviceId).Content()
    }
}

private data class TrackChapterSelectorScreen(
    private val track: Track,
    private val serviceId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        TsuzukiTrackChapterSelectorScreen(track, serviceId).Content()
    }
}

private data class TrackScoreSelectorScreen(
    private val track: Track,
    private val serviceId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        TsuzukiTrackScoreSelectorScreen(track, serviceId).Content()
    }
}

private data class TrackDateSelectorScreen(
    private val track: Track,
    private val serviceId: Long,
    private val start: Boolean,
) : Screen() {

    @Composable
    override fun Content() {
        TsuzukiTrackDateSelectorScreen(track, serviceId, start).Content()
    }
}

private data class TrackDateRemoverScreen(
    private val track: Track,
    private val serviceId: Long,
    private val start: Boolean,
) : Screen() {

    @Composable
    override fun Content() {
        TsuzukiTrackDateRemoverScreen(track, serviceId, start).Content()
    }
}

data class TrackerSearchScreen(
    private val mangaId: Long,
    private val initialQuery: String,
    private val currentUrl: String?,
    private val serviceId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        TsuzukiTrackerSearchScreen(mangaId, initialQuery, currentUrl, serviceId).Content()
    }
}

private data class TrackerRemoveScreen(
    private val mangaId: Long,
    private val track: Track,
    private val serviceId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        TsuzukiTrackerRemoveScreen(mangaId, track, serviceId).Content()
    }
}

@Composable
private fun TrackingSkeleton(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(3) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(TsuzukiCorners.group))
                    .background(TsuzukiTheme.colors.grouped)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ShimmerBox(Modifier.fillMaxWidth(0.42f).height(14.dp))
                repeat(4) { ShimmerBox(Modifier.fillMaxWidth().height(24.dp)) }
            }
        }
    }
}
