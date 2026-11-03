package dev.errnolink.tsuzuki.ui.settings
import eu.kanade.presentation.more.settings.screen.SearchableSettings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SettingsReaderScreen
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

object TsuzukiSettingsReaderScreen : SearchableSettings {
    @Suppress("unused")
    private fun readResolve(): Any = TsuzukiSettingsReaderScreen

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_reader

    @Composable
    override fun Content() = TsuzukiPreferenceContent()

    val prefKeys: Set<String> = setOf(
        "pref_default_reading_mode_key",
        "pref_double_tap_anim_speed",
        "pref_show_reading_mode",
        "reader_navigation_overlay_on_start",
        "reader_navigation_smaller_tap_zone",
        "pref_force_horz_seekbar",
        "pref_show_vert_seekbar_landscape",
        "pref_left_handed_vertical_seekbar",
        "pref_default_orientation_type_key",
        "pref_reader_theme_key",
        "fullscreen",
        "cutout_short",
        "pref_keep_screen_on_key",
        "pref_show_page_number_key",
        "pref_reader_flash",
        "pref_reader_flash_duration",
        "pref_reader_flash_interval",
        "pref_reader_flash_mode",
        "skip_read",
        "skip_filtered",
        "skip_dupe",
        "always_show_chapter_transition",
        "reader_navigation_mode_pager",
        "reader_tapping_inverted",
        "pref_image_scale_type_key",
        "pref_zoom_start_key",
        "crop_borders",
        "pref_enable_transitions_pager_key",
        "landscape_zoom",
        "landscape_zoom_type",
        "paged_disable_zoom_in",
        "pref_enable_double_tap_zoom_paged",
        "navigate_pan",
        "pref_dual_page_split",
        "pref_dual_page_invert",
        "pref_dual_page_rotate",
        "pref_dual_page_rotate_invert",
        "page_layout",
        "invert_double_pages",
        "center_margin_type",
        "reader_navigation_mode_webtoon",
        "reader_tapping_inverted_webtoon",
        "pref_webtoon_scale_type_key",
        "pref_webtoon_smart_scale_long_strip_gap",
        "webtoon_side_padding",
        "reader_hide_threshold",
        "crop_borders_webtoon",
        "pref_dual_page_split_webtoon",
        "pref_dual_page_invert_webtoon",
        "pref_dual_page_rotate_webtoon",
        "pref_dual_page_rotate_invert_webtoon",
        "pref_enable_double_tap_zoom_webtoon",
        "pref_enable_pinch_to_zoom_webtoon",
        "webtoon_disable_zoom_out",
        "pref_enable_transitions_webtoon_key",
        "crop_borders_continues_vertical",
        "reader_volume_keys",
        "reader_volume_keys_inverted",
        "reader_long_tap",
        "create_folder_per_manga",
        "reader_bottom_buttons",
        "eh_preload_size",
        "eh_reader_threads",
        "eh_cache_size",
        "eh_aggressive_page_loading",
        "eh_reader_instant_retry",
        "eh_preserve_reading_position",
        "eh_use_auto_webtoon",
        "archive_reader_mode",
        "pref_beta_reader_bar_style",
    )

