package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupFlatMetadata
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.backupChapterMapper
import eu.kanade.tachiyomi.data.backup.models.backupMergedMangaReferenceMapper
import eu.kanade.tachiyomi.data.backup.models.backupTrackMapper
import eu.kanade.tachiyomi.source.online.MetadataSource
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import exh.source.MERGED_SOURCE_ID
import exh.source.getMainSource
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.MemoColumnAdapter
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.manga.interactor.GetFlatMetadataById
import tachiyomi.domain.manga.model.CustomMangaInfo
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaBackupCreator(
    private val handler: DatabaseHandler = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    // SY -->
    private val sourceManager: SourceManager = Injekt.get(),
    private val getCustomMangaInfo: GetCustomMangaInfo = Injekt.get(),
    private val getFlatMetadataById: GetFlatMetadataById = Injekt.get(),
    // SY <--
) {

    suspend operator fun invoke(mangas: List<Manga>, options: BackupOptions): List<BackupManga> {
        if (mangas.isEmpty()) return emptyList()
        if (mangas.size == 1) {
            return listOf(backupManga(mangas.first(), options))
        }
        return backupMangasBatched(mangas, options)
    }

    private suspend fun backupMangasBatched(mangas: List<Manga>, options: BackupOptions): List<BackupManga> {
        val mangaIds = mangas.map { it.id }

        val chaptersByMangaId: Map<Long, List<BackupChapter>> = if (options.chapters) {
            val map = HashMap<Long, MutableList<BackupChapter>>()
            for (chunk in mangaIds.chunked(BATCH_CHUNK_SIZE)) {
                handler.awaitList {
                    tsuzuki_continue_targetsQueries.selectBackupChaptersByMangaIds(chunk) { _, mangaId, url, name, scanlator, read, bookmark, lastPageRead, chapterNumber, sourceOrder, dateFetch, dateUpload, lastModifiedAt, version, _, memo ->
                        mangaId to BackupChapter(
                            url = url,
                            name = name,
                            chapterNumber = chapterNumber.toFloat(),
                            scanlator = scanlator,
                            read = read != 0L,
                            bookmark = bookmark != 0L,
                            lastPageRead = lastPageRead,
                            dateFetch = dateFetch,
                            dateUpload = dateUpload,
                            sourceOrder = sourceOrder,
                            lastModifiedAt = lastModifiedAt,
                            version = version,
                            memo = MemoColumnAdapter.encode(MemoColumnAdapter.decode(memo)),
                        )
                    }
                }.forEach { (mangaId, chapter) ->
                    map.getOrPut(mangaId) { ArrayList() }.add(chapter)
                }
            }
            map
        } else {
            emptyMap()
        }

        val categoriesByMangaId: Map<Long, List<Long>> = if (options.categories) {
            val map = HashMap<Long, MutableList<Long>>()
            for (chunk in mangaIds.chunked(BATCH_CHUNK_SIZE)) {
                handler.awaitList {
                    tsuzuki_continue_targetsQueries.selectBackupCategoriesByMangaIds(chunk) { mangaId, order ->
                        mangaId to order
                    }
                }.forEach { (mangaId, order) ->
                    map.getOrPut(mangaId) { ArrayList() }.add(order)
                }
            }
            map
        } else {
            emptyMap()
        }

        val historyByMangaId: Map<Long, List<BackupHistory>> = if (options.history) {
            val map = HashMap<Long, MutableList<BackupHistory>>()
            for (chunk in mangaIds.chunked(BATCH_CHUNK_SIZE)) {
                handler.awaitList {
                    tsuzuki_continue_targetsQueries.selectBackupHistoryByMangaIds(chunk) { mangaId, url, lastRead, timeRead ->
                        mangaId to BackupHistory(url, lastRead?.time ?: 0L, timeRead)
                    }
                }.forEach { (mangaId, history) ->
                    map.getOrPut(mangaId) { ArrayList() }.add(history)
                }
            }
            map
        } else {
            emptyMap()
        }

        val tracksByMangaId: Map<Long, List<eu.kanade.tachiyomi.data.backup.models.BackupTracking>> = if (options.tracking) {
            val map = HashMap<Long, MutableList<eu.kanade.tachiyomi.data.backup.models.BackupTracking>>()
            for (chunk in mangaIds.chunked(BATCH_CHUNK_SIZE)) {
                handler.awaitList {
                    manga_syncQueries.getTracksByMangaIds(chunk) { _, mangaId, syncId, remoteId, libraryId, title, lastChapterRead, totalChapters, status, score, remoteUrl, startDate, finishDate, private ->
                        mangaId to eu.kanade.tachiyomi.data.backup.models.BackupTracking(
                            syncId = syncId.toInt(),
                            mediaId = remoteId,
                            libraryId = libraryId ?: 0,
                            title = title,
                            lastChapterRead = lastChapterRead.toFloat(),
                            totalChapters = totalChapters.toInt(),
                            score = score.toFloat(),
                            status = status.toInt(),
                            startedReadingDate = startDate,
                            finishedReadingDate = finishDate,
                            trackingUrl = remoteUrl,
                            private = private,
                        )
                    }
                }.forEach { (mangaId, track) ->
                    map.getOrPut(mangaId) { ArrayList() }.add(track)
                }
            }
            map
        } else {
            emptyMap()
        }

        val excludedScanlatorsByMangaId: Map<Long, List<String>> = run {
            val map = HashMap<Long, MutableList<String>>()
            for (chunk in mangaIds.chunked(BATCH_CHUNK_SIZE)) {
                handler.awaitList {
                    tsuzuki_continue_targetsQueries.selectBackupExcludedScanlatorsByMangaIds(chunk) { mangaId, scanlator ->
                        mangaId to scanlator
                    }
                }.forEach { (mangaId, scanlator) ->
                    map.getOrPut(mangaId) { ArrayList() }.add(scanlator)
                }
            }
            map
        }

        val mergedIds = mangas.filter { it.source == MERGED_SOURCE_ID }.map { it.id }
        val mergedReferencesByMangaId: Map<Long, List<eu.kanade.tachiyomi.data.backup.models.BackupMergedMangaReference>> =
            if (mergedIds.isNotEmpty()) {
                val map = HashMap<Long, MutableList<eu.kanade.tachiyomi.data.backup.models.BackupMergedMangaReference>>()
                for (chunk in mergedIds.chunked(BATCH_CHUNK_SIZE)) {
                    handler.awaitList {
                        tsuzuki_continue_targetsQueries.selectBackupMergedByMangaIds(chunk) { _, infoManga, getChapterUpdates, chapterSortMode, chapterPriority, downloadChapters, mergeId, mergeUrl, _, mangaUrl, mangaSource ->
                            mergeId to eu.kanade.tachiyomi.data.backup.models.BackupMergedMangaReference(
                                isInfoManga = infoManga,
                                getChapterUpdates = getChapterUpdates,
                                chapterSortMode = chapterSortMode.toInt(),
                                chapterPriority = chapterPriority.toInt(),
                                downloadChapters = downloadChapters,
                                mergeUrl = mergeUrl,
                                mangaUrl = mangaUrl,
                                mangaSourceId = mangaSource,
                            )
                        }
                    }.forEach { (mergeId, ref) ->
                        map.getOrPut(mergeId) { ArrayList() }.add(ref)
                    }
                }
                map
            } else {
                emptyMap()
            }

        val metadataMangaIds = mangas.filter {
            sourceManager.get(it.source)?.getMainSource<MetadataSource<*, *>>() != null
        }.map { it.id }

        val flatMetadataByMangaId: Map<Long, BackupFlatMetadata> = if (metadataMangaIds.isNotEmpty()) {
            val metaList = HashMap<Long, exh.metadata.sql.models.SearchMetadata>()
            for (chunk in metadataMangaIds.chunked(BATCH_CHUNK_SIZE)) {
                handler.awaitList {
                    tsuzuki_continue_targetsQueries.selectBackupSearchMetadataByMangaIds(chunk) { mangaId, uploader, extra, indexedExtra, extraVersion ->
                        mangaId to exh.metadata.sql.models.SearchMetadata(
                            mangaId = mangaId,
                            uploader = uploader,
                            extra = extra,
                            indexedExtra = indexedExtra,
                            extraVersion = extraVersion.toInt(),
                        )
                    }
                }.forEach { (mangaId, meta) ->
                    metaList[mangaId] = meta
                }
            }

            val tagsByManga = HashMap<Long, MutableList<exh.metadata.sql.models.SearchTag>>()
            for (chunk in metadataMangaIds.chunked(BATCH_CHUNK_SIZE)) {
                handler.awaitList {
                    tsuzuki_continue_targetsQueries.selectBackupSearchTagsByMangaIds(chunk) { mangaId, id, namespace, name, type ->
                        mangaId to exh.metadata.sql.models.SearchTag(
                            id = id,
                            mangaId = mangaId,
                            namespace = namespace,
                            name = name,
                            type = type.toInt(),
                        )
                    }
                }.forEach { (mangaId, tag) ->
                    tagsByManga.getOrPut(mangaId) { ArrayList() }.add(tag)
                }
            }

            val titlesByManga = HashMap<Long, MutableList<exh.metadata.sql.models.SearchTitle>>()
            for (chunk in metadataMangaIds.chunked(BATCH_CHUNK_SIZE)) {
                handler.awaitList {
                    tsuzuki_continue_targetsQueries.selectBackupSearchTitlesByMangaIds(chunk) { mangaId, id, title, type ->
                        mangaId to exh.metadata.sql.models.SearchTitle(
                            id = id,
                            mangaId = mangaId,
                            title = title,
                            type = type.toInt(),
                        )
                    }
                }.forEach { (mangaId, title) ->
                    titlesByManga.getOrPut(mangaId) { ArrayList() }.add(title)
                }
            }

            metaList.mapValues { (mangaId, meta) ->
                val tags = tagsByManga[mangaId].orEmpty()
                val titles = titlesByManga[mangaId].orEmpty()
                BackupFlatMetadata.copyFrom(exh.metadata.metadata.base.FlatMetadata(meta, tags, titles))
            }
        } else {
            emptyMap()
        }

        return mangas.map { manga ->
            val mangaObject = manga.toBackupManga(
                if (options.customInfo) {
                    getCustomMangaInfo.get(manga.id)
                } else {
                    null
                },
            )

            if (manga.source == MERGED_SOURCE_ID) {
                mangaObject.mergedMangaReferences = mergedReferencesByMangaId[manga.id].orEmpty()
            }

            flatMetadataByMangaId[manga.id]?.let { flatMetadata ->
                mangaObject.flatMetadata = flatMetadata
            }

            mangaObject.excludedScanlators = excludedScanlatorsByMangaId[manga.id].orEmpty()

            if (options.chapters) {
                chaptersByMangaId[manga.id]?.takeUnless(List<BackupChapter>::isEmpty)?.let {
                    mangaObject.chapters = it
                }
            }

            if (options.categories) {
                categoriesByMangaId[manga.id]?.takeUnless(List<Long>::isEmpty)?.let {
                    mangaObject.categories = it
                }
            }

            if (options.tracking) {
                tracksByMangaId[manga.id]?.takeUnless(List<eu.kanade.tachiyomi.data.backup.models.BackupTracking>::isEmpty)?.let {
                    mangaObject.tracking = it
                }
            }

            if (options.history) {
                historyByMangaId[manga.id]?.takeUnless(List<BackupHistory>::isEmpty)?.let {
                    mangaObject.history = it
                }
            }

            mangaObject
        }
    }

    private suspend fun backupManga(manga: Manga, options: BackupOptions): BackupManga {
        // Entry for this manga
        val mangaObject = manga.toBackupManga(
            // SY -->
            if (options.customInfo) {
                getCustomMangaInfo.get(manga.id)
            } else {
                null
            },
            // SY <--
        )

        // SY -->
        if (manga.source == MERGED_SOURCE_ID) {
            mangaObject.mergedMangaReferences = handler.awaitList {
                mergedQueries.selectByMergeId(manga.id, backupMergedMangaReferenceMapper)
            }
        }

        val source = sourceManager.get(manga.source)?.getMainSource<MetadataSource<*, *>>()
        if (source != null) {
            getFlatMetadataById.await(manga.id)?.let { flatMetadata ->
                mangaObject.flatMetadata = BackupFlatMetadata.copyFrom(flatMetadata)
            }
        }
        // SY <--

        mangaObject.excludedScanlators = handler.awaitList {
            excluded_scanlatorsQueries.getExcludedScanlatorsByMangaId(manga.id)
        }

        if (options.chapters) {
            // Backup all the chapters
            handler.awaitList {
                chaptersQueries.getChaptersByMangaId(
                    mangaId = manga.id,
                    applyFilter = 0, // false
                    // KMK -->
                    Manga.CHAPTER_SHOW_NOT_BOOKMARKED,
                    Manga.CHAPTER_SHOW_BOOKMARKED,
                    // KMK <--
                    mapper = backupChapterMapper,
                )
            }
                .takeUnless(List<BackupChapter>::isEmpty)
                ?.let { mangaObject.chapters = it }
        }

        if (options.categories) {
            // Backup categories for this manga
            val categoriesForManga = getCategories.await(manga.id)
            if (categoriesForManga.isNotEmpty()) {
                mangaObject.categories = categoriesForManga.map { it.order }
            }
        }

        if (options.tracking) {
            val tracks = handler.awaitList { manga_syncQueries.getTracksByMangaId(manga.id, backupTrackMapper) }
            if (tracks.isNotEmpty()) {
                mangaObject.tracking = tracks
            }
        }

        if (options.history) {
            val history = handler.awaitList {
                historyQueries.getBackupHistoryByMangaId(manga.id) { url, lastRead, timeRead ->
                    BackupHistory(url, lastRead?.time ?: 0L, timeRead)
                }
            }
            if (history.isNotEmpty()) {
                mangaObject.history = history
            }
        }

        return mangaObject
    }

    companion object {
        private const val BATCH_CHUNK_SIZE = 500
    }
}

