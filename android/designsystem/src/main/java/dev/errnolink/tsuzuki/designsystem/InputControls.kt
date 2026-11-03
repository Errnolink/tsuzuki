package dev.errnolink.tsuzuki.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(options.isNotEmpty() && selectedIndex in options.indices)
    val colors = TsuzukiTheme.colors
    val motion = TsuzukiTheme.motion
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val largeFont = LocalDensity.current.fontScale > 1.15f
    val position by animateFloatAsState(
        selectedIndex.toFloat(), if (motion.reduced) snap() else motion.tabSpring(), label = "Segment selection",
    )
    val track = SystemFill.copy(alpha = if (colors.isDark) 0.24f else 0.12f)
    val thumb = if (colors.isDark) colors.selectedFill else Color.White
    BoxWithConstraints(modifier.heightIn(min = TsuzukiSpacing.touchTarget).clip(CircleShape).background(track).padding(2.dp)) {
        val cellWidth = maxWidth / options.size
        val cellPx = with(LocalDensity.current) { cellWidth.toPx() }
        Box(Modifier.matchParentSize()) {
            Box(
                Modifier.width(cellWidth).fillMaxHeight().graphicsLayer {
                    translationX = position.coerceIn(0f, options.lastIndex.toFloat()) * cellPx * if (rtl) -1f else 1f
                }
                    .shadow(if (colors.isDark) 0.dp else 3.dp, CircleShape, clip = false)
                    .background(thumb, CircleShape),
            )
        }
        Row(Modifier.fillMaxWidth().fillMaxHeight().selectableGroup()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    Modifier.weight(1f).clip(CircleShape)
                        .selectable(selected, role = Role.RadioButton, onClick = { onSelect(index) })
                        .heightIn(min = 44.dp).padding(horizontal = 10.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    TsuzukiText(
                        label,
                        style = TsuzukiTheme.typography.subhead.copy(
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        color = colors.text,
                        maxLines = if (largeFont) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

internal val SystemFill = Color(0xFF787880)

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String,
    enabled: Boolean = true,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = TsuzukiTheme.colors
    val labels = LocalTsuzukiComponentLabels.current
    val shape = RoundedCornerShape(TsuzukiCorners.field)
    val focusManager = LocalFocusManager.current
    var draft by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(value, TextRange(value.length)))
    }
    if (draft.text != value) draft = TextFieldValue(value, TextRange(value.length))
    Row(
        modifier.clip(shape).background(colors.grouped).heightIn(min = TsuzukiSpacing.touchTarget)
            .padding(start = 14.dp, end = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TsuzukiIcon(TsuzukiIcons.Search, null, Modifier.size(19.dp), tint = colors.secondary)
        BasicTextField(
            value = draft,
            onValueChange = {
                val changed = draft.text != it.text
                draft = it
                if (changed) onValueChange(it.text)
            },
            enabled = enabled,
            modifier = Modifier.weight(1f).padding(vertical = 12.dp)
                .semantics { contentDescription = placeholder },
            textStyle = TsuzukiTheme.typography.body.copy(color = colors.text),
            cursorBrush = SolidColor(colors.accent), singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(); focusManager.clearFocus() }),
            decorationBox = { inner ->
                Box {
                    if (draft.text.isEmpty()) TsuzukiText(placeholder, color = colors.secondary, maxLines = 1)
                    inner()
                }
            },
        )
        if (draft.text.isNotEmpty()) IconButton(
            labels.clearSearch,
            { draft = TextFieldValue(""); onValueChange("") },
            enabled = enabled,
        ) { TsuzukiIcon(TsuzukiIcons.X, null, Modifier.size(18.dp)) }
        trailing?.invoke(this)
    }
}

@Composable
fun SliderRow(
    title: String,
    value: Int,
    valueString: String,
    range: IntProgression,
    enabled: Boolean,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TsuzukiTheme.colors
    val motion = TsuzukiTheme.motion
    val haptics = TsuzukiTheme.haptics
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val first = range.first
    val last = range.last
    val span = (last - first).coerceAtLeast(1)
    val animated by animateFloatAsState(
        value.toFloat(), if (motion.reduced) snap() else motion.pressSpring(), label = "Slider value",
    )
    Column(modifier.padding(horizontal = 16.dp, vertical = 12.dp).alpha(if (enabled) 1f else 0.45f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TsuzukiText(title, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            TsuzukiText(valueString, Modifier, TsuzukiTheme.typography.subhead, colors.secondary)
        }
        var trackWidth by remember { mutableStateOf(1f) }
        var dragging by remember { mutableStateOf(false) }
        fun valueAt(x: Float): Int {
            val raw = if (isRtl) (1f - (x / trackWidth).coerceIn(0f, 1f)) else (x / trackWidth).coerceIn(0f, 1f)
            val v = first + raw * span
            return if (range.step > 1) (v / range.step).toInt() * range.step else v.roundToInt()
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(TsuzukiSpacing.touchTarget)
                .semantics {
                    contentDescription = title
                    stateDescription = valueString
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        (value - first).toFloat() / span,
                        0f..1f,
                    )
                    if (!enabled) disabled()
                    setProgress { target ->
                        if (enabled) {
                            val targetVal = first + (target.coerceIn(0f, 1f) * span)
                            val next = if (range.step > 1) (targetVal / range.step).toInt() * range.step else targetVal.roundToInt()
                            onChange(next.coerceIn(first, last))
                        }
                        enabled
                    }
                }
                .pointerInput(enabled, first, span, range.step, isRtl) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(onPress = { onChange(valueAt(it.x)) })
                }
                .pointerInput(enabled, first, span, range.step, isRtl) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, _ ->
                        change.consume()
                        val next = valueAt(change.position.x)
                        if (next != value) haptics.select()
                        onChange(next)
                    }
                },
        ) {
            trackWidth = size.width
            val fraction = ((animated - first) / span).coerceIn(0f, 1f)
            val visualFraction = if (isRtl) 1f - fraction else fraction
            val knobRadius = if (dragging) 15.dp.toPx() else 13.dp.toPx()
            val centerY = size.height / 2f
            drawRoundRect(
                colors.separator,
                topLeft = Offset(0f, centerY - 2.dp.toPx()),
                size = Size(size.width, 4.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
            if (isRtl) {
                val startX = (size.width * visualFraction).coerceAtMost(size.width - knobRadius)
                drawRoundRect(
                    colors.accent,
                    topLeft = Offset(startX, centerY - 2.dp.toPx()),
                    size = Size(size.width - startX, 4.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            } else {
                drawRoundRect(
                    colors.accent,
                    topLeft = Offset(0f, centerY - 2.dp.toPx()),
                    size = Size((size.width * fraction).coerceAtLeast(knobRadius), 4.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
            val knobX = if (isRtl) {
                (size.width * visualFraction).coerceIn(knobRadius, size.width - knobRadius)
            } else {
                (size.width * fraction).coerceIn(knobRadius, size.width - knobRadius)
            }
            drawCircle(colors.grouped, knobRadius, Offset(knobX, centerY))
            drawCircle(
                colors.separator.copy(alpha = 0.6f),
                knobRadius,
                Offset(knobX, centerY),
                style = Stroke(0.5.dp.toPx()),
            )
        }
    }
}
