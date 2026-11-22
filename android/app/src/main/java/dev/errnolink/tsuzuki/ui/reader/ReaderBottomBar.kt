package dev.errnolink.tsuzuki.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.errnolink.tsuzuki.designsystem.ArrowLeftRight
import dev.errnolink.tsuzuki.designsystem.CapsuleRestRadius
import dev.errnolink.tsuzuki.designsystem.ChevronLeft
import dev.errnolink.tsuzuki.designsystem.ChevronRight
import dev.errnolink.tsuzuki.designsystem.ContextMenuAction
import dev.errnolink.tsuzuki.designsystem.Crop
import dev.errnolink.tsuzuki.designsystem.GlassContext
import dev.errnolink.tsuzuki.designsystem.GlassSurface
import dev.errnolink.tsuzuki.designsystem.Images
import dev.errnolink.tsuzuki.designsystem.LayoutGrid
import dev.errnolink.tsuzuki.designsystem.MenuMorphSource
import dev.errnolink.tsuzuki.designsystem.Sun
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.X
import dev.errnolink.tsuzuki.designsystem.menuMorphSource
import dev.errnolink.tsuzuki.designsystem.pressScale
import dev.errnolink.tsuzuki.designsystem.pressShapeScale
import dev.errnolink.tsuzuki.designsystem.rememberPressShapeState
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReaderBottomButton
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.coroutines.flow.StateFlow
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

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
    val labels = ReaderMenuWords(
        readingMode = stringResource(MR.strings.viewer),
        orientation = stringResource(MR.strings.rotation_type),
        openInWebView = stringResource(MR.strings.action_open_in_web_view),
        openInBrowser = stringResource(MR.strings.action_open_in_browser),
        share = stringResource(MR.strings.action_share),
        keepPageBorders = stringResource(MR.strings.tsuzuki_keep_page_borders),
        cropPageBorders = stringResource(MR.strings.tsuzuki_crop_page_borders),
        pageLayout = stringResource(MR.strings.tsuzuki_page_layout),
        shiftDoublePages = stringResource(MR.strings.tsuzuki_shift_double_pages),
    )
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
        labels,
    ) {
        buildList {
            if (ReaderBottomButton.ReadingMode.isIn(enabledButtons)) add(readerMenuAction(labels.readingMode, TsuzukiIcons.Book, onClickReadingMode, group = MenuLayoutGroup))
            if (ReaderBottomButton.Rotation.isIn(enabledButtons)) add(readerMenuAction(labels.orientation, orientation.icon, onClickOrientation, group = MenuLayoutGroup))
            if (ReaderBottomButton.WebView.isIn(enabledButtons)) onClickWebView?.let { add(readerMenuAction(labels.openInWebView, TsuzukiIcons.Globe, it)) }
            if (ReaderBottomButton.Browser.isIn(enabledButtons)) onClickBrowser?.let { add(readerMenuAction(labels.openInBrowser, TsuzukiIcons.Browse, it)) }
            if (ReaderBottomButton.Share.isIn(enabledButtons)) onClickShare?.let { add(readerMenuAction(labels.share, TsuzukiIcons.Share, it)) }
            val cropButton = when (currentReadingMode) {
                ReadingMode.WEBTOON -> ReaderBottomButton.CropBordersWebtoon
                ReadingMode.CONTINUOUS_VERTICAL -> ReaderBottomButton.CropBordersContinuesVertical
                else -> ReaderBottomButton.CropBordersPager
            }
            if (cropButton.isIn(enabledButtons)) add(readerMenuAction(if (cropEnabled) labels.keepPageBorders else labels.cropPageBorders, TsuzukiIcons.Crop, onClickCropBorder))
            if (!dualPageSplitEnabled && ReaderBottomButton.PageLayout.isIn(enabledButtons) && ReadingMode.isPagerType(currentReadingMode.flagValue)) {
                add(readerMenuAction(labels.pageLayout, TsuzukiIcons.Images, onClickPageLayout))
            }
            if (doublePages) add(readerMenuAction(labels.shiftDoublePages, TsuzukiIcons.ArrowLeftRight, onClickShiftPage))
        }
    }
}

