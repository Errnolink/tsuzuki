package dev.errnolink.tsuzuki.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.ArrowLeftRight
import dev.errnolink.tsuzuki.designsystem.Bookmark
import dev.errnolink.tsuzuki.designsystem.BookmarkFilled
import dev.errnolink.tsuzuki.designsystem.ChevronRight
import dev.errnolink.tsuzuki.designsystem.ContextMenuAction
import dev.errnolink.tsuzuki.designsystem.Crop
import dev.errnolink.tsuzuki.designsystem.Ellipsis
import dev.errnolink.tsuzuki.designsystem.GlassContext
import dev.errnolink.tsuzuki.designsystem.M3Motion
import dev.errnolink.tsuzuki.designsystem.GlassMenu
import dev.errnolink.tsuzuki.designsystem.GlassSurface
import dev.errnolink.tsuzuki.designsystem.Images
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import eu.kanade.tachiyomi.ui.reader.setting.ReaderBottomButton
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import kotlinx.collections.immutable.ImmutableSet

@Composable
fun rememberReaderMenuActions(
    enabledButtons: ImmutableSet<String>,
    onClickWebView: (() -> Unit)?,
    onClickBrowser: (() -> Unit)?,
    onClickShare: (() -> Unit)?,
    onClickReadingMode: () -> Unit,
    orientation: ReaderOrientation,
    onClickOrientation: () -> Unit,
    cropEnabled: Boolean,
    onClickCropBorder: () -> Unit,
    currentReadingMode: ReadingMode,
    dualPageSplitEnabled: Boolean,
    onClickPageLayout: () -> Unit,
    doublePages: Boolean,
    onClickShiftPage: () -> Unit,
): List<ContextMenuAction> {
    return remember(
        enabledButtons,
        onClickWebView,
        onClickBrowser,
        onClickShare,
        onClickReadingMode,
        orientation,
        onClickOrientation,
        cropEnabled,
        onClickCropBorder,
        currentReadingMode,
        dualPageSplitEnabled,
        onClickPageLayout,
        doublePages,
        onClickShiftPage,
    ) {
        buildList {
            if (ReaderBottomButton.ReadingMode.isIn(enabledButtons)) add(readerMenuAction("Reading mode", dev.errnolink.tsuzuki.designsystem.TsuzukiIcons.Book, onClickReadingMode, group = MenuLayoutGroup))
            if (ReaderBottomButton.Rotation.isIn(enabledButtons)) add(readerMenuAction("Orientation", orientation.icon, onClickOrientation, group = MenuLayoutGroup))
            if (ReaderBottomButton.WebView.isIn(enabledButtons)) onClickWebView?.let { add(readerMenuAction("Open in web view", dev.errnolink.tsuzuki.designsystem.TsuzukiIcons.Globe, it)) }
            if (ReaderBottomButton.Browser.isIn(enabledButtons)) onClickBrowser?.let { add(readerMenuAction("Open in browser", dev.errnolink.tsuzuki.designsystem.TsuzukiIcons.Browse, it)) }
            if (ReaderBottomButton.Share.isIn(enabledButtons)) onClickShare?.let { add(readerMenuAction("Share", dev.errnolink.tsuzuki.designsystem.TsuzukiIcons.Share, it)) }
            val cropButton = when (currentReadingMode) {
                ReadingMode.WEBTOON -> ReaderBottomButton.CropBordersWebtoon
                ReadingMode.CONTINUOUS_VERTICAL -> ReaderBottomButton.CropBordersContinuesVertical
                else -> ReaderBottomButton.CropBordersPager
            }
            if (cropButton.isIn(enabledButtons)) add(readerMenuAction(if (cropEnabled) "Keep page borders" else "Crop page borders", TsuzukiIcons.Crop, onClickCropBorder))
            if (!dualPageSplitEnabled && ReaderBottomButton.PageLayout.isIn(enabledButtons) && ReadingMode.isPagerType(currentReadingMode.flagValue)) {
                add(readerMenuAction("Page layout", TsuzukiIcons.Images, onClickPageLayout))
            }
            if (doublePages) add(readerMenuAction("Shift double pages", TsuzukiIcons.ArrowLeftRight, onClickShiftPage))
        }
    }
}

internal const val MenuLayoutGroup = 1

private fun readerMenuAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    group: Int = 0,
) = ContextMenuAction(
    label,
    icon = { Icon(icon, null, tint = TsuzukiTheme.colors.text) },
    group = group,
    onClick = onClick,
)

