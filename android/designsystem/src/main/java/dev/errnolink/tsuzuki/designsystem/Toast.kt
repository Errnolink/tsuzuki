package dev.errnolink.tsuzuki.designsystem

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sign

/**
 * Toast (Kasane `toast`): stacked confirmations — the newest in front, older ones tucked behind
 * (three visible, scaled 5 % and raised 12 dp a step), expanded to natural heights with a 14 dp gap
 * on tap, swipe to dismiss with velocity, promise toasts that morph from loading to the result, and
 * timers that pause while you read, while a swipe is under way and while the app is backgrounded.
 * On phones it is a glass pill; the shell mounts it at the bottom centre.
 */
enum class ToastKind { Default, Success, Error, Warning, Info, Loading }

data class ToastAction(val label: String, val onClick: () -> Unit)

/** Kasane's numbers, kept as pure functions for the behaviour tests. */
object ToastSpec {
    /** Toasts rendered behind the front one; deeper ones stay parked until room frees up. */
    const val VISIBLE = 3

    /** Expanded stack: natural heights with this gap; collapsed, each step tucks and scales. */
    val GAP = 14.dp
    val TUCK = 12.dp
    const val STEP_SCALE = 0.05f

    /** Swipe to dismiss: 45 px of travel or a fling over 0.11 px/ms in the leaving direction. */
    const val DISMISS_PX = 45f
    const val DISMISS_VELOCITY_PX_PER_MS = 0.11f

    /** 4 s alone, 6 s with an action (WCAG 2.2.1); loading toasts never time out. */
    const val BASE_DURATION_MS = 4_000L
    const val ACTION_DURATION_MS = 6_000L
    const val EXIT_MS = 200

    /** Rest pose of the toast [depth] steps behind the front (0 = front). */
    fun tuck(depth: Int): Dp = TUCK * depth

    fun scale(depth: Int): Float = 1f - STEP_SCALE * depth

    /** True when the toast at this depth is rendered rather than parked behind the stack. */
    fun visible(depth: Int): Boolean = depth < VISIBLE

    /** Duration rule: explicit request wins, loading never times out, actions buy six seconds. */
    fun durationMs(kind: ToastKind, hasAction: Boolean, requested: Long?): Long = when {
        requested != null -> requested
        kind == ToastKind.Loading -> 0L
        hasAction -> ACTION_DURATION_MS
        else -> BASE_DURATION_MS
    }

    /** Wrong-direction drag resistance: d / (1.5 + |d| / 20), Kasane's damp. */
    fun damp(deltaPx: Float): Float = deltaPx / (1.5f + abs(deltaPx) / 20f)

    /**
     * The release decision. [distancePx] is the settled drag along the locked axis, [velocityPxPerMs]
     * the recent fling, [awaySign] points off the nearest screen edge (+1 downward for a bottom
     * stack); [horizontal] is whether the axis locked sideways. Sideways, either direction counts on
     * a centred stack (the drag's own direction leads); vertically only travel toward the screen
     * edge — however far — or a fling in that direction commits.
     */
    fun dismisses(
        distancePx: Float,
        velocityPxPerMs: Float,
        awaySign: Float,
        horizontal: Boolean,
    ): Boolean {
        val direction = if (horizontal) sign(distancePx) else awaySign
        val fling = abs(velocityPxPerMs) > DISMISS_VELOCITY_PX_PER_MS && sign(velocityPxPerMs) == direction
        val far = abs(distancePx) >= DISMISS_PX && sign(distancePx) == direction
        return far || fling
    }
}

class ToastHandle internal constructor(
    val id: Long,
    private val state: ToastHostState,
) {
    /** Re-skins the live toast: loading → success morphs the icon, swaps the text, resizes the pill. */
    fun update(
        message: String,
        kind: ToastKind = ToastKind.Default,
        description: String? = null,
        action: ToastAction? = null,
        durationMs: Long? = null,
    ) {
        state.update(id, message, kind, description, action, durationMs)
    }

    fun dismiss() = state.dismiss(id)
}

class ToastHostState {
    internal var entries by mutableStateOf<List<ToastEntry>>(emptyList())
    private val heights = mutableStateMapOf<Long, Int>()
    private var nextId = 0L
    internal var expanded by mutableStateOf(false)

