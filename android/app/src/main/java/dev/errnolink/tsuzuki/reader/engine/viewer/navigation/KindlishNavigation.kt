// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): eu/kanade/tachiyomi/ui/reader/viewer/navigation/KindlishNavigation.kt
package dev.errnolink.tsuzuki.reader.engine.viewer.navigation

import android.graphics.RectF
import dev.errnolink.tsuzuki.reader.engine.viewer.ViewerNavigation

/**
 * Visualization of default state without any inversion +---+---+---+ | M | M | M | P: Previous
 * +---+---+---+ | P | N | N | M: Menu +---+---+---+ | P | N | N | N: Next +---+---+---+
 */
class KindlishNavigation : ViewerNavigation() {

    override var regions: List<Region> =
        listOf(
            Region(rectF = RectF(0.33f, 0.33f, 1f, 1f), type = NavigationRegion.NEXT),
            Region(rectF = RectF(0f, 0.33f, 0.33f, 1f), type = NavigationRegion.PREV),
        )
}
