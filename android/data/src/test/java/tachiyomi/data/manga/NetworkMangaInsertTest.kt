package tachiyomi.data.manga

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
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
import tachiyomi.domain.manga.model.Manga
import java.util.concurrent.CopyOnWriteArrayList

class NetworkMangaInsertTest {

    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { Database::class.schema.create(it) }
    private val db = Database::class.newInstance(
        driver,
        Chapters.Adapter(MemoColumnAdapter),
        History.Adapter(DateColumnAdapter),
        Mangas.Adapter(StringListColumnAdapter, UpdateStrategyColumnAdapter, MemoColumnAdapter),
    )
    private val handler = AndroidDatabaseHandler(db, driver, Dispatchers.IO)
    private val repository = MangaRepositoryImpl(handler)

    private fun network(url: String) = Manga.create().copy(source = 1L, url = url, ogTitle = "Title $url")

    @Test
    fun `listing titles the library already knows does not wake screens watching the library`() = runBlocking {
        val known = repository.insertNetworkManga(listOf(network("a"), network("b")), updateInfo = false)
        val seen = CopyOnWriteArrayList<Int>()
        val screen = launch(Dispatchers.IO) {
            handler.subscribeToList { mangasQueries.getAllMangaSourceAndUrl() }.collect { seen += it.size }
        }
        withTimeout(5_000) { while (seen.isEmpty()) delay(10) }

        repeat(5) {
            val again = repository.insertNetworkManga(listOf(network("b"), network("a")), updateInfo = false)
            assertEquals(listOf(known[1].id, known[0].id), again.map { it.id })
        }
        delay(300)
        screen.cancel()

        assertEquals(listOf(2), seen)
    }

    @Test
    fun `new titles mixed with known ones are inserted and come back in the order given`() = runBlocking {
        val known = repository.insertNetworkManga(listOf(network("a")), updateInfo = false).single()
        val result = repository.insertNetworkManga(listOf(network("x"), network("a"), network("y")), updateInfo = false)

        assertEquals(listOf("x", "a", "y"), result.map { it.url })
        assertEquals(known.id, result[1].id)
        assertEquals(3, handler.awaitList { mangasQueries.getAllMangaSourceAndUrl() }.size)
    }
}
