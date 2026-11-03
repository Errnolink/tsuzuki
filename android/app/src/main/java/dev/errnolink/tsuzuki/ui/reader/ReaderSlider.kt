package dev.errnolink.tsuzuki.ui.reader

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing
import kotlin.math.roundToInt

internal enum class ReaderSliderStyle { Knob, Playhead }

@Composable
internal fun ReaderSlider(
    value: Int,
    valueRange: IntRange,
    onCommit: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Page",
    vertical: Boolean = false,
    reversed: Boolean = false,
    enabled: Boolean = true,
    onPreview: (Int?) -> Unit = {},
    style: ReaderSliderStyle = ReaderSliderStyle.Knob,
) {
    val colors = TsuzukiTheme.colors
    val haptics = TsuzukiTheme.haptics
    val motion = TsuzukiTheme.motion
    var preview by remember(valueRange) { mutableStateOf<Int?>(null) }
    val rail by animateDpAsState(
        if (preview != null) 6.dp else 4.dp,
        if (motion.reduced) snap<Dp>() else motion.tabSpring(),
        label = "rail",
    )
    val currentValue by rememberUpdatedState(value.coerceIn(valueRange))
    val commit by rememberUpdatedState(onCommit)
    val previewChanged by rememberUpdatedState(onPreview)
    val available = enabled && valueRange.last > valueRange.first
    val displayed = preview ?: currentValue
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
                fun valueAt(position: Offset): Int {
                    val length = (if (vertical) size.height else size.width).toFloat()
                    var fraction = ((if (vertical) position.y else position.x) - padding) / (length - padding * 2).coerceAtLeast(1f)
                    if (reversed) fraction = 1f - fraction
                    return (valueRange.first + fraction.coerceIn(0f, 1f) * (valueRange.last - valueRange.first)).roundToInt()
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    try {
                        setPreview(valueAt(down.position))
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed || event.changes.any { it.id != down.id && it.pressed }) break
                            if (!change.pressed) {
                                commit(preview ?: currentValue)
                                change.consume()
                                break
                            }
                            setPreview(valueAt(change.position))
                            change.consume()
                        }
                    } finally {
                        preview = null
                        previewChanged(null)
                    }
                }
            },
    ) {
        val padding = 16.dp.toPx()
        val length = if (vertical) size.height else size.width
        val cross = if (vertical) size.width / 2 else size.height / 2
        val start = if (vertical) Offset(cross, padding) else Offset(padding, cross)
        val end = if (vertical) Offset(cross, length - padding) else Offset(length - padding, cross)
        var fraction = (displayed - valueRange.first).toFloat() / (valueRange.last - valueRange.first).coerceAtLeast(1)
        if (reversed) fraction = 1f - fraction
        val thumb = if (vertical) Offset(cross, padding + fraction * (length - padding * 2)) else Offset(padding + fraction * (length - padding * 2), cross)
        when (style) {
            ReaderSliderStyle.Knob -> {
                drawLine(colors.separator, start, end, 3.dp.toPx(), StrokeCap.Round)
                drawLine(if (available) colors.accent else colors.secondary, if (reversed) end else start, thumb, 3.dp.toPx(), StrokeCap.Round)
                drawCircle(colors.separator, if (preview != null) 12.dp.toPx() else 8.dp.toPx(), thumb)
                drawCircle(colors.text, if (preview != null) 10.dp.toPx() else 6.dp.toPx(), thumb)
            }
            ReaderSliderStyle.Playhead -> {
                drawLine(colors.separator, start, end, rail.toPx(), StrokeCap.Round)
                drawLine(if (available) colors.accent else colors.secondary, if (reversed) end else start, thumb, rail.toPx(), StrokeCap.Round)
                drawLine(colors.text, Offset(thumb.x, thumb.y - 12.dp.toPx()), Offset(thumb.x, thumb.y + 12.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}
