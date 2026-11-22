package eu.kanade.tachiyomi.data.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkerParameters
import eu.kanade.tachiyomi.util.system.NetworkState
import eu.kanade.tachiyomi.util.system.activeNetworkState
import eu.kanade.tachiyomi.util.system.networkStateFlow
import eu.kanade.tachiyomi.util.system.setForegroundSafely
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.download.service.DownloadPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektScope
import uy.kohesive.injekt.api.addSingleton
import uy.kohesive.injekt.registry.default.DefaultRegistrar
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class DownloadJobTest {

    private val online = NetworkState(isConnected = true, isValidated = true, isWifi = true)
    private val offline =
        NetworkState(isConnected = false, isValidated = false, isWifi = false)

    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var downloadManager: DownloadManager
    private lateinit var downloadPreferences: DownloadPreferences
    private val networkState = MutableStateFlow(online)

    @Volatile private var downloaderRunning = false

    @BeforeEach
    fun setup() {
        Injekt = InjektScope(DefaultRegistrar())
        context = mockk(relaxed = true)
        workerParams = mockk(relaxed = true)

        downloadManager = mockk {
            every { downloaderStart() } answers
                {
                    downloaderRunning = true
                    true
                }
            every { downloaderStop(any()) } answers { downloaderRunning = false }
            every { pauseDownloads() } answers { downloaderRunning = false }
            every { isRunning } answers { downloaderRunning }
        }
        Injekt.addSingleton(downloadManager)
        downloadPreferences = mockk {
            val wifiOnly = mockk<Preference<Boolean>> {
                every { get() } returns false
                every { changes() } returns flowOf(false)
            }
            every { downloadOnlyOverWifi() } returns wifiOnly
        }
        Injekt.addSingleton(downloadPreferences)

        mockkStatic("eu.kanade.tachiyomi.util.system.NetworkStateTrackerKt")
        every { any<Context>().activeNetworkState() } answers { networkState.value }
        every { any<Context>().networkStateFlow() } returns networkState
        mockkStatic("eu.kanade.tachiyomi.util.system.WorkManagerExtensionsKt")
        coEvery { any<CoroutineWorker>().setForegroundSafely() } just runs
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        Injekt = InjektScope(DefaultRegistrar())
    }

    @Test
    fun `given network lost while downloading when doWork then worker finishes`() = runTest {
        launch {
            delay(5.seconds)
            networkState.value = offline
        }

        val result = withTimeoutOrNull(1.minutes) { DownloadJob(context, workerParams).doWork() }

        assertEquals(ListenableWorker.Result.success(), result)
        verify(exactly = 1) { downloadManager.downloaderStop(any()) }
    }

    @Test
    fun `given downloads finish when doWork then worker finishes and stops watching network`() =
        runTest {
            launch {
                delay(5.seconds)
                downloaderRunning = false
            }

            val result =
                withTimeoutOrNull(1.minutes) { DownloadJob(context, workerParams).doWork() }
            networkState.value = offline
            advanceUntilIdle()

            assertEquals(ListenableWorker.Result.success(), result)
            verify(exactly = 0) { downloadManager.downloaderStop(any()) }
        }

    @Test
    fun `given system stops the worker when doWork then downloader is paused`() = runTest {
        val worker = DownloadJob(context, workerParams)
        val work = async { worker.doWork() }
        delay(5.seconds)

        worker.stop(WorkInfo.STOP_REASON_QUOTA)
        work.cancel()
        advanceUntilIdle()

        verify(exactly = 1) { downloadManager.pauseDownloads() }
        assertEquals(false, downloaderRunning)
    }

    @Test
    fun `given system stops the worker below API 31 when doWork then downloader is paused`() =
        runTest {
            val worker = DownloadJob(context, workerParams)
            val work = async { worker.doWork() }
            delay(5.seconds)

            worker.stop(WorkInfo.STOP_REASON_UNKNOWN)
            work.cancel()
            advanceUntilIdle()

            verify(exactly = 1) { downloadManager.pauseDownloads() }
            assertEquals(false, downloaderRunning)
        }

    @Test
    fun `given network watcher fails when doWork then downloader is paused`() = runTest {
        every { any<Context>().networkStateFlow() } returns
            flow { throw IllegalStateException("too many network callbacks") }

        val result = runCatching { DownloadJob(context, workerParams).doWork() }

        assertTrue(result.exceptionOrNull() is IllegalStateException)
        verify(exactly = 1) { downloadManager.pauseDownloads() }
        assertEquals(false, downloaderRunning)
    }

    @Test
    fun `given network turns metered with wifi only on when doWork then worker finishes`() =
        runTest {
            val wifiOnly = mockk<Preference<Boolean>> {
                every { get() } returns true
                every { changes() } returns flowOf(true)
            }
            every { downloadPreferences.downloadOnlyOverWifi() } returns wifiOnly
            launch {
                delay(5.seconds)
                networkState.value = online.copy(isWifi = false)
            }

            val result =
                withTimeoutOrNull(1.minutes) { DownloadJob(context, workerParams).doWork() }

            assertEquals(ListenableWorker.Result.success(), result)
            verify(exactly = 1) { downloadManager.downloaderStop(any()) }
        }

    @Test
    fun `given nothing to download when doWork then worker succeeds`() = runTest {
        every { downloadManager.downloaderStart() } returns false

        val result = DownloadJob(context, workerParams).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        verify(exactly = 0) { downloadManager.downloaderStop(any()) }
    }

    @Test
    fun `given network offline when doWork then worker fails`() = runTest {
        networkState.value = offline

        val result = DownloadJob(context, workerParams).doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        verify(exactly = 0) { downloadManager.downloaderStart() }
    }

    @Test
    fun `given worker is replaced when doWork then downloader keeps running`() = runTest {
        val worker = DownloadJob(context, workerParams)
        val work = async { worker.doWork() }
        delay(5.seconds)

        worker.stop(WorkInfo.STOP_REASON_CANCELLED_BY_APP)
        work.cancel()
        advanceUntilIdle()

        verify(exactly = 0) { downloadManager.pauseDownloads() }
        assertEquals(true, downloaderRunning)
    }

    @Test
    fun `given downloader already running when doWork then worker stays until downloads finish`() =
        runTest {
            downloaderRunning = true
            every { downloadManager.downloaderStart() } returns false
            launch {
                delay(5.seconds)
                downloaderRunning = false
            }

            val result =
                withTimeoutOrNull(1.minutes) { DownloadJob(context, workerParams).doWork() }

            assertEquals(ListenableWorker.Result.success(), result)
            assertTrue(currentTime >= 5_000L)
        }
}
