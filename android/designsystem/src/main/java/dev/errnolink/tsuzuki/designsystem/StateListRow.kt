package dev.errnolink.tsuzuki.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.min

sealed interface RowState {
    data class Action(val label: String, val destructive: Boolean = false, val onClick: () -> Unit) : RowState
    data class Circle(val icon: ImageVector, val contentDescription: String?, val destructive: Boolean = false, val onClick: () -> Unit) : RowState
    data class Done(val text: String) : RowState
    data object None : RowState
}

/** Which side a [SwipeRow]'s actions sit on. */
enum class SwipeEdge { Start, End }

/** One revealed action of a [SwipeRow] (existing row actions only). */
data class SwipeActionSpec(
    val icon: ImageVector,
    val label: String,
    val background: Color,
    val onClick: () -> Unit,
    /** The full-swipe action commits on a swipe past the threshold or a strong fling. */
    val full: Boolean = false,
    /** True: the row springs back after the action; false: the row flies out (the caller removes it). */
    val keep: Boolean = true,
)

/**
 * Kasane swipe-row numbers, pure for the behaviour tests: a full swipe past 58 % of the row commits
 * the full action, a fling over 0.5 px/ms commits too (once the reveal is past 60 % of the actions),
 * the row resists past the actions (harder without a full action), and a release past half the
 * actions (or a fling) stays open.
 */
object SwipeRowSpec {
    const val COMMIT_FRACTION = 0.58f
    const val FLING_PX_PER_MS = 0.5f
    const val FLING_COMMIT_EXPOSURE = 0.6f
    const val RESIST_FULL = 0.78f
    const val RESIST_PLAIN = 0.3f
    const val EXIT_MS = 200

    /** Row offset past the actions: clamped at zero and resisted with [RESIST_FULL]/[RESIST_PLAIN]. */
    fun resisted(rawLeftOffset: Float, actionsWidthPx: Float, hasFull: Boolean): Float {
        val x = min(0f, rawLeftOffset)
        val over = -x - actionsWidthPx
        return if (over > 0f) -actionsWidthPx - over * (if (hasFull) RESIST_FULL else RESIST_PLAIN) else x
    }

    /** The threshold haptic edge: armed once the reveal passes 58 % of the row. */
    fun armed(resistedLeftOffset: Float, rowWidthPx: Float): Boolean =
        rowWidthPx > 0f && -resistedLeftOffset / rowWidthPx >= COMMIT_FRACTION

    /** Full-swipe commit: armed on release, or a strong fling while the reveal is past 60 %. */
    fun commits(resistedLeftOffset: Float, velocityLeftPxPerMs: Float, actionsWidthPx: Float, rowWidthPx: Float, hasFull: Boolean): Boolean =
        hasFull && (
            armed(resistedLeftOffset, rowWidthPx) ||
                (velocityLeftPxPerMs < -FLING_PX_PER_MS * 1.6f && -resistedLeftOffset > actionsWidthPx * FLING_COMMIT_EXPOSURE)
            )

    /** Where a plain release lands: fling open, fling closed, otherwise past half the actions. */
    fun openOnRelease(resistedLeftOffset: Float, velocityLeftPxPerMs: Float, actionsWidthPx: Float): Boolean =
        when {
            velocityLeftPxPerMs < -FLING_PX_PER_MS -> true
            velocityLeftPxPerMs > FLING_PX_PER_MS -> false
            else -> resistedLeftOffset < -actionsWidthPx / 2f
        }
}


val StateRowCoverWidth = 40.dp
val StateRowCoverHeight = 56.dp
val StateRowCoverCorner = 9.dp
val StateRowTileSize = 40.dp
val StateRowTileCorner = 12.dp

fun stateRowCapsuleFill(accent: Color, dark: Boolean): Color = accent.copy(alpha = if (dark) 0.20f else 0.14f)

internal fun stateRowActionFill(colors: TsuzukiColors, destructive: Boolean): Color =
    if (destructive) {
        colors.destructive.copy(alpha = if (colors.isDark) 0.16f else 0.12f)
    } else {
        stateRowCapsuleFill(colors.accent, colors.isDark)
    }

