package dev.errnolink.tsuzuki.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object TsuzukiShellMaterial {
    const val TintAlphaLight: Float = 0.74f
    const val TintAlphaDark: Float = 0.72f

    fun tintAlpha(isDark: Boolean): Float = if (isDark) TintAlphaDark else TintAlphaLight

    @Composable
    fun tintAlpha(): Float = tintAlpha(TsuzukiTheme.colors.isDark)

    fun surfaceColor(colors: TsuzukiColors): Color =
        colors.elevated.copy(alpha = tintAlpha(colors.isDark))

    @Composable
    fun surfaceColor(): Color = surfaceColor(TsuzukiTheme.colors)
}
