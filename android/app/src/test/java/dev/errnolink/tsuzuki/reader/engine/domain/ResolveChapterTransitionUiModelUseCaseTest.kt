// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): app/src/test/java/eu/kanade/tachiyomi/ui/reader/domain/ResolveChapterTransitionUiModelUseCaseTest.kt
package dev.errnolink.tsuzuki.reader.engine.domain

import dev.errnolink.tsuzuki.reader.engine.model.ChapterTransition
import dev.errnolink.tsuzuki.reader.engine.model.ChapterTransitionUiModel
import dev.errnolink.tsuzuki.reader.engine.model.ReaderChapter
import eu.kanade.tachiyomi.data.download.DownloadManager
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

class ResolveChapterTransitionUiModelUseCaseTest {

    private val downloadManager = mockk<DownloadManager>()
    private val useCase = ResolveChapterTransitionUiModelUseCase(downloadManager)

    private fun createChapter(id: Long, chapterNumber: Double = id.toDouble()): ReaderChapter {
        val dbChapter =
            Chapter.create().copy(
                id = id,
                url = "/chapter/$id",
                name = "Chapter $id",
                chapterNumber = chapterNumber,
            )
        return ReaderChapter(dbChapter)
    }

    private fun createManga(id: Long = 1L): Manga {
        return Manga.create().copy(
            id = id,
            url = "/manga/$id",
            ogTitle = "Test Manga",
        )
    }

    @Test
    fun `resolve prev transition with missing chapters calculates gap and download statuses`() {
        val prevChapter = createChapter(1L, chapterNumber = 1.0)
        val currChapter = createChapter(5L, chapterNumber = 5.0)
        val transition = ChapterTransition.Prev(from = currChapter, to = prevChapter)
        val manga = createManga()

        every {
            downloadManager.isChapterDownloaded(eq(currChapter.chapter.name), any(), any(), any(), any())
        } returns true
        every {
            downloadManager.isChapterDownloaded(eq(prevChapter.chapter.name), any(), any(), any(), any())
        } returns false

        val uiModel = useCase(transition, manga)

        assertTrue(uiModel is ChapterTransitionUiModel.Prev)
        val prevModel = uiModel as ChapterTransitionUiModel.Prev
        assertEquals("Chapter 5", prevModel.fromChapterName)
        assertTrue(prevModel.isFromDownloaded)
        assertNotNull(prevModel.toChapter)
        assertEquals("Chapter 1", prevModel.toChapter?.name)
        assertFalse(prevModel.toChapter?.isDownloaded ?: true)
        assertEquals(3, prevModel.missingChaptersCount)
    }

    @Test
    fun `resolve next transition when next chapter is null`() {
        val currChapter = createChapter(10L, chapterNumber = 10.0)
        val transition = ChapterTransition.Next(from = currChapter, to = null)

        every { downloadManager.isChapterDownloaded(any(), any(), any(), any(), any()) } returns
            false

        val uiModel = useCase(transition, null)

        assertTrue(uiModel is ChapterTransitionUiModel.Next)
        val nextModel = uiModel as ChapterTransitionUiModel.Next
        assertEquals("Chapter 10", nextModel.fromChapterName)
        assertFalse(nextModel.isFromDownloaded)
        assertNull(nextModel.toChapter)
        assertEquals(0, nextModel.missingChaptersCount)
    }

    @Test
    fun `resolve next transition with adjacent chapter has zero missing chapters`() {
        val currChapter = createChapter(1L, chapterNumber = 1.0)
        val nextChapter = createChapter(2L, chapterNumber = 2.0)
        val transition = ChapterTransition.Next(from = currChapter, to = nextChapter)
        val manga = createManga()

        every {
            downloadManager.isChapterDownloaded(eq(currChapter.chapter.name), any(), any(), any(), any())
        } returns true
        every {
            downloadManager.isChapterDownloaded(eq(nextChapter.chapter.name), any(), any(), any(), any())
        } returns true

        val uiModel = useCase(transition, manga)

        assertTrue(uiModel is ChapterTransitionUiModel.Next)
        val nextModel = uiModel as ChapterTransitionUiModel.Next
        assertEquals("Chapter 1", nextModel.fromChapterName)
        assertTrue(nextModel.isFromDownloaded)
        assertNotNull(nextModel.toChapter)
        assertEquals("Chapter 2", nextModel.toChapter?.name)
        assertTrue(nextModel.toChapter?.isDownloaded ?: false)
        assertEquals(0, nextModel.missingChaptersCount)
        assertEquals(ChapterTransitionUiModel.PreloadState.Ready, nextModel.toChapter?.preloadState)
    }

    @Test
    fun `resolve transition reflects chapter error preload state`() {
        val currChapter = createChapter(1L, chapterNumber = 1.0)
        val nextChapter =
            createChapter(2L, chapterNumber = 2.0).apply {
                state = ReaderChapter.State.Error(Exception("Network timeout"))
            }
        val transition = ChapterTransition.Next(from = currChapter, to = nextChapter)

        every { downloadManager.isChapterDownloaded(any(), any(), any(), any(), any()) } returns
            false

        val uiModel = useCase(transition, null)

        val nextModel = uiModel as ChapterTransitionUiModel.Next
        val target = nextModel.toChapter
        assertNotNull(target)
        assertTrue(target?.preloadState is ChapterTransitionUiModel.PreloadState.Error)
        assertEquals(
            "Network timeout",
            (target?.preloadState as ChapterTransitionUiModel.PreloadState.Error).message,
        )
    }
}
