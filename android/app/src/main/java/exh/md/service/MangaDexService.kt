package exh.md.service

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import exh.md.dto.AggregateDto
import exh.md.dto.AtHomeDto
import exh.md.dto.AtHomeImageReportDto
import exh.md.dto.ChapterDto
import exh.md.dto.ChapterListDto
import exh.md.dto.CoverListDto
import exh.md.dto.MangaDto
import exh.md.dto.MangaListDto
import exh.md.dto.RelationListDto
import exh.md.dto.ResultDto
import exh.md.dto.StatisticsDto
import exh.md.utils.MdApi
import exh.md.utils.MdConstants
import exh.md.utils.MdUtil
import exh.util.dropEmpty
import exh.util.trimAll
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

class MangaDexService(
    internal val client: OkHttpClient,
    private val headers: Headers,
) {

    suspend fun viewMangas(
        ids: List<String>,
    ): MangaListDto {
        return with(MdUtil.jsonParser) {
            client.newCall(
                GET(
                    MdApi.manga.toHttpUrl()
                        .newBuilder()
                        .apply {
                            addQueryParameter("includes[]", MdConstants.Types.coverArt)
                            addQueryParameter("limit", ids.size.toString())
                            ids.forEach {
                                addQueryParameter("ids[]", it)
                            }
                        }
                        .build(),
                    headers,
                    cache = CacheControl.FORCE_NETWORK,
                ),
            ).awaitSuccess().parseAs()
        }
    }

    suspend fun viewManga(
        id: String,
    ): MangaDto {
        return with(MdUtil.jsonParser) {
            client.newCall(
                GET(
                    MdApi.manga.toHttpUrl()
                        .newBuilder()
                        .apply {
                            addPathSegment(id)
                            addQueryParameter("includes[]", MdConstants.Types.coverArt)
                            addQueryParameter("includes[]", MdConstants.Types.author)
                            addQueryParameter("includes[]", MdConstants.Types.artist)
                        }
                        .build(),
                    headers,
                    cache = CacheControl.FORCE_NETWORK,
                ),
            ).awaitSuccess().parseAs()
        }
    }

    suspend fun mangasRating(
        vararg ids: String,
    ): StatisticsDto {
        return with(MdUtil.jsonParser) {
            client.newCall(
                GET(
                    MdApi.statistics.toHttpUrl()
                        .newBuilder()
                        .apply {
                            ids.forEach { id ->
                                addQueryParameter("manga[]", id)
                            }
                        }
                        .build(),
                    headers,
                    cache = CacheControl.FORCE_NETWORK,
                ),
            ).awaitSuccess().parseAs()
        }
    }

    suspend fun aggregateChapters(
        id: String,
        translatedLanguage: String,
    ): AggregateDto {
        return with(MdUtil.jsonParser) {
            client.newCall(
                GET(
                    MdApi.manga.toHttpUrl()
                        .newBuilder()
                        .apply {
                            addPathSegment(id)
                            addPathSegment("aggregate")
                            addQueryParameter("translatedLanguage[]", translatedLanguage)
                        }
                        .build(),
                    headers,
                    cache = CacheControl.FORCE_NETWORK,
                ),
            ).awaitSuccess().parseAs()
        }
    }

    suspend fun viewChapters(
        id: String,
        translatedLanguages: List<String>,
        offset: Int,
        blockedGroups: String,
        blockedUploaders: String,
        includeUnavailable: Boolean,
    ): ChapterListDto = with(MdUtil.jsonParser) {
        client.newCall(
            MangaDexFeatureRequests.chapters(
                id, offset, translatedLanguages,
                blockedGroups.split(',').map(String::trim).filter(String::isNotEmpty),
                blockedUploaders.split(',').map(String::trim).filter(String::isNotEmpty),
                includeUnavailable,
            ),
        ).awaitSuccess().parseAs()
    }

    suspend fun viewChapter(id: String): ChapterDto {
        return with(MdUtil.jsonParser) {
            client.newCall(MangaDexFeatureRequests.chapter(id))
                .awaitSuccess()
                .parseAs()
        }
    }

    suspend fun randomManga(): MangaDto {
        return with(MdUtil.jsonParser) {
            client.newCall(GET("${MdApi.manga}/random", headers, cache = CacheControl.FORCE_NETWORK))
                .awaitSuccess()
                .parseAs()
        }
    }

    suspend fun atHomeImageReport(atHomeImageReportDto: AtHomeImageReportDto): ResultDto {
        return with(MdUtil.jsonParser) {
            client.newCall(
                POST(
                    MdConstants.atHomeReportUrl,
                    headers,
                    body = MdUtil.encodeToBody(atHomeImageReportDto),
                    cache = CacheControl.FORCE_NETWORK,
                ),
            ).awaitSuccess().parseAs()
        }
    }

    suspend fun getAtHomeServer(
        atHomeRequestUrl: String,
        headers: Headers,
    ): AtHomeDto {
        return with(MdUtil.jsonParser) {
            client.newCall(GET(atHomeRequestUrl, headers, CacheControl.FORCE_NETWORK))
                .awaitSuccess()
                .parseAs()
        }
    }

    suspend fun relatedManga(id: String): RelationListDto {
        return with(MdUtil.jsonParser) {
            client.newCall(
                GET(
                    MdApi.manga.toHttpUrl().newBuilder()
                        .apply {
                            addPathSegment(id)
                            addPathSegment("relation")
                        }
                        .build(),
                    headers,
                    cache = CacheControl.FORCE_NETWORK,
                ),
            ).awaitSuccess().parseAs()
        }
    }

    suspend fun fetchFirstVolumeCover(mangaDto: MangaDto): String? {
        val mangaData = mangaDto.data
        val result: CoverListDto = with(MdUtil.jsonParser) {
            client.newCall(
                GET(
                    MdApi.cover.toHttpUrl().newBuilder()
                        .apply {
                            addQueryParameter("order[volume]", "asc")
                            addQueryParameter("manga[]", mangaData.id)
                            addQueryParameter("locales[]", mangaData.attributes.originalLanguage)
                            addQueryParameter("limit", "1")
                        }
                        .build(),
                    headers,
                ),
            ).awaitSuccess().parseAs()
        }
        return result.data.firstOrNull()?.attributes?.fileName
    }
}
