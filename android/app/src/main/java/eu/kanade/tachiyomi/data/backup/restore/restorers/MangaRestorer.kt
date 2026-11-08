package eu.kanade.tachiyomi.data.backup.restore.restorers

import dev.errnolink.tsuzuki.mangadex.backup.NekoBackupMapper
import kotlinx.serialization.json.JsonObject
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupFlatMetadata
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupMergedMangaReference
import eu.kanade.tachiyomi.data.backup.models.BackupTracking
import exh.EXHMigrations
import exh.source.MERGED_SOURCE_ID
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.data.UpdateStrategyColumnAdapter
import tachiyomi.data.manga.MangaMapper
import tachiyomi.data.manga.MergedMangaMapper
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.interactor.FetchInterval
import tachiyomi.domain.manga.interactor.GetFlatMetadataById
import tachiyomi.domain.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.manga.interactor.InsertFlatMetadata
import tachiyomi.domain.manga.interactor.SetCustomMangaInfo
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.domain.track.model.Track
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.ZonedDateTime
import java.util.Date
import kotlin.math.max

class MangaRestorer(
    private var isSync: Boolean = false,

    private val handler: DatabaseHandler = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val getMangaByUrlAndSourceId: GetMangaByUrlAndSourceId = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
    private val getTracks: GetTracks = Injekt.get(),
    private val insertTrack: InsertTrack = Injekt.get(),
    fetchInterval: FetchInterval = Injekt.get(),
    // SY -->
    private val setCustomMangaInfo: SetCustomMangaInfo = Injekt.get(),
    private val insertFlatMetadata: InsertFlatMetadata = Injekt.get(),
    private val getFlatMetadataById: GetFlatMetadataById = Injekt.get(),
    // SY <--
) {
    private var now = ZonedDateTime.now()
    private var currentFetchWindow = fetchInterval.getWindow(now)

    init {
        now = ZonedDateTime.now()
        currentFetchWindow = fetchInterval.getWindow(now)
    }

    suspend fun sortByNew(backupMangas: List<BackupManga>): List<BackupManga> {
        val urlsBySource = handler.awaitList { mangasQueries.getAllMangaSourceAndUrl() }
            .groupBy({ it.source }, { it.url })

        return backupMangas
            .sortedWith(
                // KMK -->
                compareBy<BackupManga> { it.source == MERGED_SOURCE_ID }
                    // KMK <--
                    .then(compareBy { it.url in urlsBySource[it.source].orEmpty() })
                    .then(compareByDescending { it.lastModifiedAt }),
            )
    }

    /**
     * Restore a single manga
     */
    suspend fun restore(
        backupManga: BackupManga,
        backupCategories: List<BackupCategory>,
    ) {
        handler.await(inTransaction = true) {
            val dbManga = findExistingManga(backupManga)
            var manga = backupManga.getMangaImpl()
            // SY -->
            manga = EXHMigrations.migrateBackupEntry(manga)
            // SY <--
            var backupWins = true
            var restoredManga = if (dbManga == null) {
                restoreNewManga(manga)
            } else {
                val (updated, wins) = restoreExistingManga(manga, dbManga)
                backupWins = wins
                updated
            }
            val nekoMemo = manga.memo.filterKeys { it.startsWith("neko") } - NekoBackupMapper.HIDDEN_CHILD
            if (nekoMemo.isNotEmpty() || NekoBackupMapper.HIDDEN_CHILD in restoredManga.memo) {
                restoredManga = updateManga(
                    restoredManga.copy(memo = JsonObject(restoredManga.memo - NekoBackupMapper.HIDDEN_CHILD + nekoMemo)),
                )
            }

            // KMK -->
            if (isSync && backupManga.source == MERGED_SOURCE_ID) {
                mangasQueries.setSyncing(restoredManga.id)
            }
            // KMK <--

            restoreMangaDetails(
                manga = restoredManga,
                chapters = backupManga.chapters,
                categories = backupManga.categories,
                backupCategories = backupCategories,
                history = backupManga.history,
                tracks = backupManga.tracking,
                excludedScanlators = NekoBackupMapper.excludedScanlators(backupManga),
                // SY -->
                mergedMangaReferences = backupManga.mergedMangaReferences,
                flatMetadata = backupManga.flatMetadata,
                customManga = backupManga.getCustomMangaInfo(),
                // SY <--
                // KMK -->
                replaceMergedReferences = backupWins || backupManga.source == MERGED_SOURCE_ID &&
                    (backupManga.mergeMangaList.isNotEmpty() || backupManga.mergedMangaUrl != null),
                // KMK <--
            )
            if (backupManga.source == MERGED_SOURCE_ID && (backupManga.mergeMangaList.isNotEmpty() || backupManga.mergedMangaUrl != null)) {
                restoreNekoChapterOwnership(backupManga.mergedMangaReferences)
                handler.await(inTransaction = true) {
                    backupManga.mergedMangaReferences.forEach { reference ->
                        if (reference.mangaSourceId != MERGED_SOURCE_ID) {
                            mangasQueries.clearFavoriteByUrlAndSource(reference.mangaUrl, reference.mangaSourceId)
                        }
                    }
                }
            }

            if (isSync) {
                mangasQueries.resetIsSyncing()
                chaptersQueries.resetIsSyncing()
            }
        }
    }

    private suspend fun findExistingManga(backupManga: BackupManga): Manga? {
        return getMangaByUrlAndSourceId.await(backupManga.url, backupManga.source)
    }

    private suspend fun restoreExistingManga(manga: Manga, dbManga: Manga): Pair<Manga, Boolean> {
        // KMK -->
        val backupWins = manga.version > dbManga.version ||
            (manga.version == dbManga.version && manga.lastModifiedAt > dbManga.lastModifiedAt) ||
            (manga.version == dbManga.version && manga.lastModifiedAt == dbManga.lastModifiedAt && isSync)
        // KMK <--
        return if (backupWins) {
            updateManga(
                dbManga.copyFrom(manga).copy(
                    id = dbManga.id,
                    // KMK -->
                    favorite = if (isSync) manga.favorite else dbManga.favorite || manga.favorite,
                    // KMK <--
                ),
            ) to true
        } else {
            updateManga(manga.copyFrom(dbManga).copy(id = dbManga.id)) to false
        }
    }

    private fun Manga.copyFrom(newer: Manga): Manga {
        return this.copy(
            favorite = this.favorite || newer.favorite,
            // SY -->
            ogAuthor = newer.author,
            ogArtist = newer.artist,
            ogDescription = newer.description,
            ogGenre = newer.genre,
            ogThumbnailUrl = newer.thumbnailUrl,
            ogStatus = newer.status,
            // SY <--
            initialized = this.initialized || newer.initialized,
            version = newer.version,
        )
    }

    suspend fun updateManga(manga: Manga): Manga {
        handler.await(true) {
            mangasQueries.update(
                source = manga.source,
                url = manga.url,
                // SY -->
                artist = manga.ogArtist,
                author = manga.ogAuthor,
                description = manga.ogDescription,
                genre = manga.ogGenre?.joinToString(separator = ", "),
                title = manga.ogTitle,
                status = manga.ogStatus,
                thumbnailUrl = manga.ogThumbnailUrl,
                // SY <--
                favorite = manga.favorite,
                lastUpdate = manga.lastUpdate,
                nextUpdate = null,
                calculateInterval = null,
                initialized = manga.initialized,
                viewer = manga.viewerFlags,
                chapterFlags = manga.chapterFlags,
                coverLastModified = manga.coverLastModified,
                dateAdded = manga.dateAdded,
                mangaId = manga.id,
                updateStrategy = manga.updateStrategy.let(UpdateStrategyColumnAdapter::encode),
                version = manga.version,
                isSyncing = 1,
                notes = manga.notes,
                memo = manga.memo.let(MemoColumnAdapter::encode),
            )
        }
        return manga
    }

    private suspend fun restoreNewManga(
        manga: Manga,
    ): Manga {
        return manga.copy(
            id = insertManga(manga),
        )
    }

    private suspend fun restoreNekoChapterOwnership(references: List<BackupMergedMangaReference>) {
        val primaryRef = references.firstOrNull { it.isInfoManga } ?: return
        val primary = getMangaByUrlAndSourceId.await(primaryRef.mangaUrl, primaryRef.mangaSourceId) ?: return
        val primaryChapters = getChaptersByMangaId.await(primary.id)
        for (ref in references.filter { !it.isInfoManga && it.mangaSourceId != MERGED_SOURCE_ID }) {
            val child = getMangaByUrlAndSourceId.await(ref.mangaUrl, ref.mangaSourceId) ?: continue
            val childUrls = getChaptersByMangaId.await(child.id).mapTo(hashSetOf()) { it.url }
            val moved = primaryChapters.filter { it.url in childUrls }
            if (moved.isEmpty()) continue
            restoreChapters(child, moved.map { chapter ->
                BackupChapter(
                    url = chapter.url, name = chapter.name, scanlator = chapter.scanlator,
                    read = chapter.read, bookmark = chapter.bookmark, lastPageRead = chapter.lastPageRead,
                    dateFetch = chapter.dateFetch, dateUpload = chapter.dateUpload,
                    chapterNumber = chapter.chapterNumber.toFloat(), sourceOrder = chapter.sourceOrder,
                    version = chapter.version, lastModifiedAt = chapter.lastModifiedAt,
                    memo = MemoColumnAdapter.encode(JsonObject(chapter.memo - NekoBackupMapper.UNRESOLVED_SOURCE)),
                )
            })
            handler.await { chaptersQueries.removeChaptersWithIds(moved.map { it.id }) }
        }
    }

    private suspend fun restoreChapters(manga: Manga, backupChapters: List<BackupChapter>) {
        val dbChaptersByUrl = getChaptersByMangaId.await(manga.id)
            .associateBy { it.url }

        val (existingChapters, newChapters) = backupChapters
            .distinctBy { it.url }
            .mapNotNull { backupChapter ->
                val chapter = backupChapter.toChapterImpl().copy(mangaId = manga.id)
                val dbChapter = dbChaptersByUrl[chapter.url]

                when {
                    dbChapter == null -> chapter // New chapter
                    chapter.forComparison() == dbChapter.forComparison() -> null // Same state; skip
                    else -> updateChapterBasedOnSyncState(chapter, dbChapter) // Update existed chapter
                }
            }
            .partition { it.id > 0 }

        insertNewChapters(newChapters)
        updateExistingChapters(existingChapters)
    }

    private fun updateChapterBasedOnSyncState(chapter: Chapter, dbChapter: Chapter): Chapter =
        dev.errnolink.tsuzuki.data.backup.RestoredChapterMerge.merge(chapter, dbChapter, isSync)

    private fun Chapter.forComparison() =
        this.copy(
            id = 0L,
            mangaId = 0L,
            dateFetch = 0L,
            // KMK -->
            // dateUpload = 0L, some time source loses dateUpload so we overwrite with backup
            // sourceOrder = 0L, although sourceOrder will be updated on refresh, we want to avoid order mixed up anyway
            // KMK <--
            lastModifiedAt = 0L,
            version = 0L,
        )

    private suspend fun insertNewChapters(chapters: List<Chapter>) {
        handler.await(true) {
            chapters.forEach { chapter ->
                chaptersQueries.insert(
                    chapter.mangaId,
                    chapter.url,
                    chapter.name,
                    chapter.scanlator,
                    chapter.read,
                    chapter.bookmark,
                    chapter.lastPageRead,
                    chapter.chapterNumber,
                    chapter.sourceOrder,
                    chapter.dateFetch,
                    chapter.dateUpload,
                    chapter.version,
                    chapter.memo,
                )
            }
        }
    }

    private suspend fun updateExistingChapters(chapters: List<Chapter>) {
        handler.await(true) {
            chapters.forEach { chapter ->
                chaptersQueries.update(
                    mangaId = null,
                    url = null,
                    name = null,
                    scanlator = null,
                    read = chapter.read,
                    bookmark = chapter.bookmark,
                    lastPageRead = chapter.lastPageRead,
                    chapterNumber = null,
                    dateFetch = null,
                    // KMK -->
                    sourceOrder = chapter.sourceOrder,
                    dateUpload = chapter.dateUpload,
                    // KMK <--
                    chapterId = chapter.id,
                    version = chapter.version,
                    isSyncing = 1,
                    memo = chapter.memo.let(MemoColumnAdapter::encode),
                )
            }
        }
    }

    /**
     * Inserts manga and returns id
     *
     * @return id of [Manga], null if not found
     */
    private suspend fun insertManga(manga: Manga): Long {
        return handler.awaitOneExecutable(true) {
            mangasQueries.insert(
                source = manga.source,
                url = manga.url,
                // SY -->
                artist = manga.ogArtist,
                author = manga.ogAuthor,
                description = manga.ogDescription,
                genre = manga.ogGenre,
                title = manga.ogTitle,
                status = manga.ogStatus,
                thumbnailUrl = manga.ogThumbnailUrl,
                // SY <--
                favorite = manga.favorite,
                lastUpdate = manga.lastUpdate,
                nextUpdate = 0L,
                calculateInterval = 0L,
                initialized = manga.initialized,
                viewerFlags = manga.viewerFlags,
                chapterFlags = manga.chapterFlags,
                coverLastModified = manga.coverLastModified,
                dateAdded = manga.dateAdded,
                updateStrategy = manga.updateStrategy,
                version = manga.version,
                notes = manga.notes,
                memo = manga.memo,
            )
            mangasQueries.selectLastInsertedRowId()
        }
    }

    private suspend fun restoreMangaDetails(
        manga: Manga,
        chapters: List<BackupChapter>,
        categories: List<Long>,
        backupCategories: List<BackupCategory>,
        history: List<BackupHistory>,
        tracks: List<BackupTracking>,
        excludedScanlators: List<String>,
        // SY -->
        mergedMangaReferences: List<BackupMergedMangaReference>,
        flatMetadata: BackupFlatMetadata?,
        customManga: CustomMangaInfo?,
        // SY <--
        // KMK -->
        replaceMergedReferences: Boolean,
        // KMK <--
    ): Manga {
        restoreCategories(manga, categories, backupCategories)
        restoreChapters(manga, chapters)
        restoreTracking(manga, tracks)
        restoreHistory(manga, history)
        restoreExcludedScanlators(manga, excludedScanlators)
        updateManga.awaitUpdateFetchInterval(manga, now, currentFetchWindow)
        // SY -->
        restoreMergedMangaReferencesForManga(manga.id, manga.url, mergedMangaReferences, replaceMergedReferences)
        flatMetadata?.let { restoreFlatMetadata(manga.id, it) }
        restoreEditedInfo(customManga?.copy(id = manga.id))
        // SY <--

        return manga
    }

    /**
     * Restores the categories a manga is in.
     * Only if [backupCategories] is provided and user chooses to restore it.
     *
     * @param manga the manga whose categories have to be restored.
     * @param categories the categories to restore.
     */
    private suspend fun restoreCategories(
        manga: Manga,
        categories: List<Long>,
        backupCategories: List<BackupCategory>,
    ) {
        val dbCategories = getCategories.await()
        val dbCategoriesByName = dbCategories.associateBy { it.name }

        val backupCategoriesByOrder = backupCategories.associateBy { it.order }

        val mangaCategoriesToUpdate = categories.mapNotNull { backupCategoryOrder ->
            backupCategoriesByOrder[backupCategoryOrder]?.let { backupCategory ->
                dbCategoriesByName[backupCategory.name]?.let { dbCategory ->
                    Pair(manga.id, dbCategory.id)
                }
            }
        }

        if (mangaCategoriesToUpdate.isNotEmpty()) {
            handler.await(true) {
                mangas_categoriesQueries.deleteMangaCategoryByMangaId(manga.id)
                mangaCategoriesToUpdate.forEach { (mangaId, categoryId) ->
                    mangas_categoriesQueries.insert(mangaId, categoryId)
                }
            }
        }
    }

    private suspend fun restoreHistory(manga: Manga, backupHistory: List<BackupHistory>) {
        if (backupHistory.isEmpty()) return
        val targets = handler.awaitList { historyQueries.getRestoreHistoryTargets(manga.id) }
            .groupBy { it.url }
            .mapValues { (_, rows) -> rows.firstOrNull { it.historyId != null } ?: rows.first() }
        val toUpdate = backupHistory.mapNotNull { history ->
            val target = targets[history.url] ?: return@mapNotNull null
            val item = history.getHistoryImpl()
            val historyId = target.historyId
                ?: return@mapNotNull item.copy(chapterId = target.chapterId)
            val dbReadDuration = target.time_read ?: 0L
            item.copy(
                id = historyId,
                chapterId = target.chapterId,
                readAt = max(item.readAt?.time ?: 0L, target.last_read?.time ?: 0L)
                    .takeIf { it > 0L }
                    ?.let { Date(it) },
                readDuration = max(item.readDuration, dbReadDuration) - dbReadDuration,
            )
        }

        if (toUpdate.isNotEmpty()) {
            handler.await(true) {
                toUpdate.forEach {
                    historyQueries.upsert(
                        it.chapterId,
                        it.readAt,
                        it.readDuration,
                    )
                }
            }
        }
    }

    private suspend fun restoreTracking(manga: Manga, backupTracks: List<BackupTracking>) {
        val dbTrackByTrackerId = getTracks.await(manga.id).associateBy { it.trackerId }

        val (existingTracks, newTracks) = backupTracks
            .mapNotNull {
                val track = it.getTrackImpl()
                val dbTrack = dbTrackByTrackerId[track.trackerId]
                    ?: // New track
                    return@mapNotNull track.copy(
                        id = 0, // Let DB assign new ID
                        mangaId = manga.id,
                    )

                if (track.forComparison() == dbTrack.forComparison()) {
                    // Same state; skip
                    return@mapNotNull null
                }

                // Update to an existing track
                dbTrack.copy(
                    remoteId = track.remoteId,
                    libraryId = track.libraryId,
                    lastChapterRead = max(dbTrack.lastChapterRead, track.lastChapterRead),
                )
            }
            .partition { it.id > 0 }

        if (newTracks.isNotEmpty()) {
            insertTrack.awaitAll(newTracks)
        }
        if (existingTracks.isNotEmpty()) {
            handler.await(true) {
                existingTracks.forEach { track ->
                    manga_syncQueries.update(
                        track.mangaId,
                        track.trackerId,
                        track.remoteId,
                        track.libraryId,
                        track.title,
                        track.lastChapterRead,
                        track.totalChapters,
                        track.status,
                        track.score,
                        track.remoteUrl,
                        track.startDate,
                        track.finishDate,
                        track.private,
                        track.id,
                    )
                }
            }
        }
    }

    // SY -->
    /**
     * Restore the categories from Json
     *
     * @param mergeMangaId the merge manga for the references
     * @param backupMergedMangaReferences the list of backup manga references for the merged manga
     */
    private suspend fun restoreMergedMangaReferencesForManga(
        mergeMangaId: Long,
        mergeMangaUrl: String,
        backupMergedMangaReferences: List<BackupMergedMangaReference>,
        // KMK -->
        replace: Boolean,
        // KMK <--
    ) {
        // KMK -->
        // A newer local owner keeps its own reference set; only the winning
        // backup snapshot may replace it.
        if (!replace) return

        val dbMergedMangaReferences = handler.awaitList {
            mergedQueries.selectByMergeId(mergeMangaId, MergedMangaMapper::map)
        }
        val reconciliation = reconcileMergedReferences(dbMergedMangaReferences, backupMergedMangaReferences)
        if (reconciliation.toInsert.isEmpty() && reconciliation.toUpdate.isEmpty() && reconciliation.toDelete.isEmpty()) {
            return
        }

        val childIdsByKey = reconciliation.toInsert.associate { ref ->
            "${ref.mangaSourceId}|${ref.mangaUrl}" to handler.awaitList {
                mangasQueries.getMangaByUrlAndSource(ref.mangaUrl, ref.mangaSourceId, MangaMapper::mapManga)
            }.firstOrNull()?.id
        }

        handler.await(inTransaction = true) {
            reconciliation.toDelete.forEach { mergedQueries.deleteById(it) }
            reconciliation.toUpdate.forEach { (id, ref) ->
                mergedQueries.updateSettingsById(
                    id = id,
                    getChapterUpdates = ref.getChapterUpdates,
                    downloadChapters = ref.downloadChapters,
                    infoManga = ref.isInfoManga,
                    chapterPriority = ref.chapterPriority.toLong(),
                    chapterSortMode = ref.chapterSortMode.toLong(),
                )
            }
            reconciliation.toInsert.forEach { backupRef ->
                backupRef.getMergedMangaReference().run {
                    mergedQueries.insert(
                        infoManga = isInfoManga,
                        getChapterUpdates = getChapterUpdates,
                        chapterSortMode = chapterSortMode.toLong(),
                        chapterPriority = chapterPriority.toLong(),
                        downloadChapters = downloadChapters,
                        mergeId = mergeMangaId,
                        mergeUrl = mergeUrl.ifEmpty { mergeMangaUrl },
                        mangaId = childIdsByKey["${mangaSourceId}|${mangaUrl}"],
                        mangaUrl = mangaUrl,
                        mangaSource = mangaSourceId,
                    )
                }
            }
        }
        // KMK <--
    }

    private suspend fun restoreFlatMetadata(mangaId: Long, backupFlatMetadata: BackupFlatMetadata) {
        if (getFlatMetadataById.await(mangaId) == null) {
            insertFlatMetadata.await(backupFlatMetadata.getFlatMetadata(mangaId))
        }
    }

    private fun restoreEditedInfo(mangaJson: CustomMangaInfo?) {
        mangaJson ?: return
        setCustomMangaInfo.set(mangaJson)
    }

    private fun BackupManga.getCustomMangaInfo(): CustomMangaInfo? {
        if (customTitle != null ||
            customArtist != null ||
            customAuthor != null ||
            customThumbnailUrl != null || alternativeArtwork != null ||
            customDescription != null ||
            customGenre != null ||
            customStatus != 0
        ) {
            return CustomMangaInfo(
                id = 0L,
                title = customTitle,
                author = customAuthor,
                artist = customArtist,
                thumbnailUrl = customThumbnailUrl ?: alternativeArtwork,
                description = customDescription,
                genre = customGenre,
                status = customStatus.takeUnless { it == 0 }?.toLong(),
            )
        }
        return null
    }
    // SY <--

    private fun Track.forComparison() = this.copy(id = 0L, mangaId = 0L)

    /**
     * Restores the excluded scanlators for the manga.
     *
     * @param manga the manga whose excluded scanlators have to be restored.
     * @param excludedScanlators the excluded scanlators to restore.
     */
    private suspend fun restoreExcludedScanlators(manga: Manga, excludedScanlators: List<String>) {
        if (excludedScanlators.isEmpty()) return
        val existingExcludedScanlators = handler.awaitList {
            excluded_scanlatorsQueries.getExcludedScanlatorsByMangaId(manga.id)
            // KMK -->
        }.toSet()
        val toInsert = excludedScanlators.toSet().subtract(existingExcludedScanlators)
        if (toInsert.isNotEmpty()) {
            handler.await(inTransaction = true) {
                // KMK <--
                toInsert.forEach {
                    excluded_scanlatorsQueries.insert(manga.id, it)
                }
            }
        }
    }
}
