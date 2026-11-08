package dev.errnolink.tsuzuki.ui.feed

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import java.time.ZonedDateTime
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.chapter.interactor.SetReadStatus
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.mdlist.MdList
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import exh.md.dto.ChapterDataDto
import exh.md.handlers.MangaDexFeatures
import exh.md.handlers.MangaDexFeedEntry
import exh.md.utils.MdUtil
import exh.source.getMainSource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import logcat.LogPriority
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChapter
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.history.interactor.GetNextChapters
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.updates.interactor.GetUpdates
import tachiyomi.domain.updates.model.UpdatesWithRelations
import mihon.domain.manga.model.toDomainManga
import mihon.domain.source.interactor.UpdateMangaFromRemote
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.time.Instant
import java.time.LocalDate
data class UpNextCard(
    val mangaId: Long,
    val chapterId: Long,
    val mangaTitle: String,
    val chapterName: String,
    val lastPageRead: Long,
    val pageTotal: Int?,
    val progressFraction: Float?,
    val coverData: tachiyomi.domain.manga.model.MangaCover,
    val isResume: Boolean,
)

@Immutable
data class UnifiedFeedState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val upNext: ImmutableList<UpNextCard> = persistentListOf(),
    val upNextLoaded: Boolean = false,
    val dayGroups: Map<LocalDate, List<FeedTitleGroup>> = emptyMap(),
    val totalUnreadCount: Int = 0,
    val selectedChip: FeedChip = FeedChip.ALL,
    val isMangaDexLoggedIn: Boolean = false,
    val mangaDexUsername: String? = null,
    val mangaDexLastChecked: Instant? = null,
    val followsError: Boolean = false,
)

sealed interface FeedNavigation {
    data class Title(val id: Long) : FeedNavigation
    data class Chapter(val mangaId: Long, val chapterId: Long) : FeedNavigation
}

private data class FeedSources(
    val updates: List<UpdatesWithRelations>,
    val follows: List<MangaDexFeedEntry>,
    val favorites: List<Manga>,
)

class UnifiedFeedScreenModel : StateScreenModel<UnifiedFeedState>(UnifiedFeedState()) {

    private val preferenceStore: PreferenceStore by injectLazy()
    private val getHistory: GetHistory by injectLazy()
    private val getNextChapters: GetNextChapters by injectLazy()
    private val getUpdates: GetUpdates by injectLazy()
    private val getManga: GetManga by injectLazy()
    private val getChapter: GetChapter by injectLazy()
    private val setReadStatus: SetReadStatus by injectLazy()
    private val refreshManga: UpdateMangaFromRemote by injectLazy()
    private val importManga: NetworkToLocalManga by injectLazy()
    private val sources: SourceManager by injectLazy()
    private val mangaRepository: MangaRepository by injectLazy()
    private val chapterRepository: ChapterRepository by injectLazy()
    private val downloadManager: DownloadManager by injectLazy()
    private val sourcePreferences: SourcePreferences by injectLazy()
    private val chipPref: Preference<FeedChip> =
        preferenceStore.getEnum("pref_feed_selected_chip", FeedChip.ALL)

    private val navChannel = Channel<FeedNavigation>(Channel.BUFFERED)
    val navigation = navChannel.receiveAsFlow()
    private val followsSource = MutableStateFlow<List<MangaDexFeedEntry>>(emptyList())
    private var mangaDexSource: MangaDex? = null
    @Volatile private var latestSources: FeedSources? = null
    init {
        val savedChip = chipPref.get()
        mutableState.update { it.copy(selectedChip = savedChip) }

        observeUpNext()
        observeFeed()
        refreshMangaDexState()
    }

