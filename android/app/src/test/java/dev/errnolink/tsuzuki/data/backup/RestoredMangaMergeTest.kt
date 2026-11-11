package dev.errnolink.tsuzuki.data.backup

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton

class RestoredMangaMergeTest {

    companion object {
        private val injektRegistered = run {
            Injekt.importModule(object : InjektModule {
                override fun InjektRegistrar.registerInjectables() {
                    addSingleton(mockk<GetCustomMangaInfo>(relaxed = true))
                }
            })
            true
        }
    }

    private fun manga(version: Long, dateAdded: Long, favorite: Boolean = false, modifiedAt: Long = 0) =
        Manga.create().copy(
            id = 3,
            url = "/manga/3",
            source = 24,
            ogTitle = "T",
            version = version,
            dateAdded = dateAdded,
            favorite = favorite,
            lastModifiedAt = modifiedAt,
        )

    @Test
    fun `a losing restore entry keeps the library date added`() {
        val local = manga(version = 5, dateAdded = 1_700_000_000_000, favorite = true)
        val older = manga(version = 4, dateAdded = 1_600_000_000_000)

        val result = RestoredMangaMerge.merge(older, local, isSync = false)

        assertFalse(result.backupWins)
        assertEquals(1_700_000_000_000, result.manga.dateAdded)
        assertTrue(result.manga.favorite)
        assertEquals(5, result.manga.version)
    }

    @Test
    fun `a zero date added never erases the stored one`() {
        val local = manga(version = 5, dateAdded = 1_700_000_000_000)
        val older = manga(version = 4, dateAdded = 0)

        assertEquals(1_700_000_000_000, RestoredMangaMerge.merge(older, local, isSync = false).manga.dateAdded)
    }

    @Test
    fun `a winning backup entry keeps a stored date added over an older one`() {
        val local = manga(version = 4, dateAdded = 1_700_000_000_000)
        val newer = manga(version = 5, dateAdded = 1_600_000_000_000)

        val result = RestoredMangaMerge.merge(newer, local, isSync = false)

        assertTrue(result.backupWins)
        assertEquals(1_700_000_000_000, result.manga.dateAdded)
    }

    @Test
    fun `a winning entry fills in a zero stored date added from the backup`() {
        val local = manga(version = 4, dateAdded = 0)
        val newer = manga(version = 5, dateAdded = 1_600_000_000_000)

        val result = RestoredMangaMerge.merge(newer, local, isSync = false)

        assertTrue(result.backupWins)
        assertEquals(1_600_000_000_000, result.manga.dateAdded)
    }

    @Test
    fun `a file restore never unfavourites a library entry`() {
        val local = manga(version = 4, dateAdded = 1_700_000_000_000, favorite = true)
        val newerBackup = manga(version = 5, dateAdded = 1_600_000_000_000, favorite = false)

        val result = RestoredMangaMerge.merge(newerBackup, local, isSync = false)

        assertTrue(result.backupWins)
        assertTrue(result.manga.favorite)
    }

    @Test
    fun `a sync reply takes the payload favourite outright`() {
        val local = manga(version = 5, dateAdded = 1_700_000_000_000, favorite = true)
        val hubUnfavourited = manga(version = 5, dateAdded = 1_700_000_000_000, favorite = false, modifiedAt = 10)

        val result = RestoredMangaMerge.merge(hubUnfavourited, local, isSync = true)

        assertTrue(result.backupWins)
        assertFalse(result.manga.favorite)
        assertEquals(5, result.manga.version)
    }
}
