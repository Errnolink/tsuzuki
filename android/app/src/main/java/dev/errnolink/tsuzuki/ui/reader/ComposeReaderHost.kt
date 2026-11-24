package dev.errnolink.tsuzuki.ui.reader

import android.content.res.Configuration
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.errnolink.tsuzuki.reader.ReaderTurnSignals
import dev.errnolink.tsuzuki.reader.TurnEdge
import dev.errnolink.tsuzuki.reader.engine.bridge.ViewerChaptersBridge
import dev.errnolink.tsuzuki.reader.engine.loader.ChapterReloadPolicy
import dev.errnolink.tsuzuki.reader.engine.loader.KomikkuPageLoaderAdapter
import dev.errnolink.tsuzuki.reader.engine.loader.ReaderPreloadControllerImpl
import dev.errnolink.tsuzuki.reader.engine.model.ChapterNavTarget
import dev.errnolink.tsuzuki.reader.engine.model.ChapterTransition as EngineChapterTransition
import dev.errnolink.tsuzuki.reader.engine.model.ReaderChapter as EngineReaderChapter
import dev.errnolink.tsuzuki.reader.engine.model.ReaderNavCommand
import dev.errnolink.tsuzuki.reader.engine.model.ReaderPage as EngineReaderPage
import dev.errnolink.tsuzuki.reader.engine.model.ReaderUiItem
import dev.errnolink.tsuzuki.reader.engine.model.ViewerChapters as EngineViewerChapters
import dev.errnolink.tsuzuki.reader.engine.settings.PageLayout
import dev.errnolink.tsuzuki.reader.engine.viewer.ReaderColorFilter
import dev.errnolink.tsuzuki.reader.engine.viewer.ReaderKeyNavigation
import dev.errnolink.tsuzuki.reader.engine.viewer.ViewerNavigation as EngineViewerNavigation
import dev.errnolink.tsuzuki.reader.engine.viewer.navigation.DisabledNavigation
import dev.errnolink.tsuzuki.reader.engine.viewer.navigation.EdgeNavigation
import dev.errnolink.tsuzuki.reader.engine.viewer.navigation.KindlishNavigation
import dev.errnolink.tsuzuki.reader.engine.viewer.navigation.LNavigation
import dev.errnolink.tsuzuki.reader.engine.viewer.navigation.RightAndLeftNavigation
import dev.errnolink.tsuzuki.reader.engine.viewer.pager.ReaderPagerController
import dev.errnolink.tsuzuki.reader.engine.viewer.webtoon.ReaderWebtoonController
import dev.errnolink.tsuzuki.reader.readerCanvasColor
import dev.errnolink.tsuzuki.reader.readerCanvasDark
import dev.errnolink.tsuzuki.ui.reader.viewer.ComposePagerViewer
import dev.errnolink.tsuzuki.ui.reader.viewer.ComposeWebtoonViewer
import dev.errnolink.tsuzuki.ui.reader.viewer.PagerViewerConfigUiModel
import dev.errnolink.tsuzuki.ui.reader.viewer.WebtoonViewerConfigUiModel
import eu.kanade.tachiyomi.util.view.setComposeContent
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.loader.HttpPageLoader
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter as KomikkuReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage as KomikkuReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters as KomikkuViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Mounts the Compose engine readers (C4 viewers) as the sole reader over the running Komikku
 * reader state. The activity keeps driving the Komikku `ReaderViewModel`: this host mirrors its
 * [KomikkuViewerChapters] through [ViewerChaptersBridge], renders `ComposePagerViewer` /
 * `ComposeWebtoonViewer`, routes engine selections back to Komikku pages and turns programmatic
 * moves (scrubber, keys, autoscroll) into engine nav commands.
 */
