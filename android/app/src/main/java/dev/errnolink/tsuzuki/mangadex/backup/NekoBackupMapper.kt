// Adapted from Neko (https://github.com/nekomangaorg/Neko), Apache License 2.0. Modified for Tsuzuki.
package dev.errnolink.tsuzuki.mangadex.backup

import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupMergedMangaReference
import exh.source.MERGED_SOURCE_ID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import tachiyomi.data.MemoColumnAdapter
import java.net.URI

object NekoBackupMapper {
    const val HIDDEN_CHILD = "nekoMergedChild"
    const val UNAVAILABLE = "nekoUnavailable"
    const val UNRESOLVED_SOURCE = "nekoUnresolvedSource"

    data class SourceIdentity(val id: Long, val name: String, val language: String, val baseUrl: String? = null)
    data class Result(val manga: List<BackupManga>, val warnings: List<String>)

    private val sourceNames = mapOf(
        0 to "MangaLife", 1 to "Komga", 2 to "Toonily", 3 to "Weeb Central", 4 to "Comick",
        5 to "Suwayomi", 7 to "MangaBall", 9 to "Project Suki", 10 to "Comix", 11 to "Atsumaru", 12 to "Kagane",
    )

    fun chapterMemo(chapter: BackupChapter): JsonObject {
        val original = MemoColumnAdapter.decode(chapter.memo)
        if (chapter.uploader == null && !chapter.isUnavailable && chapter.smartOrder == 0) return original
        return JsonObject(original.toMutableMap().apply {
            chapter.uploader?.let { put("nekoUploader", JsonPrimitive(it)) }
            if (chapter.isUnavailable) put(UNAVAILABLE, JsonPrimitive(true))
            if (chapter.smartOrder != 0) put("nekoSmartOrder", JsonPrimitive(chapter.smartOrder))
        })
    }

    fun mangaMemo(manga: BackupManga): JsonObject {
        val original = MemoColumnAdapter.decode(manga.memo)
        if (manga.scanlatorFilter == null && manga.alternativeArtwork == null && manga.mergedMangaUrl == null && manga.mergeMangaList.isEmpty()) return original
        return JsonObject(original.toMutableMap().apply {
            manga.scanlatorFilter?.let { put("nekoScanlatorFilter", JsonPrimitive(it)) }
            manga.alternativeArtwork?.let { put("nekoAlternativeArtwork", JsonPrimitive(it)) }
            manga.mergedMangaUrl?.let { put("nekoMergedMangaUrl", JsonPrimitive(it)) }
            manga.mergedMangaImageUrl?.let { put("nekoMergedMangaImageUrl", JsonPrimitive(it)) }
            if (manga.mergeMangaList.isNotEmpty()) put("nekoMergeMangaList", Json.encodeToJsonElement(manga.mergeMangaList))
        })
    }

    fun excludedScanlators(manga: BackupManga): List<String> =
        (manga.excludedScanlators + manga.scanlatorFilter.orEmpty().split(" & ").filter(String::isNotBlank)).distinct()

    fun isUnavailable(memo: JsonObject): Boolean = memo[UNAVAILABLE]?.jsonPrimitive?.booleanOrNull == true

    fun accessMessage(memo: JsonObject): String? = memo[UNRESOLVED_SOURCE]?.jsonPrimitive?.contentOrNull?.let {
        "Install the $it extension and restore the Neko backup again to read this merged chapter. Its progress has been preserved."
    } ?: if (isUnavailable(memo)) "Chapter is not available on MangaDex" else null

