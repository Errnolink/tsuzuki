package dev.errnolink.tsuzuki.backup.kotatsu

import dev.errnolink.tsuzuki.mangadex.backup.NekoBackupMapper.SourceIdentity
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Behaviour tests for the Kotatsu backup import: a fixture zip (built the way
 * Kotatsu writes it, docs/kotatsu-backup.md) must convert into the app's Backup
 * model so the normal restore produces the right library entries, category
 * membership, chapter read state and unmatched-source count.
 */
class KotatsuBackupConverterTest {

    // Cross-language reference values: the Kotatsu id hash and the Mihon md5
    // source-id derivation were computed independently (Python) from the
    // published algorithms; pinning them here guards the join keys.
    private val mdUuid = "f9c33607-9180-4ba6-b85c-e4b5faee7192"
    private val chapterA = "8fb6d9c3-2a91-4d38-a24b-6f1e09d67ab3"
    private val chapterB = "1c56c4f6-15cd-4677-bff1-9ba7f5e2f9d0"
    private val chapterC = "b1bd9ba1-c3ba-4f05-a24d-6ba623cf3e0c"

    private val mdMangaId = 5473360056816312948L // hash("MANGADEX" + mdUuid)
    private val chapterAId = -6715657038144617160L
    private val chapterBId = 4683272648019406756L
    private val chapterCId = -2111523568663309565L
    private val mangafireSourceId = 6084907896154116083L // md5("mangafire/en/1")

    private val mangaDexSource = SourceIdentity(
        id = 2499283573021220255L,
        name = "MangaDex",
        language = "en",
        baseUrl = "https://mangadex.org",
    )

    private val mdMangaJson = """
        {"id":$mdMangaId,"title":"Witch Watch","alt_title":null,"url":"$mdUuid",
         "public_url":"https://mangadex.org/title/$mdUuid","rating":-1.0,"nsfw":false,
         "content_rating":null,"cover_url":"https://uploads.mangadex.org/covers/$mdUuid/cover.jpg",
         "large_cover_url":null,"state":"ONGOING","author":"Kenta Shinohara","source":"MANGADEX","tags":[]}
    """.trimIndent().replace("\n", "")

    private fun fixtureZip(): ByteArray {
        val categories = """
            [{"category_id":7,"created_at":1731000000000,"sort_key":1,"title":"Reading","order":"NEWEST","track":true,"show_in_lib":true},
             {"category_id":3,"created_at":1731000000000,"sort_key":0,"title":"Plan to read","order":"NEWEST","track":true,"show_in_lib":true}]
        """.trimIndent()
        val favourites = """
            [{"manga_id":$mdMangaId,"category_id":7,"sort_key":0,"pinned":false,"created_at":1731000000000,"manga":$mdMangaJson},
             {"manga_id":123,"category_id":3,"sort_key":1,"pinned":false,"created_at":1731000001000,
              "manga":{"id":123,"title":"Fire Title","url":"/read/some-slug","public_url":"https://mangafire.to/read/some-slug",
               "rating":-1.0,"cover_url":"https://mangafire.to/cover.jpg","state":"FINISHED","author":"A. Author","source":"MANGAFIRE_EN","tags":[]}}]
        """.trimIndent()
        val history = """
            [{"manga_id":$mdMangaId,"created_at":1731000000000,"updated_at":1731000100000,"chapter_id":$chapterAId,"page":3,"scroll":0.1,"percent":1.0,"chapters_count":3,"manga":$mdMangaJson},
             {"manga_id":$mdMangaId,"created_at":1731000000000,"updated_at":1731000200000,"chapter_id":$chapterBId,"page":7,"scroll":0.3,"percent":0.5,"chapters_count":3,"manga":$mdMangaJson}]
        """.trimIndent()
        val bookmarks = """
            [{"manga":$mdMangaJson,"tags":[],
              "bookmarks":[{"manga_id":$mdMangaId,"page_id":1,"chapter_id":$chapterCId,"page":2,"scroll":0,"image_url":"https://uploads.mangadex.org/page.jpg","created_at":1731000050000,"percent":0.2}]}]
        """.trimIndent()
        val index = """[{"app_id":"org.koitharu.kotatsu","app_version":8000,"created_at":1731000000000}]"""
        val sources = """[{"source":"MANGADEX","sort_key":0,"used_at":1731000000000,"added_in":7700,"pinned":false,"enabled":true}]"""
        return zip(
            "index" to index,
            "favourites" to favourites,
            "categories" to categories,
            "history" to history,
            "bookmarks" to bookmarks,
            "sources" to sources,
        )
    }

