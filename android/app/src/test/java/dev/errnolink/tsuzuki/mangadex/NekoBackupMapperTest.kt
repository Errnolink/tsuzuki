package dev.errnolink.tsuzuki.mangadex

import dev.errnolink.tsuzuki.mangadex.backup.BackupMergeManga
import dev.errnolink.tsuzuki.mangadex.backup.NekoBackupMapper
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupMergedMangaReference
import exh.source.MERGED_SOURCE_ID
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.manga.repository.CustomMangaRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import java.io.File
import java.util.zip.GZIPInputStream

class NekoBackupMapperTest {
    @BeforeEach
    fun setUp() {
        val repository = mockk<CustomMangaRepository>()
        every { repository.get(any()) } returns null
        Injekt.addSingleton(GetCustomMangaInfo(repository))
    }

    @Test
    fun `Neko 900 to 907 fields coexist with SY and Komikku fields`() {
        val manga = BackupManga(
            source = 42, url = "/title/manga", title = "Title", notes = "Keep notes", version = 19,
            mergedMangaUrl = "/legacy", mergedMangaImageUrl = "https://example.org/legacy.jpg",
            scanlatorFilter = "Group A & Group B", alternativeArtwork = "https://example.org/cover.jpg",
            mergeMangaList = listOf(BackupMergeManga("/series/id", "Other", "cover", 3)),
            mergedMangaReferences = listOf(BackupMergedMangaReference(mangaSourceId = 50, mangaUrl = "/existing")),
            excludedScanlators = listOf("Existing"), customThumbnailUrl = "https://example.org/sy.jpg",
            chapters = listOf(BackupChapter(url = "/chapter/id", name = "Chapter", uploader = "Uploader", isUnavailable = true, smartOrder = 7, sourceOrder = 99, read = true, lastPageRead = 12, version = 5)),
        )
        val decoded = ProtoBuf.decodeFromByteArray(BackupManga.serializer(), ProtoBuf.encodeToByteArray(BackupManga.serializer(), manga))
        assertEquals("/legacy", decoded.mergedMangaUrl)
        assertEquals("https://example.org/legacy.jpg", decoded.mergedMangaImageUrl)
        assertEquals("https://example.org/cover.jpg", decoded.alternativeArtwork)
        assertEquals(listOf("Existing", "Group A", "Group B"), NekoBackupMapper.excludedScanlators(decoded))
        assertEquals(manga.mergeMangaList, decoded.mergeMangaList)
        assertEquals(manga.mergedMangaReferences, decoded.mergedMangaReferences)
        assertEquals(manga.customThumbnailUrl, decoded.customThumbnailUrl)
        assertEquals("Keep notes", decoded.getMangaImpl().notes)
        assertEquals(19L, decoded.getMangaImpl().version)
        val chapter = decoded.chapters.single().toChapterImpl()
        assertEquals("Uploader", chapter.scanlator)
        assertTrue(chapter.read)
        assertEquals(12L, chapter.lastPageRead)
        assertEquals(7L, chapter.sourceOrder)
        assertEquals(5L, chapter.version)
        assertEquals(JsonPrimitive("Uploader"), chapter.memo["nekoUploader"])
        assertEquals(JsonPrimitive(7), chapter.memo["nekoSmartOrder"])
        assertTrue(MangaDexChapterPresentation().unavailable(chapter))
        assertEquals("Chapter is not available on MangaDex", MangaDexChapterPresentation().accessMessage(chapter))
    }

