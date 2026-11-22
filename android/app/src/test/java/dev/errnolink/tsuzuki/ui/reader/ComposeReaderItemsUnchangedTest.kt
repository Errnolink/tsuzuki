package dev.errnolink.tsuzuki.ui.reader

import dev.errnolink.tsuzuki.reader.engine.model.ChapterTransition
import dev.errnolink.tsuzuki.reader.engine.model.ReaderChapter
import dev.errnolink.tsuzuki.reader.engine.model.ReaderUiItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter

/**
 * The viewers key their anchor and effect bookkeeping on the item list's identity, so an
 * event-driven rebuild that produced equivalent content must keep the served instance; this is
 * the guard against the recomposition churn that spins the beta reader's recomposer.
 */
class ComposeReaderItemsUnchangedTest {

    private fun chapter(id: Long, url: String): ReaderChapter {
        return ReaderChapter(Chapter.create().copy(id = id, url = url, name = "Chapter $id"))
    }

    private fun page(chapter: ReaderChapter, index: Int): dev.errnolink.tsuzuki.reader.engine.model.ReaderPage {
        return dev.errnolink.tsuzuki.reader.engine.model.ReaderPage(index = index, url = "${chapter.chapter.url}/$index")
            .apply { this.chapter = chapter }
    }

    private fun items(chapter: ReaderChapter, pageCount: Int): List<ReaderUiItem> {
        return buildList {
            add(ReaderUiItem.Transition(ChapterTransition.Prev(chapter, null)))
            repeat(pageCount) { index -> add(ReaderUiItem.Page(page(chapter, index))) }
            add(ReaderUiItem.Transition(ChapterTransition.Next(chapter, null)))
        }
    }

    @Test
    fun `a rebuilt equivalent list keeps the current instance`() {
        val chapter = chapter(2L, "/2")
        val current = items(chapter, 4)
        val rebuilt = items(chapter, 4)

        assertTrue(composeReaderItemsUnchanged(current, rebuilt))
        assertTrue(composeReaderItemsUnchanged(current, current))
    }

    @Test
    fun `a content change rebuilds with the new instance`() {
        val chapter = chapter(2L, "/2")
        val current = items(chapter, 4)
        val grown = items(chapter, 5)
        val nextChapter = chapter(3L, "/3")
        val swapped = listOf(
            ReaderUiItem.Transition(ChapterTransition.Prev(nextChapter, null)),
            ReaderUiItem.Page(page(nextChapter, 0)),
        )

        assertFalse(composeReaderItemsUnchanged(current, grown))
        assertFalse(composeReaderItemsUnchanged(current, swapped))
        assertFalse(composeReaderItemsUnchanged(null, current))
        assertFalse(composeReaderItemsUnchanged(emptyList(), current))
    }

    @Test
    fun `identity agreement short-circuits before equivalence`() {
        val chapter = chapter(2L, "/2")
        val current = items(chapter, 4)
        assertEquals(current, current)
        assertSame(current, current)
        assertTrue(composeReaderItemsUnchanged(current, current))
    }
}
