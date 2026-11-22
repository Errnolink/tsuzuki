package dev.errnolink.tsuzuki.designsystem

import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.effect
import com.kyant.backdrop.effects.lens
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.HazeInputScale
import androidx.compose.ui.graphics.graphicsLayer

val LocalInsideGlass = staticCompositionLocalOf { false }
val LocalNavigationMotion = compositionLocalOf { false }

/**
 * The glass backdrop of the screen a composable lives on. Every [GlassSurface] with a context
 * provides it to its content, and screens that own a glass source provide it to theirs, so a menu
 * (its own popup window) blurs the same backdrop as the control it opens from.
 */
val LocalGlassContext = compositionLocalOf<GlassContext?> { null }

/**
 * Fallback alpha for glass with no live backdrop. The reader sets it: its pages are a native View
 * that Compose cannot sample, so its chrome is clear glass (tint + rim, the page showing through).
 */
val LocalGlassFallbackAlpha = staticCompositionLocalOf<Float?> { null }

@Stable
class GlassContext internal constructor(
    internal val haze: HazeState,
    internal val backdrop: LayerBackdrop?,
    internal val enabled: Boolean,
    internal val foldDistancePx: Float = 24f,
    internal val unfoldDistancePx: Float = 14f,
    /** Hero surfaces draw through [backdrop] only when the context asked for the hero lens. */
    internal val heroLens: Boolean = false,
    /** The view whose window records [backdrop]; glass in another window (popups) cannot read it. */
    internal val sourceView: android.view.View? = null,
) {
    var navigationMinimized by mutableStateOf(false)
    var isScrolling by mutableStateOf(false)
    var scrollDeltaListener: ((Float) -> Unit)? = null

    /** A top search field is open: the bar holds its expanded shape while it lasts (Kasane `is-searching`). */
    var searchActive by mutableStateOf(false)

    private val watcher = ScrollDirectionWatcher(foldDistancePx, unfoldDistancePx)

    val liquidBackdrop: LayerBackdrop? get() = backdrop

    fun expandNavigation() {
        navigationMinimized = false
        watcher.reset()
        pendingFoldDirection = 0
        morphLockUntilMillis = SystemClock.uptimeMillis() + MorphLockMillis
    }

    private var lastScrollMillis = 0L
    private var morphLockUntilMillis = 0L
    private var pendingFoldDirection = 0

    internal val navigationScroll = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput || source == NestedScrollSource.SideEffect) {
                isScrolling = true
            }
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            val user = source == NestedScrollSource.UserInput
            // Compose reports reading down (finger up, toward the content's end) as a NEGATIVE
            // consumed.y; the watcher wants positive = travel down the content. Normalized once,
            // here at the boundary (NAV-03). Flings and programmatic scrolls (SideEffect) feed
            // the watcher too, so the fold follows the list, not only the finger.
            val travelDownPx = scrollTravelDownPx(consumed.y)
            if (user || source == NestedScrollSource.SideEffect) {
                scrollDeltaListener?.invoke(travelDownPx)
                val now = SystemClock.uptimeMillis()
                val elapsed = now - lastScrollMillis
                lastScrollMillis = now
                // Kasane's watchDirection: 24 dp of continuous down-travel folds, 14 dp up or
                // reaching the top unfolds, a reversal restarts the count, a new gesture after
                // a long gap does too. At-top comes from the unconsumed remainder, so a fling
                // or programmatic scroll that arrives at the top reports it as well.
                val atTop = listAtTop(available.y)
                if (elapsed > GestureGapMillis) watcher.reset()
                val decision = navigationFoldFrame(
                    flipped = watcher.update(if (atTop) 0f else travelDownPx, atTop),
                    watcherDirection = watcher.direction,
                    pendingDirection = pendingFoldDirection,
                    lockExpired = now >= morphLockUntilMillis,
                )
                if (decision != null) {
                    pendingFoldDirection = decision.pendingDirection
                    if (decision.applyDirection != 0) applyFold(decision.applyDirection, now)
                }
            }
            if (consumed == Offset.Zero && available == Offset.Zero) {
                isScrolling = false
            }
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: androidx.compose.ui.unit.Velocity, available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
            isScrolling = false
            return super.onPostFling(consumed, available)
        }
    }

    private fun applyFold(direction: Int, now: Long) {
        val fold = direction == 1
        if (fold && !navigationMinimized && !searchActive) {
            navigationMinimized = true
            morphLockUntilMillis = now + MorphLockMillis
        } else if (!fold && navigationMinimized) {
            navigationMinimized = false
            morphLockUntilMillis = now + MorphLockMillis
        }
    }

    private companion object {
        const val GestureGapMillis = 1500L
        val MorphLockMillis = M3Motion.Medium1 + 50L
    }
}

