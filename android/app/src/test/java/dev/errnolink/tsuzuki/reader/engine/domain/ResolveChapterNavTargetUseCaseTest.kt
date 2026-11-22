// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): app/src/test/java/eu/kanade/tachiyomi/ui/reader/domain/ResolveChapterNavTargetUseCaseTest.kt
package dev.errnolink.tsuzuki.reader.engine.domain

import dev.errnolink.tsuzuki.reader.engine.model.ChapterNavTarget
import dev.errnolink.tsuzuki.reader.engine.model.ReaderChapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter

class ResolveChapterNavTargetUseCaseTest {

    private val useCase = ResolveChapterNavTargetUseCase()

    private fun createReaderChapter(id: Long, chapterNumber: Double = id.toDouble()): ReaderChapter {
        val chapter =
            Chapter.create().copy(
                id = id,
                url = "/chapter/$id",
                name = "Chapter $id",
                chapterNumber = chapterNumber,
            )
        return ReaderChapter(chapter)
    }

    private val ch1 = createReaderChapter(1L)
    private val ch2 = createReaderChapter(2L)
    private val ch3 = createReaderChapter(3L)
    private val chapters = listOf(ch1, ch2, ch3)

    @Test
    fun `when scrolling forward to next chapter, target is Start (page 0)`() {
        val target = useCase(currentChapter = ch1, selectedChapter = ch2, chapterList = chapters)
        assertEquals(ChapterNavTarget.Start, target)
    }

    @Test
    fun `when scrolling backward to previous chapter, target is End (last page)`() {
        val target = useCase(currentChapter = ch2, selectedChapter = ch1, chapterList = chapters)
        assertEquals(ChapterNavTarget.End, target)
    }

    @Test
    fun `when opening chapter initially with null currentChapter, target is Resume (saved progress)`() {
        val target = useCase(currentChapter = null, selectedChapter = ch2, chapterList = chapters)
        assertEquals(ChapterNavTarget.Resume, target)
    }

    @Test
    fun `when re-selecting current chapter, target is Resume`() {
        val target = useCase(currentChapter = ch2, selectedChapter = ch2, chapterList = chapters)
        assertEquals(ChapterNavTarget.Resume, target)
    }

    @Test
    fun `when nextChapter matches selectedChapter, target is Start`() {
        val target =
            useCase(
                currentChapter = ch1,
                selectedChapter = ch2,
                chapterList = chapters,
                nextChapter = ch2,
                prevChapter = null,
            )
        assertEquals(ChapterNavTarget.Start, target)
    }

    @Test
    fun `when prevChapter matches selectedChapter, target is End`() {
        val target =
            useCase(
                currentChapter = ch2,
                selectedChapter = ch1,
                chapterList = chapters,
                nextChapter = ch3,
                prevChapter = ch1,
            )
        assertEquals(ChapterNavTarget.End, target)
    }

    @Test
    fun `when chapters not found in list, falls back to chapter_number comparison`() {
        val chA = createReaderChapter(100L, chapterNumber = 10.0)
        val chB = createReaderChapter(101L, chapterNumber = 11.0)
        val targetForward =
            useCase(currentChapter = chA, selectedChapter = chB, chapterList = emptyList())
        assertEquals(ChapterNavTarget.Start, targetForward)

        val targetBackward =
            useCase(currentChapter = chB, selectedChapter = chA, chapterList = emptyList())
        assertEquals(ChapterNavTarget.End, targetBackward)
    }
}
