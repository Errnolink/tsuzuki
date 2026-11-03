// Adapted from Neko (https://github.com/nekomangaorg/Neko), Apache License 2.0. Modified for Tsuzuki.
package exh.md.handlers

import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.source.online.all.MangaDex
import exh.md.MangaDexPreferences
import exh.md.dto.ForumThreadResponseDto
import exh.md.dto.RecommendationListDto
import exh.md.dto.StatisticsDto
import exh.md.dto.ChapterListDto
import exh.md.dto.CoverDto
import exh.md.dto.CoverListDto
import exh.md.dto.CustomListDto
import exh.md.dto.CustomListPageDto
import exh.md.dto.CustomListResponseDto
import exh.md.dto.MangaDataDto
import exh.md.dto.MangaListDto
import exh.md.dto.ReadChapterDto
import exh.md.dto.ResultDto
import exh.md.service.MangaDexFeatureRequests
import exh.md.utils.MdLang
import exh.md.utils.MdUtil
import okhttp3.Request

class MangaDexFeatures(private val source: MangaDex, private val preferences: MangaDexPreferences = MangaDexPreferences()) {
    private val client get() = source.baseHttpClient
    val language = (MdLang.fromExt(source.lang) ?: MdLang.ENGLISH).lang
    val languages get() = preferences.languages(language)
    val ratings get() = preferences.visibleRatings().get().sorted()

    fun blockedGroups(): List<String> = sourceBlocks(MangaDex.getBlockedGroupsPrefKey(language)) + preferences.blockedGroups().get()
    fun blockedUploaders(): List<String> = sourceBlocks(MangaDex.getBlockedUploaderPrefKey(language)) + preferences.blockedUploaders().get()

    private fun sourceBlocks(key: String): List<String> = source.context.getSharedPreferences("source_${source.id}", 0)
        .getString(key, "").orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)

    suspend fun covers(mangaId: String): List<MangaDexArtwork> {
        val covers = mutableListOf<CoverDto>()
        var offset = 0
        do {
            val page = request<CoverListDto>(MangaDexFeatureRequests.covers(mangaId, offset))
            covers += page.data
            offset = page.offset + page.data.size
        } while (page.data.isNotEmpty() && offset < page.total)
        return MangaDexFeatureMapper.artwork(mangaId, covers.distinctBy { it.id }).also {
            MangaDexArtworkStore.saveCovers(mangaId, it)
        }
    }

    suspend fun lists(): List<CustomListDto> {
        requireLogin()
        val lists = mutableListOf<CustomListDto>()
        var offset = 0
        do {
            val page = request<CustomListPageDto>(MangaDexFeatureRequests.lists(offset))
            lists += page.data
            offset = page.offset + page.data.size
        } while (page.data.isNotEmpty() && offset < page.total)
        return lists.distinctBy { it.id }.sortedBy { it.attributes.name.lowercase() }
    }

    suspend fun list(id: String): CustomListDto {
        return request<CustomListResponseDto>(MangaDexFeatureRequests.list(id)).data
    }

    suspend fun manga(ids: List<String>): List<MangaDataDto> = if (ratings.isEmpty()) emptyList() else ids.distinct()
        .chunked(MangaDexFeatureRequests.PAGE_SIZE)
        .flatMap { request<MangaListDto>(MangaDexFeatureRequests.manga(it, ratings)).data }

    suspend fun feed(offset: Int, followed: Boolean = true): MangaDexFeedPage {
        if (followed) requireLogin()
        if (ratings.isEmpty() || languages.isEmpty()) return MangaDexFeedPage(emptyList(), offset, false)
        val page = request<ChapterListDto>(
            MangaDexFeatureRequests.feed(
                offset, languages, blockedGroups(), blockedUploaders(), ratings, followed,
                preferences.includeUnavailable().get(),
            ),
        )
        val mangaIds = page.data.flatMap { it.relationships }.filter { it.type == "manga" }.map { it.id }.distinct()
        return MangaDexFeedPage(
            MangaDexFeatureMapper.feed(page.data, manga(mangaIds), language),
            page.offset + page.data.size,
            page.data.isNotEmpty() && page.offset + page.data.size < page.total && page.offset + page.data.size < 10000,
        )
    }

    suspend fun readMarkers(mangaId: String): Set<String> {
        requireLogin()
        return request<ReadChapterDto>(MangaDexFeatureRequests.readMarkers(mangaId)).data.toSet()
    }

    suspend fun markRead(mangaId: String, read: List<String>, unread: List<String>) {
        requireLogin()
        if (read.isEmpty() && unread.isEmpty()) return
        val readIds = read.toSet()
        (readIds.map { it to true } + unread.distinct().filterNot { it in readIds }.map { it to false }).chunked(100).forEach { batch ->
            val result = request<ResultDto>(
                MangaDexFeatureRequests.markRead(mangaId, batch.filter { it.second }.map { it.first }, batch.filterNot { it.second }.map { it.first }),
            )
            check(result.result == "ok") { "MangaDex did not accept the read markers" }
        }
    }

    suspend fun chapterThread(chapterId: String, create: Boolean): Long? {
        val stats = request<StatisticsDto>(MangaDexFeatureRequests.statistics(chapterId, chapter = true))
        stats.statistics[chapterId]?.comments?.threadId?.let { return it }
        if (!create) return null
        requireLogin()
        return request<ForumThreadResponseDto>(MangaDexFeatureRequests.createThread(chapterId)).data.id
    }

    suspend fun recommendations(mangaId: String): List<MangaDataDto> {
        val ids = mutableListOf<String>()
        var offset = 0
        do {
            val page = request<RecommendationListDto>(MangaDexFeatureRequests.recommendations(mangaId, offset))
            ids += MangaDexFeatureMapper.recommendationIds(mangaId, page)
            offset = page.offset + page.data.size
        } while (page.data.isNotEmpty() && offset < page.total)
        return manga(ids)
    }

    private fun requireLogin() {
        check(source.isLogged()) { "Sign in to MangaDex to continue" }
    }

    internal suspend inline fun <reified T> request(request: Request): T = with(MdUtil.jsonParser) {
        client.newCall(request).awaitSuccess().parseAs<T>()
    }
}

data class MangaDexFeedPage(val entries: List<MangaDexFeedEntry>, val nextOffset: Int, val hasNext: Boolean)
