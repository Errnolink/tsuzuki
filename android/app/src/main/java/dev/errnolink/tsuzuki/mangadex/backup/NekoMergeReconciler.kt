package dev.errnolink.tsuzuki.mangadex.backup

import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaRestorer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.builtins.ListSerializer
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.manga.MangaMapper
import tachiyomi.data.manga.MergedMangaMapper
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URI

object NekoMergeReconciler {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun enqueueAfterExtensionChange() {
        scope.launch { reconcileOnce() }
    }

    data class Summary(val linkedChapters: Int, val touchedTitles: Int)

    private val handler: DatabaseHandler by lazy { Injekt.get() }
    private val sourceManager: SourceManager by lazy { Injekt.get() }
    private val getMangaByUrlAndSourceId: GetMangaByUrlAndSourceId by lazy { Injekt.get() }


    suspend fun reconcileOnce(): Summary {
        val repaired = repairMergeDuplicates()
        val candidates = handler.awaitList { mangasQueries.getAll(MangaMapper::mapManga) }
            .filter { NekoBackupMapper.UNRESOLVED_CHAPTERS in it.memo.keys }
        if (candidates.isEmpty()) return Summary(0, repaired)

        sourceManager.isInitialized.first { it }
        val sources = (sourceManager.getAll() + sourceManager.getStubSources()).distinctBy { it.id }.map {
            NekoBackupMapper.SourceIdentity(it.id, it.name, it.lang, (it as? eu.kanade.tachiyomi.source.online.HttpSource)?.baseUrl)
        }
        if (sources.isEmpty()) return Summary(0, repaired)

        var linkedChapters = 0
        var touchedTitles = 0
        for (manga in candidates) {
            try {
                val count = reconcileManga(manga, sources)
                if (count > 0) {
                    linkedChapters += count
                    touchedTitles += 1
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Neko merge reconcile failed for ${manga.title}" }
            }
        }
        return Summary(linkedChapters, touchedTitles + repaired)
    }

    private suspend fun repairMergeDuplicates(): Int {
        val ids = handler.awaitList {
            mergedQueries.getFavoriteMergeChildren(exh.source.MERGED_SOURCE_ID)
        }
        if (ids.isEmpty()) return 0
        handler.await(inTransaction = true) {
            ids.forEach { id ->
                mangas_categoriesQueries.deleteMangaCategoryByMangaId(id)
            }
            mangasQueries.clearFavoriteByIds(ids)
        }
        logcat(LogPriority.WARN) { "Neko merge repair hid ${ids.size} duplicate merge children" }
        return ids.size
    }

    private suspend fun reconcileManga(manga: Manga, sources: List<NekoBackupMapper.SourceIdentity>): Int {
        val memo = manga.memo
        val unresolved = memo[NekoBackupMapper.UNRESOLVED_CHAPTERS]
            ?.let { runCatching { Json.decodeFromJsonElement(ListSerializer(BackupChapter.serializer()), it) }.getOrNull() }
            .orEmpty()
        if (unresolved.isEmpty()) return 0
        val merges = memo[NekoBackupMapper.MERGE_MANGA_LIST]
            ?.let { runCatching { Json.decodeFromJsonElement(ListSerializer(BackupMergeManga.serializer()), it) }.getOrNull() }
            .orEmpty()

        val bySource = unresolved.groupBy { chapter ->
            MemoColumnAdapter.decode(chapter.memo)[NekoBackupMapper.UNRESOLVED_SOURCE]?.jsonPrimitive?.content
        }
        val references = handler.awaitList { mergedQueries.selectByMergeUrl(manga.url, MergedMangaMapper::map) }
        val parent = references.firstOrNull() ?: return 0

        var linked = 0
        var remaining = unresolved.toList()
        for ((name, chapters) in bySource) {
            if (name == null) continue
            val merge = merges.lastOrNull { NekoBackupMapper.sourceName(it.mergeType) == name } ?: continue
            val mergeHost = runCatching { URI(merge.url).host?.lowercase() }.getOrNull()
            val source = NekoBackupMapper.resolveSource(name, mergeHost, sources) ?: continue
            val childUrl = NekoBackupMapper.relativeUrl(merge.url, source.baseUrl)
            if (references.any { it.mangaSourceId == source.id && it.mangaUrl == childUrl }) {
                linked += dropChapters(manga, chapters)
                remaining = remaining - chapters.toSet()
                continue
            }

            val child = BackupManga(
                source = source.id,
                url = childUrl,
                title = merge.title.ifBlank { manga.title },
                thumbnailUrl = merge.coverUrl.takeIf(String::isNotBlank),
                favorite = false,
                chapters = chapters.map { chapter ->
                    val chapterMemo = MemoColumnAdapter.decode(chapter.memo) - NekoBackupMapper.UNRESOLVED_SOURCE
                    chapter.copy(memo = MemoColumnAdapter.encode(JsonObject(chapterMemo)))
                },
            )
            MangaRestorer().restore(child, emptyList())
            handler.await(inTransaction = true) {
                mergedQueries.insert(
                    infoManga = false,
                    getChapterUpdates = true,
                    chapterSortMode = 0,
                    chapterPriority = 0,
                    downloadChapters = true,
                    mergeId = parent.mergeId ?: 0L,
                    mergeUrl = parent.mergeUrl,
                    mangaId = getMangaByUrlAndSourceId.await(childUrl, source.id)?.id,
                    mangaUrl = childUrl,
                    mangaSource = source.id,
                )
            }
            linked += dropChapters(manga, chapters)
            remaining = remaining - chapters.toSet()
        }

        if (linked > 0) {
            val updated = memo.toMutableMap()
            if (remaining.isEmpty()) {
                updated -= NekoBackupMapper.UNRESOLVED_CHAPTERS
            } else {
                updated[NekoBackupMapper.UNRESOLVED_CHAPTERS] = Json.encodeToJsonElement(ListSerializer(BackupChapter.serializer()), remaining)
            }
            handler.await {
                mangasQueries.updateMemoById(JsonObject(updated), manga.id)
            }
        }
        return linked
    }

    private suspend fun dropChapters(manga: Manga, chapters: List<BackupChapter>): Int {
        if (chapters.isEmpty()) return 0
        val urls = chapters.mapTo(hashSetOf()) { it.url }
        val ids = handler.awaitList {
            chaptersQueries.getChapterIdsByMangaIdAndUrls(manga.id, urls)
        }
        if (ids.isNotEmpty()) {
            handler.await(inTransaction = true) { chaptersQueries.removeChaptersWithIds(ids) }
        }
        return ids.size
    }
}
