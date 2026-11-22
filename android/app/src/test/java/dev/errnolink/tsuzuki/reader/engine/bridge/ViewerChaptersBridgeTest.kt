package dev.errnolink.tsuzuki.reader.engine.bridge

import dev.errnolink.tsuzuki.reader.engine.model.ChapterTransition
import dev.errnolink.tsuzuki.reader.engine.model.ReaderChapter
import dev.errnolink.tsuzuki.reader.engine.model.ReaderUiItem
import dev.errnolink.tsuzuki.reader.engine.viewer.pager.ReaderPagerController
import eu.kanade.domain.chapter.model.toDbChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter as KomikkuReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage as KomikkuReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters as KomikkuViewerChapters
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter

class ViewerChaptersBridgeTest {

    /** Komikku loader stand-in: the bridge never loads through it, only wraps it. */
    private class StubLoader : eu.kanade.tachiyomi.ui.reader.loader.PageLoader() {
        override var isLocal: Boolean = false
        var recycled = false

        override suspend fun getPages(): List<KomikkuReaderPage> = emptyList()

        override suspend fun loadPage(page: KomikkuReaderPage) = Unit

        override fun recycle() {
            recycled = true
        }
    }

    private fun komikkuChapter(id: Long, url: String, pageCount: Int, loader: StubLoader): KomikkuReaderChapter {
        val chapter = KomikkuReaderChapter(
            Chapter.create().copy(id = id, url = url, name = "Chapter $id").toDbChapter(),
        )
        val pages = (0 until pageCount).map { index ->
            KomikkuReaderPage(index = index, url = "$url/page/$index").apply { this.chapter = chapter }
        }
        chapter.pageLoader = loader
        chapter.state = KomikkuReaderChapter.State.Loaded(pages)
        return chapter
    }

    private fun mappedViewerChapters(pageCounts: Triple<Int, Int, Int> = Triple(2, 6, 2)): Triple<ViewerChaptersBridge, KomikkuViewerChapters, StubLoader> {
        val loader = StubLoader()
        val prev = komikkuChapter(1L, "/1", pageCounts.first, loader)
        val curr = komikkuChapter(2L, "/2", pageCounts.second, loader)
        val next = komikkuChapter(3L, "/3", pageCounts.third, loader)
        val bridge = ViewerChaptersBridge()
        val engine = bridge.map(KomikkuViewerChapters(curr, prev, next))
        assertEquals(2L, engine.currChapter.chapter.id)
        assertEquals(1L, engine.prevChapter?.chapter?.id)
        assertEquals(3L, engine.nextChapter?.chapter?.id)
        return Triple(bridge, KomikkuViewerChapters(curr, prev, next), loader)
    }

    private fun pagerItems(bridge: ViewerChaptersBridge, isRtl: Boolean = false): List<ReaderUiItem> {
        return ReaderPagerController().buildItems(
            chapters = bridge.current,
            forceTransition = true,
            doublePages = false,
            splitPages = false,
            shiftDoublePage = false,
            isRtl = isRtl,
        )
    }

    @Test
    fun `komikku chapters map to engine pages in the same order with transitions`() {
        val (bridge, komikku, _) = mappedViewerChapters()
        val items = pagerItems(bridge)

        val pages = items.filterIsInstance<ReaderUiItem.Page>().map { it.page }
        // prev chapter's last 2 pages, current 6 in order, next chapter's first 2 pages
        assertEquals(listOf(1L, 1L), pages.take(2).map { it.chapter.chapter.id })
        assertEquals(listOf(0, 1), pages.take(2).map { it.index })
        assertEquals((0..5).toList(), pages.drop(2).take(6).map { it.index })
        assertEquals(listOf(2L, 2L, 2L, 2L, 2L, 2L), pages.drop(2).take(6).map { it.chapter.chapter.id })
        assertEquals(listOf(3L, 3L), pages.takeLast(2).map { it.chapter.chapter.id })
        assertEquals(listOf(0, 1), pages.takeLast(2).map { it.index })

        val transitions = items.filterIsInstance<ReaderUiItem.Transition>()
        assertEquals(2, transitions.size)
        assertTrue(transitions[0].transition is ChapterTransition.Prev)
        assertEquals(1L, transitions[0].transition.to?.chapter?.id)
        assertTrue(transitions[1].transition is ChapterTransition.Next)
        assertEquals(3L, transitions[1].transition.to?.chapter?.id)

        val komikkuCurrentPages = komikku.currChapter.pages!!
        val engineCurrPages = bridge.current.currChapter.pages!!
        assertEquals(komikkuCurrentPages.size, engineCurrPages.size)
        engineCurrPages.forEachIndexed { index, enginePage ->
            assertSame(komikkuCurrentPages[index], bridge.komikkuPageOf(enginePage))
            assertSame(komikkuCurrentPages[index].chapter, bridge.komikkuChapterOf(enginePage.chapter.chapter.id))
        }
    }

