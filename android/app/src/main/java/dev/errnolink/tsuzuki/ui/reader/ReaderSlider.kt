package dev.errnolink.tsuzuki.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.KasaneSprings
import dev.errnolink.tsuzuki.designsystem.LocalOpaqueGlass
import dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.drawSliderBarHandle
import dev.errnolink.tsuzuki.designsystem.drawSliderLens
import dev.errnolink.tsuzuki.designsystem.drawSliderThumbGap
import dev.errnolink.tsuzuki.designsystem.sliderKnobFill
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun ReaderSlider(
    value: Int,
    valueRange: IntRange,
    onCommit: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String,
    vertical: Boolean = false,
    reversed: Boolean = false,
    enabled: Boolean = true,
    onPreview: (Int?) -> Unit = {},
) {
    val colors = TsuzukiTheme.colors
    val motion = TsuzukiTheme.motion
    val haptics = TsuzukiTheme.haptics
    val opaqueGlass = LocalOpaqueGlass.current
    var preview by remember(valueRange) { mutableStateOf<Int?>(null) }
    val currentValue by rememberUpdatedState(value.coerceIn(valueRange))
    val commit by rememberUpdatedState(onCommit)
    val previewChanged by rememberUpdatedState(onPreview)
    val scope = rememberCoroutineScope()
    val available = enabled && valueRange.last > valueRange.first
    val displayed = preview ?: currentValue
    val dragging = preview != null
    var dragFraction by remember(valueRange) { mutableStateOf<Float?>(null) }
    val spanCount = (valueRange.last - valueRange.first).coerceAtLeast(1)
    fun fractionForValue(v: Int): Float {
        val raw = (v - valueRange.first).toFloat() / spanCount
        return (if (reversed) 1f - raw else raw).coerceIn(0f, 1f)
    }
    val restFraction = fractionForValue(currentValue)
    val visualFraction = remember(valueRange) { Animatable(restFraction) }
    LaunchedEffect(restFraction, dragFraction) {
        if (dragFraction == null) {
            if (motion.reduced) {
                visualFraction.snapTo(restFraction)
            } else {
                visualFraction.animateTo(restFraction, KasaneSprings.fastSpatial(motion.reduced))
            }
        }
    }
    val pressedFraction by animateFloatAsState(
        if (dragging && available) 1f else 0f,
        KasaneSprings.fastSpatial(motion.reduced),
        label = "ReaderSlider press",
    )
    val lensFraction by animateFloatAsState(
        if (dragging && available && !motion.reduced) 1f else 0f,
        KasaneSprings.fastSpatial(motion.reduced),
        label = "ReaderSlider lens",
    )
    fun setPreview(next: Int) {
        val changed = next != (preview ?: currentValue)
        preview = next
        if (changed) {
            haptics.pageTurn()
            previewChanged(next)
        }
    }
    Canvas(
        modifier.then(if (vertical) Modifier.width(48.dp) else Modifier.fillMaxWidth().height(TsuzukiSpacing.touchTarget))
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .semantics {
                contentDescription = label
                stateDescription = displayed.toString()
                progressBarRangeInfo = ProgressBarRangeInfo(displayed.toFloat(), valueRange.first.toFloat()..valueRange.last.toFloat(), (valueRange.last - valueRange.first - 1).coerceAtLeast(0))
                if (!available) disabled()
                setProgress { target ->
                    if (available) commit(target.roundToInt().coerceIn(valueRange))
                    available
                }
            }
            .onKeyEvent { event ->
                if (!available || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val delta = when (event.key) {
                    Key.DirectionLeft -> if (reversed && !vertical) 1 else -1
                    Key.DirectionRight -> if (reversed && !vertical) -1 else 1
                    Key.DirectionUp -> -1
                    Key.DirectionDown -> 1
                    else -> return@onKeyEvent false
                }
                commit((currentValue + delta).coerceIn(valueRange))
                true
            }
            .focusable(available)
            .pointerInput(valueRange, vertical, reversed, available) {
                if (!available) return@pointerInput
                val padding = 16.dp.toPx()
                fun rawFractionAt(position: Offset): Float {
                    val length = (if (vertical) size.height else size.width).toFloat()
                    return (((if (vertical) position.y else position.x) - padding) / (length - padding * 2).coerceAtLeast(1f)).coerceIn(0f, 1f)
                }
                fun valueAtRawFraction(raw: Float): Int {
                    val logical = if (reversed) 1f - raw else raw
                    return (valueRange.first + logical * (valueRange.last - valueRange.first)).roundToInt().coerceIn(valueRange)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    try {
                        val startRaw = rawFractionAt(down.position)
                        dragFraction = startRaw
                        setPreview(valueAtRawFraction(startRaw))
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed || event.changes.any { it.id != down.id && it.pressed }) break
                            if (!change.pressed) {
                                val endRaw = rawFractionAt(change.position)
                                dragFraction = endRaw
                                val next = valueAtRawFraction(endRaw)
                                setPreview(next)
                                commit(next)
                                change.consume()
                                break
                            }
                            val raw = rawFractionAt(change.position)
                            dragFraction = raw
                            setPreview(valueAtRawFraction(raw))
                            change.consume()
                        }
                    } finally {
                        val released = dragFraction
                        scope.launch {
                            if (released != null) visualFraction.snapTo(released)
                            dragFraction = null
                        }
                        preview = null
                        previewChanged(null)
                    }
                }
            },
    ) {
        val padding = 16.dp.toPx()
        val trackThickness = 6.dp.toPx()
        val length = if (vertical) size.height else size.width
        val cross = if (vertical) size.width / 2f else size.height / 2f
        val fill = if (available) inkOnGlass(colors.accent, colors.text, colors.glassTint) else colors.secondary
        val fraction = dragFraction ?: visualFraction.value
        val spanPx = (length - padding * 2f).coerceAtLeast(0f)
        val thumb = if (vertical) {
            Offset(cross, padding + fraction * spanPx)
        } else {
            Offset(padding + fraction * spanPx, cross)
        }
        if (vertical) {
            drawRoundRect(
                colors.separator,
                topLeft = Offset(cross - trackThickness / 2f, padding),
                size = Size(trackThickness, spanPx),
                cornerRadius = CornerRadius(trackThickness / 2f),
            )
            val fillTop = if (reversed) thumb.y else padding
            val fillHeight = if (reversed) (length - padding - thumb.y) else (thumb.y - padding)
            drawRoundRect(
                fill,
                topLeft = Offset(cross - trackThickness / 2f, fillTop),
                size = Size(trackThickness, fillHeight.coerceAtLeast(0f)),
                cornerRadius = CornerRadius(trackThickness / 2f),
            )
            withTransform({ rotate(90f, pivot = thumb) }) {
                drawSliderThumbGap(thumb, pressedFraction, lensFraction)
                drawSliderLens(
                    centre = thumb,
                    lens = lensFraction,
                    rtl = reversed,
                    fill = fill,
                    rest = colors.separator,
                    knob = sliderKnobFill(colors, lensFraction, opaqueGlass),
                    darkGlass = colors.isDark,
                )
                drawSliderBarHandle(fill, thumb, pressedFraction, lensFraction)
            }
        } else {
            drawRoundRect(
                colors.separator,
                topLeft = Offset(padding, cross - trackThickness / 2f),
                size = Size(spanPx, trackThickness),
                cornerRadius = CornerRadius(trackThickness / 2f),
            )
            val fillLeft = if (reversed) thumb.x else padding
            val fillWidth = if (reversed) (length - padding - thumb.x) else (thumb.x - padding)
            drawRoundRect(
                fill,
                topLeft = Offset(fillLeft, cross - trackThickness / 2f),
                size = Size(fillWidth.coerceAtLeast(0f), trackThickness),
                cornerRadius = CornerRadius(trackThickness / 2f),
            )
            drawSliderThumbGap(thumb, pressedFraction, lensFraction)
            drawSliderLens(
                centre = thumb,
                lens = lensFraction,
                rtl = reversed,
                fill = fill,
                rest = colors.separator,
                knob = sliderKnobFill(colors, lensFraction, opaqueGlass),
                darkGlass = colors.isDark,
            )
            drawSliderBarHandle(fill, thumb, pressedFraction, lensFraction)
        }
    }
}
