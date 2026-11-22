package tachiyomi.data.updates

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
import tachiyomi.core.common.util.lang.toLong
import eu.kanade.tachiyomi.source.model.UpdateStrategy

class UpdatesQueryEquivalenceTest {

    private lateinit var driver: SqlDriver
    private lateinit var db: Database

    @BeforeEach
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database::class.schema.create(driver)
        val viewExists = driver.executeQuery(
            null,
            "SELECT count(*) FROM sqlite_master WHERE type = 'view' AND name = 'updatesView'",
            { cursor ->
                cursor.next()
                QueryResult.Value(cursor.getLong(0) == 1L)
            },
            0,
        ).value
        if (viewExists) {
            driver.execute(null, "DROP VIEW updatesView", 0)
        }
        driver.execute(null, REFERENCE_UPDATES_VIEW, 0)
        db = Database::class.newInstance(
            driver,
            Chapters.Adapter(MemoColumnAdapter),
            History.Adapter(DateColumnAdapter),
            Mangas.Adapter(StringListColumnAdapter, UpdateStrategyColumnAdapter, MemoColumnAdapter),
        )
        seed()
    }

    private fun manga(id: Long, favorite: Boolean = true, source: Long = 1L) {
        db.mangasQueries.insert(
            source = source,
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
            dateAdded = 1000L,
            updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
            calculateInterval = 0L,
            version = 0L,
            notes = "",
            memo = JsonObject(emptyMap()),
        )
    }

    private fun chapter(
        id: Long,
        mangaId: Long,
        read: Boolean = false,
        bookmark: Boolean = false,
        lastPageRead: Long = 0L,
        scanlator: String? = null,
        dateFetch: Long,
        dateUpload: Long,
    ) {
        db.chaptersQueries.insert(
            mangaId = mangaId,
            url = "ch-$id",
            name = "Chapter $id",
            scanlator = scanlator,
            read = read,
            bookmark = bookmark,
            lastPageRead = lastPageRead,
            chapterNumber = 1.0,
            sourceOrder = 0L,
            dateFetch = dateFetch,
            dateUpload = dateUpload,
            version = 0L,
            memo = JsonObject(emptyMap()),
        )
    }

    private fun seed() {
        manga(1)
        chapter(101, 1, dateFetch = 5000, dateUpload = 6000)
        chapter(102, 1, read = true, lastPageRead = 10, dateFetch = 5001, dateUpload = 6001)
        chapter(103, 1, lastPageRead = 3, dateFetch = 5002, dateUpload = 6002)
        chapter(104, 1, bookmark = true, dateFetch = 5003, dateUpload = 6003)
        chapter(105, 1, dateFetch = 1000, dateUpload = 99999)
        chapter(106, 1, dateFetch = 999, dateUpload = 99998)
        chapter(107, 1, dateFetch = 5005, dateUpload = 100)
        chapter(108, 1, dateFetch = 5004, dateUpload = 6004)
        chapter(109, 1, dateFetch = 5004, dateUpload = 6005)

        manga(2)
        db.excluded_scanlatorsQueries.insert(mangaId = 2, scanlator = "A")
        chapter(201, 2, scanlator = "A & B", dateFetch = 5100, dateUpload = 6100)
        chapter(202, 2, scanlator = "A", dateFetch = 5101, dateUpload = 6101)
        chapter(203, 2, scanlator = "Indie", dateFetch = 5102, dateUpload = 6102)
        chapter(204, 2, scanlator = "B", dateFetch = 5004, dateUpload = 6103)
        chapter(205, 2, dateFetch = 5104, dateUpload = 6104)

        manga(3, favorite = false)
        chapter(301, 3, dateFetch = 5200, dateUpload = 6200)

        manga(4, source = 6969)
        chapter(401, 4, dateFetch = 5300, dateUpload = 6300)

        manga(5, source = 6969)
        manga(6, source = 24)
        manga(7, source = 24)
        db.mergedQueries.insert(
            infoManga = true,
            getChapterUpdates = true,
            chapterSortMode = 0L,
            chapterPriority = 0L,
            downloadChapters = false,
            mergeId = 5L,
            mergeUrl = "merge-5",
            mangaId = 6L,
            mangaUrl = "child-6",
            mangaSource = 24L,
        )
        db.mergedQueries.insert(
            infoManga = true,
            getChapterUpdates = true,
            chapterSortMode = 0L,
            chapterPriority = 0L,
            downloadChapters = false,
            mergeId = 5L,
            mergeUrl = "merge-5-b",
            mangaId = 7L,
            mangaUrl = "child-7",
            mangaSource = 24L,
        )
        db.excluded_scanlatorsQueries.insert(mangaId = 5, scanlator = "ParentEx & A")
        chapter(601, 6, scanlator = "ParentEx", dateFetch = 5400, dateUpload = 6400)
        chapter(602, 6, scanlator = "ChildFree", dateFetch = 5401, dateUpload = 6401)
        chapter(701, 7, scanlator = "ChildFree", dateFetch = 5500, dateUpload = 6500)
        chapter(702, 7, scanlator = "A & B", dateFetch = 5501, dateUpload = 6501)
    }

    private fun setMatchAll(matchAll: Boolean) {
        driver.execute(null, "UPDATE tsuzuki_chapter_filter SET match_all = ${if (matchAll) 1 else 0}", 0)
    }

    private fun referenceRows(sql: String): List<List<Any?>> {
        val rows = mutableListOf<List<Any?>>()
        driver.executeQuery(
            null,
            sql,
            { cursor ->
                while (cursor.next().value) {
                    rows.add(
                        listOf(
                            cursor.getLong(0)!!,
                            cursor.getString(1)!!,
                            cursor.getLong(2)!!,
                            cursor.getString(3)!!,
                            cursor.getString(4),
                            cursor.getString(5)!!,
                            cursor.getBoolean(6),
                            cursor.getBoolean(7),
                            cursor.getLong(8)!!,
                            cursor.getLong(9)!!,
                            cursor.getBoolean(10),
                            cursor.getString(11),
                            cursor.getLong(12)!!,
                            cursor.getLong(13)!!,
                            cursor.getLong(14)!!,
                            cursor.getString(15),
                        ),
                    )
                }
                QueryResult.Value(Unit)
            },
            0,
        )
        return rows
    }

    private fun booleanLiteral(value: Boolean?): String = when (value) {
        null -> "NULL"
        else -> if (value) "1" else "0"
    }

    private fun referenceRecentUpdates(after: Long, limit: Long): List<List<Any?>> =
        referenceRows(
            """
            SELECT *
            FROM updatesView
            WHERE dateUpload > $after
            LIMIT $limit
            """.trimIndent(),
        )

    private fun referenceUpdatesByReadStatus(read: Boolean, after: Long, limit: Long): List<List<Any?>> =
        referenceRows(
            """
            SELECT *
            FROM updatesView
            WHERE read = ${booleanLiteral(read)}
            AND dateUpload > $after
            LIMIT $limit
            """.trimIndent(),
        )

    private fun referenceRecentUpdatesWithFilters(
        after: Long,
        limit: Long,
        read: Boolean?,
        started: Boolean?,
        bookmarked: Boolean?,
        hideExcludedScanlators: Boolean,
    ): List<List<Any?>> = referenceRows(
        """
        SELECT *
        FROM updatesView
        WHERE dateUpload > $after
        AND (${booleanLiteral(read)} IS NULL OR read = ${booleanLiteral(read)})
        AND (
            ${booleanLiteral(started)} IS NULL
            OR (${booleanLiteral(started)} = 1 AND last_page_read > 0 AND read = 0)
            OR (${booleanLiteral(started)} = 0 AND last_page_read = 0 AND read = 0)
        )
        AND (${booleanLiteral(bookmarked)} IS NULL OR bookmark = ${booleanLiteral(bookmarked)})
        AND (
            (excludedScanlator IS NULL OR ${if (hideExcludedScanlators) 1 else 0} = 0)
        )
        LIMIT $limit
        """.trimIndent(),
    )

    private fun assertSameRows(expected: List<List<Any?>>, actual: List<List<Any?>>, label: String) {
        assertEquals(expected.size, actual.size, "$label row count: expected=$expected actual=$actual")
        expected.zip(actual).forEachIndexed { index, (expectedRow, actualRow) ->
            assertEquals(expectedRow, actualRow, "$label row $index")
        }
    }

    private fun checkRecentUpdates() {
        for (after in listOf(500L, 6100L)) {
            for (limit in listOf(3L, 1000L)) {
                val expected = referenceRecentUpdates(after, limit)
                val actual = db.updatesViewQueries.getRecentUpdates(
                    after = after,
                    limit = limit,
                ).executeAsList()
                    .map { row ->
                        listOf<Any?>(
                            row.mangaId,
                            row.mangaTitle,
                            row.chapterId,
                            row.chapterName,
                            row.scanlator,
                            row.chapterUrl,
                            row.read,
                            row.bookmark,
                            row.last_page_read,
                            row.source,
                            row.favorite,
                            row.thumbnailUrl,
                            row.coverLastModified,
                            row.dateUpload,
                            row.datefetch,
                            row.excludedScanlator,
                        )
                    }
                assertSameRows(expected, actual, "getRecentUpdates(after=$after, limit=$limit)")
            }
        }
    }

    private fun checkUpdatesByReadStatus() {
        for (read in listOf(false, true)) {
            for (after in listOf(500L, 6100L)) {
                for (limit in listOf(3L, 1000L)) {
                    val expected = referenceUpdatesByReadStatus(read, after, limit)
                    val actual = db.updatesViewQueries.getUpdatesByReadStatus(
                    read = read,
                    after = after,
                    limit = limit,
                ).executeAsList()
                        .map { row ->
                            listOf<Any?>(
                                row.mangaId,
                                row.mangaTitle,
                                row.chapterId,
                                row.chapterName,
                                row.scanlator,
                                row.chapterUrl,
                                row.read,
                                row.bookmark,
                                row.last_page_read,
                                row.source,
                                row.favorite,
                                row.thumbnailUrl,
                                row.coverLastModified,
                                row.dateUpload,
                                row.datefetch,
                                row.excludedScanlator,
                            )
                        }
                    assertSameRows(expected, actual, "getUpdatesByReadStatus(read=$read, after=$after, limit=$limit)")
                }
            }
        }
    }

    private fun checkRecentUpdatesWithFilters() {
        val readValues = listOf<Boolean?>(null, false, true)
        val startedValues = listOf<Boolean?>(null, false, true)
        val bookmarkedValues = listOf<Boolean?>(null, false, true)
        for (after in listOf(500L, 6100L)) {
            for (read in readValues) {
                for (started in startedValues) {
                    for (bookmarked in bookmarkedValues) {
                        for (hideExcludedScanlators in listOf(false, true)) {
                            for (limit in listOf(3L, 1000L)) {
                                val expected = referenceRecentUpdatesWithFilters(
                                    after,
                                    limit,
                                    read,
                                    started,
                                    bookmarked,
                                    hideExcludedScanlators,
                                )
                                val actual = db.updatesViewQueries.getRecentUpdatesWithFilters(
                                    after = after,
                                    limit = limit,
                                    read = read,
                                    started = started?.toLong(),
                                    bookmarked = bookmarked,
                                    hideExcludedScanlators = if (hideExcludedScanlators) 1L else 0L,
                                ).executeAsList()
                                    .map { row ->
                                        listOf<Any?>(
                                            row.mangaId,
                                            row.mangaTitle,
                                            row.chapterId,
                                            row.chapterName,
                                            row.scanlator,
                                            row.chapterUrl,
                                            row.read,
                                            row.bookmark,
                                            row.last_page_read,
                                            row.source,
                                            row.favorite,
                                            row.thumbnailUrl,
                                            row.coverLastModified,
                                            row.dateUpload,
                                            row.datefetch,
                                            row.excludedScanlator,
                                        )
                                    }
                                assertSameRows(
                                    expected,
                                    actual,
                                    "getRecentUpdatesWithFilters(after=$after, limit=$limit, read=$read, " +
                                        "started=$started, bookmarked=$bookmarked, " +
                                        "hideExcludedScanlators=$hideExcludedScanlators)",
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun newQueriesMatchReferenceViewForEveryParameterCombination() {
        for (matchAll in listOf(false, true)) {
            setMatchAll(matchAll)
            checkRecentUpdates()
            checkUpdatesByReadStatus()
            checkRecentUpdatesWithFilters()
        }
    }

    private companion object {
        val REFERENCE_UPDATES_VIEW = """
            CREATE VIEW updatesView AS
            SELECT
                mangas._id AS mangaId,
                mangas.title AS mangaTitle,
                chapters._id AS chapterId,
                chapters.name AS chapterName,
                chapters.scanlator,
                chapters.url AS chapterUrl,
                chapters.read,
                chapters.bookmark,
                chapters.last_page_read,
                mangas.source,
                mangas.favorite,
                mangas.thumbnail_url AS thumbnailUrl,
                mangas.cover_last_modified AS coverLastModified,
                chapters.date_upload AS dateUpload,
                chapters.date_fetch AS datefetch,
                excluded_scanlators.scanlator AS excludedScanlator
            FROM mangas JOIN chapters
            ON mangas._id = chapters.manga_id
            LEFT JOIN excludedScanlatorsView AS excluded_scanlators
            ON mangas._id = excluded_scanlators.manga_id
            AND chapters.scanlator = excluded_scanlators.scanlator
            WHERE favorite = 1 AND source <> 6969
            AND date_fetch > date_added
            UNION
            SELECT
                mangas._id AS mangaId,
                mangas.title AS mangaTitle,
                chapters._id AS chapterId,
                chapters.name AS chapterName,
                chapters.scanlator,
                chapters.url AS chapterUrl,
                chapters.read,
                chapters.bookmark,
                chapters.last_page_read,
                mangas.source,
                mangas.favorite,
                mangas.thumbnail_url AS thumbnailUrl,
                mangas.cover_last_modified AS coverLastModified,
                chapters.date_upload AS dateUpload,
                chapters.date_fetch AS datefetch,
                excluded_scanlators.scanlator AS excludedScanlator
            FROM mangas
            LEFT JOIN (
                SELECT merged.manga_id,merged.merge_id
                FROM merged
                GROUP BY merged.merge_id
            ) AS ME
            ON ME.merge_id = mangas._id
            JOIN chapters
            ON ME.manga_id = chapters.manga_id
            LEFT JOIN excludedScanlatorsView AS excluded_scanlators
            ON mangas._id = excluded_scanlators.manga_id
            AND chapters.scanlator = excluded_scanlators.scanlator
            WHERE favorite = 1 AND source = 6969
            AND date_fetch > date_added
            ORDER BY datefetch DESC, chapterId DESC, mangaId DESC
        """.trimIndent()
    }
}
