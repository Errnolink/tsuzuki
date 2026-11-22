package dev.errnolink.tsuzuki.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

val LocalGroupedListGutter = androidx.compose.runtime.staticCompositionLocalOf { TsuzukiSpacing.gutter }

@Composable
fun InsetGroupedList(
    modifier: Modifier = Modifier,
    title: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.padding(horizontal = LocalGroupedListGutter.current), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        title?.let {
            TsuzukiText(it, Modifier.padding(horizontal = 16.dp), TsuzukiTheme.typography.sectionHeader, TsuzukiTheme.colors.secondary)
        }
        Column(
            Modifier.fillMaxWidth().clip(tsuzukiRounded(TsuzukiCorners.group)).background(TsuzukiTheme.colors.grouped),
            content = content,
        )
        footer?.let {
            TsuzukiText(it, Modifier.padding(horizontal = 16.dp), TsuzukiTheme.typography.footnote, TsuzukiTheme.colors.secondary)
        }
    }
}

/**
 * One shelf/section header: the label with an optional trailing action. Callers keep their own
 * typography, colour and padding ([titleStyle]/[titleColor]/[modifier]) — the audit's header
 * styles differ on purpose until a design pass unifies them — so this replaces the hand-rolled
 * rows without moving anything. [actionLabel] renders the accent text action in a 44 dp-min box
 * (pass [actionMinHeight] null for a bare text link); [actionChevron] renders the 14 dp chevron
 * instead (the caller makes the whole row clickable); [trailing] is the escape hatch for counts
 * and buttons.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = TsuzukiTheme.typography.sectionHeader,
    titleColor: Color = TsuzukiTheme.colors.secondary,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionStyle: TextStyle = TsuzukiTheme.typography.footnote.copy(fontWeight = FontWeight.SemiBold),
    actionColor: Color = TsuzukiTheme.colors.accent,
    actionMinHeight: Dp? = 44.dp,
    actionChevron: Boolean = false,
    leading: (@Composable RowScope.() -> Unit)? = null,
    titleTrailing: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        leading?.invoke(this)
        val separates = actionLabel != null || trailing != null
        TsuzukiText(
            title,
            if (separates) Modifier.weight(1f) else Modifier,
            titleStyle,
            titleColor,
        )
        titleTrailing?.invoke(this)
        if (actionLabel != null && onAction != null) {
            Box(
                Modifier
                    .then(if (actionMinHeight != null) Modifier.heightIn(min = actionMinHeight) else Modifier)
                    .clickable(onClick = onAction),
                contentAlignment = Alignment.Center,
            ) {
                TsuzukiText(actionLabel, style = actionStyle, color = actionColor)
            }
        } else if (actionChevron) {
            TsuzukiIcon(TsuzukiIcons.ChevronRight, null, Modifier.size(14.dp), tint = TsuzukiTheme.colors.secondary)
        }
        trailing?.invoke(this)
    }
}

@Composable
fun GroupedRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    enabled: Boolean = true,
    divider: Boolean = true,
    destructive: Boolean = false,
    selected: Boolean = false,
    thumbIcon: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    require((checked == null) == (onCheckedChange == null))
    require(checked == null || onClick == null)
    val colors = TsuzukiTheme.colors
    val toggleSource = if (checked != null) remember { MutableInteractionSource() } else null
    val interaction = when {
        checked != null -> Modifier.toggleable(
            checked,
            interactionSource = toggleSource!!,
            indication = LocalIndication.current,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange!!,
        )
        onClick != null -> Modifier.clickable(enabled = enabled, onClick = onClick)
        else -> Modifier
    }
    val stackValue = LocalDensity.current.fontScale >= 1.5f
    // S1/S2: the divider starts at the row's text edge, so it follows the measured leading slot
    // (24 dp icon, 29 dp badge, 32 dp image) instead of a guessed constant.
    val leadingWidthPx = remember { mutableIntStateOf(0) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(
        modifier
            .then(interaction)
            .then(
                if (selected) {
                    Modifier.clip(RoundedCornerShape(TsuzukiCorners.field)).selectionFill(true)
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = TsuzukiSpacing.rowMin)
                .padding(horizontal = GroupedRowHorizontalPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GroupedRowItemSpacing),
        ) {
            leading?.let { slot ->
                Box(Modifier.onSizeChanged { leadingWidthPx.intValue = it.width }) { slot() }
            }
            // S4: weight the title column so trailing value/widget measures against the full row
            // width rather than shrinking with the title, while capping value to at most 46% (42%
            // with subtitle) so long titles and values share the row cleanly.
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                TsuzukiText(title, color = if (!enabled) colors.secondary else if (destructive) colors.destructive else colors.text)
                subtitle?.let { TsuzukiText(it, style = TsuzukiTheme.typography.footnote, color = colors.secondary) }
                if (stackValue && value != null) TsuzukiText(value, style = TsuzukiTheme.typography.subhead, color = colors.secondary)
            }
            if (!stackValue && value != null) {
                Box(
                    Modifier.maxWidthFraction(if (subtitle == null) 0.46f else 0.42f),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    TsuzukiText(
                        value,
                        style = TsuzukiTheme.typography.subhead.copy(textAlign = TextAlign.End),
                        color = colors.secondary,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) trailing()
            if (checked != null) TsuzukiToggle(checked, enabled, interaction = toggleSource, onCheckedChange = onCheckedChange, thumbIcon = thumbIcon)
            if (chevron) TsuzukiChevron()
        }
        if (divider) {
            val density = LocalDensity.current
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(TsuzukiSpacing.hairline)
                    .drawBehind {
                        val inset = groupDividerStart(with(density) { leadingWidthPx.intValue.toDp() }).toPx()
                        val (x0, x1) = insetDividerSpan(size.width, inset, fromEnd = false, rtl)
                        drawRect(
                            color = colors.separator,
                            topLeft = Offset(x0, 0f),
                            size = Size(x1 - x0, size.height),
                        )
                    },
            )
        }
    }
}

private val GroupedRowHorizontalPadding = 16.dp
private val GroupedRowItemSpacing = 12.dp

/**
 * Inset of a grouped-row divider: the row's start padding plus the leading slot and the item
 * spacing, so the divider starts at the row's text edge whatever leads the row.
 */
