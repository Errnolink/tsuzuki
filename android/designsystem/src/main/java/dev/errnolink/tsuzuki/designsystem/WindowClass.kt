package dev.errnolink.tsuzuki.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

enum class WindowWidthClass { Compact, Medium, Expanded }

fun windowWidthClass(widthDp: Float): WindowWidthClass =
    when {
        widthDp < 600f -> WindowWidthClass.Compact
        widthDp < 840f -> WindowWidthClass.Medium
        else -> WindowWidthClass.Expanded
    }

val LocalWindowWidthClass = staticCompositionLocalOf { WindowWidthClass.Compact }

@Composable
fun rememberWindowWidthClass(): WindowWidthClass {
    val info = LocalWindowInfo.current
    val density = LocalDensity.current
    return remember(info.containerSize.width, density.density) {
        windowWidthClass(info.containerSize.width / density.density)
    }
}
