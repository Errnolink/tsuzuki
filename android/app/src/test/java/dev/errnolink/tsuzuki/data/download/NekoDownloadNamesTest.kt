package dev.errnolink.tsuzuki.data.download

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NekoDownloadNamesTest {

    private val id = "3b0b1c3f-6f5e-4f2c-9a2e-1d2c3b4a5f60"

    @Test
    fun `a MangaDex chapter maps to the folder Neko downloaded it into`() {
        assertEquals("Vol.2 Ch.10 - Harvest - $id", NekoDownloadNames.chapterDirName("Vol.2 Ch.10 - Harvest", "/chapter/$id"))
    }

    @Test
    fun `characters Neko replaced in folder names are replaced the same way`() {
        assertEquals("Ch.5_ What_ - $id", NekoDownloadNames.chapterDirName("Ch.5: What?", "/chapter/$id"))
    }

    @Test
    fun `very long chapter names are cut where Neko cut them`() {
        val name = NekoDownloadNames.chapterDirName("x".repeat(400), "/chapter/$id")!!
        assertEquals(240, name.length)
        assertEquals(" - $id", name.takeLast(id.length + 3))
    }

    @Test
    fun `chapters from other sources have no Neko folder name`() {
        assertNull(NekoDownloadNames.chapterDirName("Chapter 1", "/series/abc/chapter-1"))
    }
}
