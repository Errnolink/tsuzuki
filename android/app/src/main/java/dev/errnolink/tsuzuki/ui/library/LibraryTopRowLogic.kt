package dev.errnolink.tsuzuki.ui.library

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.PullDownTriState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryGroup
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.library.service.LibraryPreferences

enum class QuickFilterItem(
    val label: String,
    val getPreference: (LibraryPreferences) -> Preference<TriState>,
) {
    Unread("Unread", LibraryPreferences::filterUnread),
    Started("Started", LibraryPreferences::filterStarted),
    Downloaded("Downloaded", LibraryPreferences::filterDownloaded),
    Bookmarked("Bookmarked", LibraryPreferences::filterBookmarked),
    Completed("Completed", LibraryPreferences::filterCompleted),
    Merged("Merged", LibraryPreferences::filterMerged),
    Missing("Missing", LibraryPreferences::filterMissingChapters),
    Unavailable("Unavailable", LibraryPreferences::filterUnavailableChapters),
}

fun nextFilterState(current: TriState): TriState = current.next()

fun TriState.toPullDownTriState(): PullDownTriState = when (this) {
    TriState.DISABLED -> PullDownTriState.Off
    TriState.ENABLED_IS -> PullDownTriState.Include
    TriState.ENABLED_NOT -> PullDownTriState.Exclude
}

fun PullDownTriState.toTriState(): TriState = when (this) {
    PullDownTriState.Off -> TriState.DISABLED
    PullDownTriState.Include -> TriState.ENABLED_IS
    PullDownTriState.Exclude -> TriState.ENABLED_NOT
}

fun LibrarySort.flipped(): LibrarySort = copy(
    direction = if (isAscending) LibrarySort.Direction.Descending else LibrarySort.Direction.Ascending,
)

fun calculateActiveFilterCount(states: Collection<TriState>): Int =
    states.count { it != TriState.DISABLED }

fun formatFilterCount(activeCount: Int): String =
    if (activeCount > 9) "9+" else activeCount.toString()

data class LibraryMenuOptionsState(
    val quickFilterStates: List<TriState> = List(QuickFilterItem.entries.size) { TriState.DISABLED },
    val trackedStates: List<TriState> = emptyList(),
    val lewd: TriState = TriState.DISABLED,
    val intervalCustom: TriState = TriState.DISABLED,
    val categoriesFiltered: Boolean = false,
    val categoryIncludeIds: Set<String> = emptySet(),
    val categoryExcludeIds: Set<String> = emptySet(),
    val scanlatorMatchAll: Boolean = false,
    val sortType: LibrarySort.Type = LibrarySort.default.type,
    val sortAscending: Boolean = LibrarySort.default.isAscending,
    val displayMode: LibraryDisplayMode = LibraryDisplayMode.default,
    val grouping: Int = LibraryGroup.BY_DEFAULT,
    val portraitColumns: Int = 0,
    val landscapeColumns: Int = 0,
    val downloadBadge: Boolean = false,
    val unreadBadge: Boolean = true,
    val localBadge: Boolean = true,
    val languageBadge: Boolean = true,
    val useLangIcon: Boolean = true,
    val sourceBadge: Boolean = true,
    val continueReadingButton: Boolean = false,
    val categoryTabs: Boolean = true,
    val showHiddenCategories: Boolean = false,
    val categoryNumberOfItems: Boolean = false,
)

private val LibraryMenuOptionsDefaults = LibraryMenuOptionsState()

fun libraryOptionsAtDefaults(state: LibraryMenuOptionsState): Boolean =
    state == LibraryMenuOptionsDefaults

val LibraryTopRowSubPillHeight = 36.dp

enum class LibraryTopRowCollapseState {
    Expanded,
    Collapsed,
}

