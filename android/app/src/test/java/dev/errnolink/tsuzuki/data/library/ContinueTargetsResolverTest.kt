package dev.errnolink.tsuzuki.data.library

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.util.chapter.getNextUnread
import exh.source.MERGED_SOURCE_ID
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.data.AndroidDatabaseHandler
import tachiyomi.data.Chapters
import tachiyomi.data.Database
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.History
import tachiyomi.data.Mangas
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.UpdateStrategyColumnAdapter
import tachiyomi.data.chapter.ChapterRepositoryImpl
import tachiyomi.data.manga.MangaMergeRepositoryImpl
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.GetMergedChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.interactor.GetMergedMangaById
import tachiyomi.domain.manga.interactor.GetMergedReferencesById
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ContinueTargetsResolverTest {

    companion object {
        private val injektRegistered = run {
            val downloadedOnly = mockk<Preference<Boolean>> {
                every { get() } returns false
            }
            val basePreferences = mockk<BasePreferences> {
                every { downloadedOnly() } returns downloadedOnly
            }
            Injekt.importModule(object : InjektModule {
                override fun InjektRegistrar.registerInjectables() {
                    addSingleton(basePreferences)
                }
            })
            true
        }
    }

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var db: Database
    private lateinit var chapterRepository: ChapterRepositoryImpl
    private lateinit var mangaMergeRepository: MangaMergeRepositoryImpl
    private lateinit var getChaptersByMangaId: GetChaptersByMangaId
    private lateinit var getMergedChaptersByMangaId: GetMergedChaptersByMangaId
    private lateinit var getMergedMangaById: GetMergedMangaById
    private lateinit var downloadManager: DownloadManager
    private lateinit var resolver: ContinueTargetsResolver

    private val downloadedUrls = mutableSetOf<String>()
    private val downloadCounts = mutableMapOf<Long, Int>()

    @BeforeEach
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database.Schema.create(driver)
        db = Database(
            driver = driver,
            historyAdapter = History.Adapter(last_readAdapter = DateColumnAdapter),
            mangasAdapter = Mangas.Adapter(
                genreAdapter = StringListColumnAdapter,
                update_strategyAdapter = UpdateStrategyColumnAdapter,
                memoAdapter = MemoColumnAdapter,
            ),
            chaptersAdapter = Chapters.Adapter(memoAdapter = MemoColumnAdapter),
        )
        val handler = AndroidDatabaseHandler(db, driver, Dispatchers.Default, Dispatchers.Default)
        chapterRepository = ChapterRepositoryImpl(handler)
        mangaMergeRepository = MangaMergeRepositoryImpl(handler)
        getChaptersByMangaId = GetChaptersByMangaId(chapterRepository)
        getMergedChaptersByMangaId = GetMergedChaptersByMangaId(
            chapterRepository,
            GetMergedReferencesById(mangaMergeRepository),
        )
        getMergedMangaById = GetMergedMangaById(mangaMergeRepository)
        downloadedUrls.clear()
        downloadCounts.clear()
        downloadManager = mockk {
            every { isChapterDownloaded(any(), any(), any(), any(), any(), any()) } answers {
                downloadedUrls.contains(arg<String>(2))
            }
        }
        val downloadCache = mockk<DownloadCache>()
        coEvery { downloadCache.getDownloadCounts(any<Sequence<Manga>>()) } answers {
            firstArg<Sequence<Manga>>().associate { it.id to downloadCounts.getOrDefault(it.id, 0) }
        }
        resolver = ContinueTargetsResolver(
            handler = handler,
            downloadManager = downloadManager,
            downloadCache = downloadCache,
            getMergedChaptersByMangaId = getMergedChaptersByMangaId,
            getMergedMangaById = getMergedMangaById,
        )
    }

    private fun insertManga(chapterFlags: Long, ogTitle: String, source: Long = 1L): Manga {
        val manga = Manga.create().copy(
            url = "url/$ogTitle",
            ogTitle = ogTitle,
            source = source,
            chapterFlags = chapterFlags,
        )
        db.mangasQueries.insert(
            manga.source,
            manga.url,
            manga.ogArtist,
            manga.ogAuthor,
            manga.ogDescription,
            manga.ogGenre,
            manga.ogTitle,
            manga.ogStatus,
            manga.ogThumbnailUrl,
            true,
            manga.lastUpdate,
            manga.nextUpdate,
            manga.initialized,
            manga.viewerFlags,
            manga.chapterFlags,
            manga.coverLastModified,
            manga.dateAdded,
            manga.updateStrategy,
            manga.fetchInterval.toLong(),
            manga.version,
            manga.notes,
            manga.memo,
        )
        return manga.copy(id = db.mangasQueries.selectLastInsertedRowId().executeAsOne())
    }

    private fun insertChapter(
        manga: Manga,
        sourceOrder: Long,
        chapterNumber: Double = sourceOrder.toDouble(),
        name: String = "Chapter $sourceOrder",
        scanlator: String? = null,
        read: Boolean = false,
        bookmark: Boolean = false,
        lastPageRead: Long = 0,
        dateUpload: Long = sourceOrder * 1_000,
        url: String = "${manga.url}/ch$sourceOrder",
    ): Chapter {
        db.chaptersQueries.insert(
            manga.id,
            url,
            name,
            scanlator,
            read,
            bookmark,
            lastPageRead,
            chapterNumber,
            sourceOrder,
            0,
            dateUpload,
            0,
            JsonObject(emptyMap()),
        )
        val id = db.chaptersQueries.selectLastInsertedRowId().executeAsOne()
        return Chapter(
            id = id,
            mangaId = manga.id,
            read = read,
            bookmark = bookmark,
            lastPageRead = lastPageRead,
            dateFetch = 0,
            sourceOrder = sourceOrder,
            url = url,
            name = name,
            dateUpload = dateUpload,
            chapterNumber = chapterNumber,
            scanlator = scanlator,
            lastModifiedAt = 0,
            version = 0,
            memo = JsonObject(emptyMap()),
        )
    }

    private suspend fun legacyNextUnreadChapter(manga: Manga): Chapter? {
        val mergedManga = getMergedMangaById.await(manga.id).associateBy { it.id }
        return getChaptersByMangaId.await(manga.id, applyFilter = true)
            .getNextUnread(manga, downloadManager, mergedManga)
    }

    private suspend fun assertBothPathsAgree(mangas: List<Manga>) {
        val candidates = mangas.map { ContinueTargetsResolver.Candidate(it.id, it) }
        val batched = resolver.resolve(candidates)
        mangas.forEach { manga ->
            assertEquals(
                legacyNextUnreadChapter(manga)?.id,
                batched[manga.id]?.chapterId,
                "continue chapter differs for manga ${manga.id} (${manga.ogTitle})",
            )
        }
    }

    @Test
    fun `source order sorting matches legacy for descending and ascending direction`() = runTest {
        val descending = insertManga(Manga.CHAPTER_SORTING_SOURCE, "source-desc")
        val ascending = insertManga(Manga.CHAPTER_SORTING_SOURCE or Manga.CHAPTER_SORT_ASC, "source-asc")
        listOf(descending, ascending).forEach { manga ->
            insertChapter(manga, 1, read = true)
            insertChapter(manga, 2)
            insertChapter(manga, 3, read = true)
            insertChapter(manga, 4)
        }
        assertBothPathsAgree(listOf(descending, ascending))
    }

    @Test
    fun `chapter number sorting matches legacy for both directions`() = runTest {
        val descending = insertManga(Manga.CHAPTER_SORTING_NUMBER, "number-desc")
        val ascending = insertManga(Manga.CHAPTER_SORTING_NUMBER or Manga.CHAPTER_SORT_ASC, "number-asc")
        listOf(descending, ascending).forEach { manga ->
            insertChapter(manga, 1, chapterNumber = 1.0)
            insertChapter(manga, 2, chapterNumber = 2.5, read = true)
            insertChapter(manga, 3, chapterNumber = 3.1)
            insertChapter(manga, 4, chapterNumber = 10.0, read = true)
            insertChapter(manga, 5, chapterNumber = 0.5)
            insertChapter(manga, 6, chapterNumber = 1.0)
        }
        assertBothPathsAgree(listOf(descending, ascending))
    }

    @Test
    fun `upload date and alphabetical sorting match legacy`() = runTest {
        val byDate = insertManga(Manga.CHAPTER_SORTING_UPLOAD_DATE, "upload-date")
        val byName = insertManga(Manga.CHAPTER_SORTING_ALPHABET, "alphabetical")
        insertChapter(byDate, 1, dateUpload = 500, read = true)
        insertChapter(byDate, 2, dateUpload = 9_000)
        insertChapter(byDate, 3, dateUpload = 1)
        insertChapter(byDate, 4, dateUpload = 400, read = true)
        insertChapter(byName, 1, name = "zeta")
        insertChapter(byName, 2, name = "Alpha")
        insertChapter(byName, 3, name = "midway", read = true)
        insertChapter(byName, 4, name = "beta")
        assertBothPathsAgree(listOf(byDate, byName))
    }

    @Test
    fun `unread filter is not and all-read libraries resolve to null in both paths`() = runTest {
        val showRead = insertManga(Manga.CHAPTER_SHOW_READ, "show-read")
        val allRead = insertManga(0, "all-read")
        insertChapter(showRead, 1)
        insertChapter(showRead, 2, read = true)
        insertChapter(allRead, 1, read = true)
        insertChapter(allRead, 2, read = true)
        assertBothPathsAgree(listOf(showRead, allRead))
        assertNull(legacyNextUnreadChapter(showRead))
        assertNull(legacyNextUnreadChapter(allRead))
    }

    @Test
    fun `bookmark filters use the sql mask in both paths`() = runTest {
        val bookmarkedOnly = insertManga(Manga.CHAPTER_SHOW_BOOKMARKED, "bookmarked-only")
        val notBookmarked = insertManga(Manga.CHAPTER_SHOW_NOT_BOOKMARKED, "not-bookmarked")
        listOf(bookmarkedOnly, notBookmarked).forEach { manga ->
            insertChapter(manga, 1, bookmark = true)
            insertChapter(manga, 2)
            insertChapter(manga, 3, bookmark = true, read = true)
            insertChapter(manga, 4)
        }
        assertBothPathsAgree(listOf(bookmarkedOnly, notBookmarked))
    }

    @Test
    fun `downloaded filters follow the mocked download cache in both paths`() = runTest {
        val downloadedOnly = insertManga(Manga.CHAPTER_SHOW_DOWNLOADED, "downloaded-only")
        val notDownloaded = insertManga(Manga.CHAPTER_SHOW_NOT_DOWNLOADED, "not-downloaded")
        val noFilter = insertManga(0, "no-download-filter")
        val chapters = listOf(downloadedOnly, notDownloaded, noFilter).flatMap { manga ->
            listOf(
                insertChapter(manga, 1),
                insertChapter(manga, 2, url = "${manga.url}/ch2"),
                insertChapter(manga, 3, url = "${manga.url}/ch3", read = true),
                insertChapter(manga, 4, url = "${manga.url}/ch4"),
            )
        }
        downloadedUrls.addAll(chapters.filter { it.sourceOrder == 4L }.map { it.url })
        assertBothPathsAgree(listOf(downloadedOnly, notDownloaded, noFilter))
    }

    @Test
    fun `excluded scanlators are skipped in both paths`() = runTest {
        val manga = insertManga(0, "excluded-scanlator")
        insertChapter(manga, 1, scanlator = "Scan A")
        insertChapter(manga, 2, scanlator = "Scan B")
        insertChapter(manga, 3, scanlator = "Scan A", read = true)
        db.excluded_scanlatorsQueries.insert(manga.id, "Scan A")
        assertBothPathsAgree(listOf(manga))
    }

    @Test
    fun `merged favorite continues on an unread child chapter and an empty title has no target`() = runTest {
        val container = insertManga(0, "merged-container", source = MERGED_SOURCE_ID)
        val childA = insertManga(0, "child-a")
        val childB = insertManga(0, "child-b")
        val unreadA = listOf(insertChapter(childA, 1), insertChapter(childA, 2)).map { it.id }
        insertChapter(childB, 1, read = true)
        db.mergedQueries.insert(
            infoManga = true,
            getChapterUpdates = true,
            chapterSortMode = 0,
            chapterPriority = 0,
            downloadChapters = true,
            mergeId = container.id,
            mergeUrl = container.url,
            mangaId = childA.id,
            mangaUrl = childA.url,
            mangaSource = childA.source,
        )
        db.mergedQueries.insert(
            infoManga = false,
            getChapterUpdates = true,
            chapterSortMode = 0,
            chapterPriority = 1,
            downloadChapters = true,
            mergeId = container.id,
            mergeUrl = container.url,
            mangaId = childB.id,
            mangaUrl = childB.url,
            mangaSource = childB.source,
        )
        val empty = insertManga(0, "no-chapters")
        val merged = resolver.resolveMerged(listOf(ContinueTargetsResolver.Candidate(container.id, container)))
        assertTrue(merged[container.id]?.chapterId in unreadA)
        assertBothPathsAgree(listOf(empty))
        assertNull(resolver.resolve(listOf(ContinueTargetsResolver.Candidate(empty.id, empty)))[empty.id])
    }

    @Test
    fun `resolved rows carry the target chapter last page read and progress math matches the reader`() = runTest {
        val manga = insertManga(0, "progress")
        insertChapter(manga, 1, lastPageRead = 11)
        insertChapter(manga, 2, lastPageRead = 11)
        val rows = resolver.resolve(listOf(ContinueTargetsResolver.Candidate(manga.id, manga)))
        val chapterId = rows.getValue(manga.id).chapterId
        assertEquals(legacyNextUnreadChapter(manga)?.id, chapterId)
        assertEquals(11L, rows.getValue(manga.id).lastPageRead)
        assertEquals(12f / 30f, resolver.progressOf(rows.getValue(manga.id), "$chapterId:30"))
        assertNull(resolver.progressOf(rows.getValue(manga.id), "other:30"))
        assertNull(resolver.progressOf(rows.getValue(manga.id), "bogus"))
    }

    @Test
    fun `chunked resolution agrees with legacy beyond the query chunk size`() = runTest {
        val mangas = (1..950).map { index ->
            val manga = insertManga(0, "bulk-$index")
            insertChapter(manga, 1)
            insertChapter(manga, 2, read = true)
            manga
        }
        assertBothPathsAgree(mangas)
    }

    @Test
    fun `digest is stable when nothing relevant changed and reacts to chapter download and scanlator changes`() = runTest {
        val mangaA = insertManga(0, "digest-a")
        val mangaB = insertManga(0, "digest-b")
        insertChapter(mangaA, 1)
        insertChapter(mangaB, 1)
        val candidates = listOf(mangaA, mangaB).map { ContinueTargetsResolver.Candidate(it.id, it) }

        val digest = resolver.digest(candidates)
        assertEquals(digest, resolver.digest(candidates))

        insertChapter(mangaB, 2)
        assertNotEquals(digest, resolver.digest(candidates))

        val afterInsert = resolver.digest(candidates)
        downloadCounts[mangaA.id] = 3
        assertNotEquals(afterInsert, resolver.digest(candidates))

        val afterDownload = resolver.digest(candidates)
        db.excluded_scanlatorsQueries.insert(mangaA.id, "Scan C")
        assertNotEquals(afterDownload, resolver.digest(candidates))

        val afterScanlator = resolver.digest(candidates)
        val refiltered = listOf(mangaA, mangaB).map {
            ContinueTargetsResolver.Candidate(it.id, it.copy(chapterFlags = Manga.CHAPTER_SHOW_UNREAD))
        }
        assertNotEquals(afterScanlator, resolver.digest(refiltered))
    }

    @Test
    fun `resolves continue targets when chapter columns contain NULLs`() = runTest {
        val manga = insertManga(0, "manga-with-null-columns")
        insertChapter(manga, 1, read = true)
        driver.execute(null, "PRAGMA legacy_alter_table = ON", 0)
        driver.execute(null, "ALTER TABLE chapters RENAME TO chapters_strict", 0)
        driver.execute(null, "CREATE TABLE chapters AS SELECT * FROM chapters_strict", 0)
        val chapterId = 9_000L
        driver.execute(
            null,
            """
            INSERT INTO chapters (
                _id, manga_id, url, name, scanlator, read, bookmark, last_page_read,
                chapter_number, source_order, date_fetch, date_upload, last_modified_at,
                version, is_syncing, memo
            ) VALUES (
                $chapterId, ${manga.id}, NULL, NULL, NULL, 0, NULL, NULL,
                NULL, NULL, NULL, NULL, NULL,
                NULL, NULL, NULL
            )
            """.trimIndent(),
            0,
        )

        val candidate = ContinueTargetsResolver.Candidate(manga.id, manga)
        val resolved = resolver.resolve(listOf(candidate))

        val target = resolved[manga.id]
        org.junit.jupiter.api.Assertions.assertNotNull(target)
        assertEquals(chapterId, target!!.chapterId)
        assertEquals(0L, target.lastPageRead)
    }
}
