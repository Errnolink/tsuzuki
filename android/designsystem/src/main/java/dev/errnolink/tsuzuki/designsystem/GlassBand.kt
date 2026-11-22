package dev.errnolink.tsuzuki.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.RectangleShape
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur

/** The top bar's blur shape; the temporary experiment picker is gone and Progressive is the shell's pick. */
enum class TopBarBlurStyle {
    Progressive,
    SoftFade,
}

/**
 * Whether a context can host a progressive blur band right now: glass on, at least one haze
 * source registered, and not nested inside glass or forced opaque.
 */
@Composable
fun bandBlurEnabled(context: GlassContext?): Boolean =
    context?.enabled == true && context.haze.areas.isNotEmpty() &&
        !LocalOpaqueGlass.current && !LocalInsideGlass.current

/**
 * A progressive-blur band (iOS Photos' top edge): Haze's vertical gradient runs at full intensity
 * over the band's start and fades to zero at [fadeToPx]; an optional light background-colour
 * gradient ([backgroundTintTop] alpha at the top to 0 at [fadeToPx]) keeps the content under the
 * band legible on bright covers. Callers that need the opaque fallback draw their own colour
 * gradient instead ([TopEdge] does).
 */
@Composable
fun GlassBand(
    context: GlassContext?,
    modifier: Modifier = Modifier,
    tintScale: Float = 1f,
    fadeFromPx: Float = 0f,
    fadeToPx: Float = Float.POSITIVE_INFINITY,
    fadeFromPxProvider: (() -> Float)? = null,
    fadeToPxProvider: (() -> Float)? = null,
    backgroundTintTop: Float = 0f,
    /** Motion progress 0..1 while a transition flies (WP6.6): the glass input scale follows it
     *  continuously instead of snapping between the rest and motion scales at stage boundaries.
     *  Null falls back to the navigation-motion boolean. */
    motionFraction: (() -> Float)? = null,
    hairline: Boolean = false,
    blurStyle: TopBarBlurStyle = TopBarBlurStyle.SoftFade,
    active: Boolean = true,
) {
    val colors = TsuzukiTheme.colors
    val nested = LocalInsideGlass.current
    val opaque = LocalOpaqueGlass.current
    val moving = LocalNavigationMotion.current
    val density = LocalDensity.current
    val enabled = bandBlurEnabled(context) && !opaque && !nested
    val tint = colors.glassTint.copy(
        alpha = GlassMaterial.Thick.tintAlpha(colors.isDark, LocalGlassTintStrength.current) * tintScale,
    )
    val styleBlurRadius = GlassMaterial.Regular.blurRadius
    val style = remember(colors, tint, styleBlurRadius) {
        HazeStyle(
            backgroundColor = colors.background,
            tint = HazeTint(tint),
            blurRadius = styleBlurRadius,
            noiseFactor = GlassMaterial.NoiseFactor,
            fallbackTint = HazeTint(colors.background),
        )
    }
    val hairlineWidth = with(density) { TsuzukiSpacing.hairline.toPx() }
    val lineModifier = if (hairline) {
        Modifier.drawWithCache {
            val y = size.height - hairlineWidth / 2f
            onDrawBehind {
                drawLine(colors.separator, Offset(0f, y), Offset(size.width, y), hairlineWidth)
            }
        }
    } else {
        Modifier
    }
    if (!enabled || context == null) {
        if (active) {
            Spacer(modifier.background(colors.background).then(lineModifier))
        } else {
            Spacer(modifier)
        }
        return
    }
    val view = androidx.compose.ui.platform.LocalView.current
    var insideSource by remember(context) { mutableStateOf(true) }
    val kasane = LocalLiquidGlass.current && android.os.Build.VERSION.SDK_INT >= 33 && context.backdrop != null && !insideSource &&
        context.sourceView === view
    val kasaneTint = colors.glassTint.copy(
        alpha = GlassMaterial.Regular.kasaneTintAlpha(colors.isDark, LocalGlassTintStrength.current) * tintScale,
    )
    val probe = Modifier.glassSourceProbe(context) { insideSource = it }
    if (!active) {
        // Keep the probe attached at rest so Frame 1 of scrolling already knows it is outside the source.
        Spacer(Modifier.then(probe))
        return
    }
    val useBackdropEffect = kasane
    val backdrop = context.backdrop
    val showGradientTint = backgroundTintTop > 0f
    val tintModifier = if (showGradientTint) {
        Modifier.drawWithContent {
            drawContent()
            val to = (fadeToPxProvider?.invoke() ?: fadeToPx).let { if (it.isFinite()) it else size.height }.coerceAtLeast(1f)
            drawRect(
                Brush.verticalGradient(
                    0f to colors.background.copy(alpha = backgroundTintTop),
                    0.55f to colors.background.copy(alpha = backgroundTintTop * 0.80f),
                    0.82f to colors.background.copy(alpha = backgroundTintTop * 0.28f),
                    1f to colors.background.copy(alpha = 0f),
                    startY = 0f,
                    endY = to,
                ),
            )
        }
    } else {
        Modifier
    }
    if (useBackdropEffect && backdrop != null) {
        Spacer(
            modifier
                .then(probe)
                .then(tintModifier)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RectangleShape },
                    effects = {
                        if (android.os.Build.VERSION.SDK_INT >= 33) {
                            val from = fadeFromPxProvider?.invoke() ?: fadeFromPx
                            val to = (fadeToPxProvider?.invoke() ?: fadeToPx).let { if (it.isFinite()) it else 1e6f }
                            when (blurStyle) {
                                TopBarBlurStyle.Progressive -> progressiveBand(from, to, kasaneTint, colors.background)
                                TopBarBlurStyle.SoftFade -> kasaneBand(from, to, kasaneTint, colors.background)
                            }
                        }
                    },
                    highlight = null,
                    shadow = null,
                )
                .then(lineModifier),
        )
        return
    }
    Spacer(
        modifier
            .then(probe)
            .then(tintModifier)
            .hazeEffect(state = context.haze, style = style) {
                // WP6.6: the input scale rides the transition's own progress (read here in draw),
                // not a boolean flipped at the stage boundary.
                val fraction = (motionFraction?.invoke() ?: if (moving) 1f else 0f).coerceIn(0f, 1f)
                val scale = GlassMaterial.InputScale + (GlassMaterial.MotionInputScale - GlassMaterial.InputScale) * fraction
                inputScale = HazeInputScale.Fixed(scale)
                // Haze 1.7 builds the progressive and mask shaders in the down-scaled content
                // layer's space, so the gradient stops are scaled with it. Unscaled stops sat
                // past the layer's end: full blur everywhere and a hard edge at the band's bottom.
                val from = (fadeFromPxProvider?.invoke() ?: fadeFromPx) * scale
                val to = ((fadeToPxProvider?.invoke() ?: fadeToPx).let { if (it.isFinite()) it else 1e6f }) * scale
                progressive = HazeProgressive.verticalGradient(
                    startY = from,
                    startIntensity = 1f,
                    endY = to,
                    endIntensity = 0f,
                )
                // TOP-01: the mask fades the whole effect, tint as well as blur, over the same
                // vertical window, so the band ends in a soft edge instead of a hard tint line.
                mask = Brush.verticalGradient(
                    listOf(Color.Black, Color.Transparent),
                    startY = from,
                    endY = to,
                )
            }
            .then(lineModifier),
    )
}

