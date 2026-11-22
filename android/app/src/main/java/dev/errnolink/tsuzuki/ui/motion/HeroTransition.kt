package dev.errnolink.tsuzuki.ui.motion

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

/*
 * Matched-hero zoom (iOS 18 zoom transition; outside review 2026-11-19, `docs/motion/zoom-guide.md`):
 * one object stays continuous, the cover. It flies as its own bitmap from the tile's cover rect to
 * the page's cover rect. The page is never scaled: it is laid out at full size, and a card clip
 * grows from the tile rect to the whole screen around it. The page's sections fade in at their
 * final places on staggered windows of the same progress, and leave over the first 40 % of a
 * close, so only the cover lands on the tile. One progress value drives every layer, so the
 * transition reverses from any point.
 */

/** Flight card corner radius: at least this much while the page is mid-flight. */
internal val ZoomFlightRadius = 34.dp

/** Finger travel span for dismissal, as a fraction of the page height. */
internal const val ZoomDismissSpan = 0.6f

/** Release past this much of the travel dismisses (guide: d > 0.25). */
internal const val ZoomDismissCommit = 0.25f

/** Release speed toward the edge (dp/s) that dismisses regardless of travel (guide: ~800 dp/s). */
internal const val ZoomFlickVelocityDps = 800f

/** The card centre follows the finger 1:1 across and at half speed down (guide). */
internal const val ZoomFollowX = 1f
internal const val ZoomFollowY = 0.5f

/** A plain dim behind the card, no blur (guide: black up to 25 %, 10 % while dragged). */
internal const val HeroScrimAlpha = 0.25f
internal const val ZoomCardScrimClear = 0.6f

internal val HeroElevation = 28.dp

internal const val HeroTargetWaitFrames = 2

private const val QuickBackWindowMillis = 700L

private const val QuickBackSpeed = 0.6f

/** A back press within this window of the landing leaves at this initial progress velocity. */
internal fun heroButtonBackSpeed(sinceSettledMillis: Long): Float =
    if (sinceSettledMillis in 0 until QuickBackWindowMillis) QuickBackSpeed else 0f

internal fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** A fade window: 0 until p reaches [start], 1 after [end], linear between. */
internal fun zoomFade(p: Float, start: Float, end: Float): Float = ((p - start) / (end - start)).coerceIn(0f, 1f)

/** Where a close starts fading the page: its content is gone by the first 40 % of the trip. */
internal const val ZoomCloseFadeStart = 0.6f

/** A page section's opacity: its own window while opening, everything over p 1 → 0.6 while closing. */
internal fun zoomRevealAlpha(p: Float, start: Float, end: Float, closing: Boolean): Float =
    if (closing) zoomFade(p, ZoomCloseFadeStart, 1f) else zoomFade(p, start, end)

/** Opacity for the flying cover bitmap: holds at 1 when target exists, otherwise fades in cleanly once closing shrinks past mid-flight so no phantom cover hovers over scrolled pages during predictive back. */
internal fun heroCoverAlpha(hasTarget: Boolean, progress: Float): Float =
    if (hasTarget) 1f else 1f - zoomFade(progress, 0.25f, 0.72f)

/** Shared top chrome reveal fraction: crossfades cleanly at rest/flight and fades out early while predictive-back card shrink runs (`cardT > 0`). */
internal fun heroChromeProgress(progress: Float, cardT: Float, inFlight: Boolean): Float {
    val cardFade = 1f - zoomFade(cardT, 0f, 0.35f)
    val base = if (inFlight) zoomFade(progress, ZoomReveal.Chrome.start, ZoomReveal.Chrome.end) else progress
    return (base * cardFade).coerceIn(0f, 1f)
}

/** The guide's open windows for the page's sections, and how far each rises into place. */
enum class ZoomReveal(val start: Float, val end: Float, val riseDp: Float) {
    Header(0.15f, 0.48f, 10f),
    Actions(0.22f, 0.56f, 12f),
    Body(0.28f, 0.66f, 16f),
    Chrome(0.38f, 0.80f, 0f),
}

/** The tile's caption fades out over the first 15 % of an open and back in over the last 15 % of a close. */
internal fun zoomTileLabelAlpha(p: Float): Float = 1f - zoomFade(p, 0f, 0.15f)

/** Corner radius on screen: tile → flight card → the window's own corners. */
internal fun zoomCorner(sourceRadius: Float, screenRadius: Float, flightRadius: Float, p: Float): Float {
    val flight = maxOf(screenRadius, flightRadius)
    return lerp(lerp(sourceRadius, flight, smoothstep(0f, 0.55f, p)), screenRadius, smoothstep(0.8f, 1f, p))
}

/** The scrim behind follows progress, and the predictive-back card lifts it toward 10 %. */
internal fun zoomBackdrop(p: Float, card: Float = 0f): Float =
    p.coerceIn(0f, 1f) * (1f - ZoomCardScrimClear * card.coerceIn(0f, 1f))

/** Predictive-back card (guide): shrink to 86 % about the centre, device-round by 10 % of the gesture. */
internal const val ZoomCardShrink = 0.14f

internal val ZoomCardCorner = 34.dp

/** The card scales down about its own centre as the gesture deepens. */
internal fun zoomCardScale(card: Float): Float = 1f - ZoomCardShrink * card.coerceIn(0f, 1f)

/**
 * Composes the predictive-back card onto a zoom pose: shrink about the page centre and round the
 * corners to the device radius over the first 10 % of the gesture, while the zoom progress itself
 * stays at rest.
 */
