package dev.errnolink.tsuzuki.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.ContextMenuAction
import dev.errnolink.tsuzuki.designsystem.GlassContext
import dev.errnolink.tsuzuki.designsystem.GlassMenu
import dev.errnolink.tsuzuki.designsystem.GlassSurface
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.BookOpen
import dev.errnolink.tsuzuki.designsystem.ChevronRight
import dev.errnolink.tsuzuki.designsystem.Ellipsis
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiSize
import dev.errnolink.tsuzuki.ui.details.DetailIcon
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun GlassCircle(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    size: Dp,
    enabled: Boolean,
    glass: GlassContext?,
    flip: Boolean = false,
) {
    GlassSurface(glass, Modifier.size(size), shape = CircleShape) {
        DetailIcon(label, icon, onClick, Modifier.alpha(if (enabled) 1f else 0.3f), enabled, flip = flip)
    }
}

@Composable
fun ScrubberReaderBar(
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
    onClickSettings: () -> Unit,
    moreActions: List<ContextMenuAction>,
    modifier: Modifier = Modifier,
    pageProvider: (Int) -> ReaderPage? = { null },
) {
    var preview by remember { mutableStateOf<Int?>(null) }
    var jump by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val shown = preview ?: currentPage
    val tabular = TsuzukiTheme.typography.headline.copy(fontFeatureSettings = "tnum")
    val previousChapterLabel = stringResource(MR.strings.action_previous_chapter)
    val nextChapterLabel = stringResource(MR.strings.action_next_chapter)
    val leftArrow = resolveLeftArrow(isRtl, enabledPrevious, enabledNext, previousChapterLabel, nextChapterLabel)
    val rightArrow = resolveRightArrow(isRtl, enabledPrevious, enabledNext, previousChapterLabel, nextChapterLabel)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            var heldPreview by remember { mutableStateOf<Int?>(null) }
            if (preview != null) heldPreview = preview
            val scrubTrack = remember { ScrubTrack() }
            // While scrubbing, the Kasane bubble (thumbnail, page, "of N") floats 12 dp above the pill.
            Box(Modifier.fillMaxWidth()) {
                ReaderScrubBubble(
                    visible = preview != null,
                    page = heldPreview,
                    totalPages = totalPages.coerceAtLeast(1),
                    rtl = isRtl,
                    pageAt = pageProvider,
                    track = scrubTrack,
                )
                GlassSurface(glass, Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(TsuzukiCorners.capsulePercent)) {
                Row(Modifier.align(Alignment.Center).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(TsuzukiSize.touch).clip(CircleShape)
                            .background(TsuzukiTheme.colors.text.copy(alpha = 0.10f))
                            .clickable(enabled = enabled && totalPages > 0, role = Role.Button, onClickLabel = stringResource(MR.strings.tsuzuki_go_to_page)) { jump = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        TsuzukiText(shown.toString(), style = tabular, maxLines = 1)
                    }
                    ReaderScrubber(
                        page = currentPage,
                        totalPages = totalPages.coerceAtLeast(1),
                        rtl = isRtl,
                        enabled = enabled && totalPages > 1,
                        onPageIndexChange = onPageIndexChange,
                        modifier = Modifier.weight(1f),
                        onPreview = { preview = it },
                        track = scrubTrack,
                    )
                    TsuzukiText(
                        totalPages.toString(),
                        Modifier.width(48.dp).padding(start = 12.dp),
                        TsuzukiTheme.typography.subhead,
                        TsuzukiTheme.colors.secondary,
                    )
                }
                }
            }
            BoxWithConstraints(Modifier.padding(top = 16.dp)) {
                val targetTotalWidth = 48.dp * 6 + 14.dp * 5
                val spacing = if (maxWidth < targetTotalWidth) {
                    ((maxWidth - 48.dp * 6) / 5).coerceAtLeast(2.dp)
                } else {
                    14.dp
                }
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
                ) {
                    GlassCircle(
                        leftArrow.contentDescription,
                        TsuzukiIcons.ChevronRight,
                        if (leftArrow.isNext) onNextChapter else onPreviousChapter,
                        48.dp,
                        enabled && leftArrow.enabled,
                        glass,
                        flip = true,
                    )
                    GlassCircle(stringResource(MR.strings.chapters), TsuzukiIcons.Chapters, onClickChapterList, 48.dp, enabled, glass)
                    GlassCircle(stringResource(MR.strings.viewer), TsuzukiIcons.BookOpen, onClickReadingMode, 48.dp, enabled, glass)
                    GlassCircle(stringResource(MR.strings.tsuzuki_reading_settings), TsuzukiIcons.Settings, onClickSettings, 48.dp, enabled, glass)
                    Box {
                        GlassCircle(
                            stringResource(MR.strings.tsuzuki_more_reading_actions),
                            TsuzukiIcons.Ellipsis,
                            { if (moreActions.isNotEmpty()) menu = true },
                            48.dp,
                            enabled && moreActions.isNotEmpty(),
                            glass,
                        )
                        if (moreActions.isNotEmpty()) {
                            GlassMenu(menu && enabled, stringResource(MR.strings.tsuzuki_reading_actions), moreActions, { menu = false })
                        }
                    }
                    GlassCircle(
                        rightArrow.contentDescription,
                        TsuzukiIcons.ChevronRight,
                        if (rightArrow.isNext) onNextChapter else onPreviousChapter,
                        48.dp,
                        enabled && rightArrow.enabled,
                        glass,
                        flip = false,
                    )
                }
            }
        }
    }
    GoToPageSheet(jump, currentPage, totalPages, onPageIndexChange) { jump = false }
}

