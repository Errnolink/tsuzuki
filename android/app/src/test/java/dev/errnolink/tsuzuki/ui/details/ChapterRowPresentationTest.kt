package dev.errnolink.tsuzuki.ui.details

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.ui.manga.track.TrackItem
import tachiyomi.domain.track.model.Track

class ChapterRowPresentationTest {

    private val strings = ChapterRowStrings(
        pageProgressPattern = "p. %1\$d",
        chapterNumberPattern = "Chapter %1\$s",
        chapterShortPattern = "Ch. %1\$s",
        unavailable = "Unavailable",
        markAsRead = "Mark as read",
        markAsUnread = "Mark as unread",
        bookmark = "Bookmark",
        unbookmark = "Unbookmark",
        cancelDownload = "Cancel download",
        deleteDownload = "Delete download",
        download = "Download",
    )

    @Test
    fun `in-progress row shows kasane page label`() {
        val progress = chapterRowProgress(lastPageRead = 6, pageTotal = 19, strings)
        assertEquals("p. 7", progress?.label)
    }

    @Test
    fun `progress without a known total still shows the page label`() {
        val progress = chapterRowProgress(lastPageRead = 4, pageTotal = null, strings)
        assertEquals("p. 5", progress?.label)
    }

    @Test
    fun `untouched and read rows have no progress`() {
        assertNull(chapterRowProgress(lastPageRead = null, pageTotal = 19, strings))
        assertNull(chapterRowProgress(lastPageRead = 0, pageTotal = 19, strings))
    }

    @Test
    fun `translated page pattern flows into the label`() {
        val progress = chapterRowProgress(lastPageRead = 6, pageTotal = 19, strings.copy(pageProgressPattern = "S. %1\$d"))
        assertEquals("S. 7", progress?.label)
    }

    @Test
    fun `meta line joins scanlator date and progress with separators`() {
        val line = chapterRowMeta(
            scanlator = "Kirei Cake",
            date = "8 Oct",
            progress = chapterRowProgress(6, 19, strings),
            unavailable = false,
            unavailableLabel = strings.unavailable,
        )
        assertEquals("Kirei Cake · 8 Oct · p. 7", line)
    }

    @Test
    fun `meta line omits blank and missing facts`() {
        val line = chapterRowMeta(
            scanlator = " ",
            date = "Today",
            progress = null,
            unavailable = false,
            unavailableLabel = strings.unavailable,
        )
        assertEquals("Today", line)
    }

    @Test
    fun `unavailable replaces progress in the meta line`() {
        val line = chapterRowMeta(
            scanlator = null,
            date = null,
            progress = chapterRowProgress(6, 19, strings),
            unavailable = true,
            unavailableLabel = strings.unavailable,
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
        assertEquals(
            "Ch. 12 / 248",
            trackingPanelChapterLabel(
                track(1, 1, lastChapterRead = 12.0, totalChapters = 248),
                chapterShortPattern = "Ch. %1\$s",
                chapterOfTotalPattern = "Ch. %1\$d / %2\$d",
            ),
        )
        assertEquals(
            "Ch. 7",
            trackingPanelChapterLabel(
                track(1, 1, lastChapterRead = 7.0),
                chapterShortPattern = "Ch. %1\$s",
                chapterOfTotalPattern = "Ch. %1\$d / %2\$d",
            ),
        )
        assertNull(
            trackingPanelChapterLabel(
                track(1, 1),
                chapterShortPattern = "Ch. %1\$s",
                chapterOfTotalPattern = "Ch. %1\$d / %2\$d",
            ),
        )
    }
}

/** Minimal tracker stand-in: row logic only reads identity. */
private fun fakeTracker(id: Long): Tracker = java.lang.reflect.Proxy.newProxyInstance(
    Tracker::class.java.classLoader,
    arrayOf(Tracker::class.java),
) { _, method, _ -> if (method.name == "getId") id else error("not used") } as Tracker
