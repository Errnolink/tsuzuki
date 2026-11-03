package mihon.feature.migration.config

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Deselect
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.compose.ui.util.fastForEachIndexed
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.browse.components.SourceIcon
import dev.errnolink.tsuzuki.designsystem.Ellipsis
import dev.errnolink.tsuzuki.designsystem.GripVertical
import dev.errnolink.tsuzuki.designsystem.PullDownAction
import dev.errnolink.tsuzuki.designsystem.TopBarSearch
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.rememberTopBarSearch
import dev.errnolink.tsuzuki.ui.settings.SettingsScaffold
import dev.errnolink.tsuzuki.ui.shell.ShellTopAction
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.update
import mihon.feature.migration.list.MigrationListScreen
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.source.model.Source
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.Pill
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.icons.FlagEmoji
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Screen that allows the user to configure which sources should be used when migrating manga.
 *
 * It displays available sources, supports searching and bulk selection, and opens a migration
 * configuration bottom sheet before starting the actual migration flow for the selected sources.
 *
 * @param mangaIds IDs of the manga that will be migrated using the sources configured on this screen.
 */
class MigrationConfigScreen(private val mangaIds: Collection<Long>) : Screen() {

    constructor(mangaId: Long) : this(listOf(mangaId))

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { ScreenModel() }
        val state by screenModel.state.collectAsState()

        // KMK -->
        var searchQuery by remember { mutableStateOf("") }
        BackHandler(enabled = searchQuery.isNotBlank()) {
            searchQuery = ""
        }
        // KMK <--

        var migrationSheetOpen by rememberSaveable { mutableStateOf(false) }

        fun continueMigration(openSheet: Boolean, extraSearchQuery: String?) {
            // KMK -->
            // val mangaId = mangaIds.singleOrNull()
            // if (mangaId == null && openSheet) {
            if (openSheet) {
                // KMK <--
                migrationSheetOpen = true
                return
            }
            val screen = // KMK --> if (mangaId == null) {
                MigrationListScreen(mangaIds, extraSearchQuery, screenModel.sourcePreferences.migrationSmartSearchSingleEntry().get())
            // KMK -->
            // } else {
            //     MigrateSearchScreen(mangaId)
            // }
            // KMK <--
            navigator.replace(screen)
        }

        if (state.isLoading) {
            LoadingScreen()
            return
        }

        val (selectedSources, availableSources) = state.sources
            // KMK -->
            .filter { sources ->
                if (searchQuery.isBlank()) return@filter true
                val source = sources.source
                searchQuery.split(",").any {
                    val input = it.trim()
                    if (input.isEmpty()) return@any false
                    source.name.contains(input, ignoreCase = true) ||
                        source.id == input.toLongOrNull()
                }
            }
            // KMK <--
            .partition { it.isSelected }
        val showLanguage by remember(state) {
            derivedStateOf {
                state.sources.distinctBy { it.source.lang }.size > 1
            }
        }

