package tachiyomi.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.data.data.newInstance
import tachiyomi.data.data.schema
import java.util.concurrent.CopyOnWriteArrayList

class HoldingSubscriptionsTest {

    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { Database::class.schema.create(it) }
    private val db = Database::class.newInstance(
        driver,
        Chapters.Adapter(MemoColumnAdapter),
        History.Adapter(DateColumnAdapter),
        Mangas.Adapter(StringListColumnAdapter, UpdateStrategyColumnAdapter, MemoColumnAdapter),
    )
    private val handler = AndroidDatabaseHandler(db, driver, Dispatchers.IO)

    @Test
    fun `an open screen does not re-run its query for every restored title and sees all of them afterwards`() = runBlocking {
        val seen = CopyOnWriteArrayList<Int>()
        val screen = launch(Dispatchers.IO) {
            handler.subscribeToList { mangasQueries.getAllMangaSourceAndUrl() }.collect { seen += it.size }
        }
        withTimeout(5_000) { while (seen.isEmpty()) kotlinx.coroutines.delay(10) }

        handler.holdingSubscriptions {
            repeat(40) { id -> handler.await(inTransaction = true) { insertManga(id.toLong()) } }
        }

        val final = withTimeout(5_000) {
            handler.subscribeToList { mangasQueries.getAllMangaSourceAndUrl() }.first()
        }
        withTimeout(5_000) { while (seen.last() != 40) kotlinx.coroutines.delay(10) }
        screen.cancel()

        assertEquals(40, final.size)
        assertTrue(seen.size <= 3, "screen re-ran ${seen.size} times: $seen")
    }

    @Test
    fun `a screen opened during a restore still gets its first result`() = runBlocking {
        handler.holdingSubscriptions {
            handler.await(inTransaction = true) { insertManga(1) }
            val first = withTimeout(5_000) {
                handler.subscribeToList { mangasQueries.getAllMangaSourceAndUrl() }.first()
            }
            assertEquals(1, first.size)
        }
    }

    private fun Database.insertManga(id: Long) {
        mangasQueries.insert(
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
}
