package dev.errnolink.tsuzuki.data.backup

import tachiyomi.domain.manga.model.Manga

object RestoredMangaMerge {

    data class Result(val manga: Manga, val backupWins: Boolean)

    fun merge(incoming: Manga, local: Manga, isSync: Boolean): Result {
        val backupWins = incoming.version > local.version ||
            (
                incoming.version == local.version &&
                    incoming.lastModifiedAt > local.lastModifiedAt
                ) ||
            (
                incoming.version == local.version &&
                    incoming.lastModifiedAt == local.lastModifiedAt &&
                    isSync
                )
        val dateAdded = local.dateAdded.takeIf { it != 0L } ?: incoming.dateAdded
        return if (backupWins) {
            Result(
                local.copyFrom(incoming).copy(
                    id = local.id,
                    favorite = if (isSync) incoming.favorite else local.favorite || incoming.favorite,
                    dateAdded = dateAdded,
                ),
                true,
            )
        } else {
            Result(
                incoming.copyFrom(local).copy(
                    id = local.id,
                    dateAdded = dateAdded,
                ),
                false,
            )
        }
    }

    private fun Manga.copyFrom(newer: Manga): Manga {
        return this.copy(
            favorite = this.favorite || newer.favorite,
            ogAuthor = newer.author,
            ogArtist = newer.artist,
            ogDescription = newer.description,
            ogGenre = newer.genre,
            ogThumbnailUrl = newer.thumbnailUrl,
            ogStatus = newer.status,
            initialized = this.initialized || newer.initialized,
            version = newer.version,
        )
    }
}
