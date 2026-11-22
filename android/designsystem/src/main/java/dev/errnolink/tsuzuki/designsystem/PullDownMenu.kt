package dev.errnolink.tsuzuki.designsystem

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.material3.minimumInteractiveComponentSize
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import com.kyant.backdrop.drawBackdrop
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import kotlin.math.min
import kotlin.math.roundToInt
import android.os.SystemClock
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Room around the panel inside the menu layer, so the landing overshoot, the rubber band and the shadow are not cut off. */
private val LiquidPad = 16.dp

/** Soft, wide shadow under the menu glass. */
private val MenuShadowElevation = 12.dp

/** iOS 26 menus are one width whatever their content. */
private val MenuWidth = 260.dp

/** The menu covers its trigger and sticks out this far past the trigger's outer edge. */
private val MenuOutset = 6.dp

/** Space above the first row and below the last. */
private val MenuVerticalPadding = 9.dp

/** iOS menus frost the page well beyond the bars' clear glass. */
private val MenuFrost = 20.dp
private val SubmenuFrost = MenuFrost

/** The raised submenu panel: vertical margin to the menu's edges, how far it sticks out past its sides. */
private val SubmenuPanelInset = 4.dp
private val SubmenuPanelOutset = 5.dp
private val SubmenuPanelRadius = 24.dp
private val SubmenuInnerPad = 6.dp

/** How far the parent menu's rows fade while a submenu is raised over them (iOS 26). */
private const val SubmenuParentDim = 0.7f

internal fun PullDownMenuPositionProvider.anchorRectF(): Rect =
    Rect(anchorInPopup.left.toFloat(), anchorInPopup.top.toFloat(), anchorInPopup.right.toFloat(), anchorInPopup.bottom.toFloat())

/**
 * Positions a morphing menu: it covers its trigger and grows toward open space
 * (Kasane's placeOnTrigger), sticking out [outsetPx] past the trigger's outer edge. The provider
 * exposes the settled placement, the trigger's rect in panel coordinates and the pinned origin so
 * the liquid can pour out of the trigger. The popup carries [padPx] of empty room on every side so
 * the landing overshoot is not cut by its window.
 *
 * [morphAnchor]: when the menu's trigger is a whole multi-action capsule, its source's window
 * rect overrides the popup's own anchor, so the menu pours from the capsule for every button
 * inside it (each button keeps its own menu content).
 */
class PullDownMenuPositionProvider(
    private val offsetPx: IntOffset = IntOffset.Zero,
    private val marginPx: Int = 8,
    private val padPx: Int = 0,
    /** LIB-04: space the menu never enters (the tab bar area), subtracted from the window bottom. */
    private val bottomInsetPx: Int = 0,
    private val morphAnchor: MenuMorphSource? = null,
    private val outsetPx: Int = 0,
) : PopupPositionProvider {
    var placement by mutableStateOf(MenuMorphPlacement(IntOffset.Zero, MenuGrow.Down, MenuAlign.Start, Int.MAX_VALUE))
        private set
    var maxAvailableHeightPx by mutableIntStateOf(Int.MAX_VALUE)
        private set
    var contentOffset by mutableStateOf(IntOffset.Zero)
        private set
    var anchorInPopup by mutableStateOf(IntRect.Zero)
        private set
    var origin by mutableStateOf(TransformOrigin(0f, 0f))
        private set
    var positionLocked: Boolean = false
    internal var safeTopPx: Int = 0

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        if (positionLocked) {
            return IntOffset(contentOffset.x - padPx, contentOffset.y - padPx)
        }
        // A menu of a button inside a multi-action capsule pours from the whole capsule: anchor on
        // the capsule's rect (window coordinates), not on the button the popup is composed in.
        val capsule = morphAnchor?.anchorBoundsInWindow
        val baseAnchor = if (capsule != null && capsule.width > 0f && capsule.height > 0f) {
            IntRect(
                capsule.left.roundToInt(),
                capsule.top.roundToInt(),
                capsule.right.roundToInt(),
                capsule.bottom.roundToInt(),
            )
        } else {
            anchorBounds
        }
        // A caller offset nudges the whole trigger the menu grows out of, so the glass still
        // starts exactly where the menu lands.
        val nudgeAnchor = baseAnchor.translate(offsetPx.x, offsetPx.y)
        // The menu reasons about the window minus the status bar top and bottom inset, so tall
        // menus neither enter the status bar nor overlap the tab bar.
        val bottomEdge = (windowSize.height - bottomInsetPx).coerceAtLeast(safeTopPx)
        val result = computeMorphPlacement(
            anchor = nudgeAnchor,
            bounds = IntRect(0, safeTopPx, windowSize.width, bottomEdge),
            viewport = IntSize(windowSize.width, bottomEdge),
            popupSize = IntSize(popupContentSize.width - 2 * padPx, popupContentSize.height - 2 * padPx),
            marginPx = marginPx,
            outsetPx = outsetPx,
        )
        placement = result
        maxAvailableHeightPx = result.maxHeightPx
        contentOffset = result.offset
        anchorInPopup = nudgeAnchor.translate(-result.offset.x, -result.offset.y)
        origin = TransformOrigin(result.align.pinFractionX(), result.grow.pinFractionY())
        return IntOffset(result.offset.x - padPx, result.offset.y - padPx)
    }
}

class PullDownAction(
    val label: String,
    val enabled: Boolean = true,
    val icon: ImageVector? = null,
    val iconContent: (@Composable () -> Unit)? = null,
    val subtitle: String? = null,
    val group: Int = 0,
    val destructive: Boolean = false,
    val selected: Boolean? = null,
    /** Rows of a submenu this row opens (a chevron row); [onClick] is then unused. */
    val submenu: List<PullDownAction>? = null,
    val onClick: () -> Unit,
)

class SubmenuData(
    val title: String,
    val content: @Composable ColumnScope.() -> Unit,
)

class SubmenuHolder {
    var activeSubmenu by mutableStateOf<SubmenuData?>(null)

    /** The row the open submenu unrolls from, and its icon (the panel's header repeats it). */
    internal var anchor: LayoutCoordinates? = null
    internal var anchorIcon: ImageVector? = null
}

val LocalSubmenuHolder = compositionLocalOf<SubmenuHolder?> { null }

/** The inline submenu panel inside the menu: its top and height, in the menu's px. */
data class InlineSubmenuFrame(val top: Float, val height: Float)

/** Current bounds and cutout alpha of the open inline submenu, in menu panel px. */
@Stable
internal class SubmenuFootprint {
    var left by mutableFloatStateOf(0f)
    var top by mutableFloatStateOf(0f)
    var width by mutableFloatStateOf(0f)
    var height by mutableFloatStateOf(0f)
    var radius by mutableFloatStateOf(0f)
    var cutAlpha by mutableFloatStateOf(0f)
}

/**
 * iOS 26 inline submenu: the panel unrolls down from its row over the rows below it. It keeps the
 * row's top when it fits, slides up just enough to fit otherwise, and never grows past the menu
 * (its rows scroll inside it then).
 */
