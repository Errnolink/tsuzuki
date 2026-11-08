package dev.errnolink.tsuzuki.data.backup

import tachiyomi.domain.chapter.model.Chapter

object RestoredChapterMerge {

    fun merge(incoming: Chapter, local: Chapter, isSync: Boolean): Chapter {
        val base = (if (isSync) incoming else incoming.copyFrom(local)).copy(
            id = local.id,
            bookmark = incoming.bookmark || local.bookmark,
            sourceOrder = maxOf(incoming.sourceOrder, local.sourceOrder),
            dateUpload = minOf(incoming.dateUpload, local.dateUpload),
        )
        if (isSync) {
            val localNewer = local.version > incoming.version
            return base.copy(
                read = if (localNewer) local.read else incoming.read,
                lastPageRead = if (localNewer) local.lastPageRead else incoming.lastPageRead,
                version = maxOf(incoming.version, local.version),
            )
        }
        return when {
            local.read && !base.read -> base.copy(read = true, lastPageRead = local.lastPageRead)
            base.read -> base
            else -> base.copy(lastPageRead = maxOf(base.lastPageRead, local.lastPageRead))
        }
    }
}
