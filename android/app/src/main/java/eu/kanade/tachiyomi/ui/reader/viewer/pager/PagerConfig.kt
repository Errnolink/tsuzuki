package eu.kanade.tachiyomi.ui.reader.viewer.pager

/**
 * Shared layout constants preserved for reader settings after legacy View viewers were retired in C7.
 */
object PagerConfig {

    object CenterMarginType {
        const val NONE = 0
        const val DOUBLE_PAGE_CENTER_MARGIN = 1
        const val WIDE_PAGE_CENTER_MARGIN = 2
        const val DOUBLE_AND_WIDE_CENTER_MARGIN = 3
    }

    object PageLayout {
        const val SINGLE_PAGE = 0
        const val DOUBLE_PAGES = 1
        const val AUTOMATIC = 2
    }
}
