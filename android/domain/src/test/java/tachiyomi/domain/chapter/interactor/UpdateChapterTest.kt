package tachiyomi.domain.chapter.interactor

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.chapter.service.ChapterReadSync

class UpdateChapterTest {

    private val stored = mutableMapOf<Long, Chapter>()
    private val repository = mockk<ChapterRepository>(relaxed = true).also { repo ->
        coEvery { repo.getChapterById(any()) } answers { stored[firstArg()] }
    }
    private val sent = mutableListOf<ChapterUpdate>()
    private val readSync = object : ChapterReadSync {
        override fun onReadStatusChanged(chapters: List<ChapterUpdate>) {
            sent += chapters
        }
    }
    private val updateChapter = UpdateChapter(repository, readSync)

    private fun chapter(id: Long, read: Boolean) {
        stored[id] = Chapter.create().copy(id = id, read = read)
    }

    @Test
    fun `turning pages of an unread chapter never tells MangaDex it is unread`() = runBlocking {
        chapter(1, read = false)
        repeat(20) { page -> updateChapter.await(ChapterUpdate(id = 1, read = false, lastPageRead = page.toLong())) }
        sent.none { it.read != null } shouldBe true
    }

    @Test
    fun `finishing a chapter tells MangaDex once that it is read`() = runBlocking {
        chapter(1, read = false)
        updateChapter.await(ChapterUpdate(id = 1, read = true, lastPageRead = 30))
        sent.map { it.read } shouldBe listOf(true)
    }

    @Test
    fun `a library refresh that rewrites unchanged chapters sends no read changes`() = runBlocking {
        chapter(1, read = false)
        chapter(2, read = true)
        updateChapter.awaitAll(
            listOf(
                ChapterUpdate(id = 1, read = false, lastPageRead = 0, sourceOrder = 5),
                ChapterUpdate(id = 2, read = true, lastPageRead = 12, sourceOrder = 6),
            ),
        )
        sent.none { it.read != null } shouldBe true
    }

    @Test
    fun `marking a read chapter unread is still sent`() = runBlocking {
        chapter(3, read = true)
        updateChapter.awaitAll(listOf(ChapterUpdate(id = 3, read = false)))
        sent.map { it.id to it.read } shouldBe listOf(3L to false)
    }
}