        val lazyListState = rememberLazyListState()
        val reorderableState = rememberReorderableLazyListState(lazyListState, PaddingValues(0.dp)) { from, to ->
            val fromIndex = selectedSources.indexOfFirst { it.id == from.key }
            val toIndex = selectedSources.indexOfFirst { it.id == to.key }
            if (fromIndex == -1 || toIndex == -1) return@rememberReorderableLazyListState
            screenModel.orderSource(fromIndex, toIndex)
        }
        val search: TopBarSearch = rememberTopBarSearch(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            placeholder = stringResource(KMR.strings.action_search_for_source),
        )
        // KMK -->
        SettingsScaffold(
            title = stringResource(SYMR.strings.select_sources),
            navigateUp = navigator::pop,
            state = lazyListState,
            search = search,
            actions = listOf(
                ShellTopAction(
                    stringResource(MR.strings.migrationConfigScreen_selectPinnedLabel),
                    Icons.Outlined.PushPin,
                    { screenModel.toggleSelection(ScreenModel.SelectionConfig.Pinned) },
                ),
                ShellTopAction(
                    stringResource(MR.strings.migrationConfigScreen_selectNoneLabel),
                    Icons.Outlined.Deselect,
                    { screenModel.toggleSelection(ScreenModel.SelectionConfig.None) },
                ),
                ShellTopAction(
                    "More actions",
                    TsuzukiIcons.Ellipsis,
                    {},
                    menuTitle = "More actions",
                    menu = listOf(
                        PullDownAction(
                            stringResource(MR.strings.migrationConfigScreen_selectEnabledLabel),
                            onClick = { screenModel.toggleSelection(ScreenModel.SelectionConfig.Enabled) },
                        ),
                        PullDownAction(
                            stringResource(MR.strings.migrationConfigScreen_selectAllLabel),
                            onClick = { screenModel.toggleSelection(ScreenModel.SelectionConfig.All) },
                        ),
                    ),
                ),
            ),
            bottomBar = {
                androidx.compose.animation.AnimatedVisibility(
                    visible = selectedSources.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                    ) {
                        dev.errnolink.tsuzuki.designsystem.PillButton(
                            stringResource(MR.strings.migrationConfigScreen_continueButtonText),
                            {
                                screenModel.saveSources()
                                continueMigration(openSheet = true, extraSearchQuery = null)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            },
        ) {
            listOf(selectedSources, availableSources).fastForEachIndexed { listIndex, sources ->
                val selectedSourceList = listIndex == 0
                if (sources.isNotEmpty()) {
                    val headerPrefix = if (selectedSourceList) "selected" else "available"
                    item("$headerPrefix-header") {
                        dev.errnolink.tsuzuki.ui.shell.ShellCaption(
                            stringResource(
                                resource = if (selectedSourceList) {
                                    MR.strings.migrationConfigScreen_selectedHeader
                                } else {
                                    MR.strings.migrationConfigScreen_availableHeader
                                },
                            ),
                            Modifier.padding(horizontal = 20.dp, vertical = 8.dp).animateItem(),
                        )
                    }
                }
                itemsIndexed(
                    items = sources,
                    key = { _, item -> if (selectedSourceList) item.id else "available-${item.id}" },
                ) { index, item ->
                    SourceItemContainer(
                        firstItem = index == 0,
                        lastItem = index == (sources.size - 1),
                        source = item,
                        showLanguage = showLanguage,
                        dragEnabled = selectedSourceList && sources.size > 1,
                        state = reorderableState,
                        key = { if (selectedSourceList) it.id else "available-${it.id}" },
                        onClick = { screenModel.toggleSelection(item.id) },
                    )
                }
            }
        }
        // KMK <--

        if (migrationSheetOpen) {
            MigrationConfigScreenSheet(
                preferences = screenModel.sourcePreferences,
                onDismissRequest = { migrationSheetOpen = false },
                onStartMigration = { extraSearchQuery ->
                    migrationSheetOpen = false
                    continueMigration(openSheet = false, extraSearchQuery = extraSearchQuery)
                },
                // KMK -->
                isSingleEntry = mangaIds.size == 1,
                // KMK <--
            )
        }
    }

    // KMK -->
    @Composable
    private fun LazyItemScope.SourceItemContainer(
        firstItem: Boolean,
        lastItem: Boolean,
        source: MigrationSource,
        showLanguage: Boolean,
        dragEnabled: Boolean,
        state: ReorderableLazyListState,
        key: (MigrationSource) -> Any,
        onClick: () -> Unit,
    ) {
        ReorderableItem(
            state = state,
            key = key(source),
            enabled = dragEnabled,
        ) { _ ->
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .animateItem()
                    .clip(RoundedCornerShape(14.dp))
                    .background(TsuzukiTheme.colors.grouped)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SourceIcon(source = source.source, modifier = Modifier.size(32.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    dev.errnolink.tsuzuki.designsystem.TsuzukiText(source.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (showLanguage) {
                        dev.errnolink.tsuzuki.designsystem.TsuzukiText(
                            FlagEmoji.getEmojiLangFlag(source.shortLanguage) + " (" + source.upperShortLanguage + ")",
                            style = dev.errnolink.tsuzuki.designsystem.TsuzukiTheme.typography.footnote,
                            color = TsuzukiTheme.colors.secondary,
                        )
                    }
                }
                if (dragEnabled) {
                    Icon(
                        imageVector = Icons.Outlined.DragHandle,
                        contentDescription = null,
                        tint = TsuzukiTheme.colors.secondary,
                        modifier = Modifier.draggableHandle(),
                    )
                }
            }
        }
    }
    // KMK <--

    private class ScreenModel(
        val sourcePreferences: SourcePreferences = Injekt.get(),
        private val sourceManager: SourceManager = Injekt.get(),
    ) : StateScreenModel<ScreenModel.State>(State()) {

        // KMK -->
        private val pinnedSources by lazy { sourcePreferences.pinnedSources().get().mapNotNull { it.toLongOrNull() } }
        private val disabledSources by lazy { sourcePreferences.disabledSources().get().mapNotNull { it.toLongOrNull() } }
        // KMK <--

        private val sourcesComparator = { includedSources: /* KMK --> */ Map<Long, Int> /* KMK <-- */ ->
            compareBy<MigrationSource>(
                // KMK -->
                // { !it.isSelected },
                { includedSources[it.source.id] ?: Int.MAX_VALUE },
                // KMK <--
                { with(it) { "$name ($shortLanguage)" } },
            )
        }

        init {
            screenModelScope.launchIO {
                initSources()
                mutableState.update { it.copy(isLoading = false) }
            }
        }

        private fun updateSources(action: (List<MigrationSource>) -> List<MigrationSource>) {
            mutableState.update { state ->
                val updatedSources = action(state.sources)
                val includedSources = updatedSources.mapNotNull { if (!it.isSelected) null else it.id }
                    // KMK -->
                    .mapIndexed { index, id -> id to index }.toMap()
                // KMK <--
                state.copy(sources = updatedSources.sortedWith(sourcesComparator(includedSources)))
            }
            saveSources()
        }

        private fun initSources() {
            val languages = sourcePreferences.enabledLanguages().get()
            val includedSources = sourcePreferences.migrationSources().get()
                // KMK -->
                .mapIndexed { index, id -> id to index }.toMap()
            // KMK <--
            val sources = sourceManager
                // KMK -->
                .getVisibleSources()
                // KMK <--
                .asSequence()
                .filterIsInstance<HttpSource>()
                .filter { it.lang in languages }
                .map {
                    val source = Source(
                        id = it.id,
                        lang = it.lang,
                        name = it.name,
                        supportsLatest = false,
                        isStub = false,
                    )
                    MigrationSource(
                        source = source,
                        isSelected = when {
                            includedSources.isNotEmpty() -> source.id in includedSources
                            pinnedSources.isNotEmpty() -> source.id in pinnedSources
                            else -> source.id !in disabledSources
                        },
                    )
                }
                .toList()

            mutableState.update { state ->
                state.copy(sources = sources.sortedWith(sourcesComparator(includedSources)))
            }
        }

        fun toggleSelection(id: Long) {
            updateSources { sources ->
                sources.map { source ->
                    source.copy(isSelected = if (source.source.id == id) !source.isSelected else source.isSelected)
                }
            }
        }

        fun toggleSelection(config: SelectionConfig) {
            val isSelected: (Long) -> Boolean = {
                when (config) {
                    SelectionConfig.All -> true
                    SelectionConfig.None -> false
                    SelectionConfig.Pinned -> it in pinnedSources
                    SelectionConfig.Enabled -> it !in disabledSources
                }
            }
            updateSources { sources ->
                sources.map { source ->
                    source.copy(isSelected = isSelected(source.source.id))
                }
            }
        }

        fun orderSource(from: Int, to: Int) {
            updateSources {
                it.toMutableList()
                    .apply {
                        add(to, removeAt(from))
                    }
                    .toList()
            }
        }

        fun saveSources() {
            state.value.sources
                .filter { source -> source.isSelected }
                .map { source -> source.source.id }
                .let { sources -> sourcePreferences.migrationSources().set(sources) }
        }

        data class State(
            val isLoading: Boolean = true,
            val sources: List<MigrationSource> = emptyList(),
        )

        enum class SelectionConfig {
            All,
            None,
            Pinned,
            Enabled,
        }
    }

    data class MigrationSource(
        val source: Source,
        val isSelected: Boolean,
        // KMK -->
        val shortLanguage: String = LocaleHelper.getShortDisplayName(source.lang),
        val upperShortLanguage: String = shortLanguage.uppercase(),
        // KMK <--
    ) {
        val id: Long
            inline get() = source.id

        val name: String
            inline get() = source.name
    }
}
