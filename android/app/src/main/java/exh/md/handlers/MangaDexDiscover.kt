// Adapted from Neko (https://github.com/nekomangaorg/Neko), Apache License 2.0. Modified for Tsuzuki.
package exh.md.handlers

import exh.md.dto.MangaDataDto
import exh.md.dto.MangaListDto
import exh.md.dto.SeasonalDto
import exh.md.service.MangaDexFeatureRequests
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class MangaDexDiscoverRow(
    val key: String,
    val title: String,
    val manga: List<MangaDataDto> = emptyList(),
    val error: String? = null,
)

class MangaDexDiscover(private val features: MangaDexFeatures) {
    fun rows(signedIn: Boolean): Flow<MangaDexDiscoverRow> {
        val loaders = listOf<Pair<String, suspend () -> MangaDexDiscoverRow>>(
            "Seasonal" to {
                val season = features.request<SeasonalDto>(MangaDexFeatureRequests.seasonal())
                MangaDexDiscoverRow("seasonal", season.name, list(season.id))
            },
            "Staff picks" to { MangaDexDiscoverRow("staff", "Staff picks", list(MangaDexFeatureRequests.STAFF_PICKS)) },
            "Neko picks" to { MangaDexDiscoverRow("neko", "Neko picks", list(MangaDexFeatureRequests.NEKO_PICKS)) },
            "Popular new titles" to {
                val since = LocalDateTime.now(ZoneOffset.UTC).minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
                MangaDexDiscoverRow("popular", "Popular new titles", features.request<MangaListDto>(MangaDexFeatureRequests.popularNew(features.ratings, since)).data)
            },
            "Recently added" to {
                MangaDexDiscoverRow("recent", "Recently added", features.request<MangaListDto>(MangaDexFeatureRequests.recentlyAdded(features.ratings)).data)
            },
            "Latest chapters" to {
                MangaDexDiscoverRow("latest", "Latest chapters", features.feed(0, followed = false).entries.distinctBy { it.manga.id }.take(20).map { it.manga })
            },
        ) + if (signedIn) listOf("Your follows" to suspend {
            MangaDexDiscoverRow("follows", "Your follows", features.feed(0).entries.distinctBy { it.manga.id }.take(20).map { it.manga })
        }) else emptyList()
        return rows(loaders)
    }

    internal fun rows(loaders: List<Pair<String, suspend () -> MangaDexDiscoverRow>>): Flow<MangaDexDiscoverRow> = channelFlow {
        if (features.ratings.isEmpty()) return@channelFlow
        emitConcurrently(loaders)
    }

    private suspend fun ProducerScope<MangaDexDiscoverRow>.emitConcurrently(
        loaders: List<Pair<String, suspend () -> MangaDexDiscoverRow>>,
    ) {
        for ((title, load) in loaders) {
            launch {
                val row = try {
                    load()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    MangaDexDiscoverRow(title, title, error = e.message ?: "MangaDex could not load this shelf")
                }
                send(row)
            }
        }
    }

    private suspend fun list(id: String): List<MangaDataDto> = features.manga(features.list(id).mangaIds).shuffled().take(20)
}
