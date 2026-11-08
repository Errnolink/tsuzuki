package tachiyomi.data.updates

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
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
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.manga.repository.CustomMangaRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The widget's process-lifetime subscription used to execute the full updatesView
 * query once per invalidation. It must emit the first result immediately and
 * coalesce invalidation bursts into one debounced re-run.
 */
class UpdatesWidgetSubscriptionTest {

    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { Database::class.schema.create(it) }
    private val db = Database::class.newInstance(
        driver,
        Chapters.Adapter(MemoColumnAdapter),
        History.Adapter(DateColumnAdapter),
        Mangas.Adapter(StringListColumnAdapter, UpdateStrategyColumnAdapter, MemoColumnAdapter),
    )
    private val handler = AndroidDatabaseHandler(db, driver, Dispatchers.IO)

    init {
        // mapUpdatesWithRelations builds MangaCover values, whose companion lazily
        // injects GetCustomMangaInfo; register a stub so the mapper can run in JVM tests.
        Injekt.addSingleton(GetCustomMangaInfo(StubCustomMangaRepository))
    }

    private object StubCustomMangaRepository : CustomMangaRepository {
        override fun get(mangaId: Long): CustomMangaInfo? = null
        override fun set(mangaInfo: CustomMangaInfo) = Unit
    }

    @Test
    fun `subscribeWithRead emits immediately then coalesces an invalidation burst into one re-run`() = runBlocking {
        db.mangasQueries.insert(
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
        // Unread chapter in the updates window: dateFetch (1) > dateAdded (0), dateUpload (1) > after (0).
        db.chaptersQueries.insert(
            mangaId = 1L,
            url = "ch-1",
            name = "Chapter 1",
            scanlator = "Group",
            read = false,
            bookmark = false,
            lastPageRead = 0L,
            chapterNumber = 1.0,
            sourceOrder = 1L,
            dateFetch = 1L,
            dateUpload = 1L,
            version = 0L,
            memo = JsonObject(emptyMap()),
        )
        val repository = UpdatesRepositoryImpl(handler)
        val seen = CopyOnWriteArrayList<Int>()
        val screen = launch(Dispatchers.IO) {
            repository.subscribeWithRead(read = false, after = 0L, limit = 500L).collect { seen += it.size }
        }
        withTimeout(5_000) { while (seen.isEmpty()) delay(10) }
        assertEquals(listOf(1), seen, "first emission must be immediate")

        // A burst of separate writes (separate invalidations, e.g. related-manga
        // inserts during a Details open) inside the debounce window.
        repeat(10) { id ->
            db.chaptersQueries.update(
                mangaId = 1L,
                url = "ch-1",
                name = "Chapter 1 ($id)",
                scanlator = "Group",
                read = false,
                bookmark = false,
                lastPageRead = 0L,
                chapterNumber = 1.0,
                sourceOrder = 1L,
                dateFetch = 1L,
                dateUpload = 1L,
                version = 0L,
                isSyncing = 0L,
                memo = null,
                chapterId = 1L,
            )
            delay(5)
        }
        delay(100)
        assertEquals(1, seen.size, "subscription re-ran ${seen.size - 1} times during the debounce window")

        withTimeout(5_000) { while (seen.size < 2) delay(10) }
        screen.cancel()
        assertEquals(2, seen.size, "burst must coalesce into exactly one debounced re-run")
    }
}
