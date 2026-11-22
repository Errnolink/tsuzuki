package dev.errnolink.tsuzuki.reader.engine.loader

import dev.errnolink.tsuzuki.reader.engine.model.ReaderChapter
import dev.errnolink.tsuzuki.reader.engine.model.ReaderPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage as KomikkuReaderPage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Bridges the engine [PageLoader] contract onto Komikku's
 * [eu.kanade.tachiyomi.ui.reader.loader.PageLoader] implementations (Http, Download, Archive, Epub,
 * Directory). Every Komikku page gets an engine mirror whose status and stream follow the Komikku
 * page, so the preload engine watches engine flows while the actual loading stays in Komikku's
 * loaders with their politeness caps untouched.
 *
 * [loadPage] launches the Komikku call in the caller's context, so the preload engine cancelling an
 * out-of-window download propagates into the Komikku loader (its Http loader then un-queues the
 * pages it had enqueued). Once the Komikku page reaches a terminal status the launched call is
 * stopped: the never-returning HTTP loader then only leaves its internal preload entries behind,
 * which that loader removes on cancellation.
 *
 * [isHttpLoader] marks a wrapped `HttpPageLoader`; the next-chapter reload gate uses it to decide
 * whether a since-downloaded chapter should be reloaded from disk.
 */
class KomikkuPageLoaderAdapter(
    private val loader: eu.kanade.tachiyomi.ui.reader.loader.PageLoader,
    komikkuPages: List<KomikkuReaderPage>,
    engineChapter: ReaderChapter,
    val isHttpLoader: Boolean = false,
    mirrorDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PageLoader() {

    private val scope = CoroutineScope(SupervisorJob() + mirrorDispatcher)

    private val komikkuByEngine = HashMap<ReaderPage, KomikkuReaderPage>()

    val pages: List<ReaderPage> =
        komikkuPages.map { komikku ->
            ReaderPage(
                index = komikku.index,
                url = komikku.url,
                imageUrl = komikku.imageUrl,
                stream = komikku.stream,
            ).apply { chapter = engineChapter }
        }

    init {
        pages.forEachIndexed { position, enginePage ->
            komikkuByEngine[enginePage] = komikkuPages[position]
            val komikku = komikkuPages[position]
            enginePage.stream = komikku.stream
            enginePage.status = komikku.status
            scope.launch {
                komikku.statusFlow.collect { status ->
                    enginePage.stream = komikku.stream
                    enginePage.status = status
                }
            }
        }
    }

    override suspend fun getPages(): List<ReaderPage> = pages

    override suspend fun loadPage(page: ReaderPage) {
        val komikku = komikkuByEngine[page] ?: return
        coroutineScope {
            val komikkuLoad = launch { loader.loadPage(komikku) }
            try {
                komikku.statusFlow.first { it == Page.State.Ready || it is Page.State.Error }
                page.stream = komikku.stream
                page.status = komikku.status
            } finally {
                komikkuLoad.cancel()
            }
        }
    }

    override fun retryPage(page: ReaderPage) {
        komikkuByEngine[page]?.let { loader.retryPage(it) }
    }

    /**
     * Stops the mirror flows without recycling the wrapped Komikku loader: when the bridge tears
     * down mirrors the loader stays owned by Komikku's reader (its own ref counting recycles it).
     */
    fun dispose() {
        if (isRecycled) return
        super.recycle()
        scope.cancel()
    }

    override fun recycle() {
        dispose()
        loader.recycle()
    }
}