/**
 * Full-area Kasane frosted backdrop matching the app's glass blur system (`drawBackdrop` blur on
 * API 33+ or `Haze` fallback below), overlaid with [scrimColor] so panels and grids stay legible
 * without text bleed-through.
 */
@Composable
fun GlassBackdropScrim(
    context: GlassContext?,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 24.dp,
    scrimColor: Color = TsuzukiTheme.colors.background.copy(alpha = 0.78f),
    fallbackColor: Color = TsuzukiTheme.colors.background.copy(alpha = 0.96f),
) {
    val colors = TsuzukiTheme.colors
    if (!bandBlurEnabled(context) || context == null) {
        Spacer(modifier.background(fallbackColor))
        return
    }
    val view = androidx.compose.ui.platform.LocalView.current
    var insideSource by remember(context) { mutableStateOf(true) }
    val probe = Modifier.glassSourceProbe(context) { insideSource = it }
    val backdrop = context.backdrop
    val kasane = LocalLiquidGlass.current &&
        android.os.Build.VERSION.SDK_INT >= 33 &&
        backdrop != null &&
        !insideSource &&
        context.sourceView === view
    if (kasane && backdrop != null) {
        Spacer(
            modifier
                .then(probe)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RectangleShape },
                    effects = {
                        if (android.os.Build.VERSION.SDK_INT >= 33) {
                            blur(blurRadius.toPx())
                        }
                    },
                    highlight = null,
                    shadow = null,
                )
                .background(scrimColor),
        )
    } else {
        val style = remember(colors, scrimColor, fallbackColor, blurRadius) {
            HazeStyle(
                backgroundColor = colors.background,
                tint = HazeTint(scrimColor),
                blurRadius = blurRadius,
                noiseFactor = GlassMaterial.NoiseFactor,
                fallbackTint = HazeTint(fallbackColor),
            )
        }
        Spacer(
            modifier
                .then(probe)
                .hazeEffect(state = context.haze, style = style),
        )
    }
}
