package tachiyomi.domain.manga.model

import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.ConcurrentHashMap

/**
 * Contains the required data for MangaCoverFetcher
 */
data class MangaCover(
    val mangaId: Long,
    val sourceId: Long,
    val isMangaFavorite: Boolean,
    // SY -->
    val ogUrl: String?,
    // SY <--
    val lastModified: Long,
) {
    // SY -->
    private val customThumbnailUrl = if (isMangaFavorite) {
        getCustomMangaInfo.get(mangaId)?.thumbnailUrl
    } else {
        null
    }
    val url: String? = customThumbnailUrl ?: ogUrl
    // SY <--

    // KMK -->

    /**
     * [dominantCoverColors] is used to set cover/text's color in Library (Favorite) grid view.
     * It contains only color for in-library (favorite) mangas.
     *
     * It reads/saves to a hashmap in [MangaCover.dominantCoverColorMap].
     *
     * Format: <first: cover color, second: text color>.
     *
     * Get in *[CommonMangaItem.MangaCompactGridItem]*, *[CommonMangaItem.MangaComfortableGridItem]* and
     *  *[CommonMangaItem.MangaListItem]*
     */
    @Suppress("KDocUnresolvedReference")
    var dominantCoverColors: Pair<Int, Int>?
        get() = dominantCoverColorMap[mangaId]
        set(value) {
            value ?: return
            dominantCoverColorMap[mangaId] = value.first to value.second
        }

    var ratio: Float?
        get() = coverRatioMap[mangaId]
        set(value) {
            value ?: return
            coverRatioMap[mangaId] = value
        }

    companion object {
        var dominantCoverColorMap = ConcurrentHashMap<Long, Pair<Int, Int>>()

        var coverRatioMap = ConcurrentHashMap<Long, Float>()
        // KMK <--

        // SY -->
        private val getCustomMangaInfo: GetCustomMangaInfo by injectLazy()
        // SY <--
    }
}

fun Manga.asMangaCover(): MangaCover {
    return MangaCover(
        mangaId = id,
        sourceId = source,
        isMangaFavorite = favorite,
        ogUrl = thumbnailUrl,
        lastModified = coverLastModified,
    )
}
