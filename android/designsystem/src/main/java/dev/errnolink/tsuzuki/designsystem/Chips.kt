package dev.errnolink.tsuzuki.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.State
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Kasane chips (`components/chip.css`, `web/docs-data.js` id "chip"): capsules that square off and
 * grow a check when selected — the check draws itself while the chip widens on the spring — assist
 * chips act, suggestion chips stay quiet, and input chips carry an avatar and a remove button whose
 * tap fades and collapses the chip before emitting the removal.
 */

enum class ChipTone {
    Neutral,
    @Deprecated("Use Neutral or Highlight", ReplaceWith("ChipTone.Neutral")) New,
    Highlight,
}

enum class ChipTriState { Off, Include, Exclude }

private val IosSystemGray = Color(0xFF767680)

fun chipColors(selected: Boolean, tone: ChipTone, triState: ChipTriState?, colors: TsuzukiColors): Pair<Color, Color> {
    val offFill = IosSystemGray.copy(alpha = if (colors.isDark) 0.24f else 0.12f)
    return when {
        triState == ChipTriState.Exclude -> colors.destructive to if (colors.isDark) colors.background else Color.White
        triState == ChipTriState.Include -> colors.accent to colors.onAccent
        triState == ChipTriState.Off -> offFill to colors.text
        tone == ChipTone.Highlight -> colors.accent.copy(alpha = if (colors.isDark) 0.20f else 0.14f) to colors.accent
        selected -> colors.accent to colors.onAccent
        else -> offFill to colors.text
    }
}

fun chipColors(selected: Boolean, tone: ChipTone, triState: ChipTriState?, dark: Boolean): Pair<Color, Color> =
    chipColors(selected, tone, triState, tsuzukiColors(dark))

/** Rest capsule radius of a chip of [minHeight]. */
internal fun chipRestRadius(minHeight: Dp): Dp = minHeight / 2

/** Selected chips square off to this radius (Kasane `--r-sm`). */
internal val ChipSelectedRadius = 8.dp

/** Width of the check slot a filter chip grows when selected (Kasane `.k-chip__check`). */
internal val ChipCheckSize = 18.dp

/** A filter chip grows its own check when selected and the caller brought no leading icon. */
internal fun chipShowsCheck(selected: Boolean, hasLeading: Boolean, triState: ChipTriState?): Boolean =
    selected && !hasLeading && triState == null

/** Selection pad-start: the capsule's padding tightens one step when the check is in. */
internal fun chipSelectedPadStart(selected: Boolean, base: Dp): Dp = if (selected) 10.dp else base

/**
 * Every geometry piece of a selecting chip follows ONE fraction on the spatial-fast spring, so
 * the widening, the tightening corners, the pad and the check that draws itself are one
 * continuous motion (Kasane `chip.css`: the check's width springs, so the chip widens smoothly) —
 * and the deselected chip narrows again on the same spring, never in steps.
 */
internal data class ChipSelectionGeometry(
    val corner: Dp,
    val checkSlot: Dp,
    val padStart: Dp,
    /** The check draws itself once the slot has begun to open. */
    val checkDraw: Float,
)

internal fun chipSelectionGeometry(fraction: Float, minHeight: Dp, basePadStart: Dp): ChipSelectionGeometry {
    val f = fraction.coerceIn(0f, 1f)
    return ChipSelectionGeometry(
        corner = lerpDp(chipRestRadius(minHeight), ChipSelectedRadius, f),
        checkSlot = ChipCheckSize * f,
        padStart = lerpDp(basePadStart, chipSelectedPadStart(selected = true, base = basePadStart), f),
        checkDraw = ((f - 0.25f) / 0.75f).coerceIn(0f, 1f),
    )
}

