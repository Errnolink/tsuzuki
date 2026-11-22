package dev.errnolink.tsuzuki.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/* Menu placement, ported from Kasane v1.8.3 (kasane/src/js/ui.js placeOnTrigger): the menu covers its
   trigger and grows toward open space. The motion itself is the liquid in MenuLiquid.kt. */

/** Vertical side the menu grows toward. Down grows below the trigger's top edge. */
enum class MenuGrow { Down, Up }

/** Horizontal edge of the trigger the menu keeps. */
enum class MenuAlign { Start, Center, End }

/** Fraction of the content to pin the items to, so they stay glued to the grow corner. */
fun MenuAlign.pinFractionX(): Float = when (this) {
    MenuAlign.Start -> 0f
    MenuAlign.Center -> 0.5f
    MenuAlign.End -> 1f
}

fun MenuGrow.pinFractionY(): Float = when (this) {
    MenuGrow.Down -> 0f
    MenuGrow.Up -> 1f
}

/** Where a morphing menu goes: it covers its trigger and grows toward open space. */
data class MenuMorphPlacement(
    val offset: IntOffset,
    val grow: MenuGrow,
    val align: MenuAlign,
    /** Height cap when the menu is taller than the reasoned bounds (scroll inside); else [Int.MAX_VALUE]. */
    val maxHeightPx: Int,
)

/**
 * Kasane's placeOnTrigger: the menu covers the trigger and keeps the trigger's corner that faces
 * the nearest screen corner, so it grows toward open space. Vertical: grow down when it fits,
 * up when only that way fits. Horizontal:
 * left third keeps its left edge, right third its right edge, middle third centres on it.
 * Clamped to [marginPx], but a menu may sit as close to an edge as its trigger does. A menu
 * taller than the bounds is capped to them (the caller scrolls inside).
 *
 * @param anchor trigger rect, in [bounds] coordinates.
 * @param bounds the area the menu reasons about (a pane; usually the window itself).
 * @param viewport the viewport the edge margins clamp against.
 */
fun computeMorphPlacement(
    anchor: IntRect,
    bounds: IntRect,
    viewport: IntSize,
    popupSize: IntSize,
    marginPx: Int = 8,
    grow: MenuGrow? = null,
    align: MenuAlign? = null,
    /** iOS 26: the menu sticks out this far past the trigger's outer edge. */
    outsetPx: Int = 0,
): MenuMorphPlacement {
    // Keep a margin from the edges, but a menu may sit as close to an edge as its trigger does.
    val topMin = minOf(maxOf(marginPx, bounds.top + marginPx), anchor.top)
    val bottomMax = maxOf(minOf(viewport.height - marginPx, bounds.bottom - marginPx), anchor.bottom)
    val leftMin = minOf(maxOf(marginPx, bounds.left + marginPx), anchor.left)
    val rightMax = maxOf(minOf(viewport.width - marginPx, bounds.right - marginPx), anchor.right)

    val cx = anchor.center.x
    val cy = anchor.center.y
    val fx = (cx - bounds.left).toFloat() / bounds.width.coerceAtLeast(1)
    val fy = (cy - bounds.top).toFloat() / bounds.height.coerceAtLeast(1)

    // Taller than the reasoned bounds: cap the height (scroll inside the glass).
    val boundsHeight = (bottomMax - topMin).coerceAtLeast(0)
    val maxHeight = if (popupSize.height > boundsHeight) boundsHeight else Int.MAX_VALUE
    val placedHeight = minOf(popupSize.height, boundsHeight)

    // Vertical: grow down when the menu fits below the trigger's top; otherwise up, if it fits that way.
    var growV = grow ?: MenuGrow.Down
    if (grow == null && anchor.top + placedHeight > bottomMax && anchor.bottom - placedHeight >= topMin) {
        growV = MenuGrow.Up
    }
    if (growV == MenuGrow.Up && anchor.bottom - placedHeight < topMin && anchor.top + placedHeight <= bottomMax) {
        growV = MenuGrow.Down
    }

    // Horizontal: keep the outer edge; triggers in the middle third grow from their centre.
    val alignV = align ?: when {
        fx < 1f / 3f -> MenuAlign.Start
        fx > 2f / 3f -> MenuAlign.End
        else -> MenuAlign.Center
    }

    val left = when (alignV) {
        MenuAlign.Start -> anchor.left - outsetPx
        MenuAlign.End -> anchor.right + outsetPx - popupSize.width
        MenuAlign.Center -> cx - popupSize.width / 2
    }
    val top = if (growV == MenuGrow.Down) anchor.top else anchor.bottom - placedHeight
    val clampedLeft = maxOf(leftMin, minOf(left, rightMax - popupSize.width))
    val clampedTop = maxOf(topMin, minOf(top, bottomMax - placedHeight))
    return MenuMorphPlacement(
        offset = IntOffset(clampedLeft, clampedTop),
        grow = growV,
        align = alignV,
        maxHeightPx = maxHeight,
    )
}


