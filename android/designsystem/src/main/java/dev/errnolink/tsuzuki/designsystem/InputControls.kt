package dev.errnolink.tsuzuki.designsystem

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tanh

/** Slider value under a finger: nearest step when [step] > 1, always clamped to the range. */
internal fun sliderValueAt(fraction: Float, first: Int, last: Int, step: Int): Int {
    val span = (last - first).coerceAtLeast(1)
    val raw = first + fraction.coerceIn(0f, 1f) * span
    return if (step > 1) {
        (first + ((raw - first) / step).roundToInt() * step).coerceIn(first, last)
    } else {
        raw.roundToInt().coerceIn(first, last)
    }
}

/** Segment under a drop point [x] px in the control's frame: inset-based cells, clamped to the ends. */
internal fun segmentIndexAt(x: Float, widthPx: Float, count: Int, insetPx: Float, rtl: Boolean): Int {
    val seg = ((widthPx - 2f * insetPx) / count.coerceAtLeast(1)).coerceAtLeast(1f)
    val f = (if (rtl) widthPx - insetPx - x else x - insetPx) / seg
    return f.toInt().coerceIn(0, count - 1)
}

/** Centre px of segment [index] — where the resting pill sits. */
internal fun segmentCenterAt(index: Int, widthPx: Float, count: Int, insetPx: Float, rtl: Boolean): Float {
    val seg = ((widthPx - 2f * insetPx) / count.coerceAtLeast(1)).coerceAtLeast(1f)
    return if (rtl) widthPx - insetPx - seg * (index + 0.5f) else insetPx + seg * (index + 0.5f)
}

/**
 * Where a finger at [px] drives the pill: clamped to the end centres, pulled past them with a
 * tanh rubber band of at most [overdragPx]. Pure; testable.
 */
internal fun lensFollowTarget(px: Float, firstPx: Float, lastPx: Float, overdragPx: Float): Float {
    val clamped = px.coerceIn(minOf(firstPx, lastPx), maxOf(firstPx, lastPx))
    return clamped + overdragPx * tanh((px - clamped) / overdragPx.coerceAtLeast(1f))
}

/** Lens squash fraction from pill speed: 1 (full stretch) at [maxSpeedPx], 0 at rest. Pure. */
internal fun lensSpeedStretch(speedPxPerS: Float, maxSpeedPx: Float): Float =
    (abs(speedPxPerS) / maxSpeedPx.coerceAtLeast(1f)).coerceIn(0f, 1f)

/** The iOS 26 selection lens, measured in the reference gallery (liquid-menu GUIDE §11). */
internal object SelectionLensSpec {
    const val LiftDamping = 0.65f
    const val LiftResponse = 0.34f
    const val FollowDamping = 0.85f
    const val FollowResponse = 0.20f
    const val SettleDamping = 0.78f
    const val SettleResponse = 0.36f
    const val DropDamping = 1f
    const val DropResponse = 0.30f
    const val LiftScaleX = 1.12f
    const val LiftExtraWidthFraction = 0.06f
    const val LiftMaxExtraWidthDp = 7f
    const val LiftScaleY = 1.24f
    const val IconLiftScaleY = 1.12f
    const val PressScale = 1.03f
    const val MaxStretch = 0.22f
    const val StretchHeightLoss = 0.35f
    const val MaxStretchSpeed = 1800f
    const val DropDelayMillis = 60L
}

/**
 * The selection pill's px spring with its live velocity (Animatable hides velocity, and the lens
 * stretch is driven by it). Substep-integrated like the tab bar's PillPhysics. Position and
 * velocity are snapshot state, so the pill and lens draws follow every frame of the spring.
 */
internal class LensPillMotion(initial: Float, private val scope: CoroutineScope?) {
    var position by mutableFloatStateOf(initial)
        private set
    var velocity by mutableFloatStateOf(0f)
        private set
    private var target = initial
    private var dampingRatio = SelectionLensSpec.SettleDamping
    private var stiffness = KasaneSprings.kasaneStiffness(SelectionLensSpec.SettleResponse)
    private var loopJob: Job? = null

    fun snapTo(v: Float) {
        loopJob?.cancel()
        loopJob = null
        position = v
        velocity = 0f
        target = v
    }

    /** Chase [v] on the follow spring (the finger). */
    fun follow(v: Float) {
        target = v
        dampingRatio = SelectionLensSpec.FollowDamping
        stiffness = KasaneSprings.kasaneStiffness(SelectionLensSpec.FollowResponse)
        requestFrame()
    }

    /** Spring to [v] on the settle spring (release, external selection changes). */
    fun settle(v: Float) {
        target = v
        dampingRatio = SelectionLensSpec.SettleDamping
        stiffness = KasaneSprings.kasaneStiffness(SelectionLensSpec.SettleResponse)
        requestFrame()
    }

    /** Integrates one frame; false once the pill rests on [target]. */
    fun tick(dt: Float): Boolean {
        var x = position
        var v = velocity
        var remaining = dt.coerceAtLeast(0f)
        while (remaining > 0f) {
            val h = minOf(1f / 120f, remaining)
            remaining -= h
            val damping = 2f * dampingRatio * sqrt(stiffness)
            val acceleration = stiffness * (target - x) - damping * v
            v += acceleration * h
            x += v * h
        }
        if (abs(target - x) < 0.01f && abs(v) < 0.01f) {
            position = target
            velocity = 0f
            return false
        }
        position = x
        velocity = v
        return true
    }

