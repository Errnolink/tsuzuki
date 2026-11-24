package dev.errnolink.tsuzuki.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.errnolink.tsuzuki.designsystem.GlassContext
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.coroutines.flow.StateFlow

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
) {
    var suppressStatusLine by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        // edge glow rides over the page, under the chrome
        ReaderTurnGlow()
        ReaderAppBarsCurrent(
                visible = visible,
                glass = glass,
                mangaTitle = mangaTitle,
                chapterTitle = chapterTitle,
                navigateUp = navigateUp,
                onClickTopAppBar = onClickTopAppBar,
                bookmarked = bookmarked,
                onToggleBookmarked = onToggleBookmarked,
                onOpenInWebView = onOpenInWebView,
                onOpenInBrowser = onOpenInBrowser,
                onShare = onShare,
                onNextChapter = onNextChapter,
                enabledNext = enabledNext,
                onPreviousChapter = onPreviousChapter,
                enabledPrevious = enabledPrevious,
                currentPage = currentPage,
                totalPages = totalPages,
                onPageIndexChange = onPageIndexChange,
                readingMode = readingMode,
                onClickReadingMode = onClickReadingMode,
                orientation = orientation,
                onClickOrientation = onClickOrientation,
                cropEnabled = cropEnabled,
                onClickCropBorder = onClickCropBorder,
                onClickSettings = onClickSettings,
                isExhToolsVisible = isExhToolsVisible,
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
                navBarType = navBarType,
                currentPageText = currentPageText,
                enabledButtons = enabledButtons,
                currentReadingMode = currentReadingMode,
                dualPageSplitEnabled = dualPageSplitEnabled,
                doublePages = doublePages,
                onClickChapterList = onClickChapterList,
                onClickPageLayout = onClickPageLayout,
                onClickShiftPage = onClickShiftPage,
                barStyle = barStyle,
                prevChapterShortName = prevChapterShortName,
                nextChapterShortName = nextChapterShortName,
                currentChapterShortName = currentChapterShortName,
                pageProvider = pageProvider,
                isWebtoon = isWebtoon,
                webtoonProgressFlow = webtoonProgressFlow,
                onScrubWebtoonFraction = onScrubWebtoonFraction,
                onSaveCapturedPng = onSaveCapturedPng,
                onShareCapturedPng = onShareCapturedPng,
                onSuppressStatusLineChange = { suppressStatusLine = it },
            )
            // the status line takes the chrome's place while it is away (Kasane reader shell)
            ReaderStatusLine(
                visible = visible || suppressStatusLine,
                chapterTitle = chapterTitle,
                currentPage = currentPage,
                totalPages = totalPages,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
    }
}