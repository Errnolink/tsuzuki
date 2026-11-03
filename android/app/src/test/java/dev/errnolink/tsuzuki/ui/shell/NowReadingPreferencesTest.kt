package dev.errnolink.tsuzuki.ui.shell

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NowReadingPreferencesTest {

    @Test
    fun `encode page total produces expected chapter and total string`() {
        val encoded = NowReadingPreferences.encodePageTotal(chapterId = 12345L, totalPages = 29)
        assertEquals("12345:29", encoded)
    }

    @Test
    fun `decode page total returns page count when chapter id matches`() {
        val decoded = NowReadingPreferences.decodePageTotal("12345:29", targetChapterId = 12345L)
        assertEquals(29, decoded)
    }

    @Test
    fun `decode page total returns null on chapter id mismatch`() {
        val decoded = NowReadingPreferences.decodePageTotal("12345:29", targetChapterId = 99999L)
        assertNull(decoded)
    }

    @Test
    fun `decode page total returns null for malformed or non-numeric inputs`() {
        assertNull(NowReadingPreferences.decodePageTotal("", 12345L))
        assertNull(NowReadingPreferences.decodePageTotal("invalid", 12345L))
        assertNull(NowReadingPreferences.decodePageTotal("12345", 12345L))
        assertNull(NowReadingPreferences.decodePageTotal("12345:abc", 12345L))
        assertNull(NowReadingPreferences.decodePageTotal("12345:0", 12345L))
        assertNull(NowReadingPreferences.decodePageTotal("12345:-5", 12345L))
        assertNull(NowReadingPreferences.decodePageTotal("12345:29:extra", 12345L))
    }
}
