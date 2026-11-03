package exh.md.handlers

import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.kitsu.KitsuApi
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import exh.md.dto.AggregateDto
import exh.md.dto.RecommendationListDto
import exh.md.dto.StatisticsMangaDto
import kotlinx.serialization.Serializable
import exh.md.dto.ChapterDataDto
import exh.md.dto.CoverDto
import exh.md.dto.MangaDataDto
import exh.md.utils.MdConstants
import exh.md.utils.MdUtil
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

@Serializable
data class MangaDexArtwork(
    val id: String,
    val mangaId: String,
    val fileName: String,
    val volume: String?,
    val locale: String?,
    val description: String,
) {
    val label: String get() = volume?.takeIf { it.isNotBlank() }?.let { "Volume $it" } ?: "Extra artwork"
    val url: String get() = MdUtil.cdnCoverUrl(mangaId, fileName)
    val thumbnailUrl: String get() = exh.md.utils.MdCoverVariantUtil.getVariantUrl(url, exh.md.utils.MangaDexCoverVariant.MEDIUM)
}

data class MangaDexFeedEntry(
    val chapter: ChapterDataDto,
    val manga: MangaDataDto,
    val mangaTitle: String,
    val coverUrl: String?,
    val publishedAt: Instant,
    val chapterName: String,
    val groups: String,
) {
    fun day(zone: ZoneId = ZoneId.systemDefault()): LocalDate = publishedAt.atZone(zone).toLocalDate()
}

object MangaDexFeatureMapper {
    fun artwork(mangaId: String, covers: List<CoverDto>): List<MangaDexArtwork> = covers.map { cover ->
        MangaDexArtwork(
            cover.id, mangaId, cover.attributes.fileName, cover.attributes.volume,
            cover.attributes.locale, cover.attributes.description.orEmpty(),
        )
    }.sortedWith(compareBy<MangaDexArtwork> { it.volume?.toDoubleOrNull() ?: Double.MAX_VALUE }.thenBy { it.volume }.thenBy { it.locale })

    fun feed(
        chapters: List<ChapterDataDto>,
        manga: List<MangaDataDto>,
        lang: String,
        now: Instant = Instant.now(),
    ): List<MangaDexFeedEntry> {
        val byId = manga.associateBy { it.id }
        return chapters.mapNotNull { chapter ->
            val mangaId = chapter.relationships.firstOrNull { it.type == "manga" }?.id ?: return@mapNotNull null
            val title = byId[mangaId] ?: return@mapNotNull null
            val date = OffsetDateTime.parse(chapter.attributes.readableAt).toInstant()
            if (date > now) return@mapNotNull null
            MangaDexFeedEntry(
                chapter, title,
                MdUtil.getTitleFromManga(title.attributes, lang, true),
                title.relationships.firstOrNull { it.type == MdConstants.Types.coverArt }?.attributes?.fileName
                    ?.let { MdUtil.cdnCoverUrl(title.id, it) },
                date, chapterName(chapter),
                chapterCredit(chapter),
            )
        }.sortedByDescending { it.publishedAt }
    }

    fun chapterName(chapter: ChapterDataDto): String = with(chapter.attributes) {
        listOfNotNull(
            volume?.takeIf(String::isNotBlank)?.let { "Vol. $it" },
            this.chapter?.takeIf(String::isNotBlank)?.let { "Ch. $it" },
            title?.takeIf(String::isNotBlank),
        ).joinToString(" · ").ifBlank { "Oneshot" }
    }

    fun trackerLinks(links: JsonElement?): Map<String, String> {
        val values = links as? JsonObject ?: return emptyMap()
        return listOf("al", "mal", "kt", "mu").mapNotNull { key ->
            val value = (values[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
                ?: return@mapNotNull null
            val url = value.toHttpUrlOrNull()
            val segments = url?.pathSegments.orEmpty()
            val kindIndex = segments.indexOfFirst { it in setOf("manga", "series", "comics") }
            val id = url?.queryParameter("id")
                ?: if (kindIndex >= 0) segments.getOrNull(kindIndex + 1).orEmpty() else value.trimEnd('/').substringAfterLast('/').substringBefore('?')
            when {
                key == "mu" && id.matches(Regex("[A-Za-z0-9]+")) -> key to id
                key != "mu" && id.toLongOrNull()?.let { it > 0 } == true -> key to id
                else -> null
            }
        }.toMap()
    }

    fun kitsuTrack(payload: String): TrackSearch {
        val data = MdUtil.jsonParser.parseToJsonElement(payload).jsonObject.getValue("data").jsonObject
        val attributes = data.getValue("attributes").jsonObject
        return TrackSearch.create(TrackerManager.KITSU).apply {
            remote_id = data.getValue("id").jsonPrimitive.content.toLong()
            title = attributes.getValue("canonicalTitle").jsonPrimitive.content
            total_chapters = attributes["chapterCount"]?.jsonPrimitive?.longOrNull ?: 0
            tracking_url = KitsuApi.mangaUrl(remote_id)
            summary = attributes["synopsis"]?.jsonPrimitive?.contentOrNull.orEmpty()
        }
    }

    fun chapterCredit(chapter: ChapterDataDto): String {
        val groups = chapter.relationships.filter { it.type == MdConstants.Types.scanlator }
            .mapNotNull { it.attributes?.name }.distinct()
        if (groups.isNotEmpty()) return groups.joinToString(" & ")
        return chapter.relationships.firstOrNull { it.type == "user" }?.attributes?.username ?: "Deleted uploader"
    }

    fun recommendationIds(mangaId: String, page: RecommendationListDto): List<String> = page.data
        .sortedByDescending { it.attributes.score }
        .flatMap { it.relationships }
        .filter { it.type == "manga" && it.id != mangaId }
        .map { it.id }.distinct()

    fun estimatedMissing(aggregate: AggregateDto, lastChapter: Int? = null): Int? =
        dev.errnolink.tsuzuki.mangadex.missingChapterEstimate(
            aggregate.volumes.values.asSequence().flatMap { it.chapters.values.asSequence() }.map { it.chapter },
            lastChapter,
        )

    fun statistics(dto: StatisticsMangaDto, missing: Int?): MangaDexStatistics = MangaDexStatistics(
        dto.rating?.bayesian ?: dto.rating?.average,
        dto.follows,
        (1..10).associateWith { dto.rating?.distribution?.get(it.toString()) ?: 0 },
        dto.comments?.threadId,
        dto.comments?.repliesCount ?: 0,
        missing,
        dto.unavailableChaptersCount,
    )

    fun chapterAccess(chapter: ChapterDataDto, now: Instant = Instant.now()): String? = when {
        chapter.attributes.isUnavailable -> "Chapter is not available on MangaDex"
        OffsetDateTime.parse(chapter.attributes.readableAt).toInstant() > now -> "This chapter has not been released yet"
        chapter.relationships.any { it.attributes?.name in unsupportedOfficialGroups } ->
            "This official chapter must be read on the publisher website. Open WebView to continue."
        else -> null
    }

    val unsupportedOfficialGroups = setOf("Alpha Manga", "INKR Comics", "J-Novel Club", "Kodansha USA", "Tapas", "K MANGA")
}