    private fun chapterStub() = listOf(
        KotatsuChapter(url = "/chapter/$chapterA", name = "Chapter 1", chapterNumber = 1.0, scanlator = "Group", dateUpload = 1000),
        KotatsuChapter(url = "/chapter/$chapterB", name = "Chapter 2", chapterNumber = 2.0, scanlator = "Group", dateUpload = 2000),
        KotatsuChapter(url = "/chapter/$chapterC", name = "Chapter 3", chapterNumber = 3.0, scanlator = "Group", dateUpload = 3000),
    )

    private suspend fun convert(
        bytes: ByteArray,
        chapters: List<KotatsuChapter> = chapterStub(),
        sources: List<SourceIdentity> = listOf(mangaDexSource),
        mangaDex: SourceIdentity? = mangaDexSource,
    ): KotatsuConversion {
        val backup = KotatsuBackupReader.read(ByteArrayInputStream(bytes))
        return KotatsuBackupConverter(sources, mangaDex) { chapters }.convert(backup)
    }

    @Test
    fun `favourites restore as library entries with categories and MangaDex identity`() = runTest {
        val conversion = convert(fixtureZip())
        val backup = conversion.backup

        // Category order is the list position after Kotatsu's sort_key.
        assertEquals(listOf("Plan to read", "Reading"), backup.backupCategories.map { it.name })
        assertEquals(listOf(0L, 1L), backup.backupCategories.map { it.order })

        val md = backup.backupManga.single { it.source == mangaDexSource.id }
        assertTrue(md.favorite)
        assertEquals("/manga/$mdUuid", md.url)
        assertEquals("Witch Watch", md.title)
        assertEquals(listOf(1L), md.categories) // "Reading"
        assertEquals(1, md.status)
        assertEquals("Kenta Shinohara", md.author)
        assertEquals("https://uploads.mangadex.org/covers/$mdUuid/cover.jpg", md.thumbnailUrl)
        assertEquals(1731000000000L, md.dateAdded)

        val fire = backup.backupManga.single { it.source == mangafireSourceId }
        assertTrue(fire.favorite)
        assertEquals("/read/some-slug", fire.url)
        assertEquals(listOf(0L), fire.categories) // "Plan to read"
        assertEquals(2, fire.status) // FINISHED
    }

    @Test
    fun `history and bookmarks restore as chapter read state, last page and history rows`() = runTest {
        val conversion = convert(fixtureZip())
        val md = conversion.backup.backupManga.single { it.source == mangaDexSource.id }

        val chapters = md.chapters.associateBy { it.url }
        assertEquals(3, md.chapters.size)

        // The whole chapter list is restored so the title is browsable.
        assertEquals("Chapter 1", chapters.getValue("/chapter/$chapterA").name)
        assertEquals(1.0f, chapters.getValue("/chapter/$chapterA").chapterNumber)

        // Latest history row = current position: page 7, not finished.
        val current = chapters.getValue("/chapter/$chapterB")
        assertFalse(current.read)
        assertEquals(7L, current.lastPageRead)

        // Earlier history rows count as read.
        val read = chapters.getValue("/chapter/$chapterA")
        assertTrue(read.read)
        assertEquals(3L, read.lastPageRead)

        // Bookmarked page keeps its chapter flagged.
        assertTrue(chapters.getValue("/chapter/$chapterC").bookmark)
        assertFalse(chapters.getValue("/chapter/$chapterA").bookmark)

        // History rows carry the last-read time of each chapter.
        val historyByChapter = md.history.associateBy { it.url }
        assertEquals(1731000100000L, historyByChapter.getValue("/chapter/$chapterA").lastRead)
        assertEquals(1731000200000L, historyByChapter.getValue("/chapter/$chapterB").lastRead)
    }

    @Test
    fun `sources without an installed match restore as stubs and are counted`() = runTest {
        val conversion = convert(fixtureZip())

        assertEquals(mapOf("mangafire" to 1), conversion.unmatchedSources)
        assertEquals(1, conversion.unmatchedTitles)
        assertEquals(1, conversion.warnings.count { it.contains("mangafire") && it.contains("extension") })

        val fire = conversion.backup.backupSources.single { it.sourceId == mangafireSourceId }
        assertEquals("mangafire", fire.name)
        // The app's own MangaDex source is listed under its real identity.
        assertEquals(mangaDexSource.id, conversion.backup.backupSources.single { it.name == "MangaDex" }.sourceId)
    }