fun inlineSubmenuFrame(rowTop: Float, naturalHeight: Float, menuHeight: Float, insetPx: Float): InlineSubmenuFrame {
    val height = naturalHeight.coerceIn(0f, (menuHeight - 2f * insetPx).coerceAtLeast(0f))
    val top = rowTop.coerceAtMost(menuHeight - insetPx - height).coerceAtLeast(insetPx)
    return InlineSubmenuFrame(top, height)
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction

/* Drag to select (iOS 26): rows highlight under the finger with a cross-fading pill and a haptic
   tick, releasing on a row activates it, a finger outside the menu stretches it toward itself,
   and hovering a submenu row opens it. The pure piece: */

/** A scrub target row of an open menu, vertical span in any one coordinate space. */
data class MenuScrubRow(
    val top: Float,
    val bottom: Float,
    val enabled: Boolean,
)

/**
 * The row a finger at [pointerY] scrubs to (index into [rows]), or null when the finger is outside
 * the menu's vertical range. Disabled rows and the gaps between them (separators, section gaps) are
 * skipped: the nearest enabled row wins, so the highlight never sticks to a divider.
 */
fun menuScrubRow(pointerY: Float, panelTop: Float, panelBottom: Float, rows: List<MenuScrubRow>): Int? {
    if (rows.isEmpty() || pointerY < panelTop || pointerY > panelBottom) return null
    var best = -1
    var bestDistance = Float.MAX_VALUE
    rows.forEachIndexed { index, row ->
        if (!row.enabled) return@forEachIndexed
        val distance = when {
            pointerY < row.top -> row.top - pointerY
            pointerY > row.bottom -> pointerY - row.bottom
            else -> 0f
        }
        if (distance < bestDistance) {
            bestDistance = distance
            best = index
        }
    }
    return if (best >= 0) best else null
}

/** One registered row of an open menu: where it sits and what activating it does. */
internal class MenuDragRow(
    val coordinates: LayoutCoordinates,
    val enabled: Boolean,
    /** The submenu the row belongs to (null: the menu itself). */
    val group: Any?,
    /** A row that opens a submenu: hovering it opens it. */
    val submenu: Boolean,
    val onClick: () -> Unit,
)

/** The submenu the rows composed here belong to. */
internal val LocalMenuGroup = staticCompositionLocalOf<Any?> { null }

/**
 * Drag-select state of one open menu. Rows register themselves ([LocalMenuDragSession]); the panel
 * scrubs with the finger, and a trigger that opened the menu on touch-down drives the same session
 * ([MenuMorphSource.dragSession]). Rows are hit-tested in the menu's own rest coordinates.
 */
internal class MenuDragSession(
    private val haptics: Haptics?,
    private val pullMaxPx: Float,
    private val reduced: Boolean,
    private val scope: CoroutineScope,
) {
    internal val rows = mutableStateMapOf<Any, MenuDragRow>()

    /** The panel's rest bounds on screen (outside its own transforms). */
    internal var panelBounds by mutableStateOf(Rect.Zero)

    /** The menu's content box (inside its transforms): row bounds are taken relative to it. */
    internal var content: LayoutCoordinates? = null
    internal var highlightKey by mutableStateOf<Any?>(null)
    internal var scrubbing by mutableStateOf(false)
    internal var pullTarget by mutableStateOf(Offset.Zero)

    /** The open submenu: only its rows are live while it is open. */
    internal var activeGroup by mutableStateOf<Any?>(null)
    private var dwell: Job? = null
    private var openedByDwell = false

    internal fun localBounds(row: MenuDragRow): Rect? {
        val root = content ?: return null
        if (!root.isAttached || !row.coordinates.isAttached) return null
        val bounds = root.localBoundingBoxOf(row.coordinates, clipBounds = true)
        return if (bounds.height > 0f && bounds.width > 0f) bounds else null
    }

    private fun hit(pointer: Offset): Any? {
        val local = pointer - panelBounds.topLeft
        if (local.x < 0f || local.x > panelBounds.width) return null
        val live = rows.mapNotNull { (key, row) ->
            if (row.group != activeGroup) return@mapNotNull null
            localBounds(row)?.let { Triple(key, row, it) }
        }.sortedBy { it.third.top }
        val index = menuScrubRow(
            pointerY = local.y,
            panelTop = 0f,
            panelBottom = panelBounds.height,
            rows = live.map { (_, row, bounds) -> MenuScrubRow(bounds.top, bounds.bottom, row.enabled) },
        )
        return index?.let { live[it].first }
    }

    /** A finger came down on the menu: its row lights up, nothing is chosen yet. */
    internal fun touch(pointer: Offset) {
        if (!scrubbing) highlightKey = hit(pointer)
    }

    /** A plain tap ended (the row's own click handles it): the highlight fades. */
    internal fun release() {
        if (!scrubbing) highlightKey = null
    }

    internal fun moveTo(pointer: Offset) {
        scrubbing = true
        val key = hit(pointer)
        if (key != highlightKey) {
            highlightKey = key
            dwell?.cancel()
            if (key != null) {
                haptics?.select()
                val row = rows[key]
                if (row != null && row.submenu && activeGroup == null) {
                    dwell = scope.launch {
                        delay(MenuLiquidMotion.SubmenuHoverOpenMillis)
                        if (highlightKey == key) {
                            openedByDwell = true
                            row.onClick()
                        }
                    }
                }
            }
        }
        if (!reduced) pullTarget = menuPullOffset(pointer, panelBounds, pullMaxPx)
    }

    /** Finger lifted: activate the highlighted row. Releasing over nothing does nothing. */
    internal fun end() {
        if (!scrubbing) return
        scrubbing = false
        pullTarget = Offset.Zero
        dwell?.cancel()
        val dwelled = openedByDwell
        openedByDwell = false
        val key = highlightKey
        highlightKey = null
        val row = key?.let { rows[it] } ?: return
        if (!row.enabled || (row.submenu && dwelled)) return
        row.onClick()
    }

    /** The menu closed or reopened: forget everything. */
    internal fun reset() {
        dwell?.cancel()
        openedByDwell = false
        scrubbing = false
        highlightKey = null
        pullTarget = Offset.Zero
        rows.clear()
    }
}

/** Provided to the rows of one open menu so they can register for drag-select. */
internal val LocalMenuDragSession = compositionLocalOf<MenuDragSession?> { null }

/** Registers a menu row for drag-select under the submenu it belongs to. */
@Composable
private fun Modifier.menuDragRow(enabled: Boolean, submenu: Boolean = false, onClick: () -> Unit): Modifier {
    val session = LocalMenuDragSession.current ?: return this
    val group = LocalMenuGroup.current
    val key = remember { Any() }
    val currentOnClick by rememberUpdatedState(onClick)
    val currentEnabled by rememberUpdatedState(enabled)
    DisposableEffect(session, key) {
        onDispose { session.rows.remove(key) }
    }
    return onGloballyPositioned { coords ->
        session.rows[key] = MenuDragRow(coords, currentEnabled, group, submenu) { currentOnClick() }
    }
}

/**
 * The menu opens on touch-down (iOS `showsMenuAsPrimaryAction`) and the same finger can slide
 * onto a row and release to activate it. A quick tap leaves the menu open. The gesture is consumed
 * once the menu opened, so the trigger's own click never fires.
 */
fun Modifier.menuScrubLauncher(source: MenuMorphSource, onOpen: () -> Unit): Modifier = composed {
    // The launcher may sit inside a capsule-sized source: map the finger from THIS node, not the source.
    var originOnScreen by remember { mutableStateOf(Offset.Zero) }
    val open by rememberUpdatedState(onOpen)
    this
        .onGloballyPositioned { originOnScreen = it.positionOnScreen() }
        .pointerInput(source) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val downId = down.id
                open()
                var dragging = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == downId }
                    val up = change == null || change.changedToUp()
                    event.changes.forEach { it.consume() }
                    if (up) {
                        if (dragging) source.dragSession?.end()
                        break
                    }
                    if (!dragging && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                        dragging = true
                    }
                    if (dragging) source.dragSession?.moveTo(originOnScreen + change.position)
                }
            }
        }
}

/**
 * The panel side of drag-select: watches presses on the menu's own rows, and once the finger travels
 * past slop, takes the gesture over (consuming it, so the row clicks never fire), scrubs the
 * highlight with the finger and activates on release. A plain tap falls through to the row's click.
 */
private fun Modifier.menuScrubGesture(session: MenuDragSession): Modifier = pointerInput(session) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val downId = down.id
        var scrubbing = false
        var travelled = 0f
        var last = down.position
        session.touch(session.panelBounds.topLeft + down.position)
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == downId }
            if (!scrubbing) {
                if (change == null || change.changedToUp() || change.isConsumed) {
                    session.release()
                    break
                }
                travelled += (change.position - last).getDistance()
                last = change.position
                if (travelled > viewConfiguration.touchSlop) {
                    scrubbing = true
                    session.moveTo(session.panelBounds.topLeft + change.position)
                }
            } else {
                val up = change == null || change.changedToUp()
                event.changes.forEach { it.consume() }
                if (up) {
                    session.end()
                    break
                }
                session.moveTo(session.panelBounds.topLeft + change.position)
            }
        }
    }
}

/** Highlight pill: inset from the menu edges and from the row (iOS 26, measured). */
private val MenuHighlightInsetH = 8.dp
private val MenuHighlightInsetV = 1.dp

/**
 * The rows' highlight pills: each row's pill cross-fades (in fast, out slow) and never slides.
 * [origin] is where this drawing box sits in the menu's content box.
 */