    @Composable
    override fun getPreferences(): List<Preference> {
        val base = SettingsReaderScreen.getPreferences()
        val groups = base.preferenceGroups()
        val loose = base.looseItems()

        fun groupAt(index: Int) = groups.getOrNull(index)

        val fork = groupAt(9)?.preferenceItems.orEmpty()
        fun forkItem(key: String) = fork.byStoreKey(key)

        fun takeKeys(items: List<Preference.PreferenceItem<out Any, out Any>>, keys: Set<String?>) =
            items.filter { it.storeKey() in keys }

        val readingMode = newGroup(
            title = "Reading mode",
            items = loose
                .retitledWhere("pref_double_tap_anim_speed", "Double-tap zoom animation")
                .subtitledWhere("reader_navigation_smaller_tap_zone", "Shrinks the tap zones that turn pages"),
        )
        val display = groupAt(0)?.withTitle("Display")
        val eink = groupAt(1)?.withTitle("E-Ink flash")
        val skipping = newGroup(
            title = "Skipping",
            items = groupAt(2)?.preferenceItems.orEmpty()
                .subtitledWhere("skip_read", "Jumps past chapters you have already read when opening a manga")
                .subtitledWhere("skip_filtered", "Jumps past chapters hidden by your filters when opening a manga")
                .subtitledWhere("skip_dupe", "Jumps past duplicate chapters when opening a manga")
                .subtitledWhere("always_show_chapter_transition", "Shows the chapter name page between chapters even when skipping"),
        )
        val pagerBase = groupAt(3)?.preferenceItems.orEmpty()
        val pagerMoved = setOf(
            "landscape_zoom", "landscape_zoom_type", "paged_disable_zoom_in", "pref_enable_double_tap_zoom_paged",
            "pref_dual_page_split", "pref_dual_page_invert", "pref_dual_page_rotate", "pref_dual_page_rotate_invert",
        )
        val pager = newGroup(
            title = "Pager",
            items = listOf<Preference>()
                .plus(pagerBase.filter { it.storeKey() !in pagerMoved })
                .retitledWhere("pref_image_scale_type_key", "Image scaling"),
        )
        val pagerZoom = newGroup(
            title = "Pager zoom",
            items = listOf<Preference>()
                .plus(
                    takeKeys(
                        pagerBase,
                        setOf("landscape_zoom", "landscape_zoom_type", "paged_disable_zoom_in", "pref_enable_double_tap_zoom_paged"),
                    ),
                )
                .subtitledWhere("paged_disable_zoom_in", "Turns off pinch and double-tap zoom in"),
        )
        val dualPages = newGroup(
            title = "Dual pages",
            items = listOf<Preference>()
                .plusNotNull(forkItem("page_layout"))
                .plus(
                    takeKeys(
                        pagerBase,
                        setOf("pref_dual_page_split", "pref_dual_page_invert", "pref_dual_page_rotate", "pref_dual_page_rotate_invert"),
                    ),
                )
                .plusNotNull(forkItem("invert_double_pages"))
                .plusNotNull(forkItem("center_margin_type"))
                .retitledWhere("center_margin_type", "Center margin")
                .subtitledWhere("invert_double_pages", "Swaps the two pages shown side by side"),
        )
        val webtoonBase = groupAt(4)?.preferenceItems.orEmpty()
        val webtoonMoved = setOf(
            "pref_dual_page_split_webtoon", "pref_dual_page_invert_webtoon", "pref_dual_page_rotate_webtoon", "pref_dual_page_rotate_invert_webtoon",
            "pref_enable_double_tap_zoom_webtoon", "pref_enable_pinch_to_zoom_webtoon", "webtoon_disable_zoom_out", "pref_enable_transitions_webtoon_key",
        )
        val webtoon = newGroup(
            title = "Webtoon",
            items = listOf<Preference>()
                .plus(webtoonBase.filter { it.storeKey() !in webtoonMoved })
                .plusNotNull(forkItem("eh_use_auto_webtoon"))
                .retitledWhere("pref_webtoon_scale_type_key", "Image scaling")
                .retitledWhere("pref_webtoon_smart_scale_long_strip_gap", "Smart scale images with gaps")
                .retitledWhere("webtoon_side_padding", "Webtoon side margins")
                .retitledWhere("reader_hide_threshold", "Chrome auto-hide delay"),
        )
        val webtoonDual = newGroup(
            title = "Webtoon dual pages",
            items = listOf<Preference>()
                .plus(
                    takeKeys(
                        webtoonBase,
                        setOf(
                            "pref_dual_page_split_webtoon",
                            "pref_dual_page_invert_webtoon",
                            "pref_dual_page_rotate_webtoon",
                            "pref_dual_page_rotate_invert_webtoon",
                        ),
                    ),
                ),
        )
        val webtoonZoom = newGroup(
            title = "Webtoon zoom & transitions",
            items = listOf<Preference>()
                .plus(
                    takeKeys(
                        webtoonBase,
                        setOf(
                            "pref_enable_double_tap_zoom_webtoon",
                            "pref_enable_pinch_to_zoom_webtoon",
                            "webtoon_disable_zoom_out",
                            "pref_enable_transitions_webtoon_key",
                        ),
                    ),
                )
                .subtitledWhere("webtoon_disable_zoom_out", "Prevents zooming out below normal size"),
        )
        val vertical = groupAt(5)?.let { group ->
            newGroup("Vertical", group.preferenceItems)
        }
        val navActions = newGroup(
            title = "Navigation & actions",
            items = listOf<Preference>()
                .plus(groupAt(6)?.preferenceItems.orEmpty())
                .plus(groupAt(7)?.preferenceItems.orEmpty())
                .plusNotNull(forkItem("reader_bottom_buttons"))
                .retitledWhere("reader_volume_keys", "Volume keys turn pages")
                .retitledWhere("reader_bottom_buttons", "Reader bottom buttons")
                .subtitledWhere("reader_volume_keys_inverted", "Swaps which volume key turns the page"),
        )
        val advanced = newGroup(
            title = "Advanced",
            items = listOf<Preference>()
                .plus(groupAt(8)?.preferenceItems.orEmpty())
                .plusNotNull(forkItem("eh_reader_instant_retry"))
                .plusNotNull(forkItem("eh_preserve_reading_position"))
                .plusNotNull(forkItem("archive_reader_mode"))
                .retitledWhere("eh_preload_size", "Preload pages ahead")
                .retitledWhere("eh_reader_threads", "Image decoder threads")
                .retitledWhere("eh_cache_size", "Page cache size")
                .retitledWhere("archive_reader_mode", "Open archives as")
                .subtitledWhere("eh_preserve_reading_position", "Reopens finished chapters at the page you left off"),
        )
        val beta = groupAt(10)?.withTitle("Beta")

        return listOfNotNull(
                readingMode,
                display,
                eink,
                skipping,
                pager,
                pagerZoom,
                dualPages,
                webtoon,
                webtoonDual,
                webtoonZoom,
                vertical,
                navActions,
                advanced,
                beta,
            )
    }
}

private fun List<Preference>.plusNotNull(item: Preference?): List<Preference> =
    if (item != null) this + item else this
