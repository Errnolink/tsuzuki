// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): eu/kanade/tachiyomi/ui/reader/settings/PageLayout.kt
package dev.errnolink.tsuzuki.reader.engine.settings

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR

enum class PageLayout(
    val value: Int,
    val webtoonValue: Int,
    val stringRes: StringResource,
    private val _fullStringRes: StringResource? = null,
) {
    SINGLE_PAGE(0, 0, SYMR.strings.single_page),
    DOUBLE_PAGES(1, 2, SYMR.strings.double_pages),
    AUTOMATIC(2, 3, MR.strings.tsuzuki_automatic, SYMR.strings.automatic_orientation),
    SPLIT_PAGES(3, 1, MR.strings.tsuzuki_split_double_pages);

    val fullStringRes: StringResource = _fullStringRes ?: stringRes

    companion object {
        fun fromPreference(preference: Int): PageLayout =
            values().find { it.value == preference } ?: SINGLE_PAGE
    }
}
