// Adapted from Neko (https://github.com/nekomangaorg/Neko), Apache License 2.0. Modified for Tsuzuki.
package exh.md.service

import exh.md.dto.ForumThreadDto
import exh.md.dto.MarkStatusDto
import exh.md.utils.MdApi
import exh.md.utils.MdUtil
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

object MangaDexFeatureRequests {
    const val PAGE_SIZE = 100
    const val STAFF_PICKS = "805ba886-dd99-4aa4-b460-4bd7c7b71352"
    const val NEKO_PICKS = "4dd69b87-046e-4a85-9dca-ec88d7d314c7"
    val headers: Headers = Headers.Builder()
        .add("User-Agent", "OpenAI File Downloader, XaiImageApiFetch/1.0")
        .add("Accept", "application/json")
        .build()

    fun covers(mangaId: String, offset: Int): Request = get(
        MdApi.cover.toHttpUrl().newBuilder()
            .addQueryParameter("manga[]", mangaId)
            .addQueryParameter("order[volume]", "asc")
            .page(offset).build(),
    )

    fun lists(offset: Int): Request = get(api("user/list").page(offset).build())
    fun list(id: String): Request = get(api("list").addPathSegment(id).build())
    fun readMarkers(mangaId: String): Request = get(api("manga").addPathSegment(mangaId).addPathSegment("read").build())

    fun markRead(mangaId: String, read: List<String>, unread: List<String>): Request {
        val body = MarkStatusDto(read.distinct(), unread.distinct().filterNot { it in read })
        return Request.Builder().url(api("manga").addPathSegment(mangaId).addPathSegment("read").build())
            .headers(headers).post(MdUtil.encodeToBody(body)).build()
    }

    fun feed(
        offset: Int,
        languages: List<String>,
        blockedGroups: List<String> = emptyList(),
        blockedUploaders: List<String> = emptyList(),
        ratings: List<String> = listOf("safe", "suggestive"),
        followed: Boolean = true,
        includeUnavailable: Boolean = false,
    ): Request = get(
        api(if (followed) "user/follows/manga/feed" else "chapter")
            .page(offset)
            .addQueryParameter("order[readableAt]", "desc")
            .chapterOptions(languages, blockedGroups, blockedUploaders, ratings, includeUnavailable).build(),
    )

    fun chapters(
        mangaId: String,
        offset: Int,
        languages: List<String>,
        blockedGroups: List<String>,
        blockedUploaders: List<String>,
        includeUnavailable: Boolean,
    ): Request = get(
        api("manga").addPathSegment(mangaId).addPathSegment("feed")
            .page(offset, 500)
            .addQueryParameter("order[volume]", "desc")
            .addQueryParameter("order[chapter]", "desc")
            .chapterOptions(languages, blockedGroups, blockedUploaders, allRatings, includeUnavailable).build(),
    )

    fun manga(ids: List<String>, ratings: List<String> = allRatings): Request {
        require(ids.isNotEmpty() && ids.size <= PAGE_SIZE)
        return get(
            api("manga").addQueryParameter("limit", ids.size.toString())
                .addQueryParameter("includes[]", "cover_art")
                .parameters("ids[]", ids).parameters("contentRating[]", ratings).build(),
        )
    }

    fun browse(offset: Int, query: Map<String, Any>, ratings: List<String>): Request = get(
        api("manga").page(offset, 20).addQueryParameter("includes[]", "cover_art")
            .apply {
                query.forEach { (key, value) ->
                    if (value is Iterable<*>) value.forEach { addQueryParameter(key, it.toString()) }
                    else addQueryParameter(key, value.toString())
                }
                if ("contentRating[]" !in query) parameters("contentRating[]", ratings)
            }.build(),
    )

    fun recentlyAdded(ratings: List<String>): Request = browse(0, mapOf("order[createdAt]" to "desc"), ratings)

    fun popularNew(ratings: List<String>, createdSince: String): Request = browse(
        0,
        mapOf("order[followedCount]" to "desc", "hasAvailableChapters" to "true", "createdAtSince" to createdSince),
        ratings,
    )

    fun statistics(id: String, chapter: Boolean = false): Request = get(
        api("statistics/${if (chapter) "chapter" else "manga"}").addPathSegment(id).build(),
    )

    fun createThread(chapterId: String): Request = Request.Builder().url(api("forums/thread").build())
        .headers(headers).post(MdUtil.encodeToBody(ForumThreadDto(chapterId, "chapter"))).build()

    fun recommendations(mangaId: String, offset: Int): Request = get(
        api("manga").addPathSegment(mangaId).addPathSegment("recommendation").page(offset).build(),
    )

    fun aggregate(mangaId: String, languages: List<String>): Request = get(
        api("manga").addPathSegment(mangaId).addPathSegment("aggregate")
            .parameters("translatedLanguage[]", languages).build(),
    )

    fun lookup(type: String, name: String, offset: Int = 0): Request {
        require(type in listOf("author", "group", "user"))
        return get(api(type).page(offset).addQueryParameter(if (type == "user") "username" else "name", name).build())
    }

    fun chapter(id: String): Request = get(
        api("chapter").addPathSegment(id).addQueryParameter("includes[]", "scanlation_group")
            .addQueryParameter("includes[]", "user").build(),
    )

    fun author(id: String): Request = get(api("author").addPathSegment(id).build())

    fun seasonal(): Request = get("https://antsylich.github.io/mangadex-seasonal/seasonal-list.min.json".toHttpUrl())

    fun kitsu(id: String): Request = get("https://kitsu.app/api/edge/manga".toHttpUrl().newBuilder().addPathSegment(id).build())

    private fun HttpUrl.Builder.chapterOptions(
        languages: List<String>,
        groups: List<String>,
        uploaders: List<String>,
        ratings: List<String>,
        unavailable: Boolean,
    ) = parameters("translatedLanguage[]", languages)
        .parameters("excludedGroups[]", groups).parameters("excludedUploaders[]", uploaders)
        .parameters("contentRating[]", ratings)
        .addQueryParameter("includes[]", "scanlation_group").addQueryParameter("includes[]", "user")
        .addQueryParameter("includeFutureUpdates", "0").addQueryParameter("includeUnavailable", if (unavailable) "1" else "0")

    private fun HttpUrl.Builder.parameters(key: String, values: List<String>) = apply {
        values.distinct().forEach { addQueryParameter(key, it) }
    }

    private fun HttpUrl.Builder.page(offset: Int, limit: Int = PAGE_SIZE): HttpUrl.Builder {
        require(offset >= 0)
        return addQueryParameter("limit", limit.toString()).addQueryParameter("offset", offset.toString())
    }

    private fun api(path: String) = "https://api.mangadex.org/".toHttpUrl().newBuilder().addPathSegments(path)

    private fun get(url: HttpUrl): Request = Request.Builder().url(url).headers(headers)
        .cacheControl(CacheControl.FORCE_NETWORK).build()

    private val allRatings = listOf("safe", "suggestive", "erotica", "pornographic")
}