    fun show(
        message: String,
        kind: ToastKind = ToastKind.Default,
        description: String? = null,
        action: ToastAction? = null,
        durationMs: Long? = null,
        closeButton: Boolean = kind == ToastKind.Error,
    ): ToastHandle {
        val id = ++nextId
        entries = entries + ToastEntry(
            id, message, kind, description, action, closeButton,
            ToastSpec.durationMs(kind, action != null, durationMs),
        )
        while (entries.count { !it.removing } > QUEUE) {
            entries.firstOrNull { !it.removing }?.let { dismiss(it.id) }
        }
        return ToastHandle(id, this)
    }

    internal fun update(
        id: Long,
        message: String,
        kind: ToastKind,
        description: String?,
        action: ToastAction?,
        durationMs: Long?,
    ) {
        val index = entries.indexOfFirst { it.id == id && !it.removing }
        if (index < 0) return
        val old = entries[index]
        entries = entries.toMutableList().apply {
            set(
                index,
                ToastEntry(
                    id, message, kind,
                    description ?: old.description,
                    action ?: old.action,
                    kind == ToastKind.Error || old.closeButton,
                    ToastSpec.durationMs(kind, action != null || old.action != null, durationMs),
                    previousKind = old.kind,
                ),
            )
        }
    }

    fun dismiss(id: Long) {
        val entry = entries.firstOrNull { it.id == id && !it.removing } ?: return
        entry.removing = true
        entry.swiping = false
        if (!entries.any { !it.removing }) expanded = false
    }

    fun dismissAll() {
        entries.forEach { if (!it.removing) dismiss(it.id) }
    }

    /** Reactive count of toasts that are still shown or exiting. */
    val liveCount: Int
        get() = entries.count { !it.removing }

    /** True while the toast is shown or its exit animation is running. */
    fun isLive(id: Long): Boolean = entries.any { it.id == id && !it.removing }

    internal fun setHeight(id: Long, height: Int) {
        heights[id] = height
    }

    internal fun heightOf(id: Long): Int = heights[id] ?: 0

    internal fun prune(entry: ToastEntry) {
        entries = entries - entry
        heights.remove(entry.id)
    }

    private companion object {
        /** Kasane keeps ten live toasts at most; the rest are dismissed oldest-first. */
        const val QUEUE = 10
    }
}

internal class ToastEntry(
    val id: Long,
    message: String,
    kind: ToastKind,
    description: String?,
    action: ToastAction?,
    closeButton: Boolean,
    remainingMs: Long,
    previousKind: ToastKind? = null,
) {
    val neverTimesOut = remainingMs == 0L
    var message by mutableStateOf(message)
    var kind by mutableStateOf(kind)
    var description by mutableStateOf(description)
    var action by mutableStateOf(action)
    var closeButton by mutableStateOf(closeButton)
    var remainingMs by mutableStateOf(remainingMs)
    var previousKind by mutableStateOf(previousKind ?: kind)
    var removing by mutableStateOf(false)
    var swiping by mutableStateOf(false)
    /** Last depth in the live stack; reused while the exit animation runs. */
    var depth by mutableIntStateOf(0)
}

/**
 * The stacked toast host. Mount once near the bottom of the shell; [glass] is the shell glass
 * context so the pill picks up live blur where one is active. Tap the stack to expand it to natural
 * heights, tap again (or tap outside, or press Escape) to fold it.
 */
