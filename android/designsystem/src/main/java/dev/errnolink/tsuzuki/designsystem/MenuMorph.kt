package dev.errnolink.tsuzuki.designsystem

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

internal fun menuMorphStart(anchor: Rect, size: Size): Rect {
    val bounds = Rect(0f, 0f, size.width, size.height)
    val overlap = anchor.intersect(bounds)
    if (overlap.width > 0f && overlap.height > 0f) return overlap
    val point = Offset(
        anchor.center.x.coerceIn(0f, size.width),
        anchor.center.y.coerceIn(0f, size.height),
    )
    return Rect(point, point)
}

internal class MenuMorphShape(
    private val anchor: Rect,
    private val progress: Float,
    private val endRadiusPx: Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val start = menuMorphStart(anchor, size)
        val p = progress.coerceIn(0f, 1f)
        val left = start.left * (1f - p)
        val top = start.top * (1f - p)
        val right = start.right + (size.width - start.right) * p
        val bottom = start.bottom + (size.height - start.bottom) * p
        val startRadius = minOf(start.width, start.height) / 2f
        val radius = startRadius + (endRadiusPx - startRadius) * p
        return Outline.Rounded(RoundRect(left, top, right, bottom, CornerRadius(radius)))
    }
}

internal fun menuMorphContentAlpha(progress: Float): Float = ((progress - 0.35f) / 0.45f).coerceIn(0f, 1f)