@Composable
private fun MenuRowHighlights(session: MenuDragSession, group: Any?, origin: () -> Offset, modifier: Modifier) {
    val color = if (TsuzukiTheme.colors.isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.08f)
    val fades = remember { mutableStateMapOf<Any, Animatable<Float, AnimationVector1D>>() }
    val highlighted = session.highlightKey
    LaunchedEffect(highlighted) {
        if (highlighted != null && session.rows[highlighted]?.group == group && highlighted !in fades) {
            fades[highlighted] = Animatable(0f)
        }
        fades.forEach { (key, fade) ->
            val target = if (key == highlighted) 1f else 0f
            launch {
                fade.animateTo(
                    target,
                    tween(if (target > 0f) MenuLiquidMotion.HighlightInMillis else MenuLiquidMotion.HighlightOutMillis),
                )
            }
        }
    }
    Box(
        modifier.drawBehind {
            val o = origin()
            val ih = MenuHighlightInsetH.toPx()
            val iv = MenuHighlightInsetV.toPx()
            fades.forEach { (key, fade) ->
                val a = fade.value
                if (a <= 0.001f) return@forEach
                val row = session.rows[key] ?: return@forEach
                val bounds = session.localBounds(row) ?: return@forEach
                val w = bounds.width - 2f * ih
                val h = bounds.height - 2f * iv
                if (w <= 0f || h <= 0f) return@forEach
                drawRoundRect(
                    color = color,
                    topLeft = Offset(bounds.left - o.x + ih, bounds.top - o.y + iv),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(h / 2f),
                    alpha = a,
                )
            }
        },
    )
}

@Composable
fun PullDownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    paneTitle: String? = null,
    properties: PopupProperties = PopupProperties(focusable = true),
    width: Dp? = null,
    maxHeight: Dp? = null,
    /** LIB-04: space the popover never enters (the shell's tab bar); its bottom edge ends above it. */
    bottomInset: Dp = 0.dp,
    morphSource: MenuMorphSource? = LocalMenuMorphSource.current,
    content: @Composable ColumnScope.() -> Unit,
) {
    PullDownMenuSeeded(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        paneTitle = paneTitle,
        properties = properties,
        width = width,
        maxHeight = maxHeight,
        bottomInset = bottomInset,
        morphSource = morphSource,
        content = content,
    )
}

