package eu.kanade.tachiyomi.data.download

import android.content.Context
import android.content.SharedPreferences
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.source.online.HttpSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import eu.kanade.domain.source.service.SourcePreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektScope
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.registry.default.DefaultRegistrar
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DownloaderUpdateQueueTest {
    private lateinit var downloader: Downloader
    private lateinit var sourceManager: SourceManager
    private lateinit var provider: DownloadProvider

    @Volatile private var onStoreWrite: () -> Unit = {}

    private val httpSource = mockk<HttpSource>(relaxed = true)
    private val manga = Manga.create().copy(
        id = 1L,
        url = "/title/1",
        ogTitle = "Manga",
        source = 1L,
    )

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        Injekt = InjektScope(DefaultRegistrar())
        Injekt.addSingleton<Json>(Json)
        val sm = mockk<SourceManager>()
        every { sm.get(any()) } returns httpSource
        sourceManager = sm
        Injekt.addSingleton(sourceManager)
        Injekt.addSingleton(mockk<GetManga> { coEvery { await(any()) } returns null })
        Injekt.addSingleton(mockk<GetChapter> { coEvery { await(any()) } returns null })
        Injekt.addSingleton(mockk<ChapterCache>(relaxed = true))
        Injekt.addSingleton(mockk<DownloadPreferences>(relaxed = true))
        Injekt.addSingleton(mockk<GetCategories>(relaxed = true))
        Injekt.addSingleton(mockk<GetTracks>(relaxed = true))
        Injekt.addSingleton(mockk<SourcePreferences>(relaxed = true))

        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        val activeDownloads =
            mockk<SharedPreferences>(relaxed = true) {
                every { all } returns emptyMap<String, Any>()
                every { edit() } answers
                    {
                        onStoreWrite()
                        editor
                    }
            }
        val context =
            mockk<Context>(relaxed = true) {
                every { getSharedPreferences(any(), any()) } returns activeDownloads
            }

        mockkObject(DownloadJob.Companion)
        every { DownloadJob.start(any()) } just runs
        every { DownloadJob.stop(any()) } just runs
        mockkConstructor(DownloadNotifier::class)
        every { anyConstructed<DownloadNotifier>().onError(any(), any(), any(), any()) } just runs
        every { anyConstructed<DownloadNotifier>().onError(any(), any()) } just runs

        val providerMock =
            mockk<DownloadProvider> {
                every { findChapterDir(any(), any(), any(), any(), any()) } returns null
                coEvery { findDownloadedChapterIds(any(), any(), any()) } returns emptySet()
            }
        provider = providerMock
        downloader = Downloader(context, providerMock, mockk(relaxed = true), sourceManager)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
        Injekt = InjektScope(DefaultRegistrar())
    }

    @Test
    fun `looks up downloaded chapters once per queueing instead of probing each`() = runTest {
        coEvery { provider.findDownloadedChapterIds(any(), any(), any()) } answers {
            assertEquals(setOf(1L, 2L, 3L), firstArg<List<Chapter>>().mapTo(HashSet()) { it.id })
            setOf(1L)
        }
        every { provider.findChapterDir(any(), any(), any(), any(), any()) } answers {
            throw AssertionError("queueing must not probe chapters one by one")
        }

        downloader.queueChapters(manga, listOf(chapter(1), chapter(2), chapter(3)), autoStart = false)

        coVerify(exactly = 1) { provider.findDownloadedChapterIds(any(), any(), any()) }
        assertEquals(listOf(3L, 2L), downloader.queueState.value.map { it.chapter.id })
    }

    @Test
    fun `does not queue again a download that finishes during a reorder`() = runTest {
        downloader.queueChapters(manga, listOf(chapter(1), chapter(2)), autoStart = false)
        val finished = downloader.queueState.value.first { it.chapter.id == 1L }
        finished.status = Download.State.DOWNLOADED

        downloader.updateQueue { it.reversed() }

        assertEquals(listOf(2L), downloader.queueState.value.map { it.chapter.id })
        assertEquals(Download.State.DOWNLOADED, finished.status)
    }

    @Test
    fun `leaves the queue alone when the reorder returns null`() = runTest {
        downloader.queueChapters(manga, listOf(chapter(1)), autoStart = false)

        downloader.updateQueue { null }

        assertEquals(listOf(1L), downloader.queueState.value.map { it.chapter.id })
        assertEquals(Download.State.QUEUE, downloader.queueState.value.single().status)
    }

    @Test
    fun `keeps a chapter queued while a reorder waits for the queue`() = runTest {
        downloader.queueChapters(manga, listOf(chapter(1)), autoStart = false)

        val adding = CountDownLatch(1)
        val finishAdding = CountDownLatch(1)
        onStoreWrite = {
            adding.countDown()
            finishAdding.await(10, TimeUnit.SECONDS)
        }
        var error: Throwable? = null
        val queueing =
            Thread({
                try {
                    runBlocking { downloader.queueChapters(manga, listOf(chapter(2)), autoStart = false) }
                } catch (e: Throwable) {
                    error = e
                }
            }, QUEUEING_THREAD)
        queueing.start()
        check(adding.await(10, TimeUnit.SECONDS)) { "the chapter was not queued" }

        val reordering = Thread { downloader.updateQueue { it.reversed() } }
        reordering.start()
        waitUntil { !reordering.isAlive || reordering.isWaitingInUpdateQueue() }
        finishAdding.countDown()
        queueing.join(10_000)
        reordering.join(10_000)
        error?.let { throw it }

        assertEquals(listOf(2L, 1L), downloader.queueState.value.map { it.chapter.id })
    }

    private fun Thread.isWaitingInUpdateQueue(): Boolean =
        state == Thread.State.BLOCKED && stackTrace.firstOrNull()?.methodName == "updateQueue"

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "condition not met within 10 s" }
            Thread.sleep(10)
        }
    }

    private fun chapter(id: Long): Chapter = Chapter.create().copy(
        id = id,
        mangaId = manga.id!!,
        url = "/chapter/$id",
        name = "Ch.$id",
        sourceOrder = id - 1,
    )

    private companion object {
        const val QUEUEING_THREAD = "queueing"
    }
}
