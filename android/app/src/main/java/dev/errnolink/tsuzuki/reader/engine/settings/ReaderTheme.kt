// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): eu/kanade/tachiyomi/ui/reader/settings/ReaderTheme.kt
package dev.errnolink.tsuzuki.reader.engine.settings

import android.graphics.Color as AndroidColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color as ComposeColor
import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR

enum class ReaderTheme(
    val prefValue: Int,
    val stringRes: StringResource,
    val settingsStringRes: StringResource = stringRes,
) {
    WHITE(0, MR.strings.white_background),
    BLACK(1, MR.strings.black_background),
    SMART_BY_PAGE(2, MR.strings.tsuzuki_smart_by_page, MR.strings.tsuzuki_smart_based_on_page),
    SMART_BY_THEME(
        3,
        MR.strings.tsuzuki_smart_by_theme,
        MR.strings.tsuzuki_smart_based_on_page_and_theme,
    ),
    SMART_BY_THEME_BUT_BLACK(
        4,
        MR.strings.tsuzuki_smart_by_theme_but_black,
        MR.strings.tsuzuki_smart_based_on_page_and_theme_use_black,
    );

    val isSmart: Boolean
        get() = this == SMART_BY_PAGE || this == SMART_BY_THEME || this == SMART_BY_THEME_BUT_BLACK

    fun color(themeBackground: ComposeColor): ComposeColor {
        return when (this) {
            WHITE -> ComposeColor.White
            BLACK,
            SMART_BY_THEME_BUT_BLACK -> ComposeColor.Black
            SMART_BY_PAGE,
            SMART_BY_THEME -> themeBackground
        }
    }

    @Composable
    @ReadOnlyComposable
    fun color(): ComposeColor = color(MaterialTheme.colorScheme.background)

    fun androidColor(isDark: Boolean): Int {
        return when (this) {
            WHITE -> AndroidColor.WHITE
            BLACK,
            SMART_BY_THEME_BUT_BLACK -> AndroidColor.BLACK
            SMART_BY_PAGE,
            SMART_BY_THEME -> if (isDark) AndroidColor.BLACK else AndroidColor.WHITE
        }
    }

    companion object {
        fun fromPreference(preference: Int?): ReaderTheme =
            entries.find { it.prefValue == preference } ?: SMART_BY_PAGE
    }
}