@Composable
internal fun PullDownMenuSeeded(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    paneTitle: String? = null,
    properties: PopupProperties = PopupProperties(focusable = true),
    width: Dp? = null,
    maxHeight: Dp? = null,
    bottomInset: Dp = 0.dp,
    morphSource: MenuMorphSource? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val motion = TsuzukiTheme.motion
    val haptics = LocalHaptics.current ?: rememberHaptics()
    val density = LocalDensity.current
    val labels = LocalTsuzukiComponentLabels.current
    val glass = LocalGlassContext.current
    val scope = rememberCoroutineScope()

    val offsetPx = remember(offset, density) {
        with(density) { IntOffset(offset.x.roundToPx(), offset.y.roundToPx()) }
    }
    val morph = !motion.reduced
    val padPx = with(density) { LiquidPad.roundToPx() }
    val bottomInsetPx = with(density) { bottomInset.roundToPx() }
    val positionProvider = remember(offsetPx, bottomInsetPx, morph, morphSource, density) {
        PullDownMenuPositionProvider(
            offsetPx = offsetPx,
            marginPx = with(density) { 8.dp.roundToPx() },
            padPx = padPx,
            bottomInsetPx = bottomInsetPx,
            morphAnchor = morphSource,
            outsetPx = with(density) { MenuOutset.roundToPx() },
        )
    }
    val submenuHolder = remember { SubmenuHolder() }
    val dragSession = remember {
        MenuDragSession(
            haptics = haptics,
            pullMaxPx = with(density) { MenuLiquidMotion.PullMax.toPx() },
            reduced = !morph,
            scope = scope,
        )
    }
    val liquid = remember { MenuLiquid(scope) }
    val borderlessTrigger = morphSource?.borderless == true
    val footprint = remember { SubmenuFootprint() }
    val activeSubmenu = submenuHolder.activeSubmenu
    var mounted by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var panelPlaced by remember { mutableStateOf(false) }
    var panelSize by remember { mutableStateOf(Size.Zero) }
    // C-OVL-04: the probe/scroll decision is sticky for the menu's lifetime. Deciding it fresh on
    // every measure swapped between two subcomposition slots whenever the height crossed the cap,
    // recreating the panel and dropping its scroll state (Columns resize flash).
    var panelScrolls by remember { mutableStateOf(false) }
    // The drop sags toward open space: down for a menu that grows down, up for one that grows up.
    fun sagPx(): Float = panelSize.height * (if (positionProvider.placement.grow == MenuGrow.Up) -1f else 1f)
    fun landed() {
        closing = false
        morphSource?.reveal(wobble = false)
        mounted = false
        panelPlaced = false
    }
    // A menu that leaves composition is gone: give the trigger back so a shared source (one
    // capsule reused across screens) never stays hidden.
    DisposableEffect(morphSource) {
        onDispose {
            if (morphSource != null && morphSource.covered) {
                if (morphSource.dragSession === dragSession) morphSource.dragSession = null
                morphSource.reveal(wobble = false)
            }
        }
    }
    LaunchedEffect(expanded) {
        if (expanded) {
            closing = false
            submenuHolder.activeSubmenu = null
            dragSession.reset()
            morphSource?.dragSession = dragSession
            mounted = true
            haptics.select()
            if (morph) {
                // Only a settled menu starts from the button; an interrupted close turns around in place.
                val fromRest = !liquid.inFlight
                if (fromRest) {
                    snapshotFlow { panelPlaced }.first { it }
                    // One frame: the panel's glass has drawn once, so the pour is glass from its
                    // first frame instead of the opaque fallback fill.
                    withFrameNanos { }
                }
                // The drop leaves the button once the press swell has had its ~70 ms.
                val sincePress = SystemClock.uptimeMillis() - (morphSource?.pressedAtMillis ?: 0L)
                val lead = (MenuLiquidMotion.PressLeadMillis - sincePress).coerceIn(0L, MenuLiquidMotion.PressLeadMillis)
                liquid.open(fromRest, lead, sagPx()) { morphSource?.hide() }
            } else {
                morphSource?.hide()
            }
        } else {
            if (morphSource?.dragSession === dragSession) morphSource?.dragSession = null
            dragSession.reset()
            if (morph) {
                if (liquid.inFlight) {
                    closing = true
                    liquid.close(
                        sagPx(),
                        landDropPx = with(density) { MenuLiquidMotion.LandDrop.toPx() },
                        onReveal = { morphSource?.reveal(wobble = false) },
                        onLanded = ::landed,
                        borderless = borderlessTrigger,
                    )
                } else if (mounted) {
                    // Closed before the drop left the button: nothing to drain.
                    liquid.cancelPour()
                    landed()
                }
            } else {
                morphSource?.reveal()
            }
        }
    }

    val transition = updateTransition(targetState = expanded, label = "PullDownMenuTransition")
    val alpha by transition.animateFloat(
        transitionSpec = {
            if (motion.reduced) {
                M3Motion.reducedFade()
            } else if (targetState) {
                tween(120, easing = M3Motion.EmphasizedDecelerate)
            } else {
                tween(180, easing = M3Motion.EmphasizedAccelerate)
            }
        },
        label = "MenuAlpha",
    ) { isVisible -> if (isVisible) 1f else 0f }
    LaunchedEffect(transition.currentState, transition.targetState, morph) {
        if (!morph && !transition.currentState && !transition.targetState) {
            mounted = false
            panelPlaced = false
        }
    }
    LaunchedEffect(dragSession, morph) {
        snapshotFlow { dragSession.pullTarget }.collectLatest { target -> if (morph) liquid.pullTo(target) }
    }

    if (!mounted) return

    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val dynamicMaxHeight = remember(screenHeightDp, positionProvider.maxAvailableHeightPx, density) {
        if (positionProvider.maxAvailableHeightPx < Int.MAX_VALUE) {
            with(density) { positionProvider.maxAvailableHeightPx.toDp() }
        } else {
            (screenHeightDp - 64.dp).coerceAtLeast(160.dp)
        }
    }
    // An external cap (a filter pane that must scroll well before the window edge) wins when tighter.
    val effectiveMaxHeight = if (maxHeight != null && maxHeight < dynamicMaxHeight) maxHeight else dynamicMaxHeight

    val submenuProgress = remember { Animatable(0f, visibilityThreshold = 0.001f) }
    val presenting = activeSubmenu != null
    positionProvider.positionLocked = presenting
    val shownSubmenu = remember { mutableStateOf<SubmenuData?>(null) }
    if (activeSubmenu != null) {
        shownSubmenu.value = activeSubmenu
    }
    dragSession.activeGroup = activeSubmenu
    val showSubmenu = presenting || submenuProgress.value > 0f
    LaunchedEffect(showSubmenu) {
        if (!showSubmenu) footprint.cutAlpha = 0f
    }
    LaunchedEffect(presenting) {
        if (presenting) {
            if (motion.reduced) submenuProgress.snapTo(1f) else submenuProgress.animateTo(1f, MenuLiquidMotion.SubmenuOpen)
        } else if (submenuProgress.value > 0f) {
            if (motion.reduced) submenuProgress.snapTo(0f) else submenuProgress.animateTo(0f, MenuLiquidMotion.SubmenuClose)
        }
    }

    MenuLayerPopup(
        popupPositionProvider = positionProvider,
        onDismissRequest = {
            if (submenuHolder.activeSubmenu != null) {
                submenuHolder.activeSubmenu = null
            } else {
                onDismissRequest()
            }
        },
        properties = properties,
    ) {
        CompositionLocalProvider(
            LocalInsideGlass provides false,
            LocalSubmenuHolder provides submenuHolder,
            // Menus opened from inside this menu pour from their own rows, not from this trigger.
            LocalMenuMorphSource provides null,
        ) {
            val cardShape = tsuzukiRounded(TsuzukiCorners.menu)
            val snapshot = morphSource?.snapshot
            val menuWidth = width ?: MenuWidth
            fun frame(): MenuLiquidFrame = with(density) {
                menuLiquidFrame(
                    trigger = positionProvider.anchorRectF(),
                    panel = panelSize,
                    menuRadius = TsuzukiCorners.menu.toPx(),
                    morph = liquid.morph.value,
                    drop = liquid.drop.value,
                    corner = liquid.corner.value,
                    presence = liquid.presence.value,
                    absorb = liquid.absorb.value,
                    content = liquid.content.value,
                    label = liquid.label.value,
                    press = morphSource?.press?.value ?: 0f,
                    pressGrowPx = MenuLiquidMotion.PressGrow.toPx(),
                    mergePx = MenuLiquidMotion.MergeSmoothness.toPx(),
                    borderless = borderlessTrigger,
                    closing = closing,
                )
            }
            SubcomposeLayout { constraints ->
                val menuPanel: @Composable (Boolean) -> Unit = { scrollable ->
                    val menuScroll = rememberScrollState()
                    // The submenu is raised over the menu and sticks out past its sides, so it is a
                    // sibling of the glass (outside its clip) under one shared morph layer.
                    Box(
                        modifier
                            // Scrollable menus keep their scroll; the rest scrub with a dragged finger.
                            .then(if (scrollable) Modifier else Modifier.menuScrubGesture(dragSession))
                            .graphicsLayer {
                                shadowElevation = MenuShadowElevation.toPx() * (if (morph && borderlessTrigger) frame().glassAlpha else 1f)
                                ambientShadowColor = Color.Black.copy(alpha = 0.35f)
                                spotShadowColor = Color.Black.copy(alpha = 0.35f)
                                if (morph) {
                                    // The button and the drop are one liquid; the pinned corner
                                    // carries the landing overshoot, and a finger dragged outside
                                    // stretches the menu toward it (half moves it, half stretches it).
                                    val f = frame()
                                    val s = f.layerScale
                                    val pull = liquid.pull.value
                                    val w = size.width.coerceAtLeast(1f)
                                    val h = size.height.coerceAtLeast(1f)
                                    val pin = positionProvider.origin
                                    transformOrigin = TransformOrigin.Center
                                    scaleX = s * (1f + abs(pull.x) / w)
                                    scaleY = s * (1f + abs(pull.y) / h)
                                    translationX = (pin.pivotFractionX * w - w / 2f) * (1f - s) + pull.x / 2f
                                    translationY = (pin.pivotFractionY * h - h / 2f) * (1f - s) + pull.y / 2f
                                    shape = LiquidGlassShape(f.glass)
                                    clip = false
                                    this.alpha = liquid.fade.value
                                } else {
                                    transformOrigin = positionProvider.origin
                                    shape = cardShape
                                    clip = true
                                    this.alpha = alpha
                                }
                            }
                            .semantics { this.paneTitle = paneTitle ?: labels.menu },
                    ) {
                        GlassSurface(
                            context = glass,
                            // Blurred glass behind the menu, or fully opaque: never see-through without blur.
                            material = if (glass?.enabled == true) GlassMaterial.Thick else GlassMaterial.Opaque,
                            frost = MenuFrost,
                            shape = cardShape,
                            liquid = if (morph) ({ frame().glass }) else null,
                            glassAlpha = if (morph && borderlessTrigger) ({ frame().glassAlpha }) else null,
                            modifier = Modifier
                                .width(menuWidth)
                                .heightIn(max = effectiveMaxHeight)
                                .onGloballyPositioned {
                                    panelSize = Size(it.size.width.toFloat(), it.size.height.toFloat())
                                    panelPlaced = true
                                    val origin = it.positionOnScreen()
                                    dragSession.panelBounds = Rect(origin.x, origin.y, origin.x + it.size.width, origin.y + it.size.height)
                                },
                        ) {
                            Box(Modifier.matchParentSize().onGloballyPositioned { dragSession.content = it })
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                    .drawWithContent {
                                        drawContent()
                                        val a = footprint.cutAlpha
                                        if (a > 0.001f && footprint.width > 0f && footprint.height > 0f) {
                                            val r = footprint.radius.coerceAtMost(min(footprint.width, footprint.height) / 2f)
                                            drawRoundRect(
                                                color = Color.Black.copy(alpha = a),
                                                topLeft = Offset(footprint.left, footprint.top),
                                                size = Size(footprint.width, footprint.height),
                                                cornerRadius = CornerRadius(r),
                                                blendMode = BlendMode.DstOut,
                                            )
                                        }
                                    },
                            ) {
                                MenuRowHighlights(dragSession, group = null, origin = { Offset.Zero }, modifier = Modifier.matchParentSize())
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(if (scrollable) Modifier.fadeEdges(menuScroll).verticalScroll(menuScroll) else Modifier)
                                        .graphicsLayer {
                                            if (morph) {
                                                // Laid out once at the menu's size; it rides the drop, scaled
                                                // with it (never under half), and sharpens as it lands.
                                                val f = frame()
                                                scaleX = f.contentScale
                                                scaleY = f.contentScale
                                                translationX = f.contentOffset.x
                                                translationY = f.contentOffset.y
                                                this.alpha = f.contentAlpha * (1f - SubmenuParentDim * submenuProgress.value.coerceIn(0f, 1f))
                                                val blur = f.contentBlur * MenuLiquidMotion.ContentMaxBlur.toPx()
                                                renderEffect = if (blur >= 0.5f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                    BlurEffect(blur, blur, TileMode.Decal)
                                                } else {
                                                    null
                                                }
                                            } else {
                                                this.alpha = alpha * (1f - SubmenuParentDim * submenuProgress.value.coerceIn(0f, 1f))
                                            }
                                        }
                                        .padding(vertical = MenuVerticalPadding),
                                ) {
                                    CompositionLocalProvider(LocalMenuDragSession provides dragSession) {
                                        content()
                                    }
                                }
                            }
                            if (morph) {
                                // The button's own pixels where it sits: they blur away as the drop
                                // leaves and come back as the button swallows it on the way home. The
                                // press light rides the button's glass.
                                Box(
                                    Modifier
                                        .matchParentSize()
                                        .graphicsLayer {
                                            val f = frame()
                                            val trigger = positionProvider.anchorRectF()
                                            this.alpha = f.labelAlpha
                                            val blur = f.labelBlur * MenuLiquidMotion.LabelMaxBlur.toPx()
                                            renderEffect = if (blur >= 0.5f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                BlurEffect(blur, blur, TileMode.Decal)
                                            } else {
                                                null
                                            }
                                            if (size.width > 0f && size.height > 0f) {
                                                transformOrigin = TransformOrigin(trigger.center.x / size.width, trigger.center.y / size.height)
                                            }
                                            scaleX = f.labelScale
                                            scaleY = f.labelScale
                                        }
                                        .drawBehind {
                                            val trigger = positionProvider.anchorRectF()
                                            if (snapshot != null) translate(trigger.left, trigger.top) { drawLayer(snapshot) }
                                        },
                                )
                                Box(
                                    Modifier
                                        .matchParentSize()
                                        .drawBehind {
                                            val f = frame()
                                            if (f.brighten > 0.005f && !f.glass.a.isEmpty && !borderlessTrigger) {
                                                val r = f.glass.a.toRoundRect()
                                                drawRoundRect(
                                                    color = Color.White.copy(alpha = f.brighten),
                                                    topLeft = Offset(r.left, r.top),
                                                    size = Size(r.width, r.height),
                                                    cornerRadius = r.topLeftCornerRadius,
                                                )
                                            }
                                        },
                                )
                            }
                        }
                        val shown = shownSubmenu.value
                        if (showSubmenu && shown != null) {
                            InlineSubmenu(
                                data = shown,
                                icon = submenuHolder.anchorIcon,
                                progress = { submenuProgress.value },
                                anchorBounds = {
                                    val root = dragSession.content
                                    val row = submenuHolder.anchor
                                    if (root != null && row != null && root.isAttached && row.isAttached) {
                                        root.localBoundingBoxOf(row, clipBounds = false)
                                    } else {
                                        null
                                    }
                                },
                                menuSize = { panelSize },
                                session = dragSession,
                                glass = glass,
                                footprint = footprint,
                                onClose = { submenuHolder.activeSubmenu = null },
                            )
                        }
                    }
                }
                val capPx = with(density) { effectiveMaxHeight.roundToPx() }
                // A slot may be subcomposed once per measure pass, so the probe and the scroll
                // panel are separate slots. The flip happens once, on the first oversize measure,
                // before anything can scroll, and is sticky while the menu is open.
                var parentMeasurable = subcompose(if (panelScrolls) "panel-scroll" else "panel-probe") { menuPanel(panelScrolls) }[0].measure(
                    if (panelScrolls) {
                        constraints.copy(minWidth = 0, minHeight = 0)
                    } else {
                        constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
                    },
                )
                if (!panelScrolls && parentMeasurable.height >= capPx) {
                    panelScrolls = true
                    parentMeasurable = subcompose("panel-scroll") { menuPanel(true) }[0].measure(
                        constraints.copy(minWidth = 0, minHeight = 0),
                    )
                }
                layout(parentMeasurable.width + 2 * padPx, parentMeasurable.height + 2 * padPx) {
                    parentMeasurable.place(padPx, padPx)
                }
            }
        }
    }
}