    @Test
    fun `resolved Neko merge creates SY owner children and canonical chapter progress`() {
        val main = BackupChapter(url = "/chapter/id", name = "MD", read = true)
        val merged = BackupChapter(url = "/chapters/id", name = "Other", scanlator = "Weeb Central", lastPageRead = 6)
        val input = BackupManga(source = 1, url = "/title/id", title = "Title", chapters = listOf(main, merged), mergeMangaList = listOf(BackupMergeManga("/series/id", mergeType = 3)))
        val result = NekoBackupMapper.migrate(listOf(input), listOf(NekoBackupMapper.SourceIdentity(2, "WeebCentral", "en")))
        assertTrue(result.warnings.isEmpty())
        assertEquals(3, result.manga.size)
        val parent = result.manga.single { it.source == MERGED_SOURCE_ID }
        assertEquals(3, parent.mergedMangaReferences.size)
        assertEquals(setOf(1L, 2L, MERGED_SOURCE_ID), parent.mergedMangaReferences.map { it.mangaSourceId }.toSet())
        assertTrue(parent.chapters.isEmpty())
        assertTrue(parent.favorite)
        assertEquals(6L, result.manga.single { it.source == 2L }.chapters.single().lastPageRead)
        assertTrue(result.manga.filter { it.source != MERGED_SOURCE_ID }.none { it.favorite })
    }

    @Test
    fun `real Neko phone backup decodes counts preserves progress and reports every missing merge source`() {
        val path = System.getenv("NEKO_BACKUP") ?: "${System.getenv("LOCALAPPDATA")}/Tsuzuki/phone-backups/neko_2026-09-12_22-47.tachibk"
        val file = File(path)
        assumeTrue(file.isFile, "Set NEKO_BACKUP to the private phone backup; it is not committed")
        val backup = GZIPInputStream(file.inputStream()).use { ProtoBuf.decodeFromByteArray(Backup.serializer(), it.readBytes()) }
        assertEquals(1976, backup.backupManga.size)
        assertEquals(1874, backup.backupManga.count { it.favorite })
        assertEquals(89, backup.backupManga.sumOf { it.mergeMangaList.size })
        val chapters = backup.backupManga.flatMap { it.chapters }
        assertEquals(84950, chapters.size)
        assertEquals(24923, chapters.count { it.read })
        assertEquals(248, chapters.count { !it.read && it.lastPageRead > 0 })
        assertEquals(17, backup.backupManga.count { it.alternativeArtwork != null })
        assertEquals(70305, chapters.count { it.uploader != null })
        assertEquals(79561, chapters.count { it.smartOrder != 0 })
        val unresolved = NekoBackupMapper.migrate(backup.backupManga, emptyList())
        assertEquals(89, unresolved.warnings.size)
        val expectedNames = mapOf(3 to "Weeb Central", 7 to "MangaBall", 10 to "Comix", 11 to "Atsumaru")
        backup.backupManga.filter { it.mergeMangaList.isNotEmpty() }.forEach { title ->
            title.mergeMangaList.forEach { merge ->
                assertTrue(unresolved.warnings.any { it.startsWith("${title.title}:") && it.contains(expectedNames.getValue(merge.mergeType)) && it.contains(merge.url) })
            }
        }
        assertEquals(1976, unresolved.manga.size)
        assertEquals(84950, unresolved.manga.sumOf { it.chapters.size })
        assertEquals(24923, unresolved.manga.sumOf { it.chapters.count { chapter -> chapter.read } })
        val sources = expectedNames.map { (id, name) -> NekoBackupMapper.SourceIdentity(100L + id, name, "en") }
        val resolved = NekoBackupMapper.migrate(backup.backupManga, sources)
        assertTrue(resolved.warnings.isEmpty())
        assertEquals(89, resolved.manga.count { it.source == MERGED_SOURCE_ID })
        assertEquals(84950, resolved.manga.sumOf { it.chapters.size })
        assertEquals(24923, resolved.manga.sumOf { it.chapters.count { chapter -> chapter.read } })
        assertEquals(1874, resolved.manga.count { it.favorite })
        val partial = NekoBackupMapper.migrate(backup.backupManga, sources.filter { it.name == "Weeb Central" })
        assertEquals(33, partial.warnings.size)
        assertEquals(56, partial.manga.count { it.source == MERGED_SOURCE_ID })
        assertTrue(unresolved.manga.any { it.getMangaImpl().memo.containsKey("nekoUnresolvedChapters") })
    }
}
