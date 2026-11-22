// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): org/nekomanga/presentation/screens/reader/viewer/PagerViewerConfigUiModel.kt
package dev.errnolink.tsuzuki.ui.reader.viewer

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import dev.errnolink.tsuzuki.reader.engine.model.ChapterNavTarget
import dev.errnolink.tsuzuki.reader.engine.model.ReaderChapter
import dev.errnolink.tsuzuki.reader.engine.model.ReaderPage
import dev.errnolink.tsuzuki.reader.engine.settings.PageLayout
import dev.errnolink.tsuzuki.reader.engine.viewer.ViewerNavigation
import dev.errnolink.tsuzuki.reader.engine.viewer.navigation.DisabledNavigation
import dev.errnolink.tsuzuki.reader.engine.viewer.pager.PagerPanDelegate
import tachiyomi.domain.chapter.model.Chapter

/** Immutable UI configuration state for [ComposePagerViewer]. */
@Immutable
data class PagerViewerConfigUiModel(
    val initialIndex: Int = 0,
    val activeChapterId: Long? = null,
    val backgroundColor: Color = Color.Black,
    val colorFilter: ColorFilter? = null,
    val isRtl: Boolean = false,
    val isVertical: Boolean = false,
    val animatedTransitions: Boolean = true,
    val imageScaleType: Int = 0,
    val pageLayout: PageLayout = PageLayout.SINGLE_PAGE,
    val doublePages: Boolean = false,
    val shiftDoublePage: Boolean = false,
    val invertDoublePages: Boolean = false,
    val doublePageGap: Int = 0,
    val zoomDoublePageSpreads: Boolean = false,
    val doublePageRotate: Boolean = false,
    val doublePageRotateReverse: Boolean = false,
    val zoomStart: Int = 0,
    val landscapeZoom: Boolean = false,
    val navigateToPan: Boolean = false,
    val doubleTapAnimDuration: Int = 300,
    val doubleTapZoomEnabled: Boolean = true,
    val disableZoomIn: Boolean = false,
    val landscapeZoomScaleDouble: Boolean = false,
    val centerMarginType: Int = 0,
    val automaticBackground: Boolean = false,
    val eInkMode: Boolean = false,
    val longTapEnabled: Boolean = true,
    val menuVisible: Boolean = false,
    val cropBorders: Boolean = false,
    val navigator: ViewerNavigation = DisabledNavigation(),
    val preloadPageAmount: Int = 4,
    val onToggleMenu: () -> Unit = {},
    val onNavigateAdjacent: (forward: Boolean) -> Unit = {},
    val onTapTurn: ((forward: Boolean, xFraction: Float, yFraction: Float) -> Unit)? = null,
    val onActivePanDelegateChanged: ((delegate: PagerPanDelegate?, active: Boolean) -> Unit)? =
        null,
    val onRetryTransition: (ReaderChapter) -> Unit = {},
    val onNavigateToChapter: ((Chapter, ChapterNavTarget) -> Unit)? = null,
    val onRequestPreloadChapter: ((ReaderChapter) -> Unit)? = null,
    val onPageLongTap: ((ReaderPage, ReaderPage?) -> Unit)? = null,
    val onWidePageDetected: ((ReaderPage) -> Unit)? = null,
)
