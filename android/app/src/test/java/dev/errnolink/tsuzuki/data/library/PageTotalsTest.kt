package dev.errnolink.tsuzuki.data.library

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
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
import tachiyomi.domain.manga.model.Manga

class PageTotalsTest {

    private lateinit var db: Database
    private lateinit var pageTotals: PageTotals

    @BeforeEach
    fun setUp() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
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
        pageTotals = PageTotals(AndroidDatabaseHandler(db, driver, Dispatchers.Default, Dispatchers.Default))
    }

    @Test
    fun `carry over of a chapter that no longer exists is dropped instead of crashing startup`() = runTest {
        pageTotals.carryOver("987654:24")

        assertEquals(emptyMap<Long, Int>(), pageTotals.totals(listOf(987654L)))
    }

    @Test
    fun `recording a deleted chapter is ignored while existing chapters still update`() = runTest {
        val chapterId = insertChapter()

        pageTotals.record(chapterId, 20)
        pageTotals.record(chapterId, 31)
        pageTotals.record(chapterId + 1, 12)

        assertEquals(mapOf(chapterId to 31), pageTotals.totals(listOf(chapterId, chapterId + 1)))
    }

    private fun insertChapter(): Long {
        val manga = Manga.create().copy(url = "url/a", ogTitle = "A", source = 1L)
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
        val mangaId = db.mangasQueries.selectLastInsertedRowId().executeAsOne()
        db.chaptersQueries.insert(
            mangaId,
            "url/a/ch1",
            "Chapter 1",
            null,
            false,
            false,
            0,
            1.0,
            1,
            0,
            1_000,
            0,
            JsonObject(emptyMap()),
        )
        return db.chaptersQueries.selectLastInsertedRowId().executeAsOne()
    }
}
