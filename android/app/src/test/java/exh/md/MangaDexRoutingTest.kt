package exh.md

import dev.errnolink.tsuzuki.mangadex.MangaDexChapterPresentation
import exh.md.dto.ChapterListDto
import exh.md.dto.MangaUpPage
import exh.md.dto.MangaUpPageBlock
import exh.md.dto.MangaUpViewerResponse
import exh.md.handlers.ApiMangaParser
import exh.md.handlers.FilterHandler
import exh.md.handlers.MangaDexImageFallback
import exh.md.handlers.MangaUpHandler
import exh.md.handlers.MangaUpImageInterceptor
import exh.md.service.MangaDexFeatureRequests
import exh.md.utils.MdUtil
import kotlinx.serialization.protobuf.ProtoBuf
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tachiyomi.domain.chapter.model.Chapter
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class MangaDexRoutingTest {
    @Test
    fun `chapter lookup requests include group and uploader names`() {
        val url = MangaDexFeatureRequests.chapter("chapter").url
        assertEquals("/chapter/chapter", url.encodedPath)
        assertEquals(listOf("scanlation_group", "user"), url.queryParameterValues("includes[]"))
    }

    @Test
    fun `filter path exposes creators groups lists availability and all eight sorts`() {
        val handler = FilterHandler()
        val filters = handler.getMDFilterList()
        assertEquals(1, filters.filterIsInstance<FilterHandler.Author>().size)
        assertEquals(1, filters.filterIsInstance<FilterHandler.Group>().size)
        assertEquals(1, filters.filterIsInstance<FilterHandler.ListId>().size)
        val query = handler.getQueryMap(filters)
        assertEquals("true", query["hasAvailableChapters"])
        assertEquals(listOf("safe", "suggestive"), query["contentRating[]"])
        assertEquals("desc", query["order[followedCount]"])
        assertEquals(8, handler.sortableList.size)
    }

    @Test
    fun `filter handler respects showRatingFilter false and omits rating filter`() {
        val handler = FilterHandler(showRatingFilter = false)
        val filters = handler.getMDFilterList()
        val query = handler.getQueryMap(filters)
        assertFalse(query.containsKey("contentRating[]"))
    }

    @Test
    fun `chapter mapping keeps uploader fallback languages and unavailable labels`() {
        val feed = MdUtil.jsonParser.decodeFromString<ChapterListDto>(javaClass.getResourceAsStream("/mangadex/manga-feed.json")!!.bufferedReader().use { it.readText() })
        val dto = feed.data.first().let { it.copy(attributes = it.attributes.copy(publishAt = "2020-01-01T00:00:00+00:00", isUnavailable = true)) }
        val chapter = ApiMangaParser("en").chapterListParse(listOf(dto), emptyMap(), true).single()
        assertTrue(chapter.name.contains("[EN]"))
        assertTrue(chapter.name.endsWith("Unavailable"))
        assertTrue(chapter.scanlator!!.contains("Deleted uploader"))
        val local = Chapter.create().copy(url = chapter.url)
        val presentation = MangaDexChapterPresentation("m", mapOf(chapter.url to dto), setOf("fr"))
        assertFalse(presentation.isVisible(local))
        assertTrue(presentation.unavailable(local))
        assertEquals("EN", presentation.languageLabel(local))
        assertTrue(presentation.accessMessage(local)!!.contains("not available"))
        assertTrue(presentation.isVisible(local.copy(url = "/external")))
    }

    @Test
    fun `at home relative image paths also fall back to CDN`() {
        assertEquals("https://uploads.mangadex.org/data/hash/image.jpg", MangaDexImageFallback.url("/data/hash/image.jpg", "node,https://api.mangadex.org/at-home/server/chapter,123"))
    }

    @Test
    fun `MangaUp page request maps external chapter id and publisher headers`() {
        val handler = MangaUpHandler(OkHttpClient())
        val request = handler.pageListRequest("https://global.manga-up.com/manga/12/chapter/345/")
        assertEquals("POST", request.method)
        assertEquals("global-api.manga-up.com", request.url.host)
        assertEquals("/api/manga/viewer_v2", request.url.encodedPath)
        assertEquals("345", request.url.queryParameter("chapter_id"))
        assertEquals("high", request.url.queryParameter("quality"))
        assertEquals("en", request.url.queryParameter("lang"))
        assertEquals("https://global.manga-up.com/", request.header("Referer"))
        assertEquals("https://global.manga-up.com", request.header("Origin"))
    }

    @Test
    fun `MangaUp protobuf mapper omits tutorials and preserves optional image cipher metadata`() {
        val response = MangaUpViewerResponse(listOf(MangaUpPageBlock(listOf(
            MangaUpPage("/tutorial/page.jpg"),
            MangaUpPage("/pages/one.jpg", "00", "11"),
            MangaUpPage("/pages/two.jpg"),
        ))))
        val dto = ProtoBuf.decodeFromByteArray(MangaUpViewerResponse.serializer(), ProtoBuf.encodeToByteArray(MangaUpViewerResponse.serializer(), response))
        val handler = MangaUpHandler(OkHttpClient())
        val pages = handler.pageListParse(dto)
        assertEquals(2, pages.size)
        assertEquals("https://global-img.manga-up.com/pages/one.jpg#key=00#iv=11", pages.first().imageUrl)
        assertEquals("https://global-img.manga-up.com/pages/two.jpg", pages.last().imageUrl)
        assertThrows<Exception> { handler.pageListParse(MangaUpViewerResponse()) }
    }

    @Test
    fun `MangaUp image client decrypts AES CBC payload without buffering decoded image`() {
        val key = ByteArray(16) { it.toByte() }
        val iv = ByteArray(16)
        val plain = "image payload".toByteArray()
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv)) }
        val client = OkHttpClient.Builder().addInterceptor(MangaUpImageInterceptor()).addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(cipher.doFinal(plain).toResponseBody("image/jpeg".toMediaType())).build()
        }.build()
        try {
            val request = Request.Builder().url("https://global-img.manga-up.com/pages/test#key=${key.toHexString()}#iv=${iv.toHexString()}").build()
            client.newCall(request).execute().use { assertArrayEquals(plain, it.body.bytes()) }
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }
}
