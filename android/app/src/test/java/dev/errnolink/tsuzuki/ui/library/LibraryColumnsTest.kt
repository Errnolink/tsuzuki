package dev.errnolink.tsuzuki.ui.library

import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.WindowWidthClass
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.library.model.LibraryDisplayMode

class LibraryColumnsTest {

    @Test
    fun compactKeepsTheSetting() {
        assertEquals(StaggeredGridCells.Fixed(3), libraryColumns(3, 400f, WindowWidthClass.Compact, LibraryDisplayMode.CompactGrid))
        assertEquals(StaggeredGridCells.Fixed(2), libraryColumns(2, 1200f, WindowWidthClass.Compact, LibraryDisplayMode.ComfortableGrid))
    }

    @Test
    fun wideTreatsTheSettingAsAMinimum() {
        assertEquals(StaggeredGridCells.Fixed(5), libraryColumns(3, 1000f, WindowWidthClass.Expanded, LibraryDisplayMode.CompactGrid))
        assertEquals(StaggeredGridCells.Fixed(3), libraryColumns(3, 700f, WindowWidthClass.Medium, LibraryDisplayMode.CompactGrid))
        assertEquals(StaggeredGridCells.Fixed(8), libraryColumns(6, 1500f, WindowWidthClass.Expanded, LibraryDisplayMode.ComfortableGrid))
    }

    @Test
    fun listModeIsAlwaysOneColumn() {
        assertEquals(StaggeredGridCells.Fixed(1), libraryColumns(3, 1000f, WindowWidthClass.Expanded, LibraryDisplayMode.List))
        assertEquals(StaggeredGridCells.Fixed(1), libraryColumns(4, 400f, WindowWidthClass.Compact, LibraryDisplayMode.List))
    }

    @Test
    fun zeroStaysAdaptive() {
        assertEquals(
            StaggeredGridCells.Adaptive(112.dp),
            libraryColumns(0, 1000f, WindowWidthClass.Expanded, LibraryDisplayMode.CompactGrid),
        )
        assertEquals(
            StaggeredGridCells.Adaptive(148.dp),
            libraryColumns(0, 400f, WindowWidthClass.Compact, LibraryDisplayMode.ComfortableGrid),
        )
    }
}