@Composable
fun StateListRow(
    leading: (@Composable () -> Unit)?,
    title: String,
    subtitle: String?,
    progress: Float?,
    indeterminate: Boolean,
    trailing: RowState,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    divider: Boolean = false,
    dividerStart: Dp = if (leading != null) 68.dp else 16.dp,
) {
    val colors = TsuzukiTheme.colors
    val rowModifier = modifier
        .heightIn(min = 56.dp)
        .then(
            when {
                onClick != null && onLongClick != null -> Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                onClick != null -> Modifier.clickable(onClick = onClick)
                else -> Modifier
            },
        )
    Row(
        rowModifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            TsuzukiText(title, style = TsuzukiTheme.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                TsuzukiText(
                    subtitle,
                    style = TsuzukiTheme.typography.footnote,
                    color = colors.secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (progress != null) {
                WavyProgress(progress, Modifier.fillMaxWidth().padding(top = 4.dp).height(12.dp))
            } else if (indeterminate) {
                WavyProgress(null, Modifier.fillMaxWidth().padding(top = 4.dp).height(12.dp))
            }
        }
        StateRowTrailing(trailing)
    }
    if (divider) {
        Spacer(Modifier.padding(start = dividerStart).fillMaxWidth().height(TsuzukiSpacing.hairline).background(colors.separator))
    }
}

@Composable
private fun StateRowTrailing(state: RowState) {
    when (state) {
        is RowState.Action -> {
            val colors = TsuzukiTheme.colors
            val interaction = remember { MutableInteractionSource() }
            val pressState = rememberPressShapeState(interaction, restRadius = 17.dp)
            val shape = if (pressState.enabled) pressState.shape else CircleShape
            Box(
                Modifier
                    .then(if (pressState.enabled) Modifier.pressShapeScale(pressState) else Modifier.pressScale(interaction))
                    .minimumInteractiveComponentSize()
                    .defaultMinSize(minWidth = 34.dp, minHeight = 34.dp)
                    .clip(shape)
                    .background(stateRowActionFill(colors, state.destructive))
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = state.onClick)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                TsuzukiText(
                    state.label,
                    style = TsuzukiTheme.typography.footnote.copy(fontWeight = FontWeight.SemiBold),
                    color = if (state.destructive) colors.destructive else colors.accent,
                )
            }
        }
        is RowState.Circle -> {
            val colors = TsuzukiTheme.colors
            val interaction = remember { MutableInteractionSource() }
            val pressState = rememberPressShapeState(interaction, restRadius = 20.dp)
            val shape = if (pressState.enabled) pressState.shape else CircleShape
            Box(
                Modifier
                    .then(if (pressState.enabled) Modifier.pressShapeScale(pressState) else Modifier.pressScale(interaction))
                    .minimumInteractiveComponentSize()
                    .size(40.dp)
                    .clip(shape)
                    .background(colors.elevated)
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = state.onClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    state.icon,
                    state.contentDescription,
                    Modifier.size(19.dp),
                    tint = if (state.destructive) colors.destructive else colors.text,
                )
            }
        }
        is RowState.Done -> TsuzukiText(state.text, style = TsuzukiTheme.typography.footnote, color = TsuzukiTheme.colors.secondary)
        RowState.None -> Unit
    }
}

/**
 * Kasane swipe row (`swipe-row`): the row follows the finger 1:1, resists past the actions, a full
 * swipe past 58 % of the row commits the full action with a haptic (a fling commits too), and any
 * other release springs open or closed. Wrap a row once per side; the chapter rows stack both.
 */