/** Fallback fill when live Kasane backdrop is unavailable: tracks opaque & glass tint settings like the parent menu. */
private fun submenuFallbackFill(
    colors: TsuzukiColors,
    tintStrength: Float,
    opaqueGlass: Boolean,
): Color = if (opaqueGlass) {
    colors.elevated
} else {
    colors.glassTint.copy(alpha = GlassMaterial.Thick.tintAlpha(colors.isDark, tintStrength))
}

/**
 * iOS 26 inline submenu (uikit.even.how, frame-stepped): the row lifts into its own raised
 * translucent Liquid Glass panel that sticks out a little past the menu's sides and unrolls down
 * from the row; uncovered parent rows fade back under it while covered rows are masked out.
 */
@Composable
private fun BoxScope.InlineSubmenu(
    data: SubmenuData,
    icon: ImageVector?,
    progress: () -> Float,
    anchorBounds: () -> Rect?,
    menuSize: () -> Size,
    session: MenuDragSession,
    glass: GlassContext?,
    footprint: SubmenuFootprint,
    onClose: () -> Unit,
) {
    val colors = TsuzukiTheme.colors
    val dark = colors.isDark
    val labels = LocalTsuzukiComponentLabels.current
    val density = LocalDensity.current
    val insetPx = with(density) { SubmenuPanelInset.toPx() }
    val padPx = with(density) { SubmenuInnerPad.toPx() }
    val maxOutPx = with(density) { SubmenuPanelOutset.toPx() }
    val maxOutInt = maxOutPx.roundToInt()
    val highlight = if (dark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.08f)
    val frame = remember(data) { arrayOf(InlineSubmenuFrame(0f, 0f)) }
    val rowRest = remember(data) { arrayOf(Rect.Zero) }
    if (rowRest[0] == Rect.Zero || progress() <= 0f) {
        anchorBounds()?.let { rowRest[0] = it }
    }
    val view = LocalView.current
    val tintStrength = LocalGlassTintStrength.current
    val opaqueGlass = LocalOpaqueGlass.current
    val backdrop = glass?.backdrop
    val useKasane = glass?.enabled == true &&
        LocalLiquidGlass.current &&
        !opaqueGlass &&
        Build.VERSION.SDK_INT >= 33 &&
        backdrop != null &&
        glass.sourceView === view &&
        glass.haze.areas.isNotEmpty()

    fun p(): Float = progress().coerceIn(0f, 1f)
    fun startTop(): Float = (rowRest[0].top - padPx).coerceAtLeast(insetPx)
    fun startHeight(): Float = rowRest[0].height + padPx
    fun top(): Float = lerp(startTop(), frame[0].top, progress())
    fun height(): Float = lerp(startHeight(), frame[0].height, progress())

    // A tap outside the panel (on dimmed parent rows) folds the submenu back up.
    Box(
        Modifier
            .matchParentSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            ),
    )

    // Raised submenu shadow + translucent Liquid Glass plane sticking out past the menu's sides.
    Layout(
        modifier = Modifier.matchParentSize(),
        content = {
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val pv = p()
                        if (pv <= 0.001f) {
                            footprint.cutAlpha = 0f
                            return@drawBehind
                        }
                        val panelAlpha = (pv * 2.5f).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
                        val radius = SubmenuPanelRadius.toPx()
                        val out = maxOutPx * pv
                        val left = maxOutInt - out
                        val topY = top()
                        val bw = (size.width - 2f * left).coerceAtLeast(1f)
                        val bh = height().coerceAtLeast(1f)
                        val r = radius.coerceAtMost(min(bw, bh) / 2f)
                        footprint.left = -out
                        footprint.top = topY
                        footprint.width = bw
                        footprint.height = bh
                        footprint.radius = r
                        footprint.cutAlpha = smoothstep(0f, 0.30f, pv)
                        for (i in 1..6) {
                            val g = i * 3f * density.density
                            drawRoundRect(
                                color = Color.Black.copy(alpha = (if (dark) 0.055f else 0.025f) * pv),
                                topLeft = Offset(left - g / 2f, topY - g / 2f + g * 0.4f),
                                size = Size(bw + g, bh + g),
                                cornerRadius = CornerRadius(r + g / 2f),
                            )
                        }
                        if (!useKasane) {
                            drawRoundRect(
                                color = submenuFallbackFill(colors, tintStrength, opaqueGlass),
                                topLeft = Offset(left, topY),
                                size = Size(bw, bh),
                                cornerRadius = CornerRadius(r),
                                alpha = panelAlpha,
                            )
                            drawRoundRect(
                                color = Color.White.copy(alpha = (if (dark) 0.16f else 0.70f) * panelAlpha),
                                topLeft = Offset(left, topY),
                                size = Size(bw, bh),
                                cornerRadius = CornerRadius(r),
                                style = Stroke(0.5.dp.toPx()),
                            )
                        }
                    },
            ) {
                if (useKasane && backdrop != null) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = { RectangleShape },
                                layerBlock = {
                                    val pv = p()
                                    alpha = (pv * 2.5f).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
                                },
                                effects = {
                                    if (Build.VERSION.SDK_INT >= 33) {
                                        val pv = p()
                                        val out = maxOutPx * pv
                                        val t = top()
                                        val h = height().coerceAtLeast(1f)
                                        val left = maxOutInt - out
                                        val bw = (size.width - 2f * left).coerceAtLeast(1f)
                                        val r = SubmenuPanelRadius.toPx().coerceAtMost(min(bw, h) / 2f)
                                        val tint = colors.glassTint.copy(
                                            alpha = GlassMaterial.Thick.kasaneTintAlpha(dark, tintStrength),
                                        )
                                        liquidGlass(
                                            LiquidGlassGeometry(
                                                GlassBox.Empty,
                                                GlassBox(size.width / 2f, t + h / 2f, bw / 2f, h / 2f, r),
                                                0f,
                                            ),
                                            dark = dark,
                                            tint = tint,
                                            page = colors.background,
                                            highlightStrength = if (dark) 0.35f else 0.6f,
                                            blurRadius = SubmenuFrost,
                                        )
                                    }
                                },
                                highlight = null,
                                shadow = null,
                            ),
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val widenedW = (w + 2 * maxOutInt).coerceAtLeast(0)
        val placeable = measurables[0].measure(Constraints.fixed(widenedW, h.coerceAtLeast(0)))
        layout(w, h) {
            placeable.place(-maxOutInt, 0)
        }
    }

    MenuRowHighlights(
        session = session,
        group = data,
        origin = { Offset.Zero },
        modifier = Modifier
            .matchParentSize()
            .graphicsLayer {
                val h = height()
                shape = BandRoundedShape(top(), h, SubmenuPanelRadius.toPx())
                clip = true
            },
    )

    val scroll = remember(data) { ScrollState(0) }
    Layout(
        content = {
            Column(Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(SubmenuInnerPad))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = MenuRowHeight)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClickLabel = labels.collapseSubmenu,
                            onClick = onClose,
                        )
                        .drawBehind {
                            val ih = MenuHighlightInsetH.toPx()
                            val iv = MenuHighlightInsetV.toPx()
                            val h = size.height - 2f * iv
                            drawRoundRect(
                                color = highlight,
                                topLeft = Offset(ih, iv),
                                size = Size(size.width - 2f * ih, h),
                                cornerRadius = CornerRadius(h / 2f),
                                alpha = p(),
                            )
                        }
                        .padding(start = MenuRowStart, end = MenuRowEnd),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MenuRowLeading(false, false, colors.text, icon, null, colors.text)
                    TsuzukiText(
                        text = data.title,
                        style = TsuzukiTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    Image(
                        imageVector = TsuzukiIcons.ChevronRight,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colors.secondary),
                        modifier = Modifier
                            .size(14.dp)
                            .graphicsLayer { rotationZ = 90f * p() },
                    )
                }
                Spacer(
                    Modifier
                        .padding(horizontal = MenuRowStart, vertical = 4.dp)
                        .fillMaxWidth()
                        .height(TsuzukiSpacing.hairline)
                        .graphicsLayer { alpha = p() }
                        .background(colors.separator.copy(alpha = 0.6f)),
                )
            }
            Column(
                Modifier
                    .graphicsLayer {
                        val pv = progress()
                        alpha = (pv * 1.6f).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
                    }
                    .verticalScroll(scroll)
                    .padding(bottom = SubmenuInnerPad),
            ) {
                CompositionLocalProvider(LocalMenuGroup provides data) {
                    data.content(this)
                }
            }
        },
        modifier = Modifier
            .offset { IntOffset(0, top().roundToInt()) }
            .graphicsLayer {
                shape = TopRoundedShape(height(), SubmenuPanelRadius.toPx())
                clip = true
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) { measurables, constraints ->
        // The box this sits in also measures the submenu, so its width comes from the menu, not
        // from the incoming constraints (the popup's whole width).
        val w = menuSize().width.roundToInt().coerceIn(0, constraints.maxWidth)
        val header = measurables[0].measure(Constraints(minWidth = w, maxWidth = w))
        val available = (menuSize().height - 2f * insetPx - header.height).roundToInt().coerceAtLeast(0)
        val body = measurables[1].measure(Constraints(minWidth = w, maxWidth = w, maxHeight = available))
        frame[0] = inlineSubmenuFrame(startTop(), (header.height + body.height).toFloat(), menuSize().height, insetPx)
        layout(w, header.height + body.height) {
            header.place(0, 0)
            body.place(0, header.height)
        }
    }
}

