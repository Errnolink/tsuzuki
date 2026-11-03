package tachiyomi.domain.manga.repository

import exh.metadata.metadata.RaisedSearchMetadata
import exh.metadata.metadata.base.FlatMetadata
import exh.metadata.sql.models.SearchMetadata
import exh.metadata.sql.models.SearchTag
import exh.metadata.sql.models.SearchTitle
import kotlinx.coroutines.flow.Flow

interface MangaMetadataRepository {
    suspend fun getMetadataById(id: Long): SearchMetadata?

    fun subscribeMetadataById(id: Long): Flow<SearchMetadata?>

    suspend fun getTagsById(id: Long): List<SearchTag>

    fun subscribeTagsById(id: Long): Flow<List<SearchTag>>

    suspend fun getTitlesById(id: Long): List<SearchTitle>

    fun subscribeTitlesById(id: Long): Flow<List<SearchTitle>>

    suspend fun getFavoriteTags(): Map<Long, List<SearchTag>>

    suspend fun getFavoriteTitles(): Map<Long, List<SearchTitle>>

    fun subscribeFavoriteTags(): Flow<Map<Long, List<SearchTag>>>

    fun subscribeFavoriteTitles(): Flow<Map<Long, List<SearchTitle>>>

    suspend fun insertFlatMetadata(flatMetadata: FlatMetadata)

    suspend fun insertMetadata(metadata: RaisedSearchMetadata) = insertFlatMetadata(metadata.flatten())

    suspend fun getIdsOfFavoriteMangaWithMetadata(): List<Long>

    fun subscribeIdsOfFavoriteMangaWithMetadata(): Flow<List<Long>>

    suspend fun getSearchMetadata(): List<SearchMetadata>
}