/**
 * Kasane's directional scroll accumulator (kasane/src/js/components.js `watchDirection`): a few
 * tens of points of continuous travel in one direction flips the fold state, a reversal resets
 * the count, and the top of the list always means "expand". Pure; behaviour-tested.
 *
 * @param downPx continuous down-travel that folds the bar.
 * @param upPx continuous up-travel that unfolds it.
 */
class ScrollDirectionWatcher(private val downPx: Float, private val upPx: Float) {
    /** -1 = travel up (unfold), +1 = travel down (fold), 0 = no direction yet. */
    var direction: Int = 0
        private set

    fun reset() {
        direction = 0
        acc = 0f
    }

    /**
     * Feed one scroll frame: [dyPx] is the consumed delta (positive = scrolling down); [atTop]
     * means the list cannot scroll up any further. Returns true when the direction flipped.
     */
    fun update(dyPx: Float, atTop: Boolean): Boolean {
        if (atTop) {
            acc = 0f
            return flip(-1)
        }
        if (dyPx * acc < 0f) acc = 0f
        acc += dyPx
        return when {
            acc > downPx -> flip(1)
            acc < -upPx -> flip(-1)
            else -> false
        }
    }

    private var acc = 0f

    private fun flip(to: Int): Boolean {
        if (direction == to) return false
        direction = to
        return true
    }
}

/**
 * Compose's nested-scroll y at the watcher's boundary (NAV-03): reading down the list (finger
 * up, `consumed.y` negative in Compose) becomes positive down-the-content travel, which is the
 * watcher's contract ("positive = scrolling down"). Pure; behaviour-tested in NavigationFoldTest.
 */
fun scrollTravelDownPx(consumedY: Float): Float = -consumedY

/**
 * At-top from the list's own unconsumed remainder (NAV-03): leftover finger-down travel means the
 * list cannot scroll any further toward its start — a drag, a fling or a programmatic scroll all
 * report it, so the bar unfolds on every arrival at the top, not only on a user drag.
 */
fun listAtTop(availableY: Float): Boolean = availableY > 0f

/** One scroll frame's fold decision: the direction to apply now (0 = none) and the flip a live
 *  morph lock swallowed, retried while the direction holds (NAV-03). Pure; behaviour-tested. */
data class NavigationFoldDecision(val applyDirection: Int, val pendingDirection: Int)

fun navigationFoldFrame(
    flipped: Boolean,
    watcherDirection: Int,
    pendingDirection: Int,
    lockExpired: Boolean,
): NavigationFoldDecision? = when {
    flipped && lockExpired -> NavigationFoldDecision(watcherDirection, 0)
    flipped -> NavigationFoldDecision(0, watcherDirection)
    pendingDirection != 0 && watcherDirection != pendingDirection -> NavigationFoldDecision(0, 0)
    pendingDirection != 0 && lockExpired -> NavigationFoldDecision(pendingDirection, 0)
    else -> null
}

fun Modifier.floatingNavigationScroll(context: GlassContext): Modifier = nestedScroll(context.navigationScroll)

