// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): eu/kanade/tachiyomi/ui/reader/viewer/navigation/DisabledNavigation.kt
package dev.errnolink.tsuzuki.reader.engine.viewer.navigation

import dev.errnolink.tsuzuki.reader.engine.viewer.ViewerNavigation

/**
 * Visualization of default state without any inversion +---+---+---+ | M | M | M | P: Previous
 * +---+---+---+ | M | M | M | M: Menu +---+---+---+ | M | M | M | N: Next +---+---+---+
 */
class DisabledNavigation : ViewerNavigation() {

    override var regions: List<Region> = emptyList()
}
