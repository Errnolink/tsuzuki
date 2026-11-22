// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): eu/kanade/tachiyomi/ui/reader/viewer/ViewerNavigation.kt
package dev.errnolink.tsuzuki.reader.engine.viewer

import android.graphics.PointF
import android.graphics.RectF
import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR

abstract class ViewerNavigation {

    sealed class NavigationRegion(val nameRes: StringResource, val color: Int) {
        data object MENU : NavigationRegion(MR.strings.action_menu, 0xCC95818D.toInt())
        data object PREV : NavigationRegion(MR.strings.nav_zone_prev, 0xCCFF7733.toInt())
        data object NEXT : NavigationRegion(MR.strings.nav_zone_next, 0xCC84E296.toInt())
        data object LEFT : NavigationRegion(MR.strings.nav_zone_left, 0xCC7D1128.toInt())
        data object RIGHT : NavigationRegion(MR.strings.nav_zone_right, 0xCCA6CFD5.toInt())

        fun directionalRegion(LTR: Boolean): NavigationRegion {
            return if (this === LEFT || this === RIGHT) {
                if (if (LTR) this === RIGHT else this === LEFT) NEXT else PREV
            } else {
                this
            }
        }
    }

    data class Region(val rectF: RectF, val type: NavigationRegion) {
        fun invert(invertMode: TappingInvertMode): Region {
            if (invertMode == TappingInvertMode.NONE) return this
            return this.copy(rectF = this.rectF.invert(invertMode))
        }
    }

    private var constantMenuRegion: RectF = RectF(0f, 0f, 1f, 0.05f)

    abstract var regions: List<Region>

    var invertMode: TappingInvertMode = TappingInvertMode.NONE

    var smallerTapZone: Boolean = false

    fun scaledRegions(): List<Region> {
        if (!smallerTapZone) return regions
        return regions.map { region ->
            region.copy(
                rectF = RectF(
                    shrinkEdgeCoord(region.rectF.left, smaller = true),
                    shrinkEdgeCoord(region.rectF.top, smaller = true),
                    shrinkEdgeCoord(region.rectF.right, smaller = true),
                    shrinkEdgeCoord(region.rectF.bottom, smaller = true),
                ),
            )
        }
    }

    fun getAction(pos: PointF): NavigationRegion {
        val x = pos.x
        val y = pos.y
        val region = scaledRegions().map { it.invert(invertMode) }.find { it.rectF.contains(x, y) }
        return when {
            region != null -> region.type
            constantMenuRegion.contains(x, y) -> NavigationRegion.MENU
            else -> NavigationRegion.MENU
        }
    }

    enum class TappingInvertMode(
        val shouldInvertHorizontal: Boolean = false,
        val shouldInvertVertical: Boolean = false,
    ) {
        NONE,
        HORIZONTAL(shouldInvertHorizontal = true),
        VERTICAL(shouldInvertVertical = true),
        BOTH(shouldInvertHorizontal = true, shouldInvertVertical = true),
    }
}

fun RectF.invert(invertMode: ViewerNavigation.TappingInvertMode): RectF {
    val horizontal = invertMode.shouldInvertHorizontal
    val vertical = invertMode.shouldInvertVertical
    return when {
        horizontal && vertical ->
            RectF(1f - this.right, 1f - this.bottom, 1f - this.left, 1f - this.top)
        vertical -> RectF(this.left, 1f - this.bottom, this.right, 1f - this.top)
        horizontal -> RectF(1f - this.right, this.top, 1f - this.left, this.bottom)
        else -> this
    }
}

internal fun shrinkEdgeCoord(coord: Float, smaller: Boolean): Float {
    if (!smaller || coord <= 0f || coord >= 1f) return coord
    return when {
        coord < 0.5f -> coord * 0.75f
        coord > 0.5f -> 1f - (1f - coord) * 0.75f
        else -> coord
    }
}
