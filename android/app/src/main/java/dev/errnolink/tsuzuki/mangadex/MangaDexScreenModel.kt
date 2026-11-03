package dev.errnolink.tsuzuki.mangadex

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.manga.interactor.UpdateManga
import android.content.Context
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Size
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.saver.Image
import eu.kanade.tachiyomi.data.saver.ImageSaver
import eu.kanade.tachiyomi.data.saver.Location
import eu.kanade.tachiyomi.util.system.getBitmapOrNull
import eu.kanade.tachiyomi.util.system.toShareIntent
import exh.md.MangaDexPreferences
import exh.md.handlers.MangaDexDiscover
import exh.md.handlers.MangaDexDiscoverRow
import exh.source.MERGED_SOURCE_ID
import tachiyomi.domain.manga.repository.MangaMergeRepository
import kotlinx.coroutines.withContext
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.util.editCover
import exh.md.dto.CustomListDto
import exh.md.dto.MangaDataDto
import exh.md.dto.MangaListDto
import exh.md.dto.SeasonalDto
import exh.md.handlers.MangaDexArtwork
import exh.md.handlers.MangaDexFeatures
import exh.md.handlers.MangaDexFeedEntry
import exh.md.service.MangaDexFeatureRequests
import exh.md.utils.MdUtil
import exh.source.getMainSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.domain.manga.model.toDomainManga
import mihon.domain.source.interactor.UpdateMangaFromRemote
import okhttp3.Request
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

sealed class MangaDexDestination {
    data object Home : MangaDexDestination()
    data object Covers : MangaDexDestination()
    data object Lists : MangaDexDestination()
    data object ListManga : MangaDexDestination()
    data object Feed : MangaDexDestination()
    data class Shelf(val kind: String, val title: String) : MangaDexDestination()
}

internal data class MangaDexState(
    val loading: Boolean = true,
    val working: Boolean = false,
    val signedIn: Boolean = false,
    val sourceAvailable: Boolean = true,
    val error: String? = null,
    val notice: String? = null,
    val manga: Manga? = null,
    val covers: List<MangaDexArtwork> = emptyList(),
    val lists: List<CustomListDto> = emptyList(),
    val titles: List<Manga> = emptyList(),
    val listName: String = "MDList",
    val entries: List<MangaDexFeedEntry> = emptyList(),
    val readChapterIds: Set<String> = emptySet(),
    val hasNext: Boolean = false,
    val rows: List<MangaDexDiscoverRow> = emptyList(),
)

internal sealed interface MangaDexNavigation {
    data class Title(val id: Long) : MangaDexNavigation
    data class Chapter(val mangaId: Long, val chapterId: Long) : MangaDexNavigation
}

