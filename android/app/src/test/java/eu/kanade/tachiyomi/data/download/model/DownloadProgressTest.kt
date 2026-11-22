package eu.kanade.tachiyomi.data.download.model

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import io.mockk.mockk
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

class DownloadProgressTest {
    private val download = Download(mockk<HttpSource>(relaxed = true), Manga.create(), Chapter.create())

    @Test
    fun `progress follows page flows instead of polling for the page list`() = runTest {
        val emitted = mutableListOf<Int>()
        backgroundScope.launch {
            download.progressFlow.collect { emitted.add(it) }
        }
        runCurrent()
        assertEquals(emptyList<Int>(), emitted)
        advanceTimeBy(50)
        runCurrent()
        assertEquals(listOf(0), emitted)

        download.pages = listOf(
            Page(0, url = "1").apply { progress = 25 },
            Page(1, url = "2").apply { progress = 75 },
        )
        advanceTimeBy(50)
        runCurrent()
        assertEquals(listOf(0, 50), emitted)

        download.pages!!.first().progress = 100
        advanceTimeBy(50)
        runCurrent()
        assertEquals(listOf(0, 50, 87), emitted)
    }
}
