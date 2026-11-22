package dev.errnolink.tsuzuki.ui.reader

import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.contrastRatio
import dev.errnolink.tsuzuki.preference.TsuzukiPreferences
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import androidx.compose.runtime.CompositionLocalProvider
import dev.errnolink.tsuzuki.designsystem.GlassMaterial
import dev.errnolink.tsuzuki.designsystem.LocalGlassTintStrength

/**
 * The reader chrome's scheme follows the canvas (the page background), not the app theme: on a
 * white page the bars are light with dark ink, on a black page dark with light ink — the same
 * glass material as the rest of the app, so labels keep 4.5:1 on both extremes instead of the
 * old grey-on-white chrome (U2).
 */
@Composable
fun ReaderChromeTheme(canvasDark: Boolean, content: @Composable () -> Unit) {
    val wallpaper by remember { Injekt.get<TsuzukiPreferences>() }.coloursFromWallpaper().collectAsState()
    TsuzukiTheme(dark = canvasDark, wallpaperColors = wallpaper) {
        val strength = GlassMaterial.chromeOverContentStrength(LocalGlassTintStrength.current)
        CompositionLocalProvider(LocalGlassTintStrength provides strength) { content() }
    }
}

/** The fill a glass tint makes over the page it floats on, at [alpha] tint strength. */
fun glassFillOver(tint: Color, page: Color, alpha: Float): Color = lerp(page, tint, alpha)

/**
 * Interactive chrome colours (the scrub fill) need 3:1 against the glass they sit on; an accent
 * that doesn't read (light on light, maroon on grey) falls back to the label colour.
 */
fun inkOnGlass(candidate: Color, ink: Color, tint: Color): Color =
    if (contrastRatio(candidate, tint) >= 3f) candidate else ink