internal class MangaDexScreenModel(
    private val destination: MangaDexDestination,
    private val mangaId: Long? = null,
    private val listId: String? = null,
) : StateScreenModel<MangaDexState>(MangaDexState()) {
    private val sources: SourceManager by injectLazy()
    private val mangaRepository: MangaRepository by injectLazy()
    private val chapterRepository: ChapterRepository by injectLazy()
    private val importManga: NetworkToLocalManga by injectLazy()
    private val updateManga: UpdateManga by injectLazy()
    private val refreshManga: UpdateMangaFromRemote by injectLazy()
    private var source: MangaDex? = null
    private var offset = 0
    private var listIds: List<String> = emptyList()
    private val navigation = Channel<MangaDexNavigation>(Channel.BUFFERED)
    val events = navigation.receiveAsFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (state.value.working) return
        screenModelScope.launch(Dispatchers.IO) {
            mutableState.update { it.copy(loading = true, working = true, error = null, notice = null) }
            try {
                sources.isInitialized.first { it }
                val title = mangaId?.let { mangaRepository.getMangaById(it) }
                val sourceTitle = if (title?.source == MERGED_SOURCE_ID) {
                    Injekt.get<MangaMergeRepository>().getMergedMangaById(title.id)
                        .firstOrNull { sources.get(it.source)?.getMainSource<MangaDex>() != null }
                } else title
                source = if (sourceTitle != null) sources.get(sourceTitle.source)?.getMainSource<MangaDex>() else MdUtil.getEnabledMangaDex(sourceManager = sources)
                val current = source
                mutableState.update { it.copy(sourceAvailable = current != null, signedIn = current?.isLogged() == true, manga = title) }
                if (current == null || (destination in setOf(MangaDexDestination.Feed, MangaDexDestination.Lists) && !current.isLogged())) return@launch
                val features = MangaDexFeatures(current)
                offset = 0
                when (destination) {
                    MangaDexDestination.Home -> {
                        mutableState.update { it.copy(rows = emptyList()) }
                        MangaDexDiscover(features).rows(current.isLogged()).collect { row ->
                            mutableState.update { it.copy(rows = it.rows + row) }
                        }
                    }
                    MangaDexDestination.Covers -> {
                        val covers = features.covers(MdUtil.getMangaId(requireNotNull(sourceTitle).url))
                        mutableState.update { it.copy(covers = covers) }
                    }
                    MangaDexDestination.Lists -> {
                        val lists = features.lists()
                        mutableState.update { it.copy(lists = lists) }
                    }
                    MangaDexDestination.ListManga -> {
                        val list = features.list(requireNotNull(listId))
                        listIds = list.mangaIds
                        mutableState.update { it.copy(listName = list.attributes.name, titles = emptyList()) }
                        loadListPage(features)
                    }
                    MangaDexDestination.Feed -> {
                        val page = features.feed(0)
                        offset = page.nextOffset
                        mutableState.update {
                            it.copy(entries = page.entries, hasNext = page.hasNext, readChapterIds = resolveReadChapterIds(page.entries))
                        }
                    }
                    is MangaDexDestination.Shelf -> {
                        mutableState.update { it.copy(listName = destination.title, titles = emptyList()) }
                        loadShelfPage(features, reset = true)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.update { it.copy(error = e.message ?: "MangaDex could not be reached. Please try again.") }
            } finally {
                mutableState.update { it.copy(loading = false, working = false) }
            }
        }
    }

    fun onResume() {
        val logged = source?.isLogged() == true
        if (!state.value.working && logged != state.value.signedIn) refresh()
    }

    fun loadMore() = action {
        if (!state.value.hasNext) return@action
        val features = MangaDexFeatures(requireNotNull(source))
        when (destination) {
            MangaDexDestination.Feed -> {
                val page = features.feed(offset)
                offset = page.nextOffset
                val merged = (state.value.entries + page.entries).distinctBy { entry -> entry.chapter.id }
                mutableState.update {
                    it.copy(entries = merged, hasNext = page.hasNext, readChapterIds = resolveReadChapterIds(merged))
                }
            }
            MangaDexDestination.ListManga -> loadListPage(features)
            is MangaDexDestination.Shelf -> loadShelfPage(features, reset = false)
            else -> Unit
        }
    }

    private suspend fun loadListPage(features: MangaDexFeatures) {
        val ids = listIds.drop(offset).take(MangaDexFeatureRequests.PAGE_SIZE)
        val manga = features.manga(ids).associateBy { it.id }
        val local = ids.mapNotNull { manga[it] }.map { persist(it) }
        offset += ids.size
        mutableState.update { it.copy(titles = it.titles + local, hasNext = offset < listIds.size) }
    }

    private suspend fun loadShelfPage(features: MangaDexFeatures, reset: Boolean) {
        val kind = (destination as? MangaDexDestination.Shelf)?.kind ?: return
        if (reset) offset = 0
        when (kind) {
            "seasonal", "staff", "neko" -> {
                val listId = when (kind) {
                    "staff" -> MangaDexFeatureRequests.STAFF_PICKS
                    "neko" -> MangaDexFeatureRequests.NEKO_PICKS
                    else -> features.request<SeasonalDto>(MangaDexFeatureRequests.seasonal()).id
                }
                if (reset) {
                    listIds = features.list(listId).mangaIds
                }
                loadListPage(features)
            }
            "popular", "recent" -> {
                val query = if (kind == "popular") {
                    mapOf<String, Any>(
                        "order[followedCount]" to "desc",
                        "hasAvailableChapters" to "true",
                        "createdAtSince" to monthAgoUtc(),
                    )
                } else {
                    mapOf<String, Any>("order[createdAt]" to "desc")
                }
                val page = features.request<MangaListDto>(MangaDexFeatureRequests.browse(offset, query, features.ratings))
                val local = page.data.map { persist(it) }
                offset = page.offset + page.data.size
                mutableState.update { it.copy(titles = it.titles + local, hasNext = offset < page.total) }
            }
            "latest", "follows" -> {
                val page = features.feed(offset, followed = kind == "follows")
                offset = page.nextOffset
                val local = page.entries.distinctBy { entry -> entry.manga.id }.map { persist(it.manga) }
                mutableState.update { state -> state.copy(titles = (state.titles + local).distinctBy { it.id }, hasNext = page.hasNext) }
            }
        }
    }

    private fun monthAgoUtc(): String =
        LocalDateTime.now(ZoneOffset.UTC).minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))

    private suspend fun persist(dto: MangaDataDto): Manga {
        val current = requireNotNull(source)
        return importManga(MdUtil.createMangaEntry(dto, MangaDexFeatures(current).language).toDomainManga(current.id), updateInfo = false)
    }

    fun addToLibrary(manga: Manga) = action {
        check(updateManga.awaitUpdateFavorite(manga.id, true)) { "Could not add this title to your library" }
        val updated = mangaRepository.getMangaById(manga.id)
        mutableState.update { state ->
            state.copy(
                titles = state.titles.map { if (it.id == manga.id) updated else it },
                manga = if (state.manga?.id == manga.id) updated else state.manga,
                notice = "Added to your library",
            )
        }
    }

    fun useCover(cover: MangaDexArtwork) = action {
        val title = mangaRepository.getMangaById(requireNotNull(mangaId))
        check(title.favorite) { "Add this title to your library before choosing a custom cover" }
        val request = Request.Builder().url(cover.url).headers(MangaDexFeatureRequests.headers).build()
        requireNotNull(source).client.newCall(request).awaitSuccess().use { response ->
            response.body.byteStream().use { title.editCover(Injekt.get(), it) }
        }
        MangaDexPreferences().manualCover(title.id).set(true)
        mutableState.update { it.copy(notice = "${cover.label} is now your library cover") }
    }

    fun resetCover() = action {
        val title = mangaRepository.getMangaById(requireNotNull(mangaId))
        Injekt.get<CoverCache>().deleteCustomCover(title.id)
        MangaDexPreferences().manualCover(title.id).set(false)
        MangaDexPreferences().dynamicCoverUrl(MdUtil.getMangaId(title.url)).delete()
        updateManga.awaitUpdateCoverLastModified(title.id)
        refreshManga(title, fetchDetails = true, fetchChapters = false).getOrThrow()
        mutableState.update { it.copy(notice = "Default cover restored") }
    }

    fun saveCover(context: Context, artwork: MangaDexArtwork, share: Boolean) = action {
        val drawable = context.imageLoader.execute(
            ImageRequest.Builder(context).data(artwork.url).size(Size.ORIGINAL).build(),
        ).image?.asDrawable(context.resources)
        val bitmap = drawable?.getBitmapOrNull() ?: error("Could not decode this artwork")
        val uri = Injekt.get<ImageSaver>().save(
            Image.Cover(bitmap, "${state.value.manga?.title.orEmpty()} ${artwork.label}", if (share) Location.Cache else Location.Pictures.create()),
        )
        if (share) withContext(Dispatchers.Main) { context.startActivity(uri.toShareIntent(context)) }
        else mutableState.update { it.copy(notice = "Cover saved to Pictures") }
    }

    private suspend fun resolveReadChapterIds(entries: List<MangaDexFeedEntry>): Set<String> {
        val current = source ?: return emptySet()
        val read = HashSet<String>()
        for ((mangaUuid, group) in entries.groupBy { it.manga.id }) {
            val local = runCatching {
                mangaRepository.getMangaByUrlAndSourceId(MdUtil.buildMangaUrl(mangaUuid), current.id)
            }.getOrNull() ?: continue
            read += chapterRepository.getChapterByMangaId(local.id)
                .asSequence()
                .filter { chapter -> chapter.read }
                .map { chapter -> MdUtil.getChapterId(chapter.url) }
        }
        return read
    }

    fun open(manga: MangaDataDto) = action {
        navigation.send(MangaDexNavigation.Title(persist(manga).id))
    }

    fun open(entry: MangaDexFeedEntry, chapter: Boolean) = action {
        val title = persist(entry.manga)
        if (!chapter) {
            navigation.send(MangaDexNavigation.Title(title.id))
            return@action
        }
        refreshManga(title, fetchDetails = true, fetchChapters = true).getOrThrow()
        val local = chapterRepository.getChapterByUrlAndMangaId("/chapter/${entry.chapter.id}", title.id)
        check(local != null) { "This chapter is not available in your source language or is blocked. Open the manga for other chapters." }
        navigation.send(MangaDexNavigation.Chapter(title.id, local.id))
    }

    private fun action(block: suspend () -> Unit) {
        if (state.value.working) return
        mutableState.update { it.copy(working = true, error = null, notice = null) }
        screenModelScope.launch(Dispatchers.IO) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.update { it.copy(error = e.message ?: "This action could not be completed. Please try again.") }
            } finally {
                mutableState.update { it.copy(working = false) }
            }
        }
    }
}