@Composable
fun rememberGlassContext(enableHeroLens: Boolean = false): GlassContext {
    val enabled = !LocalOpaqueGlass.current && Build.VERSION.SDK_INT >= 31
    val haze = rememberHazeState(blurEnabled = enabled)
    val backdrop = if (enabled && Build.VERSION.SDK_INT >= 33) rememberLayerBackdrop() else null
    val view = androidx.compose.ui.platform.LocalView.current
    val density = LocalDensity.current
    val foldDistancePx = with(density) { 24.dp.toPx() }
    val unfoldDistancePx = with(density) { 14.dp.toPx() }
    return remember(haze, backdrop, enabled, foldDistancePx, unfoldDistancePx, enableHeroLens, view) {
        GlassContext(haze, backdrop, enabled, foldDistancePx, unfoldDistancePx, enableHeroLens, view)
    }
}

fun Modifier.glassSource(context: GlassContext): Modifier = if (context.enabled) {
    glassSourceMarker(context)
        .hazeSource(context.haze)
        .then(context.backdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier)
} else {
    this
}

@Composable
fun GlassSurface(
    context: GlassContext?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(TsuzukiCorners.capsulePercent),
    hero: Boolean = false,
    material: GlassMaterial = GlassMaterial.Regular,
    /** Kasane glass blur; menus and sheets frost more than bars so their rows stay legible. */
    frost: androidx.compose.ui.unit.Dp = KasaneGlassSpec.BlurRadius,
    /**
     * A moving liquid outline (two merged boxes, in this surface's px): the Kasane glass draws its
     * own coverage and neck from it, and [shape] (their union without the neck) clips the content
     * and the fallback materials.
     */
    liquid: (() -> LiquidGlassGeometry)? = null,
    /** Opacity of the liquid glass surface itself (leaves inner content/icon snapshots independent). */
    glassAlpha: (() -> Float)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = TsuzukiTheme.colors
    val nested = LocalInsideGlass.current
    val opaque = LocalOpaqueGlass.current || material == GlassMaterial.Opaque
    val tintStrength = LocalGlassTintStrength.current
    val moving = LocalNavigationMotion.current
    val enabled = context?.enabled == true && context.haze.areas.isNotEmpty() && !opaque && !nested
    val tint = colors.glassTint.copy(alpha = material.tintAlpha(colors.isDark, tintStrength))
    // GLS-01: every no-source path falls back to the material's near-opaque alpha (or the
    // reader's clear-glass override) — never the old unblurred ~0.72 tint, which read as
    // see-through glass with nothing behind it.
    val fallback = when {
        opaque -> colors.elevated
        else -> colors.elevated.copy(alpha = LocalGlassFallbackAlpha.current ?: material.fallbackAlpha)
    }
    val blur = material.blurRadius
    val view = androidx.compose.ui.platform.LocalView.current
    // Starts as "inside" so the first frame is Haze; the probe flips it once attached.
    var insideSource by remember(context) { mutableStateOf(true) }
    val kasane = enabled && LocalLiquidGlass.current && Build.VERSION.SDK_INT >= 33 && !hero && context.backdrop != null && !insideSource &&
        context.sourceView === view &&
        (liquid != null || shape is androidx.compose.foundation.shape.CornerBasedShape || shape is RoundedRectShape)
    val liquidPass = kasane && liquid != null
    val kasaneTint = colors.glassTint.copy(alpha = material.kasaneTintAlpha(colors.isDark, tintStrength))
    val style = remember(colors, tint, fallback, material) {
        HazeStyle(
            backgroundColor = colors.background,
            tints = material.hazeTints(tint),
            blurRadius = blur,
            noiseFactor = GlassMaterial.NoiseFactor,
            fallbackTint = HazeTint(fallback),
        )
    }
    val surface = when {
        liquidPass -> Modifier.then(Modifier)
        !enabled -> Modifier.background(
            if (nested) Color.Transparent else fallback,
            shape,
        )
        kasane -> Modifier.drawBackdrop(
            backdrop = context.backdrop,
            shape = { shape },
            effects = {
                val radii = glassCornerRadii(shape, size, layoutDirection, this)
                if (radii != null) kasaneGlass(radii, colors.isDark, kasaneTint, colors.background, highlightStrength = if (colors.isDark) 0.35f else 0.6f, blurRadius = frost)
            },
            highlight = null,
            shadow = { com.kyant.backdrop.shadow.Shadow(radius = 12.dp, color = Color.Black.copy(alpha = 0.10f)) },
        )
        hero && context.heroLens && Build.VERSION.SDK_INT >= 33 && context.backdrop != null -> Modifier.drawBackdrop(
            backdrop = context.backdrop,
            shape = { shape },
            effects = {
                blur(blur.toPx())
                lens(8.dp.toPx(), 12.dp.toPx())
            },
            onDrawSurface = { drawRect(tint) },
        )
        else -> Modifier.hazeEffect(state = context.haze, style = style) {
            inputScale = HazeInputScale.Fixed(if (moving) GlassMaterial.MotionInputScale else GlassMaterial.InputScale)
        }
    }
    val rim = if (nested || liquid != null) Modifier else Modifier.drawWithCache {
        val ring = shape.createOutline(size, layoutDirection, this)
        val inset = GlassMaterial.HighlightInset.toPx()
        val inner = shape.createOutline(
            Size((size.width - 2 * inset).coerceAtLeast(0f), (size.height - 2 * inset).coerceAtLeast(0f)),
            layoutDirection,
            this,
        )
        val ringColor = if (colors.isDark) GlassMaterial.DarkRing else GlassMaterial.LightRing
        val highlight = Brush.verticalGradient(
            listOf(if (colors.isDark) GlassMaterial.DarkHighlight else GlassMaterial.LightHighlight, Color.Transparent),
        )
        val ringStroke = Stroke(GlassMaterial.RingWidth.toPx())
        val highlightStroke = Stroke(GlassMaterial.HighlightWidth.toPx())
        onDrawWithContent {
            drawContent()
            // iOS glass has no outline: a faint light along the top edge only (the Kasane shader
            // lights the rim itself). The ring stays for the Haze material.
            if (!kasane) drawOutline(ring, ringColor, style = ringStroke)
            translate(inset, inset) { drawOutline(inner, highlight, style = highlightStroke) }
        }
    }
    val probed = modifier.glassSourceProbe(context) { insideSource = it }
    // A liquid outline changes every frame: the layer block re-reads it, the clip follows.
    val liquidClip = liquid?.let { geometry ->
        Modifier.graphicsLayer {
            this.shape = LiquidGlassShape(geometry())
            clip = true
        }
    }
    val provided: @Composable BoxScope.() -> Unit = {
        CompositionLocalProvider(
            LocalInsideGlass provides true,
            LocalGlassContext provides (context ?: LocalGlassContext.current),
        ) { content() }
    }
    when {
        liquidClip == null -> Box(probed.clip(shape).then(surface).then(rim)) { provided() }
        liquidPass && liquid != null && context?.backdrop != null -> Box(probed) {
            val page = context.backdrop
            // The liquid glass draws its own coverage and neck, so it is a sibling layer under
            // the clipped content, not a modifier on it (a backdrop pass in that chain never drew).
            Box(
                Modifier
                    .matchParentSize()
                    .drawBackdrop(
                        backdrop = page,
                        shape = { androidx.compose.ui.graphics.RectangleShape },
                        layerBlock = {
                            if (glassAlpha != null) alpha = glassAlpha()
                        },
                        effects = {
                            liquidGlass(liquid(), colors.isDark, kasaneTint, colors.background, highlightStrength = if (colors.isDark) 0.35f else 0.6f, blurRadius = frost)
                        },
                        highlight = null,
                        shadow = null,
                    ),
            )
            Box(liquidClip) { provided() }
        }
        else -> if (glassAlpha != null) {
            Box(probed) {
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = glassAlpha() }
                        .then(liquidClip)
                        .then(surface),
                )
                Box(liquidClip) { provided() }
            }
        } else {
            Box(probed.then(liquidClip).then(surface)) { provided() }
        }
    }
}
