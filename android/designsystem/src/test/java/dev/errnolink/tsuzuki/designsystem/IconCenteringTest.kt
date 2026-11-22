package dev.errnolink.tsuzuki.designsystem

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * Review screenshot s3 (the Feed calendar sits low and right): every icon's drawn bounds must sit
 * centred in its 24-unit viewport — the offset is fixed in the vector paths at the source, not by
 * nudging each caller. Stroke widths expand the drawn bounds by half their width on each side.
 */
class IconCenteringTest {

    private data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val centreX get() = (left + right) / 2f
        val centreY get() = (top + bottom) / 2f
    }

    /** The union bounds of one parsed path, control points included (curves lie inside their hull). */
    private fun pathBounds(nodes: List<PathNode>): Bounds? {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var lastX = 0f
        var lastY = 0f
        var startX = 0f
        var startY = 0f

        fun point(x: Float, y: Float) {
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
        }

        for (node in nodes) {
            when (node) {
                is PathNode.MoveTo -> { point(node.x, node.y); lastX = node.x; lastY = node.y; startX = lastX; startY = lastY }
                is PathNode.LineTo -> { point(node.x, node.y); lastX = node.x; lastY = node.y }
                is PathNode.CurveTo -> {
                    point(node.x1, node.y1); point(node.x2, node.y2); point(node.x3, node.y3)
                    lastX = node.x3; lastY = node.y3
                }
                is PathNode.QuadTo -> {
                    point(node.x1, node.y1); point(node.x2, node.y2)
                    lastX = node.x2; lastY = node.y2
                }
                is PathNode.ReflectiveCurveTo -> {
                    point(node.x1, node.y1); point(node.x2, node.y2)
                    lastX = node.x2; lastY = node.y2
                }
                is PathNode.ReflectiveQuadTo -> {
                    point(node.x, node.y)
                    lastX = node.x; lastY = node.y
                }
                is PathNode.ArcTo -> {
                    arc(lastX, lastY, node.horizontalEllipseRadius, node.verticalEllipseRadius, node.theta, node.isMoreThanHalf, node.isPositiveArc, node.arcStartX, node.arcStartY, ::point)
                    lastX = node.arcStartX; lastY = node.arcStartY
                }
                is PathNode.RelativeMoveTo -> {
                    lastX += node.dx; lastY += node.dy; point(lastX, lastY); startX = lastX; startY = lastY
                }
                is PathNode.RelativeLineTo -> {
                    lastX += node.dx; lastY += node.dy; point(lastX, lastY)
                }
                is PathNode.RelativeCurveTo -> {
                    point(lastX + node.dx1, lastY + node.dy1)
                    point(lastX + node.dx2, lastY + node.dy2)
                    point(lastX + node.dx3, lastY + node.dy3)
                    lastX += node.dx3; lastY += node.dy3
                }
                is PathNode.RelativeQuadTo -> {
                    point(lastX + node.dx1, lastY + node.dy1)
                    point(lastX + node.dx2, lastY + node.dy2)
                    lastX += node.dx2; lastY += node.dy2
                }
                is PathNode.RelativeReflectiveCurveTo -> {
                    point(lastX + node.dx1, lastY + node.dy1)
                    point(lastX + node.dx2, lastY + node.dy2)
                    lastX += node.dx2; lastY += node.dy2
                }
                is PathNode.RelativeReflectiveQuadTo -> {
                    point(lastX + node.dx, lastY + node.dy)
                    lastX += node.dx; lastY += node.dy
                }
                is PathNode.RelativeArcTo -> {
                    val endX = lastX + node.arcStartDx
                    val endY = lastY + node.arcStartDy
                    arc(lastX, lastY, node.horizontalEllipseRadius, node.verticalEllipseRadius, node.theta, node.isMoreThanHalf, node.isPositiveArc, endX, endY, ::point)
                    lastX = endX; lastY = endY
                }
                PathNode.Close -> { lastX = startX; lastY = startY }
                is PathNode.HorizontalTo -> { point(node.x, lastY); lastX = node.x }
                is PathNode.VerticalTo -> { point(lastX, node.y); lastY = node.y }
                is PathNode.RelativeHorizontalTo -> { lastX += node.dx; point(lastX, lastY) }
                is PathNode.RelativeVerticalTo -> { lastY += node.dy; point(lastX, lastY) }
                else -> Unit
            }
        }
        if (minX > maxX || minY > maxY) return null
        return Bounds(minX, minY, maxX, maxY)
    }

    /** Samples an SVG elliptical arc (endpoint form, SVG 1.1 F.6.5) so its true extent counts. */
    private fun arc(
        x0: Float, y0: Float, rxIn: Float, ryIn: Float, angleDeg: Float,
        large: Boolean, sweep: Boolean, x1: Float, y1: Float, point: (Float, Float) -> Unit,
    ) {
        point(x1, y1)
        var rx = abs(rxIn).toDouble()
        var ry = abs(ryIn).toDouble()
        if (rx == 0.0 || ry == 0.0 || (x0 == x1 && y0 == y1)) return
        val phi = Math.toRadians(angleDeg.toDouble())
        val cosP = kotlin.math.cos(phi)
        val sinP = kotlin.math.sin(phi)
        val dx = (x0 - x1) / 2.0
        val dy = (y0 - y1) / 2.0
        val xp = cosP * dx + sinP * dy
        val yp = -sinP * dx + cosP * dy
        val lambda = (xp * xp) / (rx * rx) + (yp * yp) / (ry * ry)
        if (lambda > 1) { rx *= kotlin.math.sqrt(lambda); ry *= kotlin.math.sqrt(lambda) }
        val num = rx * rx * ry * ry - rx * rx * yp * yp - ry * ry * xp * xp
        val den = rx * rx * yp * yp + ry * ry * xp * xp
        var coef = kotlin.math.sqrt(maxOf(0.0, num / den))
        if (large == sweep) coef = -coef
        val cxp = coef * rx * yp / ry
        val cyp = -coef * ry * xp / rx
        val cx = cosP * cxp - sinP * cyp + (x0 + x1) / 2.0
        val cy = sinP * cxp + cosP * cyp + (y0 + y1) / 2.0
        fun angle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
            val a = kotlin.math.atan2(ux * vy - uy * vx, ux * vx + uy * vy)
            return a
        }
        val theta1 = angle(1.0, 0.0, (xp - cxp) / rx, (yp - cyp) / ry)
        var delta = angle((xp - cxp) / rx, (yp - cyp) / ry, (-xp - cxp) / rx, (-yp - cyp) / ry)
        if (!sweep && delta > 0) delta -= 2 * Math.PI
        if (sweep && delta < 0) delta += 2 * Math.PI
        val steps = 64
        for (i in 0..steps) {
            val t = theta1 + delta * i / steps
            val ex = rx * kotlin.math.cos(t)
            val ey = ry * kotlin.math.sin(t)
            point((cosP * ex - sinP * ey + cx).toFloat(), (sinP * ex + cosP * ey + cy).toFloat())
        }
    }

    /** The icon's drawn bounds: every path's hull expanded by half its stroke width. */
    private fun drawnBounds(vector: ImageVector): Bounds? {
        var union: Bounds? = null
        fun merge(b: Bounds) {
            union = union?.let { Bounds(minOf(it.left, b.left), minOf(it.top, b.top), maxOf(it.right, b.right), maxOf(it.bottom, b.bottom)) } ?: b
        }
        // Groups only translate here (the icon builder's centring offset), so offsets add up.
        fun walk(node: Any, ox: Float, oy: Float) {
            when (node) {
                is androidx.compose.ui.graphics.vector.VectorGroup ->
                    node.forEach { walk(it, ox + node.translationX, oy + node.translationY) }
                is androidx.compose.ui.graphics.vector.VectorPath -> {
                    val b = pathBounds(node.pathData) ?: return
                    val half = node.strokeLineWidth / 2f
                    merge(Bounds(b.left - half + ox, b.top - half + oy, b.right + half + ox, b.bottom + half + oy))
                }
            }
        }
        walk(vector.root, 0f, 0f)
        return union
    }

    @Test
    fun `every icon's drawn bounds are centred in its viewport within three quarters of a unit`() {
        // The icons are `by lazy` vals: members of `TsuzukiIcons` and extension properties on it
        // (static getters on `TsuzukiIconsKt`), so reflection walks both kinds of getter.
        val members = TsuzukiIcons::class.java.methods
            .filter { it.parameterCount == 0 && ImageVector::class.java.isAssignableFrom(it.returnType) }
            .associateBy({ it.name.removePrefix("get") }, { it.invoke(TsuzukiIcons) as ImageVector })
        val extensions = Class.forName("dev.errnolink.tsuzuki.designsystem.TsuzukiIconsKt").methods
            .filter {
                it.parameterCount == 1 &&
                    it.parameterTypes[0] == TsuzukiIcons::class.java &&
                    ImageVector::class.java.isAssignableFrom(it.returnType)
            }
            .associateBy({ it.name.removePrefix("get") }, { it.invoke(null, TsuzukiIcons) as ImageVector })
        val icons = members + extensions
        assertTrue(members.size >= 12 && extensions.size >= 90, "reflection found ${members.size} + ${extensions.size} icons")
        val offenders = buildMap {
            icons.forEach { (name, vector) ->
                val bounds = drawnBounds(vector) ?: return@forEach
                val dx = abs(bounds.centreX - vector.viewportWidth / 2f)
                val dy = abs(bounds.centreY - vector.viewportHeight / 2f)
                if (dx > Tolerance || dy > Tolerance) {
                    put(name, "centre (${bounds.centreX}, ${bounds.centreY}) in ${vector.viewportWidth}×${vector.viewportHeight}")
                }
            }
        }
        if (offenders.isNotEmpty()) {
            throw AssertionError("off-centre icons (fix the path offsets at the source): $offenders")
        }
    }

    companion object {
        private const val Tolerance = 0.75f
    }
}
