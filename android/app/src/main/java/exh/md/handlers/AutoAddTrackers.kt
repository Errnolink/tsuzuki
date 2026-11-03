// Adapted from Neko (https://github.com/nekomangaorg/Neko), Apache License 2.0. Modified for Tsuzuki.
package exh.md.handlers

import eu.kanade.domain.track.model.toDomainTrack
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.kitsu.KitsuApi
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.online.all.MangaDex
import exh.md.MangaDexPreferences
import exh.md.utils.FollowStatus
import exh.md.service.MangaDexFeatureRequests
import exh.md.service.MangaDexService
import exh.md.utils.MdUtil
import exh.source.MERGED_SOURCE_ID
import tachiyomi.domain.manga.repository.MangaMergeRepository
import exh.source.getMainSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import logcat.LogPriority
import okhttp3.Request
import tachiyomi.core.common.util.system.logcat
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

    private suspend fun bind(mangaId: Long) {
        val manga = mangaRepository.getMangaById(mangaId)
        if (!manga.favorite) return
        val mdManga = if (manga.source == MERGED_SOURCE_ID) {
            merges.getMergedMangaById(mangaId).firstOrNull { sourceManager.get(it.source)?.getMainSource<MangaDex>() != null } ?: return
        } else manga
        val source = sourceManager.get(mdManga.source)?.getMainSource<MangaDex>() ?: return
        val existing = trackRepository.getTracksByMangaId(mangaId)
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
                    check(source.updateFollowStatus(MdUtil.getMangaId(mdManga.url), status)) { "MangaDex follow update failed" }
                    track.status = status.long
                }
                trackRepository.insert(requireNotNull(track.toDomainTrack(idRequired = false)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Could not auto-add the MangaDex library track" }
            }
        }
        val selected = preferences.autoTrackServices().get()
        val services = mapOf(
            "al" to trackerManager.aniList, "mal" to trackerManager.myAnimeList,
            "kt" to trackerManager.kitsu, "mu" to trackerManager.mangaUpdates,
        ).filter { (key, tracker) -> tracker.isLoggedIn && key in selected }
        val tracked = existing.map { it.trackerId }.toSet()
        val missing = services.filterValues { it.id !in tracked }
        if (missing.isEmpty()) return
        val attributes = MangaDexService(source.client, MangaDexFeatureRequests.headers)
            .viewManga(MdUtil.getMangaId(mdManga.url)).data.attributes
        if (attributes.contentRating !in preferences.autoTrackRatings().get()) return
        val links = MangaDexFeatureMapper.trackerLinks(attributes.links)
        missing.forEach { (key, tracker) ->
            val id = links[key] ?: return@forEach
            try {
                val track = if (key == "kt") kitsuById(id) else tracker.searchById(id)
                if (track != null && mangaRepository.getMangaById(mangaId).favorite &&
                    trackRepository.getTracksByMangaId(mangaId).none { it.trackerId == tracker.id }
                ) {
                    track.manga_id = mangaId
                    addTracks.bind(tracker, track, mangaId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Could not auto-bind ${tracker.name} from MangaDex" }
            }
        }
    }

    private suspend fun kitsuById(id: String): TrackSearch = network.client
        .newCall(MangaDexFeatureRequests.kitsu(id)).awaitSuccess().use { response ->
            MangaDexFeatureMapper.kitsuTrack(response.body.string())
        }
}
