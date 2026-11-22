package dev.errnolink.tsuzuki.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.E
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tanh

/* The iOS 26 button-to-menu morph (uikit.even.how, frame-tracked at 60 fps and fitted with springs).
   Two glass boxes merged by a smooth union: A is the button capsule, B the drop. Pressed, the
   button swells; the drop leaves its centre, sags below where it belongs and is pulled back up
   while it grows into the menu; the button shrinks and is drawn into the drop, so while the menu
   is open there is no button, the menu is the button. Closing plays the same parts on their own
   springs: the rows fade first, the drop shrinks home with a second sag, and the button regrows
   under it and swallows it through the neck. */

/** A UIKit spring: damping ratio and response (period of the undamped oscillation, seconds). */
internal fun iosSpring(dampingRatio: Float, response: Float, threshold: Float = 0.001f): AnimationSpec<Float> {
    val omega = 2f * PI.toFloat() / response
    return spring(dampingRatio = dampingRatio, stiffness = omega * omega, visibilityThreshold = threshold)
}

internal object MenuLiquidMotion {
    val PressSpring = iosSpring(0.7f, 0.25f)
    val PressGrow = 6.dp
    const val PressLabelScale = 1.06f
    const val PressBrighten = 0.10f

    /** The drop leaves the button this long after touch-down, so the swell shows first. */
    const val PressLeadMillis = 70L
    const val LabelOutMillis = 90

    val Morph = iosSpring(0.78f, 0.32f)
    const val MorphInitialVelocity = 5f

    /** Critically damped sag: x(t) = v·t·e^(−ωt), peak [DropAmount] of the menu height. */
    const val DropResponse = 0.44f
    val Drop = iosSpring(1f, DropResponse, threshold = 0.5f)
    const val DropAmount = 0.16f
    const val CloseDropAmount = 0.14f
    /** The close sag rings out faster than the open one, so the drop is home in ~250 ms like iOS. */
    const val CloseDropResponse = 0.30f
    val CloseDrop = iosSpring(1f, CloseDropResponse, threshold = 0.5f)

    val Corner = iosSpring(1f, 0.36f)
    val ButtonCollapse = iosSpring(1f, 0.22f)
    val ContentIn = iosSpring(1f, 0.30f)
    const val ContentAlphaRamp = 0.18f
    val ContentMaxBlur = 10.dp
    const val MinContentScale = 0.5f
    val LabelMaxBlur = 8.dp

    val CloseMorph = iosSpring(0.8f, 0.34f)
    const val CloseMorphInitialVelocity = 0.35f
    const val ContentOutMillis = 90
    const val CornerOutMillis = 180
    const val ButtonRegrowDelayMillis = 120L
    val ButtonRegrow = iosSpring(0.72f, 0.30f)
    const val LabelInDelayMillis = 110L
    const val LabelInMillis = 130

    /** The close counts as landed once the drop is this close to the button and the icon is back. */
    const val LandMorph = 0.03f
    const val LandLabel = 0.95f
    val LandDrop = 3.dp
    /** On landing the real trigger shows again and the menu's glass dissolves over it. */
    const val LandFadeMillis = 90

    /** Smooth-union width where the drop and the button touch: the liquid neck. */
    val MergeSmoothness = 14.dp

    /** Rubber band toward a finger dragged outside the open menu. */
    val PullMax = 14.dp
    const val PullStrength = 0.35f
    val PullRelease = spring(dampingRatio = 0.6f, stiffness = (2f * PI.toFloat() / 0.35f).let { it * it }, visibilityThreshold = Offset(0.5f, 0.5f))

    /** Row highlight: cross-fades in fast and out slow, it never slides. */
    const val HighlightInMillis = 60
    const val HighlightOutMillis = 160

    val SubmenuOpen = iosSpring(0.86f, 0.30f)
    val SubmenuClose = iosSpring(1f, 0.24f)
    const val SubmenuHoverOpenMillis = 450L

    fun dropKick(peakPx: Float, response: Float = DropResponse): Float {
        val omega = 2f * PI.toFloat() / response
        return peakPx * omega * E.toFloat()
    }
}

/** One frame of the morph, in the panel's coordinates (the settled menu at 0,0 sized [Size]). */
internal data class MenuLiquidFrame(
    val glass: LiquidGlassGeometry,
    val blob: GlassBox,
    val contentScale: Float,
    val contentOffset: Offset,
    val contentAlpha: Float,
    val contentBlur: Float,
    val labelAlpha: Float,
    val labelBlur: Float,
    val labelScale: Float,
    /** Morph overshoot past the menu, applied to the whole panel layer about the pinned corner. */
    val layerScale: Float,
    val brighten: Float,
    /** Opacity of the menu's glass container; fades to 0 on borderless icon close so no outline pops off. */
    val glassAlpha: Float = 1f,
)

