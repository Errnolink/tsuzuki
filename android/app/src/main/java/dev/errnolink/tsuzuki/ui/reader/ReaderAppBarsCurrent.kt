package dev.errnolink.tsuzuki.ui.reader

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.GlassContext
import dev.errnolink.tsuzuki.designsystem.GlassSurface
import dev.errnolink.tsuzuki.designsystem.M3Motion
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/** Kasane chrome tokens (reader.css/reader-craft.css): spatial slide, effects fade, bottom stagger. */
private const val KasaneChromeMillis = 380
private const val KasaneChromeExitMillis = 250
private const val KasaneChromeEffectsMillis = 200
private const val KasaneChromeExitEffectsMillis = 150
private const val KasaneBottomStaggerMillis = 30
private val KasaneTopSlideDp = 28.dp
private val KasaneBottomSlideDp = 44.dp
private val KasaneChromeSpatial = CubicBezierEasing(0.3f, 1.25f, 0.45f, 1f)
private val KasaneChromeEffects = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val KasaneChromeExit = CubicBezierEasing(0.4f, 0f, 1f, 1f)
internal val ReaderClearGlassAlpha = 0.72f

/** How long the chrome shows the page a jump asked for when the viewer never confirms it. */
internal const val PageLandingHoldMillis = 600L

