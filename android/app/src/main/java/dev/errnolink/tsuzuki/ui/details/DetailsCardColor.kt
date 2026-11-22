package dev.errnolink.tsuzuki.ui.details

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import dev.errnolink.tsuzuki.designsystem.M3Motion
import dev.errnolink.tsuzuki.designsystem.LocalTsuzukiColors
import dev.errnolink.tsuzuki.designsystem.LocalTsuzukiContentColor
import dev.errnolink.tsuzuki.designsystem.expressiveCoverColors
import dev.errnolink.tsuzuki.designsystem.lerpColors
import dev.errnolink.tsuzuki.ui.motion.LocalNavigationProgress
import dev.errnolink.tsuzuki.ui.motion.MangaHero

object DetailsCardColor {

    const val MinWhiteContrast = 6f

    fun forDominant(dominant: Color): Color {
        var candidate = dominant.copy(alpha = 1f)
        repeat(48) {
            if (ContinueActionColor.whiteContrast(candidate) >= MinWhiteContrast) return candidate
            candidate = Color(candidate.red * 0.92f, candidate.green * 0.92f, candidate.blue * 0.92f, 1f)
        }
        return candidate
    }

    fun fromBitmap(source: Bitmap): Color? {
        val readable = if (source.config == Bitmap.Config.HARDWARE) source.copy(Bitmap.Config.ARGB_8888, false) else source
        val palette = Palette.from(readable).maximumColorCount(16).generate()
        val swatch = palette.vibrantSwatch ?: palette.darkVibrantSwatch ?: palette.dominantSwatch ?: return null
        return forDominant(Color(swatch.rgb))
    }
}

/**
 * Colours for a cover-tinted scope (title page, reader chrome): the seed swatch becomes a full
 * scheme via expressiveScheme (light and dark both covered), and the switch from the app scheme
 * is visible — 400 ms on M3 standard easing, riding the page transition (LocalNavigationProgress
 * drops below 1f while the page closes or is dragged away, and returns on a cancelled gesture).
 */
@Composable
fun CoverTheme(seed: Color?, content: @Composable () -> Unit) {
    val appColors = LocalTsuzukiColors.current
    if (seed == null) {
        content()
        return
    }
    val dark = appColors.isDark
    val target = remember(seed, dark) { expressiveCoverColors(seed, dark) }
    // DET-04: a page that already knows its seed opens at the cover scheme — the lerp only runs
    // when the seed arrives later (palette computed on the fly).
    val fraction = remember(seed) { Animatable(if (seed != null) 1f else 0f) }
    val navigationProgress = LocalNavigationProgress.current
    LaunchedEffect(seed, navigationProgress) {
        fraction.animateTo(1f, tween(M3Motion.Medium4, easing = M3Motion.Standard))
        snapshotFlow { navigationProgress() < 1f }.collect { leaving ->
            fraction.animateTo(if (leaving) 0f else 1f, tween(M3Motion.Medium4, easing = M3Motion.Standard))
        }
    }
    val colors = lerpColors(appColors, target, fraction.value)
    CompositionLocalProvider(
        LocalTsuzukiColors provides colors,
        LocalTsuzukiContentColor provides colors.text,
        androidx.compose.material3.LocalContentColor provides colors.text,
        content = content,
    )
}

@Composable
fun DetailsCardTheme(mangaId: Long, content: @Composable () -> Unit) {
    CoverTheme(seed = MangaHero.cardColor(mangaId), content = content)
}
