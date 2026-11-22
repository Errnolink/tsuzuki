// Kotatsu backup JSON models as documented by the Kotatsu app's backup writer
// (https://github.com/KotatsuApp/Kotatsu, copyleft; read as format documentation
// only, no code taken) and by galpt/mk-bkconv (MIT). Field names follow the backup wire format.
package dev.errnolink.tsuzuki.backup.kotatsu

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KotatsuManga(
    @SerialName("id") val id: Long = 0,
    @SerialName("title") val title: String = "",
    @SerialName("alt_title") val altTitle: String? = null,
    @SerialName("url") val url: String = "",
    @SerialName("public_url") val publicUrl: String = "",
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("large_cover_url") val largeCoverUrl: String? = null,
    @SerialName("state") val state: String? = null,
    @SerialName("author") val author: String? = null,
    @SerialName("source") val source: String = "",
)

@Serializable
data class KotatsuFavourite(
    @SerialName("manga_id") val mangaId: Long = 0,
    @SerialName("category_id") val categoryId: Long = 0,
    @SerialName("sort_key") val sortKey: Int = 0,
    @SerialName("pinned") val pinned: Boolean = false,
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("manga") val manga: KotatsuManga = KotatsuManga(),
)

@Serializable
data class KotatsuHistoryEntry(
    @SerialName("manga_id") val mangaId: Long = 0,
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("updated_at") val updatedAt: Long = 0,
    @SerialName("chapter_id") val chapterId: Long = 0,
    @SerialName("page") val page: Int = 0,
    @SerialName("percent") val percent: Float = 0f,
    @SerialName("manga") val manga: KotatsuManga = KotatsuManga(),
)

@Serializable
data class KotatsuBookmarkEntry(
    @SerialName("manga_id") val mangaId: Long = 0,
    @SerialName("chapter_id") val chapterId: Long = 0,
    @SerialName("page") val page: Int = 0,
    @SerialName("created_at") val createdAt: Long = 0,
)

@Serializable
data class KotatsuBookmarkGroup(
    @SerialName("manga") val manga: KotatsuManga = KotatsuManga(),
    @SerialName("bookmarks") val bookmarks: List<KotatsuBookmarkEntry> = emptyList(),
)

@Serializable
data class KotatsuCategory(
    @SerialName("category_id") val categoryId: Long = 0,
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("sort_key") val sortKey: Int = 0,
    @SerialName("title") val title: String = "",
)

/** The sections of a Kotatsu backup zip that Tsuzuki restores. */
data class KotatsuBackup(
    val favourites: List<KotatsuFavourite> = emptyList(),
    val history: List<KotatsuHistoryEntry> = emptyList(),
    val categories: List<KotatsuCategory> = emptyList(),
    val bookmarks: List<KotatsuBookmarkGroup> = emptyList(),
)

/**
 * Kotatsu's manga/chapter id: a 31x rolling hash over `sourceName + url`
 * starting from 1125899906842597, wrapping i64 — kotatsu-parsers
 * `String.longHashCode()` / `MangaParser.generateUid(url)`
 * (https://github.com/KotatsuApp/kotatsu-parsers, Apache-2.0; see
 * docs/kotatsu-backup.md).
 */
object KotatsuIds {
    private const val SEED = 1125899906842597L

    fun longHashCode(value: String): Long {
        var h = SEED
        for (c in value) {
            h = 31 * h + c.code
        }
        return h
    }

    fun mangaId(sourceName: String, url: String): Long = longHashCode(sourceName + url)
}
