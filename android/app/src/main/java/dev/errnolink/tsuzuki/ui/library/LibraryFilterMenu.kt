package dev.errnolink.tsuzuki.ui.library

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import dev.errnolink.tsuzuki.designsystem.Minus
import dev.errnolink.tsuzuki.designsystem.Plus
import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.errnolink.tsuzuki.designsystem.ArrowDown
import dev.errnolink.tsuzuki.designsystem.ArrowDownUp
import dev.errnolink.tsuzuki.designsystem.ArrowUp
import dev.errnolink.tsuzuki.designsystem.Check
import dev.errnolink.tsuzuki.designsystem.Grid3x3
import dev.errnolink.tsuzuki.designsystem.Images
import dev.errnolink.tsuzuki.designsystem.Layers
import dev.errnolink.tsuzuki.designsystem.LayoutGrid
import dev.errnolink.tsuzuki.designsystem.List as ListIcon
import dev.errnolink.tsuzuki.designsystem.BookOpen
import dev.errnolink.tsuzuki.designsystem.Bookmark
import dev.errnolink.tsuzuki.designsystem.Download
import dev.errnolink.tsuzuki.designsystem.LocalHaptics
import dev.errnolink.tsuzuki.designsystem.LocalSubmenuHolder
import dev.errnolink.tsuzuki.designsystem.M3Motion
import dev.errnolink.tsuzuki.designsystem.Play
import dev.errnolink.tsuzuki.designsystem.PlainIconButton
import dev.errnolink.tsuzuki.designsystem.PullDownMenu
import dev.errnolink.tsuzuki.designsystem.PullDownMenuItem
import dev.errnolink.tsuzuki.designsystem.pressScale
import androidx.compose.ui.graphics.vector.ImageVector
import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import dev.errnolink.tsuzuki.designsystem.PullDownSectionDivider
import dev.errnolink.tsuzuki.designsystem.PullDownSubmenuItem
import dev.errnolink.tsuzuki.designsystem.PullDownSubmenuOption
import dev.errnolink.tsuzuki.designsystem.PullDownToggleItem
import dev.errnolink.tsuzuki.designsystem.PullDownTriState
import dev.errnolink.tsuzuki.designsystem.PullDownTriStateItem
import dev.errnolink.tsuzuki.designsystem.Rows2
import dev.errnolink.tsuzuki.designsystem.SlidersHorizontal
import dev.errnolink.tsuzuki.designsystem.SubmenuData
import dev.errnolink.tsuzuki.designsystem.RotateCcw
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.KasaneSprings
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
    var draining by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(expanded) {
        if (expanded) {
            draining = true
        } else if (draining) {
            kotlinx.coroutines.delay(MenuDrainMillis)
            draining = false
        }
    }
    if (!expanded && !draining) return

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
    val showHiddenCategories by preferences.showHiddenCategories().collectAsState()

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
        showHiddenCategories = showHiddenCategories,
    )
    val showReset = !libraryOptionsAtDefaults(optionsState)
    val sortTitle = stringResource(MR.strings.action_sort)
    val moreFiltersTitle = stringResource(MR.strings.tsuzuki_more_filters)
    val groupByTitle = stringResource(MR.strings.tsuzuki_group_by)
    val viewTitle = stringResource(MR.strings.tsuzuki_view)
    val categoriesTitle = stringResource(MR.strings.categories)
    val categoriesSummary = when {
        categoryInclude.isEmpty() && categoryExclude.isEmpty() -> stringResource(MR.strings.tsuzuki_any)
        else -> buildList {
            if (categoryInclude.isNotEmpty()) add(stringResource(MR.strings.tsuzuki_n_only, categoryInclude.size))
            if (categoryExclude.isNotEmpty()) add(stringResource(MR.strings.tsuzuki_n_hidden, categoryExclude.size))
        }.joinToString(", ")
    }

    var showCategoriesDialog by rememberSaveable { mutableStateOf(false) }
    if (showCategoriesDialog) {
        TriStateListDialog(
            title = categoriesTitle,
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

    // Compact, iOS Photos-style: a display segmented control up top, one sort row whose submenu
    // slides in place, one tri-state row per quick filter, chevron submenus for the rest. The menu
    // takes the room down to the tab bar (plus a breathing margin) and only scrolls beyond it.
    val shellBottomPadding = dev.errnolink.tsuzuki.ui.shell.LocalShellBottomPadding.current
    PullDownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        paneTitle = stringResource(MR.strings.tsuzuki_library_filters),
        width = 284.dp,
        // The popup window ends above the navigation bar while the shell's bottom padding is
        // measured from the screen edge, so the gesture inset is added back: on the emulator
        // the menu otherwise overlapped the tab bar's top edge by ~18 dp.
        bottomInset = shellBottomPadding +
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
            MenuBottomClearance,
    ) {
        val submenuHolder = LocalSubmenuHolder.current
        Column {
            // Every display type the app has: five borderless icon tiles with a soft sliding accent pill.
            LibraryLayoutPalette(
                options = displayPaletteOptions.map { it.first to stringResource(it.second) },
                selectedIndex = displayPaletteModes.indexOf(displayMode).coerceAtLeast(0),
                onSelect = { index ->
                    haptics?.select()
                    setDisplayMode.await(displayPaletteModes[index])
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            )

            PullDownSectionDivider()

            PullDownSubmenuItem(
                title = sortTitle,
                value = stringResource(sortTypeName(activeSort.type)),
                icon = TsuzukiIcons.ArrowDownUp,
                trailing = {
                    Image(
                        imageVector = if (sortAscending) TsuzukiIcons.ArrowUp else TsuzukiIcons.ArrowDown,
                        contentDescription = stringResource(if (sortAscending) MR.strings.action_asc else MR.strings.action_desc),
                        colorFilter = ColorFilter.tint(TsuzukiTheme.colors.accent),
                        modifier = Modifier.size(15.dp),
                    )
                },
                onClick = {
                    submenuHolder?.activeSubmenu = SubmenuData(sortTitle) {
                        sortTypeOptions(hasTrackers = trackers.isNotEmpty(), hasSortTags = sortTagsPresent)
                            .forEach { (type, label) ->
                                val selected = activeSort.type == type
                                PullDownSubmenuOption(
                                    label = stringResource(label),
                                    selected = selected,
                                    onClick = {
                                        haptics?.select()
                                        setSortInScope(type, sortTapTarget(activeSort, type).direction)
                                    },
                                    trailing = if (selected) {
                                        {
                                            Image(
                                                imageVector = if (sortAscending) TsuzukiIcons.ArrowUp else TsuzukiIcons.ArrowDown,
                                                contentDescription = stringResource(
                                                    if (sortAscending) MR.strings.action_asc else MR.strings.action_desc,
                                                ),
                                                colorFilter = ColorFilter.tint(TsuzukiTheme.colors.accent),
                                                modifier = Modifier.size(15.dp),
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                )
                            }
                    }
                },
            )

            PullDownSubmenuItem(
                title = groupByTitle,
                value = stringResource(groupTypeName(grouping)),
                icon = TsuzukiIcons.Layers,
                onClick = {
                    submenuHolder?.activeSubmenu = SubmenuData(groupByTitle) {
                        val groups = buildList {
                            add(LibraryGroup.BY_DEFAULT)
                            add(LibraryGroup.BY_SOURCE)
                            add(LibraryGroup.BY_STATUS)
                            if (trackers.isNotEmpty()) {
                                add(LibraryGroup.BY_TRACK_STATUS)
                            }
                            if (hasCategories || grouping == LibraryGroup.UNGROUPED) {
                                add(LibraryGroup.UNGROUPED)
                            }
                        }
                        groups.forEach { grp ->
                            PullDownSubmenuOption(
                                label = stringResource(groupTypeName(grp)),
                                selected = grouping == grp,
                                onClick = { preferences.groupLibraryBy().set(grp) },
                            )
                        }
                    }
                },
            )

            PullDownSubmenuItem(
                title = viewTitle,
                value = null,
                icon = TsuzukiIcons.LayoutGrid,
                onClick = {
                    submenuHolder?.activeSubmenu = SubmenuData(viewTitle) {
                        if (displayMode != LibraryDisplayMode.List) {
                            val orientation = LocalConfiguration.current.orientation
                            val columnPreference = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                                preferences.landscapeColumns()
                            } else {
                                preferences.portraitColumns()
                            }
                            val columns = if (orientation == Configuration.ORIENTATION_LANDSCAPE) landscapeColumns else portraitColumns
                            ColumnsStepperRow(
                                columns = columns,
                                onSet = { value ->
                                    haptics?.select()
                                    columnPreference.set(value)
                                },
                            )
                            PullDownSectionDivider()
                        }
                        SubmenuHeading(stringResource(MR.strings.tsuzuki_badges))
                        PullDownToggleItem(
                            label = stringResource(MR.strings.tsuzuki_download_badge),
                            checked = downloadBadge,
                            onChecked = { preferences.downloadBadge().set(it) },
                        )
                        PullDownToggleItem(
                            label = stringResource(MR.strings.tsuzuki_unread_badge),
                            checked = unreadBadge,
                            onChecked = { preferences.unreadBadge().set(it) },
                        )
                        PullDownToggleItem(
                            label = stringResource(MR.strings.tsuzuki_local_badge),
                            checked = localBadge,
                            onChecked = { preferences.localBadge().set(it) },
                        )
                        PullDownToggleItem(
                            label = stringResource(MR.strings.tsuzuki_language_badge),
                            checked = languageBadge,
                            onChecked = { preferences.languageBadge().set(it) },
                        )
                        if (languageBadge) {
                            PullDownToggleItem(
                                label = stringResource(MR.strings.tsuzuki_language_icon),
                                checked = useLangIcon,
                                onChecked = { preferences.useLangIcon().set(it) },
                            )
                        }
                        PullDownToggleItem(
                            label = stringResource(MR.strings.tsuzuki_source_badge),
                            checked = sourceBadge,
                            onChecked = { preferences.sourceBadge().set(it) },
                        )
                        PullDownSectionDivider()
                        SubmenuHeading(stringResource(MR.strings.label_library))
                        PullDownToggleItem(
                            label = stringResource(MR.strings.action_display_show_continue_reading_button),
                            checked = continueReadingButton,
                            onChecked = { preferences.showContinueReadingButton().set(it) },
                        )
                        if (continueReadingButton) {
                            PullDownToggleItem(
                                label = stringResource(MR.strings.tsuzuki_progress_ring),
                                checked = progressRing,
                                onChecked = { tsuzukiPreferences.libraryProgressRing().set(it) },
                            )
                        }
                        PullDownToggleItem(
                            label = stringResource(MR.strings.tsuzuki_hidden_categories),
                            checked = showHiddenCategories,
                            onChecked = { preferences.showHiddenCategories().set(it) },
                        )
                    }
                },
            )

            PullDownSectionDivider()

            FilterSectionLabel(stringResource(MR.strings.action_filter))

            PrimaryQuickFilters.forEach { item ->
                val isDownloaded = item == QuickFilterItem.Downloaded
                val rawState = quickFilterStates[item.ordinal]
                val state = if (isDownloaded && downloadedOnly) TriState.ENABLED_IS else rawState
                PullDownTriStateItem(
                    label = stringResource(item.label),
                    state = state.toPullDownTriState(),
                    enabled = !(isDownloaded && downloadedOnly),
                    icon = item.menuIcon(),
                    onCycle = {
                        if (isDownloaded && downloadedOnly) return@PullDownTriStateItem
                        item.getPreference(preferences).getAndSet { it.next() }
                    },
                )
            }

            PullDownSubmenuItem(
                title = moreFiltersTitle,
                icon = TsuzukiIcons.SlidersHorizontal,
                value = moreFiltersSummary(
                    secondaryActive = SecondaryQuickFilters
                        .filter { quickFilterStates[it.ordinal] != TriState.DISABLED }
                        .map { it.label },
                    trackedActive = trackedStates.any { it != TriState.DISABLED },
                    lewd = lewdState,
                    categories = categoriesFiltered,
                    intervalCustom = intervalCustomState,
                    intervalVisible = intervalCustomVisible,
                    scanlatorMatchAll = scanlatorOption == ScanlatorFilterOption.ALL,
                ).map { stringResource(it) }.takeIf { it.isNotEmpty() }?.joinToString(", "),
                onClick = {
                    submenuHolder?.activeSubmenu = SubmenuData(moreFiltersTitle) {
                        SecondaryQuickFilters.forEach { item ->
                            val rawState = quickFilterStates[item.ordinal]
                            PullDownTriStateItem(
                                label = stringResource(item.label),
                                state = rawState.toPullDownTriState(),
                                onCycle = { item.getPreference(preferences).getAndSet { it.next() } },
                            )
                        }
                        PullDownSectionDivider()
                        if (trackers.size == 1) {
                            val service = trackers[0]
                            PullDownTriStateItem(
                                label = stringResource(MR.strings.action_filter_tracked),
                                state = trackedStates[0].toPullDownTriState(),
                                onCycle = { preferences.filterTracking(service.id.toInt()).getAndSet { it.next() } },
                            )
                        } else if (trackers.size > 1) {
                            SubmenuHeading(stringResource(MR.strings.action_filter_tracked))
                            trackers.forEachIndexed { index, service ->
                                PullDownTriStateItem(
                                    label = service.name,
                                    state = trackedStates[index].toPullDownTriState(),
                                    onCycle = { preferences.filterTracking(service.id.toInt()).getAndSet { it.next() } },
                                )
                            }
                        }
                        PullDownMenuItem(
                            text = stringResource(MR.strings.categories),
                            subtitle = categoriesSummary,
                            enabled = hasCategories,
                            onClick = { showCategoriesDialog = true },
                        )
                        PullDownTriStateItem(
                            label = stringResource(SYMR.strings.lewd),
                            state = lewdState.toPullDownTriState(),
                            onCycle = { preferences.filterLewd().getAndSet { it.next() } },
                        )
                        if (intervalCustomVisible) {
                            PullDownTriStateItem(
                                label = stringResource(MR.strings.tsuzuki_interval_custom),
                                state = intervalCustomState.toPullDownTriState(),
                                onCycle = { preferences.filterIntervalCustom().getAndSet { it.next() } },
                            )
                        }
                        PullDownSectionDivider()
                        SubmenuHeading(stringResource(MR.strings.tsuzuki_hide_chapter_when))
                        PullDownSubmenuOption(
                            label = stringResource(MR.strings.tsuzuki_scanlator_any_excluded),
                            selected = scanlatorOption == ScanlatorFilterOption.ANY,
                            onClick = {
                                scope.launch { ScanlatorFilterOption.set(ScanlatorFilterOption.ANY) }
                            },
                        )
                        PullDownSubmenuOption(
                            label = stringResource(MR.strings.tsuzuki_scanlator_all_excluded),
                            selected = scanlatorOption == ScanlatorFilterOption.ALL,
                            onClick = {
                                scope.launch { ScanlatorFilterOption.set(ScanlatorFilterOption.ALL) }
                            },
                        )
                    }
                },
            )

            if (showReset) {
                PullDownSectionDivider()
                PullDownMenuItem(
                    text = stringResource(MR.strings.tsuzuki_reset_library_options),
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
}

/** Long enough for the liquid close (about 270 ms) to drain back into the filter button. */
private const val MenuDrainMillis = 550L

/** LIB-07: the popover's rows inset 16 dp horizontally; the segmented control uses the same inset. */
internal val MenuRowInset = 16.dp

/** LIB-04: the popover ends this far above the tab bar (the shell's bottom padding carries the rest). */
private val MenuBottomClearance = 12.dp

internal val displayPaletteModes = listOf(
    LibraryDisplayMode.CompactGrid,
    LibraryDisplayMode.ComfortableGrid,
    LibraryDisplayMode.ComfortableGridPanorama,
    LibraryDisplayMode.List,
    LibraryDisplayMode.CoverOnlyGrid,
)

internal val displayPaletteOptions: List<Pair<ImageVector, StringResource>> = listOf(
    TsuzukiIcons.Grid3x3 to MR.strings.tsuzuki_layout_compact,
    TsuzukiIcons.LayoutGrid to MR.strings.tsuzuki_layout_grid,
    TsuzukiIcons.Rows2 to MR.strings.tsuzuki_layout_wide,
    TsuzukiIcons.ListIcon to MR.strings.tsuzuki_layout_list,
    TsuzukiIcons.Images to MR.strings.tsuzuki_layout_cover,
)

internal fun sortTypeOptions(hasTrackers: Boolean, hasSortTags: Boolean): List<Pair<LibrarySort.Type, StringResource>> =
    listOfNotNull(
        LibrarySort.Type.Alphabetical,
        LibrarySort.Type.TotalChapters,
        LibrarySort.Type.LastRead,
        LibrarySort.Type.LastUpdate,
        LibrarySort.Type.UnreadCount,
        LibrarySort.Type.LatestChapter,
        LibrarySort.Type.ChapterFetchDate,
        LibrarySort.Type.DateAdded,
        if (hasTrackers) LibrarySort.Type.TrackerMean else null,
        if (hasSortTags) LibrarySort.Type.TagList else null,
        LibrarySort.Type.Random,
    ).map { it to sortTypeName(it) }

internal fun sortTypeName(type: LibrarySort.Type): StringResource = when (type) {
    LibrarySort.Type.Alphabetical -> MR.strings.tsuzuki_sort_alphabetical
    LibrarySort.Type.TotalChapters -> MR.strings.action_sort_total
    LibrarySort.Type.LastRead -> MR.strings.action_sort_last_read
    LibrarySort.Type.LastUpdate -> MR.strings.tsuzuki_sort_last_update
    LibrarySort.Type.UnreadCount -> MR.strings.action_sort_unread_count
    LibrarySort.Type.LatestChapter -> MR.strings.action_sort_latest_chapter
    LibrarySort.Type.ChapterFetchDate -> MR.strings.action_sort_chapter_fetch_date
    LibrarySort.Type.DateAdded -> MR.strings.action_sort_date_added
    LibrarySort.Type.TrackerMean -> MR.strings.action_sort_tracker_score
    LibrarySort.Type.TagList -> MR.strings.tsuzuki_sort_tag_list
    LibrarySort.Type.Random -> MR.strings.action_sort_random
}

/** The Columns stepper's clamp: the same 0–10 range the old View › Columns submenu had (0 = Auto). */
internal fun columnsStepped(current: Int, delta: Int): Int = (current + delta).coerceIn(0, 10)


/** Tapping a sort mode: the current one flips its direction, a different one keeps the direction. */
internal fun sortTapTarget(current: LibrarySort, tapped: LibrarySort.Type): LibrarySort =
    if (current.type == tapped) current.flipped() else current.copy(type = tapped)

/** The Columns stepper row under the layout segments: Auto (0) through 10, one step per press. */
@Composable
internal fun ColumnsStepperRow(
    columns: Int,
    onSet: (Int) -> Unit,
) {
    val colors = TsuzukiTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TsuzukiText(stringResource(MR.strings.tsuzuki_columns), Modifier.weight(1f), color = colors.text)
        PlainIconButton(stringResource(MR.strings.tsuzuki_fewer_columns), { onSet(columnsStepped(columns, -1)) }, enabled = columns > 0) {
            Icon(TsuzukiIcons.Minus, null, Modifier.size(18.dp), tint = colors.text)
        }
        TsuzukiText(
            if (columns == 0) stringResource(MR.strings.label_auto) else columns.toString(),
            Modifier.padding(horizontal = 10.dp).width(36.dp),
            style = TsuzukiTheme.typography.subhead,
            color = colors.secondary,
            maxLines = 1,
        )
        PlainIconButton(stringResource(MR.strings.tsuzuki_more_columns), { onSet(columnsStepped(columns, +1)) }, enabled = columns < 10) {
            Icon(TsuzukiIcons.Plus, null, Modifier.size(18.dp), tint = colors.text)
        }
    }
}

private fun groupTypeName(group: Int): StringResource = when (group) {
    LibraryGroup.BY_SOURCE -> MR.strings.tsuzuki_group_source
    LibraryGroup.BY_STATUS -> MR.strings.status
    LibraryGroup.BY_TRACK_STATUS -> MR.strings.tsuzuki_group_track_status
    LibraryGroup.UNGROUPED -> SYMR.strings.ungrouped
    else -> MR.strings.label_default
}

internal fun moreFiltersSummary(
    secondaryActive: List<StringResource> = emptyList(),
    trackedActive: Boolean,
    lewd: TriState,
    categories: Boolean,
    intervalCustom: TriState,
    intervalVisible: Boolean,
    scanlatorMatchAll: Boolean,
): List<StringResource> = buildList {
    addAll(secondaryActive)
    if (trackedActive) add(MR.strings.action_filter_tracked)
    if (categories) add(MR.strings.categories)
    if (lewd != TriState.DISABLED) add(SYMR.strings.lewd)
    if (intervalVisible && intervalCustom != TriState.DISABLED) add(MR.strings.tsuzuki_interval)
    if (scanlatorMatchAll) add(MR.strings.tsuzuki_all_scanlators)
}

@Composable
internal fun LibraryLayoutPalette(
    options: List<Pair<ImageVector, String>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return
    val clampedIndex = selectedIndex.coerceIn(0, options.lastIndex)
    val colors = TsuzukiTheme.colors
    val motion = TsuzukiTheme.motion
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val animatedIndex by animateFloatAsState(
        targetValue = clampedIndex.toFloat(),
        animationSpec = if (motion.reduced) snap() else KasaneSprings.tab(),
        label = "LibraryLayoutPaletteSelection",
    )
    val pillFill = colors.accent.copy(alpha = if (colors.isDark) 0.20f else 0.13f)
    val pillRim = colors.accent.copy(alpha = if (colors.isDark) 0.35f else 0.25f)
    val tileCorner = RoundedCornerShape(14.dp)
    BoxWithConstraints(modifier.height(54.dp)) {
        val gapDp = 4.dp
        val gapPx = with(density) { gapDp.toPx() }
        val totalPx = with(density) { maxWidth.toPx() }
        val cellPx = ((totalPx - gapPx * (options.size - 1)) / options.size).coerceAtLeast(1f)
        val textMeasurer = rememberTextMeasurer()
        val caption2 = TsuzukiTheme.typography.caption2
        val baseSize = caption2.fontSize.value.roundToInt()
        val maxTextPx = (cellPx - with(density) { 4.dp.toPx() }).roundToInt().coerceAtLeast(0)
        val fittedSizes = remember(options, maxTextPx) {
            options.map { (_, label) ->
                (baseSize downTo 9).firstOrNull { size ->
                    !textMeasurer.measure(
                        label,
                        style = caption2.copy(fontSize = size.sp, fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        constraints = Constraints(maxWidth = maxTextPx),
                    ).didOverflowWidth
                } ?: 9
            }
        }
        Spacer(
            Modifier
                .matchParentSize()
                .drawBehind {
                    val slotX = animatedIndex * (cellPx + gapPx)
                    val left = if (rtl) size.width - cellPx - slotX else slotX
                    val radius = 14.dp.toPx()
                    val stroke = 0.75.dp.toPx()
                    drawRoundRect(
                        color = pillFill,
                        topLeft = Offset(left, 0f),
                        size = Size(cellPx, size.height),
                        cornerRadius = CornerRadius(radius),
                    )
                    drawRoundRect(
                        color = pillRim,
                        topLeft = Offset(left + stroke / 2f, stroke / 2f),
                        size = Size((cellPx - stroke).coerceAtLeast(1f), (size.height - stroke).coerceAtLeast(1f)),
                        cornerRadius = CornerRadius((radius - stroke / 2f).coerceAtLeast(0f)),
                        style = Stroke(stroke),
                    )
                },
        )
        Row(
            Modifier.fillMaxSize().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(gapDp),
        ) {
            options.forEachIndexed { index, (icon, label) ->
                val selected = index == clampedIndex
                val interaction = remember { MutableInteractionSource() }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(tileCorner)
                        .pressScale(interaction)
                        .selectable(
                            selected = selected,
                            interactionSource = interaction,
                            indication = null,
                            role = Role.RadioButton,
                            onClick = { onSelect(index) },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                        tint = if (selected) colors.accent else colors.text.copy(alpha = 0.78f),
                    )
                    TsuzukiText(
                        text = label,
                        style = caption2.copy(
                            fontSize = fittedSizes[index].sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                        color = if (selected) colors.text else colors.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

internal fun QuickFilterItem.menuIcon(): ImageVector? = when (this) {
    QuickFilterItem.Unread -> TsuzukiIcons.BookOpen
    QuickFilterItem.Downloaded -> TsuzukiIcons.Download
    QuickFilterItem.Started -> TsuzukiIcons.Play
    QuickFilterItem.Bookmarked -> TsuzukiIcons.Bookmark
    else -> null
}

@Composable
internal fun SubmenuHeading(text: String) {
    TsuzukiText(
        text = text,
        style = TsuzukiTheme.typography.footnote,
        color = TsuzukiTheme.colors.secondary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

/** Small grey section label inside the compact menu (iOS Photos: "Sort", "Filter"). */
@Composable
internal fun FilterSectionLabel(text: String) {
    TsuzukiText(
        text = text,
        style = TsuzukiTheme.typography.footnote,
        color = TsuzukiTheme.colors.secondary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp),
    )
}

/** The filter button's icon with the active-filter count badge (the old bottom circle's badge). */
@Composable
internal fun LibraryFilterIcon(activeCount: Int, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Icon(TsuzukiIcons.SlidersHorizontal, null, Modifier.size(22.dp), tint = TsuzukiTheme.colors.text)
        LibraryFilterBadge(count = activeCount, modifier = Modifier.align(Alignment.TopEnd))
    }
}

@Composable
internal fun LibraryFilterBadge(count: Int, modifier: Modifier = Modifier) {
    val motion = TsuzukiTheme.motion
    AnimatedVisibility(
        visible = count > 0,
        enter = if (motion.reduced) {
            fadeIn(snap())
        } else {
            scaleIn(tween(M3Motion.Short2, easing = M3Motion.Standard), initialScale = 0f) +
                fadeIn(tween(M3Motion.Short2, easing = M3Motion.Standard))
        },
        exit = if (motion.reduced) {
            fadeOut(snap())
        } else {
            scaleOut(tween(M3Motion.Short2, easing = M3Motion.StandardAccelerate), targetScale = 0f) +
                fadeOut(tween(M3Motion.Short2, easing = M3Motion.StandardAccelerate))
        },
        modifier = modifier.offset(x = 6.dp, y = (-2).dp),
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(TsuzukiTheme.colors.accent)
                .padding(horizontal = 4.dp, vertical = 1.dp),
            contentAlignment = Alignment.Center,
        ) {
            TsuzukiText(
                text = formatFilterCount(count),
                style = TsuzukiTheme.typography.caption2.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 12.sp,
                ),
                color = TsuzukiTheme.colors.onAccent,
                maxLines = 1,
            )
        }
    }
}

internal fun resetLibraryOptions(
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
    preferences.showHiddenCategories().set(false)
}
