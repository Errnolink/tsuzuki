package dev.errnolink.tsuzuki.ui.library

import dev.errnolink.tsuzuki.designsystem.PullDownTriState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryGroup
import tachiyomi.domain.library.model.LibrarySort

class LibraryTopRowLogicTest {

    @Test
    fun `tri-state cycles correctly through off, include, exclude, off`() {
        var state = TriState.DISABLED
        state = nextFilterState(state)
        assertEquals(TriState.ENABLED_IS, state)

        state = nextFilterState(state)
        assertEquals(TriState.ENABLED_NOT, state)

        state = nextFilterState(state)
        assertEquals(TriState.DISABLED, state)
    }

    @Test
    fun `tri-state maps to pull-down segments and back`() {
        assertEquals(PullDownTriState.Off, TriState.DISABLED.toPullDownTriState())
        assertEquals(PullDownTriState.Include, TriState.ENABLED_IS.toPullDownTriState())
        assertEquals(PullDownTriState.Exclude, TriState.ENABLED_NOT.toPullDownTriState())

        assertEquals(TriState.DISABLED, PullDownTriState.Off.toTriState())
        assertEquals(TriState.ENABLED_IS, PullDownTriState.Include.toTriState())
        assertEquals(TriState.ENABLED_NOT, PullDownTriState.Exclude.toTriState())

        TriState.entries.forEach { state ->
            assertEquals(state, state.toPullDownTriState().toTriState())
        }
    }

    @Test
    fun `sort direction flip toggles ascending and descending without changing type`() {
        val ascending = LibrarySort(LibrarySort.Type.LatestChapter, LibrarySort.Direction.Ascending)
        val flipped = ascending.flipped()
        assertEquals(LibrarySort.Type.LatestChapter, flipped.type)
        assertFalse(flipped.isAscending)
        assertEquals(ascending, flipped.flipped())
    }

    @Test
    fun `reset row is hidden at defaults and shown when any option differs`() {
        val defaults = LibraryMenuOptionsState()
        assertTrue(libraryOptionsAtDefaults(defaults))
        assertTrue(libraryOptionsAtDefaults(LibraryMenuOptionsState(quickFilterStates = List(QuickFilterItem.entries.size) { TriState.DISABLED })))
        assertTrue(libraryOptionsAtDefaults(LibraryMenuOptionsState(trackedStates = emptyList())))
        assertTrue(libraryOptionsAtDefaults(LibraryMenuOptionsState(trackedStates = listOf(TriState.DISABLED, TriState.DISABLED))))

        assertFalse(libraryOptionsAtDefaults(defaults.copy(quickFilterStates = defaults.quickFilterStates.toMutableList().also { it[0] = TriState.ENABLED_IS })))
        assertFalse(libraryOptionsAtDefaults(defaults.copy(trackedStates = listOf(TriState.ENABLED_NOT))))
        assertFalse(libraryOptionsAtDefaults(defaults.copy(lewd = TriState.ENABLED_IS)))
        assertFalse(libraryOptionsAtDefaults(defaults.copy(intervalCustom = TriState.ENABLED_IS)))
        assertFalse(libraryOptionsAtDefaults(defaults.copy(categoriesFiltered = true)))
        assertFalse(libraryOptionsAtDefaults(defaults.copy(categoryIncludeIds = setOf("1"))))
        assertFalse(libraryOptionsAtDefaults(defaults.copy(categoryExcludeIds = setOf("2"))))
        assertFalse(libraryOptionsAtDefaults(defaults.copy(scanlatorMatchAll = true)))
        assertFalse(libraryOptionsAtDefaults(defaults.copy(sortType = LibrarySort.Type.LatestChapter)))
        assertFalse(libraryOptionsAtDefaults(defaults.copy(sortAscending = false)))
        assertFalse(libraryOptionsAtDefaults(defaults.copy(displayMode = LibraryDisplayMode.List)))
        assertFalse(libraryOptionsAtDefaults(defaults.copy(grouping = LibraryGroup.BY_SOURCE)))
        assertFalse(libraryOptionsAtDefaults(defaults.copy(portraitColumns = 3)))
        assertFalse(libraryOptionsAtDefaults(defaults.copy(landscapeColumns = 1)))
        assertFalse(libraryOptionsAtDefaults(defaults.copy(downloadBadge = true)))
        assertFalse(libraryOptionsAtDefaults(defaults.copy(unreadBadge = false)))
        assertFalse(libraryOptionsAtDefaults(defaults.copy(localBadge = false)))
        assertFalse(libraryOptionsAtDefaults(defaults.copy(languageBadge = false)))
        assertFalse(libraryOptionsAtDefaults(defaults.copy(useLangIcon = false)))
        assertFalse(libraryOptionsAtDefaults(defaults.copy(sourceBadge = false)))
        assertFalse(libraryOptionsAtDefaults(defaults.copy(continueReadingButton = true)))
        assertFalse(libraryOptionsAtDefaults(defaults.copy(showHiddenCategories = true)))
    }

