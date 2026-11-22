// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): eu/kanade/tachiyomi/ui/reader/settings/ReaderBottomButton.kt
package dev.errnolink.tsuzuki.reader.engine.settings

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR

enum class ReaderBottomButton(val value: String, val stringRes: StringResource) {
    ViewChapters("vc", MR.strings.action_view_chapters),
    WebView("wb", MR.strings.action_open_in_web_view),
    Comment("com", MR.strings.tsuzuki_comments),
    ReadingMode("rm", MR.strings.pref_category_reading_mode),
    Rotation("rot", MR.strings.rotation_type),
    CropBordersPaged("cbp", SYMR.strings.pref_crop_borders_pager),
    CropBordersWebtoon("cbw", SYMR.strings.pref_crop_borders_webtoon),
    Grayscale("gs", MR.strings.pref_grayscale),
    PageLayout("pl", SYMR.strings.page_layout),
    ShiftDoublePage("sdp", SYMR.strings.shift_double_pages);

    fun isIn(buttons: Collection<String>) = value in buttons

    companion object {
        val BUTTONS_DEFAULTS =
            setOf(ViewChapters, Comment, ShiftDoublePage).map { it.value }.toSet()
    }
}