fun groupDividerStart(leadingWidth: Dp = 0.dp): Dp = when {
    leadingWidth > 0.dp -> GroupedRowHorizontalPadding + leadingWidth + GroupedRowItemSpacing
    else -> GroupedRowHorizontalPadding
}

/** Measure at most [fraction] of the incoming maximum width; below that the content wraps. */
internal fun Modifier.maxWidthFraction(fraction: Float): Modifier = layout { measurable, constraints ->
    val max = if (constraints.maxWidth == Constraints.Infinity) {
        constraints.maxWidth
    } else {
        (constraints.maxWidth * fraction).toInt().coerceAtLeast(constraints.minWidth)
    }
    val placeable = measurable.measure(constraints.copy(maxWidth = max))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

private val SwitchTrackWidth = 52.dp
private val SwitchTrackHeight = 32.dp
private val SwitchInset = 4.dp
private val SwitchThumbOff = 16.dp
private val SwitchThumbOn = 24.dp
private val SwitchLensWidth = 42.dp
private val SwitchLensHeight = 38.dp

/** A drag past the midpoint of the track commits the toggle on release. */
internal fun switchDragCommits(fraction: Float): Boolean = fraction > 0.5f

/**
 * M3 track whose thumb grows when on (Kasane switch). While held, the thumb stretches into a glass
 * lens that overhangs the track; the thumb can also be dragged to toggle, committing past the
 * midpoint. Toggle, slide and grow run on the fast spatial spring.
 */
@Composable
fun TsuzukiToggle(
    checked: Boolean,
    enabled: Boolean = true,
    interaction: InteractionSource? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    thumbIcon: Boolean = false,
) {
    val motion = TsuzukiTheme.motion
    val haptics = TsuzukiTheme.haptics
    val colors = TsuzukiTheme.colors
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val opaqueGlass = LocalOpaqueGlass.current
    val reduced = motion.reduced
    val pressedExternal = interaction?.collectIsPressedAsState()?.value ?: false

    val position = remember { Animatable(if (checked) 1f else 0f) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }
    var pressedLocal by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // The gesture handlers outlive recompositions between key resets: they read the live toggle.
    val currentChecked by rememberUpdatedState(checked)
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)
    val currentReduced by rememberUpdatedState(reduced)

    // State changes drive the thumb unless a finger owns it; the release path snaps first, so the
    // slide always continues from wherever the finger left it.
    LaunchedEffect(checked, dragging) {
        if (!dragging) position.animateTo(if (checked) 1f else 0f, KasaneSprings.fastSpatial(reduced))
    }
    val firstComposition = remember { mutableStateOf(true) }
    LaunchedEffect(checked) {
        if (firstComposition.value) {
            firstComposition.value = false
        } else {
            if (checked) haptics.toggleOn() else haptics.toggleOff()
        }
    }

    val held = (pressedExternal || pressedLocal || dragging) && enabled
    val lensFraction by animateFloatAsState(
        if (held && !reduced) 1f else 0f,
        KasaneSprings.fastSpatial(reduced),
        label = "Switch lens",
    )
    val progress = if (dragging) dragFraction else position.value
    val travel = SwitchTrackWidth - SwitchThumbOn - SwitchInset * 2
    val travelPx = with(LocalDensity.current) { travel.toPx() }
    val thumbSize = lerpDp(SwitchThumbOff, SwitchThumbOn, progress)
    val lensWidth = lerpDp(thumbSize, SwitchLensWidth, lensFraction)
    val lensHeight = lerpDp(thumbSize, SwitchLensHeight, lensFraction)
    val centerX = SwitchInset + SwitchThumbOn / 2f + travel * progress
    val lensOffsetX = centerX - lensWidth / 2f
    val mirroredOffsetX = SwitchTrackWidth - lensOffsetX - lensWidth
    val dir = if (rtl) mirroredOffsetX else lensOffsetX
    val knobShape = RoundedCornerShape(TsuzukiCorners.capsulePercent)
    val offFill = Color(0xFF787880).copy(alpha = if (colors.isDark) 0.32f else 0.16f)

    Box(
        Modifier
            .size(SwitchTrackWidth, SwitchTrackHeight)
            .alpha(if (enabled) 1f else 0.4f)
            .then(
                if (enabled && onCheckedChange != null) {
                    Modifier.pointerInput(rtl, travelPx) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                dragging = true
                                dragFraction = position.value
                                pressedLocal = true
                            },
                            onDragEnd = {
                                scope.launch {
                                    position.snapTo(dragFraction)
                                    dragging = false
                                    pressedLocal = false
                                    val commit = switchDragCommits(dragFraction)
                                    if (commit != currentChecked) currentOnCheckedChange?.invoke(commit) else {
                                        position.animateTo(if (currentChecked) 1f else 0f, KasaneSprings.settle(currentReduced))
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    position.snapTo(dragFraction)
                                    dragging = false
                                    pressedLocal = false
                                    position.animateTo(if (currentChecked) 1f else 0f, KasaneSprings.settle(currentReduced))
                                }
                            },
                        ) { change, _ ->
                            change.consume()
                            val next = (dragFraction + change.positionChange().x / travelPx * (if (rtl) -1f else 1f))
                                .coerceIn(0f, 1f)
                            if ((dragFraction < 0.5f) != (next < 0.5f)) haptics.select()
                            dragFraction = next
                        }
                    }
                } else {
                    Modifier
                },
            )
            .clip(CircleShape)
            .background(if (checked) colors.accent else offFill),
    ) {
        Box(
            Modifier
                .offset(x = dir)
                .size(lensWidth, lensHeight)
                .align(Alignment.CenterStart)
                .shadow(lerpDp(1.dp, 4.dp, lensFraction), knobShape, clip = false)
                .drawBehind {
                    val fill = sliderKnobFill(colors, lensFraction, opaqueGlass)
                    drawRoundRect(fill, cornerRadius = CornerRadius(size.minDimension / 2f))
                    drawGlassRim(colors.isDark, lensFraction)
                },
            contentAlignment = Alignment.Center,
        ) {
            if (thumbIcon) {
                TsuzukiIcon(
                    TsuzukiIcons.Check,
                    null,
                    Modifier
                        .size(14.dp)
                        .graphicsLayer {
                            val shown = checked && lensFraction < 0.5f
                            alpha = if (shown) 1f else 0f
                            scaleX = if (shown) 1f else 0.4f
                            scaleY = if (shown) 1f else 0.4f
                        },
                    tint = colors.accent,
                )
            }
        }
    }
}