    @Test
    fun `collapse state machine stays collapsed on partial scroll-up`() {
        val sm = LibraryTopRowStateMachine(collapseThreshold = 8f, hysteresis = 24f)
        assertTrue(sm.isExpanded)
        assertFalse(sm.isCollapsed)

        // Small scroll <= 8dp does not collapse
        sm.onScroll(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 5f)
        assertTrue(sm.isExpanded)

        // Scrolled > 8dp collapses
        sm.onScroll(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 50f)
        assertTrue(sm.isCollapsed)
        assertFalse(sm.isExpanded)

        // Partial scroll up stays collapsed
        sm.onScroll(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 20f)
        assertTrue(sm.isCollapsed)
        assertFalse(sm.isExpanded)
    }

    @Test
    fun `collapse state machine expands on tap of collapsed pill`() {
        val sm = LibraryTopRowStateMachine(collapseThreshold = 8f, hysteresis = 24f)
        sm.onScroll(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 50f)
        assertTrue(sm.isCollapsed)

        // Tap to expand
        sm.onTapCollapsedPill(currentScrollOffset = 50f)
        assertTrue(sm.isExpanded)
        assertFalse(sm.isCollapsed)

        // Scrolling within 24dp hysteresis stays expanded
        sm.onScroll(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 60f)
        assertTrue(sm.isExpanded)

        // Scrolling beyond 24dp hysteresis collapses again
        sm.onScroll(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 80f)
        assertTrue(sm.isCollapsed)
    }

    @Test
    fun `collapse state machine expands when scroll reaches top`() {
        val sm = LibraryTopRowStateMachine(collapseThreshold = 8f, hysteresis = 24f)
        sm.onScroll(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 50f)
        assertTrue(sm.isCollapsed)

        // Reaching top (index == 0 && offset == 0) expands
        sm.onScroll(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0f)
        assertTrue(sm.isExpanded)
        assertFalse(sm.isCollapsed)
    }

    @Test
    fun `collapse state machine applies 24dp hysteresis to avoid flapping near top`() {
        val sm = LibraryTopRowStateMachine(collapseThreshold = 8f, hysteresis = 24f)
        sm.onScroll(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 50f)
        assertTrue(sm.isCollapsed)

        // Reach top -> expands
        sm.onScroll(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0f)
        assertTrue(sm.isExpanded)

        // Small scroll down within 24dp hysteresis does not collapse (anti-flapping)
        sm.onScroll(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 16f)
        assertTrue(sm.isExpanded)

        // Scrolling beyond 24dp collapses
        sm.onScroll(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 25f)
        assertTrue(sm.isCollapsed)
    }

    @Test
    fun `hoisted-state identity preserves LazyListState and LazyGridState instances`() {
        val holder = ShellTabScrollStates()
        val cat1GridA = holder.libraryGridStateFor(1L)
        val cat1GridB = holder.libraryGridStateFor(1L)
        val cat2Grid = holder.libraryGridStateFor(2L)

        assertSame(cat1GridA, cat1GridB)
        assertNotSame(cat1GridA, cat2Grid)

        val updatesA = holder.updatesListState
        val updatesB = holder.updatesListState
        assertSame(updatesA, updatesB)

        val historyA = holder.historyListState
        val historyB = holder.historyListState
        assertSame(historyA, historyB)
    }

    @Test
    fun `active filter count sums non-disabled quick filters`() {
        assertEquals(0, calculateActiveFilterCount(emptyList()))

        val states = listOf(
            TriState.ENABLED_IS,
            TriState.ENABLED_NOT,
            TriState.DISABLED,
            TriState.DISABLED,
            TriState.ENABLED_IS,
        )
        assertEquals(3, calculateActiveFilterCount(states))
    }

