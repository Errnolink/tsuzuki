package dev.errnolink.tsuzuki.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.reader.ReaderTurnSignals
import dev.errnolink.tsuzuki.reader.TurnEdge
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Kasane zone glow: 520 ms ease-out, flash to full at 22 %, then out while stretching 1.15x. */
private val TurnGlowEasing = CubicBezierEasing(0f, 0f, 0.58f, 1f)

/**
 * The Kasane edge glow: a soft gradient flash at the tapped (or reading-direction) edge when a
 * page turn is requested. Draws over the page, under the chrome; never intercepts touches.
 * Reduced motion skips the flash entirely, like Kasane's `glow()`.
 */
@Composable
fun ReaderTurnGlow(modifier: Modifier = Modifier) {
    val flash by ReaderTurnSignals.flash.collectAsState()
    val motion = TsuzukiTheme.motion
    val progress = remember { Animatable(1f) }
    LaunchedEffect(flash.seq) {
        if (flash.edge == TurnEdge.None) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(520, easing = TurnGlowEasing))
        }
    }
    if (motion.reduced) return
    val prefs = remember { Injekt.get<ReaderPreferences>() }
    val readerTheme by prefs.readerTheme().collectAsState()
    val dark = dev.errnolink.tsuzuki.reader.readerCanvasDark(readerTheme, isSystemInDarkTheme())
    val base = if (dark) Color.White else TsuzukiTheme.colors.accent
    val f = progress.value
    if (f >= 1f || f <= 0f) return
    val inner = if (f < 0.22f) f / 0.22f else (f - 0.22f) / 0.78f
    val opacity = if (f < 0.22f) inner else 1f - inner
    val stretch = if (f < 0.22f) 0.6f + 0.4f * inner else 1f + 0.15f * inner

    Canvas(modifier.fillMaxSize()) {
        val lit = base.copy(alpha = 0.40f * opacity)
        val rim = base.copy(alpha = 0.55f * opacity)
        when (flash.edge) {
            TurnEdge.Left -> drawEdgeGlow(lit, rim, stretch, vertical = false, leading = true)
            TurnEdge.Right -> drawEdgeGlow(lit, rim, stretch, vertical = false, leading = false)
            TurnEdge.Top -> drawEdgeGlow(lit, rim, stretch, vertical = true, leading = true)
            TurnEdge.Bottom -> drawEdgeGlow(lit, rim, stretch, vertical = true, leading = false)
            TurnEdge.None -> Unit
        }
    }
}

/**
 * One glow band pinned to an edge: a 34 %-span gradient (40 % tint fading out by 72 %) plus a
 * 2 dp inset line, scaled about the edge origin from .6 to 1.15 while it flashes.
 */
private fun DrawScope.drawEdgeGlow(
    lit: Color,
    rim: Color,
    stretch: Float,
    vertical: Boolean,
    leading: Boolean,
) {
    val line = 2.dp.toPx()
    if (vertical) {
        val glowH = size.height * 0.34f
        val originY = if (leading) 0f else size.height - glowH
        clipRect(0f, originY, size.width, originY + glowH) {
            withTransform({
                scale(1f, stretch, pivot = Offset(size.width / 2f, if (leading) 0f else size.height))
            }) {
                val brush = Brush.verticalGradient(
                    0f to lit,
                    0.72f to Color.Transparent,
                    startY = originY,
                    endY = originY + glowH,
                )
                drawRect(brush, topLeft = Offset(0f, originY), size = Size(size.width, glowH))
                drawRect(
                    rim,
                    topLeft = Offset(0f, if (leading) 0f else size.height - line),
                    size = Size(size.width, line),
                )
            }
        }
    } else {
        val glowW = size.width * 0.34f
        val originX = if (leading) 0f else size.width - glowW
        clipRect(originX, 0f, originX + glowW, size.height) {
            withTransform({
                scale(stretch, 1f, pivot = Offset(if (leading) 0f else size.width, size.height / 2f))
            }) {
                val brush = Brush.horizontalGradient(
                    0f to lit,
                    0.72f to Color.Transparent,
                    startX = originX,
                    endX = originX + glowW,
                )
                drawRect(brush, topLeft = Offset(originX, 0f), size = Size(glowW, size.height))
                drawRect(
                    rim,
                    topLeft = Offset(if (leading) 0f else size.width - line, 0f),
                    size = Size(line, size.height),
                )
            }
        }
    }
}
