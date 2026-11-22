package dev.errnolink.tsuzuki.ui.library

import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import dev.errnolink.tsuzuki.designsystem.WindowWidthClass
import dev.errnolink.tsuzuki.ui.next.NextLibraryAutoCellWidth
import tachiyomi.domain.library.model.LibraryDisplayMode
import kotlin.math.floor

private const val MinFixedCellWidthDp = 180f

fun libraryColumns(
    setting: Int,
    contentWidthDp: Float,
    widthClass: WindowWidthClass,
    displayMode: LibraryDisplayMode,
): StaggeredGridCells =
    when {
        displayMode == LibraryDisplayMode.List -> StaggeredGridCells.Fixed(1)
        setting <= 0 -> StaggeredGridCells.Adaptive(NextLibraryAutoCellWidth(displayMode))
        widthClass == WindowWidthClass.Compact -> StaggeredGridCells.Fixed(setting)
        else -> StaggeredGridCells.Fixed(maxOf(setting, floor(contentWidthDp / MinFixedCellWidthDp).toInt()))
    }
