package dev.errnolink.tsuzuki.reader.engine.bridge

import dev.errnolink.tsuzuki.reader.engine.loader.KomikkuPageLoaderAdapter
import dev.errnolink.tsuzuki.reader.engine.model.ReaderChapter as EngineReaderChapter
import dev.errnolink.tsuzuki.reader.engine.model.ViewerChapters as EngineViewerChapters
import eu.kanade.tachiyomi.data.database.models.toDomainChapter
import eu.kanade.tachiyomi.ui.reader.loader.HttpPageLoader
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter as KomikkuReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage as KomikkuReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters as KomikkuViewerChapters
import java.util.IdentityHashMap

/**
 * Maps the running Komikku [KomikkuViewerChapters] (chapters loaded through the Komikku loaders)
 * onto the engine model so the Compose viewers can render them: every Komikku chapter gets an
 * engine mirror chapter whose pages stream through the C2 [KomikkuPageLoaderAdapter] while the
 * Komikku loaders keep doing the actual loading.
 *
 * The bridge keeps a two-way page map so a selection in the engine viewer maps back to the exact
 * Komikku [KomikkuReaderPage] (and vice versa), and a chapter map so preload/adjacent requests
 * resolve back to Komikku chapters. Mirrors are reused while the Komikku loader and page list of
 * the wrapped chapter stay the same, so adjacent chapters that become current keep their identity
 * across chapter swaps. Teardown only stops the mirrors ([KomikkuPageLoaderAdapter.dispose]):
 * recycling the wrapped Komikku loaders stays the Komikku reader's job.
 */
class ViewerChaptersBridge {

    /** The engine chapters of the last [map] call, keyed by the Komikku chapter instance. */
    private val engineChapters = IdentityHashMap<KomikkuReaderChapter, EngineReaderChapter>()

    /** The mirror each engine chapter was built with; null for chapters with no pages yet. */
    private val adaptersByEngine = IdentityHashMap<EngineReaderChapter, KomikkuPageLoaderAdapter?>()

    /** The Komikku loader/page-list snapshot a mirror was built from. */
    private val loaderByEngine = IdentityHashMap<EngineReaderChapter, eu.kanade.tachiyomi.ui.reader.loader.PageLoader?>()
    private val pagesListByEngine = IdentityHashMap<EngineReaderChapter, List<KomikkuReaderPage>?>()

    private val komikkuByEnginePage = HashMap<EngineReaderPageKey, KomikkuReaderPage>()
    private val engineByKomikkuPageMap = IdentityHashMap<KomikkuReaderPage, dev.errnolink.tsuzuki.reader.engine.model.ReaderPage>()
    private val komikkuChaptersById = HashMap<Long, KomikkuReaderChapter>()

    /** The mapping of the last [map] call. */
    lateinit var current: EngineViewerChapters
        private set

    fun map(source: KomikkuViewerChapters): EngineViewerChapters {
        val curr = engineChapterFor(source.currChapter)
        val prev = source.prevChapter?.let(::engineChapterFor)
        val next = source.nextChapter?.let(::engineChapterFor)
        current = EngineViewerChapters(curr, prev, next)
        val live = setOfNotNull(curr, prev, next)
        engineChapters.entries.retainAll { (_, engine) -> engine in live }
        prune(live)
        return current
    }

    /** The Komikku page a selection of the engine [page] corresponds to. */
    fun komikkuPageOf(page: dev.errnolink.tsuzuki.reader.engine.model.ReaderPage): KomikkuReaderPage? {
        return komikkuByEnginePage[EngineReaderPageKey(page.chapter.chapter.id, page.index)]
    }

    /** The engine page for Komikku [page]; [komikkuPageOf] is its inverse. */
    fun enginePageOf(page: KomikkuReaderPage): dev.errnolink.tsuzuki.reader.engine.model.ReaderPage? {
        return engineByKomikkuPageMap[page]
    }

