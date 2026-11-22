package exh.md.handlers

import dev.errnolink.tsuzuki.mangadex.MangaDexSharedFetch
import dev.errnolink.tsuzuki.mangadex.MangaDexUpdatePrefetch
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import exh.md.MangaDexPreferences
import exh.md.dto.ChapterDataDto
import exh.md.service.MangaDexService
import exh.md.utils.MdConstants
import exh.md.utils.MdUtil
import exh.md.utils.mdListCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import rx.Observable
import tachiyomi.core.common.util.lang.runAsObservable
import tachiyomi.core.common.util.lang.withIOContext

class MangaHandler(
    private val lang: String,
    private val service: MangaDexService,
    private val apiMangaParser: ApiMangaParser,
) {
    suspend fun getMangaDetails(
        manga: SManga,
        sourceId: Long,
        tryUsingFirstVolumeCover: Boolean,
        altTitlesInDesc: Boolean,
        finalChapterInDesc: Boolean,
        preferExtensionLangTitle: Boolean,
        simpleChapters: List<String>? = null,
    ): SManga {
        return coroutineScope {
            val mangaId = MdUtil.getMangaId(manga.url)
            val prefetched = MangaDexUpdatePrefetch.takeManga(mangaId)
            val prefetchedStatistics = MangaDexUpdatePrefetch.takeStatistics(mangaId)
            val response = async(Dispatchers.IO) { prefetched ?: MangaDexSharedFetch.viewManga(service.client, mangaId) }
            val simpleChaptersDef = async(Dispatchers.IO) { simpleChapters ?: getSimpleChapters(manga) }
            val statistics =
                async(Dispatchers.IO) {
                    prefetchedStatistics ?: kotlin.runCatching { service.mangasRating(mangaId) }.getOrNull()?.statistics?.get(mangaId)
                }
            val responseData = response.await()
            val coverFileName = if (tryUsingFirstVolumeCover) {
                async(Dispatchers.IO) {
                    service.fetchFirstVolumeCover(responseData)
                }
            } else {
                null
            }
            apiMangaParser.parseToManga(
                manga,
                sourceId,
                responseData,
                simpleChaptersDef.await(),
                statistics.await(),
                coverFileName?.await(),
                altTitlesInDesc,
                finalChapterInDesc,
                preferExtensionLangTitle,
            )
        }
    }

    fun fetchMangaDetailsObservable(
        manga: SManga,
        sourceId: Long,
        tryUsingFirstVolumeCover: Boolean,
        altTitlesInDesc: Boolean,
        finalChapterInDesc: Boolean,
        preferExtensionLangTitle: Boolean,
    ): Observable<SManga> {
        return runAsObservable {
            getMangaDetails(
                manga,
                sourceId,
                tryUsingFirstVolumeCover,
                altTitlesInDesc,
                finalChapterInDesc,
                preferExtensionLangTitle,
            )
        }
    }

    fun fetchChapterListObservable(
        manga: SManga,
        blockedGroups: String,
        blockedUploaders: String,
        languages: List<String>,
        includeUnavailable: Boolean,
    ): Observable<List<SChapter>> = runAsObservable {
        getChapterList(manga, blockedGroups, blockedUploaders, languages, includeUnavailable)
    }

    suspend fun getChapterList(
        manga: SManga,
        blockedGroups: String,
        blockedUploaders: String,
        languages: List<String>,
        includeUnavailable: Boolean,
    ): List<SChapter> = getChapterListWithNumbers(manga, blockedGroups, blockedUploaders, languages, includeUnavailable).first

    /** The chapter list plus its raw chapter numbers ("12", "12.5"), which stand in for the aggregate. */
    suspend fun getChapterListWithNumbers(
        manga: SManga,
        blockedGroups: String,
        blockedUploaders: String,
        languages: List<String>,
        includeUnavailable: Boolean,
    ): Pair<List<SChapter>, List<String>> {
        return withIOContext {
            if (languages.isEmpty()) return@withIOContext emptyList<SChapter>() to emptyList()
            val mangaId = MdUtil.getMangaId(manga.url)
            suspend fun fetch(withUnavailable: Boolean) = mdListCall {
                service.viewChapters(mangaId, languages, it, blockedGroups, blockedUploaders, withUnavailable)
            }
            val results = fetch(includeUnavailable).ifEmpty {
                if (includeUnavailable) emptyList() else fetch(withUnavailable = true)
            }

            MangaDexArtworkStore.saveChapters(mangaId, results)
            apiMangaParser.chapterListParse(results, getGroupMap(results), languages.size > 1) to
                results.mapNotNull { it.attributes.chapter }.distinct()
        }
    }

    private fun getGroupMap(results: List<ChapterDataDto>): Map<String, String> {
        return results
            .flatMap { it.relationships }
            .filter { it.type == MdConstants.Types.scanlator }
            // KMK -->
            .mapNotNull { relationship ->
                relationship.attributes?.name?.let { relationship.id to it }
            }
            .toMap()
        // KMK <--
    }

    suspend fun fetchRandomMangaId(): String {
        return withIOContext {
            service.randomManga().data.id
        }
    }

    suspend fun getMangaFromChapterId(chapterId: String): String? {
        return withIOContext {
            apiMangaParser.chapterParseForMangaId(service.viewChapter(chapterId))
        }
    }

    suspend fun getMangaMetadata(
        track: Track,
        sourceId: Long,
        tryUsingFirstVolumeCover: Boolean,
        altTitlesInDesc: Boolean,
        finalChapterInDesc: Boolean,
        preferExtensionLangTitle: Boolean,
    ): SManga {
        return withIOContext {
            val mangaId = MdUtil.getMangaId(track.tracking_url)
            val response = service.viewManga(mangaId)
            val coverFileName = if (tryUsingFirstVolumeCover) {
                service.fetchFirstVolumeCover(response)
            } else {
                null
            }
            apiMangaParser.parseToManga(
                SManga.create().apply {
                    url = track.tracking_url
                },
                sourceId,
                response,
                emptyList(),
                null,
                coverFileName,
                altTitlesInDesc,
                finalChapterInDesc,
                preferExtensionLangTitle,
            )
        }
    }

    private suspend fun getSimpleChapters(manga: SManga): List<String> {
        return runCatching { MangaDexSharedFetch.aggregate(service.client, MdUtil.getMangaId(manga.url), lang) }
            .onFailure {
                if (it is CancellationException) throw it
            }
            .map { dto ->
                dto.volumes.values
                    .flatMap { it.chapters.values }
                    .map { it.chapter }
            }
            .getOrElse { emptyList() }
    }
}