@Immutable
private data class ReaderMenuWords(
    val readingMode: String,
    val orientation: String,
    val openInWebView: String,
    val openInBrowser: String,
    val share: String,
    val keepPageBorders: String,
    val cropPageBorders: String,
    val pageLayout: String,
    val shiftDoublePages: String,
)

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

internal val ReaderChromeHeight = 44.dp
private val ReaderBarHeight = 56.dp
private val ReaderBarInnerCircle = 38.dp

/**
 * Floating `"Return to p.N"` glass chip rendered above the dock after jumping `>= 3` pages.
 */
@Composable
internal fun ReturnToOriginChip(
    visible: Boolean,
    originPage: Int?,
    glass: GlassContext?,
    onReturn: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (originPage == null) return
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        val colors = TsuzukiTheme.colors
        val pillShape = RoundedCornerShape(999.dp)
        GlassSurface(glass, shape = pillShape) {
            Row(
                Modifier.padding(start = 12.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier
                        .clip(pillShape)
                        .clickable(role = Role.Button, onClick = onReturn)
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(ReturnOriginPinColor))
                    TsuzukiText(
                        stringResource(MR.strings.tsuzuki_reader_return_to_page, originPage),
                        style = TsuzukiTheme.typography.caption1.copy(
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum",
                        ),
                    )
                }
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(colors.text.copy(alpha = 0.10f))
                        .clickable(
                            role = Role.Button,
                            onClickLabel = stringResource(MR.strings.tsuzuki_dismiss),
                            onClick = onDismiss,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(TsuzukiIcons.X, stringResource(MR.strings.tsuzuki_dismiss), Modifier.size(12.dp), tint = colors.secondary)
                }
            }
        }
    }
}

/**
 * Reader UI v3 unified Liquid Glass bottom dock:
 * Collapsible Filmstrip / Storyboard tray + RTL-aware flanking `<`/`>` chapter steppers +
 * Scrubber with origin dot + hairline divider + 5-action dock (Chapters, Overview, Lighting, Capture, Settings).
 */
