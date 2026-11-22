package dev.errnolink.tsuzuki.ui.downloads

import eu.kanade.tachiyomi.data.download.model.Download
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DownloadRowStateTest {

    @Test
    fun `queued row waits while the downloader runs`() {
        val view = downloadRowState(Download.State.QUEUE, running = true, progressPercent = 0, downloadedPages = 0, totalPages = null, chapterName = "Ch. 41")
        assertEquals("Ch. 41 · waiting", view.subtitle)
        assertNull(view.progress)
        assertFalse(view.indeterminate)
        assertFalse(view.failed)
    }

    @Test
    fun `queued row pauses when the downloader is stopped`() {
        val view = downloadRowState(Download.State.QUEUE, running = false, progressPercent = 0, downloadedPages = 0, totalPages = null, chapterName = "Ch. 41")
        assertEquals("Ch. 41 · paused", view.subtitle)
        assertNull(view.progress)
        assertFalse(view.indeterminate)
    }

    @Test
    fun `running row with a known total shows the page count and fraction`() {
        val view = downloadRowState(Download.State.DOWNLOADING, running = true, progressPercent = 63, downloadedPages = 14, totalPages = 22, chapterName = "Ch. 93")
        assertEquals("Ch. 93 · 14 of 22 pages", view.subtitle)
        assertEquals(0.63f, view.progress)
        assertFalse(view.indeterminate)
        assertFalse(view.failed)
    }

    @Test
    fun `running row without a known total is indeterminate`() {
        val view = downloadRowState(Download.State.DOWNLOADING, running = true, progressPercent = 0, downloadedPages = 0, totalPages = null, chapterName = "Ch. 7")
        assertEquals("Ch. 7 · downloading", view.subtitle)
        assertNull(view.progress)
        assertTrue(view.indeterminate)
    }

    @Test
    fun `failed row reports the failure`() {
        val view = downloadRowState(Download.State.ERROR, running = true, progressPercent = 10, downloadedPages = 2, totalPages = 22, chapterName = "Ch. 5")
        assertEquals("Ch. 5 · download failed · retry available", view.subtitle)
        assertNull(view.progress)
        assertFalse(view.indeterminate)
        assertTrue(view.failed)
    }

    @Test
    fun `done row reports the download`() {
        val view = downloadRowState(Download.State.DOWNLOADED, running = false, progressPercent = 100, downloadedPages = 22, totalPages = 22, chapterName = "Ch. 93")
        assertEquals("Ch. 93 · downloaded", view.subtitle)
        assertNull(view.progress)
        assertFalse(view.indeterminate)
        assertFalse(view.failed)
    }
}