private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun smoothStep(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * The frame for the animated values. [trigger] is the button's rest rect and [panel] the settled
 * menu, both in panel coordinates; [press] is the button's press swell (0..1).
 */
internal fun menuLiquidFrame(
    trigger: Rect,
    panel: Size,
    menuRadius: Float,
    morph: Float,
    drop: Float,
    corner: Float,
    presence: Float,
    absorb: Float,
    content: Float,
    label: Float,
    press: Float,
    pressGrowPx: Float,
    mergePx: Float,
    borderless: Boolean = false,
    closing: Boolean = false,
): MenuLiquidFrame {
    val m = morph.coerceAtMost(1f)
    val shrinkFloor = if (borderless && closing) 0.55f else 1f
    val seed = min(trigger.width, trigger.height) * shrinkFloor
    val w = max(lerpF(seed, panel.width, m), 0f)
    val h = max(lerpF(seed, panel.height, m), 0f)
    val cx = lerpF(trigger.center.x, panel.width / 2f, m)
    val cy = lerpF(trigger.center.y, panel.height / 2f, m) + drop
    val round = min(w, h) / 2f
    val blob = GlassBox(cx, cy, w / 2f, h / 2f, lerpF(round, menuRadius, corner).coerceIn(0f, round))

    val grow = pressGrowPx * press
    val ahw = (trigger.width + grow) / 2f * presence
    val ahh = (trigger.height + grow) / 2f * presence
    val button = GlassBox(
        lerpF(trigger.center.x, blob.cx, absorb),
        lerpF(trigger.center.y, blob.cy, absorb),
        ahw,
        ahh,
        min(ahw, ahh),
    )
    val pw = panel.width.coerceAtLeast(1f)
    val ph = panel.height.coerceAtLeast(1f)
    val scale = max(w / pw, h / ph).coerceIn(MenuLiquidMotion.MinContentScale, 1.2f)
    val glassAlpha = if (borderless && closing) smoothStep((m - 0.05f) / 0.43f) else 1f
    return MenuLiquidFrame(
        glass = LiquidGlassGeometry(button, blob, liquidMergeFor(button, blob, mergePx)),
        blob = blob,
        contentScale = scale,
        contentOffset = Offset(cx - panel.width / 2f, cy - panel.height / 2f),
        contentAlpha = smoothStep(content / MenuLiquidMotion.ContentAlphaRamp),
        contentBlur = (1f - content).coerceIn(0f, 1f),
        labelAlpha = label.coerceIn(0f, 1f),
        labelBlur = (1f - label).coerceIn(0f, 1f),
        labelScale = lerpF(1f, MenuLiquidMotion.PressLabelScale, press),
        layerScale = if (morph > 1f) morph else 1f,
        brighten = MenuLiquidMotion.PressBrighten * press,
        glassAlpha = glassAlpha,
    )
}

/**
 * Rubber band toward a finger outside [panel]: per axis, how far past the edge the finger is,
 * eased by tanh and capped at [maxPx]. Zero while the finger is over the menu.
 */
fun menuPullOffset(pointer: Offset, panel: Rect, maxPx: Float, strength: Float = MenuLiquidMotion.PullStrength): Offset {
    if (panel.width <= 0f || panel.height <= 0f || maxPx <= 0f) return Offset.Zero
    val dx = pointer.x - pointer.x.coerceIn(panel.left, panel.right)
    val dy = pointer.y - pointer.y.coerceIn(panel.top, panel.bottom)
    val k = strength / maxPx
    return Offset(maxPx * tanh(dx * k), maxPx * tanh(dy * k))
}

/** Every animated value of one menu's morph, read only in draw and layer blocks. */
@Stable
internal class MenuLiquid(private val scope: CoroutineScope) {
    val morph = Animatable(0f, visibilityThreshold = 0.001f)
    val drop = Animatable(0f, visibilityThreshold = 0.5f)
    val corner = Animatable(0f, visibilityThreshold = 0.001f)
    val presence = Animatable(1f, visibilityThreshold = 0.001f)
    val absorb = Animatable(0f, visibilityThreshold = 0.001f)
    val content = Animatable(0f, visibilityThreshold = 0.001f)
    val label = Animatable(1f, visibilityThreshold = 0.001f)
    /** The whole menu layer's opacity: 1 except in the hand-off to the real trigger after a close. */
    val fade = Animatable(1f, visibilityThreshold = 0.001f)
    val pull = Animatable(Offset.Zero, Offset.VectorConverter, visibilityThreshold = Offset(0.5f, 0.5f))

    private var job: Job? = null

    /** True once a pour has started and until the drop has landed back in the button. */
    val inFlight: Boolean get() = morph.value > 0f || presence.value < 1f

    /**
     * Pours the menu out of the button. From rest every value starts at the button; an open during
     * a close keeps the running velocities. [onStart] runs in the first frame of the liquid.
     */
    fun open(fromRest: Boolean, leadMillis: Long, sagPx: Float, onStart: () -> Unit) {
        val vMorph = morph.velocity
        val vDrop = drop.velocity
        job?.cancel()
        job = scope.launch {
            fade.snapTo(1f)
            if (fromRest) {
                morph.snapTo(0f)
                drop.snapTo(0f)
                corner.snapTo(0f)
                content.snapTo(0f)
                presence.snapTo(1f)
                absorb.snapTo(0f)
                label.snapTo(1f)
                pull.snapTo(Offset.Zero)
            }
            onStart()
            launch { label.animateTo(0f, tween(MenuLiquidMotion.LabelOutMillis)) }
            if (fromRest && leadMillis > 0L) delay(leadMillis)
            launch { presence.animateTo(0f, MenuLiquidMotion.ButtonCollapse) }
            launch { absorb.animateTo(1f, MenuLiquidMotion.ButtonCollapse) }
            launch {
                morph.animateTo(
                    1f,
                    MenuLiquidMotion.Morph,
                    initialVelocity = if (fromRest) MenuLiquidMotion.MorphInitialVelocity else vMorph,
                )
            }
            launch {
                drop.animateTo(
                    0f,
                    MenuLiquidMotion.Drop,
                    initialVelocity = if (fromRest) MenuLiquidMotion.dropKick(MenuLiquidMotion.DropAmount * sagPx) else vDrop,
                )
            }
            launch { corner.animateTo(1f, MenuLiquidMotion.Corner) }
            launch { content.animateTo(1f, MenuLiquidMotion.ContentIn) }
        }
    }

    /**
     * Pours the menu back into the button. Once the drop is home and the icon is back, [onReveal]
     * shows the real trigger under the menu, the menu dissolves over it, and [onLanded] unmounts
     * it. The springs' settle tails are cut there: they only move pixels the fade already hides.
     */
    fun close(
        sagPx: Float,
        landDropPx: Float,
        onReveal: () -> Unit,
        onLanded: () -> Unit,
        borderless: Boolean = false,
    ) {
        val vMorph = morph.velocity
        val vDrop = drop.velocity
        job?.cancel()
        job = scope.launch {
            val springs = launch {
                launch { pull.animateTo(Offset.Zero, MenuLiquidMotion.PullRelease) }
                launch {
                    content.animateTo(0f, tween(MenuLiquidMotion.ContentOutMillis, easing = FastOutLinearInEasing))
                }
                launch { corner.animateTo(0f, tween(MenuLiquidMotion.CornerOutMillis)) }
                launch {
                    morph.animateTo(0f, MenuLiquidMotion.CloseMorph, initialVelocity = vMorph - MenuLiquidMotion.CloseMorphInitialVelocity)
                }
                launch {
                    drop.animateTo(
                        0f,
                        MenuLiquidMotion.CloseDrop,
                        initialVelocity = vDrop + MenuLiquidMotion.dropKick(MenuLiquidMotion.CloseDropAmount * sagPx, MenuLiquidMotion.CloseDropResponse),
                    )
                }
                if (!borderless) {
                    launch {
                        delay(MenuLiquidMotion.ButtonRegrowDelayMillis)
                        launch { absorb.animateTo(0f, MenuLiquidMotion.ButtonRegrow) }
                        presence.animateTo(1f, MenuLiquidMotion.ButtonRegrow)
                    }
                } else {
                    launch {
                        launch { absorb.animateTo(1f, MenuLiquidMotion.ButtonCollapse) }
                        presence.animateTo(0f, MenuLiquidMotion.ButtonCollapse)
                    }
                }
                launch {
                    delay(if (borderless) 60L else MenuLiquidMotion.LabelInDelayMillis)
                    label.animateTo(1f, tween(if (borderless) 140 else MenuLiquidMotion.LabelInMillis))
                }
            }
            val landThreshold = if (borderless) 0.06f else MenuLiquidMotion.LandMorph
            snapshotFlow {
                morph.value <= landThreshold &&
                    label.value >= MenuLiquidMotion.LandLabel &&
                    abs(drop.value) <= landDropPx
            }.first { it }
            onReveal()
            fade.animateTo(0f, tween(if (borderless) 45 else MenuLiquidMotion.LandFadeMillis))
            springs.cancel()
            morph.snapTo(0f)
            drop.snapTo(0f)
            corner.snapTo(0f)
            content.snapTo(0f)
            presence.snapTo(1f)
            absorb.snapTo(0f)
            label.snapTo(1f)
            pull.snapTo(Offset.Zero)
            onLanded()
        }
    }

    /** The menu closed before its drop left the button: stop, everything back at the button. */
    fun cancelPour() {
        job?.cancel()
        job = scope.launch {
            fade.snapTo(1f)
            morph.snapTo(0f)
            drop.snapTo(0f)
            corner.snapTo(0f)
            content.snapTo(0f)
            presence.snapTo(1f)
            absorb.snapTo(0f)
            label.snapTo(1f)
            pull.snapTo(Offset.Zero)
        }
    }

    /** The finger dragged outside the open menu: follow it at once. Zero springs back. */
    fun pullTo(target: Offset) {
        scope.launch {
            if (target == Offset.Zero) {
                pull.animateTo(Offset.Zero, MenuLiquidMotion.PullRelease)
            } else if (morph.value >= 0.8f) {
                pull.snapTo(target)
            }
        }
    }
}
