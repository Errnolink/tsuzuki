package dev.errnolink.tsuzuki.data.download

import eu.kanade.tachiyomi.util.storage.DiskUtil

object NekoDownloadNames {
    private val mangaDexChapterId = Regex("""/chapter/([0-9a-fA-F-]{36})""")
    private const val NEKO_MAX_CHARS = 240

    fun chapterDirName(chapterName: String, chapterUrl: String): String? {
        val id = mangaDexChapterId.find(chapterUrl)?.groupValues?.get(1) ?: return null
        val suffix = " - $id"
        val base = DiskUtil.buildValidFilename(chapterName, maxBytes = Int.MAX_VALUE)
        return base.take(NEKO_MAX_CHARS - suffix.length) + suffix
    }
}
