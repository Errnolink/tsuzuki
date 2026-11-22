package dev.errnolink.tsuzuki.reader

import androidx.compose.ui.graphics.Color

/**
 * The reader's canvas (page background), resolved once and shared by the native reader container,
 * the cold-open loading surface and the open transition's veil, so the growing shape, the loading
 * state and the page all sit on the same colour (reader theme values: 0 white, 1 black, 2 grey,
 * 3 automatic by system dark mode).
 */
fun readerCanvasDark(readerTheme: Int, systemDark: Boolean): Boolean = when (readerTheme) {
    0 -> false
    3 -> systemDark
    else -> true
}

fun readerCanvasArgb(readerTheme: Int, systemDark: Boolean): Int = when (readerTheme) {
    0 -> CanvasWhite
    2 -> CanvasGray
    3 -> if (systemDark) CanvasGray else CanvasWhite
    else -> CanvasBlack
}

fun readerCanvasColor(readerTheme: Int, systemDark: Boolean): Color = Color(readerCanvasArgb(readerTheme, systemDark))

private const val CanvasWhite = 0xFFFFFFFF.toInt()
private const val CanvasGray = 0xFF202125.toInt()
private const val CanvasBlack = 0xFF000000.toInt()