@Composable
fun ClassicReaderBar(
    glass: GlassContext?,
    enabled: Boolean,
    isRtl: Boolean,
    isWebtoon: Boolean,
    filmstripExpanded: Boolean,
    onFilmstripExpandedChange: (Boolean) -> Unit,
    onPreviousChapter: () -> Unit,
    enabledPrevious: Boolean,
    onNextChapter: () -> Unit,
    enabledNext: Boolean,
    currentPage: Int,
    totalPages: Int,
    originPage: Int?,
    onJumpPageIndex: (Int) -> Unit,
    webtoonProgressFlow: StateFlow<WebtoonProgressState>?,
    onScrubWebtoonFraction: ((Float) -> Unit)?,
    onClickChapterList: () -> Unit,
    onClickOverview: () -> Unit,
    onClickLighting: () -> Unit,
    onClickCapture: () -> Unit,
    onClickSettings: () -> Unit,
    overviewActive: Boolean = false,
    lightingActive: Boolean = false,
    modifier: Modifier = Modifier,
    pageProvider: (Int) -> ReaderPage? = { null },
) {
    var preview by remember { mutableStateOf<Int?>(null) }
    var heldPreview by remember { mutableStateOf<Int?>(null) }
    if (preview != null) heldPreview = preview
    val scrubTrack = remember { ScrubTrack() }
    val haptics = TsuzukiTheme.haptics
    val leftArrow = resolveLeftArrow(
        isRtl,
        enabledPrevious,
        enabledNext,
        stringResource(MR.strings.action_previous_chapter),
        stringResource(MR.strings.action_next_chapter),
    )
    val rightArrow = resolveRightArrow(
        isRtl,
        enabledPrevious,
        enabledNext,
        stringResource(MR.strings.action_previous_chapter),
        stringResource(MR.strings.action_next_chapter),
    )

    val upperShape = if (filmstripExpanded) {
        RoundedCornerShape(TsuzukiCorners.menu)
    } else {
        RoundedCornerShape(TsuzukiCorners.capsulePercent)
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.fillMaxWidth()) {
                if (totalPages > 1) {
                    ReaderScrubBubble(
                        visible = preview != null,
                        page = heldPreview,
                        totalPages = totalPages.coerceAtLeast(1),
                        rtl = isRtl,
                        pageAt = pageProvider,
                        track = scrubTrack,
                    )
                }
                GlassSurface(glass, Modifier.fillMaxWidth(), shape = upperShape) {
                    Box(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                        ) {
                            ReaderFilmstripTray(
                                expanded = filmstripExpanded,
                                onExpandedChange = onFilmstripExpandedChange,
                                isWebtoon = isWebtoon,
                                isRtl = isRtl,
                                currentPage = currentPage,
                                totalPages = totalPages,
                                pageProvider = pageProvider,
                                onSelectPage = onJumpPageIndex,
                                webtoonProgressFlow = webtoonProgressFlow,
                                onScrubWebtoonFraction = onScrubWebtoonFraction,
                                showHandle = false,
                                trayTopPadding = 16.dp,
                            )

                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(if (filmstripExpanded) 48.dp else 56.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().height(40.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    BarArrow(
                                        leftArrow.contentDescription,
                                        TsuzukiIcons.ChevronLeft,
                                        enabled && leftArrow.enabled,
                                    ) {
                                        haptics.chapterArm()
                                        if (leftArrow.isNext) onNextChapter() else onPreviousChapter()
                                    }
                                    if (totalPages > 1) {
                                        ReaderScrubber(
                                            page = currentPage,
                                            totalPages = totalPages.coerceAtLeast(1),
                                            rtl = isRtl,
                                            enabled = enabled,
                                            originPage = originPage,
                                            onPageIndexChange = onJumpPageIndex,
                                            modifier = Modifier.weight(1f),
                                            onPreview = { preview = it },
                                            track = scrubTrack,
                                        )
                                    } else {
                                        Spacer(Modifier.weight(1f))
                                    }
                                    BarArrow(
                                        rightArrow.contentDescription,
                                        TsuzukiIcons.ChevronRight,
                                        enabled && rightArrow.enabled,
                                    ) {
                                        haptics.chapterArm()
                                        if (rightArrow.isNext) onNextChapter() else onPreviousChapter()
                                    }
                                }
                            }
                        }

                        if (totalPages > 1) {
                            FilmstripPullHandle(
                                expanded = filmstripExpanded,
                                onExpandedChange = onFilmstripExpandedChange,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 5.dp),
                            )
                        }
                    }
                }
            }

            ClassicReaderButtons(
                glass = glass,
                enabled = enabled,
                onClickChapterList = onClickChapterList,
                onClickOverview = onClickOverview,
                onClickLighting = onClickLighting,
                onClickCapture = onClickCapture,
                onClickSettings = onClickSettings,
                overviewActive = overviewActive,
                lightingActive = lightingActive,
            )
        }
    }
}

/**
 * Floating Liquid Glass pill hosting [ReaderFilmstripTray] above [ClassicReaderButtons] when
 * vertical-seekbar Webtoon mode is active so the Storyboard Ribbon remains reachable.
 */
@Composable
internal fun WebtoonStoryboardPill(
    glass: GlassContext?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    isRtl: Boolean,
    currentPage: Int,
    totalPages: Int,
    pageProvider: (Int) -> ReaderPage?,
    onSelectPage: (Int) -> Unit,
    webtoonProgressFlow: StateFlow<WebtoonProgressState>?,
    onScrubWebtoonFraction: ((Float) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val pillShape = if (expanded) {
        RoundedCornerShape(TsuzukiCorners.menu)
    } else {
        RoundedCornerShape(TsuzukiCorners.capsulePercent)
    }
    GlassSurface(glass, modifier.fillMaxWidth(), shape = pillShape) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
            ReaderFilmstripTray(
                expanded = expanded,
                onExpandedChange = onExpandedChange,
                isWebtoon = true,
                isRtl = isRtl,
                currentPage = currentPage,
                totalPages = totalPages,
                pageProvider = pageProvider,
                onSelectPage = onSelectPage,
                webtoonProgressFlow = webtoonProgressFlow,
                onScrubWebtoonFraction = onScrubWebtoonFraction,
            )
        }
    }
}

