package exh.md

import exh.md.dto.AggregateDto
import exh.md.dto.ChapterListDto
import exh.md.dto.CustomListResponseDto
import exh.md.dto.ForumThreadDto
import exh.md.dto.LookupPageDto
import exh.md.dto.RecommendationListDto
import exh.md.dto.SeasonalDto
import exh.md.dto.StatisticsDto
import exh.md.handlers.MangaDexArtwork
import exh.md.handlers.MangaDexDynamicCovers
import exh.md.handlers.MangaDexFeatureMapper
import exh.md.handlers.MangaDexImageFallback
import exh.md.handlers.MangaDexSearch
import exh.md.service.MangaDexFeatureRequests as Requests
import exh.md.utils.FollowStatus
import exh.md.utils.MdUtil
import okio.Buffer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class MangaDexParityTest {
    private val mangaId = "f9c33607-9180-4ba6-b85c-e4b5faee7192"
    private inline fun <reified T> fixture(name: String): T = MdUtil.jsonParser.decodeFromString(
        javaClass.getResourceAsStream("/mangadex/$name")!!.bufferedReader().use { it.readText() },
    )

    @Test
    fun `statistics includes follows distribution comments and unavailable count`() {
        val result = fixture<StatisticsDto>("statistics.json")
        val stats = MangaDexFeatureMapper.statistics(result.statistics.getValue(mangaId), 7)
        assertEquals(9752L, stats.follows)
        assertEquals(825L, stats.threadId)
        assertEquals(1979, stats.comments)
        assertEquals(2920, stats.distribution[10])
        assertEquals(7, stats.estimatedMissing)
        assertEquals(2, stats.unavailable)
        assertEquals(8.18753728440833, stats.rating)
    }

    @Test
    fun `chapter statistics does not require a rating`() {
        val result = fixture<StatisticsDto>("chapter-statistics.json").statistics.values.single()
        assertNull(result.rating)
        assertEquals(2699988L, result.comments!!.threadId)
        assertEquals(10, result.comments.repliesCount)
    }

    @Test
    fun `public recommendations exclude the requested title and preserve score order`() {
        val ids = MangaDexFeatureMapper.recommendationIds(mangaId, fixture<RecommendationListDto>("recommendations.json"))
        assertEquals(10, ids.size)
        assertFalse(mangaId in ids)
        assertEquals("01dde6c5-4d40-4abf-82d8-ddba3435112a", ids.first())
    }

    @Test
    fun `public MDList and discovery JSON decode`() {
        val list = fixture<CustomListResponseDto>("staff-list.json").data
        assertEquals(Requests.STAFF_PICKS, list.id)
        assertEquals("Recommended", list.attributes.name)
        assertEquals(47, list.mangaIds.size)
        val seasonal = fixture<SeasonalDto>("seasonal.json")
        assertEquals("68ab4f4e-6f01-4898-9038-c5eee066be27", seasonal.id)
        assertEquals("Summer 2026", seasonal.name)
    }

    @Test
    fun `author and group collections map names and identifiers`() {
        val authors = fixture<LookupPageDto>("authors.json")
        assertEquals("Urasawa Naoki", authors.data.last().attributes.name)
        val groups = fixture<LookupPageDto>("groups.json")
        assertEquals("4f1de6a2-f0c5-4ac5-bce5-02c7dbb67deb", groups.data.first().id)
        assertEquals("MangaPlus", groups.data.first().attributes.name)
    }

    @Test
    fun `aggregate uses numeric counts and preserves alternate chapter ids`() {
        val aggregate = fixture<AggregateDto>("aggregate.json")
        assertEquals(12, aggregate.volumes.getValue("0").count)
        assertEquals(2, aggregate.volumes.getValue("0").chapters.getValue("0").others.size)
        assertEquals("fd50ca9b-147d-4690-bab4-fc032603ccbb", aggregate.volumes.getValue("5446").chapters.getValue("0").id)
        assertNull(MangaDexFeatureMapper.estimatedMissing(aggregate))
        assertEquals(0, MangaDexFeatureMapper.estimatedMissing(aggregate.copy(volumes = emptyMap())))
    }

    @Test
    fun `uploader fallback availability release and official guards are explicit`() {
        val chapter = fixture<ChapterListDto>("manga-feed.json").data.first()
        assertEquals("Deleted uploader", MangaDexFeatureMapper.chapterCredit(chapter))
        val user = chapter.copy(relationships = listOf(exh.md.dto.RelationshipDto("u", "user", exh.md.dto.IncludesAttributesDto(username = "Uploader"))))
        assertEquals("Uploader", MangaDexFeatureMapper.chapterCredit(user))
        assertNull(MangaDexFeatureMapper.chapterAccess(chapter, Instant.parse("2100-01-01T00:00:00Z")))
        assertTrue(MangaDexFeatureMapper.chapterAccess(chapter.copy(attributes = chapter.attributes.copy(isUnavailable = true)))!!.contains("not available"))
        assertTrue(MangaDexFeatureMapper.chapterAccess(chapter, Instant.EPOCH)!!.contains("not been released"))
        MangaDexFeatureMapper.unsupportedOfficialGroups.forEach { name ->
            val official = chapter.copy(relationships = listOf(exh.md.dto.RelationshipDto("g", "scanlation_group", exh.md.dto.IncludesAttributesDto(name = name))))
            assertTrue(MangaDexFeatureMapper.chapterAccess(official, Instant.parse("2100-01-01T00:00:00Z"))!!.contains("WebView"))
        }
    }

    @Test
    fun `multilingual chapter and site feed requests honor blocks and availability`() {
        val request = Requests.chapters(mangaId, 500, listOf("en", "ja"), listOf("g"), listOf("u"), true).url
        assertEquals("/manga/$mangaId/feed", request.encodedPath)
        assertEquals(listOf("en", "ja"), request.queryParameterValues("translatedLanguage[]"))
        assertEquals("1", request.queryParameter("includeUnavailable"))
        assertEquals("500", request.queryParameter("offset"))
        assertEquals("500", request.queryParameter("limit"))
        assertEquals(listOf("g"), request.queryParameterValues("excludedGroups[]"))
        assertEquals(listOf("u"), request.queryParameterValues("excludedUploaders[]"))
        val latest = Requests.feed(0, listOf("en", "fr"), ratings = listOf("safe"), followed = false).url
        assertEquals("/chapter", latest.encodedPath)
        assertEquals(listOf("safe"), latest.queryParameterValues("contentRating[]"))
    }

    @Test
    fun `browse and discovery request builders preserve API filters`() {
        val search = Requests.browse(20, mapOf("authorOrArtist" to "a", "group" to "g", "includedTags[]" to listOf("t")), listOf("safe")).url
        assertEquals("a", search.queryParameter("authorOrArtist"))
        assertEquals("g", search.queryParameter("group"))
        assertEquals(listOf("t"), search.queryParameterValues("includedTags[]"))
        assertEquals("20", search.queryParameter("offset"))
        assertEquals("desc", Requests.recentlyAdded(listOf("safe")).url.queryParameter("order[createdAt]"))
        val popular = Requests.popularNew(listOf("safe"), "2026-09-06T00:00:00").url
        assertEquals("true", popular.queryParameter("hasAvailableChapters"))
        assertEquals("desc", popular.queryParameter("order[followedCount]"))
        assertEquals("2026-09-06T00:00:00", popular.queryParameter("createdAtSince"))
        assertEquals("antsylich.github.io", Requests.seasonal().url.host)
    }

    @Test
    fun `stats forum aggregate recommendation author group and user builders target correct endpoints`() {
        assertEquals("/statistics/manga/m", Requests.statistics("m").url.encodedPath)
        assertEquals("/statistics/chapter/c", Requests.statistics("c", true).url.encodedPath)
        assertEquals("/manga/m/recommendation", Requests.recommendations("m", 100).url.encodedPath)
        assertEquals("100", Requests.recommendations("m", 100).url.queryParameter("offset"))
        val aggregate = Requests.aggregate("m", listOf("en", "ja")).url
        assertEquals("/manga/m/aggregate", aggregate.encodedPath)
        assertEquals(listOf("en", "ja"), aggregate.queryParameterValues("translatedLanguage[]"))
        assertEquals("/author/a", Requests.author("a").url.encodedPath)
        listOf("author", "group", "user").forEach { type ->
            val url = Requests.lookup(type, "name & value", 100).url
            assertEquals("/$type", url.encodedPath)
            assertEquals("name & value", url.queryParameter(if (type == "user") "username" else "name"))
            assertEquals("100", url.queryParameter("offset"))
        }
        val thread = Requests.createThread("chapter-id")
        assertEquals("POST", thread.method)
        assertEquals("/forums/thread", thread.url.encodedPath)
        val buffer = Buffer()
        thread.body!!.writeTo(buffer)
        assertEquals(ForumThreadDto("chapter-id", "chapter"), MdUtil.jsonParser.decodeFromString<ForumThreadDto>(buffer.readUtf8()))
    }

    @Test
    fun `search prefixes preserve names and do not consume ordinary titles`() {
        listOf("author", "group", "list", "manga", "error").forEach { prefix ->
            assertEquals(prefix to "value", MangaDexSearch.prefix("$prefix: value"))
        }
        assertNull(MangaDexSearch.prefix("Title: Subtitle"))
        assertThrows<IllegalArgumentException> { MangaDexSearch.prefix("author:") }
    }

    @Test
    fun `auto follow preferences match Neko status mapping`() {
        assertNull(MangaDexPreferences.autoFollowStatus(0))
        assertEquals(FollowStatus.PLAN_TO_READ, MangaDexPreferences.autoFollowStatus(1))
        assertEquals(FollowStatus.ON_HOLD, MangaDexPreferences.autoFollowStatus(2))
        assertEquals(FollowStatus.READING, MangaDexPreferences.autoFollowStatus(3))
    }

    @Test
    fun `dynamic covers choose exact last-read volume then matching language`() {
        val english = MangaDexArtwork("en", mangaId, "en.jpg", "2", "en", "")
        val japanese = english.copy(id = "ja", locale = "ja", fileName = "ja.jpg")
        assertEquals(english, MangaDexDynamicCovers.select(listOf(japanese, english), "2.0", "en"))
        assertNull(MangaDexDynamicCovers.select(listOf(english), "3", "en"))
        assertNull(MangaDexDynamicCovers.select(listOf(english), null, "en"))
    }

    @Test
    fun `CDN fallback preserves data mode and never rewrites external images`() {
        val page = "https://node.example,https://api.mangadex.org/at-home/server/chapter,123"
        assertEquals("https://uploads.mangadex.org/data/hash/page.jpg", MangaDexImageFallback.url("https://node.example/data/hash/page.jpg", page))
        assertEquals("https://uploads.mangadex.org/data-saver/hash/page.jpg", MangaDexImageFallback.url("https://node.example:8443/data-saver/hash/page.jpg", page))
        assertNull(MangaDexImageFallback.url("https://external.example/page.jpg", page))
        assertNull(MangaDexImageFallback.url("https://uploads.mangadex.org/data/hash/page.jpg", page))
        assertNull(MangaDexImageFallback.url("https://node.example/data/hash/page.jpg", "external"))
    }
}
