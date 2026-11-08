package dev.errnolink.tsuzuki.ui.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.extension.interactor.GetExtensionsByType
import eu.kanade.domain.source.interactor.GetEnabledSources
import eu.kanade.domain.source.interactor.ToggleSource
import eu.kanade.domain.source.interactor.ToggleSourcePin
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.ui.browse.feed.FeedScreenModel
import eu.kanade.tachiyomi.ui.browse.feed.FeedScreenState
import exh.source.isMdBasedSource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.domain.manga.model.toDomainManga
import dev.errnolink.tsuzuki.data.listing.ListingCache
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.model.Pin
import tachiyomi.domain.source.model.Source as DomainSource
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class BrowseSourceItem(
    val source: DomainSource,
    val isPinned: Boolean,
    val hasUpdate: Boolean = false,
    val isEnabled: Boolean = true,
)

@Immutable
data class BrowseHomeState(
    val isLoadingSources: Boolean = true,
    val pinnedSources: ImmutableList<DomainSource> = persistentListOf(),
    val allSources: ImmutableList<BrowseSourceItem> = persistentListOf(),
    val selectedSourceId: Long? = null,
    val isLoadingPopular: Boolean = false,
    val popularManga: ImmutableList<Manga> = persistentListOf(),
    val isLoadingRecent: Boolean = false,
    val recentManga: ImmutableList<Manga> = persistentListOf(),
    val extensionUpdatesCount: Int = 0,
    val loadError: Boolean = false,
)