    @Test
    fun `history only titles restore as non-favourite entries with progress`() = runTest {
        val history = """[{"manga_id":$mdMangaId,"created_at":1731000000000,"updated_at":1731000300000,"chapter_id":$chapterCId,"page":1,"scroll":0.0,"percent":1.0,"chapters_count":3,"manga":$mdMangaJson}]"""
        val conversion = convert(zip("history" to history, "index" to "[]"))

        val md = conversion.backup.backupManga.single()
        assertFalse(md.favorite)
        val chapter = md.chapters.single { it.url == "/chapter/$chapterC" }
        // Latest (only) row at full progress: the chapter is finished.
        assertTrue(chapter.read)
        assertEquals(1L, chapter.lastPageRead)
        assertEquals(1731000300000L, md.history.single().lastRead)
    }

    @Test
    fun `kotatsu zip is detected by content and other files are not`() {
        assertTrue(KotatsuBackupReader.isKotatsuBackup(ByteArrayInputStream(fixtureZip())))

        // A protobuf/gzip backup never carries zip section names.
        assertFalse(KotatsuBackupReader.isKotatsuBackup(ByteArrayInputStream(byteArrayOf(0x0a, 0x03, 0x08, 0x01))))
        // Neither does a zip of images.
        val cbz = zip("001.jpg" to "jpeg bytes", "002.jpg" to "jpeg bytes")
        assertFalse(KotatsuBackupReader.isKotatsuBackup(ByteArrayInputStream(cbz)))
        // Kotatsu sections without payload sections are not treated as restorable.
        val emptySections = zip("index" to "[]", "sources" to "[]")
        assertFalse(KotatsuBackupReader.isKotatsuBackup(ByteArrayInputStream(emptySections)))
    }

    @Test
    fun `chapters before the current chapter restore as read`() = runTest {
        // Six chapters; the backup's single history row sits on chapter 5 at page 3, 40%.
        val chapters = (1L..6L).map { number ->
            KotatsuChapter(url = "/chapter/chapter-$number", name = "Chapter $number", chapterNumber = number.toDouble())
        }
        val chapterFiveId = KotatsuIds.mangaId("MANGADEX", "chapter-5")
        val history = """
            [{"manga_id":$mdMangaId,"created_at":1731000000000,"updated_at":1731000400000,"chapter_id":$chapterFiveId,"page":3,"scroll":0.4,"percent":0.4,"chapters_count":6,"manga":$mdMangaJson}]
        """.trimIndent()
        val conversion = convert(zip("history" to history, "index" to "[]"), chapters = chapters)

        val restored = conversion.backup.backupManga.single().chapters.associateBy { it.url }
        (1L..4L).forEach { number ->
            assertTrue(restored.getValue("/chapter/chapter-$number").read, "chapter $number should be read")
        }
        val current = restored.getValue("/chapter/chapter-5")
        assertFalse(current.read)
        assertEquals(3L, current.lastPageRead)
        assertFalse(restored.getValue("/chapter/chapter-6").read)
    }

    @Test
    fun `unnumbered chapters use the source's newest-first order, so only older chapters restore as read`() = runTest {
        // The MangaDex feed often leaves numbers at -1; the list runs newest first (index 0 = newest).
        val chapters = listOf("f", "e", "d", "c", "b", "a").map { id ->
            KotatsuChapter(url = "/chapter/chapter-$id", name = "Oneshot $id", chapterNumber = -1.0)
        }
        val currentId = KotatsuIds.mangaId("MANGADEX", "chapter-c")
        val history = """
            [{"manga_id":$mdMangaId,"created_at":1731000000000,"updated_at":1731000400000,"chapter_id":$currentId,"page":2,"scroll":0.1,"percent":0.2,"chapters_count":6,"manga":$mdMangaJson}]
        """.trimIndent()
        val restored = convert(zip("history" to history, "index" to "[]"), chapters = chapters)
            .backup.backupManga.single().chapters.associateBy { it.url }

        assertTrue(restored.getValue("/chapter/chapter-a").read)
        assertTrue(restored.getValue("/chapter/chapter-b").read)
        assertFalse(restored.getValue("/chapter/chapter-c").read)
        assertEquals(2L, restored.getValue("/chapter/chapter-c").lastPageRead)
        listOf("d", "e", "f").forEach { assertFalse(restored.getValue("/chapter/chapter-$it").read, "newer chapter $it must stay unread") }
    }

    @Test
    fun `unavailable chapter lists keep the title and report lost progress`() = runTest {
        val conversion = convert(fixtureZip(), chapters = emptyList())

        val md = conversion.backup.backupManga.single { it.source == mangaDexSource.id }
        assertTrue(md.favorite)
        assertTrue(md.chapters.isEmpty())
        assertTrue(md.history.isEmpty())
        assertEquals(1, conversion.warnings.count { it.contains("Witch Watch") && it.contains("chapter list") })
        // No phantom chapters with made-up urls.
        assertTrue(conversion.backup.backupManga.none { it.chapters.isNotEmpty() || it.history.isNotEmpty() })
    }

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