/** Keeps the band [top .. top + height] of the layer, its corners rounded by [radius]. */
private data class BandRoundedShape(val top: Float, val height: Float, val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val t = top.coerceIn(0f, size.height)
        val b = (top + height).coerceIn(t, size.height)
        val h = (b - t).coerceAtLeast(0f)
        return Outline.Rounded(RoundRect(0f, t, size.width, b, CornerRadius(radius.coerceAtMost(h / 2f))))
    }
}

/** Keeps the top [height] px of the layer, its corners rounded by [radius]. */
private data class TopRoundedShape(val height: Float, val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val h = height.coerceIn(0f, size.height)
        return Outline.Rounded(RoundRect(0f, 0f, size.width, h, CornerRadius(radius.coerceAtMost(h / 2f))))
    }
}

@Composable
fun PullDownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    actions: List<PullDownAction>,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    paneTitle: String? = null,
    properties: PopupProperties = PopupProperties(focusable = true),
    maxHeight: Dp? = null,
    morphSource: MenuMorphSource? = LocalMenuMorphSource.current,
) {
    PullDownMenuSeeded(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        paneTitle = paneTitle,
        properties = properties,
        maxHeight = maxHeight,
        morphSource = morphSource,
    ) {
        PullDownActionRows(actions, onDismissRequest)
    }
}

/**
 * One menu level's live rows. An open submenu re-resolves its rows from this holder on every
 * pass, so checkmarks, sort direction and multi-select keep moving while the panel is open
 * instead of freezing at the snapshot taken when the row was tapped.
 */
internal class MenuLevelRows(initial: List<PullDownAction>) {
    var rows: List<PullDownAction> by mutableStateOf(initial)
}

/** Freshest rows for the open submenu [title], re-resolved from the level's live rows. */
internal fun MenuLevelRows?.submenuRows(title: String, opened: List<PullDownAction>): List<PullDownAction> =
    this?.rows?.firstOrNull { it.submenu != null && it.label == title }?.submenu ?: opened

@Composable
private fun PullDownActionRows(actions: List<PullDownAction>, onDismissRequest: () -> Unit) {
    val reduced = TsuzukiTheme.motion.reduced
    val scope = rememberCoroutineScope()
    val submenuHolder = LocalSubmenuHolder.current
    val level = remember { MenuLevelRows(actions) }
    level.rows = actions
    val reserveCheck = actions.any { it.selected != null && it.icon == null && it.iconContent == null }
    CompositionLocalProvider(LocalMenuReserveCheck provides reserveCheck) {
        actions.forEachIndexed { index, action ->
            if (index > 0 && actions[index - 1].group != action.group) PullDownSectionDivider()
            val children = action.submenu
            if (children != null) {
                PullDownSubmenuItem(
                    title = action.label,
                    icon = action.icon,
                    onClick = {
                        submenuHolder?.activeSubmenu = SubmenuData(action.label) {
                            PullDownActionRows(level.submenuRows(action.label, children), onDismissRequest)
                        }
                    },
                )
                return@forEachIndexed
            }
            PullDownMenuItem(
                text = action.label,
                subtitle = action.subtitle,
                icon = action.icon,
                iconContent = action.iconContent,
                selected = action.selected,
                destructive = action.destructive,
                enabled = action.enabled,
                onClick = {
                    onDismissRequest()
                    if (reduced) {
                        action.onClick()
                    } else {
                        scope.launch {
                            withFrameNanos { }
                            action.onClick()
                        }
                    }
                },
            )
        }
    }
}

/* Menu row columns (dp from the menu's leading edge): icons sit flush at 16 with 12 dp gap (text
   at 48); icon-less checkable/radio rows reserve 24 dp for the checkmark (text at 40); plain
   text rows without icons or checkmarks start flush at 16. Rows are 44 tall, 56 with subtitle. */
internal val MenuRowHeight = 44.dp
private val MenuRowHeightWithSubtitle = 56.dp
private val MenuRowStart = 16.dp
private val MenuRowEnd = 16.dp
private val MenuIconSize = 20.dp
private val MenuIconGap = 12.dp
private val MenuCheckColumnWidth = 24.dp
private val MenuCheckSize = 14.dp

internal val LocalMenuReserveCheck = compositionLocalOf { false }

