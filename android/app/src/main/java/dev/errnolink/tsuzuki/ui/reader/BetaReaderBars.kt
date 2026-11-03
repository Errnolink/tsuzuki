package dev.errnolink.tsuzuki.ui.reader

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.ContextMenuAction
import dev.errnolink.tsuzuki.designsystem.GlassContext
import dev.errnolink.tsuzuki.designsystem.GlassMenu
import dev.errnolink.tsuzuki.designsystem.GlassSurface
import dev.errnolink.tsuzuki.designsystem.M3Motion
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.BookOpen
import dev.errnolink.tsuzuki.designsystem.ChevronRight
import dev.errnolink.tsuzuki.designsystem.Ellipsis
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.ui.details.DetailIcon
import kotlin.math.roundToInt

enum class TrackStyle {
    Line,
    Capsule,
}

@Composable
internal fun ReaderTrackSlider(
    value: Int,
    valueRange: IntRange,
    onCommit: (Int) -> Unit,
    style: TrackStyle,
    modifier: Modifier = Modifier,
    reversed: Boolean = false,
    enabled: Boolean = true,
    onPreview: (Int?) -> Unit = {},
) {
    val colors = TsuzukiTheme.colors
    val motion = TsuzukiTheme.motion
    val haptics = TsuzukiTheme.haptics
    var preview by remember(valueRange) { mutableStateOf<Int?>(null) }
    val currentValue by rememberUpdatedState(value.coerceIn(valueRange))
    val commit by rememberUpdatedState(onCommit)
    val previewChanged by rememberUpdatedState(onPreview)
    val available = enabled && valueRange.last > valueRange.first
    val displayed = preview ?: currentValue
    val dragging = preview != null
    val spec = if (motion.reduced) snap<Dp>() else tween(140)
    val rail by animateDpAsState(
        when (style) { TrackStyle.Line -> 6.dp; TrackStyle.Capsule -> if (dragging) 10.dp else 4.dp },
        spec,
        label = "rail",
    )
    val thumbLen by animateDpAsState(if (dragging) 40.dp else 30.dp, spec, label = "thumb")
    val dot by animateFloatAsState(if (dragging) 0f else 1f, if (motion.reduced) snap() else tween(140), label = "dot")
    val pad = when (style) { TrackStyle.Line -> 8.dp; TrackStyle.Capsule -> 6.dp }

    fun setPreview(next: Int) {
        val changed = next != (preview ?: currentValue)
        preview = next
        if (changed) {
            haptics.pageTurn()
            previewChanged(next)
        }
    }

    Canvas(
        modifier.fillMaxWidth().height(if (style == TrackStyle.Line) 48.dp else 22.dp)
            .semantics {
                contentDescription = "Page"
                stateDescription = "Page $displayed of ${valueRange.last}"
                progressBarRangeInfo = ProgressBarRangeInfo(
                    displayed.toFloat(),
                    valueRange.first.toFloat()..valueRange.last.toFloat(),
                    (valueRange.last - valueRange.first - 1).coerceAtLeast(0),
                )
                if (!available) disabled()
                setProgress { target -> if (available) commit(target.roundToInt().coerceIn(valueRange)); available }
            }
            .onKeyEvent { event ->
                if (!available || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val delta = when (event.key) {
                    Key.DirectionLeft -> if (reversed) 1 else -1
                    Key.DirectionRight -> if (reversed) -1 else 1
                    else -> return@onKeyEvent false
                }
                commit((currentValue + delta).coerceIn(valueRange))
                true
            }
            .focusable(available)
            .pointerInput(valueRange, reversed, available) {
                if (!available) return@pointerInput
                val p = pad.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    try {
                        setPreview(calculateSliderValueAt(down.position.x, size.width.toFloat(), p, valueRange, reversed))
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed || event.changes.any { it.id != down.id && it.pressed }) break
                            if (!change.pressed) {
                                commit(preview ?: currentValue)
                                change.consume()
                                break
                            }
                            setPreview(calculateSliderValueAt(change.position.x, size.width.toFloat(), p, valueRange, reversed))
                            change.consume()
                        }
                    } finally {
                        preview = null
                        previewChanged(null)
                    }
                }
            },
    ) {
        val p = pad.toPx()
        val cy = size.height / 2
        val f = calculateSliderFraction(displayed, valueRange, reversed)
        val start = Offset(p, cy)
        val end = Offset(size.width - p, cy)
        val x = p + (if (reversed) 1f - f else f) * (size.width - p * 2)
        val fillColor = if (available) colors.accent else colors.secondary
        drawLine(colors.separator, start, end, rail.toPx(), StrokeCap.Round)
        drawLine(fillColor, if (reversed) end else start, Offset(x, cy), rail.toPx(), StrokeCap.Round)
        when (style) {
            TrackStyle.Line -> {
                val half = thumbLen.toPx() / 2
                drawLine(
                    colors.background.copy(alpha = 0.45f),
                    Offset(x, cy - half - 2),
                    Offset(x, cy + half + 2),
                    9.dp.toPx(),
                    StrokeCap.Round,
                )
                drawLine(
                    colors.text,
                    Offset(x, cy - half),
                    Offset(x, cy + half),
                    (if (dragging) 4.dp else 3.dp).toPx(),
                    StrokeCap.Round,
                )
            }
            TrackStyle.Capsule -> if (dot > 0f) drawCircle(colors.text, 6.dp.toPx() * dot, Offset(x, cy))
        }
    }
}

