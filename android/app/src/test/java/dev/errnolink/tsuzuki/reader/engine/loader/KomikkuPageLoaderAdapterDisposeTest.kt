package dev.errnolink.tsuzuki.reader.engine.loader

import dev.errnolink.tsuzuki.reader.engine.model.ReaderChapter
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.loader.PageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter

/**
 * The bridge teardown must stop the mirror flows without recycling the wrapped Komikku loader:
 * the loader stays owned by Komikku's reader (its own ref counting recycles it).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KomikkuPageLoaderAdapterDisposeTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private class RecordingLoader : PageLoader() {
        override var isLocal: Boolean = false
        var recycleCalls = 0

        override suspend fun getPages(): List<eu.kanade.tachiyomi.ui.reader.model.ReaderPage> = emptyList()

        override suspend fun loadPage(page: eu.kanade.tachiyomi.ui.reader.model.ReaderPage) = Unit

        override fun recycle() {
            recycleCalls++
        }
    }

    private val loader = RecordingLoader()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newAdapter(): Pair<KomikkuPageLoaderAdapter, eu.kanade.tachiyomi.ui.reader.model.ReaderPage> {
        val engineChapter = ReaderChapter(Chapter.create().copy(id = 7L, url = "/7", name = "Chapter 7"))
        val komikkuPage = eu.kanade.tachiyomi.ui.reader.model.ReaderPage(index = 0, url = "/7/page/0")
        val adapter = KomikkuPageLoaderAdapter(
            loader = loader,
            komikkuPages = listOf(komikkuPage),
            engineChapter = engineChapter,
            mirrorDispatcher = testDispatcher,
        )
        return adapter to komikkuPage
    }

    @Test
    fun `dispose stops mirroring and leaves the wrapped loader unrecycled`() {
        val (adapter, komikkuPage) = newAdapter()
        testScope.runCurrent()

        adapter.dispose()
        komikkuPage.status = Page.State.Ready
        testScope.runCurrent()

        assertFalse(adapter.pages.first().status == Page.State.Ready)
        assertEquals(0, loader.recycleCalls)
        assertTrue(adapter.isRecycled)
    }

    @Test
    fun `mirroring follows the komikku status until disposed`() {
        val (adapter, komikkuPage) = newAdapter()
        testScope.runCurrent()

        komikkuPage.status = Page.State.Ready
        testScope.runCurrent()

        assertTrue(adapter.pages.first().status == Page.State.Ready)
    }

    @Test
    fun `recycle recycles the wrapped loader exactly once`() {
        val (adapter, _) = newAdapter()

        adapter.recycle()

        assertEquals(1, loader.recycleCalls)
        assertTrue(adapter.isRecycled)
    }

    @Test
    fun `dispose before recycle still recycles the loader exactly once`() {
        val (adapter, _) = newAdapter()

        adapter.dispose()
        adapter.recycle()

        assertEquals(1, loader.recycleCalls)
    }
}
