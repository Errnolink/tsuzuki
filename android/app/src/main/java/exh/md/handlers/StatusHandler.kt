// Adapted from Neko (https://github.com/nekomangaorg/Neko), Apache License 2.0. Modified for Tsuzuki.
package exh.md.handlers

import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.online.all.MangaDex
import exh.md.MangaDexPreferences
import exh.md.utils.MdUtil
import exh.source.MERGED_SOURCE_ID
import exh.source.getMainSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.chapter.service.ChapterReadSync
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaMergeRepository
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap

class StatusHandler(
    private val chapters: ChapterRepository,
    private val manga: MangaRepository,
    private val sources: SourceManager,
    private val trackers: TrackerManager,
    private val preferences: MangaDexPreferences = MangaDexPreferences(),
    private val merges: MangaMergeRepository = Injekt.get(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : ChapterReadSync {
    private val pending = ConcurrentHashMap<Long, ChapterUpdate>()
    private val queue = Channel<List<ChapterUpdate>>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (updates in queue) {
                try {
                    push(updates)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "Could not sync MangaDex chapter state" }
                } finally {
                    updates.forEach { pending.remove(it.id, it) }
                }
            }
        }
    }

    override fun onReadStatusChanged(chapters: List<ChapterUpdate>) {
        val sync = preferences.readingSync().get() && trackers.mdList.isLoggedIn
        val dynamic = preferences.dynamicCovers().get()
        if (!sync && !dynamic) return
        val changes = chapters.filter { (sync && it.read != null) || (dynamic && (it.read == true || it.lastPageRead != null)) }
        if (changes.isEmpty()) return
        changes.filter { sync && it.read != null }.forEach { pending[it.id] = it }
        queue.trySend(changes)
    }

    private suspend fun push(updates: List<ChapterUpdate>) {
        val byId = updates.associateBy { it.id }
        updates.mapNotNull { chapters.getChapterById(it.id) }.groupBy { it.mangaId }.forEach { (id, entries) ->
            val title = manga.getMangaById(id)
            val candidates = if (title.source == MERGED_SOURCE_ID) merges.getMergedMangaById(id) else listOf(title)
            candidates.forEach candidateLoop@ { candidate ->
                val source = sources.get(candidate.source)?.getMainSource<MangaDex>() ?: return@candidateLoop
                val owned = if (candidate.id == id) entries else {
                    val urls = chapters.getChapterByMangaId(candidate.id).map { it.url }.toSet()
                    entries.filter { it.url in urls }
                }
                if (preferences.readingSync().get() && source.isLogged()) {
                    val changes = owned.mapNotNull { chapter ->
                        val update = byId.getValue(chapter.id)
                        update.read?.takeIf { pending[chapter.id] === update }?.let { MdUtil.getChapterId(chapter.url) to it }
                    }.toMap()
                    if (changes.isNotEmpty()) {
                        MangaDexFeatures(source, preferences).markRead(
                            MdUtil.getMangaId(candidate.url),
                            changes.filterValues { it }.keys.toList(),
                            changes.filterValues { !it }.keys.toList(),
                        )
                    }
                }
                if (preferences.dynamicCovers().get()) {
                    owned.lastOrNull { it.read || it.lastPageRead > 0 }?.let {
                        MangaDexDynamicCovers.update(candidate, it, source)
                    }
                }
            }
        }
    }

    suspend fun pull(title: Manga) {
        if (!preferences.readingSync().get() || !trackers.mdList.isLoggedIn) return
        val candidates = if (title.source == MERGED_SOURCE_ID) merges.getMergedMangaById(title.id) else listOf(title)
        candidates.forEach { candidate ->
            val source = sources.get(candidate.source)?.getMainSource<MangaDex>() ?: return@forEach
            try {
                val readIds = MangaDexFeatures(source, preferences).readMarkers(MdUtil.getMangaId(candidate.url))
                val changes = chapters.getChapterByMangaId(candidate.id).mapNotNull { chapter ->
                    if (!chapter.read && pending[chapter.id]?.read != false && MdUtil.getChapterId(chapter.url) in readIds) {
                        ChapterUpdate(id = chapter.id, read = true)
                    } else {
                        null
                    }
                }
                if (changes.isNotEmpty()) chapters.updateAll(changes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.WARN, e) { "Could not retrieve MangaDex read markers" }
            }
        }
    }
}