class ComposeReaderHost(
    private val activity: ReaderActivity,
    private val preferences: ReaderPreferences = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val bridge = ViewerChaptersBridge()
    private val pagerController = ReaderPagerController()
    private val webtoonController = ReaderWebtoonController()
    private val navCommandChannel = Channel<ReaderNavCommand>(capacity = Channel.BUFFERED)
    private val navCommands: Flow<ReaderNavCommand> = navCommandChannel.receiveAsFlow()

    private val itemsFlow = MutableStateFlow<List<ReaderUiItem>>(emptyList())
    private val kindFlow = MutableStateFlow<ComposeReaderKind?>(null)
    private val pagerConfigFlow =
        MutableStateFlow<dev.errnolink.tsuzuki.ui.reader.viewer.PagerViewerConfigUiModel?>(null)
    private val webtoonConfigFlow =
        MutableStateFlow<dev.errnolink.tsuzuki.ui.reader.viewer.WebtoonViewerConfigUiModel?>(null)
    private val _webtoonProgressFlow = MutableStateFlow(WebtoonProgressState())
    val webtoonProgressFlow: StateFlow<WebtoonProgressState> = _webtoonProgressFlow.asStateFlow()

    private var kind: ComposeReaderKind?
        get() = kindFlow.value
        set(value) {
            kindFlow.value = value
        }
    private var readingMode: ReadingMode = ReadingMode.DEFAULT
    private var shiftDoublePages: Boolean =
        activity.viewModel.state.value.lastShiftDoubleState ?: false
    private var doublePagesOverride: Boolean? = null
    private var destroyed = false

    private var lastKomikkuChapters: KomikkuViewerChapters? = null

    /** The Komikku page the engine viewer last reported as active, for the EXH page helpers. */
    var lastSelectedKomikkuPage: KomikkuReaderPage? = null
        private set

    val isWebtoon: Boolean
        get() = kind == ComposeReaderKind.Webtoon

    private val reloadPolicy = ChapterReloadPolicy(
        isDownloadedWithCache = { engineChapter ->
            komikkuChapter(engineChapter)?.let { isChapterDownloaded(it, skipCache = false) } ?: false
        },
        isDownloadedOnDisk = { engineChapter ->
            komikkuChapter(engineChapter)?.let { isChapterDownloaded(it, skipCache = true) } ?: false
        },
    )

    private val preloadController = ReaderPreloadControllerImpl(
        context = activity.applicationContext,
        scope = scope,
        onRequestPreloadChapter = { onEngineChapterPreloadRequested(it) },
    )

    val view: ComposeView = ComposeView(activity).apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        )
        ViewCompat.setOnApplyWindowInsetsListener(this) { v, windowInsets ->
            applyInsets(v, windowInsets, preferences.fullscreen().get(), preferences.drawUnderCutout().get())
            windowInsets
        }
        setComposeContent { this@ComposeReaderHost.ReaderContent() }
    }

    init {
        preferences.fullscreen().changes()
            .onEach { view.requestApplyInsets() }
            .launchIn(scope)
        preferences.drawUnderCutout().changes()
            .onEach { view.requestApplyInsets() }
            .launchIn(scope)
        preferences.pageLayout().changes()
            .drop(1)
            .onEach { rebuild() }
            .launchIn(scope)
        preferences.dualPageSplitPaged().changes()
            .drop(1)
            .onEach { rebuild() }
            .launchIn(scope)
        preferences.alwaysShowChapterTransition().changes()
            .drop(1)
            .onEach { rebuild() }
            .launchIn(scope)
        preferences.splitTallPages().changes()
            .drop(1)
            .onEach { rebuild() }
            .launchIn(scope)
        configPreferences().forEach { pref ->
            pref.changes()
                .drop(1)
                .onEach { rebuildConfig() }
                .launchIn(scope)
        }
        activity.viewModel.state
            .map { it.menuVisible }
            .distinctUntilChanged()
            .onEach { menuVisible ->
                pagerConfigFlow.value?.let { pagerConfigFlow.value = it.copy(menuVisible = menuVisible) }
                webtoonConfigFlow.value?.let { webtoonConfigFlow.value = it.copy(menuVisible = menuVisible) }
            }
            .launchIn(scope)
    }

    /** Every preference the viewer configs embed; a change only needs a config rebuild. */
    private fun configPreferences(): List<Preference<*>> {
        return listOf(
            preferences.imageScaleType(),
            preferences.zoomStart(),
            preferences.cropBorders(),
            preferences.cropBordersWebtoon(),
            preferences.cropBordersContinuousVertical(),
            preferences.navigateToPan(),
            preferences.landscapeZoom(),
            preferences.landscapeZoomType(),
            preferences.doubleTapAnimSpeed(),
            preferences.pagedDoubleTapZoomEnabled(),
            preferences.pagedDisableZoomIn(),
            preferences.readWithLongTap(),
            preferences.smallerTapZone(),
            preferences.navigationModePager(),
            preferences.navigationModeWebtoon(),
            preferences.pagerNavInverted(),
            preferences.webtoonNavInverted(),
            preferences.invertDoublePages(),
            preferences.centerMarginType(),
            preferences.dualPageRotateToFit(),
            preferences.dualPageRotateToFitInvert(),
            preferences.dualPageSplitWebtoon(),
            preferences.dualPageInvertWebtoon(),
            preferences.dualPageRotateToFitWebtoon(),
            preferences.dualPageRotateToFitInvertWebtoon(),
            preferences.grayscale(),
            preferences.invertedColors(),
            preferences.eInkMode(),
            preferences.readerTheme(),
            preferences.preloadSize(),
            preferences.pageTransitionsPager(),
            preferences.pageTransitionsWebtoon(),
            preferences.webtoonSidePadding(),
            preferences.webtoonDisableZoomOut(),
            preferences.webtoonDoubleTapZoomEnabled(),
            preferences.webtoonPinchToZoomEnabled(),
            preferences.webtoonScaleType(),
            preferences.longStripGapSmartScale(),
        )
    }

    /** Mirrors the given Komikku chapters into the engine model and rebuilds the item list. */
    fun setChapters(chapters: KomikkuViewerChapters) {
        if (destroyed) return
        try {
            lastKomikkuChapters = chapters
            bridge.map(chapters)
            rebuild()
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            logcat(LogPriority.ERROR, e) { "Compose reader setChapters failed" }
        }
    }

    private fun rebuild() {
        if (destroyed) return
        try {
            val chapters = lastKomikkuChapters ?: return
            val engine = bridge.current
            val mode = ReadingMode.fromPreference(activity.viewModel.getMangaReadingMode())
            val resolvedKind = resolveComposeReaderKind(mode)
            kind = resolvedKind
            readingMode = mode
            ComposeReaderState.activeKind = resolvedKind
            val forceTransition = composeReaderForceTransition(
                kind = resolvedKind,
                mode = mode,
                alwaysShowChapterTransition = preferences.alwaysShowChapterTransition().get(),
            )
            val screenHeight =
                if (preferences.splitTallPages().get()) activity.resources.displayMetrics.heightPixels else 0
            val items = when (resolvedKind) {
                is ComposeReaderKind.Pager -> {
                    pagerController.buildItems(
                        chapters = engine,
                        forceTransition = forceTransition,
                        doublePages = effectiveDoublePages(),
                        splitPages = preferences.dualPageSplitPaged().get(),
                        shiftDoublePage = shiftDoublePages,
                        isRtl = resolvedKind.isRtl,
                    )
                }
                ComposeReaderKind.Webtoon -> {
                    webtoonController.buildItems(
                        chapters = engine,
                        forceTransition = forceTransition,
                        screenHeight = screenHeight,
                        existingItems = itemsFlow.value,
                    )
                }
            }
            if (!composeReaderItemsUnchanged(itemsFlow.value, items)) {
                itemsFlow.value = items
            }
            rebuildConfig()
            activity.viewModel.setDoublePages(resolvedKind is ComposeReaderKind.Pager && effectiveDoublePages())
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            logcat(LogPriority.ERROR, e) { "Compose reader rebuild failed" }
        }
    }

    /** Rebuilds the active viewer config; the instance stays stable between real changes. */
    private fun rebuildConfig() {
        val menuVisible = activity.viewModel.state.value.menuVisible
        when (kind) {
            is ComposeReaderKind.Pager -> {
                pagerConfigFlow.value = buildPagerConfig(menuVisible)
            }
            ComposeReaderKind.Webtoon -> {
                webtoonConfigFlow.value = buildWebtoonConfig(menuVisible)
            }
            else -> {}
        }
    }

    private fun effectiveDoublePages(): Boolean {
        doublePagesOverride?.let { return it }
        return when (PageLayout.fromPreference(preferences.pageLayout().get())) {
            PageLayout.DOUBLE_PAGES -> true
            PageLayout.AUTOMATIC ->
                activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            else -> false
        }
    }

    private fun resolveInitialIndex(items: List<ReaderUiItem>): Int {
        val engineCurr = bridge.current.currChapter
        val page = engineCurr.pages?.getOrNull(engineCurr.requestedPage) ?: return 0
        return when (val resolvedKind = kind) {
            is ComposeReaderKind.Pager -> pagerController.findPageIndex(items, page)
            ComposeReaderKind.Webtoon -> webtoonController.findPageIndex(items, page)
            else -> null
        } ?: 0
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        ComposeReaderState.activeKind = null
        preloadController.release()
        bridge.release()
        scope.cancel()
        (view.parent as? ViewGroup)?.removeView(view)
    }

    // region Programmatic navigation

    /** Moves to the Komikku page at [index] of the current chapter as an animated transition (chapter changes). */
    fun moveToPageIndex(index: Int) {
        val page = activity.viewModel.state.value.currentChapter?.pages?.getOrNull(index) ?: return
        moveToPage(page)
    }

    /**
     * Jumps to the Komikku page at [index] of the current chapter (scrubber, go-to page, overview).
     * A Webtoon jump lands at once: animating it scrolls through every page between.
     */
    fun jumpToPageIndex(index: Int) {
        if (!isWebtoon) {
            moveToPageIndex(index)
            return
        }
        val page = activity.viewModel.state.value.currentChapter?.pages?.getOrNull(index) ?: return
        val enginePage = bridge.enginePageOf(page) ?: return
        sendNavCommand(
            ReaderNavCommand.SnapToPage(
                pageIndex = enginePage.index,
                chapterId = enginePage.chapter.chapter.id,
            ),
        )
    }

    /** Jumps Webtoon scroll position immediately using a normalized storyboard/minimap fraction (`0f..1f`). */
    fun moveToWebtoonFraction(fraction: Float) {
        val currentChapter = activity.viewModel.state.value.currentChapter ?: return
        val pages = currentChapter.pages ?: return
        val pageIndex = resolveWebtoonTargetPageFromFraction(fraction, pages.size)
        val page = pages.getOrNull(pageIndex) ?: return
        val enginePage = bridge.enginePageOf(page) ?: return
        sendNavCommand(
            ReaderNavCommand.SnapToPage(
                pageIndex = enginePage.index,
                chapterId = enginePage.chapter.chapter.id,
            ),
        )
    }

    /** Sends a nav command moving to the engine mirror of the given Komikku page. */
    fun moveToPage(page: KomikkuReaderPage) {
        val enginePage = bridge.enginePageOf(page) ?: return
        sendNavCommand(
            ReaderNavCommand.ScrollToPage(
                pageIndex = enginePage.index,
                chapterId = enginePage.chapter.chapter.id,
            ),
        )
    }

    private fun step(forward: Boolean) {
        sendNavCommand(ReaderNavCommand.StepPage(forward))
    }

    private fun sendNavCommand(command: ReaderNavCommand) {
        navCommandChannel.trySend(command)
    }

    /** One SY auto-scroll tick: a page step on the pager, a screen of scroll on the webtoon viewer. */
    fun autoscrollTick() {
        if (isWebtoon && preferences.smoothAutoScroll().get()) {
            sendNavCommand(
                ReaderNavCommand.ScrollByDelta(activity.resources.displayMetrics.heightPixels.toFloat()),
            )
        } else {
            step(forward = true)
        }
    }

    // endregion

    // region Key events

    fun handleKeyEvent(event: KeyEvent): Boolean {
        val isUp = event.action == KeyEvent.ACTION_UP
        val ctrlPressed = event.metaState and KeyEvent.META_CTRL_ON > 0
        val menuVisible = activity.viewModel.state.value.menuVisible
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (!preferences.readWithVolumeKeys().get() || menuVisible) return false
                if (isUp) {
                    flashKeyGlow(event.keyCode, ctrlPressed)
                    step(forward = !preferences.readWithVolumeKeysInverted().get())
                }
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (!preferences.readWithVolumeKeys().get() || menuVisible) return false
                if (isUp) {
                    flashKeyGlow(event.keyCode, ctrlPressed)
                    step(forward = preferences.readWithVolumeKeysInverted().get())
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (isUp) {
                flashKeyGlow(event.keyCode, ctrlPressed)
                if (ctrlPressed) loadAdjacentChapter(forward = true) else moveSide(right = true)
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> if (isUp) {
                flashKeyGlow(event.keyCode, ctrlPressed)
                if (ctrlPressed) loadAdjacentChapter(forward = false) else moveSide(right = false)
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> if (isUp) {
                flashKeyGlow(event.keyCode, ctrlPressed)
                step(forward = true)
            }
            KeyEvent.KEYCODE_DPAD_UP -> if (isUp) {
                flashKeyGlow(event.keyCode, ctrlPressed)
                step(forward = false)
            }
            KeyEvent.KEYCODE_PAGE_DOWN -> if (isUp) {
                flashKeyGlow(event.keyCode, ctrlPressed)
                step(forward = true)
            }
            KeyEvent.KEYCODE_PAGE_UP -> if (isUp) {
                flashKeyGlow(event.keyCode, ctrlPressed)
                step(forward = false)
            }
            KeyEvent.KEYCODE_MENU -> if (isUp) activity.toggleMenu()
            else -> {
                val forward = ReaderKeyNavigation.resolveAdjacentDirection(event.keyCode, isRtl())
                    ?: return false
                if (isUp) {
                    loadAdjacentChapter(forward)
                    flashChapterKeyGlow(forward)
                }
            }
        }
        return true
    }

    fun handleGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_CLASS_POINTER == 0) return false
        if (event.action != MotionEvent.ACTION_SCROLL) return false
        val vscroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
        if (vscroll == 0f) return false
        if (isWebtoon) {
            val distance = activity.resources.displayMetrics.heightPixels * 3 / 4
            sendNavCommand(ReaderNavCommand.ScrollByDelta(vscroll * distance))
        } else {
            flashKeyGlow(if (vscroll > 0) KeyEvent.KEYCODE_PAGE_DOWN else KeyEvent.KEYCODE_PAGE_UP, ctrlPressed = false)
            step(forward = vscroll > 0)
        }
        return true
    }

    /** Reading-order direction for the side arrow keys: LTR right is forward, RTL right is back. */
    private fun moveSide(right: Boolean) {
        val pager = kind as? ComposeReaderKind.Pager ?: return
        step(forward = if (pager.isVertical) right else if (pager.isRtl) !right else right)
    }

    private fun isRtl(): Boolean = (kind as? ComposeReaderKind.Pager)?.isRtl == true

    private fun loadAdjacentChapter(forward: Boolean) {
        if (forward) {
            activity.loadNextChapter()
        } else {
            activity.loadPreviousChapter()
        }
    }

    /** The edge glow a handled page-turn key flashes, mirroring the old pager's key handling. */
    private fun flashKeyGlow(keyCode: Int, ctrlPressed: Boolean) {
        val pager = kind as? ComposeReaderKind.Pager ?: return
        val dir = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT ->
                if (ctrlPressed) 1 else if (pager.isVertical || !pager.isRtl) 1 else -1
            KeyEvent.KEYCODE_DPAD_LEFT ->
                if (ctrlPressed) -1 else if (pager.isVertical) -1 else if (pager.isRtl) 1 else -1
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> 1
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_PAGE_UP -> -1
            KeyEvent.KEYCODE_VOLUME_DOWN ->
                if (!preferences.readWithVolumeKeysInverted().get()) 1 else -1
            KeyEvent.KEYCODE_VOLUME_UP ->
                if (!preferences.readWithVolumeKeysInverted().get()) -1 else 1
            else -> return
        }
        ReaderTurnSignals.flashAt(flashEdge(pager, dir))
    }

    private fun flashChapterKeyGlow(forward: Boolean) {
        val pager = kind as? ComposeReaderKind.Pager ?: return
        ReaderTurnSignals.flashAt(flashEdge(pager, if (forward) 1 else -1))
    }

    private fun flashEdge(pager: ComposeReaderKind.Pager, dir: Int): TurnEdge {
        return when {
            !pager.isVertical ->
                if ((dir > 0) == pager.isRtl) TurnEdge.Left else TurnEdge.Right
            dir > 0 -> TurnEdge.Bottom
            else -> TurnEdge.Top
        }
    }

    // endregion

    // region Viewer callbacks

    private fun onActiveItemChanged(index: Int) {
        preloadController.onPositionChanged(
            currentIndex = index,
            items = itemsFlow.value,
            preloadAmount = preferences.preloadSize().get(),
            isRtl = isRtl(),
            isWebtoon = isWebtoon,
        )
    }

    private fun onPageSelected(enginePage: EngineReaderPage, hasExtraPage: Boolean) {
        val komikkuPage = bridge.komikkuPageOf(enginePage) ?: return
        lastSelectedKomikkuPage = komikkuPage
        activity.onPageSelected(komikkuPage, hasExtraPage)
    }

    private fun onPageLongTap(enginePage: EngineReaderPage, extraPage: EngineReaderPage?) {
        val komikkuPage = bridge.komikkuPageOf(enginePage) ?: return
        val komikkuExtra = extraPage?.let { bridge.komikkuPageOf(it) }
        activity.onPageLongTap(komikkuPage, komikkuExtra)
    }

    private fun onTransitionSelected(transition: EngineChapterTransition) {
        val toChapter = transition.to
        if (toChapter != null) {
            onEngineChapterPreloadRequested(toChapter)
        } else if (transition is EngineChapterTransition.Next) {
            activity.showMenu()
        }
    }

    private fun onNavigateToChapter(chapterId: Long, navTarget: ChapterNavTarget) {
        val engine = bridge.current
        when (chapterId) {
            engine.nextChapter?.chapter?.id -> activity.loadNextChapter()
            engine.prevChapter?.chapter?.id -> activity.loadPreviousChapter()
        }
    }

    /** Chapter-boundary preload request, with the Neko #3523 download-appeared reset. */
    private fun onEngineChapterPreloadRequested(engineChapter: EngineReaderChapter) {
        val komikku = bridge.komikkuChapterOf(engineChapter.chapter.id) ?: return
        if (komikku !== lastKomikkuChapters?.currChapter) {
            val adapter = engineChapter.pageLoader as? KomikkuPageLoaderAdapter
            val isHttpLoaded = adapter?.isHttpLoader == true || komikku.pageLoader is HttpPageLoader
            if (reloadPolicy.isDownloadedSinceHttpLoad(engineChapter, isHttpLoaded)) {
                // The chapter was loaded over HTTP and downloaded since: reset it so the next
                // load picks the download loader instead of looping over the HTTP one.
                komikku.state = KomikkuReaderChapter.State.Wait
                komikku.pageLoader = null
            }
        }
        activity.requestPreloadChapter(komikku)
    }

    private fun komikkuChapter(engineChapter: EngineReaderChapter): KomikkuReaderChapter? {
        return bridge.komikkuChapterOf(engineChapter.chapter.id)
    }

    private fun isChapterDownloaded(chapter: KomikkuReaderChapter, skipCache: Boolean): Boolean {
        val state = activity.viewModel.state.value
        val manga = state.mergedManga?.get(chapter.chapter.manga_id) ?: state.manga ?: return false
        return downloadManager.isChapterDownloaded(
            chapterName = chapter.chapter.name,
            chapterScanlator = chapter.chapter.scanlator,
            chapterUrl = chapter.chapter.url,
            mangaTitle = manga.ogTitle,
            sourceId = manga.source,
            skipCache = skipCache,
        )
    }

    // endregion

    // region Double pages

    /** EXH quick toggle for automatic layout: flip double pages and rebuild the item list. */
    fun toggleDoublePages() {
        if (PageLayout.fromPreference(preferences.pageLayout().get()) != PageLayout.AUTOMATIC) return
        setDoublePages(!effectiveDoublePages())
    }

    /** EXH reload path: pin the double-page mode and rebuild the item list. */
    fun setDoublePages(doublePages: Boolean) {
        doublePagesOverride = doublePages
        activity.viewModel.setDoublePages(doublePages)
        rebuild()
    }

    /** EXH shift button: swap the odd/even pairing in double-page mode. */
    fun shiftDoublePages() {
        shiftDoublePages = !shiftDoublePages
        rebuild()
    }

    // endregion

    // region Compose content

    private fun applyInsets(
        v: View,
        windowInsets: WindowInsetsCompat,
        fullscreen: Boolean,
        drawUnderCutout: Boolean,
    ) {
        val insets = when {
            !fullscreen -> windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            !drawUnderCutout -> windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
            else -> null
        } ?: Insets.NONE
        v.setPadding(insets.left, insets.top, insets.right, insets.bottom)
    }

    private fun buildNavigator(
        navigationMode: Int,
        defaultNavigation: EngineViewerNavigation,
        invertMode: ReaderPreferences.TappingInvertMode,
        smallerTapZone: Boolean,
    ): EngineViewerNavigation {
        val navigation = when (navigationMode) {
            0 -> defaultNavigation
            1 -> LNavigation()
            2 -> KindlishNavigation()
            3 -> EdgeNavigation()
            4 -> RightAndLeftNavigation()
            5 -> DisabledNavigation()
            else -> defaultNavigation
        }
        navigation.invertMode = when (invertMode) {
            ReaderPreferences.TappingInvertMode.NONE -> EngineViewerNavigation.TappingInvertMode.NONE
            ReaderPreferences.TappingInvertMode.HORIZONTAL -> EngineViewerNavigation.TappingInvertMode.HORIZONTAL
            ReaderPreferences.TappingInvertMode.VERTICAL -> EngineViewerNavigation.TappingInvertMode.VERTICAL
            ReaderPreferences.TappingInvertMode.BOTH -> EngineViewerNavigation.TappingInvertMode.BOTH
        }
        navigation.smallerTapZone = smallerTapZone
        return navigation
    }

    private fun pagerNavigator(
        navigationMode: Int,
        invertMode: ReaderPreferences.TappingInvertMode,
        smallerTapZone: Boolean,
    ): EngineViewerNavigation {
        val isVertical = (kind as? ComposeReaderKind.Pager)?.isVertical == true
        return buildNavigator(
            navigationMode = navigationMode,
            defaultNavigation = if (isVertical) LNavigation() else RightAndLeftNavigation(),
            invertMode = invertMode,
            smallerTapZone = smallerTapZone,
        )
    }

    private fun webtoonNavigator(
        navigationMode: Int,
        invertMode: ReaderPreferences.TappingInvertMode,
        smallerTapZone: Boolean,
    ): EngineViewerNavigation {
        return buildNavigator(
            navigationMode = navigationMode,
            defaultNavigation = LNavigation(),
            invertMode = invertMode,
            smallerTapZone = smallerTapZone,
        )
    }

    @Composable
    private fun ReaderContent() {
        val items by itemsFlow.collectAsState()
        val resolvedKind = kindFlow.collectAsState().value ?: return
        val pagerConfig by pagerConfigFlow.collectAsState()
        val webtoonConfig by webtoonConfigFlow.collectAsState()
        if (items.isEmpty()) return

        when (resolvedKind) {
            is ComposeReaderKind.Pager -> {
                val config = pagerConfig ?: return
                ComposePagerViewer(
                    items = items,
                    config = config,
                    onActiveItemChanged = ::onActiveItemChanged,
                    onPageSelected = ::onPageSelected,
                    onTransitionSelected = ::onTransitionSelected,
                    navCommands = navCommands,
                )
            }
            ComposeReaderKind.Webtoon -> {
                val config = webtoonConfig ?: return
                ComposeWebtoonViewer(
                    items = items,
                    config = config,
                    navCommands = navCommands,
                    onActiveItemChanged = ::onActiveItemChanged,
                    onPageSelected = { onPageSelected(it, false) },
                    onTransitionSelected = ::onTransitionSelected,
                    onPageLongTap = { onPageLongTap(it, null) },
                    onNavigateAdjacent = ::loadAdjacentChapter,
                    onWebtoonProgressChanged = { progress, viewportFraction ->
                        _webtoonProgressFlow.value = WebtoonProgressState(progress, viewportFraction)
                    },
                )
            }
        }
    }

    private fun flashTapTurnGlow(xFraction: Float, yFraction: Float) {
        val horizontalPager = (kind as? ComposeReaderKind.Pager)?.isVertical == false
        ReaderTurnSignals.flashAt(resolveTapTurnEdge(horizontalPager, xFraction, yFraction))
    }

    /**
     * Built outside composition so the instance stays stable between real changes: the viewers
     * treat an unequal config (the navigator has no value equality) as a change and re-run their
     * remember blocks and pre-measure re-anchors on every such recomposition.
     */
    private fun buildPagerConfig(menuVisible: Boolean): PagerViewerConfigUiModel {
        val pagerKind = kind as? ComposeReaderKind.Pager ?: ComposeReaderKind.Pager(isRtl = false, isVertical = false)
        val cropBorders = preferences.cropBorders().get()
        val centerMarginType = preferences.centerMarginType().get()
        val readerTheme = preferences.readerTheme().get()
        return PagerViewerConfigUiModel(
            initialIndex = resolveInitialIndex(itemsFlow.value),
            activeChapterId = bridge.current.currChapter.chapter.id,
            backgroundColor = readerCanvasColor(readerTheme, systemDark()),
            colorFilter = ReaderColorFilter.getColorFilter(
                preferences.grayscale().get(),
                preferences.invertedColors().get(),
            ),
            isRtl = pagerKind.isRtl,
            isVertical = pagerKind.isVertical,
            animatedTransitions = preferences.pageTransitionsPager().get(),
            imageScaleType = preferences.imageScaleType().get(),
            pageLayout = PageLayout.fromPreference(preferences.pageLayout().get()),
            doublePages = effectiveDoublePages(),
            shiftDoublePage = shiftDoublePages,
            invertDoublePages = preferences.invertDoublePages().get(),
            doublePageGap = resolveDoublePageGap(centerMarginType, cropBorders),
            doublePageRotate = preferences.dualPageRotateToFit().get(),
            doublePageRotateReverse = preferences.dualPageRotateToFitInvert().get(),
            zoomStart = preferences.zoomStart().get(),
            landscapeZoom = preferences.landscapeZoom().get(),
            landscapeZoomScaleDouble =
                preferences.landscapeZoomType().get() == ReaderPreferences.LandscapeZoomScaleType.DOUBLE,
            centerMarginType = centerMarginType,
            automaticBackground = readerTheme == 3,
            eInkMode = preferences.eInkMode().get(),
            navigateToPan = preferences.navigateToPan().get(),
            doubleTapAnimDuration = preferences.doubleTapAnimSpeed().get(),
            doubleTapZoomEnabled = preferences.pagedDoubleTapZoomEnabled().get(),
            disableZoomIn = preferences.pagedDisableZoomIn().get(),
            longTapEnabled = preferences.readWithLongTap().get(),
            menuVisible = menuVisible,
            cropBorders = cropBorders,
            navigator = pagerNavigator(
                preferences.navigationModePager().get(),
                preferences.pagerNavInverted().get(),
                preferences.smallerTapZone().get(),
            ),
            preloadPageAmount = preferences.preloadSize().get(),
            onToggleMenu = activity::toggleMenu,
            onNavigateAdjacent = ::loadAdjacentChapter,
            onTapTurn = { _, xFraction, yFraction -> flashTapTurnGlow(xFraction, yFraction) },
            onRetryTransition = ::onEngineChapterPreloadRequested,
            onNavigateToChapter = { chapter, navTarget -> onNavigateToChapter(chapter.id, navTarget) },
            onRequestPreloadChapter = ::onEngineChapterPreloadRequested,
            onPageLongTap = ::onPageLongTap,
        )
    }

    private fun buildWebtoonConfig(menuVisible: Boolean): WebtoonViewerConfigUiModel {
        val hasGaps = readingMode == ReadingMode.CONTINUOUS_VERTICAL
        val cropBorders = if (hasGaps) {
            preferences.cropBordersContinuousVertical().get()
        } else {
            preferences.cropBordersWebtoon().get()
        }
        val readerTheme = preferences.readerTheme().get()
        val scaleRatio = resolveWebtoonScaleRatio(
            hasGaps = hasGaps,
            smartScale = preferences.longStripGapSmartScale().get(),
            scaleType = preferences.webtoonScaleType().get(),
        )
        return WebtoonViewerConfigUiModel(
            initialIndex = resolveInitialIndex(itemsFlow.value),
            activeChapterId = bridge.current.currChapter.chapter.id,
            backgroundColor = readerCanvasColor(readerTheme, systemDark()),
            colorFilter = ReaderColorFilter.getColorFilter(
                preferences.grayscale().get(),
                preferences.invertedColors().get(),
            ),
            sidePaddingPercent = preferences.webtoonSidePadding().get() / 100f,
            hasGaps = hasGaps,
            enableZoomOut = !preferences.webtoonDisableZoomOut().get(),
            doubleTapZoomEnabled = preferences.webtoonDoubleTapZoomEnabled().get(),
            pinchToZoomEnabled = preferences.webtoonPinchToZoomEnabled().get(),
            webtoonScaleRatio = scaleRatio,
            automaticBackground = readerTheme == 3,
            eInkMode = preferences.eInkMode().get(),
            dualPageSplit = preferences.dualPageSplitWebtoon().get(),
            dualPageInvert = preferences.dualPageInvertWebtoon().get(),
            dualPageRotate = preferences.dualPageRotateToFitWebtoon().get(),
            dualPageRotateReverse = preferences.dualPageRotateToFitInvertWebtoon().get(),
            animatedTransitions = preferences.pageTransitionsWebtoon().get(),
            doubleTapAnimDuration =
            if (preferences.webtoonDoubleTapZoomEnabled().get()) preferences.doubleTapAnimSpeed().get() else 0,
            longTapEnabled = preferences.readWithLongTap().get(),
            menuVisible = menuVisible,
            cropBorders = cropBorders,
            navigator = webtoonNavigator(
                preferences.navigationModeWebtoon().get(),
                preferences.webtoonNavInverted().get(),
                preferences.smallerTapZone().get(),
            ),
            preloadPageAmount = preferences.preloadSize().get(),
            onToggleMenu = activity::toggleMenu,
            onTapTurn = { _, xFraction, yFraction -> flashTapTurnGlow(xFraction, yFraction) },
            onRetryTransition = ::onEngineChapterPreloadRequested,
            onNavigateToChapter = { chapter, navTarget -> onNavigateToChapter(chapter.id, navTarget) },
            onRequestPreloadChapter = ::onEngineChapterPreloadRequested,
        )
    }

    private fun systemDark(): Boolean =
        (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    // endregion
}

internal fun resolveDoublePageGap(centerMarginType: Int, cropBorders: Boolean): Int =
    if ((centerMarginType and 1) != 0 && !cropBorders) 6 else 0

internal fun resolveWebtoonScaleRatio(
    hasGaps: Boolean,
    smartScale: Boolean,
    scaleType: ReaderPreferences.WebtoonScaleType,
): Float = if (!hasGaps || smartScale) scaleType.ratio else 0f

internal fun resolveTapTurnEdge(horizontalPager: Boolean, xFraction: Float, yFraction: Float): TurnEdge =
    when {
        horizontalPager -> if (xFraction < 0.5f) TurnEdge.Left else TurnEdge.Right
        yFraction < 0.5f -> TurnEdge.Top
        else -> TurnEdge.Bottom
    }
