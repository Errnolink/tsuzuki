package dev.errnolink.tsuzuki.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.max

/**
 * Kasane selection controls (`components/selection.css`, `stepper.css`, `js/lens.js` hold-to-repeat):
 * the check draws itself like a stroke-dashoffset animation, the radio dot springs in from zero,
 * and the stepper repeats while held, accelerating, with no extra step on release.
 */

/** Box rest radius; selected corners round one step further. */
internal val CheckboxRestCorner = 7.dp
internal val CheckboxSelectedCorner = 8.dp

/** Stepper hold: first repeat after 420 ms, then every max(45, 130 − 8·n) ms. */
internal const val StepperHoldMs = 420L
internal const val StepperFirstRepeatMs = 130
internal const val StepperRepeatFloorMs = 45
internal const val StepperRepeatDecayMs = 8

/** Fraction of the box the press halo adds on each side (the 9 px ring of selection.css). */
internal val CheckboxPressHaloGrowth = 9.dp

/** Dot preview while an unchecked radio is held. */
internal const val RadioPressDotScale = 0.45f

internal fun stepperRepeatIntervalMs(repeatIndex: Int): Int =
    max(StepperRepeatFloorMs, StepperFirstRepeatMs - repeatIndex * StepperRepeatDecayMs)

/** Value after one step in [direction]; unmoved at the bounds. */
internal fun stepperValue(value: Int, direction: Int, range: IntRange, step: Int): Int =
    (value + direction * step).coerceIn(range.first, range.last)

internal fun stepperCanStep(value: Int, direction: Int, range: IntRange, step: Int): Boolean =
    stepperValue(value, direction, range, step) != value

/**
 * The check mark as a drawn stroke: [progress] 0..1 draws the two-segment path from the elbow out
 * (the CSS `stroke-dashoffset` animation of `.k-check__box > svg`).
 */