class BrowseHomeScreenModel(
    private val getEnabledSources: GetEnabledSources = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val toggleSourcePin: ToggleSourcePin = Injekt.get(),
    private val toggleSource: ToggleSource = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val networkToLocalManga: NetworkToLocalManga = Injekt.get(),
    private val getExtensions: GetExtensionsByType = Injekt.get(),
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val listingCache: ListingCache = Injekt.get(),
) : StateScreenModel<BrowseHomeState>(BrowseHomeState()) {

    init {
        combine(
            getEnabledSources.subscribe(),
            sourcePreferences.pinnedSources().changes(),
            extensionManager.installedExtensionsFlow,
        ) { sources, pinnedSet, installedExts ->
            val extMap = installedExts.associateBy { it.pkgName }
            val uniqueSources = sources.distinctBy { it.id }
            val items = uniqueSources.map { src ->
                val ext = extMap.values.firstOrNull { it.sources.any { s -> s.id == src.id } }
                BrowseSourceItem(
                    source = src,
                    isPinned = Pin.Pinned in src.pin || "${src.id}" in pinnedSet,
                    hasUpdate = ext?.hasUpdate == true,
                    isEnabled = true,
                )
            }
            val pinned = items.filter { it.isPinned }.map { it.source }
            Triple(items, pinned, pinnedSet)
        }
            .flowOn(Dispatchers.IO)
            .onEach { (items, pinned, _) ->
                mutableState.update { state ->
                    val currentSelected = state.selectedSourceId
                    val remembered = sourcePreferences.lastUsedSource().get()
                    val newSelected = if (currentSelected != null && items.any { it.source.id == currentSelected }) {
                        currentSelected
                    } else if (items.any { it.source.id == remembered }) {
                        remembered
                    } else {
                        pinned.firstOrNull { !it.isLocal() }?.id
                            ?: items.firstOrNull { !it.source.isLocal() }?.source?.id
                            ?: pinned.firstOrNull()?.id
                    }
                    state.copy(
                        isLoadingSources = false,
                        allSources = items.toImmutableList(),
                        pinnedSources = pinned.toImmutableList(),
                        selectedSourceId = newSelected,
                    )
                }
            }
            .launchIn(screenModelScope)

        sourcePreferences.extensionUpdatesCount().changes()
            .onEach { count ->
                mutableState.update { it.copy(extensionUpdatesCount = count) }
            }
            .launchIn(screenModelScope)

        screenModelScope.launch {
            state.distinctUntilChanged { old, new -> old.selectedSourceId == new.selectedSourceId }
                .collectLatest { cur ->
                    val sourceId = cur.selectedSourceId ?: return@collectLatest
                    loadSourceShelves(sourceId)
                }
        }
    }

    fun selectSource(sourceId: Long) {
        sourcePreferences.lastUsedSource().set(sourceId)
        mutableState.update { it.copy(selectedSourceId = sourceId) }
    }

    fun retryShelves() {
        val sourceId = state.value.selectedSourceId ?: return
        listingCache.forget(sourceId)
        screenModelScope.launch {
            loadSourceShelves(sourceId)
        }
    }

    fun togglePin(source: DomainSource) {
        screenModelScope.launchIO {
            toggleSourcePin.await(source)
        }
    }

    fun toggleDisable(source: DomainSource) {
        screenModelScope.launchIO {
            toggleSource.await(source)
        }
    }

    fun updateExtension(extension: Extension.Installed) {
        screenModelScope.launchIO {
            extensionManager.updateExtension(extension).collect { }
        }
    }

    fun uninstallExtension(extension: Extension.Installed) {
        extensionManager.uninstallExtension(extension)
    }

    private suspend fun loadSourceShelves(sourceId: Long) = kotlinx.coroutines.coroutineScope {
        mutableState.update { it.copy(isLoadingPopular = true, isLoadingRecent = true, loadError = false) }
        val source = withContext(Dispatchers.IO) {
            sourceManager.isInitialized.first { it }
            sourceManager.getOrStub(sourceId)
        }
        if (source is tachiyomi.domain.source.model.StubSource) {
            mutableState.update {
                it.copy(
                    isLoadingPopular = false,
                    isLoadingRecent = false,
                    popularManga = persistentListOf(),
                    recentManga = persistentListOf(),
                )
            }
            return@coroutineScope
        }

        launch(Dispatchers.IO) {
            try {
                val mangaDex = if (source.isMdBasedSource()) exh.md.utils.MdUtil.getEnabledMangaDex(sourceManager = sourceManager) else null
                val popPage = if (mangaDex != null) {
                    val features = exh.md.handlers.MangaDexFeatures(mangaDex)
                    val since = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusMonths(1)
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
                    runCatching {
                        features.request<exh.md.dto.MangaListDto>(exh.md.service.MangaDexFeatureRequests.popularNew(features.ratings, since))
                            .data.map { exh.md.utils.MdUtil.createMangaEntry(it, features.language) }
                    }.getOrDefault(emptyList())
                } else {
                    runCatching {
                        listingCache.page(ListingCache.Key(sourceId, ListingCache.Kind.Popular, 1)) { source.getPopularManga(1) }.mangas
                    }.getOrDefault(emptyList())
                }
                val domainManga = popPage.map { it.toDomainManga(mangaDex?.id ?: sourceId) }.distinctBy { it.url }
                val localManga = networkToLocalManga(domainManga, updateInfo = false)
                mutableState.update { it.copy(isLoadingPopular = false, popularManga = localManga.toImmutableList()) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                mutableState.update { it.copy(isLoadingPopular = false, popularManga = persistentListOf(), loadError = true) }
            }
        }

        launch(Dispatchers.IO) {
            try {
                val latestPage = if (source.supportsLatest) {
                    runCatching {
                        listingCache.page(ListingCache.Key(sourceId, ListingCache.Kind.Latest, 1)) { source.getLatestUpdates(1) }.mangas
                    }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }
                val domainManga = latestPage.map { it.toDomainManga(sourceId) }.distinctBy { it.url }
                val localManga = networkToLocalManga(domainManga, updateInfo = false)
                mutableState.update { it.copy(isLoadingRecent = false, recentManga = localManga.toImmutableList()) }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                mutableState.update { it.copy(isLoadingRecent = false, recentManga = persistentListOf(), loadError = true) }
            }
        }
    }
}
