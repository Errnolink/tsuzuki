package dev.errnolink.tsuzuki.lab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import eu.kanade.tachiyomi.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object DesignLab {
    val enabledBuildTypes = setOf("debug", "benchmark", "preview")

    fun buildTypeEnabled(buildType: String): Boolean = buildType in enabledBuildTypes

    val enabled: Boolean get() = buildTypeEnabled(BuildConfig.BUILD_TYPE)

    private val preferenceStore: PreferenceStore by lazy { Injekt.get<PreferenceStore>() }

    // Touch
    enum class PressShape { off, on }
    val pressShape = LabOption(
        key = "pressShape",
        area = "Touch",
        title = "Press shape",
        summary = "Pill buttons square off while pressed and spring back",
        choices = listOf(PressShape.off, PressShape.on),
        default = PressShape.off,
        built = true,
    )

    enum class ButtonGroups { off, on }
    val buttonGroups = LabOption(
        key = "buttonGroups",
        area = "Touch",
        title = "Button groups",
        summary = "Related actions join into one connected group",
        choices = listOf(ButtonGroups.off, ButtonGroups.on),
        default = ButtonGroups.off,
    )

    enum class ChipShape { off, on }
    val chipShape = LabOption(
        key = "chipShape",
        area = "Touch",
        title = "Chip shape",
        summary = "Chips take the expressive shape",
        choices = listOf(ChipShape.off, ChipShape.on),
        default = ChipShape.off,
    )

    enum class SwitchStretch { off, on }
    val switchStretch = LabOption(
        key = "switchStretch",
        area = "Touch",
        title = "Switch stretch",
        summary = "Switches stretch while dragged",
        choices = listOf(SwitchStretch.off, SwitchStretch.on),
        default = SwitchStretch.off,
    )

    enum class SpringFeel { calm, lively }
    val springFeel = LabOption(
        key = "springFeel",
        area = "Touch",
        title = "Spring feel",
        summary = "Motion springs across lists and chrome",
        choices = listOf(SpringFeel.calm, SpringFeel.lively),
        default = SpringFeel.calm,
    )

    enum class Haptics { current, detents }
    val haptics = LabOption(
        key = "haptics",
        area = "Touch",
        title = "Haptics",
        summary = "How touches confirm",
        choices = listOf(Haptics.current, Haptics.detents),
        default = Haptics.current,
    )

    enum class ProgressStyle { current, expressive }
    val progressStyle = LabOption(
        key = "progressStyle",
        area = "Touch",
        title = "Progress style",
        summary = "Reading progress treatment",
        choices = listOf(ProgressStyle.current, ProgressStyle.expressive),
        default = ProgressStyle.current,
        choiceSummary = { choice ->
            when (choice) {
                ProgressStyle.expressive -> "thick line, wave, morphing mark"
                ProgressStyle.current -> null
            }
        },
    )

    // Shell
    enum class Surfaces { current, liquid, expressive }
    val surfaces = LabOption(
        key = "surfaces",
        area = "Shell",
        title = "Surfaces",
        summary = "Page background treatment",
        choices = listOf(Surfaces.current, Surfaces.liquid, Surfaces.expressive),
        default = Surfaces.current,
        choiceSummary = { choice ->
            when (choice) {
                Surfaces.liquid -> "page background lit by the covers on screen"
                Surfaces.expressive -> "opaque tonal containers coloured from the last-read cover"
                Surfaces.current -> null
            }
        },
    )

    enum class TabBar { current, glass, liquid, expressive }
    val tabBar = LabOption(
        key = "tabBar",
        area = "Shell",
        title = "Tab bar",
        summary = "The main navigation bar",
        choices = listOf(TabBar.current, TabBar.glass, TabBar.liquid, TabBar.expressive),
        default = TabBar.current,
        choiceSummary = { choice ->
            when (choice) {
                TabBar.glass -> "spring lens with stretch, shrink to icon on scroll"
                TabBar.liquid -> "refracting glass dock that grows into the Continue surface"
                TabBar.expressive -> "floating toolbar, label on the current tab only, large action beside it"
                TabBar.current -> null
            }
        },
    )

    enum class TabTool { current, perTab }
    val tabTool = LabOption(
        key = "tabTool",
        area = "Shell",
        title = "Tab tool",
        summary = "The trailing circle's job",
        choices = listOf(TabTool.current, TabTool.perTab),
        default = TabTool.current,
        choiceSummary = { choice ->
            when (choice) {
                TabTool.perTab -> "trailing circle is the tab's main tool"
                TabTool.current -> null
            }
        },
    )

    enum class TopEdge { gradient, blur, solid }
    val topEdge = LabOption(
        key = "topEdge",
        area = "Shell",
        title = "Top edge",
        summary = "Top bar background",
        choices = listOf(TopEdge.gradient, TopEdge.blur, TopEdge.solid),
        default = TopEdge.gradient,
    )

    enum class StatusPlace { capsule, dockWave }
    val statusPlace = LabOption(
        key = "statusPlace",
        area = "Shell",
        title = "Status place",
        summary = "Where the status pill lives",
        choices = listOf(StatusPlace.capsule, StatusPlace.dockWave),
        default = StatusPlace.capsule,
        choiceSummary = { choice ->
            when (choice) {
                StatusPlace.capsule -> "above the bar"
                StatusPlace.dockWave -> "a wave inside the bar or dock"
            }
        },
    )

    // Library
    enum class LibraryHeader { pill, largeTitle }
    val libraryHeader = LabOption(
        key = "libraryHeader",
        area = "Library",
        title = "Library header",
        summary = "Library page header",
        choices = listOf(LibraryHeader.pill, LibraryHeader.largeTitle),
        default = LibraryHeader.pill,
        choiceSummary = { choice ->
            when (choice) {
                LibraryHeader.largeTitle -> "large title with a connected category button group under it"
                LibraryHeader.pill -> null
            }
        },
    )

    enum class ContinueSurface { card, shelf, dock, hero }
    val continueSurface = LabOption(
        key = "continueSurface",
        area = "Library",
        title = "Continue surface",
        summary = "How Continue is presented",
        choices = listOf(ContinueSurface.card, ContinueSurface.shelf, ContinueSurface.dock, ContinueSurface.hero),
        default = ContinueSurface.card,
        requires = listOf(
            LabRequirement(
                choice = "dock",
                key = "tabBar",
                value = "liquid",
                reason = "Needs tab bar liquid",
            ),
        ),
        choiceSummary = { choice ->
            when (choice) {
                ContinueSurface.dock -> "a line in the bar that opens into Continue and Up next; no section"
                ContinueSurface.hero -> "one large tonal container with a cookie-shaped cover, wave and split button"
                ContinueSurface.card -> null
                ContinueSurface.shelf -> null
            }
        },
    )

    enum class Tile { current, glass, liquid, expressive }
    val tile = LabOption(
        key = "tile",
        area = "Library",
        title = "Library tile",
        summary = "Library grid tile",
        choices = listOf(Tile.current, Tile.glass, Tile.liquid, Tile.expressive),
        default = Tile.current,
        choiceSummary = { choice ->
            when (choice) {
                Tile.glass -> "caption \"Ch. N · N new\", neutral play button with ring"
                Tile.liquid -> "large cover that leans to the finger, glass chapter strip on it"
                Tile.expressive -> "cover that rounds when pressed, scalloped count badge"
                Tile.current -> null
            }
        },
    )

    enum class LibrarySearch { current, morph }
    val librarySearch = LabOption(
        key = "librarySearch",
        area = "Library",
        title = "Library search",
        summary = "Search entry treatment",
        choices = listOf(LibrarySearch.current, LibrarySearch.morph),
        default = LibrarySearch.current,
    )

    enum class LibraryFilter { sheet, menu }
    val libraryFilter = LabOption(
        key = "libraryFilter",
        area = "Library",
        title = "Library filter",
        summary = "Where library filters open",
        choices = listOf(LibraryFilter.sheet, LibraryFilter.menu),
        default = LibraryFilter.sheet,
    )

    enum class SelectionBar { current, actionBar }
    val selectionBar = LabOption(
        key = "selectionBar",
        area = "Library",
        title = "Selection bar",
        summary = "Selection affordance",
        choices = listOf(SelectionBar.current, SelectionBar.actionBar),
        default = SelectionBar.current,
    )

    // Feed
    enum class FeedRow { current, glass, expressive }
    val feedRow = LabOption(
        key = "feedRow",
        area = "Feed",
        title = "Feed row",
        summary = "Feed chapter rows",
        choices = listOf(FeedRow.current, FeedRow.glass, FeedRow.expressive),
        default = FeedRow.current,
        choiceSummary = { choice ->
            when (choice) {
                FeedRow.glass -> "tinted chapter chips, read rows fade in place"
                FeedRow.expressive -> "joined-corner list rows, cookie thumbnails, count pill"
                FeedRow.current -> null
            }
        },
    )

    enum class FeedSegments { current, glassLens }
    val feedSegments = LabOption(
        key = "feedSegments",
        area = "Feed",
        title = "Feed segments",
        summary = "Feed segmented control",
        choices = listOf(FeedSegments.current, FeedSegments.glassLens),
        default = FeedSegments.current,
    )

    // Browse
    enum class BrowseSearch { icon, field }
    val browseSearch = LabOption(
        key = "browseSearch",
        area = "Browse",
        title = "Browse search",
        summary = "Browse search entry",
        choices = listOf(BrowseSearch.icon, BrowseSearch.field),
        default = BrowseSearch.icon,
    )

    enum class InLibraryMark { off, on }
    val inLibraryMark = LabOption(
        key = "inLibraryMark",
        area = "Browse",
        title = "In-library mark",
        summary = "Mark sources holding library titles",
        choices = listOf(InLibraryMark.off, InLibraryMark.on),
        default = InLibraryMark.off,
    )

    // Reader
    enum class ReaderBars { current, glass, liquid, expressive }
    val readerBars = LabOption(
        key = "readerBars",
        area = "Reader",
        title = "Reader bars",
        summary = "Reader control bars",
        choices = listOf(ReaderBars.current, ReaderBars.glass, ReaderBars.liquid, ReaderBars.expressive),
        default = ReaderBars.current,
        choiceSummary = { choice ->
            when (choice) {
                ReaderBars.glass -> "one bar, thick slider, four labelled actions"
                ReaderBars.liquid -> "clear glass, drop-shaped slider that stretches"
                ReaderBars.expressive -> "tonal floating bar, wave slider, connected action group"
                ReaderBars.current -> null
            }
        },
    )

    enum class ReaderDisplay { full, half }
    val readerDisplay = LabOption(
        key = "readerDisplay",
        area = "Reader",
        title = "Reader display",
        summary = "Reader bars coverage",
        choices = listOf(ReaderDisplay.full, ReaderDisplay.half),
        default = ReaderDisplay.full,
        choiceSummary = { choice ->
            when (choice) {
                ReaderDisplay.half -> "floating, no dimming"
                ReaderDisplay.full -> null
            }
        },
    )

    enum class ReaderChapters { full, half }
    val readerChapters = LabOption(
        key = "readerChapters",
        area = "Reader",
        title = "Reader chapters",
        summary = "Chapter list in the reader",
        choices = listOf(ReaderChapters.full, ReaderChapters.half),
        default = ReaderChapters.full,
    )

    enum class ReaderEnd { current, card }
    val readerEnd = LabOption(
        key = "readerEnd",
        area = "Reader",
        title = "Reader end",
        summary = "End-of-chapter screen",
        choices = listOf(ReaderEnd.current, ReaderEnd.card),
        default = ReaderEnd.current,
    )

    // Sheets
    enum class SheetContainer { attached, floating }
    val sheetContainer = LabOption(
        key = "sheetContainer",
        area = "Sheets",
        title = "Sheet container",
        summary = "Bottom sheet attachment",
        choices = listOf(SheetContainer.attached, SheetContainer.floating),
        default = SheetContainer.attached,
        choiceSummary = { choice ->
            when (choice) {
                SheetContainer.floating -> "inset glass below full height"
                SheetContainer.attached -> null
            }
        },
    )

    enum class TrackingLayout { current, compact }
    val trackingLayout = LabOption(
        key = "trackingLayout",
        area = "Sheets",
        title = "Tracking layout",
        summary = "Tracker rows layout",
        choices = listOf(TrackingLayout.current, TrackingLayout.compact),
        default = TrackingLayout.current,
    )

    enum class MenuStyle { current, unified }
    val menuStyle = LabOption(
        key = "menuStyle",
        area = "Sheets",
        title = "Menu style",
        summary = "Context menus",
        choices = listOf(MenuStyle.current, MenuStyle.unified),
        default = MenuStyle.current,
        choiceSummary = { choice ->
            when (choice) {
                MenuStyle.unified -> "icon, label, value, destructive last"
                MenuStyle.current -> null
            }
        },
    )

    // Title page
    enum class TitleActions { current, filled }
    val titleActions = LabOption(
        key = "titleActions",
        area = "Title page",
        title = "Title actions",
        summary = "Title page action row",
        choices = listOf(TitleActions.current, TitleActions.filled),
        default = TitleActions.current,
    )

    enum class ContinueProgress { off, on }
    val continueProgress = LabOption(
        key = "continueProgress",
        area = "Title page",
        title = "Continue progress",
        summary = "Progress ring on the Continue button",
        choices = listOf(ContinueProgress.off, ContinueProgress.on),
        default = ContinueProgress.off,
    )

    enum class ChapterRow { current, glass, expressive }
    val chapterRow = LabOption(
        key = "chapterRow",
        area = "Title page",
        title = "Chapter row",
        summary = "Chapter list rows",
        choices = listOf(ChapterRow.current, ChapterRow.glass, ChapterRow.expressive),
        default = ChapterRow.current,
        choiceSummary = { choice ->
            when (choice) {
                ChapterRow.glass -> "dim read rows, progress on the current row"
                ChapterRow.expressive -> "joined-corner rows"
                ChapterRow.current -> null
            }
        },
    )

    enum class ChapterSwipe { current, follow }
    val chapterSwipe = LabOption(
        key = "chapterSwipe",
        area = "Title page",
        title = "Chapter swipe",
        summary = "Chapter row swipe",
        choices = listOf(ChapterSwipe.current, ChapterSwipe.follow),
        default = ChapterSwipe.current,
        choiceSummary = { choice ->
            when (choice) {
                ChapterSwipe.follow -> "row follows the finger, labelled panel"
                ChapterSwipe.current -> null
            }
        },
    )

    // Settings
    enum class SettingChoices { push, menu }
    val settingChoices = LabOption(
        key = "settingChoices",
        area = "Settings",
        title = "Setting choices",
        summary = "How settings with choices open",
        choices = listOf(SettingChoices.push, SettingChoices.menu),
        default = SettingChoices.push,
    )

    enum class ListRows { current, unified }
    val listRows = LabOption(
        key = "listRows",
        area = "Settings",
        title = "Settings rows",
        summary = "Grouped settings rows",
        choices = listOf(ListRows.current, ListRows.unified),
        default = ListRows.current,
        choiceSummary = { choice ->
            when (choice) {
                ListRows.unified -> "downloads, extensions, errors share one row"
                ListRows.current -> null
            }
        },
    )

    val all: List<LabOption<*>> = listOf(
        pressShape,
        buttonGroups,
        chipShape,
        switchStretch,
        springFeel,
        haptics,
        progressStyle,
        surfaces,
        tabBar,
        tabTool,
        topEdge,
        statusPlace,
        libraryHeader,
        continueSurface,
        tile,
        librarySearch,
        libraryFilter,
        selectionBar,
        feedRow,
        feedSegments,
        browseSearch,
        inLibraryMark,
        readerBars,
        readerDisplay,
        readerChapters,
        readerEnd,
        sheetContainer,
        trackingLayout,
        menuStyle,
        titleActions,
        continueProgress,
        chapterRow,
        chapterSwipe,
        settingChoices,
        listRows,
    )

    private val byKey: Map<String, LabOption<*>> = all.associateBy { it.key }

    val areas: List<String> get() = all.map { it.area }.distinct()

    fun option(key: String): LabOption<*>? = byKey[key]

    fun options(area: String): List<LabOption<*>> = all.filter { it.area == area }

    fun presetTargets(preset: String): List<LabOption<*>> =
        all.filter { target -> target.choices.any { it.name == preset } }

    fun currentValues(): Map<String, String> = all.associate { option -> option.key to option.getWire() }

    fun currentName(key: String): String? = byKey[key]?.getWire()

    internal fun preference(option: LabOption<*>): Preference<String> =
        preferenceStore.getString(option.prefKey, option.default.name)

    fun applyPreset(preset: String) {
        if (!enabled) return
        for (target in presetTargets(preset)) {
            preference(target).set(preset)
        }
    }

    fun applyMix(result: LabMix.Result) {
        if (!enabled) return
        for ((key, value) in result.accepted) {
            val option = byKey[key] ?: continue
            preference(option).set(value)
        }
    }
}

