package dev.errnolink.tsuzuki.designsystem
import android.view.WindowManager

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

/** SystemUI back-commit/cancel curve for predictive-back returns. */
private val BackReturnEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

enum class SheetDetent { FitContent, Medium, Large }

enum class SheetKind { Confirm, Picker, LiveSettings, BrowseList, Free }

fun sheetShowsGrabber(kind: SheetKind): Boolean = kind != SheetKind.Confirm

data class SheetHeights(val openHeightPx: Float, val draggableToFull: Boolean)

data class SheetSettle(val target: Float, val dismiss: Boolean)

/**
 * Set by a sheet that hosts pushed pages (the tracking hub): a page's [DetentSheet] renders only
 * its content — no window, chrome or back button of its own. The host's header stays put and
 * morphs its title while only the content slides (guide §4: bar buttons morph in place).
 */
val LocalDetentSheetPage = staticCompositionLocalOf { false }

/** The page reports its title so the host's one header can morph to it. */
val LocalDetentSheetPageTitle = staticCompositionLocalOf<(String) -> Unit> { {} }

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

    /**
     * The sheet's own scrim strength for its current offset (WP4.3): 1 at the resting open
     * offset [restPx], falling monotonically to 0 at dismissed. Tied to position, so dragging
     * and settling share one continuous value (the old drag path used `offset / viewport`,
     * which is inverted — it brightened as the sheet left and jumped at a half detent).
     */
    fun scrimReveal(offsetPx: Float, restPx: Float, viewportPx: Float): Float {
        val span = viewportPx - restPx
        if (!span.isFinite() || span <= 1f) return 0f
        return ((viewportPx - offsetPx) / span).coerceIn(0f, 1f)
    }

    const val ConfirmShare = 0.40f
    const val PickerShare = 0.60f
    const val KindHalfShare = 0.5f

    fun sheetHeights(kind: SheetKind, contentPx: Float, viewportPx: Float, keyboardVisible: Boolean): SheetHeights {
        val content = contentPx.coerceIn(0f, viewportPx)
        return when (kind) {
            SheetKind.Confirm -> SheetHeights(content.coerceAtMost(viewportPx * ConfirmShare), draggableToFull = false)
            SheetKind.Picker -> SheetHeights(
                content.coerceAtLeast(viewportPx * FitFloorFraction).coerceAtMost(viewportPx * PickerShare),
                draggableToFull = true,
            )
            SheetKind.LiveSettings -> SheetHeights(viewportPx * KindHalfShare, draggableToFull = true)
            SheetKind.BrowseList -> SheetHeights(if (keyboardVisible) viewportPx else viewportPx * KindHalfShare, draggableToFull = true)
            SheetKind.Free -> SheetHeights(content, draggableToFull = true)
        }
    }
}