/** Standalone glass dock capsule for 5 reader actions (Chapters, Overview, Lighting, Capture, Settings). */
@Composable
fun ClassicReaderButtons(
    glass: GlassContext?,
    enabled: Boolean,
    onClickChapterList: () -> Unit,
    onClickOverview: () -> Unit,
    onClickLighting: () -> Unit,
    onClickCapture: () -> Unit,
    onClickSettings: () -> Unit,
    overviewActive: Boolean = false,
    lightingActive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    GlassSurface(glass, modifier.fillMaxWidth(), shape = RoundedCornerShape(TsuzukiCorners.capsulePercent)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
            ReaderDockActionsRow(
                enabled = enabled,
                onClickChapterList = onClickChapterList,
                onClickOverview = onClickOverview,
                onClickLighting = onClickLighting,
                onClickCapture = onClickCapture,
                onClickSettings = onClickSettings,
                overviewActive = overviewActive,
                lightingActive = lightingActive,
            )
        }
    }
}

@Composable
internal fun ReaderDockActionsRow(
    enabled: Boolean,
    onClickChapterList: () -> Unit,
    onClickOverview: () -> Unit,
    onClickLighting: () -> Unit,
    onClickCapture: () -> Unit,
    onClickSettings: () -> Unit,
    overviewActive: Boolean = false,
    lightingActive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val morphSources = LocalReaderMorphSources.current
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DockActionButton(
            label = stringResource(MR.strings.chapters),
            icon = TsuzukiIcons.Chapters,
            enabled = enabled,
            active = false,
            onClick = {
                morphSources?.let { it.activeChaptersSource = it.chaptersDock }
                onClickChapterList()
            },
            modifier = Modifier.weight(1f),
            morphSource = morphSources?.chaptersDock,
        )
        DockActionButton(
            label = stringResource(MR.strings.tsuzuki_reader_overview),
            icon = TsuzukiIcons.LayoutGrid,
            enabled = enabled,
            active = overviewActive,
            onClick = onClickOverview,
            modifier = Modifier.weight(1f),
        )
        DockActionButton(
            label = stringResource(MR.strings.tsuzuki_reader_lighting),
            icon = TsuzukiIcons.Sun,
            enabled = enabled,
            active = lightingActive,
            onClick = onClickLighting,
            modifier = Modifier.weight(1f),
            morphSource = morphSources?.lighting,
        )
        DockActionButton(
            label = stringResource(MR.strings.tsuzuki_reader_capture),
            icon = TsuzukiIcons.Crop,
            enabled = enabled,
            active = false,
            onClick = onClickCapture,
            modifier = Modifier.weight(1f),
        )
        DockActionButton(
            label = stringResource(MR.strings.tsuzuki_reader_settings_short),
            icon = TsuzukiIcons.Settings,
            enabled = enabled,
            active = false,
            onClick = onClickSettings,
            modifier = Modifier.weight(1f),
            morphSource = morphSources?.settings,
        )
    }
}

@Composable
private fun DockActionButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    morphSource: MenuMorphSource? = null,
) {
    val colors = TsuzukiTheme.colors
    val shape = RoundedCornerShape(TsuzukiCorners.capsulePercent)
    val interaction = remember { MutableInteractionSource() }
    val contentColor = when {
        !enabled -> colors.text.copy(alpha = 0.35f)
        active -> colors.accent
        else -> colors.text
    }
    Column(
        modifier
            .height(48.dp)
            .then(if (morphSource != null) Modifier.menuMorphSource(morphSource) else Modifier)
            .pressScale(interaction)
            .clip(shape)
            .background(if (active) colors.accent.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, Modifier.size(19.dp), tint = contentColor)
        TsuzukiText(
            label,
            style = TsuzukiTheme.typography.caption2.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BarArrow(label: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressState = rememberPressShapeState(interaction, restRadius = CapsuleRestRadius)
    Box(
        Modifier
            .size(ReaderBarInnerCircle)
            .then(if (pressState.enabled) Modifier.pressShapeScale(pressState) else Modifier.pressScale(interaction))
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            label,
            Modifier.size(20.dp),
            tint = TsuzukiTheme.colors.text.copy(alpha = if (enabled) 1f else 0.3f),
        )
    }
}