internal fun menuRowTextStartDp(hasIcon: Boolean, reserveCheck: Boolean): Dp = when {
    hasIcon -> MenuRowStart + MenuIconSize + MenuIconGap
    reserveCheck -> MenuRowStart + MenuCheckColumnWidth
    else -> MenuRowStart
}

/** The leading icon (flush at [MenuRowStart]) or check column when [reserveCheck] is set for icon-less menus. */
@Composable
private fun RowScope.MenuRowLeading(
    reserveCheck: Boolean,
    checked: Boolean,
    checkTint: Color,
    icon: ImageVector?,
    iconContent: (@Composable () -> Unit)?,
    iconTint: Color,
) {
    val hasIcon = icon != null || iconContent != null
    if (hasIcon) {
        Box(Modifier.size(MenuIconSize), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Image(imageVector = icon, contentDescription = null, colorFilter = ColorFilter.tint(iconTint), modifier = Modifier.size(MenuIconSize))
            } else {
                iconContent?.invoke()
            }
        }
        Spacer(Modifier.width(MenuIconGap))
    } else if (reserveCheck) {
        Box(Modifier.width(MenuCheckColumnWidth), contentAlignment = Alignment.CenterStart) {
            if (checked) {
                Image(
                    imageVector = TsuzukiIcons.Check,
                    contentDescription = LocalTsuzukiComponentLabels.current.selected,
                    colorFilter = ColorFilter.tint(checkTint),
                    modifier = Modifier.size(MenuCheckSize),
                )
            }
        }
    }
}

@Composable
fun PullDownMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconContent: (@Composable () -> Unit)? = null,
    selected: Boolean? = null,
    toggle: Boolean = false,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = TsuzukiTheme.colors
    val tint = when {
        !enabled -> colors.secondary.copy(alpha = 0.5f)
        destructive -> colors.destructive
        else -> colors.text
    }
    val hasIcon = icon != null || iconContent != null
    val reserveLeadingCheck = !hasIcon && (selected != null || LocalMenuReserveCheck.current)
    val interaction = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (subtitle != null) MenuRowHeightWithSubtitle else MenuRowHeight)
            .menuDragRow(enabled = enabled, onClick = onClick)
            .then(
                if (toggle) {
                    Modifier.toggleable(
                        value = selected == true,
                        interactionSource = interaction,
                        indication = null,
                        enabled = enabled,
                        role = Role.Checkbox,
                        onValueChange = { onClick() },
                    )
                } else {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        enabled = enabled,
                        role = menuRowRole(selected, toggle),
                        onClick = onClick,
                    )
                },
            )
            .padding(start = MenuRowStart, end = MenuRowEnd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MenuRowLeading(reserveLeadingCheck, selected == true, tint, icon, iconContent, tint)
        Column(
            modifier = Modifier.weight(1f).padding(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            TsuzukiText(
                text = text,
                style = TsuzukiTheme.typography.body,
                color = tint,
                // DET-14: long labels wrap instead of truncating.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                TsuzukiText(
                    text = subtitle,
                    style = TsuzukiTheme.typography.footnote,
                    color = colors.secondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (hasIcon && selected == true) {
            val checkTint = if (destructive) colors.destructive else colors.accent
            Image(
                imageVector = TsuzukiIcons.Check,
                contentDescription = LocalTsuzukiComponentLabels.current.selected,
                colorFilter = ColorFilter.tint(checkTint),
                modifier = Modifier.padding(start = 10.dp).size(15.dp),
            )
        }
    }
}

/** The separator between groups of rows (iOS 26): a hairline inset from both sides, 6 above and below. */
@Composable
fun PullDownSectionDivider(modifier: Modifier = Modifier) {
    Spacer(
        modifier = modifier
            .padding(horizontal = MenuRowStart, vertical = 6.dp)
            .fillMaxWidth()
            .height(TsuzukiSpacing.hairline)
            .background(TsuzukiTheme.colors.separator.copy(alpha = 0.6f)),
    )
}

@Composable
fun PullDownSubmenuItem(
    title: String,
    value: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = TsuzukiTheme.colors
    val submenuHolder = LocalSubmenuHolder.current
    var row by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val interaction = remember { MutableInteractionSource() }
    val open = {
        submenuHolder?.anchor = row
        submenuHolder?.anchorIcon = icon
        onClick()
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MenuRowHeight)
            .onGloballyPositioned { row = it }
            .menuDragRow(enabled = true, submenu = true, onClick = open)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = open,
            )
            .padding(start = MenuRowStart, end = MenuRowEnd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MenuRowLeading(false, false, colors.text, icon, null, colors.text)
        TsuzukiText(
            text = title,
            style = TsuzukiTheme.typography.body,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 8.dp),
        )
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (value != null) {
                TsuzukiText(
                    text = value,
                    style = TsuzukiTheme.typography.footnote,
                    color = colors.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            modifier = Modifier.padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (trailing != null) {
                trailing()
            } else {
                Image(
                    imageVector = TsuzukiIcons.ChevronRight,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colors.secondary),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
fun PullDownSubmenuOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = TsuzukiTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MenuRowHeight)
            .menuDragRow(enabled = true, onClick = onClick)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(start = MenuRowStart, end = MenuRowEnd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MenuRowLeading(true, selected, colors.accent, null, null, colors.text)
        TsuzukiText(
            text = label,
            style = TsuzukiTheme.typography.body,
            color = colors.text,
            modifier = Modifier.weight(1f, fill = trailing != null).padding(end = 8.dp),
        )
        trailing?.invoke(this)
    }
}
enum class PullDownTriState {
    Off,
    Include,
    Exclude,
}

fun menuRowRole(selected: Boolean?, toggle: Boolean): Role = when {
    toggle -> Role.Checkbox
    selected != null -> Role.RadioButton
    else -> Role.Button
}

data class PullDownPaletteOption(
    val icon: ImageVector,
    val label: String,
)

enum class ColorRole { None, Accent, Destructive }

fun triStateGlyph(state: PullDownTriState): Pair<ImageVector?, ColorRole> = when (state) {
    PullDownTriState.Off -> null to ColorRole.None
    PullDownTriState.Include -> TsuzukiIcons.Check to ColorRole.Accent
    PullDownTriState.Exclude -> TsuzukiIcons.X to ColorRole.Destructive
}

fun triStateDescription(
    state: PullDownTriState,
    labels: TsuzukiComponentLabels = TsuzukiComponentLabels(),
): String = when (state) {
    PullDownTriState.Off -> labels.triStateOff
    PullDownTriState.Include -> labels.triStateInclude
    PullDownTriState.Exclude -> labels.triStateExclude
}

@Composable
fun PullDownTriStateItem(
    label: String,
    state: PullDownTriState,
    onCycle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val colors = TsuzukiTheme.colors
    val labels = LocalTsuzukiComponentLabels.current
    val haptics = LocalHaptics.current ?: rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val (glyph, glyphRole) = triStateGlyph(state)
    val contentTint = if (enabled) colors.text else colors.secondary.copy(alpha = 0.5f)
    val cycle = {
        if (enabled) {
            haptics.toggleOn()
            onCycle()
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MenuRowHeight)
            .semantics { stateDescription = triStateDescription(state, labels) }
            .menuDragRow(enabled = enabled, onClick = cycle)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = cycle,
            )
            .padding(start = MenuRowStart, end = MenuRowEnd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(MenuIconSize), contentAlignment = Alignment.Center) {
                Image(
                    imageVector = icon,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(contentTint),
                    modifier = Modifier.size(MenuIconSize),
                )
            }
            Spacer(Modifier.width(MenuIconGap))
        }
        TsuzukiText(
            text = label,
            style = TsuzukiTheme.typography.body,
            color = contentTint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (glyph != null) {
            val badgeTint = if (glyphRole == ColorRole.Accent) colors.accent else colors.destructive
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(badgeTint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    imageVector = glyph,
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(badgeTint),
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
}

@Deprecated("Unused; tap cycles")
@Composable
fun PullDownTriStateItem(
    label: String,
    state: PullDownTriState,
    onSet: (PullDownTriState) -> Unit,
    onCycle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    PullDownTriStateItem(label = label, state = state, onCycle = onCycle, modifier = modifier, enabled = enabled, icon = null)
}

@Composable
fun PullDownToggleItem(
    label: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptics = LocalHaptics.current ?: rememberHaptics()
    PullDownMenuItem(
        text = label,
        onClick = {
            if (checked) haptics.toggleOff() else haptics.toggleOn()
            onChecked(!checked)
        },
        modifier = modifier,
        selected = checked,
        toggle = true,
        enabled = enabled,
    )
}

@Deprecated("Menus stop hosting steppers")
@Composable
fun PullDownStepperRow(
    label: String,
    value: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    valueLabel: String = value.toString(),
    canDecrement: Boolean = true,
    canIncrement: Boolean = true,
) {
    val colors = TsuzukiTheme.colors
    val labels = LocalTsuzukiComponentLabels.current
    val haptics = LocalHaptics.current ?: rememberHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TsuzukiSpacing.touchTarget)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TsuzukiText(
            text = label,
            style = TsuzukiTheme.typography.callout,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.tiny),
        ) {
            StepperButton(
                icon = TsuzukiIcons.Minus,
                contentDescription = labels.decreaseItem(label),
                enabled = canDecrement,
                onClick = {
                    haptics.select()
                    onDecrement()
                },
            )
            TsuzukiText(
                text = valueLabel,
                style = TsuzukiTheme.typography.callout.copy(textAlign = TextAlign.Center),
                color = colors.secondary,
                modifier = Modifier.widthIn(min = 36.dp),
            )
            StepperButton(
                icon = TsuzukiIcons.Plus,
                contentDescription = labels.increaseItem(label),
                enabled = canIncrement,
                onClick = {
                    haptics.select()
                    onIncrement()
                },
            )
        }
    }
}

@Composable
private fun StepperButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = TsuzukiTheme.colors
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(32.dp)
            .clip(CircleShape)
            .background(colors.text.copy(alpha = if (colors.isDark) 0.10f else 0.06f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            imageVector = icon,
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(if (enabled) colors.text else colors.secondary.copy(alpha = 0.5f)),
            modifier = Modifier.size(16.dp),
        )
    }
}

private fun Modifier.fadeEdges(scrollState: ScrollState): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fade = 24.dp.toPx()
            if (scrollState.value > 0) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black),
                        startY = 0f,
                        endY = fade,
                    ),
                    topLeft = Offset.Zero,
                    size = Size(size.width, fade),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (scrollState.value < scrollState.maxValue) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Black, Color.Transparent),
                        startY = size.height - fade,
                        endY = size.height,
                    ),
                    topLeft = Offset(0f, size.height - fade),
                    size = Size(size.width, fade),
                    blendMode = BlendMode.DstIn,
                )
            }
        }

