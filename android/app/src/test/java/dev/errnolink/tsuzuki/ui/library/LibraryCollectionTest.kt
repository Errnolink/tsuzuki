package dev.errnolink.tsuzuki.ui.library

import app.cash.sqldelight.Query
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.data.AndroidDatabaseHandler
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.manga.MangaRepositoryImpl
import tachiyomi.domain.library.model.LibraryManga

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryCollectionTest {
    @Test
    fun `library invalidation burst executes SQL only after debounce but first result is immediate`() = runTest {
        var listener: Query.Listener? = null
        var executions = 0
        val query = object : Query<Int>({ it.getLong(0)!!.toInt() }) {
            override fun addListener(queryListener: Listener) {
                check(listener == null)
                listener = queryListener
            }

            override fun removeListener(queryListener: Listener) {
                check(listener === queryListener)
                listener = null
            }

            override fun <R> execute(mapper: (SqlCursor) -> QueryResult<R>): QueryResult<R> {
                val value = ++executions
                return mapper(object : SqlCursor {
                    private var hasNext = true

                    override fun next() = QueryResult.Value(hasNext.also { hasNext = false })
                    override fun getLong(index: Int) = value.toLong()
                    override fun getString(index: Int): String? = error("Unexpected string column")
                    override fun getBytes(index: Int): ByteArray? = error("Unexpected byte column")
                    override fun getDouble(index: Int): Double? = error("Unexpected double column")
                    override fun getBoolean(index: Int): Boolean? = error("Unexpected boolean column")
                })
            }
        }
        val handler = AndroidDatabaseHandler(mockk(), mockk(), StandardTestDispatcher(testScheduler))
        val results = mutableListOf<List<Int>>()
        backgroundScope.launch { handler.subscribeToList(300) { query }.toList(results) }
        runCurrent()
        assertEquals(listOf(listOf(1)), results)
        repeat(20) { listener!!.queryResultsChanged() }
        runCurrent()
        advanceTimeBy(299)
        assertEquals(1, executions)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(listOf(1), listOf(2)), results)
    }

    @Test
    fun `unrelated writes with equal library rows do not emit another snapshot`() = runTest {
        val handler = mockk<DatabaseHandler>()
        val manga = mockk<LibraryManga>()
        val changed = mockk<LibraryManga>()
        every { handler.subscribeToList<LibraryManga>(300, any()) } returns flowOf(
            listOf(manga), listOf(manga), listOf(changed), listOf(changed),
        )
        assertEquals(
            listOf(listOf(manga), listOf(changed)),
            MangaRepositoryImpl(handler).getLibraryMangaAsFlow().toList(),
        )
    }
}
