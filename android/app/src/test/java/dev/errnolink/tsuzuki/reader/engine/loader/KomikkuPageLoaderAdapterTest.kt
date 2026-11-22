package dev.errnolink.tsuzuki.reader.engine.loader

import dev.errnolink.tsuzuki.reader.engine.domain.CheckTallPageUseCase
import dev.errnolink.tsuzuki.reader.engine.model.ReaderChapter
import dev.errnolink.tsuzuki.reader.engine.model.ReaderUiItem
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage as KomikkuReaderPage
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter

@OptIn(ExperimentalCoroutinesApi::class)
class KomikkuPageLoaderAdapterTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val memoryWarmManager = mockk<MemoryCacheWarmManager>(relaxed = true)
    private val checkTallPage = mockk<CheckTallPageUseCase>(relaxed = true)
    private val loader = RecordingLoader()

    private lateinit var engineChapter: ReaderChapter

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        engineChapter =
            ReaderChapter(Chapter.create().copy(id = 7L, url = "/chapter/7", name = "Chapter 7"))
        every { memoryWarmManager.warmMemoryCache(any(), any(), any(), any(), any()) } answers
            {
                val onSuccess = arg<(() -> Unit)?>(3)
                onSuccess?.invoke()
            }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Komikku loader stand-in: auto-ready pages behave like a downloaded chapter, the rest hang. */
    private class RecordingLoader : eu.kanade.tachiyomi.ui.reader.loader.PageLoader() {
        override var isLocal: Boolean = false

        val loaded = CopyOnWriteArrayList<Int>()
        val cancelled = CopyOnWriteArrayList<Int>()
        val autoReady = mutableSetOf<Int>()

        var pages: List<KomikkuReaderPage> = emptyList()

        override suspend fun getPages(): List<KomikkuReaderPage> = pages

        override suspend fun loadPage(page: KomikkuReaderPage) {
            loaded.add(page.index)
            if (page.index in autoReady) {
                page.stream = { ByteArrayInputStream(byteArrayOf(page.index.toByte())) }
                page.status = Page.State.Ready
                return
            }
            suspendCancellableCoroutine<Nothing> { continuation ->
                continuation.invokeOnCancellation { cancelled.add(page.index) }
            }
        }
    }

    private fun buildAdapter(pageCount: Int, readyAtConstruction: Boolean = false): KomikkuPageLoaderAdapter {
        val komikkuPages =
            (0 until pageCount).map { index ->
                KomikkuReaderPage(index, "url_$index", "img_$index").apply {
                    if (readyAtConstruction) {
                        stream = { ByteArrayInputStream(byteArrayOf(index.toByte())) }
                        status = Page.State.Ready
                    }
                }
            }
        loader.pages = komikkuPages
        if (readyAtConstruction) {
            loader.autoReady.addAll(0 until pageCount)
        }
        return KomikkuPageLoaderAdapter(
            loader = loader,
            komikkuPages = komikkuPages,
            engineChapter = engineChapter,
            mirrorDispatcher = testDispatcher,
        )
    }

    private fun controllerFor(adapter: KomikkuPageLoaderAdapter): ReaderPreloadControllerImpl =
        ReaderPreloadControllerImpl(
            context = mockk(relaxed = true),
            scope = testScope,
            memoryCacheWarmManager = memoryWarmManager,
            checkTallPage = checkTallPage,
            getScreenHeight = { 2000 },
            ioDispatcher = testDispatcher,
        ).also { engineChapter.pageLoader = adapter }

    @Test
    fun `page that becomes Ready through the komikku loader reaches DiskReady in the engine state`() =
        testScope.runTest {
            val adapter = buildAdapter(pageCount = 5)
            loader.autoReady.addAll(0 until 5)
            val controller = controllerFor(adapter)
            val items = adapter.pages.map { ReaderUiItem.Page(it) }

            controller.onPositionChanged(
                currentIndex = 0,
                items = items,
                preloadAmount = 4,
                isRtl = false,
                isWebtoon = false,
            )
            advanceTimeBy(ReaderPreloadControllerImpl.DEBOUNCE_DELAY_MS + 10L)
            runCurrent()

            assertTrue(loader.loaded.contains(0))

            val key = controller.itemDomainKey(items[0])
            val status = controller.state.value.pageStatuses[key]
            assertTrue(
                status is PreloadPageStatus.MemoryReady,
                "Expected the page warmed through the komikku loader but got $status",
            )
            assertEquals(Page.State.Ready, adapter.pages[0].status)
            assertNotNull(adapter.pages[0].stream)
        }

    @Test
    fun `window move cancels out-of-window loads through the komikku loader`() = testScope.runTest {
        val adapter = buildAdapter(pageCount = 8)
        val controller = controllerFor(adapter)
        val items = adapter.pages.map { ReaderUiItem.Page(it) }

        controller.onPositionChanged(
            currentIndex = 0,
            items = items,
            preloadAmount = 4,
            isRtl = false,
            isWebtoon = false,
        )
        advanceTimeBy(ReaderPreloadControllerImpl.DEBOUNCE_DELAY_MS + 10L)
        runCurrent()

        // The two tier-1 permits started the first window's leading pages
        assertTrue(loader.loaded.contains(0))
        assertTrue(loader.loaded.contains(1))

        controller.onPositionChanged(
            currentIndex = 7,
            items = items,
            preloadAmount = 4,
            isRtl = false,
            isWebtoon = false,
        )
        advanceTimeBy(ReaderPreloadControllerImpl.DEBOUNCE_DELAY_MS + 10L)
        runCurrent()

        // Pages 0..4 fell out of the disk window; their komikku loads were cancelled
        assertTrue(loader.cancelled.contains(0))
        assertTrue(loader.cancelled.contains(1))
    }

    @Test
    fun `pages already ready at construction are served without calling the komikku loader`() =
        testScope.runTest {
            val adapter = buildAdapter(pageCount = 5, readyAtConstruction = true)
            val controller = controllerFor(adapter)
            val items = adapter.pages.map { ReaderUiItem.Page(it) }

            controller.onPositionChanged(
                currentIndex = 0,
                items = items,
                preloadAmount = 4,
                isRtl = false,
                isWebtoon = false,
            )
            advanceTimeBy(ReaderPreloadControllerImpl.DEBOUNCE_DELAY_MS + 10L)
            runCurrent()

            assertTrue(loader.loaded.isEmpty())

            val key = controller.itemDomainKey(items[0])
            val status = controller.state.value.pageStatuses[key]
            assertTrue(
                status is PreloadPageStatus.MemoryReady,
                "Expected the preloaded page warmed from disk state but got $status",
            )
        }

    @Test
    fun `recycle stops the mirror and recycles the wrapped komikku loader`() = testScope.runTest {
        val adapter = buildAdapter(pageCount = 3)
        controllerFor(adapter)

        adapter.recycle()

        assertTrue(adapter.isRecycled)
        assertTrue(loader.isRecycled)
    }
}