@Composable
fun ToastHost(
    state: ToastHostState,
    modifier: Modifier = Modifier,
    glass: GlassContext? = null,
) {
    val live = state.entries.filter { !it.removing }
    val density = LocalDensity.current
    val reduced = TsuzukiTheme.motion.reduced

    // Timers pause while the app is backgrounded (Kasane: document.hidden).
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            resumed = event == Lifecycle.Event.ON_RESUME
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val frontHeightPx = live.lastOrNull()?.let { state.heightOf(it.id) } ?: 0
    val stackHeight: Dp = if (state.expanded) {
        var total = 0.dp
        live.forEachIndexed { index, entry ->
            if (index > 0) total += ToastSpec.GAP
            total += with(density) { state.heightOf(entry.id).toDp() }
        }
        total
    } else if (live.isNotEmpty()) {
        with(density) { frontHeightPx.toDp() } +
            ToastSpec.tuck((live.size - 1).coerceAtMost(ToastSpec.VISIBLE - 1))
    } else {
        0.dp
    }
    val animatedStack by animateDpAsState(stackHeight, KasaneSprings.nav(reduced), label = "toast stack")

    Box(
        modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(Modifier.height(animatedStack)) {
            state.entries.forEach { entry ->
                key(entry.id) {
                    ToastItem(
                        entry = entry,
                        expanded = state.expanded,
                        resumed = resumed,
                        frontHeightPx = frontHeightPx,
                        state = state,
                        glass = glass,
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.ToastItem(
    entry: ToastEntry,
    expanded: Boolean,
    resumed: Boolean,
    frontHeightPx: Int,
    state: ToastHostState,
    glass: GlassContext?,
) {
    val reduced = TsuzukiTheme.motion.reduced
    val density = LocalDensity.current
    val haptics = LocalHaptics.current
    val live = state.entries.filter { !it.removing }
    val liveIndex = live.indexOfFirst { it.id == entry.id }
    val isFront = liveIndex >= 0 && liveIndex == live.lastIndex

    SideEffect {
        if (liveIndex >= 0) entry.depth = live.size - 1 - liveIndex
    }
    val depth = entry.depth

    // Entrance: from past the bottom edge onto the stack, on a spatial spring that lands large.
    val enter = remember(entry.id) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(entry, reduced) {
        if (!reduced && enter.value < 1f) {
            enter.animateTo(1f, spring(KasaneSprings.BouncyDamping, KasaneSprings.BouncyStiffness))
        } else {
            enter.snapTo(1f)
        }
    }

    // Exit: sink toward the edge and fade — effects, never overshoot.
    val exit = remember(entry.id) { Animatable(1f) }
    LaunchedEffect(entry.removing) {
        if (entry.removing) {
            if (!reduced) {
                exit.animateTo(0f, tween(ToastSpec.EXIT_MS, easing = M3Motion.StandardAccelerate))
            } else {
                exit.animateTo(0f, tween(M3Motion.ReducedFade, easing = M3Motion.Standard))
            }
            state.prune(entry)
        }
    }

    // Timer: the remaining time survives pauses (expanded stack, swipe in progress, backgrounded).
    // A toast that expires while paused (or in the completion race) still leaves at the next pass.
    LaunchedEffect(entry, expanded, entry.swiping, resumed) {
        val total = entry.remainingMs
        if (total <= 0L) {
            if (!entry.neverTimesOut) state.dismiss(entry.id)
            return@LaunchedEffect
        }
        if (!resumed || expanded || entry.swiping) return@LaunchedEffect
        val start = SystemClock.elapsedRealtime()
        try {
            delay(total)
            entry.remainingMs = 0L
            state.dismiss(entry.id)
        } catch (cancelled: CancellationException) {
            entry.remainingMs -= (SystemClock.elapsedRealtime() - start).coerceAtLeast(0L)
            throw cancelled
        }
    }

    // Pose: expanded, natural heights with a gap; collapsed, front-height pills tucked and scaled.
    val natural = with(density) { state.heightOf(entry.id).toDp() }
    val front = with(density) { frontHeightPx.toDp() }
    val own = if (natural > 0.dp) natural else front
    val clamped = if (isFront) own else if (front > 0.dp) front else own

    val belowHeights = if (expanded && liveIndex > 0) {
        var total = 0.dp
        live.take(liveIndex).forEach { total += with(density) { state.heightOf(it.id).toDp() } }
        total + ToastSpec.GAP * liveIndex
    } else {
        0.dp
    }
    val restY = if (expanded) -belowHeights else -ToastSpec.tuck(depth)
    val restScale = if (expanded) 1f else ToastSpec.scale(depth)
    val restHeight = if (expanded) own else clamped

    val labels = LocalTsuzukiComponentLabels.current
    val animatedY by animateDpAsState(restY, KasaneSprings.nav(reduced), label = "toast tuck")
    val animatedScale by animateFloatAsState(restScale, KasaneSprings.nav(reduced), label = "toast scale")
    val animatedHeight by animateDpAsState(restHeight, KasaneSprings.nav(reduced), label = "toast clamp")

    // Swipe to dismiss: axis locks after the slop, wrong directions resist, release decides.
    var drag by remember { mutableStateOf(Offset.Zero) }
    var pillSize by remember { mutableStateOf(IntSize.Zero) }
    var swipeAxis by remember { mutableIntStateOf(-1) }
    val dragProgress = when (swipeAxis) {
        0 -> if (pillSize.width > 0) minOf(1f, abs(drag.x) / (pillSize.width * 0.8f)) else 0f
        1 -> minOf(1f, abs(drag.y) / 110f)
        else -> 0f
    }

    val itemModifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .graphicsLayer {
            val enterOffset = clamped * (1f - enter.value) * 0.9f
            translationY = (animatedY + enterOffset).toPx() +
                drag.y + (1f - exit.value) * 28.dp.toPx()
            translationX = drag.x
            scaleX = animatedScale * (0.94f + 0.06f * enter.value)
            scaleY = animatedScale * (0.94f + 0.06f * enter.value)
            alpha = enter.value * exit.value * (1f - dragProgress)
            transformOrigin = TransformOrigin(0.5f, 0.5f)
        }

    if (!ToastSpec.visible(depth) && !expanded) {
        // Parked toasts stay composed (their heights feed the expanded layout) but stay invisible.
        Box(itemModifier.height(animatedHeight).onSizeChanged { state.setHeight(entry.id, it.height) })
        return
    }

    val gestureScope = rememberCoroutineScope()
    val contentVisible by animateFloatAsState(
        if (expanded || isFront) 1f else 0f,
        tween(M3Motion.Short3, easing = M3Motion.Standard),
        label = "toast content",
    )

    Box(
        itemModifier
            .height(animatedHeight)
            .clipToBounds()
            .onSizeChanged { pillSize = it }
            .pointerInput(entry.id) {
                // awaitEachGesture loops on its own; the release runs inside the block and hands its
                // animation to a scope (the block is a restricted suspend scope).
                awaitEachGesture {
                    val tracker = VelocityTracker()
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var total = Offset.Zero
                    var axis = -1
                    var stolen = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) {
                            stolen = true
                            break
                        }
                        val delta = change.positionChange()
                        tracker.addPosition(change.uptimeMillis, change.position)
                        if (axis < 0) {
                            total += delta
                            val slop = viewConfiguration.touchSlop.toFloat()
                            if (abs(total.x) > slop || abs(total.y) > slop) {
                                axis = if (abs(total.x) > abs(total.y)) 0 else 1
                                entry.swiping = true
                                swipeAxis = axis
                            }
                        }
                        if (axis >= 0) {
                            val dx = if (axis == 0) delta.x else ToastSpec.damp(delta.x)
                            val dy = if (axis == 1) delta.y else ToastSpec.damp(delta.y)
                            drag += Offset(dx, dy)
                            change.consume()
                        }
                        if (!change.pressed) break
                    }
                    entry.swiping = false
                    if (axis < 0) {
                        // A tap on the pill itself (a child button consumes its tap) folds or spreads the stack.
                        if (!stolen) state.expanded = !state.expanded
                        return@awaitEachGesture
                    }
                    val releaseDrag = drag
                    // The tracker reports px/s; the spec thresholds are px/ms (Kasane's numbers).
                    val velocity = tracker.calculateVelocity()
                    val away = 1f // a bottom stack leaves downward
                    val dismiss = if (axis == 0) {
                        ToastSpec.dismisses(releaseDrag.x, velocity.x / 1000f, away, horizontal = true)
                    } else {
                        ToastSpec.dismisses(releaseDrag.y, velocity.y / 1000f, away, horizontal = false)
                    }
                    if (dismiss && !entry.removing) {
                        haptics?.play(TsuzukiHaptic.Select)
                        // Thrown out past the edge, then the host runs its sink-and-fade exit.
                        val thrown = if (axis == 0) {
                            Offset(sign(releaseDrag.x) * (pillSize.width + 40.dp.toPx()), 0f)
                        } else {
                            Offset(0f, pillSize.height + 80.dp.toPx())
                        }
                        gestureScope.launch {
                            Animatable(releaseDrag, Offset.VectorConverter).animateTo(
                                thrown,
                                tween(ToastSpec.EXIT_MS + 20, easing = M3Motion.StandardAccelerate),
                                Offset(velocity.x, velocity.y),
                            ) { drag = value }
                            state.dismiss(entry.id)
                            drag = Offset.Zero
                        }
                    } else {
                        gestureScope.launch {
                            Animatable(releaseDrag, Offset.VectorConverter).animateTo(
                                Offset.Zero,
                                KasaneSprings.settle(reduced),
                                Offset(velocity.x, velocity.y),
                            ) { drag = value }
                            drag = Offset.Zero
                            swipeAxis = -1
                        }
                    }
                }
            }
            .semantics {
                contentDescription = entry.message
                onClick(label = labels.dismiss) {
                    state.dismiss(entry.id)
                    true
                }
            },
    ) {
        ToastPill(
            entry = entry,
            glass = glass,
            contentVisible = contentVisible,
            onNaturalHeight = { state.setHeight(entry.id, it) },
            onDismiss = { state.dismiss(entry.id) },
        )
    }
}

/** The pill itself: measured at its natural height whatever the host's animated clamp currently is. */
@Composable
private fun ToastPill(
    entry: ToastEntry,
    glass: GlassContext?,
    contentVisible: Float,
    onNaturalHeight: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val reduced = TsuzukiTheme.motion.reduced
    val colors = TsuzukiTheme.colors
    val labels = LocalTsuzukiComponentLabels.current
    GlassSurface(
        context = glass,
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(align = Alignment.Top, unbounded = true)
            .onSizeChanged { onNaturalHeight(it.height) }
            .animateContentSize(KasaneSprings.settle(reduced)),
        shape = RoundedCornerShape(TsuzukiCorners.capsulePercent),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(start = 18.dp, end = 8.dp)
                .alpha(contentVisible),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ToastLead(entry)
            Column(Modifier.weight(1f)) {
                AnimatedContent(
                    entry.message,
                    transitionSpec = {
                        (fadeIn(tween(M3Motion.Short3)) + scaleIn(initialScale = 0.97f, animationSpec = tween(M3Motion.Short3)))
                            .togetherWith(fadeOut(tween(M3Motion.Short3)))
                    },
                    label = "toast text",
                ) { message ->
                    androidx.compose.material3.Text(
                        message,
                        style = TsuzukiTheme.typography.subhead,
                        color = colors.text,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                entry.description?.let { description ->
                    androidx.compose.material3.Text(
                        description,
                        style = TsuzukiTheme.typography.footnote,
                        color = colors.text.copy(alpha = 0.78f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            entry.action?.let { action ->
                val actionInteraction = remember { MutableInteractionSource() }
                androidx.compose.material3.Text(
                    action.label,
                    Modifier
                        .pressScale(actionInteraction)
                        .heightIn(min = 36.dp)
                        .clip(CircleShape)
                        .clickable(actionInteraction, indication = null, role = Role.Button) {
                            onDismiss()
                            action.onClick()
                        }
                        .padding(horizontal = 10.dp)
                        .semantics { role = Role.Button },
                    style = TsuzukiTheme.typography.subhead,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.accent,
                )
            }
            if (entry.closeButton) {
                val closeInteraction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .pressScale(closeInteraction)
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(closeInteraction, indication = null, role = Role.Button) {
                            onDismiss()
                        }
                        .padding(6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    TsuzukiIcon(TsuzukiIcons.X, labels.dismissNotification, Modifier.size(16.dp), colors.text.copy(alpha = 0.7f))
                }
            }
        }
    }
}

/** Tinted lead tile by kind; the loading kind carries a small loading mark. */
@Composable
private fun ToastLead(entry: ToastEntry) {
    val colors = TsuzukiTheme.colors
    val tile = when (entry.kind) {
        ToastKind.Success -> colors.success
        ToastKind.Error -> colors.destructive
        ToastKind.Warning -> colors.secondary
        ToastKind.Info -> colors.accent
        else -> null
    }
    AnimatedContent(
        entry.kind,
        transitionSpec = {
            if (targetState != initialState) {
                (
                    scaleIn(
                        initialScale = 0.4f,
                        animationSpec = spring(KasaneSprings.MorphDamping, KasaneSprings.MorphStiffness),
                    ) + fadeIn(tween(M3Motion.Short4))
                    ).togetherWith(fadeOut(tween(M3Motion.Short3)))
            } else {
                fadeIn(tween(0)).togetherWith(fadeOut(tween(0)))
            }
        },
        label = "toast lead",
        modifier = Modifier.size(24.dp),
    ) { kind ->
        when (kind) {
            ToastKind.Loading -> Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                LoadingMark(Modifier.size(24.dp), size = 24.dp, color = colors.accent)
            }
            ToastKind.Default -> Unit
            else -> Box(
                Modifier.size(24.dp).clip(CircleShape).background(tile ?: colors.secondary),
                contentAlignment = Alignment.Center,
            ) {
                val icon = when (kind) {
                    ToastKind.Success -> TsuzukiIcons.Check
                    ToastKind.Info -> TsuzukiIcons.Info
                    else -> TsuzukiIcons.CircleAlert
                }
                TsuzukiIcon(icon, null, Modifier.size(14.dp), colors.background)
            }
        }
    }
}
