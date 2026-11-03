package dev.errnolink.tsuzuki.designsystem

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

/** SystemUI back-commit/cancel curve for predictive-back returns. */
private val BackReturnEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

enum class SheetDetent { FitContent, Medium, Large }

data class SheetSettle(val target: Float, val dismiss: Boolean)

object DetentSheetGeometry {
    const val MediumFraction = 0.5f
    const val FitFloorFraction = 0.25f
    const val DragProjectionShare = 0.16f
    const val StrongFlingShare = 0.45f
    const val WeakFlingShare = 0.6f
    const val ProjectedLargeShare = 0.76f
    const val ProjectedRestShare = 0.25f

    fun quantize(px: Float, gridPx: Float): Float = (px / gridPx).roundToInt() * gridPx

    fun detentOffset(detent: SheetDetent, heightPx: Float, fitSheetHeightPx: Float): Float {
        val sheetHeight = when (detent) {
            SheetDetent.Large -> heightPx
            SheetDetent.Medium -> heightPx * MediumFraction
            SheetDetent.FitContent -> fitSheetHeightPx.coerceAtMost(heightPx).coerceAtLeast(heightPx * FitFloorFraction)
        }
        return (heightPx - sheetHeight).coerceIn(0f, heightPx)
    }

    fun releaseTarget(
        offsetPx: Float,
        velocityPx: Float,
        heightPx: Float,
        flingThresholdPx: Float,
        restPx: Float,
    ): SheetSettle {
        val projected = offsetPx + velocityPx * DragProjectionShare
        val target = when {
            velocityPx > flingThresholdPx && offsetPx >= heightPx * StrongFlingShare -> heightPx
            velocityPx < -flingThresholdPx -> if (offsetPx > heightPx * WeakFlingShare) heightPx / 2f else 0f
            projected > heightPx * ProjectedLargeShare -> heightPx
            projected > heightPx * ProjectedRestShare -> restPx
            else -> 0f
        }
        return SheetSettle(target, dismiss = target >= heightPx)
    }

    fun shouldSettle(isAnimating: Boolean, currentTargetPx: Float, targetPx: Float, gridPx: Float): Boolean =
        !isAnimating || abs(targetPx - currentTargetPx) > gridPx

    const val SettleWallClockBoundMillis = 750L

    fun shouldSkipSettle(dismissing: Boolean, isAnimating: Boolean, currentTargetPx: Float, targetPx: Float, gridPx: Float): Boolean =
        dismissing || !shouldSettle(isAnimating, currentTargetPx, targetPx, gridPx)
}