private fun Manga.toBackupManga(/* SY --> */customMangaInfo: CustomMangaInfo?/* SY <-- */) =
    BackupManga(
        url = this.url,
        // SY -->
        title = this.ogTitle,
        artist = this.ogArtist,
        author = this.ogAuthor,
        description = this.ogDescription,
        genre = this.ogGenre.orEmpty(),
        status = this.ogStatus.toInt(),
        thumbnailUrl = this.ogThumbnailUrl,
        // SY <--
        favorite = this.favorite,
        source = this.source,
        dateAdded = this.dateAdded,
        viewer = (this.viewerFlags.toInt() and ReadingMode.MASK),
        viewer_flags = this.viewerFlags.toInt(),
        chapterFlags = this.chapterFlags.toInt(),
        updateStrategy = this.updateStrategy,
        lastModifiedAt = this.lastModifiedAt,
        favoriteModifiedAt = this.favoriteModifiedAt,
        version = this.version,
        notes = this.notes,
        initialized = this.initialized,
        memo = MemoColumnAdapter.encode(this.memo),
        // SY -->
    ).also { backupManga ->
        customMangaInfo?.let {
            backupManga.customTitle = it.title
            backupManga.customArtist = it.artist
            backupManga.customAuthor = it.author
            backupManga.customThumbnailUrl = it.thumbnailUrl
            backupManga.customDescription = it.description
            backupManga.customGenre = it.genre
            backupManga.customStatus = it.status?.toInt() ?: 0
        }
    }
// SY <--
