package tachiyomi.data.history

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.serialization.json.JsonObject
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.util.lang.toLong
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
import tachiyomi.domain.history.model.HistoryWithRelations
import java.util.Date

class HistoryViewTest {

    private lateinit var driver: SqlDriver
    private lateinit var db: Database

    @BeforeEach
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database::class.schema.create(driver)
        db = Database::class.newInstance(
            driver,
            Chapters.Adapter(MemoColumnAdapter),
            History.Adapter(DateColumnAdapter),
            Mangas.Adapter(StringListColumnAdapter, UpdateStrategyColumnAdapter, MemoColumnAdapter),
        )
        seed()
    }

    private fun manga(id: Long, favorite: Boolean = true) {
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
            favorite = favorite,
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

    private fun chapter(id: Long, mangaId: Long, read: Boolean = false, bookmark: Boolean = false, scanlator: String? = null, number: Double = 1.0) {
        db.chaptersQueries.insert(
            mangaId = mangaId,
            url = "ch-$id",
            name = "Chapter $id",
            scanlator = scanlator,
            read = read,
            bookmark = bookmark,
            lastPageRead = 0L,
            chapterNumber = number,
            sourceOrder = 0L,
            dateFetch = 0L,
            dateUpload = 0L,
            version = 0L,
            memo = JsonObject(emptyMap()),
        )
    }

    private fun history(chapterId: Long, lastRead: Long, timeRead: Long = 10L) {
        db.historyQueries.upsert(chapterId = chapterId, readAt = Date(lastRead), time_read = timeRead)
    }

    private fun seed() {
        manga(1)
        manga(2)
        manga(3)
        manga(4, favorite = false)
        chapter(11, 1, read = true, number = 1.0)
        chapter(12, 1, read = true, number = 2.0)
        chapter(13, 1, read = false, bookmark = true, number = 3.0)
        chapter(21, 2, read = false, number = 1.0)
        chapter(22, 2, read = true, bookmark = true, number = 2.0, scanlator = "GroupB")
        chapter(23, 2, read = false, number = 3.0, scanlator = "GroupB")
        chapter(31, 3, read = false, number = 1.0)
        chapter(41, 4, read = false, number = 1.0)
        history(11, lastRead = 300L)
        history(12, lastRead = 500L)
        history(13, lastRead = 100L)
        history(21, lastRead = 400L)
        history(22, lastRead = 200L)
        history(31, lastRead = 50L)
        db.excluded_scanlatorsQueries.insert(mangaId = 2, scanlator = "GroupB")
    }

    private fun historyRows(
        unfinishedManga: Boolean? = null,
        unfinishedChapter: Boolean? = null,
        nonLibraryEntries: Boolean? = null,
        query: String = "",
    ): List<HistoryWithRelations> {
        return db.historyViewQueries.history(
            0x40L,
            0x20L,
            unfinishedManga?.toLong(),
            unfinishedChapter,
            nonLibraryEntries,
            query,
            HistoryMapper::mapHistoryWithRelations,
        ).executeAsList()
    }

    @Test
    fun rewrittenViewMatchesPreviousDefinition() {
        val variants = listOf(
            arrayOf<Boolean?>(null, null, null),
            arrayOf<Boolean?>(null, true, false),
            arrayOf<Boolean?>(true, null, null),
            arrayOf<Boolean?>(false, false, true),
        )
        for (filters in variants) {
            val current = historyRows(filters[0], filters[1], filters[2])
            driver.execute(null, "DROP VIEW historyView", 0)
            driver.execute(null, PREVIOUS_HISTORY_VIEW, 0)
            val previous = historyRows(filters[0], filters[1], filters[2])
            driver.execute(null, "DROP VIEW historyView", 0)
            driver.execute(null, CURRENT_HISTORY_VIEW, 0)
            assertEquals(previous, current)
        }
        val queried = historyRows(query = "manga 1")
        driver.execute(null, "DROP VIEW historyView", 0)
        driver.execute(null, PREVIOUS_HISTORY_VIEW, 0)
        val queriedPrevious = historyRows(query = "manga 1")
        assertEquals(queriedPrevious, queried)
    }

    @Test
    fun recentHistoryLimitMatchesDistinctThenTake() {
        for (limit in 1L..4L) {
            val expected = historyRows(unfinishedChapter = true, nonLibraryEntries = false)
                .distinctBy { it.mangaId }
                .take(limit.toInt())
            val limited = db.historyViewQueries.getRecentHistory(
                0x40L,
                0x20L,
                null,
                true,
                false,
                "",
                limit,
                HistoryMapper::mapHistoryWithRelations,
            ).executeAsList()
            assertEquals(expected, limited)
        }
    }

    @Test
    fun scopedLatestMatchesGlobalSubscription() {
        val global = historyRows()
        val scopes = (1L..4L).map { setOf(it) } + listOf(setOf(1L, 3L), setOf(2L, 3L, 4L))
        for (mangaIds in scopes) {
            val scoped = db.historyViewQueries.getLatestHistoryByMangaIds(
                0x40L,
                0x20L,
                mangaIds,
                HistoryMapper::mapHistoryWithRelations,
            ).executeAsList()
            assertEquals(global.filter { it.mangaId in mangaIds }, scoped)
        }
    }

    private companion object {
        val CURRENT_HISTORY_VIEW = """
            CREATE VIEW historyView AS
            SELECT
                history._id AS id,
                mangas._id AS mangaId,
                chapters._id AS chapterId,
                mangas.title,
                mangas.thumbnail_url AS thumbnailUrl,
                mangas.source,
                mangas.favorite,
                mangas.cover_last_modified,
                chapters.chapter_number AS chapterNumber,
                mangas.chapter_flags AS chapterFlags,
                chapters.read AS read,
                chapters.last_page_read AS lastPageRead,
                coalesce(C.total, 0) AS totalCount,
                coalesce(C.readCount, 0) AS readCount,
                coalesce(C.bookmarkCount, 0) AS bookmarkCount,
                coalesce(C.bookmarkReadCount, 0) AS bookmarkReadCount,
                history.last_read AS readAt,
                history.time_read AS readDuration,
                max_last_read.last_read AS maxReadAt,
                max_last_read.chapter_id AS maxReadAtChapterId
            FROM mangas
            JOIN chapters
            ON mangas._id = chapters.manga_id
            JOIN history
            ON chapters._id = history.chapter_id
            JOIN (
                SELECT chapters.manga_id,chapters._id AS chapter_id, MAX(history.last_read) AS last_read
                FROM history CROSS JOIN chapters
                ON chapters._id = history.chapter_id
                GROUP BY chapters.manga_id
            ) AS max_last_read
            ON chapters.manga_id = max_last_read.manga_id
            LEFT JOIN(
                SELECT
                    chapters.manga_id,
                    count(*) AS total,
                    sum(read) AS readCount,
                    sum(bookmark) AS bookmarkCount,
                    sum(CASE WHEN bookmark = 1 AND read = 1 THEN 1 ELSE 0 END) AS bookmarkReadCount,
                    excluded_scanlators.scanlator AS ex_scanlator
                FROM (
                    SELECT DISTINCT history_chapters.manga_id
                    FROM history
                    JOIN chapters history_chapters
                    ON history_chapters._id = history.chapter_id
                ) AS with_history
                CROSS JOIN chapters
                ON chapters.manga_id = with_history.manga_id
                LEFT JOIN excludedScanlatorsView AS excluded_scanlators
                ON chapters.manga_id = excluded_scanlators.manga_id
                AND chapters.scanlator = excluded_scanlators.scanlator
                WHERE ex_scanlator IS NULL
                GROUP BY chapters.manga_id
            ) AS C
            ON mangas._id = C.manga_id
        """.trimIndent()

        val PREVIOUS_HISTORY_VIEW = CURRENT_HISTORY_VIEW
            .replace("FROM history CROSS JOIN chapters", "FROM chapters JOIN history")
            .replace(
                """
                FROM (
                    SELECT DISTINCT history_chapters.manga_id
                    FROM history
                    JOIN chapters history_chapters
                    ON history_chapters._id = history.chapter_id
                ) AS with_history
                CROSS JOIN chapters
                ON chapters.manga_id = with_history.manga_id
                """.trimIndent(),
                "FROM chapters",
            )
    }
}
