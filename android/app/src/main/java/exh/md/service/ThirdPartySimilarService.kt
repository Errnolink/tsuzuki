package exh.md.service

import android.content.Context
import android.util.LruCache
import dev.errnolink.tsuzuki.mangadex.MangaMappings
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import exh.md.dto.AnilistMangaRecommendationsDto
import exh.md.dto.MUMangaDto
import exh.md.dto.MalMangaRecommendationsDto
import exh.md.dto.ThirdPartySimilarRec
import exh.md.utils.MdUtil
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ThirdPartySimilarService(
    private val client: OkHttpClient,
    private val context: Context,
) {
    private val http = client.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val mappings by lazy { MangaMappings.get(context) }

    suspend fun fetchRecommendations(dexId: String, trackerLinks: Map<String, String>): List<ThirdPartySimilarRec> {
        val cached = memoryCache.get(dexId)
        if (cached != null) return cached

        return coroutineScope {
            val anilistDef = async { fetchAniList(dexId, trackerLinks["al"]) }
            val malDef = async { fetchMal(dexId, trackerLinks["mal"]) }
            val muDef = async { fetchMangaUpdates(dexId, trackerLinks["mu"]) }

            val merged = (anilistDef.await() + malDef.await() + muDef.await())
                .distinctBy { it.mangadexId ?: it.title.lowercase() }

            if (merged.isNotEmpty()) {
                memoryCache.put(dexId, merged)
            }
            merged
        }
    }

    private suspend fun fetchAniList(dexId: String, directId: String?): List<ThirdPartySimilarRec> {
        val alId = directId ?: mappings.getExternalID(dexId, "al") ?: return emptyList()
        return runCatching {
            val graphql = buildJsonObject {
                put("query", "{ Media(id: $alId, type: MANGA) { recommendations { edges { node { mediaRecommendation { id format title { english romaji } coverImage { medium large } } rating } } } } }")
            }.toString().toRequestBody("application/json".toMediaType())

            val dto: AnilistMangaRecommendationsDto = with(MdUtil.jsonParser) {
                http.newCall(POST("https://graphql.anilist.co/", body = graphql))
                    .awaitSuccess()
                    .parseAs()
            }

            dto.data.Media?.recommendations?.edges.orEmpty().mapNotNull { edge ->
                val rec = edge.node.mediaRecommendation
                if (rec.format.isNotBlank() && rec.format != "MANGA") return@mapNotNull null
                val targetDexId = mappings.getMangadexUUID(rec.id.toString(), "al")
                val title = rec.title?.english ?: rec.title?.romaji ?: "Title ${rec.id}"
                ThirdPartySimilarRec(
                    title = title,
                    source = "AniList",
                    scoreOrVotes = "${edge.node.rating} votes",
                    mangadexId = targetDexId,
                    originalId = rec.id.toString(),
                    originalUrl = "https://anilist.co/manga/${rec.id}",
                    coverUrl = rec.coverImage?.large ?: rec.coverImage?.medium,
                )
            }
        }.getOrDefault(emptyList())
    }

    private suspend fun fetchMal(dexId: String, directId: String?): List<ThirdPartySimilarRec> {
        val malId = directId ?: mappings.getExternalID(dexId, "mal") ?: return emptyList()
        return jikanRateLimiter.withLock {
            runCatching {
                val dto: MalMangaRecommendationsDto = with(MdUtil.jsonParser) {
                    http.newCall(GET("https://api.jikan.moe/v4/manga/$malId/recommendations"))
                        .awaitSuccess()
                        .parseAs()
                }

                dto.data.mapNotNull { item ->
                    val targetDexId = mappings.getMangadexUUID(item.entry.mal_id.toString(), "mal")
                    ThirdPartySimilarRec(
                        title = item.entry.title,
                        source = "MyAnimeList",
                        scoreOrVotes = "${item.votes} votes",
                        mangadexId = targetDexId,
                        originalId = item.entry.mal_id.toString(),
                        originalUrl = item.entry.url.ifBlank { "https://myanimelist.net/manga/${item.entry.mal_id}" },
                        coverUrl = item.entry.images?.jpg?.large_image_url ?: item.entry.images?.jpg?.image_url,
                    )
                }
            }.getOrDefault(emptyList())
        }
    }

    private suspend fun fetchMangaUpdates(dexId: String, directId: String?): List<ThirdPartySimilarRec> {
        val muId = directId ?: mappings.getExternalID(dexId, "mu_new") ?: return emptyList()
        return runCatching {
            val dto: MUMangaDto = with(MdUtil.jsonParser) {
                http.newCall(GET("https://api.mangaupdates.com/v1/series/$muId"))
                    .awaitSuccess()
                    .parseAs()
            }

            val list = mutableListOf<ThirdPartySimilarRec>()
            dto.recommendations.forEach { rec ->
                val targetDexId = mappings.getMangadexUUID(rec.series_id.toString(), "mu_new")
                list.add(
                    ThirdPartySimilarRec(
                        title = rec.series_name,
                        source = "MangaUpdates",
                        scoreOrVotes = "${rec.weight} votes",
                        mangadexId = targetDexId,
                        originalId = rec.series_id.toString(),
                        originalUrl = "https://www.mangaupdates.com/series.html?id=${rec.series_id}",
                    )
                )
            }
            dto.category_recommendations.forEach { rec ->
                val targetDexId = mappings.getMangadexUUID(rec.series_id.toString(), "mu_new")
                list.add(
                    ThirdPartySimilarRec(
                        title = rec.series_name,
                        source = "MangaUpdates",
                        scoreOrVotes = "Similar",
                        mangadexId = targetDexId,
                        originalId = rec.series_id.toString(),
                        originalUrl = "https://www.mangaupdates.com/series.html?id=${rec.series_id}",
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    companion object {
        private val memoryCache = LruCache<String, List<ThirdPartySimilarRec>>(128)
        private val jikanRateLimiter = Mutex()
    }
}
