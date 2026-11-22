package dev.errnolink.tsuzuki.designsystem

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop

/** Whether a live tab lens can draw here (API 33+, glass on, a recorded page). */
@Stable
class GlassLensSource internal constructor(val live: Boolean)

@Composable
fun rememberGlassLensSource(context: GlassContext?): GlassLensSource {
    val live = context?.enabled == true && context.backdrop != null && Build.VERSION.SDK_INT >= 33
    return remember(live) { GlassLensSource(live) }
}

/**
 * The recorded content of a selection bar — everything its held lens magnifies (surface, pill,
 * rows, the selected-colour copy). Opaque so callers never touch the backdrop library; keep the
 * lens node a sibling of the recorded node, never a descendant.
 */
@Stable
class SelectionBarContent internal constructor(internal val backdrop: LayerBackdrop)

@Composable
fun rememberSelectionBarContent(): SelectionBarContent {
    val backdrop = rememberLayerBackdrop()
    return remember(backdrop) { SelectionBarContent(backdrop) }
}

/** Records the node's drawing as the content the held selection lens bends. */
fun Modifier.selectionBarContent(content: SelectionBarContent): Modifier =
    layerBackdrop(content.backdrop)

/**
 * The held selection lens (iOS 26): clear glass that magnifies and refracts what is under it —
 * the recorded [barContent] and, when [context] carries a page backdrop, the page as well.
 * [geometry] is read in the lens node's own px ([width], [height]) each frame; [lift] (0 at rest,
 * 1 held) fades and grows the pass; [layerBlock] carries the caller's scale so the backdrop stays
 * registered.
 */
fun Modifier.liquidSelectionLens(
    context: GlassContext?,
    barContent: SelectionBarContent,
    geometry: (width: Float, height: Float) -> LiquidGlassGeometry,
    lift: () -> Float,
    layerBlock: GraphicsLayerScope.() -> Unit = {},
): Modifier = composed {
    val colors = TsuzukiTheme.colors
    val page = context?.backdrop
    val backdrop: Backdrop = if (page != null) rememberCombinedBackdrop(page, barContent.backdrop) else barContent.backdrop
    val tint = Color.White.copy(alpha = if (colors.isDark) 0.05f else 0.12f)
    Modifier.drawBackdrop(
        backdrop = backdrop,
        shape = { RectangleShape },
        layerBlock = {
            alpha = smoothstep(0f, 0.2f, lift().coerceIn(0f, 1f))
            layerBlock()
        },
        effects = {
            if (Build.VERSION.SDK_INT >= 33 && size.minDimension > 0.5f) {
                val l = lift().coerceIn(0f, 1f)
                liquidGlass(
                    geometry(size.width, size.height),
                    dark = colors.isDark,
                    tint = tint,
                    page = colors.background,
                    highlightStrength = if (colors.isDark) 0.35f else 0.6f,
                    blurRadius = 0.dp,
                    clear = true,
                    refractionHeight = 10.dp,
                    outerAmount = 9.dp * l,
                )
            }
        },
        highlight = null,
        shadow = null,
    )
}
