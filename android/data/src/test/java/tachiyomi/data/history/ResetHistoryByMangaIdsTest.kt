package tachiyomi.data.history

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import eu.kanade.tachiyomi.source.model.UpdateStrategy
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
import tachiyomi.data.data.newInstance
import tachiyomi.data.data.schema
import java.util.Date

class ResetHistoryByMangaIdsTest {

    private lateinit var handler: AndroidDatabaseHandler
    private lateinit var repository: HistoryRepositoryImpl
    private lateinit var db: Database

    @BeforeEach
    fun setUp() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database::class.schema.create(driver)
        db = Database::class.newInstance(
            driver,
            Chapters.Adapter(MemoColumnAdapter),
            History.Adapter(DateColumnAdapter),
            Mangas.Adapter(StringListColumnAdapter, UpdateStrategyColumnAdapter, MemoColumnAdapter),
        )
        handler = AndroidDatabaseHandler(db, driver, Dispatchers.IO)
        repository = HistoryRepositoryImpl(handler)
    }

    @Test
    fun `resets the history of every manga in a selection that exceeds the variable limit`() =
        runBlocking {
            val mangaIds = (1L..1200L).toList() + 1L
            seed(mangaIds)

            repository.resetHistoryByMangaIds(mangaIds)

            mangaIds.distinct().forEach { mangaId ->
                val history = db.historyQueries
                    .getHistoryByChapterUrl(mangaId, "/chapter/$mangaId")
                    .executeAsOne()
                assertEquals(Date(0), history.last_read)
            }
        }

    @Test
    fun `resets the history of the selected chapters only`() = runBlocking {
        val mangaIds = (1L..1100L).toList()
        seed(mangaIds)

        val selected = db.historyQueries.getHistoryByMangaId(mangaIds.first()).executeAsList()
            .map { it._id }
        repository.resetHistory(selected)

        val first = db.historyQueries
            .getHistoryByChapterUrl(mangaIds.first(), "/chapter/${mangaIds.first()}")
            .executeAsOne()
        assertEquals(Date(0), first.last_read)
        val second = db.historyQueries
            .getHistoryByChapterUrl(mangaIds[1], "/chapter/${mangaIds[1]}")
            .executeAsOne()
        assertEquals(Date(1000L), second.last_read)
    }

    private fun seed(mangaIds: List<Long>) = runBlocking {
        handler.await(inTransaction = true) {
            mangaIds.distinct().forEach { mangaId ->
                mangasQueries.insert(
                    source = 1L,
                    url = "/title/$mangaId",
                    artist = null,
                    author = null,
                    description = null,
                    genre = emptyList(),
                    title = "Manga $mangaId",
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
                chaptersQueries.insert(
                    mangaId,
                    "/chapter/$mangaId",
                    "Chapter $mangaId",
                    null,
                    false,
                    false,
                    0L,
                    mangaId.toDouble(),
                    0L,
                    0L,
                    0L,
                    0L,
                    JsonObject(emptyMap()),
                )
            }
            mangaIds.distinct().forEach { mangaId ->
                val chapter = chaptersQueries.getChapterByUrl("/chapter/$mangaId").executeAsOne()
                historyQueries.upsert(
                    chapter._id,
                    Date(1000L),
                    5L,
                )
            }
        }
    }
}