@Composable
private fun ScrubBubble(page: Int?, total: Int, fraction: Float, modifier: Modifier = Modifier) {
    if (page == null) return
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val bubbleWidth = 96.dp
        val maxOffset = (maxWidth - bubbleWidth).coerceAtLeast(0.dp)
        val targetCenter = maxWidth * fraction.coerceIn(0f, 1f)
        val x = (targetCenter - bubbleWidth / 2).coerceIn(0.dp, maxOffset)
        GlassSurface(null, Modifier.offset(x = x).width(bubbleWidth), shape = RoundedCornerShape(14.dp)) {
            TsuzukiText(
                formatPageChipText(page, total),
                Modifier.align(Alignment.Center).padding(vertical = 6.dp),
                TsuzukiTheme.typography.footnote,
                maxLines = 1,
            )
        }
    }
}

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
        DetailIcon(label, icon, onClick, Modifier.fillMaxSize().alpha(if (enabled) 1f else 0.3f), enabled, flip = flip)
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
) {
    var preview by remember { mutableStateOf<Int?>(null) }
    var jump by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val motion = TsuzukiTheme.motion
    var heldPreview by remember { mutableStateOf<Int?>(null) }
    if (preview != null) heldPreview = preview
    val range = 1..totalPages.coerceAtLeast(1)
    val shown = preview ?: currentPage
    val fraction = calculateSliderFraction(shown, range, isRtl)
    val tabular = TsuzukiTheme.typography.headline.copy(fontFeatureSettings = "tnum")
    val leftArrow = resolveLeftArrow(isRtl, enabledPrevious, enabledNext)
    val rightArrow = resolveRightArrow(isRtl, enabledPrevious, enabledNext)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            // Chapter carousel removed (redundant with the prev/next arrows in the action row).
            // While scrubbing, the "Page X of Y" bubble floats 10dp above the pill without reserving layout space.
            Box(Modifier.fillMaxWidth()) {
                GlassSurface(glass, Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(32.dp)) {
                    Row(Modifier.align(Alignment.Center).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(48.dp).clip(CircleShape)
                                .background(TsuzukiTheme.colors.text.copy(alpha = 0.10f))
                                .clickable(enabled = enabled && totalPages > 0, role = Role.Button, onClickLabel = "Go to page") { jump = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            TsuzukiText(shown.toString(), style = tabular, maxLines = 1)
                        }
                        ReaderTrackSlider(
                            currentPage,
                            range,
                            { onPageIndexChange(it - 1) },
                            TrackStyle.Line,
                            Modifier.weight(1f),
                            reversed = isRtl,
                            enabled = enabled && totalPages > 1,
                            onPreview = { preview = it },
                        )
                        TsuzukiText(
                            totalPages.toString(),
                            Modifier.width(48.dp).padding(start = 12.dp),
                            TsuzukiTheme.typography.subhead,
                            TsuzukiTheme.colors.secondary,
                        )
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = preview != null,
                    modifier = Modifier.align(Alignment.TopCenter).offset(y = (-44).dp),
                    enter = if (motion.reduced) {
                        fadeIn(M3Motion.reducedFade())
                    } else {
                        scaleIn(
                            M3Motion.enter(M3Motion.Medium1),
                            initialScale = 0.92f,
                            transformOrigin = TransformOrigin(0.5f, 1f),
                        ) + fadeIn(tween(M3Motion.Short3, easing = M3Motion.EmphasizedDecelerate))
                    },
                    exit = if (motion.reduced) {
                        fadeOut(M3Motion.reducedFade())
                    } else {
                        fadeOut(M3Motion.exit(M3Motion.Short3))
                    },
                ) {
                    ScrubBubble(heldPreview, totalPages, fraction, Modifier.padding(horizontal = 8.dp + 48.dp))
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
                        flip = !leftArrow.isNext,
                    )
                    GlassCircle("Chapters", TsuzukiIcons.Chapters, onClickChapterList, 48.dp, enabled, glass)
                    GlassCircle("Reading mode", TsuzukiIcons.BookOpen, onClickReadingMode, 48.dp, enabled, glass)
                    GlassCircle("Reading settings", TsuzukiIcons.Settings, onClickSettings, 48.dp, enabled, glass)
                    Box {
                        GlassCircle(
                            "More reading actions",
                            TsuzukiIcons.Ellipsis,
                            { if (moreActions.isNotEmpty()) menu = true },
                            48.dp,
                            enabled && moreActions.isNotEmpty(),
                            glass,
                        )
                        if (moreActions.isNotEmpty()) {
                            GlassMenu(menu && enabled, "Reading actions", moreActions, { menu = false })
                        }
                    }
                    GlassCircle(
                        rightArrow.contentDescription,
                        TsuzukiIcons.ChevronRight,
                        if (rightArrow.isNext) onNextChapter else onPreviousChapter,
                        48.dp,
                        enabled && rightArrow.enabled,
                        glass,
                        flip = !rightArrow.isNext,
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
) {
    var preview by remember { mutableStateOf<Int?>(null) }
    var jump by remember { mutableStateOf(false) }
    val range = 1..totalPages.coerceAtLeast(1)
    val shown = preview ?: currentPage
    val fraction = calculateSliderFraction(shown, range, isRtl)
    val leftArrow = resolveLeftArrow(isRtl, enabledPrevious, enabledNext)
    val rightArrow = resolveRightArrow(isRtl, enabledPrevious, enabledNext)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(modifier.fillMaxWidth()) {
            ScrubBubble(preview, totalPages, fraction, Modifier.padding(horizontal = 66.dp).padding(bottom = 10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassCircle(
                    leftArrow.contentDescription,
                    TsuzukiIcons.ChevronRight,
                    if (leftArrow.isNext) onNextChapter else onPreviousChapter,
                    58.dp,
                    enabled && leftArrow.enabled,
                    glass,
                    flip = !leftArrow.isNext,
                )
                GlassSurface(glass, Modifier.weight(1f).height(58.dp), shape = RoundedCornerShape(29.dp)) {
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
                                Modifier.clickable(enabled = enabled && totalPages > 0, role = Role.Button, onClickLabel = "Go to page") { jump = true }
                                    .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                                TsuzukiTheme.typography.caption1.copy(fontFeatureSettings = "tnum"),
                                TsuzukiTheme.colors.secondary,
                            )
                        }
                        ReaderTrackSlider(
                            currentPage,
                            range,
                            { onPageIndexChange(it - 1) },
                            TrackStyle.Capsule,
                            Modifier,
                            reversed = isRtl,
                            enabled = enabled && totalPages > 1,
                            onPreview = { preview = it },
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
                    flip = !rightArrow.isNext,
                )
            }
        }
    }
    GoToPageSheet(jump, currentPage, totalPages, onPageIndexChange) { jump = false }
}