@Composable
fun CapsuleReaderBar(
    glass: GlassContext?,
    enabled: Boolean,
    isRtl: Boolean,
    chapterTitle: String,
    onPreviousChapter: () -> Unit,
    enabledPrevious: Boolean,
    onNextChapter: () -> Unit,
    enabledNext: Boolean,
    currentPage: Int,
    totalPages: Int,
    onPageIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    pageProvider: (Int) -> ReaderPage? = { null },
) {
    var preview by remember { mutableStateOf<Int?>(null) }
    var heldPreview by remember { mutableStateOf<Int?>(null) }
    if (preview != null) heldPreview = preview
    val scrubTrack = remember { ScrubTrack() }
    var jump by remember { mutableStateOf(false) }
    val shown = preview ?: currentPage
    val leftArrow = resolveLeftArrow(isRtl, enabledPrevious, enabledNext, stringResource(MR.strings.action_previous_chapter), stringResource(MR.strings.action_next_chapter))
    val rightArrow = resolveRightArrow(isRtl, enabledPrevious, enabledNext, stringResource(MR.strings.action_previous_chapter), stringResource(MR.strings.action_next_chapter))

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth()) {
                // Kasane preview bubble above the capsule while scrubbing
                ReaderScrubBubble(
                    visible = preview != null,
                    page = heldPreview,
                    totalPages = totalPages.coerceAtLeast(1),
                    rtl = isRtl,
                    pageAt = pageProvider,
                    track = scrubTrack,
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassCircle(
                    leftArrow.contentDescription,
                    TsuzukiIcons.ChevronRight,
                    if (leftArrow.isNext) onNextChapter else onPreviousChapter,
                    58.dp,
                    enabled && leftArrow.enabled,
                    glass,
                    flip = true,
                )
                GlassSurface(glass, Modifier.weight(1f).height(58.dp), shape = RoundedCornerShape(TsuzukiCorners.capsulePercent)) {
                    Column(Modifier.align(Alignment.Center).padding(horizontal = 18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TsuzukiText(
                                chapterTitle,
                                Modifier.weight(1f),
                                TsuzukiTheme.typography.footnote.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TsuzukiText(
                                formatPageCountText(shown, totalPages),
                                Modifier.clickable(enabled = enabled && totalPages > 0, role = Role.Button, onClickLabel = stringResource(MR.strings.tsuzuki_go_to_page)) { jump = true }
                                    .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                                TsuzukiTheme.typography.caption1.copy(fontFeatureSettings = "tnum"),
                                TsuzukiTheme.colors.secondary,
                            )
                        }
                        ReaderScrubber(
                            page = currentPage,
                            totalPages = totalPages.coerceAtLeast(1),
                            rtl = isRtl,
                            enabled = enabled && totalPages > 1,
                            onPageIndexChange = onPageIndexChange,
                            onPreview = { preview = it },
                            track = scrubTrack,
                        )
                    }
                }
                GlassCircle(
                    rightArrow.contentDescription,
                    TsuzukiIcons.ChevronRight,
                    if (rightArrow.isNext) onNextChapter else onPreviousChapter,
                    58.dp,
                    enabled && rightArrow.enabled,
                    glass,
                    flip = false,
                )
                }
            }
        }
    }
    GoToPageSheet(jump, currentPage, totalPages, onPageIndexChange) { jump = false }
}