@Composable
fun ClassicReaderBar(
    glass: GlassContext?,
    enabled: Boolean,
    isRtl: Boolean,
    onPreviousChapter: () -> Unit,
    enabledPrevious: Boolean,
    onNextChapter: () -> Unit,
    enabledNext: Boolean,
    currentPage: Int,
    totalPages: Int,
    onPageIndexChange: (Int) -> Unit,
    onClickChapterList: () -> Unit,
    onClickReadingMode: () -> Unit,
    onClickOrientation: () -> Unit,
    orientation: ReaderOrientation,
    onClickSettings: () -> Unit,
    bookmarked: Boolean,
    onToggleBookmarked: (() -> Unit)?,
    moreActions: List<ContextMenuAction>,
    modifier: Modifier = Modifier,
) {
    var preview by remember { mutableStateOf<Int?>(null) }
    var jump by remember { mutableStateOf(false) }
    val shown = preview ?: currentPage
    val haptics = TsuzukiTheme.haptics
    val leftArrow = resolveLeftArrow(isRtl, enabledPrevious, enabledNext)
    val rightArrow = resolveRightArrow(isRtl, enabledPrevious, enabledNext)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (totalPages > 1) {
                GlassSurface(glass, Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(28.dp)) {
                        Row(Modifier.align(Alignment.Center).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(48.dp).clickable(
                                    enabled = enabled && totalPages > 0,
                                    role = Role.Button,
                                    onClickLabel = "Go to page",
                                ) { jump = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    Modifier.size(40.dp).clip(CircleShape)
                                        .background(TsuzukiTheme.colors.elevated),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    TsuzukiText(
                                        shown.toString(),
                                        style = TsuzukiTheme.typography.subhead.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                                        maxLines = 1,
                                    )
                                }
                            }
                            ReaderSlider(
                                currentPage,
                                1..totalPages.coerceAtLeast(1),
                                { onPageIndexChange(it - 1) },
                                Modifier.weight(1f),
                                reversed = isRtl,
                                enabled = enabled && totalPages > 1,
                                onPreview = { preview = it },
                                style = ReaderSliderStyle.Playhead,
                            )
                            TsuzukiText(
                                totalPages.toString(),
                                Modifier.padding(start = 8.dp),
                                TsuzukiTheme.typography.subhead,
                                TsuzukiTheme.colors.secondary,
                            )
                        }
                }
            }
            ClassicReaderButtons(
                glass = glass,
                enabled = enabled,
                onClickChapterList = onClickChapterList,
                onClickReadingMode = onClickReadingMode,
                onClickOrientation = onClickOrientation,
                orientation = orientation,
                onClickSettings = onClickSettings,
                bookmarked = bookmarked,
                onToggleBookmarked = onToggleBookmarked,
                moreActions = moreActions,
                leading = {
                    GlassCircle(
                        leftArrow.contentDescription,
                        TsuzukiIcons.ChevronRight,
                        { haptics.chapterArm(); if (leftArrow.isNext) onNextChapter() else onPreviousChapter() },
                        48.dp,
                        enabled && leftArrow.enabled,
                        glass,
                        flip = true,
                    )
                },
                trailing = {
                    GlassCircle(
                        rightArrow.contentDescription,
                        TsuzukiIcons.ChevronRight,
                        { haptics.chapterArm(); if (rightArrow.isNext) onNextChapter() else onPreviousChapter() },
                        48.dp,
                        enabled && rightArrow.enabled,
                        glass,
                        flip = false,
                    )
                },
            )
        }
    }
    GoToPageSheet(jump, currentPage, totalPages, onPageIndexChange) { jump = false }
}

@Composable
fun ClassicReaderButtons(
    glass: GlassContext?,
    enabled: Boolean,
    onClickChapterList: () -> Unit,
    onClickReadingMode: () -> Unit,
    onClickOrientation: () -> Unit,
    orientation: ReaderOrientation,
    onClickSettings: () -> Unit,
    bookmarked: Boolean,
    onToggleBookmarked: (() -> Unit)?,
    moreActions: List<ContextMenuAction>,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    val menuActions = remember(moreActions, bookmarked, onToggleBookmarked, onClickReadingMode, onClickOrientation, orientation) {
        buildList {
            add(readerMenuAction("Reading mode", TsuzukiIcons.Book, onClickReadingMode, group = MenuLayoutGroup))
            add(readerMenuAction("Orientation", orientation.icon, onClickOrientation, group = MenuLayoutGroup))
            if (onToggleBookmarked != null) {
                add(
                    ContextMenuAction(
                        if (bookmarked) "Bookmarked" else "Bookmark",
                        icon = { Icon(if (bookmarked) TsuzukiIcons.BookmarkFilled else TsuzukiIcons.Bookmark, null, tint = TsuzukiTheme.colors.text) },
                        onClick = onToggleBookmarked,
                    )
                )
            }
            addAll(moreActions.dropWhile { it.group == MenuLayoutGroup })
        }
    }
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = if (leading != null || trailing != null) {
            Arrangement.SpaceBetween
        } else {
            Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        GlassCircle("Chapters", TsuzukiIcons.Chapters, onClickChapterList, 48.dp, enabled, glass)
        GlassCircle("Reading settings", TsuzukiIcons.Settings, onClickSettings, 48.dp, enabled, glass)
        Box {
            GlassCircle("More reading actions", TsuzukiIcons.Ellipsis, { menu = true }, 48.dp, enabled, glass)
            GlassMenu(menu && enabled, "Reading actions", menuActions, { menu = false })
        }
        trailing?.invoke()
    }
}
