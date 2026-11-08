package eu.kanade.tachiyomi.data.backup.create.creators

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupTracking
import eu.kanade.tachiyomi.source.online.MetadataSource
import exh.source.getMainSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.AndroidDatabaseHandler
import tachiyomi.data.Chapters
import tachiyomi.data.Database
import tachiyomi.data.DateColumnAdapter
import tachiyomi.data.History
import tachiyomi.data.Mangas
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.data.UpdateStrategyColumnAdapter
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.manga.interactor.GetFlatMetadataById
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import java.util.Date

class MangaBackupCreatorTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var db: Database
    private lateinit var handler: AndroidDatabaseHandler
    private lateinit var getCategories: GetCategories
    private lateinit var sourceManager: SourceManager
    private lateinit var getCustomMangaInfo: GetCustomMangaInfo
    private lateinit var getFlatMetadataById: GetFlatMetadataById
    private lateinit var creator: MangaBackupCreator

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
            chaptersAdapter = Chapters.Adapter(
                memoAdapter = MemoColumnAdapter,
            ),
        )
        handler = AndroidDatabaseHandler(db, driver, Dispatchers.Default, Dispatchers.Default)

        getCategories = mockk()
        sourceManager = mockk {
            every { get(any<Long>()) } returns null
        }
        val customMangaRepository = mockk<tachiyomi.domain.manga.repository.CustomMangaRepository>()
        every { customMangaRepository.get(any()) } returns null
        getCustomMangaInfo = GetCustomMangaInfo(customMangaRepository)
        getFlatMetadataById = mockk()

        creator = MangaBackupCreator(
            handler = handler,
            getCategories = getCategories,
            sourceManager = sourceManager,
            getCustomMangaInfo = getCustomMangaInfo,
            getFlatMetadataById = getFlatMetadataById,
        )
    }

    private fun insertManga(ogTitle: String, source: Long = 1L): Manga {
        val manga = Manga.create().copy(
            url = "url/$ogTitle",
            ogTitle = ogTitle,
            source = source,
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
        url: String = "${manga.url}/ch$sourceOrder",
    ): Long {
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
            sourceOrder * 1000L,
            0,
            JsonObject(emptyMap()),
        )
        return db.chaptersQueries.selectLastInsertedRowId().executeAsOne()
    }

    @Test
    fun `batched backup matches single-manga backups identically`() = runBlocking {
        val mergedSourceId = exh.source.MERGED_SOURCE_ID
        val metadataSourceId = 12345L
        val mockMetadataSource = mockk<MetadataSource<*, *>>()
        every { sourceManager.get(metadataSourceId) } returns mockMetadataSource

        val manga1 = insertManga("Manga One")
        val manga2 = insertManga("Manga Two")
        val mangaMerged = insertManga("Manga Merged", source = mergedSourceId)
        val mangaMeta = insertManga("Manga Meta", source = metadataSourceId)

        // Chapters
        val ch1Id = insertChapter(manga1, sourceOrder = 1L, read = true, lastPageRead = 5L)
        insertChapter(manga2, sourceOrder = 2L, scanlator = "ScanGroup", bookmark = true)
        insertChapter(mangaMerged, sourceOrder = 3L)
        insertChapter(mangaMeta, sourceOrder = 4L)

        // History
        db.historyQueries.upsert(
            chapterId = ch1Id,
            readAt = Date(123456L),
            time_read = 60L,
        )

        // Categories
        db.categoriesQueries.insert("Cat1", -1L, 0L, 0L)
        val cat = db.categoriesQueries.getCategories().executeAsList().first()
        db.mangas_categoriesQueries.insert(manga1.id, cat.id)
        every { runBlocking { getCategories.await(manga1.id) } } returns listOf(
            Category(cat.id, "Cat1", cat.order, 0L, false),
        )
        every { runBlocking { getCategories.await(manga2.id) } } returns emptyList()
        every { runBlocking { getCategories.await(mangaMerged.id) } } returns emptyList()
        every { runBlocking { getCategories.await(mangaMeta.id) } } returns emptyList()

        // Tracking
        db.manga_syncQueries.insert(
            mangaId = manga1.id,
            syncId = 1L,
            remoteId = 100L,
            libraryId = null,
            title = "Manga One",
            lastChapterRead = 1.0,
            totalChapters = 10L,
            status = 1L,
            score = 8.5,
            remoteUrl = "https://example.com/1",
            startDate = 0L,
            finishDate = 0L,
            private = false,
        )

        // Excluded scanlators
        db.excluded_scanlatorsQueries.insert(manga2.id, "BlockedGroup")

        // Merged manga references
        db.mergedQueries.insert(
            infoManga = false,
            getChapterUpdates = true,
            chapterSortMode = 0L,
            chapterPriority = 1L,
            downloadChapters = true,
            mergeId = mangaMerged.id,
            mergeUrl = mangaMerged.url,
            mangaId = manga1.id,
            mangaUrl = manga1.url,
            mangaSource = manga1.source,
        )

        // Metadata
        db.search_metadataQueries.upsert(
            mangaId = mangaMeta.id,
            uploader = "Tester",
            extra = "{\"key\":\"val\"}",
            indexedExtra = "indexed",
            extraVersion = 2L,
        )
        db.search_tagsQueries.insert(mangaMeta.id, "genre", "Action", 0L)
        db.search_titlesQueries.insert(mangaMeta.id, "Alt Title", 1L)

        val flatMetadata = exh.metadata.metadata.base.FlatMetadata(
            metadata = exh.metadata.sql.models.SearchMetadata(
                mangaId = mangaMeta.id,
                uploader = "Tester",
                extra = "{\"key\":\"val\"}",
                indexedExtra = "indexed",
                extraVersion = 2,
            ),
            tags = listOf(
                exh.metadata.sql.models.SearchTag(
                    id = 1L,
                    mangaId = mangaMeta.id,
                    namespace = "genre",
                    name = "Action",
                    type = 0,
                ),
            ),
            titles = listOf(
                exh.metadata.sql.models.SearchTitle(
                    id = 1L,
                    mangaId = mangaMeta.id,
                    title = "Alt Title",
                    type = 1,
                ),
            ),
        )
        every { runBlocking { getFlatMetadataById.await(mangaMeta.id) } } returns flatMetadata

        val options = BackupOptions(
            libraryEntries = true,
            categories = true,
            chapters = true,
            tracking = true,
            history = true,
            appSettings = false,
            extensionStores = false,
            sourceSettings = false,
            privateSettings = false,
            customInfo = false,
            readEntries = false,
            savedSearchesFeeds = false,
        )

        val mangas = listOf(manga1, manga2, mangaMerged, mangaMeta)

        val singleResults = mangas.map { creator(listOf(it), options).single() }
        val batchedResults = creator(mangas, options)

        assertEquals(mangas.size, batchedResults.size)
        for (i in mangas.indices) {
            val single = singleResults[i]
            val batched = batchedResults[i]
            org.junit.jupiter.api.Assertions.assertArrayEquals(
                protoBuf.encodeToByteArray(BackupManga.serializer(), single),
                protoBuf.encodeToByteArray(BackupManga.serializer(), batched),
                "backup bytes differ for ${single.title}",
            )

            assertEquals(single.title, batched.title)
            assertEquals(single.url, batched.url)
            assertEquals(single.source, batched.source)
            assertEquals(single.categories, batched.categories)
            assertEquals(single.excludedScanlators, batched.excludedScanlators)
            assertEquals(single.chapters.size, batched.chapters.size)
            single.chapters.zip(batched.chapters).forEach { (sCh, bCh) ->
                assertEquals(sCh.url, bCh.url)
                assertEquals(sCh.name, bCh.name)
                assertEquals(sCh.chapterNumber, bCh.chapterNumber)
                assertEquals(sCh.scanlator, bCh.scanlator)
                assertEquals(sCh.read, bCh.read)
                assertEquals(sCh.bookmark, bCh.bookmark)
                assertEquals(sCh.lastPageRead, bCh.lastPageRead)
                assertEquals(sCh.dateFetch, bCh.dateFetch)
                assertEquals(sCh.dateUpload, bCh.dateUpload)
                assertEquals(sCh.sourceOrder, bCh.sourceOrder)
                assertEquals(sCh.lastModifiedAt, bCh.lastModifiedAt)
                assertEquals(sCh.version, bCh.version)
                org.junit.jupiter.api.Assertions.assertArrayEquals(sCh.memo, bCh.memo)
            }
            assertEquals(single.history.size, batched.history.size)
            single.history.zip(batched.history).forEach { (sH, bH) ->
                assertEquals(sH.url, bH.url)
                assertEquals(sH.lastRead, bH.lastRead)
                assertEquals(sH.readDuration, bH.readDuration)
            }
            assertEquals(single.tracking.size, batched.tracking.size)
            single.tracking.zip(batched.tracking).forEach { (sT, bT) ->
                assertEquals(sT.syncId, bT.syncId)
                assertEquals(sT.mediaId, bT.mediaId)
                assertEquals(sT.libraryId, bT.libraryId)
                assertEquals(sT.title, bT.title)
                assertEquals(sT.lastChapterRead, bT.lastChapterRead)
                assertEquals(sT.totalChapters, bT.totalChapters)
                assertEquals(sT.score, bT.score)
                assertEquals(sT.status, bT.status)
                assertEquals(sT.trackingUrl, bT.trackingUrl)
            }
            assertEquals(single.mergedMangaReferences.size, batched.mergedMangaReferences.size)
            single.mergedMangaReferences.zip(batched.mergedMangaReferences).forEach { (sM, bM) ->
                assertEquals(sM.isInfoManga, bM.isInfoManga)
                assertEquals(sM.getChapterUpdates, bM.getChapterUpdates)
                assertEquals(sM.chapterSortMode, bM.chapterSortMode)
                assertEquals(sM.chapterPriority, bM.chapterPriority)
                assertEquals(sM.downloadChapters, bM.downloadChapters)
                assertEquals(sM.mergeUrl, bM.mergeUrl)
                assertEquals(sM.mangaUrl, bM.mangaUrl)
                assertEquals(sM.mangaSourceId, bM.mangaSourceId)
            }
            if (single.flatMetadata != null) {
                org.junit.jupiter.api.Assertions.assertNotNull(batched.flatMetadata)
                assertEquals(single.flatMetadata?.searchMetadata?.uploader, batched.flatMetadata?.searchMetadata?.uploader)
                assertEquals(single.flatMetadata?.searchMetadata?.extra, batched.flatMetadata?.searchMetadata?.extra)
                assertEquals(single.flatMetadata?.searchTags?.size, batched.flatMetadata?.searchTags?.size)
                assertEquals(single.flatMetadata?.searchTitles?.size, batched.flatMetadata?.searchTitles?.size)
            } else {
                org.junit.jupiter.api.Assertions.assertNull(batched.flatMetadata)
            }
        }
    }

    @Test
    fun `batched backup correctly chunks with more than 500 manga`() = runBlocking {
        val total = 550
        val mangas = (1..total).map { idx ->
            insertManga("Manga $idx")
        }
        mangas.forEachIndexed { idx, m ->
            insertChapter(m, sourceOrder = 1L, name = "Ch 1 of $idx")
            every { runBlocking { getCategories.await(m.id) } } returns emptyList()
        }

        val options = BackupOptions(
            libraryEntries = true,
            categories = true,
            chapters = true,
            tracking = false,
            history = false,
            appSettings = false,
            extensionStores = false,
            sourceSettings = false,
            privateSettings = false,
            customInfo = false,
            readEntries = false,
            savedSearchesFeeds = false,
        )

        val batchedResults = creator(mangas, options)
        assertEquals(total, batchedResults.size)
        assertEquals(1, batchedResults.first().chapters.size)
        assertEquals("Ch 1 of 0", batchedResults.first().chapters.first().name)
        assertEquals("Ch 1 of 549", batchedResults.last().chapters.first().name)
        val singles = mangas.map { creator(listOf(it), options).single() }
        singles.zip(batchedResults).forEach { (single, batched) ->
            org.junit.jupiter.api.Assertions.assertArrayEquals(
                protoBuf.encodeToByteArray(BackupManga.serializer(), single),
                protoBuf.encodeToByteArray(BackupManga.serializer(), batched),
                "backup bytes differ for ${single.title}",
            )
        }
    }

    @Test
    fun `a title that is both a favorite and a merge member is written once with its chapters once`() = runBlocking {
        val parent = insertManga("Soccer Genius")
        val other = insertManga("Other")
        insertChapter(parent, sourceOrder = 1L, name = "Episode 1")
        insertChapter(parent, sourceOrder = 0L, name = "Episode 2")
        listOf(parent, other).forEach { every { runBlocking { getCategories.await(it.id) } } returns emptyList() }
        val options = BackupOptions(
            libraryEntries = true,
            categories = true,
            chapters = true,
            tracking = false,
            history = false,
            appSettings = false,
            extensionStores = false,
            sourceSettings = false,
            privateSettings = false,
            customInfo = false,
            readEntries = true,
            savedSearchesFeeds = false,
        )

        val written = creator(listOf(parent, other, parent), options)

        assertEquals(listOf("Soccer Genius", "Other"), written.map { it.title })
        assertEquals(listOf("Episode 1", "Episode 2"), written.first().chapters.map { it.name }.sorted())
    }

    private val protoBuf = kotlinx.serialization.protobuf.ProtoBuf
}
