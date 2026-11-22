package eu.kanade.tachiyomi.data.download

import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.source.online.HttpSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class DownloadsFirstTest {

    private val queue = listOf(download(1), download(2), download(3))

    @Test
    fun `puts the downloads first and keeps the order of the rest`() {
        val result = queue.withDownloadsFirst(listOf(download(4)))

        assertEquals(listOf(4L, 1L, 2L, 3L), result.map { it.chapter.id })
    }

    @Test
    fun `a chapter that is already queued is moved, not queued twice`() {
        val result = queue.withDownloadsFirst(listOf(download(3)))

        assertEquals(listOf(3L, 1L, 2L), result.map { it.chapter.id })
    }

    @Test
    fun `a chapter that is already queued keeps its queued download`() {
        val result = queue.withDownloadsFirst(listOf(download(2)))

        assertSame(queue[1], result.first())
    }

    private fun download(chapterId: Long): Download = Download(
        source = mockk<HttpSource>(),
        manga = mockk(),
        chapter = mockk { every { id } returns chapterId },
    )
}
