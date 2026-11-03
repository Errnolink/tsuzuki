package dev.errnolink.tsuzuki.ui.details

import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.ui.manga.track.TrackItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.domain.track.model.Track

class ChapterRowPresentationTest {
    @Test
    fun `in-progress row shows page count and bar fraction when total is known`() {
        val progress = chapterRowProgress(lastPageRead = 6, pageTotal = 19)
        assertEquals("Page 7 of 19", progress?.label)
        assertEquals(7f / 19f, progress?.fraction)
    }

    @Test
    fun `progress without a known total has text but no bar`() {
        val progress = chapterRowProgress(lastPageRead = 4, pageTotal = null)
        assertEquals("Page 5", progress?.label)
        assertNull(progress?.fraction)
    }

    @Test
    fun `bar fraction clamps at the last page`() {
        val progress = chapterRowProgress(lastPageRead = 30, pageTotal = 19)
        assertEquals(1f, progress?.fraction)
    }

    @Test
    fun `untouched and read rows have no progress`() {
        assertNull(chapterRowProgress(lastPageRead = null, pageTotal = 19))
        assertNull(chapterRowProgress(lastPageRead = 0, pageTotal = 19))
    }

    @Test
    fun `secondary line joins present facts with separators`() {
        val line = chapterRowSubtitle(
            sourceName = "MangaDex",
            unavailable = false,
            languageLabel = "EN",
            progress = chapterRowProgress(6, 19),
            date = "8 Oct",
            scanlator = "Kirei Cake",
        )
        assertEquals("MangaDex · EN · Page 7 of 19 · 8 Oct · Kirei Cake", line)
    }

    @Test
    fun `secondary line omits blank source and scanlator`() {
        val line = chapterRowSubtitle(
            sourceName = " ",
            unavailable = false,
            languageLabel = null,
            progress = null,
            date = "Today",
            scanlator = null,
        )
        assertEquals("Today", line)
    }

    @Test
    fun `unavailable replaces progress in the secondary line`() {
        val line = chapterRowSubtitle(
            sourceName = null,
            unavailable = true,
            languageLabel = null,
            progress = chapterRowProgress(6, 19),
            date = null,
            scanlator = null,
        )
        assertEquals("Unavailable", line)
    }
}

class TrackingPanelRowsTest {
    private fun track(
        id: Long,
        trackerId: Long,
        lastChapterRead: Double = 0.0,
        totalChapters: Long = 0,
        status: Long = 1,
    ) = Track(
        id = id,
        mangaId = 1L,
        trackerId = trackerId,
        remoteId = 0L,
        libraryId = null,
        title = "Title $id",
        lastChapterRead = lastChapterRead,
        totalChapters = totalChapters,
        status = status,
        score = 0.0,
        startDate = 0L,
        finishDate = 0L,
        private = false,
        remoteUrl = "",
    )

    private fun item(track: Track?, trackerId: Long = track?.trackerId ?: 99L) =
        TrackItem(track, fakeTracker(trackerId))

    @Test
    fun `panel lists only linked trackers with stable order`() {
        val rows = trackingPanelRows(
            listOf(
                item(null, trackerId = 3),
                item(track(10, trackerId = 1)),
                item(null, trackerId = 4),
                item(track(11, trackerId = 2)),
            ),
        )
        assertEquals(listOf(1L, 2L), rows.map { it.tracker.id })
    }

    @Test
    fun `chapter label reads progress against the tracked total`() {
        assertEquals("Ch. 12 / 248", trackingPanelChapterLabel(track(1, 1, lastChapterRead = 12.0, totalChapters = 248)))
        assertEquals("Ch. 7", trackingPanelChapterLabel(track(1, 1, lastChapterRead = 7.0)))
        assertNull(trackingPanelChapterLabel(track(1, 1)))
    }
}

/** Minimal tracker stand-in: row logic only reads identity. */
private fun fakeTracker(id: Long): Tracker = java.lang.reflect.Proxy.newProxyInstance(
    Tracker::class.java.classLoader,
    arrayOf(Tracker::class.java),
) { _, method, _ -> if (method.name == "getId") id else error("not used") } as Tracker