internal fun zoomCardPose(pose: ZoomPose, pageSize: Size, card: Float, cardCornerPx: Float): ZoomPose {
    val t = card.coerceIn(0f, 1f)
    if (t <= 0f) return pose
    val k = zoomCardScale(t)
    val centre = Offset(pageSize.width / 2f, pageSize.height / 2f)
    return ZoomPose(
        scale = pose.scale * k,
        translation = pose.translation + Offset(
            pose.scale * centre.x * (1f - k),
            pose.scale * centre.y * (1f - k),
        ),
        clip = pose.clip,
        corner = lerp(pose.corner, maxOf(pose.corner, cardCornerPx), zoomFade(t, 0f, 0.1f)),
    )
}

/** Finger travel down the page (px) as dismissal travel 0..1. */
internal fun zoomDismissTravel(dragY: Float, pageHeightPx: Float): Float =
    if (pageHeightPx <= 0f) 0f else (dragY / (pageHeightPx * ZoomDismissSpan)).coerceIn(0f, 1f)

/** Release decision: past 25 % of the travel, or a flick toward the edge; an upward-moving finger never commits on its own. */
internal fun zoomDismissCommits(travel: Float, velocityDps: Float): Boolean =
    travel >= ZoomDismissCommit || velocityDps >= ZoomFlickVelocityDps

/** Finger velocity (px/s) as zoom-progress velocity, so the close spring carries the release speed. */
internal fun zoomReleaseVelocity(velocityPxPerSec: Float, distancePx: Float): Float =
    if (distancePx <= 0f) 0f else -(velocityPxPerSec / distancePx)

/** One frame of the zoom: where the page layer sits, what the clip shows, how round the corners are. */
internal class ZoomPose(
    val scale: Float,
    val translation: Offset,
    val clip: Rect,
    val corner: Float,
) {
    fun map(point: Offset): Offset = translation + point * scale

    fun mapRect(rect: Rect): Rect = Rect(map(rect.topLeft), map(rect.bottomRight))

    /** The visible region on screen (clip through the transform). */
    val bounds: Rect
        get() = mapRect(clip)
}

private fun lerpRect(a: Rect, b: Rect, t: Float): Rect =
    Rect(lerp(a.left, b.left, t), lerp(a.top, b.top, t), lerp(a.right, b.right, t), lerp(a.bottom, b.bottom, t))

/**
 * The card at progress [p]: the page stays unscaled at its place (only the finger [follow] moves
 * it) and its clip grows from the tile's rect [source] to the whole page.
 */
internal fun zoomPose(
    source: Rect,
    pageSize: Size,
    p: Float,
    sourceRadius: Float,
    screenRadius: Float,
    flightRadius: Float,
    follow: Offset = Offset.Zero,
): ZoomPose = ZoomPose(
    scale = 1f,
    translation = follow,
    clip = lerpRect(source.translate(-follow), Rect(0f, 0f, pageSize.width, pageSize.height), p.coerceIn(0f, 1f)),
    corner = zoomCorner(sourceRadius, screenRadius, flightRadius, p),
)

/** The flying cover in page coordinates: the tile's cover rect to the page's cover rect. */
internal fun zoomCoverRect(source: Rect, target: Rect, p: Float, follow: Offset = Offset.Zero): Rect =
    lerpRect(source.translate(-follow), target, p.coerceIn(0f, 1f))

/** N11: the ghost card's floor — the page shrinks to this fraction of itself toward the centre. */
internal const val ZoomGhostShrink = 0.8f

/**
 * N11: the pose when the origin tile is gone (scrolled away, filtered out) — the page leaves as a
 * card shrinking and fading toward the screen centre, never a slide. The centre stays put; the
 * gesture card ([zoomCardPose]) composes on top for predictive back. Pure.
 */
internal fun zoomGhostPose(p: Float, pageSize: Size, cardCornerPx: Float): ZoomPose {
    val q = p.coerceIn(0f, 1f)
    val scale = lerp(ZoomGhostShrink, 1f, q)
    val centre = Offset(pageSize.width / 2f, pageSize.height / 2f)
    return ZoomPose(
        scale,
        Offset(centre.x * (1f - scale), centre.y * (1f - scale)),
        Rect(0f, 0f, pageSize.width, pageSize.height),
        lerp(cardCornerPx, 0f, q),
    )
}

/** N11: the ghost fades as it shrinks and is gone well before the flight ends. Pure. */
internal fun zoomGhostAlpha(p: Float): Float = smoothstep(0.12f, 0.45f, p.coerceIn(0f, 1f))

/** Centre-crops a cover bitmap into [rect] with rounded [corner], both in the current draw space. */
internal fun DrawScope.drawCoverBitmap(image: ImageBitmap, rect: Rect, corner: Float, alpha: Float) {
    if (alpha <= 0f || rect.width <= 0f || rect.height <= 0f) return
    val srcW = image.width.toFloat()
    val srcH = image.height.toFloat()
    val aspect = rect.width / rect.height
    val cropW: Float
    val cropH: Float
    if (srcW / srcH > aspect) {
        cropH = srcH
        cropW = srcH * aspect
    } else {
        cropW = srcW
        cropH = srcW / aspect
    }
    val path = Path().apply { addRoundRect(RoundRect(rect, CornerRadius(corner.coerceAtLeast(0f)))) }
    clipPath(path) {
        drawImage(
            image = image,
            srcOffset = IntOffset(((srcW - cropW) / 2f).toInt(), ((srcH - cropH) / 2f).toInt()),
            srcSize = IntSize(cropW.toInt(), cropH.toInt()),
            dstOffset = IntOffset(rect.left.toInt(), rect.top.toInt()),
            dstSize = IntSize(rect.width.toInt(), rect.height.toInt()),
            filterQuality = FilterQuality.Medium,
            alpha = alpha,
        )
    }
}

internal data class HeroClipShape(val rect: Rect, val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(
            RoundRect(rect, CornerRadius(radius.coerceAtLeast(0f))),
        )
}
