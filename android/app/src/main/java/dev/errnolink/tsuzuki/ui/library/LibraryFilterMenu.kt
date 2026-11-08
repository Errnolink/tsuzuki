package dev.errnolink.tsuzuki.ui.library

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.ArrowDown
import dev.errnolink.tsuzuki.designsystem.ArrowUp
import dev.errnolink.tsuzuki.designsystem.Check
import dev.errnolink.tsuzuki.designsystem.Grid3x3
import dev.errnolink.tsuzuki.designsystem.Images
import dev.errnolink.tsuzuki.designsystem.LayoutGrid
import dev.errnolink.tsuzuki.designsystem.List as ListIcon
import dev.errnolink.tsuzuki.designsystem.LocalHaptics
import dev.errnolink.tsuzuki.designsystem.LocalSubmenuHolder
import dev.errnolink.tsuzuki.designsystem.PullDownDivider
import dev.errnolink.tsuzuki.designsystem.PullDownIconPaletteRow
import dev.errnolink.tsuzuki.designsystem.PullDownMenu
import dev.errnolink.tsuzuki.designsystem.PullDownMenuItem
import dev.errnolink.tsuzuki.designsystem.PullDownPaletteOption
import dev.errnolink.tsuzuki.designsystem.PullDownSectionDivider
import dev.errnolink.tsuzuki.designsystem.PullDownStepperRow
import dev.errnolink.tsuzuki.designsystem.PullDownSubmenuItem
import dev.errnolink.tsuzuki.designsystem.PullDownSubmenuOption
import dev.errnolink.tsuzuki.designsystem.PullDownToggleItem
import dev.errnolink.tsuzuki.designsystem.PullDownTriState
import dev.errnolink.tsuzuki.designsystem.PullDownTriStateItem
import dev.errnolink.tsuzuki.designsystem.Rows2
import dev.errnolink.tsuzuki.designsystem.SubmenuData
import dev.errnolink.tsuzuki.designsystem.RotateCcw
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.mangadex.ScanlatorFilterOption
import dev.errnolink.tsuzuki.ui.settings.TriStateListDialog
import eu.kanade.domain.base.BasePreferences
import eu.kanade.presentation.category.visualName
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.util.system.isReleaseBuildType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.domain.category.interactor.SetDisplayMode
import tachiyomi.domain.category.interactor.SetSortModeForCategory
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.model.LibraryGroup
import tachiyomi.domain.library.model.LibrarySort
import tachiyomi.domain.library.model.sort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun LibraryFilterMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    category: Category? = null,
    categories: List<Category> = emptyList(),
    modifier: Modifier = Modifier,
) {
    if (!expanded) return

    val preferences = remember { Injekt.get<LibraryPreferences>() }
    val basePreferences = remember { Injekt.get<BasePreferences>() }
    val tsuzukiPreferences = remember { Injekt.get<dev.errnolink.tsuzuki.preference.TsuzukiPreferences>() }
    val trackers = remember { Injekt.get<TrackerManager>().loggedInTrackers() }
    val setSortModeForCategory = remember { Injekt.get<SetSortModeForCategory>() }
    val setDisplayMode = remember { Injekt.get<SetDisplayMode>() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHaptics.current

    val grouping by preferences.groupLibraryBy().collectAsState()
    val globalSort by preferences.sortingMode().collectAsState()
    val displayMode by preferences.displayMode().collectAsState()
    val downloadedOnly by basePreferences.downloadedOnly().collectAsState()
    val autoUpdateRestrictions by preferences.autoUpdateMangaRestrictions().collectAsState()
    val sortTagsPresent by remember {
        preferences.sortTagsForLibrary().changes().map { it.isNotEmpty() }
    }.collectAsState(initial = preferences.sortTagsForLibrary().get().isNotEmpty())
    val scanlatorOption by remember { ScanlatorFilterOption.observe() }.collectAsState(ScanlatorFilterOption.ANY)

    val quickFilterStates = QuickFilterItem.entries.map { item ->
        item.getPreference(preferences).collectAsState().value
    }
    val trackedStates = trackers.map { service ->
        preferences.filterTracking(service.id.toInt()).collectAsState().value
    }
    val lewdState by preferences.filterLewd().collectAsState()
    val intervalCustomState by preferences.filterIntervalCustom().collectAsState()
    val categoriesFiltered by preferences.filterCategories().collectAsState()
    val categoryInclude by preferences.filterCategoriesInclude().collectAsState()
    val categoryExclude by preferences.filterCategoriesExclude().collectAsState()
    val portraitColumns by preferences.portraitColumns().collectAsState()
    val landscapeColumns by preferences.landscapeColumns().collectAsState()
    val downloadBadge by preferences.downloadBadge().collectAsState()
    val unreadBadge by preferences.unreadBadge().collectAsState()
    val localBadge by preferences.localBadge().collectAsState()
    val languageBadge by preferences.languageBadge().collectAsState()
    val useLangIcon by preferences.useLangIcon().collectAsState()
    val sourceBadge by preferences.sourceBadge().collectAsState()
    val continueReadingButton by preferences.showContinueReadingButton().collectAsState()
    val progressRing by tsuzukiPreferences.libraryProgressRing().collectAsState()
    val categoryTabs by preferences.categoryTabs().collectAsState()
    val showHiddenCategories by preferences.showHiddenCategories().collectAsState()
    val categoryNumberOfItems by preferences.categoryNumberOfItems().collectAsState()

    val activeSort = if (grouping == LibraryGroup.BY_DEFAULT) category?.sort ?: globalSort else globalSort
    val sortAscending = activeSort.isAscending
    val userCategories = remember(categories) { categories.filterNot(Category::isSystemCategory) }
    val hasCategories = userCategories.isNotEmpty()
    val intervalCustomVisible = !isReleaseBuildType &&
        LibraryPreferences.MANGA_OUTSIDE_RELEASE_PERIOD in autoUpdateRestrictions
    val optionsState = LibraryMenuOptionsState(
        quickFilterStates = quickFilterStates,
        trackedStates = trackedStates,
        lewd = lewdState,
        intervalCustom = intervalCustomState,
        categoriesFiltered = categoriesFiltered,
        categoryIncludeIds = categoryInclude,
        categoryExcludeIds = categoryExclude,
        scanlatorMatchAll = scanlatorOption == ScanlatorFilterOption.ALL,
        sortType = activeSort.type,
        sortAscending = sortAscending,
        displayMode = displayMode,
        grouping = grouping,
        portraitColumns = portraitColumns,
        landscapeColumns = landscapeColumns,
        downloadBadge = downloadBadge,
        unreadBadge = unreadBadge,
        localBadge = localBadge,
        languageBadge = languageBadge,
        useLangIcon = useLangIcon,
        sourceBadge = sourceBadge,
        continueReadingButton = continueReadingButton,
        progressRing = progressRing,
        categoryTabs = categoryTabs,
        showHiddenCategories = showHiddenCategories,
        categoryNumberOfItems = categoryNumberOfItems,
    )
    val showReset = !libraryOptionsAtDefaults(optionsState)

    var showCategoriesDialog by rememberSaveable { mutableStateOf(false) }
    if (showCategoriesDialog) {
        TriStateListDialog(
            title = "Categories",
            message = "Choose which categories to include or exclude from this view.",
            items = userCategories,
            initialChecked = categoryInclude.mapNotNull { id -> userCategories.find { it.id.toString() == id } },
            initialInversed = categoryExclude.mapNotNull { id -> userCategories.find { it.id.toString() == id } },
            itemLabel = { it.visualName },
            onDismissRequest = { showCategoriesDialog = false },
            onValueChanged = { newIncluded, newExcluded ->
                preferences.filterCategoriesInclude().set(newIncluded.map { it.id.toString() }.toSet())
                preferences.filterCategoriesExclude().set(newExcluded.map { it.id.toString() }.toSet())
                preferences.filterCategories().set(newIncluded.isNotEmpty() || newExcluded.isNotEmpty())
                showCategoriesDialog = false
            },
        )
    }

    val setSortInScope: (LibrarySort.Type, LibrarySort.Direction) -> Unit = { type, direction ->
        scope.launch { setSortModeForCategory.await(category?.id, type, direction) }
    }

    PullDownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        paneTitle = "Library options",
        glueToAnchor = true,
        width = 300.dp,
    ) {
        val submenuHolder = LocalSubmenuHolder.current

        PullDownIconPaletteRow(
            options = displayPaletteOptions,
            selected = displayPaletteModes.indexOf(displayMode),
            onSelect = { index ->
                haptics?.select()
                setDisplayMode.await(displayPaletteModes[index])
            },
        )

        PullDownSectionDivider()

        PullDownSubmenuItem(
            title = "Sort by",
            value = sortTypeName(activeSort.type),
            trailing = {
                val interaction = remember { MutableInteractionSource() }
                val colors = TsuzukiTheme.colors
                Box(
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            role = Role.Button,
                            onClickLabel = if (sortAscending) "Sort descending" else "Sort ascending",
                        ) {
                            haptics?.toggleOn()
                            setSortInScope(activeSort.type, activeSort.flipped().direction)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(colors.separator.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            imageVector = if (sortAscending) TsuzukiIcons.ArrowUp else TsuzukiIcons.ArrowDown,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(colors.accent),
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            },
            onClick = {
                submenuHolder?.activeSubmenu = SubmenuData("Sort by") {
                    sortTypeOptions(hasTrackers = trackers.isNotEmpty(), hasSortTags = sortTagsPresent)
                        .forEachIndexed { i, (type, label) ->
                            if (i > 0) PullDownDivider()
                            PullDownSubmenuOption(
                                label = label,
                                selected = activeSort.type == type,
                                onClick = {
                                    setSortInScope(type, activeSort.direction)
                                },
                            )
                        }
                }
            },
        )
        PullDownDivider()

        PullDownSubmenuItem(
            title = "Group by",
            value = groupTypeName(grouping),
            onClick = {
                submenuHolder?.activeSubmenu = SubmenuData("Group by") {
                    val groups = buildList {
                        add(LibraryGroup.BY_DEFAULT to "Default")
                        add(LibraryGroup.BY_SOURCE to "Source")
                        add(LibraryGroup.BY_STATUS to "Status")
                        if (trackers.isNotEmpty()) {
                            add(LibraryGroup.BY_TRACK_STATUS to "Track status")
                        }
                        if (hasCategories || grouping == LibraryGroup.UNGROUPED) {
                            add(LibraryGroup.UNGROUPED to "Ungrouped")
                        }
                    }
                    groups.forEachIndexed { i, (grp, label) ->
                        if (i > 0) PullDownDivider()
                        PullDownSubmenuOption(
                            label = label,
                            selected = grouping == grp,
                            onClick = { preferences.groupLibraryBy().set(grp) },
                        )
                    }
                }
            },
        )

        PullDownSectionDivider()

        QuickFilterItem.entries.forEachIndexed { index, item ->
            if (index > 0) PullDownDivider()
            val isDownloaded = item == QuickFilterItem.Downloaded
            val rawState = quickFilterStates[index]
            val state = if (isDownloaded && downloadedOnly) TriState.ENABLED_IS else rawState
            PullDownTriStateItem(
                label = item.label,
                state = state.toPullDownTriState(),
                enabled = !(isDownloaded && downloadedOnly),
                onSet = { pulled -> item.getPreference(preferences).set(pulled.toTriState()) },
                onCycle = {
                    if (isDownloaded && downloadedOnly) return@PullDownTriStateItem
                    item.getPreference(preferences).getAndSet { it.next() }
                },
            )
        }

        PullDownSectionDivider()

        PullDownSubmenuItem(
            title = "More filters",
            value = moreFiltersSummary(
                trackedActive = trackedStates.any { it != TriState.DISABLED },
                lewd = lewdState,
                categories = categoriesFiltered,
                intervalCustom = intervalCustomState,
                intervalVisible = intervalCustomVisible,
                scanlatorMatchAll = scanlatorOption == ScanlatorFilterOption.ALL,
            ),
            onClick = {
                submenuHolder?.activeSubmenu = SubmenuData("More filters") {
                    if (trackers.size == 1) {
                        val service = trackers[0]
                        PullDownTriStateItem(
                            label = "Tracked",
                            state = trackedStates[0].toPullDownTriState(),
                            onSet = { pulled -> preferences.filterTracking(service.id.toInt()).set(pulled.toTriState()) },
                            onCycle = { preferences.filterTracking(service.id.toInt()).getAndSet { it.next() } },
                        )
                    } else if (trackers.size > 1) {
                        SubmenuHeading("Tracked")
                        trackers.forEachIndexed { index, service ->
                            if (index > 0) PullDownDivider()
                            PullDownTriStateItem(
                                label = service.name,
                                state = trackedStates[index].toPullDownTriState(),
                                onSet = { pulled -> preferences.filterTracking(service.id.toInt()).set(pulled.toTriState()) },
                                onCycle = { preferences.filterTracking(service.id.toInt()).getAndSet { it.next() } },
                            )
                        }
                    }
                    PullDownToggleItem(
                        label = "Categories",
                        checked = categoriesFiltered,
                        onChecked = { preferences.filterCategories().set(it) },
                    )
                    PullDownMenuItem(
                        text = "Edit categories",
                        enabled = hasCategories,
                        onClick = { showCategoriesDialog = true },
                    )
                    PullDownDivider()
                    PullDownTriStateItem(
                        label = "Lewd",
                        state = lewdState.toPullDownTriState(),
                        onSet = { pulled -> preferences.filterLewd().set(pulled.toTriState()) },
                        onCycle = { preferences.filterLewd().getAndSet { it.next() } },
                    )
                    if (intervalCustomVisible) {
                        PullDownDivider()
                        PullDownTriStateItem(
                            label = "Interval custom",
                            state = intervalCustomState.toPullDownTriState(),
                            onSet = { pulled -> preferences.filterIntervalCustom().set(pulled.toTriState()) },
                            onCycle = { preferences.filterIntervalCustom().getAndSet { it.next() } },
                        )
                    }
                    PullDownSectionDivider()
                    SubmenuHeading("Excluded scanlator matching")
                    PullDownSubmenuOption(
                        label = "Any",
                        selected = scanlatorOption == ScanlatorFilterOption.ANY,
                        onClick = {
                            scope.launch { ScanlatorFilterOption.set(ScanlatorFilterOption.ANY) }
                        },
                    )
                    PullDownDivider()
                    PullDownSubmenuOption(
                        label = "All",
                        selected = scanlatorOption == ScanlatorFilterOption.ALL,
                        onClick = {
                            scope.launch { ScanlatorFilterOption.set(ScanlatorFilterOption.ALL) }
                        },
                    )
                    if (scanlatorOption == ScanlatorFilterOption.ALL) {
                        TsuzukiText(
                            text = "Hide collaborations only when all credited groups are excluded",
                            style = TsuzukiTheme.typography.footnote,
                            color = TsuzukiTheme.colors.secondary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            },
        )
        PullDownDivider()

        PullDownSubmenuItem(
            title = "View Options",
            value = null,
            onClick = {
                submenuHolder?.activeSubmenu = SubmenuData("View Options") {
                    PullDownToggleItem(
                        label = "Download badge",
                        checked = downloadBadge,
                        onChecked = { preferences.downloadBadge().set(it) },
                    )
                    PullDownDivider()
                    PullDownToggleItem(
                        label = "Unread badge",
                        checked = unreadBadge,
                        onChecked = { preferences.unreadBadge().set(it) },
                    )
                    PullDownDivider()
                    PullDownToggleItem(
                        label = "Local badge",
                        checked = localBadge,
                        onChecked = { preferences.localBadge().set(it) },
                    )
                    PullDownDivider()
                    PullDownToggleItem(
                        label = "Language badge",
                        checked = languageBadge,
                        onChecked = { preferences.languageBadge().set(it) },
                    )
                    if (languageBadge) {
                        PullDownDivider()
                        PullDownToggleItem(
                            label = "Language icon",
                            checked = useLangIcon,
                            onChecked = { preferences.useLangIcon().set(it) },
                        )
                    }
                    PullDownDivider()
                    PullDownToggleItem(
                        label = "Source badge",
                        checked = sourceBadge,
                        onChecked = { preferences.sourceBadge().set(it) },
                    )
                    PullDownDivider()
                    PullDownToggleItem(
                        label = "Continue reading button",
                        checked = continueReadingButton,
                        onChecked = { preferences.showContinueReadingButton().set(it) },
                    )
                    if (continueReadingButton) {
                        PullDownDivider()
                        PullDownToggleItem(
                            label = "Progress ring",
                            checked = progressRing,
                            onChecked = { tsuzukiPreferences.libraryProgressRing().set(it) },
                        )
                    }
                    PullDownSectionDivider()
                    PullDownToggleItem(
                        label = "Category tabs",
                        checked = categoryTabs,
                        onChecked = { preferences.categoryTabs().set(it) },
                    )
                    PullDownDivider()
                    PullDownToggleItem(
                        label = "Hidden categories",
                        checked = showHiddenCategories,
                        onChecked = { preferences.showHiddenCategories().set(it) },
                    )
                    PullDownDivider()
                    PullDownToggleItem(
                        label = "Number of items",
                        checked = categoryNumberOfItems,
                        onChecked = { preferences.categoryNumberOfItems().set(it) },
                    )
                    if (displayMode != LibraryDisplayMode.List) {
                        PullDownSectionDivider()
                        val orientation = LocalConfiguration.current.orientation
                        val columnPreference = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                            preferences.landscapeColumns()
                        } else {
                            preferences.portraitColumns()
                        }
                        val columns = if (orientation == Configuration.ORIENTATION_LANDSCAPE) landscapeColumns else portraitColumns
                        PullDownStepperRow(
                            label = "Columns",
                            value = columns,
                            valueLabel = if (columns > 0) columns.toString() else "Auto",
                            canDecrement = columns > 0,
                            canIncrement = columns < 10,
                            onDecrement = { columnPreference.set((columns - 1).coerceAtLeast(0)) },
                            onIncrement = { columnPreference.set((columns + 1).coerceAtMost(10)) },
                        )
                    }
                }
            },
        )

        if (showReset) {
            PullDownSectionDivider()
            PullDownMenuItem(
                text = "Reset Library options",
                icon = TsuzukiIcons.RotateCcw,
                onClick = {
                    haptics?.toggleOff()
                    resetLibraryOptions(
                        preferences = preferences,
                        tsuzukiPreferences = tsuzukiPreferences,
                        trackers = trackers,
                        scanlatorOption = scanlatorOption,
                        scope = scope,
                        setSortModeForCategory = setSortModeForCategory,
                        setDisplayMode = setDisplayMode,
                        category = category,
                    )
                },
            )
        }
    }
}

private val displayPaletteModes = listOf(
    LibraryDisplayMode.CompactGrid,
    LibraryDisplayMode.ComfortableGrid,
    LibraryDisplayMode.ComfortableGridPanorama,
    LibraryDisplayMode.List,
    LibraryDisplayMode.CoverOnlyGrid,
)

private val displayPaletteOptions = listOf(
    PullDownPaletteOption(TsuzukiIcons.Grid3x3, "Compact"),
    PullDownPaletteOption(TsuzukiIcons.LayoutGrid, "Grid"),
    PullDownPaletteOption(TsuzukiIcons.Rows2, "Wide"),
    PullDownPaletteOption(TsuzukiIcons.ListIcon, "List"),
    PullDownPaletteOption(TsuzukiIcons.Images, "Cover"),
)

private fun sortTypeOptions(hasTrackers: Boolean, hasSortTags: Boolean): List<Pair<LibrarySort.Type, String>> =
    listOfNotNull(
        LibrarySort.Type.Alphabetical to "Alphabetical",
        LibrarySort.Type.TotalChapters to "Total chapters",
        LibrarySort.Type.LastRead to "Last read",
        LibrarySort.Type.LastUpdate to "Last update",
        LibrarySort.Type.UnreadCount to "Unread count",
        LibrarySort.Type.LatestChapter to "Latest chapter",
        LibrarySort.Type.ChapterFetchDate to "Chapter fetch date",
        LibrarySort.Type.DateAdded to "Date added",
        if (hasTrackers) LibrarySort.Type.TrackerMean to "Tracker score" else null,
        if (hasSortTags) LibrarySort.Type.TagList to "Tag list" else null,
        LibrarySort.Type.Random to "Random",
    )

private fun sortTypeName(type: LibrarySort.Type): String = when (type) {
    LibrarySort.Type.Alphabetical -> "Alphabetical"
    LibrarySort.Type.TotalChapters -> "Total chapters"
    LibrarySort.Type.LastRead -> "Last read"
    LibrarySort.Type.LastUpdate -> "Last update"
    LibrarySort.Type.UnreadCount -> "Unread count"
    LibrarySort.Type.LatestChapter -> "Latest chapter"
    LibrarySort.Type.ChapterFetchDate -> "Chapter fetch date"
    LibrarySort.Type.DateAdded -> "Date added"
    LibrarySort.Type.TrackerMean -> "Tracker score"
    LibrarySort.Type.TagList -> "Tag list"
    LibrarySort.Type.Random -> "Random"
}

private fun groupTypeName(group: Int): String = when (group) {
    LibraryGroup.BY_DEFAULT -> "Default"
    LibraryGroup.BY_SOURCE -> "Source"
    LibraryGroup.BY_STATUS -> "Status"
    LibraryGroup.BY_TRACK_STATUS -> "Track status"
    LibraryGroup.UNGROUPED -> "Ungrouped"
    else -> "Default"
}

private fun moreFiltersSummary(
    trackedActive: Boolean,
    lewd: TriState,
    categories: Boolean,
    intervalCustom: TriState,
    intervalVisible: Boolean,
    scanlatorMatchAll: Boolean,
): String? {
    val parts = buildList {
        if (trackedActive) add("Tracked")
        if (categories) add("Categories")
        if (lewd != TriState.DISABLED) add("Lewd")
        if (intervalVisible && intervalCustom != TriState.DISABLED) add("Interval")
        if (scanlatorMatchAll) add("All scanlators")
    }
    return if (parts.isEmpty()) null else parts.joinToString(separator = ", ")
}

@Composable
private fun SubmenuHeading(text: String) {
    TsuzukiText(
        text = text,
        style = TsuzukiTheme.typography.footnote,
        color = TsuzukiTheme.colors.secondary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

private fun resetLibraryOptions(
    preferences: LibraryPreferences,
    tsuzukiPreferences: dev.errnolink.tsuzuki.preference.TsuzukiPreferences,
    trackers: List<Tracker>,
    scanlatorOption: ScanlatorFilterOption,
    scope: CoroutineScope,
    setSortModeForCategory: SetSortModeForCategory,
    setDisplayMode: SetDisplayMode,
    category: Category?,
) {
    QuickFilterItem.entries.forEach { it.getPreference(preferences).set(TriState.DISABLED) }
    trackers.forEach { preferences.filterTracking(it.id.toInt()).set(TriState.DISABLED) }
    preferences.filterLewd().set(TriState.DISABLED)
    preferences.filterIntervalCustom().set(TriState.DISABLED)
    preferences.filterCategories().set(false)
    preferences.filterCategoriesInclude().set(emptySet())
    preferences.filterCategoriesExclude().set(emptySet())
    if (scanlatorOption != ScanlatorFilterOption.ANY) {
        scope.launch { ScanlatorFilterOption.set(ScanlatorFilterOption.ANY) }
    }
    scope.launch {
        setSortModeForCategory.await(
            category?.id,
            LibrarySort.default.type,
            LibrarySort.default.direction,
        )
    }
    setDisplayMode.await(LibraryDisplayMode.default)
    preferences.groupLibraryBy().set(LibraryGroup.BY_DEFAULT)
    preferences.portraitColumns().set(0)
    preferences.landscapeColumns().set(0)
    preferences.downloadBadge().set(false)
    preferences.unreadBadge().set(true)
    preferences.localBadge().set(true)
    preferences.languageBadge().set(true)
    preferences.useLangIcon().set(true)
    preferences.sourceBadge().set(true)
    preferences.showContinueReadingButton().set(false)
    tsuzukiPreferences.libraryProgressRing().set(true)
    preferences.categoryTabs().set(true)
    preferences.showHiddenCategories().set(false)
    preferences.categoryNumberOfItems().set(false)
}