@Composable
fun TsuzukiChevron(modifier: Modifier = Modifier) {
    TsuzukiIcon(TsuzukiIcons.ChevronRight, null, modifier.size(18.dp), tint = TsuzukiTheme.colors.secondary)
}

@Composable
fun TsuzukiCheckMark(modifier: Modifier = Modifier) {
    TsuzukiIcon(TsuzukiIcons.Check, null, modifier.size(18.dp), tint = TsuzukiTheme.colors.accent)
}

@Composable
fun TsuzukiMinusMark(modifier: Modifier = Modifier) {
    TsuzukiIcon(TsuzukiIcons.Minus, null, modifier.size(18.dp))
}

fun Modifier.insetDivider(start: Dp = 16.dp, fromEnd: Boolean = false): Modifier = composed {
    val color = TsuzukiTheme.colors.separator
    val hairline = TsuzukiSpacing.hairline
    drawWithContent {
        drawContent()
        val y = size.height - hairline.toPx() / 2f
        val (x0, x1) = insetDividerSpan(size.width, start.toPx(), fromEnd, layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl)
        drawLine(color, Offset(x0, y), Offset(x1, y), hairline.toPx())
    }
}

/** The divider's horizontal span: inset on the leading edge (or trailing with [fromEnd]), mirrored in RTL. */
internal fun insetDividerSpan(width: Float, inset: Float, fromEnd: Boolean, rtl: Boolean): Pair<Float, Float> {
    val insetLeft = fromEnd == rtl
    return if (insetLeft) inset to width else 0f to width - inset
}