    @Test
    fun `selecting engine page k maps to komikku page k and back`() {
        val (bridge, komikku, _) = mappedViewerChapters(Triple(0, 5, 0))
        val komikkuPages = komikku.currChapter.pages!!
        val enginePages = bridge.current.currChapter.pages!!

        assertEquals(5, enginePages.size)
        komikkuPages.forEachIndexed { k, komikkuPage ->
            assertSame(enginePages[k], bridge.enginePageOf(komikkuPage))
            assertSame(komikkuPage, bridge.komikkuPageOf(enginePages[k]))
        }
        assertNull(bridge.komikkuPageOfChapter(999L, 0))
        assertNull(bridge.enginePageOf(KomikkuReaderPage(index = 42, url = "/unmapped")))
    }

    @Test
    fun `rtl item list reverses display order and keeps the engine to komikku page order`() {
        val (bridge, komikku, _) = mappedViewerChapters(Triple(0, 5, 0))
        val ltrPages = pagerItems(bridge, isRtl = false).filterIsInstance<ReaderUiItem.Page>().map { it.page }
        val rtlPages = pagerItems(bridge, isRtl = true).filterIsInstance<ReaderUiItem.Page>().map { it.page }

        // RTL flips the display direction by reversing the item list; the page identity does not move.
        assertEquals(ltrPages.reversed().map { it.index }, rtlPages.map { it.index })
        val komikkuPages = komikku.currChapter.pages!!
        rtlPages.forEach { enginePage ->
            assertSame(komikkuPages[enginePage.index], bridge.komikkuPageOf(enginePage))
        }
    }

    @Test
    fun `unloaded adjacent chapters mirror as wait chapters without a loader`() {
        val loader = StubLoader()
        val curr = komikkuChapter(2L, "/2", 4, loader)
        val unloaded = KomikkuReaderChapter(
            Chapter.create().copy(id = 3L, url = "/3", name = "Chapter 3").toDbChapter(),
        )
        val bridge = ViewerChaptersBridge()
        val engine = bridge.map(KomikkuViewerChapters(curr, null, unloaded))

        assertTrue(engine.currChapter.state is ReaderChapter.State.Loaded)
        assertEquals(ReaderChapter.State.Wait, engine.nextChapter?.state)
        assertNull(engine.nextChapter?.pageLoader)
        assertNull(bridge.komikkuPageOfChapter(3L, 0))
    }

    @Test
    fun `remapping the same chapters reuses mirrors and keeps page identity`() {
        val (bridge, komikku, _) = mappedViewerChapters(Triple(0, 5, 0))
        val firstPages = bridge.current.currChapter.pages!!

        bridge.map(komikku)

        assertSame(firstPages, bridge.current.currChapter.pages)
        komikku.currChapter.pages!!.forEachIndexed { k, komikkuPage ->
            assertSame(firstPages[k], bridge.enginePageOf(komikkuPage))
        }
    }

    @Test
    fun `a reloaded chapter gets a fresh mirror instead of a stale one`() {
        val (bridge, komikku, loader) = mappedViewerChapters(Triple(0, 5, 0))
        val stalePages = bridge.current.currChapter.pages!!
        val freshLoader = StubLoader()
        val freshPages = (0 until 5).map { KomikkuReaderPage(index = it, url = "/2/fresh/$it") }
        komikku.currChapter.pageLoader = freshLoader
        komikku.currChapter.state = KomikkuReaderChapter.State.Loaded(freshPages)

        bridge.map(komikku)

        assertFalse(stalePages === bridge.current.currChapter.pages)
        assertEquals((0 until 5).toList(), bridge.current.currChapter.pages!!.map { it.index })
        komikku.currChapter.pages!!.forEachIndexed { k, komikkuPage ->
            assertSame(komikkuPage, bridge.komikkuPageOf(bridge.current.currChapter.pages!![k]))
        }
        assertFalse(loader.recycled)
    }

    @Test
    fun `release stops mirrors without recycling the komikku loader`() {
        val (bridge, komikku, loader) = mappedViewerChapters(Triple(0, 5, 0))
        val enginePages = bridge.current.currChapter.pages!!.toList()

        bridge.release()

        assertFalse(loader.recycled)
        assertNull(bridge.komikkuPageOf(enginePages.first()))
        assertNull(bridge.enginePageOf(komikku.currChapter.pages!!.first()))
        assertNull(bridge.komikkuChapterOf(2L))
    }
}