class LibraryTopRowStateMachine(
    val collapseThreshold: Float = 8f,
    val hysteresis: Float = 24f,
    initialState: LibraryTopRowCollapseState = LibraryTopRowCollapseState.Expanded,
) {
    var state: LibraryTopRowCollapseState by mutableStateOf(initialState)
        private set

    val isCollapsed: Boolean get() = state == LibraryTopRowCollapseState.Collapsed
    val isExpanded: Boolean get() = state == LibraryTopRowCollapseState.Expanded

    private var tapExpandedOffset: Float? = null
    private var justReachedTop: Boolean = false

    fun onScroll(firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Float) {
        val atTop = firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0f
        if (atTop) {
            state = LibraryTopRowCollapseState.Expanded
            justReachedTop = true
            tapExpandedOffset = null
            return
        }

        if (state == LibraryTopRowCollapseState.Collapsed) {
            return
        }

        if (tapExpandedOffset != null) {
            val delta = kotlin.math.abs(firstVisibleItemScrollOffset - tapExpandedOffset!!)
            if (delta > hysteresis || firstVisibleItemIndex > 0) {
                state = LibraryTopRowCollapseState.Collapsed
                tapExpandedOffset = null
                justReachedTop = false
            }
        } else if (justReachedTop) {
            if (firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > hysteresis) {
                state = LibraryTopRowCollapseState.Collapsed
                justReachedTop = false
            }
        } else {
            if (firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > collapseThreshold) {
                state = LibraryTopRowCollapseState.Collapsed
            }
        }
    }

    fun onTapCollapsedPill(currentScrollOffset: Float = 0f) {
        state = LibraryTopRowCollapseState.Expanded
        tapExpandedOffset = currentScrollOffset
        justReachedTop = false
    }

    fun onCategorySelected() {
        if (tapExpandedOffset != null) {
            state = LibraryTopRowCollapseState.Collapsed
            tapExpandedOffset = null
        }
    }

    fun onCollapse() {
        state = LibraryTopRowCollapseState.Collapsed
        tapExpandedOffset = null
        justReachedTop = false
    }

    fun onExpand() {
        state = LibraryTopRowCollapseState.Expanded
        tapExpandedOffset = null
        justReachedTop = true
    }
}

class ShellTabScrollStates(
    val libraryGridStates: MutableMap<Long, LazyStaggeredGridState> = mutableMapOf(),
    val libraryStripState: LazyListState = LazyListState(),
    val updatesListState: LazyListState = LazyListState(),
    val historyListState: LazyListState = LazyListState(),
) {
    fun libraryGridStateFor(categoryId: Long): LazyStaggeredGridState =
        libraryGridStates.getOrPut(categoryId) { LazyStaggeredGridState() }
}

val LocalShellTabScrollStates = staticCompositionLocalOf { ShellTabScrollStates() }

fun calculateCategoryStripScrollOffset(viewportWidth: Int, itemWidth: Int): Int =
    if (viewportWidth > itemWidth) -(viewportWidth - itemWidth) / 2 else 0

object LibraryMenuPullDownState {
    private val mutableIsOpen = MutableStateFlow(false)
    val isOpen: StateFlow<Boolean> = mutableIsOpen.asStateFlow()

    fun open() {
        LibraryFilterSheetState.close()
        mutableIsOpen.value = true
    }
    fun close() { mutableIsOpen.value = false }
    fun toggle() {
        if (!mutableIsOpen.value) LibraryFilterSheetState.close()
        mutableIsOpen.value = !mutableIsOpen.value
    }
}

object LibraryFilterSheetState {
    private val mutableIsOpen = MutableStateFlow(false)
    val isOpen: StateFlow<Boolean> = mutableIsOpen.asStateFlow()

    fun open() {
        LibraryMenuPullDownState.close()
        mutableIsOpen.value = true
    }
    fun close() { mutableIsOpen.value = false }
    fun toggle() {
        if (!mutableIsOpen.value) LibraryMenuPullDownState.close()
        mutableIsOpen.value = !mutableIsOpen.value
    }
}
