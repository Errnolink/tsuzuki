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
import dev.icerock.moko.resources.StringResource
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryGroup
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR

enum class QuickFilterItem(
    val label: StringResource,
    val getPreference: (LibraryPreferences) -> Preference<TriState>,
) {
    Unread(MR.strings.action_filter_unread, LibraryPreferences::filterUnread),
    Started(MR.strings.label_started, LibraryPreferences::filterStarted),
    Downloaded(MR.strings.label_downloaded, LibraryPreferences::filterDownloaded),
    Bookmarked(MR.strings.action_filter_bookmarked, LibraryPreferences::filterBookmarked),
    Completed(MR.strings.completed, LibraryPreferences::filterCompleted),
    Merged(MR.strings.tsuzuki_merged, LibraryPreferences::filterMerged),
    Missing(MR.strings.tsuzuki_missing, LibraryPreferences::filterMissingChapters),
    Unavailable(MR.strings.tsuzuki_unavailable, LibraryPreferences::filterUnavailableChapters),
}

val PrimaryQuickFilters: List<QuickFilterItem> = listOf(
    QuickFilterItem.Unread,
    QuickFilterItem.Downloaded,
    QuickFilterItem.Started,
    QuickFilterItem.Bookmarked,
)

val SecondaryQuickFilters: List<QuickFilterItem> = listOf(
    QuickFilterItem.Completed,
    QuickFilterItem.Merged,
    QuickFilterItem.Missing,
    QuickFilterItem.Unavailable,
)

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

/**
 * LIB-05: the badge counts every filter kind the menu can set — the eight quick tri-states, the
 * per-tracker filters, lewd and outside-release-interval, the category filter (one entry whatever
 * the include/exclude sets hold) and the scanlator match-all switch.
 */
fun activeFilterCount(state: LibraryMenuOptionsState): Int =
    calculateActiveFilterCount(state.quickFilterStates) +
        calculateActiveFilterCount(state.trackedStates) +
        calculateActiveFilterCount(listOf(state.lewd, state.intervalCustom)) +
        if (
            state.categoriesFiltered ||
            state.categoryIncludeIds.isNotEmpty() ||
            state.categoryExcludeIds.isNotEmpty()
        ) {
            1
        } else {
            0
        } +
        if (state.scanlatorMatchAll) 1 else 0

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
    val displayMode: LibraryDisplayMode = LibraryDisplayMode.CompactGrid,
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
    val progressRing: Boolean = true,
    val showHiddenCategories: Boolean = false,
)

private val LibraryMenuOptionsDefaults = LibraryMenuOptionsState()

fun libraryOptionsAtDefaults(state: LibraryMenuOptionsState): Boolean =
    state.copy(trackedStates = state.trackedStates.filter { it != TriState.DISABLED }) == LibraryMenuOptionsDefaults

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
    val updatesListState: LazyListState = LazyListState(),
    val historyListState: LazyListState = LazyListState(),
) {
    fun libraryGridStateFor(categoryId: Long): LazyStaggeredGridState =
        libraryGridStates.getOrPut(categoryId) { LazyStaggeredGridState() }
}

val LocalShellTabScrollStates = staticCompositionLocalOf { ShellTabScrollStates() }

object LibraryMenuPullDownState {
    private val mutableIsOpen = MutableStateFlow(false)
    val isOpen: StateFlow<Boolean> = mutableIsOpen.asStateFlow()

    fun open() { mutableIsOpen.value = true }
    fun close() { mutableIsOpen.value = false }
    fun toggle() { mutableIsOpen.value = !mutableIsOpen.value }
}
