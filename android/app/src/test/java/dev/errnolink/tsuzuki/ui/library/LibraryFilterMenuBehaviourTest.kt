package dev.errnolink.tsuzuki.ui.library

import dev.errnolink.tsuzuki.designsystem.PullDownTriState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibrarySort

/** The compact filter menu's pure behaviour: segment mapping and the sort row's tap rule. */
class LibraryFilterMenuBehaviourTest {

    @Test
    fun `the layout segments carry every display type the app has`() {
        assertEquals(
            listOf(
                LibraryDisplayMode.CompactGrid,
                LibraryDisplayMode.ComfortableGrid,
                LibraryDisplayMode.ComfortableGridPanorama,
                LibraryDisplayMode.List,
                LibraryDisplayMode.CoverOnlyGrid,
            ),
            displayPaletteModes,
        )
    }

    @Test
    fun `the columns stepper clamps to the 0-10 range`() {
        assertEquals(0, columnsStepped(0, -1))
        assertEquals(10, columnsStepped(10, 1))
        assertEquals(3, columnsStepped(2, 1))
        assertEquals(7, columnsStepped(8, -1))
    }
    @Test
    fun `tapping the current sort flips its direction`() {
        val current = LibrarySort(LibrarySort.Type.Alphabetical, LibrarySort.Direction.Ascending)
        val tapped = sortTapTarget(current, LibrarySort.Type.Alphabetical)
        assertEquals(LibrarySort.Type.Alphabetical, tapped.type)
        assertEquals(LibrarySort.Direction.Descending, tapped.direction)
    }

    @Test
    fun `tapping another sort keeps the current direction`() {
        val current = LibrarySort(LibrarySort.Type.LastRead, LibrarySort.Direction.Descending)
        val tapped = sortTapTarget(current, LibrarySort.Type.DateAdded)
        assertEquals(LibrarySort.Type.DateAdded, tapped.type)
        assertEquals(LibrarySort.Direction.Descending, tapped.direction)
    }

    @Test
    fun `tri-state rows cycle off, include, exclude, off`() {
        var state = PullDownTriState.Off
        state = nextFilterState(state.toTriState()).toPullDownTriState()
        assertEquals(PullDownTriState.Include, state)
        state = nextFilterState(state.toTriState()).toPullDownTriState()
        assertEquals(PullDownTriState.Exclude, state)
        state = nextFilterState(state.toTriState()).toPullDownTriState()
        assertEquals(PullDownTriState.Off, state)
        // The library count and the badge agree that only non-off states are active.
        assertEquals(
            2,
            calculateActiveFilterCount(listOf(TriState.ENABLED_IS, TriState.ENABLED_NOT, TriState.DISABLED)),
        )
    }

    @Test
    fun `primary quick filters carry leading icons while secondary filters stay iconless`() {
        assertEquals(
            listOf(
                QuickFilterItem.Unread,
                QuickFilterItem.Downloaded,
                QuickFilterItem.Started,
                QuickFilterItem.Bookmarked,
            ),
            PrimaryQuickFilters,
        )
        for (filter in PrimaryQuickFilters) {
            assertNotNull(filter.menuIcon(), "missing icon for $filter")
        }
        for (filter in SecondaryQuickFilters) {
            assertNull(filter.menuIcon(), "unexpected icon for $filter")
        }
    }
}
