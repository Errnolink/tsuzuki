package dev.errnolink.tsuzuki.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * Kasane button groups (`components/button.css` group rules, `js/lens.js` squeeze, `web/docs-data.js`
 * id "button-group"). Standard groups keep their natural widths; pressing an item grows it 15 % while
 * its neighbours give way — a middle press splits the loss, an edge press takes it all from its one
 * neighbour — on a fast spatial spring that waits for the squeeze to pass 75 % before returning, so a
 * quick tap always shows it. Connected groups replace segmented buttons: 2 dp gaps, inner corners that
 * tighten while held, and a selected item that rounds out into a full pill; the shape is the feedback,
 * not the size. Single or multi select via each item's `selected`.
 */

/** Release gate: the return spring only starts once the squeeze has passed this fraction (or 1 s). */
internal const val SqueezeReleaseGate = 0.75f

internal const val SqueezeGrowth = 0.15f

/**
 * Pixel deltas for a press. The pressed item wants `growth` of its own width; neighbours give up to
 * `max(minNeighbourPx, width * minNeighbourFraction)`; the deltas always sum to zero, so the group
 * width never changes.
 */
internal fun squeezeDeltas(
    widths: List<Float>,
    pressedIndex: Int,
    growth: Float = SqueezeGrowth,
    minNeighbourFraction: Float = 0.6f,
    minNeighbourPx: Float = 32f,
): List<Float> {
    val count = widths.size
    if (count < 2 || pressedIndex !in 0 until count) return List(count) { 0f }
    val want = widths[pressedIndex] * growth
    fun give(j: Int): Float = maxOf(0f, widths[j] - maxOf(minNeighbourPx, widths[j] * minNeighbourFraction))
    val l = if (pressedIndex > 0) pressedIndex - 1 else -1
    val r = if (pressedIndex < count - 1) pressedIndex + 1 else -1
    var sl = 0f
    var sr = 0f
    if (l >= 0 && r >= 0) {
        sl = minOf(want / 2f, give(l))
        sr = minOf(want - sl, give(r))
        if (sl + sr < want) sl = minOf(want - sr, give(l))
    } else if (l >= 0) {
        sl = minOf(want, give(l))
    } else if (r >= 0) {
        sr = minOf(want, give(r))
    }
    val grow = sl + sr
    return List(count) {
        when (it) {
            pressedIndex -> grow
            l -> -sl
            r -> -sr
            else -> 0f
        }
    }
}

/**
 * Corner radii [topStart, topEnd, bottomEnd, bottomStart] of a connected-group item: the outer ends
 * stay full, the inner corners tighten while held, and a selected item rounds into a full pill.
 */
internal fun connectedGroupRadii(
    index: Int,
    count: Int,
    height: Dp,
    selected: Boolean,
    pressFraction: Float,
    inner: Dp = 8.dp,
    innerPressed: Dp = 4.dp,
): List<Dp> {
    val capsule = height / 2
    if (count <= 1 || index < 0 || index >= count) return listOf(capsule, capsule, capsule, capsule)
    if (selected) return listOf(capsule, capsule, capsule, capsule)
    val innerRadius = lerp(inner, innerPressed, pressFraction.coerceIn(0f, 1f))
    return when {
        index == 0 -> listOf(capsule, innerRadius, innerRadius, capsule)
        index == count - 1 -> listOf(innerRadius, capsule, capsule, innerRadius)
        else -> listOf(innerRadius, innerRadius, innerRadius, innerRadius)
    }
}

fun buttonGroupContentColor(selected: Boolean?, colors: TsuzukiColors): Color =
    if (selected == true) colors.onAccent else colors.text

private class ButtonGroupState {
    val progress = Animatable(0f)
    var pressedIndex by mutableIntStateOf(-1)
    val restWidths = mutableStateListOf<Float>()
    var deltas by mutableStateOf(emptyList<Float>())
}

private class ButtonItemSpec(
    val onClick: () -> Unit,
    val modifier: Modifier,
    val enabled: Boolean,
    val selected: Boolean?,
    val content: @Composable RowScope.() -> Unit,
)

interface ButtonGroupScope {
    /**
     * One item. Pass [selected] to make it a toggle: the group then reports selection semantics and
     * a connected group draws it as the rounded-out pill.
     */
    fun item(
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        selected: Boolean? = null,
        content: @Composable RowScope.() -> Unit,
    )
}

