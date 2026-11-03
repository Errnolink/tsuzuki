package exh.md

import exh.md.dto.ChapterListDto
import exh.md.dto.CoverListDto
import exh.md.dto.MangaListDto
import exh.md.handlers.MangaDexFeatureMapper
import exh.md.utils.MdUtil
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class MangaDexFeatureMapperTest {

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/mangadex/$name")!!.readBytes().decodeToString()

    @Test
    fun `cover list json maps to per-volume artwork`() {
        val dto = MdUtil.jsonParser.decodeFromString<CoverListDto>(fixture("cover.json"))
        assertEquals(48, dto.total)
        assertEquals(48, dto.data.size)
        assertEquals(0, dto.offset)
        assertEquals(100, dto.limit)

        val artwork = MangaDexFeatureMapper.artwork("f9c33607-9180-4ba6-b85c-e4b5faee7192", dto.data)
        assertEquals(dto.data.map { it.id }.distinct().size, artwork.size)

        val volumes = artwork.mapNotNull { it.volume?.toDoubleOrNull() }
        assertEquals(volumes.sorted(), volumes)

        val volume48 = artwork.first { it.volume == "48" }
        assertEquals("Volume 48", volume48.label)
        assertTrue(volume48.url.startsWith("https://uploads.mangadex.org/covers/f9c33607-9180-4ba6-b85c-e4b5faee7192/"))
        assertEquals("${volume48.url}.512.jpg", volume48.thumbnailUrl)

        assertEquals("Extra artwork", artwork.first { it.volume == null }.label)
    }

    @Test
    fun `chapter feed json maps to sorted feed entries`() {
        val chapters = MdUtil.jsonParser.decodeFromString<ChapterListDto>(fixture("manga-feed.json"))
        val manga = MdUtil.jsonParser.decodeFromString<MangaListDto>(fixture("manga.json"))
        assertEquals(20, chapters.data.size)
        assertEquals(354, chapters.total)
        assertEquals(1, manga.data.size)

        val farFuture = Instant.parse("2100-01-01T00:00:00Z")
        val entries = MangaDexFeatureMapper.feed(chapters.data, manga.data, "en", now = farFuture)
        assertEquals(chapters.data.size, entries.size)
        assertEquals(entries.map { it.publishedAt }.sortedDescending(), entries.map { it.publishedAt })

        val first = entries.first()
        assertEquals("Official \"Test\" Manga", first.mangaTitle)
        assertNotNull(first.coverUrl)
        assertTrue(first.coverUrl!!.startsWith("https://uploads.mangadex.org/covers/f9c33607-9180-4ba6-b85c-e4b5faee7192/"))

        val withVolume = entries.first { it.chapter.attributes.volume != null }
        assertTrue(withVolume.chapterName.startsWith("Vol. ${withVolume.chapter.attributes.volume}"))

        val withGroups = entries.firstOrNull { it.groups.isNotBlank() }
        assertNotNull(withGroups)

        assertTrue(MangaDexFeatureMapper.feed(chapters.data, emptyList(), "en", now = farFuture).isEmpty())
        assertTrue(MangaDexFeatureMapper.feed(chapters.data, manga.data, "en", now = Instant.EPOCH).isEmpty())
    }

    @Test
    fun `chapter names compose from volume chapter and title`() {
        val chapter = exh.md.dto.ChapterDataDto(
            id = "id",
            type = "chapter",
            attributes = exh.md.dto.ChapterAttributesDto(
                title = "A Quiet Day",
                volume = "3",
                chapter = "14.5",
                translatedLanguage = "en",
                externalUrl = null,
                pages = 10,
                version = 1,
                createdAt = "2026-01-01T00:00:00+00:00",
                updatedAt = "2026-01-01T00:00:00+00:00",
                publishAt = "2026-01-01T00:00:00+00:00",
                readableAt = "2026-01-01T00:00:00+00:00",
            ),
            relationships = emptyList(),
        )
        assertEquals("Vol. 3 · Ch. 14.5 · A Quiet Day", MangaDexFeatureMapper.chapterName(chapter))

        val bare = chapter.copy(
            attributes = chapter.attributes.copy(title = "", volume = null, chapter = null),
        )
        assertEquals("Oneshot", MangaDexFeatureMapper.chapterName(bare))
    }

    @Test
    fun `tracker links extract known ids only`() {
        val links = buildJsonObject {
            put("al", "113138")
            put("mal", "2")
            put("kt", "36115")
            put("mu", "https://www.mangaupdates.com/series.html?id=151792")
            put("ap", "https://apple.com/ignore")
            put("raw", "")
        }
        assertEquals(
            mapOf("al" to "113138", "mal" to "2", "kt" to "36115", "mu" to "151792"),
            MangaDexFeatureMapper.trackerLinks(links),
        )

        assertEquals(emptyMap<String, String>(), MangaDexFeatureMapper.trackerLinks(null))

        val pathStyle = buildJsonObject { put("mu", "https://www.mangaupdates.com/comics/2/") }
        assertEquals(mapOf("mu" to "2"), MangaDexFeatureMapper.trackerLinks(pathStyle))

        val invalid = buildJsonObject { put("al", "not-a-number") }
        assertEquals(emptyMap<String, String>(), MangaDexFeatureMapper.trackerLinks(invalid))
    }

    @Test
    fun `aggregate volumes flatten into the bounded missing estimate`() {
        fun chapter(id: String) = exh.md.dto.AggregateChapter(id, 1, "id-$id")

        fun volume(name: String, chapters: List<String>) =
            exh.md.dto.AggregateVolume(name, chapters.size, chapters.associateWith(::chapter))

        val numbered = exh.md.dto.AggregateDto(
            "ok",
            mapOf(
                "1" to volume("1", listOf("1", "2")),
                "2" to volume("2", listOf("3", "5")),
                "none" to volume("none", listOf("2.5")),
            ),
        )
        assertEquals(1, MangaDexFeatureMapper.estimatedMissing(numbered))
        assertEquals(3, MangaDexFeatureMapper.estimatedMissing(numbered, 7))

        val specialHeavy = exh.md.dto.AggregateDto(
            "ok",
            mapOf(
                "1" to volume("1", (1..30).map(Int::toString)),
                "none" to volume("none", (2000..2100).map(Int::toString)),
            ),
        )
        assertEquals(null, MangaDexFeatureMapper.estimatedMissing(specialHeavy))

        assertEquals(0, MangaDexFeatureMapper.estimatedMissing(exh.md.dto.AggregateDto("ok", emptyMap())))
    }
}
