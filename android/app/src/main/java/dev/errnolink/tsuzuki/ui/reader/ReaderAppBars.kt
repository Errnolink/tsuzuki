package dev.errnolink.tsuzuki.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.GlassContext
import dev.errnolink.tsuzuki.designsystem.GlassSurface
import dev.errnolink.tsuzuki.designsystem.M3Motion
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.R2LPagerViewer
import kotlinx.collections.immutable.ImmutableSet

// SY -->
enum class NavBarType {
    VerticalRight,
    VerticalLeft,
    Bottom,
}
// SY <--

@Composable
fun ReaderAppBars(
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

    viewer: Viewer?,
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
) {
    val isRtl = viewer is R2LPagerViewer
    val motion = TsuzukiTheme.motion
    val moveEnter: FiniteAnimationSpec<IntOffset> = when {
        motion.reduced -> M3Motion.reducedFade()
        motion.standardTokens -> androidx.compose.animation.core.tween(M3Motion.Short4, easing = M3Motion.StandardDecelerate)
        else -> M3Motion.enter()
    }
    val moveExit: FiniteAnimationSpec<IntOffset> = when {
        motion.reduced -> M3Motion.reducedFade()
        motion.standardTokens -> androidx.compose.animation.core.tween(M3Motion.Short3, easing = M3Motion.StandardAccelerate)
        else -> M3Motion.emphasized(M3Motion.Medium1)
    }
    val fadeSpec: FiniteAnimationSpec<Float> = M3Motion.reducedFade()
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
    Box(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .then(if (visible) Modifier else Modifier.clearAndSetSemantics {}),
    ) {
        AnimatedVisibility(
            visible,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = if (motion.reduced) fadeIn(fadeSpec) else slideInVertically(moveEnter) { -it },
            exit = if (motion.reduced) fadeOut(fadeSpec) else slideOutVertically(moveExit) { -it },
        ) {
            Box {
                ReaderTopBar(
                    enabled = visible,
                    mangaTitle = mangaTitle,
                    chapterTitle = chapterTitle,
                    navigateUp = navigateUp,
                    onTitleClick = onClickTopAppBar,
                    bookmarked = bookmarked,
                    onToggleBookmarked = onToggleBookmarked,
                    onToolsClick = { onSetExhUtilsVisibility(true) },
                    compactTools = effectiveBarStyle == ReaderBarStyle.Capsule,
                    onClickChapterList = onClickChapterList,
                    onClickSettings = onClickSettings,
                    moreActions = readerMenuActions,
                )
            }
        }
        if (navBarType != NavBarType.Bottom) {
            val sideSign = if (navBarType == NavBarType.VerticalLeft) -1 else 1
            AnimatedVisibility(
                visible,
                modifier = Modifier.align(if (sideSign > 0) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(top = 76.dp, bottom = 64.dp),
                enter = if (motion.reduced) fadeIn(fadeSpec) else slideInHorizontally(moveEnter) { sideSign * it },
                exit = if (motion.reduced) fadeOut(fadeSpec) else slideOutHorizontally(moveExit) { sideSign * it },
            ) {
                GlassSurface(null, Modifier.heightIn(max = 480.dp), shape = RoundedCornerShape(28.dp)) {
                    key(chapterTitle, viewer) {
                        ChapterNavigator(
                            onNextChapter, enabledNext, onPreviousChapter, enabledPrevious,
                            currentPage, currentPageText, totalPages, onPageIndexChange, enabled = visible,
                        )
                    }
                }
            }
        }
        AnimatedVisibility(
            visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = if (motion.reduced) fadeIn(fadeSpec) else slideInVertically(moveEnter) { it },
            exit = if (motion.reduced) fadeOut(fadeSpec) else slideOutVertically(moveExit) { it },
        ) {
            when (effectiveBarStyle) {
                ReaderBarStyle.Classic -> {
                    if (navBarType == NavBarType.Bottom) {
                        key(chapterTitle, viewer) {
                            ClassicReaderBar(
                                glass = null,
                                enabled = visible,
                                isRtl = isRtl,
                                onPreviousChapter = onPreviousChapter,
                                enabledPrevious = enabledPrevious,
                                onNextChapter = onNextChapter,
                                enabledNext = enabledNext,
                                currentPage = currentPage,
                                totalPages = totalPages,
                                onPageIndexChange = onPageIndexChange,
                                onClickChapterList = onClickChapterList,
                                onClickReadingMode = onClickReadingMode,
                                onClickOrientation = onClickOrientation,
                                orientation = orientation,
                                onClickSettings = onClickSettings,
                                bookmarked = bookmarked,
                                onToggleBookmarked = onToggleBookmarked,
                                moreActions = readerMenuActions,
                            )
                        }
                    } else {
                        ClassicReaderButtons(
                            glass = null,
                            enabled = visible,
                            onClickChapterList = onClickChapterList,
                            onClickReadingMode = onClickReadingMode,
                            onClickOrientation = onClickOrientation,
                            orientation = orientation,
                            onClickSettings = onClickSettings,
                            bookmarked = bookmarked,
                            onToggleBookmarked = onToggleBookmarked,
                            moreActions = readerMenuActions,
                        )
                    }
                }
                ReaderBarStyle.Scrubber -> key(chapterTitle, viewer) {
                    ScrubberReaderBar(
                        glass = null,
                        enabled = visible,
                        isRtl = isRtl,
                        onPreviousChapter = onPreviousChapter,
                        enabledPrevious = enabledPrevious,
                        onNextChapter = onNextChapter,
                        enabledNext = enabledNext,
                        currentPage = currentPage,
                        totalPages = totalPages,
                        onPageIndexChange = onPageIndexChange,
                        onClickChapterList = onClickChapterList,
                        onClickReadingMode = onClickReadingMode,
                        onClickSettings = onClickSettings,
                        moreActions = readerMenuActions,
                    )
                }
                ReaderBarStyle.Capsule -> key(chapterTitle, viewer) {
                    CapsuleReaderBar(
                        glass = null,
                        enabled = visible,
                        isRtl = isRtl,
                        chapterTitle = chapterTitle.orEmpty(),
                        onPreviousChapter = onPreviousChapter,
                        enabledPrevious = enabledPrevious,
                        onNextChapter = onNextChapter,
                        enabledNext = enabledNext,
                        currentPage = currentPage,
                        totalPages = totalPages,
                        onPageIndexChange = onPageIndexChange,
                    )
                }
            }
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