    fun migrate(mangas: List<BackupManga>, sources: List<SourceIdentity>): Result {
        val warnings = mutableListOf<String>()
        val result = mangas.flatMap { manga ->
            val merges = manga.mergeMangaList.ifEmpty {
                manga.mergedMangaUrl?.takeIf(String::isNotBlank)?.let {
                    listOf(BackupMergeManga(it, manga.title, manga.mergedMangaImageUrl.orEmpty(), 0))
                }.orEmpty()
            }
            if (merges.isEmpty() || manga.source == MERGED_SOURCE_ID || manga.mergedMangaReferences.isNotEmpty()) {
                return@flatMap listOf(manga)
            }
            val remaining = manga.chapters.toMutableList()
            val children = mutableListOf<BackupManga>()
            val unresolved = mutableListOf<BackupChapter>()
            merges.forEach { merge ->
                val name = sourceNames[merge.mergeType] ?: "Neko merge type ${merge.mergeType}"
                val candidates = sources.filter { normalize(it.name) == normalize(name) }
                val mergeHost = host(merge.url)
                val matching = if (mergeHost != null) candidates.filter { it.baseUrl == null || host(it.baseUrl) == mergeHost } else candidates
                val source = matching.singleOrNull() ?: matching.filter { it.language == "en" }.singleOrNull()
                val owned = remaining.filter { chapter ->
                    normalize(chapter.scanlator.orEmpty().substringBefore(" & ")) == normalize(name) &&
                        (merges.count { it.mergeType == merge.mergeType } == 1 || host(chapter.url) == mergeHost && mergeHost != null)
                }
                if (source == null || merges.count { it.mergeType == merge.mergeType } > 1 && owned.isEmpty()) {
                    warnings += "${manga.title}: install or configure the $name extension and restore again (${merge.url}). Neko merge metadata and chapter progress were preserved."
                    val marked = owned.associateWith { chapter ->
                        chapter.copy(memo = MemoColumnAdapter.encode(JsonObject(chapterMemo(chapter) + (UNRESOLVED_SOURCE to JsonPrimitive(name)))))
                    }
                    remaining.replaceAll { marked[it] ?: it }
                    unresolved += marked.values
                } else {
                    remaining.removeAll(owned.toSet())
                    val urls = owned.mapTo(hashSetOf()) { it.url }
                    children += BackupManga(
                        source = source.id,
                        url = relativeUrl(merge.url, source.baseUrl),
                        title = merge.title.ifBlank { manga.title },
                        thumbnailUrl = merge.coverUrl.takeIf(String::isNotBlank),
                        favorite = false,
                        chapters = owned,
                        history = manga.history.filter { it.url in urls },
                        excludedScanlators = excludedScanlators(manga),
                        memo = hiddenChildMemo(),
                    )
                }
            }
            val memo = mangaMemo(manga).toMutableMap()
            if (unresolved.isNotEmpty()) memo["nekoUnresolvedChapters"] = Json.encodeToJsonElement(unresolved)
            val retained = manga.copy(
                chapters = remaining,
                excludedScanlators = excludedScanlators(manga),
                customThumbnailUrl = manga.customThumbnailUrl ?: manga.alternativeArtwork,
                memo = MemoColumnAdapter.encode(JsonObject(memo)),
            )
            if (children.isEmpty()) return@flatMap listOf(retained)
            val primary = retained.copy(
                favorite = false,
                categories = emptyList(),
                tracking = emptyList(),
                history = manga.history.filter { entry -> remaining.any { it.url == entry.url } },
                memo = MemoColumnAdapter.encode(JsonObject(memo + (HIDDEN_CHILD to JsonPrimitive(true)))),
            )
            val allChildren = listOf(primary) + children
            val references = allChildren.mapIndexed { index, child ->
                BackupMergedMangaReference(
                    isInfoManga = index == 0,
                    getChapterUpdates = true,
                    downloadChapters = true,
                    mergeUrl = manga.url,
                    mangaUrl = child.url,
                    mangaSourceId = child.source,
                )
            } + BackupMergedMangaReference(
                mergeUrl = manga.url, mangaUrl = manga.url, mangaSourceId = MERGED_SOURCE_ID, chapterPriority = -1,
            )
            allChildren + retained.copy(
                source = MERGED_SOURCE_ID,
                chapters = emptyList(),
                history = emptyList(),
                mergedMangaReferences = references,
            )
        }
        return Result(result, warnings)
    }

    private fun hiddenChildMemo() = MemoColumnAdapter.encode(JsonObject(mapOf(HIDDEN_CHILD to JsonPrimitive(true))))
    private fun normalize(value: String) = value.lowercase().filter(Char::isLetterOrDigit)
    private fun host(url: String): String? = runCatching { URI(url).host?.lowercase() }.getOrNull()
    private fun relativeUrl(url: String, baseUrl: String?): String =
        if (baseUrl != null && host(url) == host(baseUrl) && host(url) != null) URI(url).let { it.rawPath + (it.rawQuery?.let { query -> "?$query" } ?: "") } else url
}