fun LabOption<*>.getWire(): String {
    val stored = if (DesignLab.enabled) DesignLab.preference(this).get() else null
    return resolve(stored, DesignLab.enabled).name
}

fun LabOption<*>.setWire(name: String) {
    if (!DesignLab.enabled) return
    if (choiceFor(name) == null) return
    DesignLab.preference(this).set(name)
}

fun LabOption<*>.changesWire(): Flow<String> =
    if (!DesignLab.enabled) {
        flowOf(default.name)
    } else {
        DesignLab.preference(this).changes().map { stored -> resolve(stored, enabled = true).name }
    }

@Composable
fun LabOption<*>.stateWire(): State<String> {
    if (!DesignLab.enabled) return remember(this) { mutableStateOf(default.name) }
    val initial = remember(this) { getWire() }
    return remember(this) { changesWire() }.collectAsState(initial = initial)
}

fun <T : Enum<T>> LabOption<T>.get(): T = choiceFor(getWire()) ?: default

fun <T : Enum<T>> LabOption<T>.set(value: T) {
    setWire(value.name)
}

fun <T : Enum<T>> LabOption<T>.changes(): Flow<T> = changesWire().map { choiceFor(it) ?: default }

@Composable
fun <T : Enum<T>> LabOption<T>.state(): State<T> {
    val wire = stateWire()
    return remember(this) { derivedStateOf { choiceFor(wire.value) ?: default } }
}

@Composable
fun <T : Enum<T>> LabOption<T>.collectAsLabState(): State<T> = state()
