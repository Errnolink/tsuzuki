package dev.errnolink.tsuzuki.data.library

import kotlinx.serialization.json.JsonObject
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.util.chapter.getNextUnread
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.SelectExcludedScanlatorsDigest
import tachiyomi.data.SelectUnreadStateDigestByMangaIds
import tachiyomi.data.chapter.ChapterMapper
import tachiyomi.domain.chapter.interactor.GetMergedChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.interactor.GetMergedMangaById
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ContinueTargetsResolver(
    private val handler: DatabaseHandler = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val downloadCache: DownloadCache = Injekt.get(),
    private val getMergedChaptersByMangaId: GetMergedChaptersByMangaId = Injekt.get(),
    private val getMergedMangaById: GetMergedMangaById = Injekt.get(),
) {

    data class Candidate(val libraryItemId: Long, val manga: Manga)

    data class TargetRow(val chapterId: Long, val lastPageRead: Long)

    data class CandidateKey(
        val libraryItemId: Long,
        val chapterFlags: Long,
        val version: Long,
        val ogTitle: String,
        val source: Long,
        val downloadCount: Int,
    )

    data class Digest(
        val candidates: List<CandidateKey>,
        val rows: Map<Long, SelectUnreadStateDigestByMangaIds>,
        val excludedScanlators: SelectExcludedScanlatorsDigest,
    )

    suspend fun digest(candidates: List<Candidate>): Digest {
        val downloadCounts = downloadCache.getDownloadCounts(candidates.asSequence().map { it.manga })
        val candidateKeys = candidates.map {
            CandidateKey(
                libraryItemId = it.libraryItemId,
                chapterFlags = it.manga.chapterFlags,
                version = it.manga.version,
                ogTitle = it.manga.ogTitle,
                source = it.manga.source,
                downloadCount = downloadCounts[it.manga.id] ?: 0,
            )
        }
        val rowDigests = HashMap<Long, SelectUnreadStateDigestByMangaIds>(candidates.size)
        for (chunk in candidates.chunked(QUERY_CHUNK)) {
            handler.awaitList {
                tsuzuki_continue_targetsQueries.selectUnreadStateDigestByMangaIds(
                    chunk.map { it.manga.id },
                )
            }.forEach { rowDigests[it.mangaId] = it }
        }
        val excludedScanlatorsDigest = handler.awaitOne {
            tsuzuki_continue_targetsQueries.selectExcludedScanlatorsDigest()
        }
        return Digest(candidateKeys, rowDigests, excludedScanlatorsDigest)
    }

    suspend fun resolve(candidates: List<Candidate>): Map<Long, TargetRow> {
        val result = HashMap<Long, TargetRow>(candidates.size)
        val rowsByManga = HashMap<Long, MutableList<Chapter>>()
        for (chunk in candidates.chunked(QUERY_CHUNK)) {
            handler.awaitList {
                tsuzuki_continue_targetsQueries.selectUnreadChaptersByMangaIds(
                    chunk.map { it.manga.id },
                    Manga.CHAPTER_SHOW_NOT_BOOKMARKED,
                    Manga.CHAPTER_SHOW_BOOKMARKED,
                ) { id, mangaId, url, name, scanlator, read, bookmark, lastPageRead, chapterNumber, sourceOrder, dateFetch, dateUpload, lastModifiedAt, version, isSyncing, memo ->
                    Chapter(
                        id = id,
                        mangaId = mangaId,
                        read = read != 0L,
                        bookmark = bookmark != 0L,
                        lastPageRead = lastPageRead,
                        dateFetch = dateFetch,
                        sourceOrder = sourceOrder,
                        url = url,
                        name = name,
                        dateUpload = dateUpload,
                        chapterNumber = chapterNumber,
                        scanlator = scanlator,
                        lastModifiedAt = lastModifiedAt,
                        version = version,
                        memo = runCatching {
                            tachiyomi.data.MemoColumnAdapter.decode(memo)
                        }.getOrDefault(JsonObject(emptyMap())),
                    )
                }
            }.forEach { chapter ->
                rowsByManga.getOrPut(chapter.mangaId) { ArrayList() }.add(chapter)
            }
        }
        for (candidate in candidates) {
            val chapter = rowsByManga[candidate.manga.id].orEmpty()
                .getNextUnread(candidate.manga, downloadManager, emptyMap())
                ?: continue
            result[candidate.libraryItemId] = TargetRow(chapter.id, chapter.lastPageRead)
        }
        return result
    }

    suspend fun resolveMerged(candidates: List<Candidate>): Map<Long, TargetRow> {
        val result = HashMap<Long, TargetRow>(candidates.size)
        for (candidate in candidates) {
            val mergedManga = getMergedMangaById.await(candidate.manga.id).associateBy { it.id }
            val chapter = getMergedChaptersByMangaId.await(candidate.manga.id, applyFilter = true)
                .getNextUnread(candidate.manga, downloadManager, mergedManga)
                ?: continue
            result[candidate.libraryItemId] = TargetRow(chapter.id, chapter.lastPageRead)
        }
        return result
    }

    companion object {
        private const val QUERY_CHUNK = 900
    }
}
