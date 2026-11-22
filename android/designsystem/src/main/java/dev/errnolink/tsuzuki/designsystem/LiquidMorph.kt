package dev.errnolink.tsuzuki.designsystem

import android.os.Build
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.min

/* The liquid morph (iOS 26 menus; spec docs/decisions/ui-review-2026-11-15/02_IOS_REFERENCE_GUIDE.md §2.8).

   One progress value p, driven by one spring, from 0 (the trigger) to 1 (the menu). Every part of
   a frame is a pure function of p, so closing is opening played backwards and an interrupted flight
   reverses from where it is:
   - The body is the trigger's rect lerped into the menu's rect, width and height together, with the
     shared corner pinned. Its radius starts as the trigger's own, is held near "fully round" while
     the body is small (a blob), and relaxes into the menu's radius as it lands.
   - The trigger's own pixels (a snapshot, see MenuMorphSource) ride inside the body and blur out over
     the first 15 %: the button becomes the menu instead of being swapped for an empty slab.
   - The menu's content is attached to the pinned corner, scaled from 0.3, and un-blurs and fades in
     over the middle of the flight. No edge ever clip-reveals content that is already in place. */

/** Open: response 0.35 s, damping 0.8 (about 1.5 % settle, at rest by about 350 ms). */
internal val LiquidOpenSpring = spring<Float>(dampingRatio = 0.8f, stiffness = 322f)

/**
 * Close: the same path backwards, underdamped so it lands like iOS (UIKit Gallery, frame-stepped):
 * back at the button's size in ~250 ms, a few percent past it, then it springs back into the button
 * and is at rest by ~450-500 ms, so the menu settles into the button instead of stopping dead.
 */
internal val LiquidCloseSpring = spring<Float>(dampingRatio = 0.72f, stiffness = 200f)

/** How far the button squeezes per unit of undershoot (p below 0) while it lands or is pressed. */
private const val LandingSqueeze = 1.2f

/** iOS: on tap the button first shrinks a little (~18 % on a 44 pt circle in ~30 ms), then swells into the menu. */
internal const val LiquidPressDip = -0.12f

/** Heaviest blur on the arriving menu content. */
internal val LiquidContentBlur = 12.dp

/** Heaviest blur on the trigger's pixels as they leave. */
internal val LiquidTriggerBlur = 8.dp

/** One frame of the liquid, in the panel's coordinate space (panel at 0,0, size [panel]). */
data class LiquidFrame(
    val body: Rect,
    val bodyRadius: Float,
    /** Scale of the whole panel layer about the pinned corner (the landing overshoot only). */
    val layerScale: Float,
    /** Menu content: scale about [pin], offset of the pinned corner, alpha and blur fraction (0..1). */
    val pin: Offset,
    val contentScale: Float,
    val contentOffset: Offset,
    val contentAlpha: Float,
    val contentBlur: Float,
    /** The trigger's snapshot inside the body: alpha and blur fraction (0..1). */
    val triggerAlpha: Float,
    val triggerBlur: Float,
    /** The trigger's press light carried into the first frames of an opening. */
    val lit: Float,
    /** The landing squeeze of the button (below 1 while a closing liquid undershoots), about [squeezeCenter]. */
    val squeeze: Float = 1f,
    val squeezeCenter: Offset = Offset.Zero,
)

internal fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun ramp(from: Float, to: Float, x: Float): Float = ((x - from) / (to - from)).coerceIn(0f, 1f)

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Unit vector from the trigger to the panel centre; straight down when they coincide. */
fun liquidAxis(trigger: Rect, panel: Size): Offset {
    val d = Offset(panel.width / 2f, panel.height / 2f) - trigger.center
    val len = d.getDistance()
    return if (len < 1f) Offset(0f, 1f) else d / len
}

/** The trigger's own corner radius: circles and capsules are fully round on their short side. */
fun liquidTriggerRadius(trigger: Rect): Float = min(trigger.width, trigger.height) / 2f

/**
 * The frame at progress [p] (the raw spring value; it may overshoot past 1 while landing).
 * [trigger] is the trigger's rest rect in panel coordinates, [pin] the fractional corner the panel
 * shares with it, [panelRadius] the settled radius. [opening] only decides whether the press light
 * is carried into the first frames; the geometry is the same both ways.
 */
