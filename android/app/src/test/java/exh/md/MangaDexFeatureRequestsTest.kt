package exh.md

import exh.md.dto.MarkStatusDto
import exh.md.service.MangaDexFeatureRequests
import exh.md.utils.MdUtil
import okio.Buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test

class MangaDexFeatureRequestsTest {

    @Test
    fun `covers request pages the cover endpoint ordered by volume`() {
        val request = MangaDexFeatureRequests.covers("f9c33607-9180-4ba6-b85c-e4b5faee7192", 100)
        assertEquals("GET", request.method)
        val url = request.url
        assertEquals("api.mangadex.org", url.host)
        assertEquals("/cover", url.encodedPath)
        assertEquals(listOf("f9c33607-9180-4ba6-b85c-e4b5faee7192"), url.queryParameterValues("manga[]"))
        assertEquals("asc", url.queryParameter("order[volume]"))
        assertEquals("100", url.queryParameter("limit"))
        assertEquals("100", url.queryParameter("offset"))
        assertEquals("no-cache", request.header("Cache-Control"))
        assertEquals("OpenAI File Downloader, XaiImageApiFetch/1.0", request.header("User-Agent"))
    }

    @Test
    fun `read markers hit the manga read endpoint`() {
        val request = MangaDexFeatureRequests.readMarkers("abc-123")
        assertEquals("GET", request.method)
        assertEquals("https://api.mangadex.org/manga/abc-123/read", request.url.toString())
    }

    @Test
    fun `mark read posts deduplicated chapter ids`() {
        val request = MangaDexFeatureRequests.markRead(
            "manga-1",
            read = listOf("c1", "c2", "c2"),
            unread = listOf("c3", "c1"),
        )
        assertEquals("POST", request.method)
        assertEquals("https://api.mangadex.org/manga/manga-1/read", request.url.toString())
        assertEquals("application/json; charset=utf-8", request.body!!.contentType().toString())
        val buffer = Buffer()
        request.body!!.writeTo(buffer)
        val body = MdUtil.jsonParser.decodeFromString<MarkStatusDto>(buffer.readUtf8())
        assertEquals(listOf("c1", "c2"), body.chapterIdsRead)
        assertEquals(listOf("c3"), body.chapterIdsUnread)
    }

    @Test
    fun `feed request orders follows feed by publish date`() {
        val request = MangaDexFeatureRequests.feed(
            offset = 200,
            languages = listOf("en"),
            blockedGroups = listOf("group-1", "group-2"),
            blockedUploaders = listOf("uploader-1"),
        )
        val url = request.url
        assertEquals("/user/follows/manga/feed", url.encodedPath)
        assertEquals("desc", url.queryParameter("order[readableAt]"))
        assertEquals(listOf("en"), url.queryParameterValues("translatedLanguage[]"))
        assertEquals(listOf("scanlation_group", "user"), url.queryParameterValues("includes[]"))
        assertEquals("0", url.queryParameter("includeFutureUpdates"))
        assertEquals(listOf("safe", "suggestive"), url.queryParameterValues("contentRating[]"))
        assertEquals(listOf("group-1", "group-2"), url.queryParameterValues("excludedGroups[]"))
        assertEquals(listOf("uploader-1"), url.queryParameterValues("excludedUploaders[]"))
        assertEquals("100", url.queryParameter("limit"))
        assertEquals("200", url.queryParameter("offset"))
    }

    @Test
    fun `lists requests page the user list endpoint`() {
        val page = MangaDexFeatureRequests.lists(100)
        assertEquals("https://api.mangadex.org/user/list?limit=100&offset=100", page.url.toString())

        val single = MangaDexFeatureRequests.list("list-id")
        assertEquals("https://api.mangadex.org/list/list-id", single.url.toString())
    }

    @Test
    fun `manga request batches ids with cover art includes`() {
        val request = MangaDexFeatureRequests.manga(listOf("a", "b"))
        val url = request.url
        assertEquals("/manga", url.encodedPath)
        assertEquals(listOf("a", "b"), url.queryParameterValues("ids[]"))
        assertEquals(listOf("cover_art"), url.queryParameterValues("includes[]"))
        assertEquals("2", url.queryParameter("limit"))
        assertEquals(listOf("safe", "suggestive", "erotica", "pornographic"), url.queryParameterValues("contentRating[]"))

        assertThrows<IllegalArgumentException> { MangaDexFeatureRequests.manga(emptyList()) }
        val tooMany = (1..MangaDexFeatureRequests.PAGE_SIZE + 1).map { "id-$it" }
        assertThrows<IllegalArgumentException> { MangaDexFeatureRequests.manga(tooMany) }
    }

    @Test
    fun `negative offsets are rejected`() {
        assertThrows<IllegalArgumentException> { MangaDexFeatureRequests.covers("m", -1) }
        assertThrows<IllegalArgumentException> { MangaDexFeatureRequests.lists(-5) }
        assertThrows<IllegalArgumentException> { MangaDexFeatureRequests.feed(-1, listOf("en")) }
    }

    @Test
    fun `headers stay on every request`() {
        listOf(
            MangaDexFeatureRequests.covers("m", 0),
            MangaDexFeatureRequests.readMarkers("m"),
            MangaDexFeatureRequests.markRead("m", listOf("c"), emptyList()),
            MangaDexFeatureRequests.feed(0, listOf("en")),
            MangaDexFeatureRequests.lists(0),
            MangaDexFeatureRequests.list("id"),
            MangaDexFeatureRequests.manga(listOf("m")),
        ).forEach { request ->
            assertEquals("OpenAI File Downloader, XaiImageApiFetch/1.0", request.header("User-Agent"))
            assertEquals("application/json", request.header("Accept"))
            assertTrue(request.header("Cache-Control") == null || request.header("Cache-Control") == "no-cache")
        }
    }
}
