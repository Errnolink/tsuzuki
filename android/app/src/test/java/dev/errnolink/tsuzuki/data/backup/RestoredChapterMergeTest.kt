package dev.errnolink.tsuzuki.data.backup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter

class RestoredChapterMergeTest {

    private fun chapter(read: Boolean, page: Long, version: Long = 1, bookmark: Boolean = false) =
        Chapter.create().copy(id = 7, url = "/c/7", read = read, lastPageRead = page, version = version, bookmark = bookmark)

    @Test
    fun `a sync reply never rolls back pages read while the sync was running`() {
        val local = chapter(read = false, page = 18, version = 5)
        val stale = chapter(read = false, page = 4, version = 4).copy(id = -1)

        val merged = RestoredChapterMerge.merge(stale, local, isSync = true)

        assertEquals(18, merged.lastPageRead)
        assertEquals(5, merged.version)
    }

    @Test
    fun `a sync reply never unreads a chapter finished during the sync`() {
        val local = chapter(read = true, page = 30, version = 6)
        val stale = chapter(read = false, page = 12, version = 5)

        assertTrue(RestoredChapterMerge.merge(stale, local, isSync = true).read)
    }

    @Test
    fun `newer progress from another device is applied`() {
        val local = chapter(read = false, page = 3, version = 2)
        val remote = chapter(read = true, page = 40, version = 3)

        val merged = RestoredChapterMerge.merge(remote, local, isSync = true)

        assertTrue(merged.read)
        assertEquals(40, merged.lastPageRead)
    }

    @Test
    fun `restoring an older backup keeps the further page of an unread chapter`() {
        val local = chapter(read = false, page = 30)
        val older = chapter(read = false, page = 5)

        assertEquals(30, RestoredChapterMerge.merge(older, local, isSync = false).lastPageRead)
    }

    @Test
    fun `restoring a backup never unreads a chapter and keeps bookmarks from either side`() {
        val local = chapter(read = true, page = 20, bookmark = true)
        val backup = chapter(read = false, page = 2)

        val merged = RestoredChapterMerge.merge(backup, local, isSync = false)

        assertTrue(merged.read)
        assertTrue(merged.bookmark)
        assertEquals(7, merged.id)
    }
}