    private fun requestFrame() {
        val frames = scope ?: return
        if (loopJob?.isActive == true) return
        loopJob = frames.launch {
            var lastFrame = -1L
            while (true) {
                val now = withFrameNanos { it }
                val dt = if (lastFrame < 0L) 0f else ((now - lastFrame) / 1e9f).coerceIn(0f, 0.05f)
                lastFrame = now
                if (!tick(dt)) break
            }
            loopJob = null
        }
    }
}

fun sliderKnobFill(colors: TsuzukiColors, lensTint: Float, opaqueGlass: Boolean): Color =
    when {
        lensTint <= 0.02f || opaqueGlass -> Color.White
        else ->
            androidx.compose.ui.graphics.lerp(
                Color.White,
                Color.White.copy(alpha = if (colors.isDark) 0.22f else 0.40f),
                lensTint,
            )
    }

/*
 * Kasane slider thumb pieces, shared by the settings slider (`SliderRow`) and the reader page
 * scrubber (`ui.reader.ReaderScrubber`) so the glass lens is drawn in exactly one place.
 * The gap erase needs an offscreen compositing layer around the draw (CompositingStrategy.Offscreen).
 */

/** The erased gap behind the thumb, widening smoothly as the bar morphs into the drag lens. */
fun DrawScope.drawSliderThumbGap(centre: Offset, pressed: Float, lens: Float) {
    val barW = lerpDp(4.dp, 2.dp, pressed).toPx()
    val barH = 32.dp.toPx()
    val lensW = 44.dp.toPx()
    val lensH = 30.dp.toPx()
    val l = lens.coerceIn(0f, 1f)
    val curW = barW * (1f - l) + lensW * l
    val curH = barH * (1f - l) + lensH * l
    val gapW = curW + 12.dp.toPx() * (1f - l) + 8.dp.toPx() * l
    val gapH = curH + 6.dp.toPx()
    drawRoundRect(
        Color.Black,
        topLeft = Offset(centre.x - gapW / 2f, centre.y - gapH / 2f),
        size = Size(gapW, gapH),
        cornerRadius = CornerRadius(gapH / 2f),
        blendMode = BlendMode.Clear,
    )
}

/** The M3E bar handle, narrowed from 4 to 2 dp while pressed; dissolves smoothly into the widening lens. */
fun DrawScope.drawSliderBarHandle(color: Color, centre: Offset, pressed: Float, lens: Float) {
    val l = lens.coerceIn(0f, 1f)
    val alpha = (1f - (l / 0.45f).coerceIn(0f, 1f))
    if (alpha <= 0.02f) return
    val barW = lerpDp(4.dp, 2.dp, pressed).toPx()
    val w = barW * (1f - l) + 12.dp.toPx() * l
    val h = 32.dp.toPx() * (1f - l) + 30.dp.toPx() * l
    drawRoundRect(
        color.copy(alpha = alpha),
        topLeft = Offset(centre.x - w / 2f, centre.y - h / 2f),
        size = Size(w, h),
        cornerRadius = CornerRadius(w / 2f),
    )
}

/**
 * The glass lens thumb while dragging, physically expanding from the bar into a 44×30 dp Kasane
 * Liquid Glass pill that magnifies the split track inside (clipped cleanly to the lens).
 */
fun DrawScope.drawSliderLens(
    centre: Offset,
    lens: Float,
    rtl: Boolean,
    fill: Color,
    rest: Color,
    knob: Color,
    darkGlass: Boolean,
) {
    val l = lens.coerceIn(0f, 1f)
    if (l <= 0.02f) return
    val barW = 2.dp.toPx()
    val barH = 32.dp.toPx()
    val curW = barW * (1f - l) + 44.dp.toPx() * l
    val curH = barH * (1f - l) + 30.dp.toPx() * l
    val topLeft = Offset(centre.x - curW / 2f, centre.y - curH / 2f)
    val pillRadius = CornerRadius(curH / 2f)
    val shellAlpha = (l / 0.35f).coerceIn(0f, 1f)

    drawRoundRect(
        Color.Black.copy(alpha = 0.20f * shellAlpha),
        topLeft = Offset(topLeft.x, topLeft.y + 2.dp.toPx()),
        size = Size(curW, curH),
        cornerRadius = pillRadius,
    )
    drawRoundRect(
        knob.copy(alpha = shellAlpha),
        topLeft = topLeft,
        size = Size(curW, curH),
        cornerRadius = pillRadius,
    )

    val pieceW = (curW - 6.dp.toPx()).coerceAtLeast(0f)
    val pieceH = (curH * 0.43f).coerceAtMost(12.8.dp.toPx())
    if (pieceW > 2.dp.toPx() && pieceH > 2.dp.toPx()) {
        val pieceLeft = centre.x - pieceW / 2f
        val pieceTop = centre.y - pieceH / 2f
        val innerAlpha = ((l - 0.1f) / 0.45f).coerceIn(0f, 1f)
        if (innerAlpha > 0.01f) {
            val clip = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = pieceLeft,
                        top = pieceTop,
                        right = pieceLeft + pieceW,
                        bottom = pieceTop + pieceH,
                        cornerRadius = CornerRadius(pieceH / 2f),
                    ),
                )
            }
            clipPath(clip) {
                drawRect(
                    fill.copy(alpha = innerAlpha),
                    topLeft = Offset(if (rtl) centre.x else pieceLeft, pieceTop),
                    size = Size(pieceW / 2f, pieceH),
                )
                drawRect(
                    rest.copy(alpha = innerAlpha),
                    topLeft = Offset(if (rtl) pieceLeft else centre.x, pieceTop),
                    size = Size(pieceW / 2f, pieceH),
                )
            }
        }
    }

    drawRoundRect(
        Brush.verticalGradient(
            0f to Color.White.copy(alpha = (if (darkGlass) 0.18f else 0.35f) * shellAlpha),
            0.55f to Color.Transparent,
            startY = topLeft.y,
            endY = topLeft.y + curH,
        ),
        topLeft = topLeft,
        size = Size(curW, curH),
        cornerRadius = pillRadius,
    )
    drawGlassRim(darkGlass, shellAlpha, topLeft, Size(curW, curH))
}

