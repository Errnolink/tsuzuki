package dev.errnolink.tsuzuki.ui.reader


enum class SettingControl { Segmented, MenuRow }

const val SegmentedTrackPadDp = 2f
const val SegmentedCellPadDp = 10f

fun segmentedFits(labels: List<String>, availableDp: Float, measure: (String) -> Float): Boolean {
    if (labels.size < 2 || labels.size > 3) return false
    val cellDp = (availableDp - SegmentedTrackPadDp * 2f - SegmentedCellPadDp * 2f * labels.size) / labels.size
    return cellDp > 0f && labels.all { measure(it) <= cellDp }
}

fun settingControl(options: List<String>, availableDp: Float, measure: (String) -> Float): SettingControl =
    if (segmentedFits(options, availableDp, measure)) SettingControl.Segmented else SettingControl.MenuRow

fun chapterListInitialScroll(currentIndex: Int, rowCount: Int): Int = when {
    rowCount <= 0 -> 0
    currentIndex <= 0 -> 0
    else -> (currentIndex - 1).coerceAtMost(rowCount - 1)
}

