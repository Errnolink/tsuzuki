package tachiyomi.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.data.newInstance
import tachiyomi.data.data.schema

class PageTotalsTableTest {

    private lateinit var driver: SqlDriver
    private lateinit var db: Database

    @BeforeEach
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database::class.schema.create(driver)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        db = driver.newDatabase()
    }

    private fun SqlDriver.newDatabase(): Database = Database::class.newInstance(
        this,
        Chapters.Adapter(MemoColumnAdapter),
        History.Adapter(DateColumnAdapter),
        Mangas.Adapter(StringListColumnAdapter, UpdateStrategyColumnAdapter, MemoColumnAdapter),
    )

    private fun manga(id: Long) {
        db.mangasQueries.insert(
            source = 1L,
            url = "url-$id",
            artist = null,
            author = null,
            description = null,
            genre = emptyList(),
            title = "Manga $id",
            status = 1L,
            thumbnailUrl = null,
            favorite = true,
            lastUpdate = 0L,
            nextUpdate = 0L,
            initialized = true,
            viewerFlags = 0L,
            chapterFlags = 0L,
            coverLastModified = 0L,
            dateAdded = 0L,
            updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
            calculateInterval = 0L,
            version = 0L,
            notes = "",
            memo = JsonObject(emptyMap()),
        )
    }

    private fun chapter(id: Long, mangaId: Long, read: Boolean = false, lastPageRead: Long = 0L) {
        db.chaptersQueries.insert(
            mangaId = mangaId,
            url = "ch-$id",
            name = "Chapter $id",
            scanlator = null,
            read = read,
            bookmark = false,
            lastPageRead = lastPageRead,
            chapterNumber = 1.0,
            sourceOrder = 0L,
            dateFetch = 0L,
            dateUpload = 0L,
            version = 0L,
            memo = JsonObject(emptyMap()),
        )
    }

    private fun totalsByChapterIds(ids: List<Long>): Map<Long, Long> =
        db.tsuzuki_page_totalsQueries.totalsByChapterIds(ids).executeAsList()
            .associate { it.chapter_id to it.total_pages }

    private fun totalsForInProgress(): Map<Long, Long> =
        db.tsuzuki_page_totalsQueries.totalsForInProgress().executeAsList()
            .associate { it.chapter_id to it.total_pages }

    @Test
    fun `upsert overwrites the total and totals by ids keeps only requested chapters`() {
        manga(1)
        chapter(1, mangaId = 1)
        chapter(2, mangaId = 1)
        db.tsuzuki_page_totalsQueries.upsert(1L, 24L)
        db.tsuzuki_page_totalsQueries.upsert(1L, 30L)
        db.tsuzuki_page_totalsQueries.upsert(2L, 10L)
        assertEquals(mapOf(1L to 30L), totalsByChapterIds(listOf(1L, 99L)))
    }

    @Test
    fun `totals for in progress keeps only started unread chapters`() {
        manga(1)
        chapter(1, mangaId = 1, read = false, lastPageRead = 3)
        chapter(2, mangaId = 1, read = false, lastPageRead = 0)
        chapter(3, mangaId = 1, read = true, lastPageRead = 5)
        db.tsuzuki_page_totalsQueries.upsert(1L, 24L)
        db.tsuzuki_page_totalsQueries.upsert(2L, 24L)
        db.tsuzuki_page_totalsQueries.upsert(3L, 24L)
        assertEquals(mapOf(1L to 24L), totalsForInProgress())
    }

    @Test
    fun `deleting a chapter removes its total`() {
        manga(1)
        chapter(1, mangaId = 1)
        db.tsuzuki_page_totalsQueries.upsert(1L, 24L)
        assertEquals(mapOf(1L to 24L), totalsByChapterIds(listOf(1L)))
        db.chaptersQueries.removeChaptersWithIds(listOf(1L))
        assertTrue(totalsByChapterIds(listOf(1L)).isEmpty())
    }

    @Test
    fun `migration 53 recreates the table and keeps the cascade`() {
        val migrationDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database::class.schema.create(migrationDriver)
        migrationDriver.execute(null, "DROP TABLE tsuzuki_page_totals", 0)
        migrationDriver.execute(null, "PRAGMA foreign_keys = ON", 0)
        Database::class.schema.migrate(migrationDriver, 53, 54)
        val migrationDb = migrationDriver.newDatabase()

        migrationDb.mangasQueries.insert(
            source = 1L,
            url = "url-1",
            artist = null,
            author = null,
            description = null,
            genre = emptyList(),
            title = "Manga 1",
            status = 1L,
            thumbnailUrl = null,
            favorite = true,
            lastUpdate = 0L,
            nextUpdate = 0L,
            initialized = true,
            viewerFlags = 0L,
            chapterFlags = 0L,
            coverLastModified = 0L,
            dateAdded = 0L,
            updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
            calculateInterval = 0L,
            version = 0L,
            notes = "",
            memo = JsonObject(emptyMap()),
        )
        migrationDb.chaptersQueries.insert(
            mangaId = 1L,
            url = "ch-1",
            name = "Chapter 1",
            scanlator = null,
            read = false,
            bookmark = false,
            lastPageRead = 4L,
            chapterNumber = 1.0,
            sourceOrder = 0L,
            dateFetch = 0L,
            dateUpload = 0L,
            version = 0L,
            memo = JsonObject(emptyMap()),
        )
        migrationDb.tsuzuki_page_totalsQueries.upsert(1L, 24L)
        assertEquals(
            mapOf(1L to 24L),
            migrationDb.tsuzuki_page_totalsQueries.totalsForInProgress().executeAsList()
                .associate { it.chapter_id to it.total_pages },
        )
        migrationDb.chaptersQueries.removeChaptersWithIds(listOf(1L))
        assertTrue(migrationDb.tsuzuki_page_totalsQueries.totalsByChapterIds(listOf(1L)).executeAsList().isEmpty())
    }
}
