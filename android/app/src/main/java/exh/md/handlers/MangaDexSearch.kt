// Adapted from Neko (https://github.com/nekomangaorg/Neko), Apache License 2.0. Modified for Tsuzuki.
package exh.md.handlers

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.online.all.MangaDex
import exh.md.MangaDexPreferences
import exh.md.dto.LookupPageDto
import exh.md.dto.MangaListDto
import exh.md.service.MangaDexFeatureRequests
import exh.md.utils.MdUtil

class MangaDexSearch(private val source: MangaDex) {
    private val features = MangaDexFeatures(source)

    suspend fun search(page: Int, query: String, filters: FilterList): MangasPage {
        val preferences = MangaDexPreferences()
        if (features.ratings.isEmpty()) return MangasPage(emptyList(), false)
        val parameters = FilterHandler(preferences.visibleRatings().get(), preferences.showRatingFilter().get()).getQueryMap(filters).toMutableMap()
        if (!preferences.showRatingFilter().get()) {
            parameters.remove("contentRating[]")
        }
        val prefix = prefix(query)
        if (prefix?.first == "error") error(prefix.second)
        var listId = filters.filterIsInstance<FilterHandler.ListId>().firstOrNull()?.state.orEmpty()
        val author = if (prefix?.first == "author") prefix.second else filters.filterIsInstance<FilterHandler.Author>().firstOrNull()?.state.orEmpty()
        val group = if (prefix?.first == "group") prefix.second else filters.filterIsInstance<FilterHandler.Group>().firstOrNull()?.state.orEmpty()
        if (author.isNotBlank()) parameters["authorOrArtist"] = resolve("author", author)
        if (group.isNotBlank()) parameters["group"] = resolve("group", group)
        if (prefix?.first == "list") listId = prefix.second
        if (prefix?.first == "manga") {
            return MangasPage(features.manga(listOf(prefix.second)).map { MdUtil.createMangaEntry(it, features.language) }, false)
        }
        if (prefix == null && query.isNotBlank()) parameters["title"] = query
        val offset = (page - 1).coerceAtLeast(0) * 20
        if (listId.isNotBlank()) {
            val ids = features.list(listId).mangaIds
            return MangasPage(features.manga(ids.drop(offset).take(20)).map { MdUtil.createMangaEntry(it, features.language) }, offset + 20 < ids.size)
        }
        parameters["availableTranslatedLanguage[]"] = preferences.languages(features.language)
        val result = features.request<MangaListDto>(MangaDexFeatureRequests.browse(offset, parameters, features.ratings))
        return MangasPage(result.data.map { MdUtil.createMangaEntry(it, features.language) }, result.offset + result.data.size < result.total && offset + 20 < 10000)
    }

    private suspend fun resolve(type: String, value: String): String {
        if (uuid.matches(value)) return value
        val entries = mutableListOf<exh.md.dto.LookupEntryDto>()
        var offset = 0
        do {
            val page = features.request<LookupPageDto>(MangaDexFeatureRequests.lookup(type, value, offset))
            entries += page.data
            offset = page.offset + page.data.size
        } while (page.data.isNotEmpty() && offset < page.total)
        val exact = entries.filter { it.attributes.name.equals(value, ignoreCase = true) }
        return (exact.singleOrNull() ?: entries.singleOrNull())?.id
            ?: error(if (entries.isEmpty()) "No $type found for $value" else "Several ${type}s match. Choose one in MangaDex Home → Search creators and groups, or enter its UUID.")
    }

    companion object {
        private val uuid = Regex("[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}")

        fun prefix(query: String): Pair<String, String>? {
            val kind = query.substringBefore(':').lowercase()
            if (kind !in setOf("author", "group", "list", "manga", "error") || ':' !in query) return null
            val value = query.substringAfter(':').trim()
            require(value.isNotBlank()) { "Enter a name or MangaDex UUID after $kind:" }
            return kind to value
        }
    }
}