@Composable
internal fun DrawnCheck(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = TsuzukiTheme.colors.onAccent,
    stroke: Dp = 2.5.dp,
) {
    Canvas(modifier) {
        if (progress <= 0f) return@Canvas
        val path = Path().apply {
            moveTo(size.width * 6f / 24f, size.height * 12.8f / 24f)
            lineTo(size.width * 10.2f / 24f, size.height * 17f / 24f)
            lineTo(size.width * 18.2f / 24f, size.height * 7.4f / 24f)
        }
        val measure = PathMeasure().apply { setPath(path, false) }
        val partial = Path()
        measure.getSegment(0f, measure.length * progress.coerceIn(0f, 1f), partial, true)
        drawPath(
            partial,
            color,
            style = Stroke(stroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** The indeterminate dash of the tri-state box. */
@Composable
internal fun DrawnDash(progress: Float, modifier: Modifier = Modifier, color: Color, stroke: Dp = 2.5.dp) {
    Canvas(modifier) {
        if (progress <= 0f) return@Canvas
        val y = size.height / 2f
        val x0 = size.width * 5.5f / 24f
        val x1 = size.width * 18.5f / 24f
        drawLine(color, Offset(x0, y), Offset(x0 + (x1 - x0) * progress.coerceIn(0f, 1f), y), stroke.toPx(), StrokeCap.Round)
    }
}

/**
 * Kasane checkbox: a 22 dp rounded box whose corners round further when checked, fills on effects,
 * and whose check draws itself; held, the box shrinks to 88 % inside a primary halo.
 * Pass `onCheckedChange = null` for a display-only mark inside a row that owns the semantics.
 */
@Composable
fun TsuzukiCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    indeterminate: Boolean = false,
) {
    val colors = TsuzukiTheme.colors
    val haptics = TsuzukiTheme.haptics
    val reduced = TsuzukiTheme.motion.reduced
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by rememberPressFraction(
        interaction,
        pressIn = KasaneSprings.fastSpatial(reduced),
        release = KasaneSprings.fastSpatial(reduced),
    )
    val selected = checked || indeterminate
    val corner by animateDpAsState(
        if (selected) CheckboxSelectedCorner else CheckboxRestCorner,
        KasaneSprings.fastSpatial(reduced),
        label = "CheckboxCorner",
    )
    val fill by animateColorAsStateCompat(if (selected) colors.accent else Color.Transparent)
    val ring by animateColorAsStateCompat(if (selected) Color.Transparent else colors.secondary.copy(alpha = 0.9f))
    val checkProgress by animateFloatAsState(
        if (checked) 1f else 0f,
        tween(240, delayMillis = if (checked) 60 else 0, easing = M3Motion.Standard),
        label = "CheckboxCheck",
    )
    val dashProgress by animateFloatAsState(
        if (indeterminate && !checked) 1f else 0f,
        tween(M3Motion.Short4, delayMillis = if (indeterminate) 40 else 0, easing = M3Motion.Standard),
        label = "CheckboxDash",
    )
    val shape = RoundedCornerShape(corner)
    val content = colors.onAccent
    Box(
        modifier
            .size(22.dp + CheckboxPressHaloGrowth * 2)
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(
                        value = checked,
                        enabled = enabled,
                        role = Role.Checkbox,
                        onValueChange = { value ->
                            if (value) haptics.toggleOn() else haptics.toggleOff()
                            onCheckedChange(value)
                        },
                        interactionSource = interaction,
                        indication = null,
                    )
                } else {
                    Modifier
                },
            )
            .alpha(if (enabled) 1f else 0.38f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(22.dp + CheckboxPressHaloGrowth * 2)
                .clip(CircleShape)
                .background(colors.accent.copy(alpha = 0.12f * press.coerceIn(0f, 1f))),
        )
        Box(
            Modifier
                .size(22.dp)
                .graphicsLayer {
                    scaleX = 1f - 0.12f * press.coerceIn(0f, 1f)
                    scaleY = 1f - 0.12f * press.coerceIn(0f, 1f)
                }
                .clip(shape)
                .background(fill)
                .let { m -> if (ring.alpha > 0f) m.border(2.dp, ring, shape) else m },
            contentAlignment = Alignment.Center,
        ) {
            DrawnCheck(checkProgress, Modifier.size(18.dp), color = content)
            DrawnDash(dashProgress, Modifier.size(18.dp), color = content)
        }
    }
}

/**
 * Kasane radio: the dot springs in from zero on the fast spatial spring; held on an unchecked
 * radio it previews at a small scale in the quiet colour.
 */
@Composable
fun TsuzukiRadio(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = TsuzukiTheme.colors
    val haptics = TsuzukiTheme.haptics
    val reduced = TsuzukiTheme.motion.reduced
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val dotScale by animateFloatAsState(
        when {
            selected -> 1f
            pressed -> RadioPressDotScale
            else -> 0f
        },
        KasaneSprings.fastSpatial(reduced),
        label = "RadioDot",
    )
    val ringColor by animateColorAsStateCompat(if (selected) colors.accent else colors.secondary.copy(alpha = 0.9f))
    Box(
        modifier
            .size(22.dp)
            .then(
                if (onClick != null) {
                    Modifier.selectable(
                        selected = selected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        interactionSource = interaction,
                        indication = null,
                        onClick = {
                            haptics.select()
                            onClick()
                        },
                    )
                } else {
                    Modifier
                },
            )
            .alpha(if (enabled) 1f else 0.38f)
            .clip(CircleShape)
            .border(2.dp, ringColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .graphicsLayer {
                    scaleX = dotScale
                    scaleY = dotScale
                }
                .clip(CircleShape)
                .background(colors.accent),
        )
    }
}

/** 44 dp row with the label inside the touch target and the checkbox mark at the end. */
@Composable
fun CheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    indeterminate: Boolean = false,
) {
    val haptics = TsuzukiTheme.haptics
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { value ->
                    if (value) haptics.toggleOn() else haptics.toggleOff()
                    onCheckedChange(value)
                },
            )
            .padding(horizontal = TsuzukiSpacing.row),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.medium),
    ) {
        TsuzukiText(label, Modifier.weight(1f), style = TsuzukiTheme.typography.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
        TsuzukiCheckbox(checked, onCheckedChange = null, enabled = enabled, indeterminate = indeterminate)
    }
}

/** 44 dp radio row; the group semantics come from wrapping rows in `Modifier.selectableGroup()`. */
@Composable
fun RadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptics = TsuzukiTheme.haptics
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = {
                    haptics.select()
                    onClick()
                },
            )
            .padding(horizontal = TsuzukiSpacing.row),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.medium),
    ) {
        TsuzukiText(label, Modifier.weight(1f), style = TsuzukiTheme.typography.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
        TsuzukiRadio(selected, onClick = null, enabled = enabled)
    }
}

/**
 * Kasane stepper: a connected group of minus, value and plus. The pressed end rounds out into a
 * full pill and gives a little; a tap steps once on release, holding past 420 ms repeats with the
 * interval accelerating from 130 ms down to 45 ms and never adds an extra step on release; the
 * number rolls in the direction of the change.
 */
