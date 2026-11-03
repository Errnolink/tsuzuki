package eu.kanade.tachiyomi.ui.manga

import androidx.annotation.ColorInt
import androidx.compose.runtime.Composable
import dev.errnolink.tsuzuki.ui.manga.PaletteScreen as TsuzukiPaletteScreen
import eu.kanade.presentation.util.Screen

/**
 * A screen that displays a colors palette of current theme.
 */
class PaletteScreen(
    @param:ColorInt private val seedColor: Int?,
) : Screen() {

    @Composable
    override fun Content() {
        TsuzukiPaletteScreen(seedColor).Content()
    }
}