/**
 * The morph source: the trigger a menu pours out of. Pressed, it lights up a little; while its menu
 * is open it is hidden and the liquid carries a snapshot of its pixels ([snapshot]), so the button
 * itself becomes the menu and, on the way home, re-forms inside the shrinking glass. When the liquid
 * lands it is swapped back for the real trigger (same pixels), which wobbles once on the side the
 * liquid arrived from. Wrap the trigger (a Box around the button the menu anchors to):
 * `Box(Modifier.menuMorphSource(source)) { TriggerButton(); PullDownMenu(…, morphSource = source) }`.
 */
@Stable
class MenuMorphSource(val borderless: Boolean = false) {
    internal var covered by mutableStateOf(false)
    internal var reformTick by mutableIntStateOf(0)

    /** The press swell (0 at rest, 1 held): the trigger grows and brightens, and the drop starts from it. */
    internal val press = Animatable(0f, visibilityThreshold = 0.001f)

    /** Uptime of the last touch-down, so a menu opened on that press waits out the rest of the swell. */
    internal var pressedAtMillis = 0L
    /** Unit travel vector of the last morph (trigger to menu), for the wobble. */
    internal var axis by mutableStateOf(Offset(0f, 1f))

    /** The trigger's last drawn pixels, replayed inside the liquid while it is hidden. */
    internal var snapshot: GraphicsLayer? by mutableStateOf(null)

    /** The trigger's rest bounds on screen (for liquid cards that live in their own window). */
    var boundsOnScreen by mutableStateOf(Rect.Zero)
        internal set

    /**
     * While a menu of this trigger is open: its drag-select session. A trigger that opened the menu
     * on a press-and-hold forwards the still-held finger to it (see [menuScrubLauncher]); the menu
     * owns the lifecycle. Not for call sites.
     */
    internal var dragSession: MenuDragSession? = null

    /**
     * The trigger's rest bounds in window coordinates. A menu opened from a button inside a
     * multi-action capsule pours from the whole capsule, so its popup anchors here (the capsule),
     * not on the button it is composed in.
     */
    var anchorBoundsInWindow by mutableStateOf(Rect.Zero)
        internal set

    /** Called by the morphing menu; not for call sites. */
    internal fun hide() {
        covered = true
    }

    /** Called by the morphing menu when the liquid has landed on the trigger; not for call sites. */
    internal fun reveal(wobble: Boolean = true) {
        covered = false
        if (wobble) reformTick++
    }
}

@Composable
fun rememberMenuMorphSource(borderless: Boolean = false): MenuMorphSource =
    remember(borderless) { MenuMorphSource(borderless) }

/**
 * The trigger a menu inside it pours from. A trigger provides its source to its content, so a menu
 * declared anywhere inside the trigger's content morphs from that trigger without wiring.
 */
val LocalMenuMorphSource = staticCompositionLocalOf<MenuMorphSource?> { null }

/** Wobble after the merge: a small bulge on the arriving side that rings out quickly. */
private const val WobbleBulge = 0.05f

fun Modifier.menuMorphSource(source: MenuMorphSource): Modifier = composed {
    val motion = TsuzukiTheme.motion
    val wobble = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val snapshot = rememberGraphicsLayer()
    DisposableEffect(source, snapshot) {
        source.snapshot = snapshot
        onDispose { if (source.snapshot === snapshot) source.snapshot = null }
    }
    LaunchedEffect(source.reformTick, motion.reduced) {
        if (source.reformTick == 0 || motion.reduced) return@LaunchedEffect
        wobble.snapTo(1f)
        wobble.animateTo(0f, KasaneSprings.wobble<Float>())
    }

    this
        .onGloballyPositioned {
            val p = it.positionOnScreen()
            source.boundsOnScreen = Rect(p.x, p.y, p.x + it.size.width, p.y + it.size.height)
            source.anchorBoundsInWindow = it.boundsInWindow()
        }
        .pointerInput(source, motion.reduced) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                source.pressedAtMillis = android.os.SystemClock.uptimeMillis()
                if (!motion.reduced) scope.launch { source.press.animateTo(1f, MenuLiquidMotion.PressSpring) }
                waitForUpOrCancellation(pass = PointerEventPass.Initial)
                scope.launch { source.press.animateTo(0f, MenuLiquidMotion.PressSpring) }
            }
        }
        .graphicsLayer {
            if (source.covered) {
                alpha = 0f
                return@graphicsLayer
            }
            val grow = MenuLiquidMotion.PressGrow.toPx() * source.press.value
            val b = WobbleBulge * wobble.value
            if (size.width > 0f && size.height > 0f) {
                scaleX = (size.width + grow) / size.width + b * abs(source.axis.x)
                scaleY = (size.height + grow) / size.height + b * abs(source.axis.y)
            }
            translationX = source.axis.x * b * size.minDimension * 0.25f
            translationY = source.axis.y * b * size.minDimension * 0.25f
        }
        .drawWithContent {
            snapshot.record { this@drawWithContent.drawContent() }
            drawLayer(snapshot)
            if (!source.borderless) {
                val light = MenuLiquidMotion.PressBrighten * source.press.value
                if (light > 0.005f) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = light),
                        cornerRadius = CornerRadius(size.minDimension / 2f),
                    )
                }
            }
        }
}