    @Test
    fun `badge count covers every filter kind, categories only once`() {
        // Quick tri-states only.
        assertEquals(
            2,
            activeFilterCount(
                LibraryMenuOptionsState(
                    quickFilterStates = listOf(TriState.ENABLED_IS, TriState.DISABLED, TriState.ENABLED_NOT),
                ),
            ),
        )

        // Tracker filters, lewd, interval, categories and scanlator match-all each add one;
        // the category include/exclude set sizes do not multiply the count.
        val full = LibraryMenuOptionsState(
            quickFilterStates = listOf(TriState.ENABLED_IS),
            trackedStates = listOf(TriState.DISABLED, TriState.ENABLED_IS),
            lewd = TriState.ENABLED_NOT,
            intervalCustom = TriState.ENABLED_IS,
            categoriesFiltered = true,
            categoryIncludeIds = setOf("a", "b"),
            categoryExcludeIds = setOf("c"),
            scanlatorMatchAll = true,
        )
        assertEquals(6, activeFilterCount(full))

        // The category toggle alone counts once, include or exclude side empty.
        assertEquals(1, activeFilterCount(LibraryMenuOptionsState(categoryExcludeIds = setOf("x"))))
    }

    @Test
    fun `filter count formats single digits and overflows at 9+`() {
        assertEquals("1", formatFilterCount(1))
        assertEquals("5", formatFilterCount(5))
        assertEquals("9", formatFilterCount(9))
        assertEquals("9+", formatFilterCount(10))
        assertEquals("9+", formatFilterCount(15))
    }

    @Test
    fun `search state open, update, submit, and close routing`() {
        dev.errnolink.tsuzuki.ui.shell.ShellSearch.close()
        assertNull(dev.errnolink.tsuzuki.ui.shell.ShellSearch.query.value)

        dev.errnolink.tsuzuki.ui.shell.ShellSearch.open("manga")
        assertEquals("manga", dev.errnolink.tsuzuki.ui.shell.ShellSearch.query.value)

        dev.errnolink.tsuzuki.ui.shell.ShellSearch.update("one piece")
        assertEquals("one piece", dev.errnolink.tsuzuki.ui.shell.ShellSearch.query.value)

        var submittedQuery: String? = null
        dev.errnolink.tsuzuki.ui.shell.ShellSearch.onSubmit = { submittedQuery = it }
        dev.errnolink.tsuzuki.ui.shell.ShellSearch.submit("one piece")
        assertEquals("one piece", submittedQuery)

        dev.errnolink.tsuzuki.ui.shell.ShellSearch.close()
        assertNull(dev.errnolink.tsuzuki.ui.shell.ShellSearch.query.value)
    }

    @Test
    fun `menu pull down is a standalone state holder`() {
        LibraryMenuPullDownState.close()
        assertFalse(LibraryMenuPullDownState.isOpen.value)

        LibraryMenuPullDownState.open()
        assertTrue(LibraryMenuPullDownState.isOpen.value)

        // Toggling flips it
        LibraryMenuPullDownState.toggle()
        assertFalse(LibraryMenuPullDownState.isOpen.value)

        // Closing menu closes it
        LibraryMenuPullDownState.open()
        LibraryMenuPullDownState.close()
        assertFalse(LibraryMenuPullDownState.isOpen.value)
    }

    @Test
    fun `primary and secondary quick filters partition every quick filter item without overlap`() {
        val combined = PrimaryQuickFilters + SecondaryQuickFilters
        assertEquals(QuickFilterItem.entries.size, combined.size)
        assertEquals(QuickFilterItem.entries.toSet(), combined.toSet())
    }

    @Test
    fun `more filters summary surfaces active secondary quick filters alongside extra filters`() {
        val empty = moreFiltersSummary(
            secondaryActive = emptyList(),
            trackedActive = false,
            lewd = TriState.DISABLED,
            categories = false,
            intervalCustom = TriState.DISABLED,
            intervalVisible = true,
            scanlatorMatchAll = false,
        )
        assertTrue(empty.isEmpty())

        val active = moreFiltersSummary(
            secondaryActive = listOf(QuickFilterItem.Completed.label, QuickFilterItem.Missing.label),
            trackedActive = true,
            lewd = TriState.ENABLED_IS,
            categories = true,
            intervalCustom = TriState.ENABLED_NOT,
            intervalVisible = true,
            scanlatorMatchAll = true,
        )
        assertEquals(7, active.size)
        assertEquals(QuickFilterItem.Completed.label, active[0])
        assertEquals(QuickFilterItem.Missing.label, active[1])
    }
}
