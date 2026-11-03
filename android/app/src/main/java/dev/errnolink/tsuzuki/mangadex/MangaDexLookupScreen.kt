package dev.errnolink.tsuzuki.mangadex

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.ui.shell.ShellList
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.InsetGroupedList
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.SearchField
import dev.errnolink.tsuzuki.designsystem.SegmentedControl
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import exh.md.dto.LookupEntryDto
import exh.md.dto.LookupPageDto
import exh.md.handlers.MangaDexFeatures
import exh.md.service.MangaDexFeatureRequests
import exh.md.utils.MdUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaDexLookupScreen(private val initialQuery: String = "", private val initialType: Int = 0) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { LookupModel() }
        val state by model.state.collectAsState()
        var query by rememberSaveable { mutableStateOf(initialQuery) }
        var type by rememberSaveable { mutableIntStateOf(initialType) }
        fun search() {
            if (type == 2 && query.isNotBlank()) navigator.push(MangaDexListScreen(query.trim(), "MDList"))
            else model.search(query, if (type == 0) "author" else "group", false)
        }
        LaunchedEffect(Unit) { if (initialQuery.isNotBlank()) search() }
        ShellList("MangaDex Search", navigateUp = navigator::pop) {
            item {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SegmentedControl(listOf("Authors", "Groups", "MDList"), type, { type = it }, Modifier.fillMaxWidth())
                    SearchField(query, { query = it }, ::search, Modifier.fillMaxWidth(), placeholder = "Search MangaDex")
                }
            }
            state.error?.let { error -> item { MangaDexEmptyState("Search unavailable", error) } }
            if (state.loading) item { MangaDexLoading("Searching MangaDex…") }
            if (state.entries.isNotEmpty()) item {
                InsetGroupedList {
                    state.entries.forEachIndexed { index, entry ->
                        GroupedRow(
                            title = entry.attributes.name ?: entry.attributes.username ?: entry.id,
                            subtitle = entry.id,
                            chevron = true,
                            divider = index != state.entries.lastIndex,
                            onClick = { navigator.push(BrowseSourceScreen(requireNotNull(state.sourceId), "${state.type}:${entry.id}")) },
                        )
                    }
                }
            }
            if (state.searched && !state.loading && state.entries.isEmpty() && state.error == null) item {
                MangaDexEmptyState("No matches", "Try a different name, or enter a MangaDex UUID in the source search.")
            }
            if (state.hasNext && !state.loading) item {
                PillButton("More results", { model.search(state.query, state.type, true) }, modifier = Modifier.padding(horizontal = 20.dp))
            }
        }
    }
}

private data class LookupState(
    val entries: List<LookupEntryDto> = emptyList(),
    val loading: Boolean = false,
    val searched: Boolean = false,
    val error: String? = null,
    val sourceId: Long? = null,
    val query: String = "",
    val type: String = "author",
    val offset: Int = 0,
    val hasNext: Boolean = false,
)

private class LookupModel : StateScreenModel<LookupState>(LookupState()) {
    fun search(query: String, type: String, more: Boolean) {
        if (state.value.loading || query.isBlank()) return
        mutableState.update { it.copy(loading = true, error = null, query = query, type = type, entries = if (more) it.entries else emptyList()) }
        screenModelScope.launch(Dispatchers.IO) {
            try {
                val sources = Injekt.get<SourceManager>()
                sources.isInitialized.first { it }
                val source = MdUtil.getEnabledMangaDex(sourceManager = sources) ?: error("Enable a MangaDex source in Browse first")
                val page = MangaDexFeatures(source).request<LookupPageDto>(
                    MangaDexFeatureRequests.lookup(type, query.trim(), if (more) state.value.offset else 0),
                )
                mutableState.update {
                    it.copy(entries = it.entries + page.data, sourceId = source.id, offset = page.offset + page.data.size, hasNext = page.offset + page.data.size < page.total)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.update { it.copy(error = e.message ?: "MangaDex could not be reached") }
            } finally {
                mutableState.update { it.copy(loading = false, searched = true) }
            }
        }
    }
}
