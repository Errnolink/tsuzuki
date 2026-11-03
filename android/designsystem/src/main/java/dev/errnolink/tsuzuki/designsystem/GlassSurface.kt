package dev.errnolink.tsuzuki.designsystem

import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.CornerBasedShape
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
import androidx.compose.ui.platform.LocalWindowInfo
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
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.HazeInputScale

val LocalInsideGlass = staticCompositionLocalOf { false }
val LocalNavigationMotion = compositionLocalOf { false }

private const val CollapseFraction = 2f
private const val RestoreFraction = 6f

@Stable
class GlassContext internal constructor(
    internal val haze: HazeState,
    internal val backdrop: LayerBackdrop?,
    internal val enabled: Boolean,
    internal val collapseThresholdPx: Float = 360f,
    internal val restoreThresholdPx: Float = 260f,
    internal val collapseFlingVelocityPx: Float = 2200f,
    internal val expandFlingVelocityPx: Float = 1600f,
) {
    var navigationMinimized by mutableStateOf(false)
    var isScrolling by mutableStateOf(false)

    fun expandNavigation() {
        navigationMinimized = false
        downDistance = 0f
        upDistance = 0f
        morphLockUntilMillis = SystemClock.uptimeMillis() + MorphLockMillis
    }

    private var downDistance = 0f
    private var upDistance = 0f
    private var lastScrollMillis = 0L
    private var morphLockUntilMillis = 0L

    internal val navigationScroll = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput || source == NestedScrollSource.SideEffect) {
                isScrolling = true
            }
            return Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            val user = source == NestedScrollSource.UserInput
            if (user || (source == NestedScrollSource.SideEffect && consumed.y > 0f)) {
                val delta = consumed.y
                val now = SystemClock.uptimeMillis()
                val elapsed = now - lastScrollMillis
                lastScrollMillis = now
                if (elapsed > GestureGapMillis) {
                    downDistance = 0f
                    upDistance = 0f
                }
                if (delta < 0f) {
                    upDistance = 0f
                    if (user) downDistance -= delta
                } else if (delta > 0f) {
                    downDistance = 0f
                    upDistance += delta
                }
                val frameVelocity = if (user && elapsed in 1..FlingSampleMillis) delta * 1000f / elapsed else 0f
                if (now >= morphLockUntilMillis) {
                    if (!navigationMinimized && user && (downDistance >= collapseThresholdPx || -frameVelocity >= collapseFlingVelocityPx)) {
                        navigationMinimized = true
                        downDistance = 0f
                        upDistance = 0f
                        morphLockUntilMillis = now + MorphLockMillis
                    } else if (navigationMinimized && (upDistance >= restoreThresholdPx || frameVelocity >= expandFlingVelocityPx || (user && available.y > 0f))) {
                        navigationMinimized = false
                        downDistance = 0f
                        upDistance = 0f
                        morphLockUntilMillis = now + MorphLockMillis
                    }
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

    private companion object {
        const val GestureGapMillis = 1500L
        const val FlingSampleMillis = 250L
        val MorphLockMillis = M3Motion.Medium1 + 50L
    }
}

fun Modifier.floatingNavigationScroll(context: GlassContext): Modifier = nestedScroll(context.navigationScroll)

@Composable
fun rememberGlassContext(enableHeroLens: Boolean = false): GlassContext {
    val enabled = !LocalOpaqueGlass.current && Build.VERSION.SDK_INT >= 31
    val haze = rememberHazeState(blurEnabled = enabled)
    val backdrop = if (enabled && enableHeroLens && Build.VERSION.SDK_INT >= 33) rememberLayerBackdrop() else null
    val density = LocalDensity.current
    val windowHeightPx = LocalWindowInfo.current.containerSize.height.toFloat()
    val collapseThresholdPx = if (windowHeightPx > 0f) {
        windowHeightPx / CollapseFraction
    } else {
        with(density) { 370.dp.toPx() }
    }
    val restoreThresholdPx = if (windowHeightPx > 0f) {
        windowHeightPx / RestoreFraction
    } else {
        with(density) { 220.dp.toPx() }
    }
    return remember(haze, backdrop, enabled, collapseThresholdPx, restoreThresholdPx) {
        GlassContext(haze, backdrop, enabled, collapseThresholdPx, restoreThresholdPx)
    }
}

fun Modifier.glassSource(context: GlassContext): Modifier = if (context.enabled) {
    hazeSource(context.haze).then(context.backdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier)
} else {
    this
}

@Composable
fun GlassSurface(
    context: GlassContext?,
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = RoundedCornerShape(TsuzukiCorners.capsulePercent),
    hero: Boolean = false,
    material: GlassMaterial = GlassMaterial.Regular,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = TsuzukiTheme.colors
    val nested = LocalInsideGlass.current
    val opaque = LocalOpaqueGlass.current || material == GlassMaterial.Opaque
    val tintStrength = LocalGlassTintStrength.current
    val moving = LocalNavigationMotion.current
    val enabled = context?.enabled == true && context.haze.areas.isNotEmpty() && !opaque && !nested
    val tint = colors.glassTint.copy(alpha = material.tintAlpha(colors.isDark, tintStrength))
    val fallback = when {
        opaque -> colors.elevated
        context == null -> colors.elevated.copy(alpha = material.fallbackAlpha)
        else -> colors.elevated.copy(alpha = (TsuzukiShellMaterial.tintAlpha(colors.isDark) * (0.75f + 0.35f * tintStrength)).coerceAtMost(1f))
    }
    val blur = material.blurRadius
    val style = remember(colors, tint, fallback, blur) {
        HazeStyle(
            backgroundColor = colors.background,
            tint = HazeTint(tint),
            blurRadius = blur,
            noiseFactor = GlassMaterial.NoiseFactor,
            fallbackTint = HazeTint(fallback),
        )
    }
    val surface = when {
        !enabled -> Modifier.background(
            if (nested) Color.Transparent else fallback,
            shape,
        )
        hero && Build.VERSION.SDK_INT >= 33 && context.backdrop != null -> Modifier.drawBackdrop(
            backdrop = context.backdrop,
            shape = { shape },
            effects = {
                blur(blur.toPx())
                lens(8.dp.toPx(), 12.dp.toPx())
            },
            onDrawSurface = { drawRect(tint) },
        )
        else -> Modifier.hazeEffect(state = context.haze, style = style) {
            inputScale = HazeInputScale.Fixed(if (moving || context.isScrolling) GlassMaterial.MotionInputScale else GlassMaterial.InputScale)
        }
    }
    val rim = if (nested) Modifier else Modifier.drawWithCache {
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
            drawOutline(ring, ringColor, style = ringStroke)
            translate(inset, inset) { drawOutline(inner, highlight, style = highlightStroke) }
        }
    }
    Box(modifier.clip(shape).then(surface).then(rim)) {
        CompositionLocalProvider(LocalInsideGlass provides true) { content() }
    }
}