@Composable
fun PullDownConfirmSheet(
    visible: Boolean,
    title: String,
    message: String? = null,
    confirmLabel: String? = null,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    destructive: Boolean = true,
    extraContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val labels = LocalTsuzukiComponentLabels.current
    val haptics = LocalHaptics.current ?: rememberHaptics()
    DetentSheet(
        visible = visible,
        title = title,
        onDismissRequest = onDismissRequest,
        kind = SheetKind.Confirm,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = TsuzukiSpacing.gutter, vertical = TsuzukiSpacing.small),
            verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.medium),
        ) {
            if (message != null) {
                TsuzukiText(
                    text = message,
                    style = TsuzukiTheme.typography.callout.copy(textAlign = TextAlign.Center),
                    color = TsuzukiTheme.colors.secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            extraContent?.invoke(this)
            PillButton(
                label = confirmLabel ?: labels.confirm,
                onClick = {
                    if (destructive) haptics.warning()
                    onDismissRequest()
                    onConfirm()
                },
                destructive = destructive,
                modifier = Modifier.fillMaxWidth(),
                large = true,
            )
            PillButton(
                label = labels.cancel,
                onClick = onDismissRequest,
                prominent = false,
                modifier = Modifier.fillMaxWidth(),
                large = true,
            )
        }
    }
}

data class PopUpItem<T>(
    val value: T,
    val label: String,
    val subtitle: String? = null,
    val icon: ImageVector? = null,
)

@Composable
fun <T> PopUpButton(
    selected: T,
    options: List<PopUpItem<T>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactive: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedItem = options.firstOrNull { it.value == selected }
    val selectedLabel = selectedItem?.label ?: selected.toString()
    val haptics = LocalHaptics.current ?: rememberHaptics()

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .then(if (interactive) Modifier.heightIn(min = TsuzukiSpacing.touchTarget) else Modifier)
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (interactive) {
                        Modifier.clickable(enabled = enabled, role = Role.Button) {
                            haptics.select()
                            expanded = true
                        }
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TsuzukiText(
                text = selectedLabel,
                modifier = Modifier.weight(1f, fill = false),
                style = TsuzukiTheme.typography.callout.copy(textAlign = TextAlign.End),
                color = if (enabled) TsuzukiTheme.colors.secondary else TsuzukiTheme.colors.secondary.copy(alpha = 0.5f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Image(
                imageVector = TsuzukiIcons.ChevronsUpDown,
                contentDescription = null,
                colorFilter = ColorFilter.tint(
                    if (enabled) TsuzukiTheme.colors.secondary else TsuzukiTheme.colors.secondary.copy(alpha = 0.5f),
                ),
                modifier = Modifier.size(14.dp),
            )
        }

        if (interactive) {
            PullDownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                val reserveCheck = options.any { it.icon == null }
                CompositionLocalProvider(LocalMenuReserveCheck provides reserveCheck) {
                    options.forEach { option ->
                        val isSelected = option.value == selected
                        PullDownMenuItem(
                            text = option.label,
                            subtitle = option.subtitle,
                            icon = option.icon,
                            selected = isSelected,
                            onClick = {
                                onSelect(option.value)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun <T> PopUpRow(
    title: String,
    selected: T,
    options: List<PopUpItem<T>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    divider: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = LocalHaptics.current ?: rememberHaptics()
    val stackValue = LocalDensity.current.fontScale >= 1.5f
    val menuDropdown: @Composable () -> Unit = {
        PullDownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            val reserveCheck = options.any { it.icon == null }
            CompositionLocalProvider(LocalMenuReserveCheck provides reserveCheck) {
                options.forEach { option ->
                    val isSelected = option.value == selected
                    PullDownMenuItem(
                        text = option.label,
                        subtitle = option.subtitle,
                        icon = option.icon,
                        selected = isSelected,
                        onClick = {
                            onSelect(option.value)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
    val labelBlock: @Composable (Modifier) -> Unit = { blockModifier ->
        Row(
            modifier = blockModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            leading?.invoke()
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TsuzukiText(
                    text = title,
                    style = TsuzukiTheme.typography.callout,
                    color = if (enabled) TsuzukiTheme.colors.text else TsuzukiTheme.colors.secondary.copy(alpha = 0.5f),
                )
                if (subtitle != null) {
                    TsuzukiText(
                        text = subtitle,
                        style = TsuzukiTheme.typography.footnote,
                        color = TsuzukiTheme.colors.secondary,
                    )
                }
            }
        }
    }
    Column(modifier) {
        val rowModifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TsuzukiSpacing.rowMin)
            .clickable(enabled = enabled, role = Role.Button) {
                haptics.select()
                expanded = true
            }
            .padding(horizontal = 16.dp, vertical = 10.dp)
        if (stackValue) {
            Column(rowModifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                labelBlock(Modifier)
                Box {
                    PopUpButton(
                        selected = selected,
                        options = options,
                        onSelect = onSelect,
                        enabled = enabled,
                        interactive = false,
                    )
                    menuDropdown()
                }
            }
        } else {
            Row(
                modifier = rowModifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                labelBlock(Modifier.weight(1f).padding(end = 12.dp))
                Box(Modifier.maxWidthFraction(0.44f), contentAlignment = Alignment.CenterEnd) {
                    PopUpButton(
                        selected = selected,
                        options = options,
                        onSelect = onSelect,
                        enabled = enabled,
                        interactive = false,
                    )
                    menuDropdown()
                }
            }
        }
        if (divider) {
            Spacer(
                Modifier
                    .padding(start = 16.dp)
                    .fillMaxWidth()
                    .height(TsuzukiSpacing.hairline)
                    .background(TsuzukiTheme.colors.separator.copy(alpha = 0.6f)),
            )
        }
    }
}