    private fun observeUpNext() {
        screenModelScope.launchIO {
            getHistory.subscribeRecent(
                limit = 6,
                unfinishedChapter = null,
                nonLibraryEntries = null,
            )
                .distinctUntilChanged()
                .catch { e ->
                    if (e is CancellationException) throw e
                    logcat(LogPriority.ERROR, e) { "Feed up-next flow failed" }
                }
                .collectLatest { histories ->
                    try {
                        val cards = coroutineScope {
                            histories.map { entry ->
                                async {
                                    val next = getNextChapters.await(entry.mangaId, entry.chapterId, onlyUnread = false)
                                    val target = next.firstOrNull() ?: return@async null
                                    val isCurrent = target.id == entry.chapterId && !entry.read
                                    val lastPage = if (isCurrent) entry.lastPageRead else 0L
                                    val totalPages = target.lastPageRead.takeIf { it > 0 }?.toInt()
                                    val fraction = if (isCurrent && totalPages != null && totalPages > 0) {
                                        ((lastPage + 1).toFloat() / totalPages).coerceIn(0f, 1f)
                                    } else null
                                    UpNextCard(
                                        mangaId = entry.mangaId,
                                        chapterId = target.id,
                                        mangaTitle = entry.title,
                                        chapterName = if (target.chapterNumber >= 0) "Ch. ${eu.kanade.presentation.util.formatChapterNumber(target.chapterNumber)}" else target.name,
                                        lastPageRead = lastPage,
                                        pageTotal = totalPages,
                                        progressFraction = fraction,
                                        coverData = entry.coverData,
                                        isResume = isCurrent,
                                    )
                                }
                            }.awaitAll().filterNotNull()
                        }
                        mutableState.update { it.copy(upNext = cards.toImmutableList(), upNextLoaded = true) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR, e) { "Feed up-next mapping failed" }
                        mutableState.update { it.copy(upNext = persistentListOf(), upNextLoaded = true) }
                    }
                }
        }
    }

