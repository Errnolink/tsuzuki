package tachiyomi.data.manga

import exh.metadata.metadata.base.FlatMetadata
import exh.metadata.sql.models.SearchMetadata
import exh.metadata.sql.models.SearchTag
import exh.metadata.sql.models.SearchTitle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.manga.repository.MangaMetadataRepository

class MangaMetadataRepositoryImpl(
    private val handler: DatabaseHandler,
) : MangaMetadataRepository {

    override suspend fun getMetadataById(id: Long): SearchMetadata? {
        return handler.awaitOneOrNull { search_metadataQueries.selectByMangaId(id, ::searchMetadataMapper) }
    }

    override fun subscribeMetadataById(id: Long): Flow<SearchMetadata?> {
        return handler.subscribeToOneOrNull { search_metadataQueries.selectByMangaId(id, ::searchMetadataMapper) }
    }

    override suspend fun getTagsById(id: Long): List<SearchTag> {
        return handler.awaitList { search_tagsQueries.selectByMangaId(id, ::searchTagMapper) }
    }

    override fun subscribeTagsById(id: Long): Flow<List<SearchTag>> {
        return handler.subscribeToList { search_tagsQueries.selectByMangaId(id, ::searchTagMapper) }
    }

    override suspend fun getTitlesById(id: Long): List<SearchTitle> {
        return handler.awaitList { search_titlesQueries.selectByMangaId(id, ::searchTitleMapper) }
    }

    override fun subscribeTitlesById(id: Long): Flow<List<SearchTitle>> {
        return handler.subscribeToList { search_titlesQueries.selectByMangaId(id, ::searchTitleMapper) }
    }

    override suspend fun getFavoriteTags(): Map<Long, List<SearchTag>> {
        return handler.awaitList {
            search_tagsQueries.selectAllFavorite { rowId, mangaId, namespace, name, type ->
                SearchTag(mangaId = mangaId, id = rowId, namespace = namespace, name = name, type = type.toInt())
            }
        }.groupBy { it.mangaId }
    }

    override suspend fun getFavoriteTitles(): Map<Long, List<SearchTitle>> {
        return handler.awaitList {
            search_titlesQueries.selectAllFavorite { rowId, mangaId, title, type ->
                SearchTitle(mangaId = mangaId, id = rowId, title = title, type = type.toInt())
            }
        }.groupBy { it.mangaId }
    }

    override fun subscribeFavoriteTags(): Flow<Map<Long, List<SearchTag>>> {
        return handler.subscribeToList {
            search_tagsQueries.selectAllFavorite { rowId, mangaId, namespace, name, type ->
                SearchTag(mangaId = mangaId, id = rowId, namespace = namespace, name = name, type = type.toInt())
            }
        }.map { tags -> tags.groupBy { it.mangaId } }
    }

    override fun subscribeFavoriteTitles(): Flow<Map<Long, List<SearchTitle>>> {
        return handler.subscribeToList {
            search_titlesQueries.selectAllFavorite { rowId, mangaId, title, type ->
                SearchTitle(mangaId = mangaId, id = rowId, title = title, type = type.toInt())
            }
        }.map { titles -> titles.groupBy { it.mangaId } }
    }

    override suspend fun insertFlatMetadata(flatMetadata: FlatMetadata) {
        require(flatMetadata.metadata.mangaId != -1L)

        handler.await(true) {
            flatMetadata.metadata.run {
                search_metadataQueries.upsert(mangaId, uploader, extra, indexedExtra, extraVersion.toLong())
            }
            search_tagsQueries.deleteByManga(flatMetadata.metadata.mangaId)
            flatMetadata.tags.forEach {
                search_tagsQueries.insert(it.mangaId, it.namespace, it.name, it.type.toLong())
            }
            search_titlesQueries.deleteByManga(flatMetadata.metadata.mangaId)
            flatMetadata.titles.forEach {
                search_titlesQueries.insert(it.mangaId, it.title, it.type.toLong())
            }
        }
    }

    override suspend fun getIdsOfFavoriteMangaWithMetadata(): List<Long> {
        return handler.awaitList { mangasQueries.getIdsOfFavoriteMangaWithMetadata() }
    }

    override suspend fun getSearchMetadata(): List<SearchMetadata> {
        return handler.awaitList { search_metadataQueries.selectAll(::searchMetadataMapper) }
    }

    override fun subscribeIdsOfFavoriteMangaWithMetadata(): Flow<List<Long>> {
        return handler.subscribeToList { mangasQueries.getIdsOfFavoriteMangaWithMetadata() }
    }

    private fun searchMetadataMapper(
        mangaId: Long,
        uploader: String?,
        extra: String,
        indexedExtra: String?,
        extraVersion: Long,
    ): SearchMetadata {
        return SearchMetadata(
            mangaId = mangaId,
            uploader = uploader,
            extra = extra,
            indexedExtra = indexedExtra,
            extraVersion = extraVersion.toInt(),
        )
    }

    private fun searchTitleMapper(
        mangaId: Long,
        id: Long?,
        title: String,
        type: Long,
    ): SearchTitle {
        return SearchTitle(
            mangaId = mangaId,
            id = id,
            title = title,
            type = type.toInt(),
        )
    }

    private fun searchTagMapper(
        mangaId: Long,
        id: Long?,
        namespace: String?,
        name: String,
        type: Long,
    ): SearchTag {
        return SearchTag(
            mangaId = mangaId,
            id = id,
            namespace = namespace,
            name = name,
            type = type.toInt(),
        )
    }
}