@Composable
fun DetentSheet(
    visible: Boolean,
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    initialDetent: SheetDetent = SheetDetent.Large,
    kind: SheetKind = SheetKind.Picker,
    scrollContent: Boolean = true,
    centered: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalDetentSheetPage.current) {
        // A page inside a hosting sheet: no chrome of its own — the host's header stays put and
        // morphs its title; only this content slides.
        val reportTitle = LocalDetentSheetPageTitle.current
        LaunchedEffect(title) { reportTitle(title) }
        Column(Modifier.fillMaxWidth(), content = content)
        return
    }
    if (LocalWindowWidthClass.current != WindowWidthClass.Compact) {
        WidePanelSheet(
            visible = visible,
            title = title,
            onDismissRequest = onDismissRequest,
            kind = kind,
            scrollContent = scrollContent,
            modifier = modifier,
            content = content,
        )
        return
    }
    val colors = TsuzukiTheme.colors
    val motion = TsuzukiTheme.motion
    val labels = LocalTsuzukiComponentLabels.current
    // N15: a sheet closing hands the screen back — the floating tab bar re-derives expanded.
    val shellGlass = LocalGlassContext.current
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
    val navInsetPx = WindowInsets.navigationBars.getBottom(density)
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val kinded = kind != SheetKind.Free
    val kindedHeights = DetentSheetGeometry.sheetHeights(kind, quantizedFitSheetHeight, viewportHeight, imeVisible)
    // DET-02: the open height is derived from the LIVE viewport, not a value captured one
    // composition earlier. On a sheet's first visible composition viewportHeight is still 0,
    // so a captured kindedOpenHeight was 0 and the present settle targeted "fully hidden";
    // sheets whose content height follows the sheet's own size (a fill-MaxSize LazyColumn)
    // never recovered — the sheet stayed off-screen behind the dialog dim.
    fun kindedOpenHeight(): Float = DetentSheetGeometry.quantize(
        DetentSheetGeometry.sheetHeights(kind, quantizedFitSheetHeight, viewportHeight, imeVisible).openHeightPx,
        gridPx,
    ).coerceIn(0f, viewportHeight)
    // WP4.3: the offset the current open interaction rests at; the scrim is full above it and
    // fades below. Never shrinks within an interaction, so resizing to full does not pop.
    var scrimRest by remember { mutableFloatStateOf(Float.NaN) }
    var atFull by remember { mutableStateOf(false) }

    fun reveal(progress: Float) {
        scrimReveal = progress
    }

    fun settle(target: Float, velocity: Float = 0f, dismiss: Boolean = false) {
        if (dismissing && !dismiss) return
        animation?.cancel()
        currentTarget = target
        dismissing = dismiss
        if (!dismiss) scrimRest = if (scrimRest.isFinite()) maxOf(scrimRest, target) else target
        val start = offset.takeIf { it.isFinite() } ?: viewportHeight
        val presenting = !dismiss && start >= viewportHeight - 0.5f
        val velocityDriven = !dismiss && !presenting && abs(velocity) >= flingThreshold
        val trackedReveal = if (dismiss) 0f else 1f
        animation = scope.launch {
            val animated = withTimeoutOrNull(DetentSheetGeometry.SettleWallClockBoundMillis) {
                animateSheetOffset(start, target, velocity, viewportHeight, motion, dismiss, velocityDriven) { value, progress ->
                    offset = value.coerceIn(0f, viewportHeight)
                    sheetAlpha = when {
                        !motion.reduced -> 1f
                        dismiss -> 1f - progress
                        presenting -> progress
                        else -> 1f
                    }
                    // WP4.3: the scrim follows the sheet's position (full at the resting
                    // offset, clear at dismissed); reduced motion keeps the progress fade.
                    if (motion.reduced) {
                        if (scrimReveal != trackedReveal || dismiss) reveal(if (dismiss) 1f - progress else progress)
                    } else {
                        reveal(DetentSheetGeometry.scrimReveal(value, scrimRest, viewportHeight))
                    }
                }
            } != null
            if (!animated) {
                offset = target.coerceIn(0f, viewportHeight)
                sheetAlpha = 1f
                reveal(DetentSheetGeometry.scrimReveal(target, scrimRest, viewportHeight))
            }
            if (dismiss) {
                dismissRequest()
                shellGlass?.expandNavigation()
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

    fun kindedTarget(full: Boolean): Float =
        if (full) 0f else (viewportHeight - kindedOpenHeight()).coerceIn(0f, viewportHeight)

    fun resize(target: SheetDetent) {
        detent = target
        settle(targetFor(target))
    }

    fun resizeKinded(full: Boolean) {
        atFull = full
        settle(kindedTarget(full))
    }

    fun drag(delta: Float): Float {
        if (dismissing) return 0f
        animation?.cancel()
        val old = offset.takeIf { it.isFinite() } ?: 0f
        val floor = if (kinded && !kindedHeights.draggableToFull) {
            (viewportHeight - kindedOpenHeight()).coerceIn(0f, viewportHeight)
        } else {
            0f
        }
        offset = (old + delta).coerceIn(floor, viewportHeight)
        tracker.onMove(offset)
        // WP4.3: the scrim falls as the sheet leaves its resting offset — the old
        // `offset / viewport` was inverted (it darkened while dragging away) and jumped
        // on the first frame from a half detent.
        if (viewportHeight > 0f) reveal(DetentSheetGeometry.scrimReveal(offset, scrimRest, viewportHeight))
        sheetAlpha = 1f
        return offset - old
    }

    fun release(velocity: Float) {
        val effectiveVelocity = tracker.resolveReleaseVelocity(velocity, offset.takeIf { it.isFinite() } ?: 0f)
        tracker.reset()
        val rest = when {
            kinded -> kindedTarget(false)
            detent == SheetDetent.FitContent -> targetFor(SheetDetent.FitContent)
            else -> viewportHeight / 2f
        }
        val settleTo = DetentSheetGeometry.releaseTarget(
            offset.takeIf { it.isFinite() } ?: 0f,
            effectiveVelocity,
            viewportHeight,
            flingThreshold,
            rest,
        )
        dragging = false
        if (kinded) {
            atFull = !settleTo.dismiss && settleTo.target == 0f && kindedHeights.draggableToFull
            settle(
                if (settleTo.dismiss) settleTo.target else kindedTarget(atFull),
                effectiveVelocity,
                dismiss = settleTo.dismiss,
            )
        } else {
            if (settleTo.target == 0f) detent = SheetDetent.Large
            settle(settleTo.target, effectiveVelocity, dismiss = settleTo.dismiss)
        }
    }

    val sheetDrag = Modifier.draggable(
        state = rememberDraggableState { drag(it) },
        orientation = Orientation.Vertical,
        onDragStarted = { animation?.cancel(); dragging = true; tracker.onStart(offset.takeIf { it.isFinite() } ?: 0f) },
        onDragStopped = { release(it) },
    )
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
            atFull = false
            currentTarget = 0f
            scrimRest = Float.NaN
            sheetAlpha = 1f
            dragging = false
            backPreview.snapTo(0f)
            tracker.reset()
            if (viewportHeight > 0f) settle(if (kinded) kindedTarget(false) else targetFor(initialDetent))
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
        val dialogView = LocalView.current
        LaunchedEffect(dialogView) {
            // WP4.2/MOT-08/DET-02: the platform dialog dim is cleared for every sheet kind
            // (as LiquidCard does) — the scrim is the sheet's own, tied to its position, so it
            // can never sit on its own or outlive the sheet.
            (dialogView.parent as? DialogWindowProvider)?.window?.let { window ->
                window.setDimAmount(0f)
                window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }
        }
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
            if (scrimReveal > 0f && kind != SheetKind.LiveSettings) {
                Box(
                    Modifier.fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f * scrimReveal))
                        .clickable(interactionSource = null, indication = null, onClick = ::close)
                        .clearAndSetSemantics {},
                )
            }
            BoxWithConstraints(
                Modifier.fillMaxSize().statusBarsPadding().padding(top = 12.dp).imePadding(),
            ) {
                val heightPx = with(density) { maxHeight.toPx() }
                val imeVisible = WindowInsets.ime.getBottom(density) > 0
                LaunchedEffect(heightPx, quantizedFitSheetHeight, imeVisible, motion.reduced, density.fontScale) {
                    viewportHeight = heightPx
                    if (dragging || dismissing) return@LaunchedEffect
                    if (!kinded && !centered && (imeVisible || density.fontScale >= 1.5f || maxHeight < 360.dp)) detent = SheetDetent.Large
                    val target = if (kinded) kindedTarget(atFull) else targetFor(detent)
                    if (!offset.isFinite()) offset = heightPx
                    if (!DetentSheetGeometry.shouldSkipSettle(dismissing, animation?.isActive == true, currentTarget, target, gridPx)) {
                        settle(target)
                    }
                }
                // N21: before the first measure lands, the sheet rests fully off-screen (height
                // 0) — never a full-height frame of sheet text over the page text.
                val sheetOffset = offset.takeIf { it.isFinite() } ?: heightPx
                val attachedFull = if (kinded) atFull else detent == SheetDetent.Large
                val sheetTopRadius = TsuzukiCorners.sheet
                GlassSurface(
                    context = null,
                    modifier = modifier
                        .align(if (centered) Alignment.Center else Alignment.BottomCenter)
                        .widthIn(max = if (centered) 480.dp else 640.dp).fillMaxWidth()
                        .then(
                            when {
                                attachedFull -> Modifier
                                centered -> Modifier.padding(horizontal = 24.dp)
                                // WP4.7: a floating sheet insets all four sides or none — the
                                // rounded bottom corners no longer sit flush at the screen edge.
                                else -> Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
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
                    shape = when {
                        attachedFull -> tsuzukiRounded(topStart = sheetTopRadius, topEnd = sheetTopRadius, bottomEnd = 0.dp, bottomStart = 0.dp)
                        else -> tsuzukiRounded(sheetTopRadius)
                    },
                    material = if (attachedFull) {
                        GlassMaterial.Opaque
                    } else {
                        GlassMaterial.Thick
                    },
                ) {
                    Box(Modifier.fillMaxSize()) {
                    val contentScroll = rememberScrollState()
                    val confirmKind = kind == SheetKind.Confirm
                    val resizeModifier = Modifier
                        .clickable {
                            if (!centered) {
                                haptics.select()
                                if (kinded) {
                                    if (kindedHeights.draggableToFull) resizeKinded(!atFull)
                                } else {
                                    resize(if (detent == SheetDetent.Large) SheetDetent.FitContent else SheetDetent.Large)
                                }
                            }
                        }
                        .semantics {
                            contentDescription = labels.resizeSheet
                            stateDescription = when {
                                kinded && atFull -> labels.sheetLarge
                                kinded -> labels.sheetFit
                                detent == SheetDetent.Large -> labels.sheetLarge
                                detent == SheetDetent.Medium -> labels.sheetMedium
                                else -> labels.sheetFit
                            }
                            expand {
                                if (kinded) resizeKinded(true) else resize(SheetDetent.Large)
                                true
                            }
                            collapse {
                                if (kinded) {
                                    resizeKinded(false)
                                } else {
                                    resize(if (detent == SheetDetent.Large) SheetDetent.FitContent else SheetDetent.Medium)
                                }
                                true
                            }
                        }
                    Column(Modifier.fillMaxSize()) {
                        Column(Modifier.onSizeChanged { chromeHeight = it.height.toFloat() }) {
                            if (sheetShowsGrabber(kind)) {
                                Box(
                                    Modifier.fillMaxWidth().height(44.dp).then(sheetDrag).then(resizeModifier),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Spacer(
                                        Modifier.size(36.dp, 5.dp).background(colors.separator, CircleShape),
                                    )
                                }
                            }
                            if (confirmKind) {
                                TsuzukiText(
                                    title,
                                    Modifier.fillMaxWidth().padding(top = 20.dp, start = 20.dp, end = 20.dp),
                                    TsuzukiTheme.typography.headline.copy(textAlign = TextAlign.Center),
                                )
                            } else {
                                Row(
                                    Modifier.fillMaxWidth().then(sheetDrag).padding(start = 12.dp, end = 20.dp, bottom = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    IconButton(labels.closeSheet(title), ::close) { TsuzukiIcon(TsuzukiIcons.X, null, Modifier.size(20.dp)) }
                                    TsuzukiText(
                                        title,
                                        Modifier.weight(1f),
                                        TsuzukiTheme.typography.headline.copy(textAlign = TextAlign.Center),
                                    )
                                    Spacer(Modifier.size(48.dp))
                                }
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
}

private suspend fun animateSheetOffset(
    start: Float,
    target: Float,
    velocity: Float,
    height: Float,
    motion: TsuzukiMotion,
    closing: Boolean,
    velocityDriven: Boolean,
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
        presenting -> M3Motion.enter(M3Motion.Medium3)
        velocityDriven -> KasaneSprings.sheet<Float>()
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

private const val PanelExitMillis = 160

@Composable
private fun WidePanelSheet(
    visible: Boolean,
    title: String,
    onDismissRequest: () -> Unit,
    kind: SheetKind,
    scrollContent: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = TsuzukiTheme.colors
    val motion = TsuzukiTheme.motion
    val labels = LocalTsuzukiComponentLabels.current
    val shellGlass = LocalGlassContext.current
    var hiding by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(false) }
    val backPreview = remember { Animatable(0f) }
    val dismissRequest by rememberUpdatedState(onDismissRequest)
    val endSide = kind == SheetKind.LiveSettings
    fun requestClose() {
        if (hiding || !shown) {
            if (!shown) dismissRequest()
            return
        }
        hiding = true
        shown = false
    }
    LaunchedEffect(visible) {
        if (visible) {
            hiding = false
            shown = true
        } else if (!hiding) {
            hiding = true
            shown = false
        }
    }
    LaunchedEffect(hiding) {
        if (hiding) {
            delay((if (motion.reduced) M3Motion.Short1 else PanelExitMillis).toLong())
            hiding = false
            backPreview.snapTo(0f)
            dismissRequest()
            shellGlass?.expandNavigation()
        }
    }
    if (!visible && !hiding) return
    Dialog(
        onDismissRequest = ::requestClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val dialogView = LocalView.current
        LaunchedEffect(dialogView) {
            // WP4.2: every sheet kind clears the platform dim; the scrim is the sheet's own.
            (dialogView.parent as? DialogWindowProvider)?.window?.let { window ->
                window.setDimAmount(0f)
                window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }
        }
        CompositionLocalProvider(LocalInsideGlass provides false) {
        PredictiveBackHandler(enabled = shown) { progress ->
            try {
                progress.collect { event ->
                    if (!motion.reduced) backPreview.snapTo(M3Motion.StandardDecelerate.transform(event.progress))
                }
                backPreview.snapTo(0f)
                requestClose()
            } catch (e: CancellationException) {
                if (!motion.reduced && backPreview.value > 0f) {
                    backPreview.animateTo(0f, tween(M3Motion.Short4, easing = BackReturnEasing))
                }
                throw e
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            if (!endSide) {
                AnimatedVisibility(
                    visible = shown,
                    enter = fadeIn(if (motion.reduced) M3Motion.reducedFade() else tween(M3Motion.Medium1, easing = M3Motion.EmphasizedDecelerate)),
                    exit = fadeOut(if (motion.reduced) M3Motion.reducedFade() else tween(M3Motion.Short3, easing = M3Motion.EmphasizedAccelerate)),
                ) {
                    Box(
                        Modifier.fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f))
                            .clickable(interactionSource = null, indication = null, onClick = ::requestClose)
                            .clearAndSetSemantics {},
                    )
                }
            }
            val placement = if (endSide) {
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(top = 24.dp, bottom = 24.dp, end = 24.dp)
                    .width(400.dp)
            } else {
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp, vertical = 24.dp)
                    .widthIn(max = 560.dp)
            }
            val enter = if (motion.reduced) {
                fadeIn(M3Motion.reducedFade())
            } else if (endSide) {
                slideInHorizontally(tween(M3Motion.Medium1, easing = M3Motion.EmphasizedDecelerate)) { it } +
                    fadeIn(tween(M3Motion.Short3, easing = M3Motion.EmphasizedDecelerate))
            } else {
                scaleIn(M3Motion.enter(M3Motion.Medium1), initialScale = 0.92f) +
                    fadeIn(tween(M3Motion.Short3, easing = M3Motion.EmphasizedDecelerate))
            }
            val exit = if (motion.reduced) {
                fadeOut(M3Motion.reducedFade())
            } else if (endSide) {
                slideOutHorizontally(tween(M3Motion.Short3, easing = M3Motion.EmphasizedAccelerate)) { it } +
                    fadeOut(tween(M3Motion.Short3, easing = M3Motion.EmphasizedAccelerate))
            } else {
                scaleOut(M3Motion.exit(M3Motion.Short3), targetScale = 0.92f) +
                    fadeOut(tween(M3Motion.Short3, easing = M3Motion.EmphasizedAccelerate))
            }
            AnimatedVisibility(
                visible = shown,
                modifier = placement.then(if (endSide) Modifier else Modifier.imePadding()),
                enter = enter,
                exit = exit,
            ) {
                GlassSurface(
                    context = null,
                    modifier = modifier
                        .fillMaxWidth()
                        .then(
                            if (endSide) {
                                Modifier.fillMaxHeight()
                            } else {
                                Modifier.heightIn(max = maxHeight * 0.8f)
                            },
                        )
                        .graphicsLayer {
                            val preview = backPreview.value
                            if (preview > 0f) {
                                transformOrigin = TransformOrigin(0.5f, 0.5f)
                                scaleX = 1f - (48.dp.toPx() * preview) / size.width.coerceAtLeast(1f)
                                scaleY = 1f - (24.dp.toPx() * preview) / size.height.coerceAtLeast(1f)
                            }
                        }
                        .semantics { paneTitle = title },
                    shape = tsuzukiRounded(TsuzukiCorners.sheet),
                    material = GlassMaterial.Thick,
                ) {
                    val contentScroll = rememberScrollState()
                    Column(Modifier.fillMaxSize()) {
                        if (kind == SheetKind.Confirm) {
                            TsuzukiText(
                                title,
                                Modifier.fillMaxWidth().padding(top = 20.dp, start = 20.dp, end = 20.dp, bottom = 12.dp),
                                TsuzukiTheme.typography.headline.copy(textAlign = TextAlign.Center),
                            )
                        } else {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 12.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                IconButton(labels.closeSheet(title), ::requestClose) {
                                    TsuzukiIcon(TsuzukiIcons.X, null, Modifier.size(20.dp))
                                }
                                TsuzukiText(
                                    title,
                                    Modifier.weight(1f),
                                    TsuzukiTheme.typography.headline.copy(textAlign = TextAlign.Center),
                                )
                                Spacer(Modifier.size(48.dp))
                            }
                        }
                        Spacer(
                            Modifier.fillMaxWidth().height(TsuzukiSpacing.hairline)
                                .background(if (scrollContent && contentScroll.value > 0) colors.separator else Color.Transparent),
                        )
                        Column(
                            Modifier.weight(1f)
                                .then(if (scrollContent) Modifier.verticalScroll(contentScroll) else Modifier)
                                .imePadding()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            CompositionLocalProvider(LocalGroupedListGutter provides 0.dp) {
                                Column(
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