@Composable
fun SwipeRow(
    actions: List<SwipeActionSpec>,
    modifier: Modifier = Modifier,
    edge: SwipeEdge = SwipeEdge.End,
    /** The row's own fill; it covers the actions at rest (Kasane `--k-swipe-bg`). */
    surface: Color = TsuzukiTheme.colors.background,
    content: @Composable () -> Unit,
) {
    if (actions.isEmpty()) {
        Box(modifier) { content() }
        return
    }
    val density = LocalDensity.current
    val reduced = TsuzukiTheme.motion.reduced
    val haptics = LocalHaptics.current
    // Row offset in left-space px: negative means the actions are revealed.
    var offset by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }
    // Settles run beside the gesture loop, so a touch during one catches the row instead of being lost.
    val scope = rememberCoroutineScope()
    var settle by remember { mutableStateOf<Job?>(null) }
    fun settleTo(target: Float) {
        settle?.cancel()
        settle = scope.launch {
            Animatable(offset).animateTo(target, KasaneSprings.settle(reduced)) { offset = value }
        }
    }
    // Kasane: each action is at least 76 dp wide; the tray widens with the reveal past that.
    val actionsWidth = with(density) { (SwipeActionWidth * actions.size).roundToPx() }
    var rowWidth by remember { mutableIntStateOf(0) }
    val hasFull = actions.any { it.full }
    val full = actions.firstOrNull { it.full } ?: actions.last()

    // Kasane .is-stretch: when armed, the full action stretches across the whole reveal.
    val fill by animateFloatAsState(
        if (armed) 1f else 1f / actions.size,
        KasaneSprings.settle(reduced),
        label = "swipe fill",
    )
    val stretchFraction = if (actions.size > 1) {
        ((fill * actions.size - 1f) / (actions.size - 1f)).coerceIn(0f, 1f)
    } else {
        0f
    }

    fun commit(action: SwipeActionSpec) {
        action.onClick()
        if (action.keep) offset = 0f
    }

    val side = if (edge == SwipeEdge.End) Alignment.CenterEnd else Alignment.CenterStart
    Box(modifier.clipToBounds()) {
        if (offset != 0f) {
            Box(Modifier.matchParentSize()) {
                val trayWidth = with(density) { maxOf(actionsWidth.toFloat(), -offset).toDp() }
                Row(
                    Modifier
                        .align(side)
                        .fillMaxHeight()
                        .width(trayWidth)
                        .graphicsLayer { alpha = 1f - stretchFraction },
                ) {
                    actions.forEach { action ->
                        SwipeActionButton(action, Modifier.weight(1f).fillMaxHeight()) { commit(action) }
                    }
                }
                if (actions.size > 1 && stretchFraction > 0f) {
                    Box(
                        Modifier
                            .align(side)
                            .fillMaxHeight()
                            .width(trayWidth * stretchFraction),
                    ) {
                        SwipeActionButton(full, Modifier.fillMaxSize()) { commit(full) }
                    }
                }
            }
        }
        Box(
            Modifier
                .offset { IntOffset((if (edge == SwipeEdge.End) offset else -offset).toInt(), 0) }
                .background(surface)
                .onSizeChanged { rowWidth = it.width }
                // An open row takes the next touch for itself: it closes instead of opening the row.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        if (offset != 0f) down.consume()
                    }
                }
                .pointerInput(edge, actions.size) {
                    // awaitEachGesture loops on its own; everything a gesture does, its release included,
                    // happens inside the block.
                    awaitEachGesture {
                        val tracker = VelocityTracker()
                        var started = false
                        val down = awaitFirstDown(requireUnconsumed = false)
                        settle?.cancel()
                        var dxTotal = 0f
                        var dyTotal = 0f
                        // Finger-to-left-space direction: an End row reveals on a leftward drag.
                        val sign = if (edge == SwipeEdge.End) 1f else -1f
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed) break
                            val delta = change.positionChange()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            dxTotal += delta.x
                            dyTotal += delta.y
                            if (!started) {
                                val slop = viewConfiguration.touchSlop.toFloat()
                                if (abs(dxTotal) > slop && abs(dxTotal) >= abs(dyTotal)) started = true
                            }
                            if (started) {
                                val raw = offset + delta.x * sign
                                val resisted = SwipeRowSpec.resisted(raw, actionsWidth.toFloat(), hasFull)
                                val newArmed = hasFull && SwipeRowSpec.armed(resisted, rowWidth.toFloat())
                                if (newArmed != armed) {
                                    armed = newArmed
                                    haptics?.play(TsuzukiHaptic.Detent)
                                }
                                offset = resisted
                                change.consume()
                            }
                            if (!change.pressed) break
                        }
                        if (!started) {
                            // A tap on an open row closes it; anything else leaves the row where it is.
                            if (offset != 0f) settleTo(0f)
                            return@awaitEachGesture
                        }
                        val releaseOffset = offset
                        // Tracker reports px/s; the spec thresholds are px/ms.
                        val releaseVelocity = tracker.calculateVelocity().x / 1000f * sign
                        armed = false
                        if (SwipeRowSpec.commits(releaseOffset, releaseVelocity, actionsWidth.toFloat(), rowWidth.toFloat(), hasFull)) {
                            haptics?.play(TsuzukiHaptic.ToggleOn)
                            // The row flies out, the action stays put, then the action runs.
                            settle?.cancel()
                            scope.launch {
                                Animatable(releaseOffset).animateTo(
                                    -(rowWidth + 8f),
                                    tween(SwipeRowSpec.EXIT_MS, easing = M3Motion.StandardAccelerate),
                                ) { offset = value }
                                commit(full)
                                if (full.keep) {
                                    Animatable(offset).animateTo(0f, KasaneSprings.settle(reduced)) { offset = value }
                                }
                            }
                        } else {
                            val open = SwipeRowSpec.openOnRelease(releaseOffset, releaseVelocity, actionsWidth.toFloat())
                            if (open) haptics?.play(TsuzukiHaptic.Select)
                            settleTo(if (open) -actionsWidth.toFloat() else 0f)
                        }
                    }
                },
        ) {
            content()
        }
    }
}

@Composable
private fun SwipeActionButton(
    action: SwipeActionSpec,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .background(action.background)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = action.label
            },
        contentAlignment = Alignment.Center,
    ) {
        val ink = if (action.background.luminance() > 0.45f) Color.Black.copy(alpha = 0.87f) else Color.White
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(action.icon, null, Modifier.size(22.dp), tint = ink)
            TsuzukiText(
                action.label,
                style = TsuzukiTheme.typography.caption1.copy(fontWeight = FontWeight.SemiBold),
                color = ink,
                maxLines = 1,
            )
        }
    }
}

private val SwipeActionWidth = 76.dp