@Composable
fun Stepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: IntRange,
    modifier: Modifier = Modifier,
    step: Int = 1,
    format: ((Int) -> String)? = null,
    enabled: Boolean = true,
) {
    val reduced = TsuzukiTheme.motion.reduced
    val colors = TsuzukiTheme.colors
    val haptics = TsuzukiTheme.haptics
    val latestValue by rememberUpdatedState(value)
    val latestChange by rememberUpdatedState(onValueChange)
    val latestEnabled by rememberUpdatedState(enabled)
    val formatFn = format ?: { it.toString() }

    // direction of the last change, for the roll
    var rollDirection by remember { mutableIntStateOf(0) }
    val roll = remember { Animatable(0f) }
    val rollAlpha = remember { Animatable(1f) }
    var shownValue by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        if (value == shownValue) return@LaunchedEffect
        rollDirection = if (value > shownValue) 1 else -1
        shownValue = value
        if (reduced) {
            roll.snapTo(0f)
            rollAlpha.snapTo(1f)
            return@LaunchedEffect
        }
        roll.snapTo(-rollDirection * 6f)
        rollAlpha.snapTo(0f)
        roll.animateTo(0f, tween(M3Motion.Short4, easing = M3Motion.Standard))
        rollAlpha.animateTo(1f, tween(M3Motion.Short4, easing = M3Motion.Standard))
    }

    fun step(direction: Int): Boolean {
        if (!latestEnabled) return false
        val next = stepperValue(latestValue, direction, valueRange, step)
        if (next == latestValue) return false
        latestChange(next)
        haptics.select()
        return true
    }

    Row(modifier.heightIn(min = 40.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        StepperEnd(
            direction = -1,
            leading = true,
            enabled = latestEnabled && stepperCanStep(value, -1, valueRange, step),
            reduced = reduced,
            quietFill = colors.text.copy(alpha = if (colors.isDark) 0.10f else 0.06f),
            tonalFill = colors.accent.copy(alpha = if (colors.isDark) 0.20f else 0.14f),
            onStep = { step(-1) },
        ) {
            TsuzukiIcon(TsuzukiIcons.Minus, null, Modifier.size(18.dp), tint = colors.text)
        }
        Box(
            Modifier
                .heightIn(min = 40.dp)
                .defaultMinSize(minWidth = 52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.text.copy(alpha = if (colors.isDark) 0.10f else 0.06f))
                .padding(horizontal = 8.dp)
                .clipToBounds(),
            contentAlignment = Alignment.Center,
        ) {
            TsuzukiText(
                formatFn(value),
                Modifier.graphicsLayer {
                    translationY = roll.value.dp.toPx()
                    alpha = rollAlpha.value
                },
                style = TsuzukiTheme.typography.subhead.copy(fontWeight = FontWeight.SemiBold),
                color = colors.text,
                maxLines = 1,
            )
        }
        StepperEnd(
            direction = 1,
            leading = false,
            enabled = latestEnabled && stepperCanStep(value, 1, valueRange, step),
            reduced = reduced,
            quietFill = colors.text.copy(alpha = if (colors.isDark) 0.10f else 0.06f),
            tonalFill = colors.accent.copy(alpha = if (colors.isDark) 0.20f else 0.14f),
            onStep = { step(1) },
        ) {
            TsuzukiIcon(TsuzukiIcons.Plus, null, Modifier.size(18.dp), tint = colors.text)
        }
    }
}

@Composable
private fun StepperEnd(
    direction: Int,
    leading: Boolean,
    enabled: Boolean,
    reduced: Boolean,
    quietFill: Color,
    tonalFill: Color,
    onStep: (Int) -> Boolean,
    icon: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by rememberPressFraction(
        interaction,
        pressIn = KasaneSprings.fastSpatial(reduced),
        release = KasaneSprings.fastSpatial(reduced),
    )
    // one step per tap on release; holding past 420 ms repeats and accelerates, no extra step on release
    var holdSteps by remember { mutableIntStateOf(0) }
    LaunchedEffect(pressed, enabled) {
        if (!pressed) {
            if (holdSteps == 0) onStep(direction)
            holdSteps = 0
            return@LaunchedEffect
        }
        holdSteps = 0
        delay(StepperHoldMs)
        var n = 1
        while (isActive) {
            if (!onStep(direction)) break
            holdSteps++
            delay(stepperRepeatIntervalMs(n).toLong())
            n++
        }
    }
    val inner = 8.dp
    val capsule = 20.dp
    val fraction = press.coerceIn(0f, 1f)
    val shape = if (leading) {
        RoundedCornerShape(
            capsule,
            androidx.compose.ui.unit.lerp(inner, capsule, fraction),
            androidx.compose.ui.unit.lerp(inner, capsule, fraction),
            capsule,
        )
    } else {
        RoundedCornerShape(
            androidx.compose.ui.unit.lerp(inner, capsule, fraction),
            capsule,
            capsule,
            androidx.compose.ui.unit.lerp(inner, capsule, fraction),
        )
    }
    Box(
        Modifier
            .minimumTouchTarget()
            .size(width = 48.dp, height = 40.dp)
            .graphicsLayer {
                val s = 1f - 0.04f * fraction
                scaleX = s
                scaleY = s
            }
            .clip(shape)
            .background(if (fraction > 0f) tonalFill else quietFill)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = { /* the step happens on release in the press effect */ },
            )
            .alpha(if (enabled) 1f else 0.38f),
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

/** Effects colour fade shared by the selection controls. */
@Composable
internal fun animateColorAsStateCompat(target: Color): State<Color> {
    val reduced = TsuzukiTheme.motion.reduced
    return animateColorAsState(
        target,
        if (reduced) snap() else KasaneSprings.effects(),
        label = "SelectionColor",
    )
}
