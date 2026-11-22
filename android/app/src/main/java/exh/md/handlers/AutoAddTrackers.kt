// Adapted from Neko (https://github.com/nekomangaorg/Neko), Apache License 2.0. Modified for Tsuzuki.
package exh.md.handlers

import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.domain.track.model.toDomainTrack
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.online.all.MangaDex
import exh.md.MangaDexPreferences
import exh.md.service.MangaDexFeatureRequests
import exh.md.service.MangaDexService
import exh.md.utils.FollowStatus
import exh.md.utils.MdUtil
import exh.source.MERGED_SOURCE_ID
import exh.source.getMainSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import logcat.LogPriority
import okhttp3.Request
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaMergeRepository
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.repository.TrackRepository
import uy.kohesive.injekt.injectLazy

object AutoAddTrackers {
    private val merges: MangaMergeRepository by injectLazy()
    private val mangaRepository: MangaRepository by injectLazy()
    private val trackRepository: TrackRepository by injectLazy()
    private val sourceManager: SourceManager by injectLazy()
    private val trackerManager: TrackerManager by injectLazy()
    private val addTracks: AddTracks by injectLazy()
    private val network: NetworkHelper by injectLazy()
    private val preferences = MangaDexPreferences()
    private val queue = Channel<Long>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val eventBus = MutableSharedFlow<AutoTrackLinkedEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<AutoTrackLinkedEvent> = eventBus

    private val binder = newBinder { work -> work() }

    init {
        scope.launch {
            for (id in queue) {
                try {
                    bind(id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "Could not auto-bind MangaDex tracker links" }
                }
            }
        }
    }

    fun onAddedToLibrary(mangaId: Long) {
        queue.trySend(mangaId)
    }

    fun serviceTrackers(): Map<String, BaseTracker> = mapOf(
        "al" to trackerManager.aniList,
        "mal" to trackerManager.myAnimeList,
        "kt" to trackerManager.kitsu,
        "mu" to trackerManager.mangaUpdates,
    )

    fun serviceTrackerIds() = mapOf(
        "al" to trackerManager.aniList.id,
        "mal" to trackerManager.myAnimeList.id,
        "kt" to trackerManager.kitsu.id,
        "mu" to trackerManager.mangaUpdates.id,
    )

    fun signedInServiceCodes(): Set<String> = serviceTrackers().filterValues { it.isLoggedIn }.keys

    fun effectiveServicesNow(): Set<String> = MangaDexPreferences.effectiveServices(
        signedInServiceCodes(),
        preferences.autoTrackDisabled().get(),
    )

    private suspend fun bind(mangaId: Long) {
        val manga = mangaRepository.getMangaById(mangaId)
        if (!manga.favorite) return
        val mdManga = resolveMdManga(manga) ?: return
        val source = sourceManager.get(mdManga.source)?.getMainSource<MangaDex>() ?: return
        bindMdList(manga, mdManga, source)
        binder.bind(mangaId, effectiveServicesNow())
    }

    private suspend fun resolveMdManga(manga: Manga): Manga? {
        val mdManga = if (manga.source == MERGED_SOURCE_ID) {
            merges.getMergedMangaById(manga.id)
                .firstOrNull { sourceManager.get(it.source)?.getMainSource<MangaDex>() != null }
                ?: return null
        } else {
            manga
        }
        return mdManga.takeIf { sourceManager.get(it.source)?.getMainSource<MangaDex>() != null }
    }

