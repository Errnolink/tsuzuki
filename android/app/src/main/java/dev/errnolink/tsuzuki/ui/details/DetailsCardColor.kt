package dev.errnolink.tsuzuki.ui.details

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import dev.errnolink.tsuzuki.designsystem.LocalTsuzukiColors
import dev.errnolink.tsuzuki.designsystem.LocalTsuzukiContentColor
import dev.errnolink.tsuzuki.designsystem.tsuzukiCardColors
import dev.errnolink.tsuzuki.ui.shell.MangaHero

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

@Composable
fun DetailsCardTheme(mangaId: Long, content: @Composable () -> Unit) {
    val card = MangaHero.cardColor(mangaId)
    if (card == null) {
        content()
        return
    }
    val colors = remember(card) { tsuzukiCardColors(card) }
    CompositionLocalProvider(
        LocalTsuzukiColors provides colors,
        LocalTsuzukiContentColor provides colors.text,
        androidx.compose.material3.LocalContentColor provides colors.text,
        content = content,
    )
}