    private fun observeFeed() {
        screenModelScope.launchIO {
            val updatesFlow = getUpdates.subscribe(
                ZonedDateTime.now().minusMonths(3).toInstant(),
                unread = null,
                started = null,
                bookmarked = null,
                hideExcludedScanlators = false,
            ).distinctUntilChanged()
            val favoritesFlow = mangaRepository.getFavoritesAsFlow()
            combine(updatesFlow, followsSource, favoritesFlow) { updates, follows, favorites ->
                FeedSources(updates, follows, favorites)
            }
                .catch { e ->
                    if (e is CancellationException) throw e
                    logcat(LogPriority.ERROR, e) { "Feed sources flow failed" }
                }
                .collectLatest { sources ->
                    latestSources = sources
                    try {
                        rebuildFeed(sources)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR, e) { "Feed rebuild failed" }
                        mutableState.update {
                            it.copy(
                                isLoading = false,
                                isRefreshing = false,
                                dayGroups = emptyMap(),
                                totalUnreadCount = 0,
                            )
                        }
                    }
                }
        }
    }

    fun refreshMangaDexState() {
        screenModelScope.launchIO {
            sources.isInitialized.first { it }
            mangaDexSource = MdUtil.getEnabledMangaDex(sourceManager = sources)
            val current = mangaDexSource
            val loggedIn = current?.isLogged() == true
            val username = if (loggedIn) current?.getUsername()?.takeIf { it.isNotBlank() } else null

            mutableState.update {
                it.copy(
                    isMangaDexLoggedIn = loggedIn,
                    mangaDexUsername = username,
                )
            }

            if (loggedIn && current != null) {
                mutableState.update { it.copy(followsError = false) }
                try {
                    val features = MangaDexFeatures(current)
                    val feedPage = features.feed(0)
                    followsSource.value = feedPage.entries
                    mutableState.update { it.copy(mangaDexLastChecked = Instant.now()) }
                } catch (e: Exception) {
                    logcat(LogPriority.WARN, e) { "Failed to fetch follows feed" }
                    mutableState.update { it.copy(followsError = true) }
                }
            } else {
                followsSource.value = emptyList()
            }
            latestSources?.let { rebuildFeed(it.copy(follows = followsSource.value)) }
        }
    }

    private fun rebuildFeed(sources: FeedSources) {
        val libItems = sources.updates.map { u ->
            FeedChapterItem(
                id = "lib-${u.mangaId}-${u.chapterId}",
                mangaId = u.mangaId,
                chapterId = u.chapterId,
                mangaTitle = u.mangaTitle,
                chapterName = u.chapterName,
                chapterNumber = parseChapterNumber(u.chapterName),
                dateFetch = u.dateFetch,
                coverUrl = u.coverData.url,
                isLibrary = true,
                isRead = u.read,
                scanlator = u.scanlator,
                sourceId = u.sourceId,
                chapterUrl = u.chapterUrl,
                coverLastModified = u.coverData.lastModified,
            )
        }

        val mdSourceId = mangaDexSource?.id ?: 0L
        val followsItems = sources.follows.map { f ->
            FeedChapterItem(
                id = "md-${f.chapter.id}",
                mangaId = -1L,
                chapterId = null,
                mangaTitle = f.mangaTitle,
                chapterName = f.chapterName,
                chapterNumber = f.chapter.attributes.chapter?.toDoubleOrNull() ?: parseChapterNumber(f.chapterName),
                dateFetch = f.publishedAt.toEpochMilli(),
                coverUrl = f.coverUrl,
                isLibrary = false,
                isRead = false,
                scanlator = f.groups,
                pageCount = f.chapter.attributes.pages,
                mangaDexId = f.manga.id,
                sourceId = mdSourceId,
                chapterUrl = "/chapter/${f.chapter.id}",
            )
        }

        val favUuids = sources.favorites.mapNotNull {
            if (it.source == mdSourceId) MdUtil.getMangaId(it.url) else null
        }.toSet()
        val favMangaIds = sources.favorites.map { it.id }.toSet()

        val merged = FeedMerger.merge(libItems, followsItems, favMangaIds, favUuids)
        val filtered = FeedMerger.filter(merged, state.value.selectedChip, state.value.isMangaDexLoggedIn, sourcePreferences.hideInLibraryFeedItems().get())
        val groups = FeedMerger.groupByDayAndTitle(filtered, java.time.ZoneId.systemDefault())

        mutableState.update {
            it.copy(
                isLoading = false,
                isRefreshing = false,
                dayGroups = groups,
                totalUnreadCount = merged.count { c -> !c.isRead },
            )
        }
    }

    fun setChip(chip: FeedChip) {
        chipPref.set(chip)
        mutableState.update { it.copy(selectedChip = chip) }
        latestSources?.let { rebuildFeed(it) }
    }

    fun refreshAll() {
        mutableState.update { it.copy(isRefreshing = true) }
        LibraryUpdateJob.startNow(Injekt.get())
        refreshMangaDexState()
    }

    fun markGroupAsRead(group: FeedTitleGroup) {
        screenModelScope.launchIO {
            val chapterIds = group.chapters.mapNotNull { it.chapterId }
            if (chapterIds.isNotEmpty()) {
                val domainChapters = chapterIds.mapNotNull { getChapter.await(it) }
                setReadStatus.await(read = true, chapters = domainChapters.toTypedArray())
            }
        }
    }

    fun downloadChapter(chapterItem: FeedChapterItem) {
        val cid = chapterItem.chapterId ?: return
        val mid = chapterItem.mangaId.takeIf { it > 0 } ?: return
        screenModelScope.launchIO {
            val manga = getManga.await(mid) ?: return@launchIO
            val chapter = getChapter.await(cid) ?: return@launchIO
            downloadManager.downloadChapters(manga, listOf(chapter))
        }
    }

    fun openTitle(group: FeedTitleGroup) {
        if (group.mangaId > 0) {
            navChannel.trySend(FeedNavigation.Title(group.mangaId))
        } else if (group.mangaDexId != null) {
            screenModelScope.launchIO {
                val current = mangaDexSource ?: return@launchIO
                val mdEntry = followsSource.value.firstOrNull { it.manga.id == group.mangaDexId } ?: return@launchIO
                val domainManga = importManga(
                    MdUtil.createMangaEntry(mdEntry.manga, MangaDexFeatures(current).language).toDomainManga(current.id),
                    updateInfo = false,
                )
                navChannel.send(FeedNavigation.Title(domainManga.id))
            }
        }
    }

    fun openChapter(group: FeedTitleGroup, target: FeedChapterItem) {
        if (target.chapterId != null && target.mangaId > 0) {
            navChannel.trySend(FeedNavigation.Chapter(target.mangaId, target.chapterId))
        } else if (group.mangaDexId != null) {
            screenModelScope.launchIO {
                val current = mangaDexSource ?: return@launchIO
                val mdEntry = followsSource.value.firstOrNull { it.manga.id == group.mangaDexId } ?: return@launchIO
                val title = importManga(
                    MdUtil.createMangaEntry(mdEntry.manga, MangaDexFeatures(current).language).toDomainManga(current.id),
                    updateInfo = false,
                )
                refreshManga(title, fetchDetails = true, fetchChapters = true).getOrNull()
                val local = chapterRepository.getChapterByUrlAndMangaId(target.chapterUrl, title.id)
                if (local != null) {
                    navChannel.send(FeedNavigation.Chapter(title.id, local.id))
                } else {
                    navChannel.send(FeedNavigation.Title(title.id))
                }
            }
        }
    }

    private fun parseChapterNumber(name: String): Double {
        val match = Regex("""(?:Ch\.|Chapter)\s*([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE).find(name)
        return match?.groupValues?.get(1)?.toDoubleOrNull() ?: -1.0
    }
}