/** The iOS 26 segmented track: 40 dp capsule (56 dp with icons), the resting pill 3 dp inside it. */
private val SelectorTrackHeight = 40.dp
private val SelectorIconTrackHeight = 56.dp
private val SelectorInset = 3.dp
private val SelectorOverdrag = 14.dp

/** A fixed rounded rect as a Shape, for the tinted-copy clip that follows the lens each frame. */
private class LensRectShape(val rect: RoundRect) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(rect)
}

@Composable
private fun SegmentBadge(count: Int) {
    val colors = TsuzukiTheme.colors
    Box(
        Modifier
            .clip(CircleShape)
            .background(colors.accent)
            .heightIn(min = 16.dp)
            .defaultMinSize(minWidth = 16.dp)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        TsuzukiText(
            count.toString(),
            style = TsuzukiTheme.typography.caption2.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onAccent,
        )
    }
}

/** Linear Dp interpolation for sizes that grow with an animated fraction. */
internal fun lerpDp(start: Dp, stop: Dp, fraction: Float): Dp = start + (stop - start) * fraction

/**
 * iOS 26 segmented control (liquid-menu GUIDE §11): the resting pill springs between segments and,
 * held, lifts into a clear glass lens that follows the finger across the track — stretch along the
 * move, a haptic tick on each new segment, settle on release. Everything the lens magnifies is
 * recorded into a private backdrop, so it behaves the same on glass pages and plain ones. With
 * [icons], each segment shows its icon above a short label (56 dp tall).
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    badges: List<Int?> = emptyList(),
    icons: List<ImageVector> = emptyList(),
) {
    require(options.isNotEmpty() && selectedIndex in options.indices)
    require(icons.isEmpty() || icons.size == options.size)
    val colors = TsuzukiTheme.colors
    val reduced = TsuzukiTheme.motion.reduced
    val haptics = TsuzukiTheme.haptics
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val largeFont = LocalDensity.current.fontScale > 1.15f
    val density = LocalDensity.current

    // The lens needs the AGSL pass (API 33+) and full motion; below either, the pill just slides.
    val lensLive = Build.VERSION.SDK_INT >= 33 && !reduced
    val scope = rememberCoroutineScope()
    val pill = remember { LensPillMotion(0f, scope) }
    val lift = remember { Animatable(0f) }
    var liftJob by remember { mutableStateOf<Job?>(null) }
    val barContent = rememberSelectionBarContent()
    var pressed by remember { mutableStateOf(false) }
    var seeded by remember { mutableStateOf(false) }
    // The gesture detector and the draw lambdas outlive recompositions: read through these.
    val currentSelected by rememberUpdatedState(selectedIndex)
    val currentOnSelect by rememberUpdatedState(onSelect)

    val insetPx = with(density) { SelectorInset.toPx() }
    val overdragPx = with(density) { SelectorOverdrag.toPx() }
    val trackHpx = with(density) { (if (icons.isEmpty()) SelectorTrackHeight else SelectorIconTrackHeight).toPx() }
    val maxExtraWpx = with(density) { SelectionLensSpec.LiftMaxExtraWidthDp.dp.toPx() }
    val maxStretchSpeedPx = with(density) { SelectionLensSpec.MaxStretchSpeed.dp.toPx() }
    val trackColor = if (colors.isDark) Color.White.copy(alpha = 0.11f) else Color.Black.copy(alpha = 0.07f)
    val pillColor = if (colors.isDark) Color.White.copy(alpha = 0.22f) else Color.White
    val labelColor = if (colors.isDark) Color.White.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.8f)
    val selectedLabelColor = if (colors.isDark) Color.White else Color.Black

    fun centerOf(index: Int, widthPx: Float) = segmentCenterAt(index, widthPx, options.size, insetPx, rtl)
    fun indexAt(x: Float, widthPx: Float) = segmentIndexAt(x, widthPx, options.size, insetPx, rtl)

    /** The pill / lens rect in the control's px frame, squash and stretch along the motion in. */
    fun lensRect(widthPx: Float, heightPx: Float): RoundRect {
        val l = lift.value
        val stretch = lensSpeedStretch(pill.velocity, maxStretchSpeedPx) * SelectionLensSpec.MaxStretch
        val seg = ((widthPx - 2f * insetPx) / options.size).coerceAtLeast(1f)
        val extraHalfW = minOf(seg / 2f * SelectionLensSpec.LiftExtraWidthFraction, maxExtraWpx) * l
        val halfW = (seg / 2f + extraHalfW) * (1f + stretch)
        val liftScaleY = if (icons.isEmpty()) SelectionLensSpec.LiftScaleY else SelectionLensSpec.IconLiftScaleY
        val halfH = (trackHpx / 2f - insetPx).coerceAtLeast(1f) *
            (1f + (liftScaleY - 1f) * l) * (1f - SelectionLensSpec.StretchHeightLoss * stretch)
        val cy = heightPx / 2f
        val cx = pill.position
        return RoundRect(cx - halfW, cy - halfH, cx + halfW, cy + halfH, CornerRadius(halfH))
    }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .heightIn(min = if (icons.isEmpty()) 52.dp else SelectorIconTrackHeight)
            // The press swell lifts the whole control; the lens rises past its edge, so no clip.
            .graphicsLayer {
                val s = 1f + (SelectionLensSpec.PressScale - 1f) * lift.value
                scaleX = s
                scaleY = s
            }
            .pointerInput(rtl, options.size, reduced, lensLive, insetPx, overdragPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width.toFloat()
                    pressed = true
                    var upX = down.position.x
                    val downIndex = indexAt(down.position.x, w)
                    val grabOffset = if (downIndex == currentSelected) centerOf(currentSelected, w) - down.position.x else 0f
                    var lastIndex = downIndex
                    var claimed = false
                    var released = false
                    if (lensLive) {
                        liftJob?.cancel()
                        liftJob = scope.launch {
                            lift.animateTo(1f, spring(SelectionLensSpec.LiftDamping, KasaneSprings.kasaneStiffness(SelectionLensSpec.LiftResponse)))
                        }
                        if (downIndex != currentSelected) {
                            pill.follow(lensFollowTarget(down.position.x, centerOf(0, w), centerOf(options.lastIndex, w), overdragPx))
                        }
                    }
                    var slopTotal = 0f
                    var lastX = down.position.x
                    val slop = viewConfiguration.touchSlop
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        upX = change.position.x
                        if (!change.pressed) {
                            released = true
                            break
                        }
                        if (change.isConsumed) break // a parent (the page scroll) took the gesture
                        if (claimed) {
                            val trackedX = change.position.x + grabOffset
                            if (!reduced) {
                                pill.follow(lensFollowTarget(trackedX, centerOf(0, w), centerOf(options.lastIndex, w), overdragPx))
                            }
                            val next = indexAt(trackedX, w)
                            if (next != lastIndex) {
                                lastIndex = next
                                haptics.select()
                            }
                            change.consume()
                        } else {
                            slopTotal += change.position.x - lastX
                            lastX = change.position.x
                            if (abs(slopTotal) > slop) {
                                claimed = true
                                change.consume()
                                // Without a lens the pill still chases the finger once committed.
                                if (!reduced && !lensLive) {
                                    pill.follow(lensFollowTarget(change.position.x + grabOffset, centerOf(0, w), centerOf(options.lastIndex, w), overdragPx))
                                }
                            }
                        }
                    }
                    pressed = false
                    if (released) {
                        val target = if (claimed) indexAt(upX + grabOffset, w) else indexAt(upX, w)
                        if (target != currentSelected) currentOnSelect(target)
                        if (reduced) pill.snapTo(centerOf(target, w)) else pill.settle(centerOf(target, w))
                    } else {
                        // Cancelled: fall back home without selecting.
                        if (reduced) pill.snapTo(centerOf(currentSelected, w)) else pill.settle(centerOf(currentSelected, w))
                    }
                    if (lensLive) {
                        liftJob?.cancel()
                        liftJob = scope.launch {
                            if (released) delay(SelectionLensSpec.DropDelayMillis)
                            lift.animateTo(0f, spring(SelectionLensSpec.DropDamping, KasaneSprings.kasaneStiffness(SelectionLensSpec.DropResponse)))
                        }
                    }
                }
            },
    ) {
        val cellPx = with(density) { maxWidth.toPx() / options.size }
        val textMeasurer = rememberTextMeasurer()
        val subhead = TsuzukiTheme.typography.subhead
        val caption2 = TsuzukiTheme.typography.caption2
        val cellTextPx = (cellPx - with(density) { 20.dp.toPx() }).roundToInt().coerceAtLeast(0)
        val fittedSizes = remember(options, cellTextPx) {
            options.map { label ->
                (14 downTo 12).firstOrNull { size ->
                    !textMeasurer.measure(
                        label,
                        style = subhead.copy(fontSize = size.sp, fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        constraints = Constraints(maxWidth = cellTextPx),
                    ).didOverflowWidth
                } ?: 12
            }
        }
        val iconLabelPx = (cellPx - with(density) { 4.dp.toPx() }).roundToInt().coerceAtLeast(0)
        val iconBase = caption2.fontSize.value.roundToInt()
        val fittedIconSizes = remember(options, iconLabelPx) {
            options.map { label ->
                (iconBase downTo 9).firstOrNull { size ->
                    !textMeasurer.measure(
                        label,
                        style = caption2.copy(fontSize = size.sp, fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        constraints = Constraints(maxWidth = iconLabelPx),
                    ).didOverflowWidth
                } ?: 9
            }
        }

        // External selection changes (and first placement) move the pill; a held lens is not yanked.
        LaunchedEffect(selectedIndex, options.size, rtl, cellPx) {
            val target = centerOf(selectedIndex, cellPx * options.size)
            if (!seeded) {
                pill.snapTo(target)
                seeded = true
            } else if (!pressed) {
                if (reduced) pill.snapTo(target) else pill.settle(target)
            }
        }

        /** One label row; the tinted copy repeats it so a label can be half-tinted mid-drag. */
        @Composable
        fun SegmentRow(selectedLayer: Boolean, a11y: Boolean) {
            Row(Modifier.fillMaxSize().then(if (a11y) Modifier.selectableGroup() else Modifier)) {
                options.forEachIndexed { index, label ->
                    val weight = if (selectedLayer || index == selectedIndex) FontWeight.SemiBold else FontWeight.Medium
                    val tint = if (selectedLayer) selectedLabelColor else labelColor
                    var segment = Modifier.weight(1f).fillMaxHeight()
                    if (a11y) {
                        segment = segment.semantics(mergeDescendants = true) {
                            role = Role.RadioButton
                            selected = index == selectedIndex
                            onClick(label = label) {
                                currentOnSelect(index)
                                true
                            }
                        }
                    }
                    Box(
                        segment.padding(horizontal = if (icons.isEmpty()) 10.dp else 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (icons.isEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TsuzukiText(
                                    label,
                                    style = subhead.copy(fontSize = fittedSizes[index].sp, fontWeight = weight),
                                    color = tint,
                                    maxLines = if (largeFont) 2 else 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                badges.getOrNull(index)?.let { count -> SegmentBadge(count) }
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                TsuzukiIcon(icons[index], null, Modifier.size(18.dp), tint = tint)
                                TsuzukiText(
                                    label,
                                    style = caption2.copy(fontSize = fittedIconSizes[index].sp, fontWeight = weight),
                                    color = tint,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 1. Everything the lens magnifies: track, resting pill, labels, and the selected-colour
        // copy clipped to the pill/lens rect. Recorded so the lens can bend it.
        Box(
            Modifier
                .matchParentSize()
                .then(if (lensLive) Modifier.selectionBarContent(barContent) else Modifier)
                .drawBehind {
                    val trackTop = (size.height - trackHpx) / 2f
                    drawRoundRect(
                        trackColor,
                        topLeft = Offset(0f, trackTop),
                        size = Size(size.width, trackHpx),
                        cornerRadius = CornerRadius(trackHpx / 2f),
                    )
                    val rest = if (lensLive) 1f - lift.value else 1f
                    if (seeded && rest > 0.001f) {
                        val r = lensRect(size.width, size.height)
                        drawRoundRect(
                            Color.Black.copy(alpha = (if (colors.isDark) 0.20f else 0.08f) * rest),
                            topLeft = Offset(r.left, r.top + 1.dp.toPx()),
                            size = Size(r.width, r.height),
                            cornerRadius = CornerRadius(r.height / 2f),
                        )
                        drawRoundRect(
                            pillColor.copy(alpha = pillColor.alpha * rest),
                            topLeft = Offset(r.left, r.top),
                            size = Size(r.width, r.height),
                            cornerRadius = CornerRadius(r.height / 2f),
                        )
                        drawGlassRim(colors.isDark, 0.35f * rest, Offset(r.left, r.top), Size(r.width, r.height))
                    }
                },
        ) {
            SegmentRow(selectedLayer = false, a11y = true)
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        shape = LensRectShape(lensRect(size.width, size.height))
                        clip = true
                    }
                    .semantics { invisibleToUser() },
            ) {
                SegmentRow(selectedLayer = true, a11y = false)
            }
        }

        // 2. The lifted lens: clear glass, magnifying and refracting the recorded content only —
        // the control sits inside the page's own glass, so it never samples the page backdrop.
        if (lensLive) {
            Box(
                Modifier
                    .matchParentSize()
                    .liquidSelectionLens(
                        context = null,
                        barContent = barContent,
                        geometry = { w, h ->
                            val r = lensRect(w, h)
                            val c = Offset((r.left + r.right) / 2f, (r.top + r.bottom) / 2f)
                            LiquidGlassGeometry(
                                GlassBox.Empty,
                                GlassBox(c.x, c.y, r.width / 2f, r.height / 2f, minOf(r.width, r.height) / 2f),
                                0f,
                                1f + 0.15f * lift.value,
                                c,
                            )
                        },
                        lift = { lift.value },
                    )
                    .drawBehind {
                        val l = lift.value
                        if (l > 0.02f) {
                            val r = lensRect(size.width, size.height)
                            drawGlassRim(colors.isDark, 0.65f * l, Offset(r.left, r.top), Size(r.width, r.height))
                        }
                    },
            )
        }
    }
}

/**
 * The search field's shared internals: the draft with echo suppression, the text field and
 * Kasane's × button. [SearchField] draws its own capsule, fill and focus ring around this;
 * MorphingSearch's landed field draws nothing and lets the flying glass be the surface.
 */
@Composable
internal fun SearchFieldCore(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (() -> Unit)?,
    placeholder: String,
    enabled: Boolean,
    iconTint: Color,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onClose: (() -> Unit)? = null,
    onFocusChange: (Boolean) -> Unit = {},
    fieldPadding: PaddingValues = PaddingValues(vertical = 12.dp),
    trailing: (@Composable RowScope.() -> Unit)? = null,
    trailingAfterClear: Boolean = true,
) {
    val colors = TsuzukiTheme.colors
    val reduced = TsuzukiTheme.motion.reduced
    val labels = LocalTsuzukiComponentLabels.current
    val focusManager = LocalFocusManager.current
    var draft by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(query, TextRange(query.length)))
    }
    // The caller's value can trail the field (flows, view models). Its echoes of our own edits are
    // dropped; anything else is an outside change (a restore, a programmatic clear) and replaces the draft.
    val sent = remember { ArrayDeque<String>() }
    var seen by remember { mutableStateOf(query) }
    if (query != seen) {
        seen = query
        val echo = sent.indexOf(query)
        if (echo >= 0) {
            repeat(echo + 1) { sent.removeFirst() }
        } else {
            sent.clear()
            if (draft.text != query) draft = TextFieldValue(query, TextRange(query.length))
        }
    }
    fun emit(text: String) {
        if (sent.size == 64) sent.removeFirst()
        sent.addLast(text)
        onQueryChange(text)
    }
    val hasText = draft.text.isNotEmpty()
    val closeRule = searchCloseAction(hasText)
    // Kasane's ×: always present when the field can close, otherwise it only clears text.
    val clearVisible = hasText || onClose != null
    val clearAlpha by animateFloatAsState(if (clearVisible) 1f else 0f, KasaneSprings.effects(), label = "Search clear alpha")
    val clearScale by animateFloatAsState(
        if (clearVisible) 1f else 0.6f,
        KasaneSprings.fastSpatial(reduced),
        label = "Search clear scale",
    )
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        TsuzukiIcon(
            TsuzukiIcons.Search,
            null,
            Modifier.size(19.dp),
            tint = iconTint,
        )
        BasicTextField(
            value = draft,
            onValueChange = {
                val changed = draft.text != it.text
                draft = it
                if (changed) emit(it.text)
            },
            enabled = enabled,
            modifier = Modifier.weight(1f).padding(fieldPadding)
                .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .onFocusChanged { onFocusChange(it.isFocused) }
                .semantics { contentDescription = placeholder },
            textStyle = TsuzukiTheme.typography.body.copy(color = colors.text),
            cursorBrush = SolidColor(colors.accent), singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke(); focusManager.clearFocus() }),
            decorationBox = { inner ->
                Box {
                    if (draft.text.isEmpty()) TsuzukiText(placeholder, color = colors.secondary, maxLines = 1)
                    inner()
                }
            },
        )
        if (!trailingAfterClear) trailing?.invoke(this)
        Box(
            Modifier.graphicsLayer {
                alpha = clearAlpha
                scaleX = clearScale
                scaleY = clearScale
            },
        ) {
            PlainIconButton(
                labels.clearSearch,
                {
                    when (closeRule) {
                        SearchCloseAction.Clear -> {
                            draft = TextFieldValue("")
                            emit("")
                        }
                        SearchCloseAction.Close -> onClose?.invoke()
                    }
                },
                enabled = enabled && (hasText || onClose != null),
            ) {
                Box(
                    Modifier.size(20.dp).clip(CircleShape).background(colors.secondary.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    TsuzukiIcon(TsuzukiIcons.X, null, Modifier.size(12.dp), tint = colors.grouped)
                }
            }
        }
        if (trailingAfterClear) trailing?.invoke(this)
    }
}

/**
 * Kasane search field: a glass capsule that settles to a solid surface on focus so typing never
 * sits on moving glass; the clear button scales in once there is text. The inline variant shrinks
 * on focus while a Cancel button slides in. With [onClose], the × follows Kasane's close rule —
 * it stays visible, clears the text when there is text and closes only when it is empty.
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String,
    enabled: Boolean = true,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    inline: Boolean = false,
    onCancel: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    onClose: (() -> Unit)? = null,
) {
    val colors = TsuzukiTheme.colors
    val reduced = TsuzukiTheme.motion.reduced
    val labels = LocalTsuzukiComponentLabels.current
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val focusFraction by animateFloatAsState(
        if (focused) 1f else 0f,
        KasaneSprings.effects(),
        label = "Search focus",
    )
    val cancelShown = inline && focused
    val cancelWidth by animateDpAsState(
        if (cancelShown) 72.dp else 0.dp,
        KasaneSprings.spatialSoft(reduced),
        label = "Search cancel width",
    )
    val cancelAlpha by animateFloatAsState(
        if (cancelShown) 1f else 0f,
        KasaneSprings.effects(),
        label = "Search cancel alpha",
    )

    fun cancel() {
        onValueChange("")
        focusManager.clearFocus()
        onCancel?.invoke()
    }

    // focused, the capsule settles to a solid surface under a hairline accent ring
    val fieldFill = androidx.compose.ui.graphics.lerp(colors.grouped, colors.elevated, focusFraction)
    val fieldModifier = Modifier
        .heightIn(min = TsuzukiSpacing.touchTarget)
        .clip(CircleShape)
        .background(fieldFill)
        .drawBehind {
            if (focusFraction > 0.02f) {
                val stroke = 1.5.dp.toPx()
                drawRoundRect(
                    colors.accent.copy(alpha = focusFraction * 0.9f),
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size((size.width - stroke).coerceAtLeast(0f), (size.height - stroke).coerceAtLeast(0f)),
                    cornerRadius = CornerRadius(size.minDimension / 2f),
                    style = Stroke(stroke),
                )
            }
        }

    if (inline) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            SearchFieldCore(
                query = value,
                onQueryChange = onValueChange,
                onSearch = onSearch,
                placeholder = placeholder,
                enabled = enabled,
                iconTint = androidx.compose.ui.graphics.lerp(colors.secondary, colors.accent, focusFraction),
                modifier = Modifier.weight(1f).then(fieldModifier).padding(start = 14.dp, end = 4.dp),
                focusRequester = focusRequester,
                onClose = onClose,
                onFocusChange = { focused = it },
                trailing = trailing,
            )
            Box(
                Modifier
                    .width(cancelWidth)
                    .clipToBounds()
                    .graphicsLayer { alpha = cancelAlpha },
                contentAlignment = Alignment.CenterEnd,
            ) {
                TsuzukiText(
                    labels.cancel,
                    Modifier
                        .heightIn(min = TsuzukiSpacing.touchTarget)
                        .clipToBounds()
                        .clickable(enabled = enabled) { cancel() }
                        .padding(horizontal = 8.dp),
                    style = TsuzukiTheme.typography.body,
                    color = colors.accent,
                    maxLines = 1,
                )
            }
        }
    } else {
        SearchFieldCore(
            query = value,
            onQueryChange = onValueChange,
            onSearch = onSearch,
            placeholder = placeholder,
            enabled = enabled,
            iconTint = androidx.compose.ui.graphics.lerp(colors.secondary, colors.accent, focusFraction),
            modifier = modifier.then(fieldModifier).padding(start = 14.dp, end = 4.dp),
            focusRequester = focusRequester,
            onClose = onClose,
            onFocusChange = { focused = it },
            trailing = trailing,
        )
    }
}


/**
 * Kasane slider: an 8 dp track with the M3E bar handle and a stop dot. Pressing narrows the handle
 * and raises the value label; once dragging, the handle becomes a glass lens and the value follows
 * the finger with no lag. Taps on the track and keyboard steps travel on a spring; snapping comes
 * from the progression's step.
 */
@Composable
fun SliderRow(
    title: String,
    value: Int,
    valueString: String,
    range: IntProgression,
    enabled: Boolean,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    startLabel: String? = null,
    endLabel: String? = null,
    showHeader: Boolean = true,
) {
    val colors = TsuzukiTheme.colors
    val motion = TsuzukiTheme.motion
    val reduced = motion.reduced
    val haptics = TsuzukiTheme.haptics
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val opaqueGlass = LocalOpaqueGlass.current
    val density = LocalDensity.current
    val first = range.first
    val last = range.last
    val step = range.step
    val span = (last - first).coerceAtLeast(1)
    val fractionOf = { v: Int -> ((v - first).toFloat() / span).coerceIn(0f, 1f) }
    val thumbEdgePx = with(density) { 20.dp.toPx() }
    val fractionAtX = { x: Float, width: Float ->
        val usable = (width - thumbEdgePx * 2f).coerceAtLeast(1f)
        ((x - thumbEdgePx) / usable).coerceIn(0f, 1f).let { if (isRtl) 1f - it else it }
    }

    val animated = remember { Animatable(fractionOf(value)) }
    var dragging by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(fractionOf(value)) }
    val scope = rememberCoroutineScope()
    // The gesture handlers outlive recompositions between key resets: they read the live slider.
    val currentValue by rememberUpdatedState(value)
    val currentOnChange by rememberUpdatedState(onChange)

    // The finger owns the value while dragging; otherwise the drawn value travels to the target on
    // a spring (tap on the track, keyboard steps and setProgress all go through here).
    LaunchedEffect(value, dragging) {
        if (!dragging) animated.animateTo(fractionOf(value), KasaneSprings.spatialSoft(reduced))
    }

    val displayFraction = if (dragging) dragFraction else animated.value
    val pressFraction by animateFloatAsState(
        if ((pressed || dragging) && enabled) 1f else 0f,
        KasaneSprings.fastSpatial(reduced),
        label = "Slider press",
    )
    val lensFraction by animateFloatAsState(
        if (dragging && enabled && !reduced) 1f else 0f,
        KasaneSprings.fastSpatial(reduced),
        label = "Slider lens",
    )
    val bubbleFraction by animateFloatAsState(
        if ((pressed || dragging) && enabled) 1f else 0f,
        if (reduced) M3Motion.reducedFade<Float>() else KasaneSprings.fastSpatial(reduced),
        label = "Slider bubble",
    )
    val textMeasurer = rememberTextMeasurer()
    val bubbleStyle = TsuzukiTheme.typography.subhead.copy(
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = colors.background,
    )
    val bubbleLayout = remember(valueString, bubbleStyle, textMeasurer) {
        textMeasurer.measure(valueString, style = bubbleStyle, maxLines = 1)
    }

    Column(
        modifier
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = if (showHeader) 12.dp else 4.dp,
                bottom = 12.dp,
            )
            .alpha(if (enabled) 1f else 0.45f),
    ) {
        if (showHeader) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TsuzukiText(title, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                TsuzukiText(valueString, Modifier, TsuzukiTheme.typography.subhead, colors.secondary)
            }
        }
        var trackWidth by remember { mutableStateOf(1f) }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(TsuzukiSpacing.touchTarget)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .onSizeChanged { trackWidth = it.width.toFloat().coerceAtLeast(1f) }
                .semantics {
                    contentDescription = title
                    stateDescription = valueString
                    progressBarRangeInfo = ProgressBarRangeInfo(displayFraction, 0f..1f)
                    if (!enabled) disabled()
                    setProgress { target ->
                        if (enabled) {
                            onChange(sliderValueAt(target, first, last, step))
                        }
                        enabled
                    }
                }
                .pointerInput(enabled, first, span, step, isRtl, thumbEdgePx) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onPress = { offset ->
                            pressed = true
                            val f = fractionAtX(offset.x, trackWidth)
                            currentOnChange(sliderValueAt(f, first, last, step))
                            tryAwaitRelease()
                            pressed = false
                        },
                    )
                }
                .pointerInput(enabled, first, span, step, isRtl, thumbEdgePx) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            val f = fractionAtX(offset.x, trackWidth)
                            dragFraction = f
                            dragging = true
                        },
                        onDragEnd = {
                            scope.launch {
                                animated.snapTo(dragFraction)
                                dragging = false
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                animated.snapTo(dragFraction)
                                dragging = false
                            }
                        },
                    ) { change, _ ->
                        change.consume()
                        val f = fractionAtX(change.position.x, trackWidth)
                        dragFraction = f
                        val next = sliderValueAt(f, first, last, step)
                        if (next != currentValue) haptics.select()
                        currentOnChange(next)
                    }
                },
        ) {
            val centerY = size.height / 2f
            val trackH = 8.dp.toPx()
            val usable = (size.width - thumbEdgePx * 2f).coerceAtLeast(1f)
            val fx = if (isRtl) {
                size.width - thumbEdgePx - displayFraction * usable
            } else {
                thumbEdgePx + displayFraction * usable
            }

            // Continuous 8 dp track with stop dot before the end
            drawRoundRect(
                colors.separator,
                topLeft = Offset(0f, centerY - trackH / 2f),
                size = Size(size.width, trackH),
                cornerRadius = CornerRadius(trackH / 2f),
            )
            val fillLeft = if (isRtl) fx else 0f
            val fillWidth = if (isRtl) (size.width - fx).coerceAtLeast(0f) else fx
            drawRoundRect(
                colors.accent,
                topLeft = Offset(fillLeft, centerY - trackH / 2f),
                size = Size(fillWidth, trackH),
                cornerRadius = CornerRadius(trackH / 2f),
            )
            if (value < last) {
                drawCircle(
                    colors.accent,
                    2.dp.toPx(),
                    Offset(if (isRtl) 6.dp.toPx() else size.width - 6.dp.toPx(), centerY),
                )
            }

            // Shared M3E resting bar morphing into the Kasane Liquid Glass lens while dragging
            val thumbCentre = Offset(fx, centerY)
            drawSliderThumbGap(thumbCentre, pressFraction, lensFraction)
            drawSliderLens(
                centre = thumbCentre,
                lens = lensFraction,
                rtl = isRtl,
                fill = colors.accent,
                rest = colors.separator,
                knob = sliderKnobFill(colors, lensFraction, opaqueGlass),
                darkGlass = colors.isDark,
            )
            drawSliderBarHandle(colors.accent, thumbCentre, pressFraction, lensFraction)

            // value label rising above the handle while pressed or dragged
            if (bubbleFraction > 0.02f) {
                val bubbleW = bubbleLayout.size.width + 20.dp.toPx()
                val bubbleH = bubbleLayout.size.height + 10.dp.toPx()
                val bubbleX = (fx - bubbleW / 2f).coerceIn(0f, (size.width - bubbleW).coerceAtLeast(0f))
                val bubbleBottom = centerY - 22.dp.toPx()
                val bubbleRise = (1f - bubbleFraction) * 8.dp.toPx()
                val scale = 0.6f + 0.4f * bubbleFraction
                withTransform({
                    scale(scale, scale, pivot = Offset(fx, bubbleBottom))
                    translate(left = 0f, top = bubbleRise)
                }) {
                    drawRoundRect(
                        colors.text.copy(alpha = bubbleFraction.coerceIn(0f, 1f)),
                        topLeft = Offset(bubbleX, bubbleBottom - bubbleH),
                        size = Size(bubbleW, bubbleH),
                        cornerRadius = CornerRadius(bubbleH / 2f),
                    )
                    drawText(
                        bubbleLayout,
                        topLeft = Offset(
                            bubbleX + 10.dp.toPx(),
                            bubbleBottom - bubbleH + 5.dp.toPx(),
                        ),
                        alpha = bubbleFraction.coerceIn(0f, 1f),
                    )
                }
            }
        }
        if (startLabel != null || endLabel != null) {
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TsuzukiText(
                    startLabel.orEmpty(),
                    Modifier.weight(1f),
                    TsuzukiTheme.typography.caption1,
                    colors.secondary,
                )
                TsuzukiText(endLabel.orEmpty(), Modifier, TsuzukiTheme.typography.caption1, colors.secondary)
            }
        }
    }
}