@Composable
private fun chromeDissolve(
    scope: AnimatedVisibilityScope,
    shown: Boolean,
    reduced: Boolean,
): Modifier {
    val move by scope.transition.animateFloat(
        transitionSpec = {
            if (targetState == EnterExitState.Visible) {
                tween(KasaneChromeMillis, easing = KasaneChromeSpatial)
            } else {
                tween(KasaneChromeExitMillis, easing = KasaneChromeExit)
            }
        },
        label = "Chrome move",
    ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
    val dissolve by scope.transition.animateFloat(
        transitionSpec = {
            if (targetState == EnterExitState.Visible) {
                tween(KasaneChromeEffectsMillis, easing = KasaneChromeEffects)
            } else {
                tween(KasaneChromeExitEffectsMillis, easing = KasaneChromeExit)
            }
        },
        label = "Chrome dissolve",
    ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
    return Modifier.graphicsLayer {
        val scale = 0.97f + 0.03f * move
        scaleX = scale
        scaleY = scale
        renderEffect = if (!reduced && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dissolve < 1f) {
            val radius = 6.dp.toPx() * (1f - dissolve)
            BlurEffect(radius, radius, TileMode.Decal)
        } else {
            null
        }
    }
}

@Composable
fun ReaderAppBarsCurrent(
    visible: Boolean,
    glass: GlassContext,

    mangaTitle: String?,
    chapterTitle: String?,
    navigateUp: () -> Unit,
    onClickTopAppBar: () -> Unit,
    bookmarked: Boolean,
    onToggleBookmarked: () -> Unit,
    onOpenInWebView: (() -> Unit)?,
    onOpenInBrowser: (() -> Unit)?,
    onShare: (() -> Unit)?,

    onNextChapter: () -> Unit,
    enabledNext: Boolean,
    onPreviousChapter: () -> Unit,
    enabledPrevious: Boolean,
    currentPage: Int,
    totalPages: Int,
    onPageIndexChange: (Int) -> Unit,

    readingMode: ReadingMode,
    onClickReadingMode: () -> Unit,
    orientation: ReaderOrientation,
    onClickOrientation: () -> Unit,
    cropEnabled: Boolean,
    onClickCropBorder: () -> Unit,
    onClickSettings: () -> Unit,
    // SY -->
    isExhToolsVisible: Boolean,
    onSetExhUtilsVisibility: (Boolean) -> Unit,
    isAutoScroll: Boolean,
    isAutoScrollEnabled: Boolean,
    onToggleAutoscroll: (Boolean) -> Unit,
    autoScrollFrequency: String,
    onSetAutoScrollFrequency: (String) -> Unit,
    onClickAutoScrollHelp: () -> Unit,
    onClickRetryAll: () -> Unit,
    onClickRetryAllHelp: () -> Unit,
    onClickBoostPage: () -> Unit,
    onClickBoostPageHelp: () -> Unit,
    navBarType: NavBarType,
    currentPageText: String,
    enabledButtons: ImmutableSet<String>,
    currentReadingMode: ReadingMode,
    dualPageSplitEnabled: Boolean,
    doublePages: Boolean,
    onClickChapterList: () -> Unit,
    onClickPageLayout: () -> Unit,
    onClickShiftPage: () -> Unit,
    barStyle: ReaderBarStyle = ReaderBarStyle.Classic,
    prevChapterShortName: String? = null,
    nextChapterShortName: String? = null,
    currentChapterShortName: String? = null,
    // SY <--
    pageProvider: (Int) -> ReaderPage? = { null },
    isWebtoon: Boolean = false,
    webtoonProgressFlow: StateFlow<WebtoonProgressState>? = null,
    onScrubWebtoonFraction: ((Float) -> Unit)? = null,
    onSaveCapturedPng: (ReaderPage, ByteArray) -> Unit = { _, _ -> },
    onShareCapturedPng: (ReaderPage, ByteArray) -> Unit = { _, _ -> },
    onSuppressStatusLineChange: (Boolean) -> Unit = {},
) {
    val isRtl = currentReadingMode == ReadingMode.RIGHT_TO_LEFT
    val motion = TsuzukiTheme.motion
    val reduced = motion.reduced

    var filmstripExpanded by remember(chapterTitle) { mutableStateOf(false) }
    var returnOriginPage by remember(chapterTitle) { mutableStateOf<Int?>(null) }
    var lightingOpen by remember { mutableStateOf(false) }
    var overviewOpen by remember { mutableStateOf(false) }
    var captureOpen by remember { mutableStateOf(false) }
    /**
     * The page the last jump asked for. The viewer reports a jump some frames later, and until it
     * does every page readout would fall back to the page the reader just left.
     */
    var landingPage by remember(chapterTitle) { mutableStateOf<Int?>(null) }

    val shownPage = landingPage ?: currentPage
    val shownPageText = if (landingPage == null) currentPageText else landingPage.toString()

    LaunchedEffect(visible) {
        if (!visible) lightingOpen = false
    }
    LaunchedEffect(captureOpen, overviewOpen) {
        onSuppressStatusLineChange(captureOpen || overviewOpen)
    }
    LaunchedEffect(shownPage) {
        if (returnOriginPage != null && shownPage == returnOriginPage) {
            returnOriginPage = null
        }
    }
    LaunchedEffect(landingPage, currentPage, currentPageText) {
        val landing = landingPage ?: return@LaunchedEffect
        if (!reportsLanding(currentPage, currentPageText, landing)) delay(PageLandingHoldMillis)
        landingPage = null
    }

    fun jumpToPageIndex(index: Int) {
        returnOriginPage = recordPageJumpOrigin(
            currentOriginPage = returnOriginPage,
            fromPage = currentPage,
            toPage = index + 1,
        )
        landingPage = index + 1
        onPageIndexChange(index)
    }

    fun scrubWebtoonFraction(fraction: Float) {
        val targetIndex = resolveWebtoonTargetPageFromFraction(fraction, totalPages)
        returnOriginPage = recordPageJumpOrigin(
            currentOriginPage = returnOriginPage,
            fromPage = currentPage,
            toPage = targetIndex + 1,
        )
        landingPage = targetIndex + 1
        if (onScrubWebtoonFraction != null) {
            onScrubWebtoonFraction(fraction)
        } else {
            onPageIndexChange(targetIndex)
        }
    }

    val hudBarsVisible = visible && !overviewOpen && !captureOpen

    val topSlide = -with(LocalDensity.current) { KasaneTopSlideDp.roundToPx() }
    val bottomSlide = with(LocalDensity.current) { KasaneBottomSlideDp.roundToPx() }
    val spatialIn: FiniteAnimationSpec<IntOffset> =
        if (reduced) M3Motion.reducedFade() else tween(KasaneChromeMillis, easing = KasaneChromeSpatial)
    val fadeSpec: FiniteAnimationSpec<Float> =
        if (reduced) M3Motion.reducedFade() else tween(KasaneChromeEffectsMillis, easing = KasaneChromeEffects)
    fun slideExit(top: Boolean): FiniteAnimationSpec<IntOffset> =
        if (reduced) {
            M3Motion.reducedFade()
        } else {
            tween(KasaneChromeExitMillis, delayMillis = if (top) 0 else KasaneBottomStaggerMillis, easing = KasaneChromeExit)
        }
    fun fadeExit(top: Boolean): FiniteAnimationSpec<Float> =
        if (reduced) {
            M3Motion.reducedFade()
        } else {
            tween(KasaneChromeExitEffectsMillis, delayMillis = if (top) 0 else KasaneBottomStaggerMillis, easing = KasaneChromeExit)
        }
    val effectiveBarStyle = resolveEffectiveBarStyle(barStyle, navBarType)
    val readerMenuActions = rememberReaderMenuActions(
        enabledButtons = enabledButtons,
        onClickWebView = onOpenInWebView,
        onClickBrowser = onOpenInBrowser,
        onClickShare = onShare,
        onClickReadingMode = onClickReadingMode,
        orientation = orientation,
        onClickOrientation = onClickOrientation,
        cropEnabled = cropEnabled,
        onClickCropBorder = onClickCropBorder,
        currentReadingMode = currentReadingMode,
        dualPageSplitEnabled = dualPageSplitEnabled,
        onClickPageLayout = onClickPageLayout,
        doublePages = doublePages,
        onClickShiftPage = onClickShiftPage,
    )

    androidx.compose.runtime.CompositionLocalProvider(dev.errnolink.tsuzuki.designsystem.LocalGlassFallbackAlpha provides ReaderClearGlassAlpha) {
        Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                hudBarsVisible,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = fadeIn(fadeSpec),
                exit = fadeOut(fadeExit(top = true)),
            ) {
                ReaderTopBand(glass)
            }
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = if (captureOpen) 0f else 1f }
                    .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .then(if (hudBarsVisible) Modifier else Modifier.clearAndSetSemantics {}),
            ) {
                AnimatedVisibility(
                    hudBarsVisible,
                    modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 640.dp),
                    enter = if (reduced) fadeIn(fadeSpec) else slideInVertically(spatialIn) { topSlide },
                    exit = if (reduced) fadeOut(fadeSpec) else slideOutVertically(slideExit(top = true)) { topSlide } + fadeOut(fadeExit(top = true)),
                ) {
                    ReaderTopBar(
                        enabled = hudBarsVisible,
                        chapterTitle = chapterTitle,
                        pageCount = readerPageCount(shownPage, totalPages),
                        navigateUp = navigateUp,
                        onClickChapterList = onClickChapterList,
                        glass = glass,
                        modifier = Modifier.then(chromeDissolve(this, hudBarsVisible, reduced)),
                    )
                }
                if (navBarType != NavBarType.Bottom) {
                    val isRight = navBarType == NavBarType.VerticalRight
                    val sideSign = if (isRight) 1 else -1
                    val verticalShape = if (filmstripExpanded) {
                        RoundedCornerShape(TsuzukiCorners.menu)
                    } else {
                        RoundedCornerShape(TsuzukiCorners.capsulePercent)
                    }
                    AnimatedVisibility(
                        hudBarsVisible,
                        modifier = Modifier.align(if (isRight) Alignment.CenterEnd else Alignment.CenterStart)
                            .padding(top = 76.dp, bottom = 64.dp),
                        enter = if (reduced) fadeIn(fadeSpec) else slideInHorizontally(spatialIn) { sideSign * it },
                        exit = if (reduced) fadeOut(fadeSpec) else slideOutHorizontally(slideExit(top = true)) { sideSign * it } + fadeOut(fadeExit(top = true)),
                    ) {
                        GlassSurface(
                            glass,
                            Modifier
                                .then(chromeDissolve(this, hudBarsVisible, reduced))
                                .fillMaxHeight(0.68f)
                                .heightIn(min = 320.dp, max = 460.dp),
                            shape = verticalShape,
                        ) {
                            Box(Modifier.fillMaxHeight()) {
                                Row(
                                    Modifier.fillMaxHeight(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    val minimapStrip: @Composable () -> Unit = {
                                        if (isWebtoon && totalPages > 1) {
                                            VerticalWebtoonMinimapStripContainer(
                                                expanded = filmstripExpanded,
                                                handleOnLeft = isRight,
                                                currentPage = shownPage,
                                                totalPages = totalPages,
                                                pageProvider = pageProvider,
                                                webtoonProgressFlow = webtoonProgressFlow,
                                                onSelectPage = ::jumpToPageIndex,
                                            )
                                        }
                                    }
                                    val navigatorContent: @Composable () -> Unit = {
                                        key(chapterTitle, currentReadingMode) {
                                            ChapterNavigator(
                                                onNextChapter, enabledNext, onPreviousChapter, enabledPrevious,
                                                shownPage, shownPageText, totalPages, ::jumpToPageIndex,
                                                enabled = hudBarsVisible,
                                            )
                                        }
                                    }
                                    if (isRight) {
                                        minimapStrip()
                                        navigatorContent()
                                    } else {
                                        navigatorContent()
                                        minimapStrip()
                                    }
                                }
                                if (isWebtoon && totalPages > 1) {
                                    VerticalMinimapPullHandle(
                                        expanded = filmstripExpanded,
                                        onExpandedChange = { filmstripExpanded = it },
                                        handleOnLeft = isRight,
                                        modifier = Modifier
                                            .align(if (isRight) Alignment.CenterStart else Alignment.CenterEnd)
                                            .padding(
                                                start = if (isRight) 2.dp else 0.dp,
                                                end = if (isRight) 0.dp else 2.dp,
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }
                AnimatedVisibility(
                    hudBarsVisible,
                    modifier = Modifier.align(Alignment.BottomCenter).widthIn(max = 640.dp),
                    enter = if (reduced) fadeIn(fadeSpec) else slideInVertically(spatialIn) { bottomSlide },
                    exit = if (reduced) fadeOut(fadeSpec) else slideOutVertically(slideExit(top = false)) { bottomSlide } + fadeOut(fadeExit(top = false)),
                ) {
                    Box(Modifier.then(chromeDissolve(this, hudBarsVisible, reduced))) {
                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ReturnToOriginChip(
                                visible = hudBarsVisible && !lightingOpen && returnOriginPage != null && returnOriginPage != shownPage,
                                originPage = returnOriginPage,
                                glass = glass,
                                onReturn = {
                                    val target = returnOriginPage
                                    returnOriginPage = null
                                    landingPage = target
                                    if (target != null) onPageIndexChange((target - 1).coerceAtLeast(0))
                                },
                                onDismiss = { returnOriginPage = null },
                            )

                            when (effectiveBarStyle) {
                                ReaderBarStyle.Classic -> {
                                        if (navBarType == NavBarType.Bottom) {
                                            key(chapterTitle, currentReadingMode) {
                                                ClassicReaderBar(
                                                    glass = glass,
                                                    enabled = hudBarsVisible,
                                                    isRtl = isRtl,
                                                    isWebtoon = isWebtoon,
                                                    filmstripExpanded = filmstripExpanded,
                                                    onFilmstripExpandedChange = { filmstripExpanded = it },
                                                    onPreviousChapter = onPreviousChapter,
                                                    enabledPrevious = enabledPrevious,
                                                    onNextChapter = onNextChapter,
                                                    enabledNext = enabledNext,
                                                    currentPage = shownPage,
                                                    totalPages = totalPages,
                                                    originPage = returnOriginPage,
                                                    onJumpPageIndex = ::jumpToPageIndex,
                                                    webtoonProgressFlow = webtoonProgressFlow,
                                                    onScrubWebtoonFraction = ::scrubWebtoonFraction,
                                                    onClickChapterList = onClickChapterList,
                                                    onClickOverview = { overviewOpen = true },
                                                    onClickLighting = { lightingOpen = true },
                                                    onClickCapture = { captureOpen = true },
                                                    onClickSettings = onClickSettings,
                                                    overviewActive = overviewOpen,
                                                    lightingActive = lightingOpen,
                                                    pageProvider = pageProvider,
                                                )
                                            }
                                        } else {
                                            ClassicReaderButtons(
                                                glass = glass,
                                                enabled = hudBarsVisible,
                                                onClickChapterList = onClickChapterList,
                                                onClickOverview = { overviewOpen = true },
                                                onClickLighting = { lightingOpen = true },
                                                onClickCapture = { captureOpen = true },
                                                onClickSettings = onClickSettings,
                                                overviewActive = overviewOpen,
                                                lightingActive = lightingOpen,
                                            )
                                        }
                                    }
                                    ReaderBarStyle.Scrubber -> key(chapterTitle, currentReadingMode) {
                                        ScrubberReaderBar(
                                            glass = glass,
                                            enabled = hudBarsVisible,
                                            isRtl = isRtl,
                                            onPreviousChapter = onPreviousChapter,
                                            enabledPrevious = enabledPrevious,
                                            onNextChapter = onNextChapter,
                                            enabledNext = enabledNext,
                                            currentPage = shownPage,
                                            totalPages = totalPages,
                                            onPageIndexChange = ::jumpToPageIndex,
                                            onClickChapterList = onClickChapterList,
                                            onClickReadingMode = onClickReadingMode,
                                            onClickSettings = onClickSettings,
                                            moreActions = readerMenuActions,
                                            pageProvider = pageProvider,
                                        )
                                    }
                                    ReaderBarStyle.Capsule -> key(chapterTitle, currentReadingMode) {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            CapsuleReaderBar(
                                                glass = glass,
                                                enabled = hudBarsVisible,
                                                isRtl = isRtl,
                                                chapterTitle = chapterTitle.orEmpty(),
                                                onPreviousChapter = onPreviousChapter,
                                                enabledPrevious = enabledPrevious,
                                                onNextChapter = onNextChapter,
                                                enabledNext = enabledNext,
                                                currentPage = shownPage,
                                                totalPages = totalPages,
                                                onPageIndexChange = ::jumpToPageIndex,
                                                pageProvider = pageProvider,
                                            )
                                            ClassicReaderButtons(
                                                glass = glass,
                                                enabled = hudBarsVisible,
                                                onClickChapterList = onClickChapterList,
                                                onClickOverview = { overviewOpen = true },
                                                onClickLighting = { lightingOpen = true },
                                                onClickCapture = { captureOpen = true },
                                                onClickSettings = onClickSettings,
                                                overviewActive = overviewOpen,
                                                lightingActive = lightingOpen,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            ReaderLightingCard(
                visible = lightingOpen,
                glass = glass,
                onDismiss = { lightingOpen = false },
            )

            ReaderOverviewOverlay(
                visible = overviewOpen,
                glass = glass,
                chapterTitle = chapterTitle,
                currentPage = shownPage,
                totalPages = totalPages,
                isRtl = isRtl,
                prevChapterShortName = prevChapterShortName,
                nextChapterShortName = nextChapterShortName,
                enabledPrevious = enabledPrevious,
                enabledNext = enabledNext,
                onPreviousChapter = onPreviousChapter,
                onNextChapter = onNextChapter,
                onSelectPage = ::jumpToPageIndex,
                onDismiss = { overviewOpen = false },
                pageProvider = pageProvider,
            )

            ReaderCaptureOverlay(
                visible = captureOpen,
                glass = glass,
                currentPage = shownPage,
                totalPages = totalPages,
                isWebtoon = isWebtoon,
                webtoonProgressFlow = webtoonProgressFlow,
                pageProvider = pageProvider,
                onSavePng = onSaveCapturedPng,
                onSharePng = onShareCapturedPng,
                onDismiss = { captureOpen = false },
            )
        }
    }

    ExhUtils(
        isVisible = isExhToolsVisible,
        onSetExhUtilsVisibility = onSetExhUtilsVisibility,
        isAutoScroll = isAutoScroll,
        isAutoScrollEnabled = isAutoScrollEnabled,
        onToggleAutoscroll = onToggleAutoscroll,
        autoScrollFrequency = autoScrollFrequency,
        onSetAutoScrollFrequency = onSetAutoScrollFrequency,
        onClickAutoScrollHelp = onClickAutoScrollHelp,
        onClickRetryAll = onClickRetryAll,
        onClickRetryAllHelp = onClickRetryAllHelp,
        onClickBoostPage = onClickBoostPage,
        onClickBoostPageHelp = onClickBoostPageHelp,
    )
}
