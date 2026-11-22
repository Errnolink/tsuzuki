// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): org/nekomanga/presentation/screens/reader/viewer/WebtoonViewerConfigUiModel.kt
package dev.errnolink.tsuzuki.ui.reader.viewer

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.reader.engine.model.ChapterNavTarget
import dev.errnolink.tsuzuki.reader.engine.model.ReaderChapter
import dev.errnolink.tsuzuki.reader.engine.viewer.ViewerNavigation
import tachiyomi.domain.chapter.model.Chapter

/** Immutable UI configuration state for [ComposeWebtoonViewer]. */
@Immutable
data class WebtoonViewerConfigUiModel(
    val initialIndex: Int = 0,
    val activeChapterId: Long? = null,
    val backgroundColor: Color = Color.Transparent,
    val colorFilter: ColorFilter? = null,
    val contentPadding: PaddingValues = PaddingValues(bottom = 0.dp),
    val sidePadding: Dp = 0.dp,
    val sidePaddingPercent: Float = 0f,
    val hasGaps: Boolean = true,
    val enableZoomOut: Boolean = false,
    val doubleTapZoomEnabled: Boolean = true,
    val pinchToZoomEnabled: Boolean = true,
    val webtoonScaleRatio: Float = 0f,
    val automaticBackground: Boolean = false,
    val eInkMode: Boolean = false,
    val dualPageSplit: Boolean = false,
    val dualPageInvert: Boolean = false,
    val dualPageRotate: Boolean = false,
    val dualPageRotateReverse: Boolean = false,
    val animatedTransitions: Boolean = true,
    val doubleTapAnimDuration: Int = 300,
    val longTapEnabled: Boolean = true,
    val menuVisible: Boolean = false,
    val cropBorders: Boolean = false,
    val navigator: ViewerNavigation,
    val onToggleMenu: () -> Unit = {},
    val onTapTurn: ((forward: Boolean, xFraction: Float, yFraction: Float) -> Unit)? = null,
    val onRetryTransition: (ReaderChapter) -> Unit = {},
    val preloadPageAmount: Int = 4,
    val onNavigateToChapter: ((Chapter, ChapterNavTarget) -> Unit)? = null,
    val onRequestPreloadChapter: ((ReaderChapter) -> Unit)? = null,
)
