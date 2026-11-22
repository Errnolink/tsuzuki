package dev.errnolink.tsuzuki.mangadex

import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.domain.track.interactor.SyncChapterProgressWithTrack
import eu.kanade.tachiyomi.data.database.models.Track
import eu.kanade.tachiyomi.data.track.RefreshResult
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.model.TrackMangaMetadata
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.domain.track.model.Track as DomainTrack
import tachiyomi.domain.track.repository.TrackRepository

class AutoTrackBindProgressTest {

    private fun chapter(number: Double, read: Boolean) = Chapter(
        id = number.toLong() + 100,
        mangaId = 1L,
        read = read,
        bookmark = false,
        lastPageRead = 0,
        dateFetch = 0,
        sourceOrder = number.toLong(),
        url = "c$number",
        name = "Chapter $number",
        dateUpload = 0,
        chapterNumber = number,
        scanlator = null,
        lastModifiedAt = 0,
        version = 0,
        memo = JsonObject(emptyMap()),
    )

    private fun track(lastChapterRead: Double) = TrackSearch().apply {
        manga_id = 1L
        tracker_id = 2L
        remote_id = 42L
        title = "Title"
        status = 1L
        score = 0.0
        last_chapter_read = lastChapterRead
        total_chapters = 0
        tracking_url = "https://tracker.test/42"
        started_reading_date = 1L
        finished_reading_date = 0
        private = false
    }

    private fun chapters(readUpTo: Double): List<Chapter> =
        (1..6).map { it.toDouble() }.map { number -> chapter(number, read = number <= readUpTo) }

    private fun addTracks(readChapters: List<Chapter>): AddTracks {
        val chapterRepository = mockk<ChapterRepository>()
        coEvery { chapterRepository.getChapterByMangaId(1L, false) } returns readChapters
        val getChapters = GetChaptersByMangaId(chapterRepository)
        val insertTrack = InsertTrack(mockk<TrackRepository>(relaxed = true))
        val syncProgress = SyncChapterProgressWithTrack(
            mockk(relaxed = true),
            insertTrack,
            getChapters,
        )
        return AddTracks(insertTrack, syncProgress, getChapters, mockk(relaxed = true))
    }

    @Test
    fun `bind pushes local reading progress up to the tracker`() = runTest {
        val tracker = RecordingTracker()
        addTracks(chapters(readUpTo = 5.0)).bind(tracker, track(lastChapterRead = 2.0), 1L)
        assertEquals(listOf(5), tracker.remoteChapterUpdates)
        assertEquals(emptyList<Double>(), tracker.updateCalls)
    }

    @Test
    fun `bind never lowers the remote chapter count`() = runTest {
        val tracker = RecordingTracker()
        addTracks(chapters(readUpTo = 3.0)).bind(tracker, track(lastChapterRead = 9.0), 1L)
        assertTrue(tracker.remoteChapterUpdates.isEmpty())
        assertTrue(tracker.updateCalls.isEmpty())
    }

    private class RecordingTracker : Tracker {
        val remoteChapterUpdates = mutableListOf<Int>()
        val updateCalls = mutableListOf<Double>()

        override val id = 2L
        override val name = "Recording"
        override val client = okhttp3.OkHttpClient()
        override val supportsReadingDates = false
        override val supportsPrivateTracking = false

        override fun getLogo() = 0

        override fun getStatusList() = emptyList<Long>()

        override fun getStatus(status: Long) = null

        override fun getReadingStatus() = 1L

        override fun getRereadingStatus() = 2L

        override fun getCompletionStatus() = 3L

        override fun getScoreList() = persistentListOf<String>()

        override fun get10PointScore(track: DomainTrack) = 0.0

        override fun indexToScore(index: Int) = 0.0

        override fun displayScore(track: DomainTrack) = ""

        override suspend fun update(track: Track, didReadChapter: Boolean): Track = track.also {
            updateCalls += it.last_chapter_read
        }

        override suspend fun bind(track: Track, hasReadChapters: Boolean): Track = track

        override suspend fun search(query: String) = emptyList<TrackSearch>()

        override suspend fun refresh(track: Track): Track = track

        override suspend fun login(username: String, password: String) = Unit

        override fun logout() = Unit

        override val isLoggedIn = true

        override val isLoggedInFlow = flowOf(false)

        override val isRefreshingFlow = MutableStateFlow(false)

        override val refreshResultFlow = MutableSharedFlow<RefreshResult>()

        override suspend fun refreshUser() = Unit

        override fun getUsername() = "user"

        override fun getDisplayUsername() = "user"

        override fun saveDisplayUsername(name: String) = Unit

        override fun getPassword() = "pass"

        override fun saveCredentials(username: String, password: String) = Unit

        override suspend fun register(item: Track, mangaId: Long) = Unit

        override suspend fun setRemoteStatus(track: Track, status: Long) = Unit

        override suspend fun setRemoteLastChapterRead(track: Track, chapterNumber: Int): Track = track.also {
            remoteChapterUpdates += chapterNumber
        }

        override suspend fun setRemoteScore(track: Track, scoreString: String) = Unit

        override suspend fun setRemoteStartDate(track: Track, epochMillis: Long) = Unit

        override suspend fun setRemoteFinishDate(track: Track, epochMillis: Long) = Unit

        override suspend fun setRemotePrivate(track: Track, private: Boolean) = Unit

        override suspend fun getMangaMetadata(track: DomainTrack): TrackMangaMetadata = TrackMangaMetadata()

        override suspend fun searchById(id: String): TrackSearch? = null

        override fun hasNotStartedReading(status: Long) = false
    }
}
