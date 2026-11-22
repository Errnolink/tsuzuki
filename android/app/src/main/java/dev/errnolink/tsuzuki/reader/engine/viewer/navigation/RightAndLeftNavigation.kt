// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): eu/kanade/tachiyomi/ui/reader/viewer/navigation/RightAndLeftNavigation.kt
package dev.errnolink.tsuzuki.reader.engine.viewer.navigation

import android.graphics.RectF
import dev.errnolink.tsuzuki.reader.engine.viewer.ViewerNavigation

/**
 * Visualization of default state without any inversion +---+---+---+ | N | M | P | P: Move Right
 * +---+---+---+ | N | M | P | M: Menu +---+---+---+ | N | M | P | N: Move Left +---+---+---+
 */
class RightAndLeftNavigation : ViewerNavigation() {

    override var regions: List<Region> =
        listOf(
            Region(rectF = RectF(0f, 0f, 0.25f, 1f), type = NavigationRegion.LEFT),
            Region(rectF = RectF(0.75f, 0f, 1f, 1f), type = NavigationRegion.RIGHT),
        )
}