@Composable
fun DetentSheet(
    visible: Boolean,
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    initialDetent: SheetDetent = SheetDetent.FitContent,
    scrollContent: Boolean = true,
    centered: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = TsuzukiTheme.colors
    val motion = TsuzukiTheme.motion
    val tokens = LocalMotionTokens.current
    val labels = LocalTsuzukiComponentLabels.current
    val haptics = TsuzukiTheme.haptics
    val scope = rememberCoroutineScope()
    val dismissRequest by rememberUpdatedState(onDismissRequest)
    var hiding by remember { mutableStateOf(false) }
    var offset by remember { mutableFloatStateOf(Float.NaN) }
    var detent by remember { mutableStateOf(initialDetent) }
    var animation by remember { mutableStateOf<Job?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var dismissing by remember { mutableStateOf(false) }
    var currentTarget by remember { mutableFloatStateOf(0f) }
    var scrimReveal by remember { mutableFloatStateOf(0f) }
    var sheetAlpha by remember { mutableFloatStateOf(1f) }
    val backPreview = remember { Animatable(0f) }
    var viewportHeight by remember { mutableFloatStateOf(0f) }
    var chromeHeight by remember { mutableFloatStateOf(0f) }
    var intrinsicHeight by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val tracker = remember { VelocityCarryTracker() }
    val flingThreshold = with(density) { 800.dp.toPx() }
    val gridPx = with(density) { 8.dp.toPx() }
    val quantizedFitSheetHeight = DetentSheetGeometry.quantize(
        chromeHeight + intrinsicHeight + with(density) { (24.dp).toPx() } +
            (if (centered) 0 else WindowInsets.navigationBars.getBottom(density)),
        gridPx,
    )

    fun reveal(progress: Float) {
        scrimReveal = progress
    }

    fun settle(target: Float, velocity: Float = 0f, dismiss: Boolean = false) {
        if (dismissing && !dismiss) return
        animation?.cancel()
        currentTarget = target
        dismissing = dismiss
        val start = offset.takeIf { it.isFinite() } ?: viewportHeight
        val presenting = !dismiss && start >= viewportHeight - 0.5f
        val velocityDriven = !dismiss && !presenting && abs(velocity) >= flingThreshold
        val trackedReveal = if (dismiss) 0f else 1f
        animation = scope.launch {
            val animated = withTimeoutOrNull(DetentSheetGeometry.SettleWallClockBoundMillis) {
                animateSheetOffset(start, target, velocity, viewportHeight, motion, dismiss, velocityDriven, tokens) { value, progress ->
                    offset = value.coerceIn(0f, viewportHeight)
                    sheetAlpha = when {
                        !motion.reduced -> 1f
                        dismiss -> 1f - progress
                        presenting -> progress
                        else -> 1f
                    }
                    if (scrimReveal != trackedReveal || dismiss) reveal(if (dismiss) 1f - progress else progress)
                }
            } != null
            if (!animated) {
                offset = target.coerceIn(0f, viewportHeight)
                sheetAlpha = 1f
                reveal(if (dismiss) 0f else 1f)
            }
            if (dismiss) {
                dismissRequest()
                if (hiding) hiding = false
            }
        }
    }

    fun close() {
        if (dismissing) return
        val velocity = tracker.recentSampledVelocity().coerceAtLeast(0f)
        tracker.reset()
        settle(viewportHeight, velocity = velocity, dismiss = true)
    }

    fun targetFor(target: SheetDetent): Float =
        DetentSheetGeometry.detentOffset(target, viewportHeight, quantizedFitSheetHeight)

    fun resize(target: SheetDetent) {
        detent = target
        settle(targetFor(target))
    }

    fun drag(delta: Float): Float {
        if (dismissing) return 0f
        animation?.cancel()
        val old = offset.takeIf { it.isFinite() } ?: 0f
        offset = (old + delta).coerceIn(0f, viewportHeight)
        tracker.onMove(offset)
        if (viewportHeight > 0f) reveal((offset / viewportHeight).coerceIn(0f, 1f))
        sheetAlpha = 1f
        return offset - old
    }

    fun release(velocity: Float) {
        val effectiveVelocity = tracker.resolveReleaseVelocity(velocity, offset.takeIf { it.isFinite() } ?: 0f)
        tracker.reset()
        val rest = if (detent == SheetDetent.FitContent) targetFor(SheetDetent.FitContent) else viewportHeight / 2f
        val settleTo = DetentSheetGeometry.releaseTarget(
            offset.takeIf { it.isFinite() } ?: 0f,
            effectiveVelocity,
            viewportHeight,
            flingThreshold,
            rest,
        )
        if (settleTo.target == 0f) detent = SheetDetent.Large
        dragging = false
        settle(settleTo.target, effectiveVelocity, dismiss = settleTo.dismiss)
    }

    val nestedScroll = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            if (source == NestedScrollSource.UserInput && available.y < 0f && offset > 0f) Offset(0f, drag(available.y)) else Offset.Zero

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            if (source == NestedScrollSource.UserInput && available.y > 0f) Offset(0f, drag(available.y)) else Offset.Zero

        override suspend fun onPreFling(available: Velocity): Velocity = if (offset > 0f) {
            release(available.y)
            Velocity(0f, available.y)
        } else {
            Velocity.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (offset > 0f) release(available.y)
            return Velocity.Zero
        }
    }
    LaunchedEffect(visible) {
        if (visible) {
            if (hiding) {
                animation?.cancel()
                dismissing = false
                hiding = false
            }
            offset = Float.NaN
            detent = initialDetent
            currentTarget = 0f
            scrimReveal = 0f
            sheetAlpha = 1f
            dragging = false
            backPreview.snapTo(0f)
            tracker.reset()
            if (viewportHeight > 0f) settle(targetFor(initialDetent))
        } else if (offset.isFinite() && !dismissing) {
            hiding = true
            close()
        }
    }
    if (!visible && !hiding) return
    Dialog(
        onDismissRequest = ::close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        CompositionLocalProvider(LocalInsideGlass provides false) {
        PredictiveBackHandler(enabled = !dismissing && !dragging) { progress ->
            try {
                progress.collect { event ->
                    if (!motion.reduced) backPreview.snapTo(M3Motion.StandardDecelerate.transform(event.progress))
                }
                close()
            } catch (e: CancellationException) {
                if (!motion.reduced && backPreview.value > 0f) {
                    backPreview.animateTo(0f, tween(M3Motion.Short4, easing = BackReturnEasing))
                }
                throw e
            }
        }
        Box(Modifier.fillMaxSize()) {
            if (scrimReveal > 0f) {
                Box(
                    Modifier.fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f * scrimReveal))
                        .clickable(interactionSource = null, indication = null, onClick = ::close)
                        .clearAndSetSemantics {},
                )
            }
            BoxWithConstraints(
                Modifier.fillMaxSize().statusBarsPadding().padding(top = 12.dp)
                    .then(if (centered) Modifier.imePadding() else Modifier),
            ) {
                val heightPx = with(density) { maxHeight.toPx() }
                val imeVisible = WindowInsets.ime.getBottom(density) > 0
                LaunchedEffect(heightPx, quantizedFitSheetHeight, imeVisible, motion.reduced, density.fontScale) {
                    viewportHeight = heightPx
                    if (dragging || dismissing) return@LaunchedEffect
                    if (!centered && (imeVisible || density.fontScale >= 1.5f || maxHeight < 360.dp)) detent = SheetDetent.Large
                    val target = targetFor(detent)
                    if (!offset.isFinite()) offset = heightPx
                    if (!DetentSheetGeometry.shouldSkipSettle(dismissing, animation?.isActive == true, currentTarget, target, gridPx)) {
                        settle(target)
                    }
                }
                val sheetOffset = offset.takeIf { it.isFinite() } ?: viewportHeight
                GlassSurface(
                    context = null,
                    modifier = modifier
                        .align(if (centered) Alignment.Center else Alignment.BottomCenter)
                        .widthIn(max = if (centered) 480.dp else 640.dp).fillMaxWidth()
                        .then(
                            when {
                                detent == SheetDetent.Large -> Modifier
                                centered -> Modifier.padding(horizontal = 24.dp)
                                else -> Modifier.padding(horizontal = 8.dp)
                            },
                        )
                        .height(with(density) { (heightPx - sheetOffset).coerceAtLeast(0f).toDp() })
                        .graphicsLayer {
                            if (motion.reduced) alpha = sheetAlpha
                            val preview = backPreview.value
                            if (preview > 0f) {
                                transformOrigin = TransformOrigin(0.5f, 1f)
                                scaleX = 1f - (48.dp.toPx() * preview) / size.width.coerceAtLeast(1f)
                                scaleY = 1f - (24.dp.toPx() * preview) / size.height.coerceAtLeast(1f)
                            }
                        }
                        .nestedScroll(nestedScroll)
                        .semantics { paneTitle = title; dismiss { close(); true } },
                    shape = if (detent == SheetDetent.Large) {
                        RoundedCornerShape(topStart = TsuzukiCorners.sheet, topEnd = TsuzukiCorners.sheet)
                    } else {
                        RoundedCornerShape(TsuzukiCorners.sheet)
                    },
                    material = if (detent == SheetDetent.Large) GlassMaterial.Opaque else GlassMaterial.Thick,
                ) {
                    val contentScroll = rememberScrollState()
                    Column(Modifier.fillMaxSize()) {
                        Column(Modifier.onSizeChanged { chromeHeight = it.height.toFloat() }) {
                            val draggable = Modifier.draggable(
                                state = rememberDraggableState { drag(it) }, orientation = Orientation.Vertical,
                                onDragStarted = { animation?.cancel(); dragging = true; tracker.onStart(offset.takeIf { it.isFinite() } ?: 0f) },
                                onDragStopped = { release(it) },
                            )
                            Box(
                                Modifier.fillMaxWidth().height(44.dp).then(draggable)
                                    .clickable {
                                        if (!centered) {
                                            haptics.select()
                                            resize(if (detent == SheetDetent.Large) SheetDetent.FitContent else SheetDetent.Large)
                                        }
                                    }
                                    .semantics {
                                        contentDescription = labels.resizeSheet
                                        stateDescription = when (detent) {
                                            SheetDetent.Large -> labels.sheetLarge
                                            SheetDetent.Medium -> labels.sheetMedium
                                            SheetDetent.FitContent -> labels.sheetFit
                                        }
                                        expand { resize(SheetDetent.Large); true }
                                        collapse { resize(if (detent == SheetDetent.Large) SheetDetent.FitContent else SheetDetent.Medium); true }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Spacer(Modifier.size(36.dp, 5.dp).background(colors.separator, CircleShape))
                            }
                            Row(
                                Modifier.fillMaxWidth().then(draggable).padding(start = 12.dp, end = 20.dp, bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                IconButton(labels.closeSheet(title), ::close) { TsuzukiIcon(TsuzukiIcons.X, null, Modifier.size(20.dp)) }
                                TsuzukiText(title, Modifier.weight(1f), TsuzukiTheme.typography.title3)
                            }
                            Spacer(
                                Modifier.fillMaxWidth().height(TsuzukiSpacing.hairline)
                                    .background(if (scrollContent && contentScroll.value > 0) colors.separator else Color.Transparent),
                            )
                        }
                        Column(
                            Modifier.weight(1f)
                                .then(if (scrollContent) Modifier.verticalScroll(contentScroll) else Modifier)
                                .then(if (centered) Modifier else Modifier.navigationBarsPadding())
                                .imePadding()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            CompositionLocalProvider(LocalGroupedListGutter provides 0.dp) {
                                Column(
                                    Modifier.onSizeChanged { intrinsicHeight = it.height.toFloat() },
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    content = content,
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

private suspend fun animateSheetOffset(
    start: Float,
    target: Float,
    velocity: Float,
    height: Float,
    motion: TsuzukiMotion,
    closing: Boolean,
    velocityDriven: Boolean,
    standardTokens: Boolean,
    update: (Float, Float) -> Unit,
) {
    if (start == target) {
        update(target, 1f)
        return
    }
    if (motion.reduced) {
        val presenting = !closing && start >= height - 0.5f
        if (!closing && !presenting) {
            update(target, 1f)
            return
        }
        val position = if (closing) start else target
        val fade = TargetBasedAnimation(M3Motion.reducedFade(), Float.VectorConverter, 0f, 1f)
        val duration = fade.durationNanos
        val started = withFrameNanos { it }
        do {
            val elapsed = withFrameNanos { it } - started
            update(position, fade.getValueFromNanos(elapsed.coerceAtMost(duration)))
        } while (elapsed < duration)
        update(position, 1f)
        return
    }
    val presenting = !closing && start >= height - 0.5f
    val spec: FiniteAnimationSpec<Float> = when {
        closing -> M3Motion.exit()
        presenting -> if (standardTokens) M3Motion.enter(M3Motion.Medium3) else M3Motion.enter()
        velocityDriven -> motion.sheetSpring()
        else -> M3Motion.emphasized(M3Motion.Medium2)
    }
    val animation = TargetBasedAnimation(spec, Float.VectorConverter, start, target, velocity)
    val naturalDuration = animation.durationNanos
    val maxDuration = if (velocityDriven) naturalDuration.coerceIn(150_000_000L, 500_000_000L) else naturalDuration
    val started = withFrameNanos { it }
    val span = start - target
    do {
        val elapsed = withFrameNanos { it } - started
        val playTime = elapsed.coerceAtMost(naturalDuration)
        val value = animation.getValueFromNanos(playTime)
        update(value, if (span != 0f) ((start - value) / span).coerceIn(0f, 1f) else 1f)
    } while (elapsed < maxDuration && elapsed < naturalDuration)
    update(target, 1f)
}