@Composable
fun ButtonGroup(
    modifier: Modifier = Modifier,
    /** Connected: 2 dp gaps, joined corners, selection pills. Default is a standard group. */
    connected: Boolean = false,
    /** Connected only: items share the row equally. */
    fill: Boolean = false,
    /** Connected only: item height. */
    height: Dp = 40.dp,
    content: ButtonGroupScope.() -> Unit,
) {
    val scope = remember { ButtonGroupScopeImpl() }
    scope.items.clear()
    content(scope)
    val state = remember { ButtonGroupState() }
    val reduced = TsuzukiTheme.motion.reduced
    val count = scope.items.size
    LaunchedEffect(count) {
        while (state.restWidths.size < count) state.restWidths.add(0f)
    }
    LaunchedEffect(state.pressedIndex, count) {
        if (reduced) return@LaunchedEffect
        if (state.pressedIndex >= 0) {
            state.deltas = squeezeDeltas(state.restWidths.toList(), state.pressedIndex)
            state.progress.animateTo(1f, KasaneSprings.fastSpatial(reduced))
        } else if (state.progress.value > 0f) {
            if (state.progress.value < SqueezeReleaseGate) {
                val up = launch { state.progress.animateTo(1f, KasaneSprings.fastSpatial(reduced)) }
                withTimeoutOrNull(1000) {
                    snapshotFlow { state.progress.value }.first { it >= SqueezeReleaseGate }
                }
                up.cancel()
            }
            state.progress.animateTo(0f, KasaneSprings.fastSpatial(reduced))
            state.deltas = emptyList()
        }
    }
    if (connected) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            scope.items.forEachIndexed { index, spec ->
                ConnectedItem(index, count, height, fill, reduced, spec)
            }
        }
    } else {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            scope.items.forEachIndexed { index, spec ->
                SqueezedItem(state, index, reduced, spec)
            }
        }
    }
}

/** Width override that carries the squeeze: rest + delta · progress, total width constant. */
private fun Modifier.squeezeWidth(state: ButtonGroupState, index: Int, restWidth: State<Float>): Modifier =
    layout { measurable, constraints ->
        val p = state.progress.value
        val rest = restWidth.value
        val delta = state.deltas.getOrNull(index) ?: 0f
        if (p > 0.001f && rest > 0f && delta != 0f) {
            val w = (rest + delta * p).roundToInt().coerceAtLeast(1)
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
private fun RowScope.SqueezedItem(state: ButtonGroupState, index: Int, reduced: Boolean, spec: ButtonItemSpec) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    LaunchedEffect(pressed) {
        if (pressed) {
            state.pressedIndex = index
        } else if (state.pressedIndex == index) {
            state.pressedIndex = -1
        }
    }
    var restWidth by remember { mutableFloatStateOf(0f) }
    val restState = rememberUpdatedState(restWidth)
    Box(
        Modifier
            .onSizeChanged { if (state.progress.value <= 0.001f) restWidth = it.width.toFloat() }
            .then(if (reduced) Modifier else Modifier.squeezeWidth(state, index, restState))
            .then(spec.modifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = spec.enabled,
                role = Role.Button,
                onClick = spec.onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalTsuzukiContentColor provides buttonGroupContentColor(spec.selected, TsuzukiTheme.colors),
        ) {
            Row(content = spec.content)
        }
    }
}

@Composable
private fun RowScope.ConnectedItem(
    index: Int,
    count: Int,
    height: Dp,
    fill: Boolean,
    reduced: Boolean,
    spec: ButtonItemSpec,
) {
    val colors = TsuzukiTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by rememberPressFraction(
        interaction,
        pressIn = KasaneSprings.fastSpatial(reduced),
        release = KasaneSprings.fastSpatial(reduced),
    )
    val toggled = spec.selected == true
    val radii = connectedGroupRadii(index, count, height, toggled, press)
    val shape = RoundedCornerShape(radii[0], radii[1], radii[2], radii[3])
    val contentColor = buttonGroupContentColor(spec.selected, colors)
    val fill0 = if (toggled) colors.accent else colors.text.copy(alpha = if (colors.isDark) 0.10f else 0.06f)
    val itemModifier = if (fill) Modifier.weight(1f) else Modifier
    Box(
        itemModifier
            .height(height)
            .then(spec.modifier)
            .clip(shape)
            .background(fill0, shape)
            .then(
                when {
                    spec.selected != null -> Modifier.toggleable(
                        value = toggled,
                        enabled = spec.enabled,
                        role = Role.Checkbox,
                        onValueChange = { spec.onClick() },
                        interactionSource = interaction,
                        indication = null,
                    )
                    else -> Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        enabled = spec.enabled,
                        role = Role.Button,
                        onClick = spec.onClick,
                    )
                },
            )
            .then(if (spec.selected != null) Modifier.semantics { selected = toggled } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (pressed) {
            Box(Modifier.matchParentSize().alpha(0.12f).background(contentColor))
        }
        CompositionLocalProvider(
            LocalTsuzukiContentColor provides contentColor,
        ) {
            Row(Modifier.padding(horizontal = if (fill) 8.dp else 0.dp), content = spec.content)
        }
    }
}

private class ButtonGroupScopeImpl : ButtonGroupScope {
    val items = mutableListOf<ButtonItemSpec>()

    override fun item(
        onClick: () -> Unit,
        modifier: Modifier,
        enabled: Boolean,
        selected: Boolean?,
        content: @Composable RowScope.() -> Unit,
    ) {
        items += ButtonItemSpec(onClick, modifier, enabled, selected, content)
    }
}
