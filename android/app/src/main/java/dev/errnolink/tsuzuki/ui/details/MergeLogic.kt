package dev.errnolink.tsuzuki.ui.details

import exh.source.MERGED_SOURCE_ID
import tachiyomi.domain.manga.model.Manga
import eu.kanade.tachiyomi.ui.manga.MergedMangaData
import tachiyomi.domain.manga.model.MergedMangaReference

enum class MergePriorityMove {
    Up,
    Down,
}

fun orderMergeCandidates(
    candidates: List<Manga>,
    query: String,
): List<Manga> {
    val cleanQuery = query.trim().lowercase()
    if (cleanQuery.isEmpty()) return candidates

    val queryWords = cleanQuery.split(Regex("\\s+")).filter { it.isNotEmpty() }

    return candidates.sortedWith(
        compareBy<Manga> { manga ->
            val title = manga.title.trim().lowercase()
            when {
                title == cleanQuery -> 0
                title.startsWith(cleanQuery) -> 1
                title.contains(cleanQuery) -> 2
                queryWords.isNotEmpty() && queryWords.all { title.contains(it) } -> 3
                else -> 4
            }
        }.thenBy { manga ->
            manga.title.length
        }.thenBy { manga ->
            manga.title.lowercase()
        }.thenBy { manga ->
            manga.id
        },
    )
}

fun reorderMergePriorities(
    references: List<MergedMangaReference>,
    targetReferenceId: Long,
    move: MergePriorityMove,
): List<MergedMangaReference> {
    val container = references.filter { it.mangaSourceId == MERGED_SOURCE_ID }
    val children = references.filter { it.mangaSourceId != MERGED_SOURCE_ID }
        .sortedBy { it.chapterPriority }
        .toMutableList()

    val currentIndex = children.indexOfFirst { it.id == targetReferenceId }
    if (currentIndex == -1) return references

    val newIndex = when (move) {
        MergePriorityMove.Up -> currentIndex - 1
        MergePriorityMove.Down -> currentIndex + 1
    }

    if (newIndex !in children.indices) return references

    val item = children.removeAt(currentIndex)
    children.add(newIndex, item)

    val updatedChildren = children.mapIndexed { index, ref ->
        ref.copy(chapterPriority = index)
    }

    return container.map { it.copy(chapterPriority = -1) } + updatedChildren
}

fun reorderMergePrioritiesToIndex(
    references: List<MergedMangaReference>,
    fromIndex: Int,
    toIndex: Int,
): List<MergedMangaReference> {
    val container = references.filter { it.mangaSourceId == MERGED_SOURCE_ID }
    val children = references.filter { it.mangaSourceId != MERGED_SOURCE_ID }
        .sortedBy { it.chapterPriority }
        .toMutableList()

    if (fromIndex !in children.indices || toIndex !in children.indices || fromIndex == toIndex) {
        return references
    }

    val item = children.removeAt(fromIndex)
    children.add(toIndex, item)

    val updatedChildren = children.mapIndexed { index, ref ->
        ref.copy(chapterPriority = index)
    }

    return container.map { it.copy(chapterPriority = -1) } + updatedChildren
}

fun resolveMergePrimarySourceId(
    manga: Manga,
    mergedData: MergedMangaData?,
    installedSourceIds: List<Long>,
): Long? {
    val infoSourceId = mergedData?.references
        ?.firstOrNull { it.isInfoManga }?.mangaSourceId
    infoSourceId?.takeIf { it in installedSourceIds }?.let { return it }
    manga.source.takeIf { it != MERGED_SOURCE_ID && it in installedSourceIds }?.let { return it }
    return installedSourceIds.firstOrNull()
}