@Composable
fun Chip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    tone: ChipTone = ChipTone.Neutral,
    minHeight: Dp = 40.dp,
    horizontalPadding: Dp = 12.dp,
    textStyle: TextStyle? = null,
    gap: Dp = 6.dp,
    triState: ChipTriState? = null,
    role: Role = Role.RadioButton,
    /** Non-null turns the chip into an input chip with a trailing remove button. */
    onRemove: (() -> Unit)? = null,
    avatar: (@Composable () -> Unit)? = null,
) {
    val colors = TsuzukiTheme.colors
    val haptics = TsuzukiTheme.haptics
    val reduced = TsuzukiTheme.motion.reduced
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val active = selected || triState == ChipTriState.Include || triState == ChipTriState.Exclude
    val showCheck = chipShowsCheck(active, leading != null, triState)
    // One selection fraction drives the corner, the check slot, the pad and the check's draw,
    // so selecting (and deselecting, on the same critically damped spring) widens without shaking sibling chips.
    val selection = remember { Animatable(if (active) 1f else 0f) }
    LaunchedEffect(active, reduced) {
        if (reduced) {
            selection.snapTo(if (active) 1f else 0f)
        } else {
            selection.animateTo(if (active) 1f else 0f, KasaneSprings.selectionCheck())
        }
    }
    val geometry = chipSelectionGeometry(selection.value, minHeight, horizontalPadding)
    val corner = geometry.corner
    val scale by animateFloatAsState(
        if (pressed) 0.97f else 1f,
        if (reduced) snap() else KasaneSprings.fastSpatial(reduced),
        label = "ChipPress",
    )
    val shape = RoundedCornerShape(corner.coerceAtLeast(0.dp))
    val (fill, contentColor) = chipColors(selected, tone, triState, colors)

    // input-chip removal: fade and collapse the width, then emit (Kasane .is-leaving)
    var leaving by remember { mutableStateOf(false) }
    val leavingProgress by animateFloatAsState(
        if (leaving) 1f else 0f,
        if (reduced) snap() else KasaneSprings.spatialSoft(reduced),
        label = "ChipLeaving",
    )
    var naturalWidthPx by remember { mutableFloatStateOf(0f) }
    val leavingState = rememberUpdatedState(leavingProgress)
    LaunchedEffect(leaving) {
        if (leaving) {
            // the width collapse has ~300 ms to play; emit when it is done
            delay(if (reduced) 120L else 350L)
            onRemove?.invoke()
        }
    }

    Box(
        modifier
            .then(if (!leaving) Modifier.minimumInteractiveComponentSize() else Modifier)
            .heightIn(min = minHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .alpha(1f - leavingProgress.coerceIn(0f, 1f))
            .clip(shape)
            .background(fill)
            .then(
                when (role) {
                    Role.Checkbox -> Modifier.toggleable(
                        value = selected,
                        enabled = enabled && !leaving,
                        role = Role.Checkbox,
                        interactionSource = interaction,
                        indication = null,
                        onValueChange = {
                            haptics.select()
                            onClick()
                        },
                    )
                    Role.Button -> Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        enabled = enabled && !leaving,
                        role = Role.Button,
                        onClick = {
                            haptics.select()
                            onClick()
                        },
                    )
                    else -> Modifier.selectable(
                        selected = selected,
                        enabled = enabled && !leaving,
                        role = role,
                        interactionSource = interaction,
                        indication = null,
                        onClick = {
                            haptics.select()
                            onClick()
                        },
                    )
                },
            )
            .then(if (leavingProgress > 0f) Modifier.collapsingWidth(naturalWidthPx, leavingState) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .onSizeChanged { if (!leaving) naturalWidthPx = it.width.toFloat() }
                .padding(start = geometry.padStart, end = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            avatar?.invoke()
            if (showCheck || geometry.checkSlot > 0.dp) {
                Box(Modifier.size(geometry.checkSlot.coerceAtLeast(0.dp)), contentAlignment = Alignment.Center) {
                    if (geometry.checkSlot > 0.5.dp) {
                        DrawnCheck(geometry.checkDraw, Modifier.size(ChipCheckSize), color = contentColor)
                    }
                }
            }
            leading?.invoke(this)
            TsuzukiText(
                label,
                style = textStyle ?: TsuzukiTheme.typography.subhead.copy(fontWeight = FontWeight.SemiBold),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            trailing?.invoke(this)
            if (onRemove != null) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(enabled = enabled && !leaving, role = Role.Button, onClick = { leaving = true }),
                    contentAlignment = Alignment.Center,
                ) {
                    val removeDescription = LocalTsuzukiComponentLabels.current.removeItem(label)
                    Icon(
                        TsuzukiIcons.X,
                        contentDescription = removeDescription,
                        modifier = Modifier.size(16.dp),
                        tint = contentColor,
                    )
                }
            }
        }
    }
}

/** Collapses the chip from its rest width to zero while [progress] runs (Kasane `.is-leaving`). */
private fun Modifier.collapsingWidth(restPx: Float, progress: State<Float>): Modifier = layout { measurable, constraints ->
    val p = progress.value.coerceIn(0f, 1f)
    if (p >= 1f) {
        layout(0, 0) {}
    } else if (restPx > 0f) {
        val w = (restPx * (1f - p)).roundToInt().coerceAtLeast(1)
        val placeable = measurable.measure(
            Constraints(minWidth = w, maxWidth = w, minHeight = 0, maxHeight = constraints.maxHeight),
        )
        layout(w, placeable.height) { placeable.place(0, 0) }
    } else {
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}

@Composable
fun TriStateChip(
    label: String,
    state: ChipTriState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minHeight: Dp = 32.dp,
    textStyle: TextStyle? = null,
) {
    val colors = TsuzukiTheme.colors
    val labels = LocalTsuzukiComponentLabels.current
    val tint = chipColors(state != ChipTriState.Off, ChipTone.Neutral, state, colors).second
    val glyph = 14.dp
    Chip(
        label = label,
        selected = state != ChipTriState.Off,
        triState = state,
        role = Role.Button,
        onClick = onClick,
        modifier = modifier.semantics {
            stateDescription = when (state) {
                ChipTriState.Off -> labels.triStateOff
                ChipTriState.Include -> labels.triStateInclude
                ChipTriState.Exclude -> labels.triStateExclude
            }
        },
        enabled = enabled,
        minHeight = minHeight,
        horizontalPadding = if (state == ChipTriState.Off) 12.dp else 10.dp,
        textStyle = textStyle,
        gap = 4.dp,
        leading = if (state == ChipTriState.Off) {
            null
        } else {
            {
                Icon(
                    if (state == ChipTriState.Include) TsuzukiIcons.Check else TsuzukiIcons.X,
                    contentDescription = null,
                    modifier = Modifier.size(glyph),
                    tint = tint,
                )
            }
        },
    )
}
