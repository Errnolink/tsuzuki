// Adapted from Neko (https://github.com/nekomangaorg/Neko), Apache License 2.0. Modified for Tsuzuki.
package dev.errnolink.tsuzuki.mangadex

import exh.md.utils.FollowStatus

enum class FollowsSyncDirection { PULL, PUSH, BOTH }

internal data class MangaDexFollow(val url: String, val title: String, val status: FollowStatus)

internal data class MangaDexLibraryTitle(val id: Long, val uuid: String, val title: String)

internal data class FollowsPullOutcome(
    val addedIds: List<Long>,
    val favoritedIds: List<Long>,
    val skippedStatuses: Int,
)

internal fun shouldRunFollowsSync(signedIn: Boolean, enabled: Boolean, manual: Boolean): Boolean =
    signedIn && (manual || enabled)

internal suspend fun pullFollowsToLibrary(
    follows: List<MangaDexFollow>,
    selectedStatuses: Set<FollowStatus>,
    defaultCategoryId: Long?,
    findManga: suspend (String) -> Pair<Long, Boolean>?,
    insertFavorite: suspend (String, String) -> Long,
    markFavorite: suspend (Long) -> Unit,
    assignCategory: suspend (Long, Long) -> Unit,
    onProgress: suspend (String, Int, Int) -> Unit = { _, _, _ -> },
): FollowsPullOutcome {
    val wanted = follows.filter { it.status in selectedStatuses }
    val addedIds = mutableListOf<Long>()
    val favoritedIds = mutableListOf<Long>()

    wanted.forEachIndexed { index, follow ->
        val existing = findManga(follow.url)
        when {
            existing == null -> {
                val id = insertFavorite(follow.url, follow.title)
                addedIds += id
                if (defaultCategoryId != null) assignCategory(id, defaultCategoryId)
            }
            !existing.second -> {
                markFavorite(existing.first)
                favoritedIds += existing.first
                if (defaultCategoryId != null) assignCategory(existing.first, defaultCategoryId)
            }
            else -> {}
        }
        onProgress(follow.title, index + 1, wanted.size)
    }

    return FollowsPullOutcome(addedIds, favoritedIds, follows.size - wanted.size)
}

internal suspend fun pushReadingStatuses(
    library: List<MangaDexLibraryTitle>,
    remoteStatuses: Map<String, String?>,
    status: FollowStatus = FollowStatus.READING,
    push: suspend (String, FollowStatus) -> Boolean,
    onProgress: suspend (String, Int, Int) -> Unit = { _, _, _ -> },
): Int {
    val changed = library.filter { remoteStatuses[it.uuid] == null }.distinctBy { it.uuid }
    var pushed = 0

    changed.forEachIndexed { index, title ->
        if (push(title.uuid, status)) pushed++
        onProgress(title.title, index + 1, changed.size)
    }

    return pushed
}
