// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): eu/kanade/tachiyomi/ui/reader/settings/ReaderSliderPosition.kt
package dev.errnolink.tsuzuki.reader.engine.settings

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR

enum class ReaderSliderPosition(
    val prefValue: Int,
    val stringRes: StringResource,
) {
    LEFT(0, MR.strings.tsuzuki_slider_position_left),
    HORIZONTAL(1, MR.strings.tsuzuki_slider_position_horizontal),
    RIGHT(2, MR.strings.tsuzuki_slider_position_right);

    companion object {
        val DEFAULT = RIGHT

        fun fromPreference(preference: Int?): ReaderSliderPosition =
            entries.find { it.prefValue == preference } ?: DEFAULT

        fun fromSpinner(position: Int?): ReaderSliderPosition =
            entries.find { it.prefValue == position } ?: DEFAULT
    }
}