    private suspend fun bindMdList(manga: Manga, mdManga: Manga, source: MangaDex) {
        val existing = trackRepository.getTracksByMangaId(manga.id)
        val mdList = trackerManager.mdList
        if (mdList.isLoggedIn) {
            val track = existing.firstOrNull { it.trackerId == mdList.id }?.toDbTrack()
                ?: mdList.createInitialTracker(manga, mdManga)
            if (existing.none { it.trackerId == mdList.id }) {
                trackRepository.insert(requireNotNull(track.toDomainTrack(idRequired = false)))
            }
            try {
                mdList.refresh(track)
                val status = MangaDexPreferences.autoFollowStatus(preferences.autoAddToLibrary().get())
                if (status != null && track.status == FollowStatus.UNFOLLOWED.long) {
                    check(source.updateFollowStatus(MdUtil.getMangaId(mdManga.url), status)) {
                        "MangaDex follow update failed"
                    }
                    track.status = status.long
                }
                trackRepository.insert(requireNotNull(track.toDomainTrack(idRequired = false)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Could not auto-add the MangaDex library track" }
            }
        }
    }

    internal fun newBinder(gate: suspend (suspend () -> AutoTrackTitleInfo) -> AutoTrackTitleInfo): AutoTrackBinder =
        AutoTrackBinder(
            resolveTitle = ::resolveTitle,
            viewManga = { title -> gate { fetchTitleInfo(title) } },
            searchById = ::searchTrackerId,
            tracksOf = ::trackerIdsOf,
            trackerIds = serviceTrackerIds(),
            ratings = { preferences.autoTrackRatings().get() },
            bind = ::bindTracker,
            onBound = ::emitEvent,
        )

    private suspend fun resolveTitle(mangaId: Long): AutoTrackTitle? {
        val manga = mangaRepository.getMangaById(mangaId)
        if (!manga.favorite) return null
        val mdManga = resolveMdManga(manga) ?: return null
        val source = sourceManager.get(mdManga.source)?.getMainSource<MangaDex>() ?: return null
        return AutoTrackTitle(mangaId, mdManga.url, source)
    }

    private suspend fun fetchTitleInfo(title: AutoTrackTitle): AutoTrackTitleInfo {
        val source = requireNotNull(title.source)
        val attributes = MangaDexService(source.client, MangaDexFeatureRequests.headers)
            .viewManga(MdUtil.getMangaId(title.mdUrl)).data.attributes
        return AutoTrackTitleInfo(attributes.contentRating.orEmpty(), attributes.links)
    }

    private suspend fun searchTrackerId(key: String, id: String): TrackSearch? =
        if (key == "kt") kitsuById(id) else serviceTrackers()[key]?.searchById(id)

    private suspend fun trackerIdsOf(mangaId: Long): Set<Long> =
        trackRepository.getTracksByMangaId(mangaId).map { it.trackerId }.toSet()

    private suspend fun bindTracker(key: String, track: TrackSearch, mangaId: Long) {
        val tracker = serviceTrackers()[key] ?: return
        if (mangaRepository.getMangaById(mangaId).favorite &&
            trackRepository.getTracksByMangaId(mangaId).none { it.trackerId == tracker.id }
        ) {
            track.manga_id = mangaId
            addTracks.bind(tracker, track, mangaId)
        }
    }

    private suspend fun emitEvent(mangaId: Long, key: String) {
        eventBus.emit(
            AutoTrackLinkedEvent(
                mangaId = mangaId,
                service = key,
                trackerName = MangaDexPreferences.serviceNames.getValue(key),
                trackerId = serviceTrackerIds().getValue(key),
            ),
        )
    }

    private suspend fun kitsuById(id: String): TrackSearch = network.client
        .newCall(MangaDexFeatureRequests.kitsu(id)).awaitSuccess().use { response ->
            MangaDexFeatureMapper.kitsuTrack(response.body.string())
        }
}

data class AutoTrackTitle(
    val mangaId: Long,
    val mdUrl: String,
    val source: MangaDex?,
)

data class AutoTrackTitleInfo(
    val contentRating: String,
    val links: JsonElement?,
)

data class AutoTrackLinkedEvent(
    val mangaId: Long,
    val service: String,
    val trackerName: String,
    val trackerId: Long,
)

sealed interface AutoTrackResult {
    data class Linked(val services: Set<String>, val noLink: Set<String>) : AutoTrackResult
    data class NoLink(val services: Set<String>) : AutoTrackResult
    data object SkippedByRating : AutoTrackResult
    data object NotMangaDex : AutoTrackResult
    data class Failed(val retryable: Boolean) : AutoTrackResult
}

class AutoTrackBinder(
    private val resolveTitle: suspend (Long) -> AutoTrackTitle?,
    private val viewManga: suspend (AutoTrackTitle) -> AutoTrackTitleInfo,
    private val searchById: suspend (String, String) -> TrackSearch?,
    private val tracksOf: suspend (Long) -> Set<Long>,
    private val trackerIds: Map<String, Long>,
    private val ratings: () -> Set<String>,
    private val bind: suspend (String, TrackSearch, Long) -> Unit,
    private val onBound: suspend (Long, String) -> Unit = { _, _ -> },
    private val logFailure: (String, Throwable) -> Unit = { _, _ -> },
) {
    suspend fun bind(mangaId: Long, services: Set<String>): AutoTrackResult {
        val title = try {
            resolveTitle(mangaId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logFailure("resolve title $mangaId", e)
            return AutoTrackResult.Failed(retryable = true)
        } ?: return AutoTrackResult.NotMangaDex
        val bindId = title.mangaId
        val tracked = tracksOf(bindId)
        val missing = services.filter { key -> trackerIds[key] !in tracked }
        if (missing.isEmpty()) return AutoTrackResult.Linked(emptySet(), emptySet())
        val info = try {
            viewManga(title)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logFailure("view manga $mangaId", e)
            return AutoTrackResult.Failed(retryable = true)
        }
        if (info.contentRating !in ratings()) return AutoTrackResult.SkippedByRating
        val links = MangaDexFeatureMapper.trackerLinks(info.links)
        val linked = mutableSetOf<String>()
        val noLink = mutableSetOf<String>()
        val deadLinks = mutableSetOf<String>()
        var failedLookup = false
        for (key in missing) {
            val id = links[key]
            if (id == null) {
                noLink += key
                continue
            }
            val track = try {
                searchById(key, id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logFailure("search ${MangaDexPreferences.serviceNames[key]} for $mangaId", e)
                failedLookup = true
                continue
            }
            if (track == null) {
                deadLinks += key
                continue
            }
            try {
                bind(key, track, bindId)
                linked += key
                onBound(bindId, key)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logFailure("bind ${MangaDexPreferences.serviceNames[key]} for $mangaId", e)
                failedLookup = true
            }
        }
        return when {
            linked.isEmpty() && noLink.isEmpty() && deadLinks.isEmpty() && failedLookup -> AutoTrackResult.Failed(retryable = true)
            linked.isEmpty() && noLink.isEmpty() -> AutoTrackResult.Failed(retryable = false)
            linked.isEmpty() -> AutoTrackResult.NoLink(noLink + deadLinks)
            else -> AutoTrackResult.Linked(linked, noLink + deadLinks)
        }
    }
}