fun liquidFrame(
    p: Float,
    trigger: Rect,
    panel: Size,
    pin: Offset,
    panelRadius: Float,
    triggerRadius: Float = liquidTriggerRadius(trigger),
    opening: Boolean = true,
    bulgePx: Float = 0f,
): LiquidFrame {
    val t = p.coerceIn(0f, 1f)
    // iOS 26 menus run height ahead of width (UIKit Gallery, frame-stepped): the height leads on
    // an ease-out and overshoots mid-flight by about the same absolute amount on every menu
    // (+17 to +25 pt), the width trails. Grows away from the pinned edge.
    val h = 1f - (1f - t) * (1f - t)
    val bulge = bulgePx * 6.75f * h * h * (1f - h)
    val top = mix(trigger.top, 0f, h)
    val bottom = mix(trigger.bottom, panel.height, h)
    val squeeze = if (p < 0f) (1f + LandingSqueeze * p).coerceAtLeast(0.5f) else 1f
    val body = if (p < 0f) {
        val c = trigger.center
        Rect(
            c.x - trigger.width / 2f * squeeze,
            c.y - trigger.height / 2f * squeeze,
            c.x + trigger.width / 2f * squeeze,
            c.y + trigger.height / 2f * squeeze,
        )
    } else {
        Rect(
            left = mix(trigger.left, 0f, t),
            top = top - bulge * pin.y,
            right = mix(trigger.right, panel.width, t),
            bottom = bottom + bulge * (1f - pin.y),
        )
    }
    val round = min(body.width, body.height) / 2f
    // A drop in flight: from the button's own radius straight to fully round (an oval, then a
    // capsule), and only in the last part of the travel does it relax into the menu's corners.
    val blob = mix(triggerRadius * squeeze, round, smoothstep(0f, 0.12f, t))
    val radius = min(round, mix(blob, panelRadius, smoothstep(0.7f, 1f, t)))
    val pinned = Offset(body.left + pin.x * body.width, body.top + pin.y * body.height)
    val settledPin = Offset(pin.x * panel.width, pin.y * panel.height)
    return LiquidFrame(
        body = body,
        bodyRadius = radius,
        layerScale = if (p > 1f) p else 1f,
        pin = pin,
        // The menu is revealed at full size by the growing glass, never zoomed.
        contentScale = 1f,
        contentOffset = pinned - settledPin,
        // Opening: rows arrive blurred from ~100 ms and sharpen as the glass lands. Closing: they
        // blur and fade out in the first ~80 ms, before the glass has shrunk much.
        contentAlpha = if (opening) ramp(0.15f, 0.5f, t) else ramp(0.72f, 0.95f, t),
        contentBlur = if (opening) 1f - ramp(0.2f, 0.92f, t) else 1f - ramp(0.8f, 1f, t),
        // The button's own icon fades as it starts to grow; on the way home it re-forms blurred
        // inside the drop and is sharp just before it lands.
        triggerAlpha = if (opening) 1f - ramp(0f, 0.1f, t) else 1f - ramp(0.12f, 0.4f, t),
        triggerBlur = if (opening) ramp(0f, 0.1f, t) else ramp(0.02f, 0.3f, t),
        lit = if (opening) 1f - ramp(0f, 0.2f, t) else 0f,
        squeeze = squeeze,
        squeezeCenter = trigger.center,
    )
}

/**
 * WP4.5/MOT-10: the card variant. A card carries an editor, so on the way out of the trigger its
 * content arrives only once the container has practically landed. The drain keeps the menu ramp:
 * the late ramp on close emptied the card in its first frames and drained a dark blob into the
 * trigger (review, beta 9). Same geometry both ways.
 */
fun liquidCardFrame(
    p: Float,
    trigger: Rect,
    panel: Size,
    pin: Offset,
    panelRadius: Float,
    triggerRadius: Float = liquidTriggerRadius(trigger),
    opening: Boolean = true,
): LiquidFrame {
    val frame = liquidFrame(p, trigger, panel, pin, panelRadius, triggerRadius, opening)
    return if (opening) frame.copy(contentAlpha = ramp(0.45f, 0.85f, p.coerceIn(0f, 1f))) else frame
}

/** The liquid outline: the body's rounded rect, in panel coordinates. */
fun liquidPath(frame: LiquidFrame): Path = Path().apply {
    addRoundRect(RoundRect(frame.body, frame.bodyRadius, frame.bodyRadius))
}

/** Clip shape for one liquid frame (panel coordinates). */
internal class LiquidShape(private val frame: LiquidFrame) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(liquidPath(frame))
}

private fun GraphicsLayerScope.blurOf(px: Float) =
    if (px >= 0.5f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) BlurEffect(px, px, TileMode.Decal) else null

/** The menu content rides the pinned corner: scaled from it, faint and blurred while it arrives. */
fun GraphicsLayerScope.applyLiquidContent(frame: LiquidFrame, alphaScale: Float = 1f) {
    transformOrigin = TransformOrigin(frame.pin.x, frame.pin.y)
    scaleX = frame.contentScale
    scaleY = frame.contentScale
    translationX = frame.contentOffset.x
    translationY = frame.contentOffset.y
    alpha = alphaScale * frame.contentAlpha
    renderEffect = blurOf(frame.contentBlur * LiquidContentBlur.toPx())
}

/** The trigger's snapshot inside the body: it blurs out as the menu grows and re-forms on the way home. */
fun GraphicsLayerScope.applyLiquidTrigger(frame: LiquidFrame) {
    alpha = frame.triggerAlpha
    renderEffect = blurOf(frame.triggerBlur * LiquidTriggerBlur.toPx())
    if (size.width > 0f && size.height > 0f) {
        transformOrigin = TransformOrigin(frame.squeezeCenter.x / size.width, frame.squeezeCenter.y / size.height)
    }
    scaleX = frame.squeeze
    scaleY = frame.squeeze
}

/** Same strength as the trigger's lit press, so the handoff has no brighter frame. */
internal const val LiquidLightAlpha = 0.12f

fun DrawScope.drawLiquidLight(frame: LiquidFrame) {
    if (frame.lit > 0.01f) {
        drawRoundRect(
            color = Color.White.copy(alpha = LiquidLightAlpha * frame.lit),
            topLeft = frame.body.topLeft,
            size = frame.body.size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(frame.bodyRadius),
        )
    }
}

/** Which corner of [panel] (fractions) faces the trigger: the corner the liquid stays pinned to. */
fun liquidPinFacing(trigger: Rect, panel: Rect): Offset = Offset(
    if (trigger.center.x >= panel.center.x) 1f else 0f,
    if (trigger.center.y >= panel.center.y) 1f else 0f,
)
