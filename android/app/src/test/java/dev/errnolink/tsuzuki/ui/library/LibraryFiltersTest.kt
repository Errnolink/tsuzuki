package dev.errnolink.tsuzuki.ui.library

import dev.errnolink.tsuzuki.mangadex.ScanlatorFilterOption
import exh.source.MERGED_SOURCE_ID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.TriState

class LibraryFiltersTest {
    @Test
    fun `tri-state merged filter keys on the merged source identity`() {
        assertTrue(matchesMergedFilter(TriState.DISABLED, MERGED_SOURCE_ID))
        assertTrue(matchesMergedFilter(TriState.DISABLED, 42L))
        assertTrue(matchesMergedFilter(TriState.ENABLED_IS, MERGED_SOURCE_ID))
        assertFalse(matchesMergedFilter(TriState.ENABLED_IS, 42L))
        assertFalse(matchesMergedFilter(TriState.ENABLED_NOT, MERGED_SOURCE_ID))
        assertTrue(matchesMergedFilter(TriState.ENABLED_NOT, 42L))
    }

    @Test
    fun `tri-state missing chapters filter keys on the bulk gap count`() {
        assertTrue(matchesMissingChaptersFilter(TriState.DISABLED, 0L))
        assertTrue(matchesMissingChaptersFilter(TriState.DISABLED, 3L))
        assertTrue(matchesMissingChaptersFilter(TriState.ENABLED_IS, 3L))
        assertFalse(matchesMissingChaptersFilter(TriState.ENABLED_IS, 0L))
        assertTrue(matchesMissingChaptersFilter(TriState.ENABLED_NOT, 0L))
        assertFalse(matchesMissingChaptersFilter(TriState.ENABLED_NOT, 3L))
    }

    @Test
    fun `tri-state unavailable chapters filter keys on the bulk unavailable count`() {
        assertTrue(matchesUnavailableChaptersFilter(TriState.DISABLED, 0L))
        assertTrue(matchesUnavailableChaptersFilter(TriState.DISABLED, 2L))
        assertTrue(matchesUnavailableChaptersFilter(TriState.ENABLED_IS, 2L))
        assertFalse(matchesUnavailableChaptersFilter(TriState.ENABLED_IS, 0L))
        assertTrue(matchesUnavailableChaptersFilter(TriState.ENABLED_NOT, 0L))
        assertFalse(matchesUnavailableChaptersFilter(TriState.ENABLED_NOT, 2L))
    }

    @Test
    fun `scanlator match rule keeps the SQL all versus any column contract`() {
        assertEquals(0L, ScanlatorFilterOption.ANY.matchAll)
        assertEquals(1L, ScanlatorFilterOption.ALL.matchAll)
        assertEquals(2, ScanlatorFilterOption.entries.size)
    }
}