    /** The Komikku page [pageIndex] of the chapter the engine [chapterId] mirrors. */
    fun komikkuPageOfChapter(chapterId: Long, pageIndex: Int): KomikkuReaderPage? {
        val enginePage = engineChapterById(chapterId)?.pages?.getOrNull(pageIndex) ?: return null
        return komikkuPageOf(enginePage)
    }

    fun engineChapterById(chapterId: Long): EngineReaderChapter? {
        return current.run {
            listOfNotNull(currChapter, prevChapter, nextChapter).firstOrNull { it.chapter.id == chapterId }
        }
    }

    /** The Komikku chapter the engine chapter with [chapterId] mirrors. */
    fun komikkuChapterOf(chapterId: Long): KomikkuReaderChapter? {
        return komikkuChaptersById[chapterId]
    }

    /** Stops every mirror; Komikku's loaders stay untouched. */
    fun release() {
        adaptersByEngine.values.filterNotNull().forEach { it.dispose() }
        engineChapters.clear()
        adaptersByEngine.clear()
        loaderByEngine.clear()
        pagesListByEngine.clear()
        komikkuByEnginePage.clear()
        engineByKomikkuPageMap.clear()
        komikkuChaptersById.clear()
    }

    private fun prune(live: Set<EngineReaderChapter>) {
        val dropped = adaptersByEngine.entries.filter { it.key !in live }
        dropped.forEach { (_, adapter) -> adapter?.dispose() }
        dropped.forEach { (engine, _) ->
            adaptersByEngine.remove(engine)
            loaderByEngine.remove(engine)
            pagesListByEngine.remove(engine)
        }
        komikkuChaptersById.entries.retainAll { (_, komikku) -> komikku in engineChapters.keys }
        komikkuByEnginePage.entries.retainAll { (_, komikkuPage) ->
            engineByKomikkuPageMap[komikkuPage] != null
        }
        engineByKomikkuPageMap.entries.retainAll { (_, enginePage) ->
            enginePage.chapter in live
        }
    }

    private fun engineChapterFor(komikku: KomikkuReaderChapter): EngineReaderChapter {
        val existing = engineChapters[komikku]
        val pages = komikku.pages
        val reusable = existing != null &&
            pagesListByEngine[existing] === pages &&
            loaderByEngine[existing] === komikku.pageLoader
        if (existing != null && reusable) {
            existing.requestedPage = komikku.requestedPage
            return existing
        }
        if (existing != null) {
            adaptersByEngine.remove(existing)?.dispose()
            loaderByEngine.remove(existing)
            pagesListByEngine.remove(existing)
        }
        val engine = EngineReaderChapter(
            requireNotNull(komikku.chapter.toDomainChapter()) {
                "Chapter ${komikku.chapter.url} has no domain counterpart"
            },
        )
        if (pages != null) {
            val loader = requireNotNull(komikku.pageLoader) {
                "Loaded chapter ${komikku.chapter.url} has no page loader"
            }
            val adapter = KomikkuPageLoaderAdapter(
                loader = loader,
                komikkuPages = pages,
                engineChapter = engine,
                isHttpLoader = loader is HttpPageLoader,
            )
            engine.pageLoader = adapter
            engine.state = EngineReaderChapter.State.Loaded(adapter.pages)
            adaptersByEngine[engine] = adapter
            pages.forEachIndexed { index, komikkuPage ->
                val enginePage = adapter.pages[index]
                komikkuByEnginePage[EngineReaderPageKey(engine.chapter.id, index)] = komikkuPage
                engineByKomikkuPageMap[komikkuPage] = enginePage
            }
        } else {
            engine.state = when (val state = komikku.state) {
                is KomikkuReaderChapter.State.Error -> EngineReaderChapter.State.Error(state.error)
                else -> EngineReaderChapter.State.Wait
            }
            adaptersByEngine[engine] = null
        }
        engine.requestedPage = komikku.requestedPage
        engineChapters[komikku] = engine
        loaderByEngine[engine] = komikku.pageLoader
        pagesListByEngine[engine] = pages
        komikkuChaptersById[engine.chapter.id] = komikku
        return engine
    }

    private data class EngineReaderPageKey(val chapterId: Long, val pageIndex: Int)
}
