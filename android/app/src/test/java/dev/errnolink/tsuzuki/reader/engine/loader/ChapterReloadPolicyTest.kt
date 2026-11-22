// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): app/src/test/java/eu/kanade/tachiyomi/ui/reader/loader/ChapterLoaderTest.kt (Neko #3523)
package dev.errnolink.tsuzuki.reader.engine.loader

import dev.errnolink.tsuzuki.reader.engine.model.ReaderChapter
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter

class ChapterReloadPolicyTest {

    private var cacheDownloaded = false
    private var diskDownloaded = false
    private var diskChecked = false

    private val chapter =
        ReaderChapter(Chapter.create().copy(id = 1L, url = "/chapter/1", name = "Chapter 1"))

    private val policy =
        ChapterReloadPolicy(
            isDownloadedWithCache = { cacheDownloaded },
            isDownloadedOnDisk = {
                diskChecked = true
                diskDownloaded
            },
        )

    private fun stubDownloaded(cache: Boolean, disk: Boolean) {
        cacheDownloaded = cache
        diskDownloaded = disk
    }

    @Test
    fun `chapter loaded over http and now downloaded on disk is reported`() {
        stubDownloaded(cache = true, disk = true)

        assertTrue(policy.isDownloadedSinceHttpLoad(chapter, isHttpLoaded = true))
    }

    @Test
    fun `stale cache entry for a chapter missing on disk is not reported`() {
        // The cache still lists a download whose file is gone, while the loader checks the disk
        // and picks the HTTP loader again. Reporting it would reload the chapter on every preload.
        stubDownloaded(cache = true, disk = false)

        assertFalse(policy.isDownloadedSinceHttpLoad(chapter, isHttpLoaded = true))
    }

    @Test
    fun `chapter not in the cache is not reported and the disk is not checked`() {
        stubDownloaded(cache = false, disk = true)

        assertFalse(policy.isDownloadedSinceHttpLoad(chapter, isHttpLoaded = true))
        assertFalse(diskChecked)
    }

    @Test
    fun `chapter already loaded from disk is not reported`() {
        stubDownloaded(cache = true, disk = true)

        assertFalse(policy.isDownloadedSinceHttpLoad(chapter, isHttpLoaded = false))
    }
}
