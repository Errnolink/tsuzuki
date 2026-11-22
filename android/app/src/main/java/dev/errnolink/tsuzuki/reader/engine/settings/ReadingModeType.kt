// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): eu/kanade/tachiyomi/ui/reader/settings/ReadingModeType.kt
package dev.errnolink.tsuzuki.reader.engine.settings

import androidx.annotation.DrawableRes
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.R
import tachiyomi.i18n.MR

enum class ReadingModeType(
    val prefValue: Int,
    val stringRes: StringResource,
    @DrawableRes val iconRes: Int,
    val flagValue: Int = prefValue shl ReadingModeType.SHIFT,
) {
    DEFAULT(0, MR.strings.label_default, R.drawable.ic_reader_default_24dp),
    LEFT_TO_RIGHT(1, MR.strings.left_to_right_viewer, R.drawable.ic_reader_ltr_24dp),
    RIGHT_TO_LEFT(2, MR.strings.right_to_left_viewer, R.drawable.ic_reader_rtl_24dp),
    VERTICAL(3, MR.strings.vertical_viewer, R.drawable.ic_reader_vertical_24dp),
    WEBTOON(4, MR.strings.webtoon_viewer, R.drawable.ic_reader_webtoon_24dp);

    companion object {
        fun fromPreference(preference: Int): ReadingModeType =
            entries.find { it.flagValue == preference } ?: DEFAULT

        private const val SHIFT = 0x00000000
        const val MASK = 7 shl SHIFT

        fun getNextReadingMode(preference: Int): ReadingModeType {
            val current = fromPreference(preference)
            return current.next()
        }

        fun isPagerType(preference: Int): Boolean {
            val mode = fromPreference(preference)
            return mode == LEFT_TO_RIGHT || mode == RIGHT_TO_LEFT || mode == VERTICAL
        }

        fun isWebtoonType(preference: Int): Boolean {
            val mode = fromPreference(preference)
            return mode == WEBTOON
        }

        fun fromSpinner(position: Int?) =
            entries.find { value -> value.prefValue == position } ?: DEFAULT

        private fun ReadingModeType.next(): ReadingModeType =
            entries[(ordinal + 1) % entries.size]
    }
}
