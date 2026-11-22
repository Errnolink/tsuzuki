package tachiyomi.data.manga

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import eu.kanade.tachiyomi.source.model.UpdateStrategy
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

class LibraryQueryEquivalenceTest {

    private lateinit var driver: SqlDriver
    private lateinit var db: Database
    private val mangasAdapter = Mangas.Adapter(
        StringListColumnAdapter,
        UpdateStrategyColumnAdapter,
        MemoColumnAdapter,
    )

    @BeforeEach
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        Database::class.schema.create(driver)
        val viewExists = driver.executeQuery(
            null,
            "SELECT count(*) FROM sqlite_master WHERE type = 'view' AND name = 'libraryView'",
            { cursor ->
                cursor.next()
                QueryResult.Value(cursor.getLong(0) == 1L)
            },
            0,
        ).value
        if (viewExists) {
            driver.execute(null, "DROP VIEW libraryView", 0)
        }
        driver.execute(null, REFERENCE_LIBRARY_VIEW, 0)
        db = Database::class.newInstance(
            driver,
            Chapters.Adapter(MemoColumnAdapter),
            History.Adapter(DateColumnAdapter),
            mangasAdapter,
        )
        seed()
    }

    private fun manga(
        id: Long,
        favorite: Boolean = true,
        source: Long = 1L,
        genre: List<String> = emptyList(),
    ) {
        db.mangasQueries.insert(
            source = source,
            url = "url-$id",
            artist = null,
            author = null,
            description = null,
            genre = genre,
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
        number: Double = 1.0,
        read: Boolean = false,
        bookmark: Boolean = false,
        lastPageRead: Long = 0L,
        scanlator: String? = null,
        name: String = "Chapter $id",
        memo: JsonObject = JsonObject(emptyMap()),
        dateFetch: Long = id * 10,
        dateUpload: Long = id * 100 + 7,
    ) {
        db.chaptersQueries.insert(
            mangaId = mangaId,
            url = "ch-$id",
            name = name,
            scanlator = scanlator,
            read = read,
            bookmark = bookmark,
            lastPageRead = lastPageRead,
            chapterNumber = number,
            sourceOrder = 0L,
            dateFetch = dateFetch,
            dateUpload = dateUpload,
            version = 0L,
            memo = memo,
        )
    }

    private fun history(chapterId: Long, lastRead: Long) {
        driver.execute(
            null,
            "INSERT INTO history(chapter_id, last_read, time_read) VALUES ($chapterId, $lastRead, $lastRead)",
            0,
        )
    }

    private fun merged(parent: Long, child: Long, childSource: Long = 24L) {
        db.mergedQueries.insert(
            infoManga = true,
            getChapterUpdates = true,
            chapterSortMode = 0L,
            chapterPriority = 0L,
            downloadChapters = false,
            mergeId = parent,
            mergeUrl = "merge-$parent",
            mangaId = child,
            mangaUrl = "child-$child",
            mangaSource = childSource,
        )
    }

    private fun unavailableMemo(): JsonObject = JsonObject(mapOf("nekoUnavailable" to kotlinx.serialization.json.JsonPrimitive(true)))

    private fun seed() {
        manga(1, genre = listOf("Action", "Drama"))
        db.mangas_categoriesQueries.insert(mangaId = 1, categoryId = 1)
        chapter(101, 1, number = 1.0, read = true, lastPageRead = 5)
        history(101, 1000)
        chapter(102, 1, number = 2.0, read = true, bookmark = true)
        chapter(103, 1, number = 4.0, bookmark = true)
        history(103, 2000)
        chapter(104, 1, number = 4.5)
        chapter(105, 1, number = 2.0)
        chapter(106, 1, number = 0.5)
        chapter(107, 1, number = -1.0, name = "Chapter 107 · Unavailable")
        chapter(108, 1, number = 0.0, memo = unavailableMemo())

        manga(2)
        manga(3)
        db.mangas_categoriesQueries.insert(mangaId = 3, categoryId = 2)
        db.mangas_categoriesQueries.insert(mangaId = 3, categoryId = 3)
        db.mangas_categoriesQueries.insert(mangaId = 3, categoryId = 4)
        chapter(301, 3, number = 1.0, read = true)
        history(301, 3000)
        chapter(302, 3, number = 2.0, read = true)

        manga(4)
        chapter(401, 4)

        manga(5)
        db.excluded_scanlatorsQueries.insert(mangaId = 5, scanlator = "A & B")
        chapter(501, 5, number = 1.0, scanlator = "A & B")
        chapter(502, 5, number = 2.0, read = true, scanlator = "A")
        chapter(503, 5, number = 3.0, read = true, scanlator = "A & C")
        chapter(504, 5, number = 4.0)
        chapter(505, 5, number = 5.0, read = true, scanlator = "Indie")

        manga(6)
        db.excluded_scanlatorsQueries.insert(mangaId = 6, scanlator = "X")
        chapter(601, 6)

        manga(7)
        chapter(701, 7, number = 3.0, read = true)

        manga(8, source = 24)
        chapter(801, 8, number = 1.0, read = true, scanlator = "ParentEx")
        chapter(802, 8, number = 2.0, read = true, scanlator = "A")
        chapter(803, 8, number = 3.0, scanlator = "Free")

        manga(9, favorite = false, source = 24)
        chapter(901, 9, number = 1.0, read = true, scanlator = "A & C")
        chapter(902, 9, number = 2.0, scanlator = "Free")

        manga(10, source = 6969)
        merged(parent = 10, child = 8)
        merged(parent = 10, child = 9)
        db.excluded_scanlatorsQueries.insert(mangaId = 10, scanlator = "ParentEx & A")

        manga(11, source = 6969)

        manga(12, favorite = false, source = 6969)
        manga(13, favorite = false, source = 24)
        merged(parent = 12, child = 13)
        chapter(1301, 13, number = 1.0, read = true)

        manga(14, favorite = false)
        db.mangas_categoriesQueries.insert(mangaId = 14, categoryId = 5)
        db.mangas_categoriesQueries.insert(mangaId = 14, categoryId = 6)
        chapter(1401, 14, number = 1.0, read = true)
        history(1401, 400)
        chapter(1402, 14, number = 2.0, read = true)

        manga(15, favorite = false)
        chapter(1501, 15)

        manga(16, favorite = false)

        manga(17)
        db.mangas_categoriesQueries.insert(mangaId = 17, categoryId = 1)
        chapter(1701, 17)
    }

    private fun setMatchAll(matchAll: Boolean) {
        driver.execute(null, "UPDATE tsuzuki_chapter_filter SET match_all = ${if (matchAll) 1 else 0}", 0)
    }

    private fun rows(sql: String): List<List<Any?>> {
        val result = mutableListOf<List<Any?>>()
        driver.executeQuery(
            null,
            sql,
            { cursor ->
                while (cursor.next().value) {
                    result.add(
                        listOf(
                            cursor.getLong(0)!!,
                            cursor.getLong(1)!!,
                            cursor.getString(2)!!,
                            cursor.getString(3),
                            cursor.getString(4),
                            cursor.getString(5),
                            cursor.getString(6)?.let { mangasAdapter.genreAdapter.decode(it) },
                            cursor.getString(7)!!,
                            cursor.getLong(8)!!,
                            cursor.getString(9),
                            cursor.getBoolean(10)!!,
                            cursor.getLong(11),
                            cursor.getLong(12),
                            cursor.getBoolean(13)!!,
                            cursor.getLong(14)!!,
                            cursor.getLong(15)!!,
                            cursor.getLong(16)!!,
                            cursor.getLong(17)!!,
                            cursor.getString(18),
                            mangasAdapter.update_strategyAdapter.decode(cursor.getLong(19)!!),
                            cursor.getLong(20)!!,
                            cursor.getLong(21)!!,
                            cursor.getLong(22),
                            cursor.getLong(23)!!,
                            cursor.getLong(24)!!,
                            cursor.getString(25)!!,
                            cursor.getBytes(26)?.let { mangasAdapter.memoAdapter.decode(it) },
                            cursor.getLong(27)!!,
                            cursor.getDouble(28)!!,
                            cursor.getLong(29)!!,
                            cursor.getLong(30)!!,
                            cursor.getLong(31)!!,
                            cursor.getDouble(32)!!,
                            cursor.getLong(33)!!,
                            cursor.getString(34)!!,
                            cursor.getLong(35)!!,
                            cursor.getLong(36)!!,
                        ),
                    )
                }
                QueryResult.Value(Unit)
            },
            0,
        )
        return result
    }

    private fun referenceLibrary(): List<List<Any?>> = rows("SELECT * FROM libraryView WHERE favorite = 1")

    private fun referenceReadMangaNonLibrary(): List<List<Any?>> =
        rows("SELECT * FROM libraryView WHERE favorite = 0 AND readCount != 0")

    private fun typedLibrary(): List<List<Any?>> =
        db.libraryViewQueries.library().executeAsList().map { row ->
            listOf<Any?>(
                row._id,
                row.source,
                row.url,
                row.artist,
                row.author,
                row.description,
                row.genre,
                row.title,
                row.status,
                row.thumbnail_url,
                row.favorite,
                row.last_update,
                row.next_update,
                row.initialized,
                row.viewer,
                row.chapter_flags,
                row.cover_last_modified,
                row.date_added,
                row.filtered_scanlators,
                row.update_strategy,
                row.calculate_interval,
                row.last_modified_at,
                row.favorite_modified_at,
                row.version,
                row.is_syncing,
                row.notes,
                row.memo,
                row.totalCount,
                row.readCount,
                row.latestUpload,
                row.chapterFetchedAt,
                row.lastRead,
                row.bookmarkCount,
                row.bookmarkedReadCount,
                row.categories,
                row.missingCount,
                row.unavailableCount,
            )
        }

    private fun typedReadMangaNonLibrary(): List<List<Any?>> =
        db.libraryViewQueries.readMangaNonLibrary().executeAsList().map { row ->
            listOf<Any?>(
                row._id,
                row.source,
                row.url,
                row.artist,
                row.author,
                row.description,
                row.genre,
                row.title,
                row.status,
                row.thumbnail_url,
                row.favorite,
                row.last_update,
                row.next_update,
                row.initialized,
                row.viewer,
                row.chapter_flags,
                row.cover_last_modified,
                row.date_added,
                row.filtered_scanlators,
                row.update_strategy,
                row.calculate_interval,
                row.last_modified_at,
                row.favorite_modified_at,
                row.version,
                row.is_syncing,
                row.notes,
                row.memo,
                row.totalCount,
                row.readCount,
                row.latestUpload,
                row.chapterFetchedAt,
                row.lastRead,
                row.bookmarkCount,
                row.bookmarkedReadCount,
                row.categories,
                row.missingCount,
                row.unavailableCount,
            )
        }

    private fun assertSameRowsAsSets(expected: List<List<Any?>>, actual: List<List<Any?>>, label: String) {
        assertEquals(expected.size, expected.map { it[0] }.toSet().size, "$label reference ids not unique")
        assertEquals(expected.size, actual.size, "$label row count: expected=$expected actual=$actual")
        val expectedById = expected.associateBy { it[0] }
        val actualById = actual.associateBy { it[0] }
        assertEquals(expectedById.keys, actualById.keys, "$label id sets differ")
        for (id in expectedById.keys.sortedBy { it as Long }) {
            assertEquals(expectedById[id], actualById[id], "$label row _id=$id")
        }
    }

    @Test
    fun newQueriesMatchReferenceView() {
        for (matchAll in listOf(false, true)) {
            setMatchAll(matchAll)
            assertSameRowsAsSets(referenceLibrary(), typedLibrary(), "library(matchAll=$matchAll)")
            assertSameRowsAsSets(
                referenceReadMangaNonLibrary(),
                typedReadMangaNonLibrary(),
                "readMangaNonLibrary(matchAll=$matchAll)",
            )
        }
    }

    private companion object {
        val REFERENCE_LIBRARY_VIEW = """
            CREATE VIEW libraryView AS
            SELECT
                M.*,
                coalesce(C.total, 0) AS totalCount,
                coalesce(C.readCount, 0) AS readCount,
                coalesce(C.latestUpload, 0) AS latestUpload,
                coalesce(C.fetchedAt, 0) AS chapterFetchedAt,
                coalesce(C.lastRead, 0) AS lastRead,
                coalesce(C.bookmarkCount, 0) AS bookmarkCount,
                coalesce(C.bookmarkReadCount, 0) AS bookmarkedReadCount,
                coalesce(MC.categories, '0') AS categories,
                coalesce(C.missingCount, 0) AS missingCount,
                coalesce(C.unavailableCount, 0) AS unavailableCount
            FROM mangas M
            LEFT JOIN (
                SELECT
                    chapters.manga_id,
                    count(*) AS total,
                    sum(read) AS readCount,
                    coalesce(max(chapters.date_upload), 0) AS latestUpload,
                    coalesce(max(history.last_read), 0) AS lastRead,
                    coalesce(max(chapters.date_fetch), 0) AS fetchedAt,
                    sum(chapters.bookmark) AS bookmarkCount,
                    sum(CASE WHEN chapters.bookmark = 1 AND chapters.read = 1 THEN 1 ELSE 0 END) AS bookmarkReadCount,
                    excluded_scanlators.scanlator AS ex_scanlator
                    , max(CASE WHEN chapters.chapter_number >= 1 THEN CAST(chapters.chapter_number AS INTEGER) ELSE 0 END)
                        - count(DISTINCT CASE WHEN chapters.chapter_number >= 1 THEN CAST(chapters.chapter_number AS INTEGER) END) AS missingCount,
                    count(CASE WHEN chapters.name LIKE '% · Unavailable'
                        OR instr(CAST(chapters.memo AS TEXT), '"nekoUnavailable":true') > 0 THEN 1 END) AS unavailableCount
                FROM chapters
                LEFT JOIN excludedScanlatorsView AS excluded_scanlators
                ON chapters.manga_id = excluded_scanlators.manga_id
                AND chapters.scanlator = excluded_scanlators.scanlator
                LEFT JOIN history
                ON chapters._id = history.chapter_id
                WHERE ex_scanlator IS NULL
                GROUP BY chapters.manga_id
            ) AS C
            ON M._id = C.manga_id
            LEFT JOIN (
                SELECT manga_id, group_concat(category_id) AS categories
                FROM mangas_categories
                GROUP BY manga_id
            ) AS MC
            ON MC.manga_id = M._id
            WHERE M.source <> 6969
            UNION
            SELECT
                M.*,
                coalesce(C.total, 0) AS totalCount,
                coalesce(C.readCount, 0) AS readCount,
                coalesce(C.latestUpload, 0) AS latestUpload,
                coalesce(C.fetchedAt, 0) AS chapterFetchedAt,
                coalesce(C.lastRead, 0) AS lastRead,
                coalesce(C.bookmarkCount, 0) AS bookmarkCount,
                coalesce(C.bookmarkReadCount, 0) AS bookmarkedReadCount,
                coalesce(MC.categories, '0') AS categories,
                coalesce(C.missingCount, 0) AS missingCount,
                coalesce(C.unavailableCount, 0) AS unavailableCount
            FROM mangas M
            LEFT JOIN (
                SELECT merged.manga_id,merged.merge_id
                FROM merged
                GROUP BY merged.merge_id
            ) AS ME
            ON ME.merge_id = M._id
            LEFT JOIN(
                SELECT
                    ME.merge_id,
                    count(*) AS total,
                    sum(read) AS readCount,
                    coalesce(max(chapters.date_upload), 0) AS latestUpload,
                    coalesce(max(history.last_read), 0) AS lastRead,
                    coalesce(max(chapters.date_fetch), 0) AS fetchedAt,
                    sum(chapters.bookmark) AS bookmarkCount,
                    sum(CASE WHEN chapters.bookmark = 1 AND chapters.read = 1 THEN 1 ELSE 0 END) AS bookmarkReadCount,
                    excluded_scanlators.scanlator AS ex_scanlator
                    , max(CASE WHEN chapters.chapter_number >= 1 THEN CAST(chapters.chapter_number AS INTEGER) ELSE 0 END)
                        - count(DISTINCT CASE WHEN chapters.chapter_number >= 1 THEN CAST(chapters.chapter_number AS INTEGER) END) AS missingCount,
                    count(CASE WHEN chapters.name LIKE '% · Unavailable'
                        OR instr(CAST(chapters.memo AS TEXT), '"nekoUnavailable":true') > 0 THEN 1 END) AS unavailableCount
                FROM chapters
                LEFT JOIN history
                ON chapters._id = history.chapter_id
                LEFT JOIN merged ME
                ON ME.manga_id = chapters.manga_id
                LEFT JOIN excludedScanlatorsView AS excluded_scanlators
                ON ME.merge_id = excluded_scanlators.manga_id
                AND chapters.scanlator = excluded_scanlators.scanlator
                WHERE ex_scanlator IS NULL
                GROUP BY ME.merge_id
            ) AS C
            ON M._id = C.merge_id
            LEFT JOIN (
                SELECT manga_id, group_concat(category_id) AS categories
                FROM mangas_categories
                GROUP BY manga_id
            ) AS MC
            ON MC.manga_id = M._id
            WHERE M.source = 6969;
        """.trimIndent()
    }
}
