package dev.errnolink.tsuzuki.mangadex

import exh.md.dto.ChapterListDto
import exh.md.dto.RelationshipDto
import exh.md.utils.MdUtil
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter

class MangaDexChapterPresentationTest {
    private val dto = MdUtil.jsonParser.decodeFromString<ChapterListDto>(
        javaClass.getResourceAsStream("/mangadex/manga-feed.json")!!.bufferedReader().use { it.readText() },
    ).data.first()
    private val chapter = Chapter.create().copy(url = "/chapter/${dto.id}")

    @Test
    fun `language filters keep unrelated merged chapters and read state intact`() {
        val state = MangaDexChapterPresentation("manga", mapOf(chapter.url to dto), setOf("zz"))
        assertFalse(state.isVisible(chapter))
        assertTrue(state.isVisible(chapter.copy(url = "/another-provider/chapter")))
        assertEquals(dto.attributes.translatedLanguage.uppercase(), state.languageLabel(chapter))
        assertEquals(setOf(dto.attributes.translatedLanguage), state.languages)
        assertTrue(MangaDexChapterPresentation("manga", mapOf(chapter.url to dto)).isVisible(chapter))
    }

    @Test
    fun `global group and uploader exclusions both hide chapter rows`() {
        val data = dto.copy(relationships = listOf(RelationshipDto("group", "scanlation_group"), RelationshipDto("user", "user")))
        assertFalse(MangaDexChapterPresentation(chapters = mapOf(chapter.url to data), blockedGroups = setOf("group")).isVisible(chapter))
        assertFalse(MangaDexChapterPresentation(chapters = mapOf(chapter.url to data), blockedUploaders = setOf("user")).isVisible(chapter))
        assertTrue(MangaDexChapterPresentation(chapters = mapOf(chapter.url to data)).isVisible(chapter))
    }

    @Test
    fun `unavailable rows expose an explanation before reader navigation`() {
        val data = dto.copy(attributes = dto.attributes.copy(isUnavailable = true))
        val state = MangaDexChapterPresentation(chapters = mapOf(chapter.url to data))
        assertTrue(state.unavailable(chapter))
        assertEquals("Chapter is not available on MangaDex", state.accessMessage(chapter))
        assertFalse(state.unavailable(chapter.copy(url = "other")))
        assertNull(state.accessMessage(chapter.copy(url = "other")))
    }
}
