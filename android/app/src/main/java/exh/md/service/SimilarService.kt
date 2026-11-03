package exh.md.service

import android.util.LruCache
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import exh.md.dto.SimilarMangaDto
import exh.md.utils.MdUtil
import kotlinx.serialization.decodeFromString
import okhttp3.OkHttpClient
import tachiyomi.data.source.NoResultsException
import java.util.concurrent.TimeUnit

class SimilarService(
    private val client: OkHttpClient,
) {
    private val timeoutClient = client.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun getSimilarManga(mangaId: String): SimilarMangaDto {
        val cached = memoryCache.get(mangaId)
        if (cached != null) return cached

        if (mangaId.length < 3) throw NoResultsException()
        val prefix2 = mangaId.substring(0, 2)
        val prefix3 = mangaId.substring(0, 3)
        val url = "https://cdn.jsdelivr.net/gh/nekomangaorg/similar-data@main/similar/$prefix2/$prefix3.html"

        val responseText = timeoutClient.newCall(GET(url)).awaitSuccess().body.string()
        val matchedDto = parseSimilarDataLine(responseText, mangaId) ?: throw NoResultsException()
        memoryCache.put(mangaId, matchedDto)
        return matchedDto
    }

    companion object {
        private val memoryCache = LruCache<String, SimilarMangaDto>(256)

        fun parseSimilarDataLine(content: String, targetId: String): SimilarMangaDto? {
            return content.lineSequence().firstNotNullOfOrNull { line ->
                val split = line.split(":::||@!@||:::")
                if (split.size == 2 && split[0].trim() == targetId.trim()) {
                    runCatching {
                        MdUtil.jsonParser.decodeFromString<SimilarMangaDto>(split[1])
                    }.getOrNull()
                } else {
                    null
                }
            }
        }
    }
}

