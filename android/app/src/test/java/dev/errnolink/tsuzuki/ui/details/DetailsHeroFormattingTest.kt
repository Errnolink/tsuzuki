package dev.errnolink.tsuzuki.ui.details

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import eu.kanade.presentation.manga.components.DisplayTag
import eu.kanade.presentation.manga.components.SearchMetadataChips

class DetailsHeroFormattingTest {
    @Test
    fun `all caps author words become title case`() {
        assertEquals("Tatsuki Fujimoto", titleCaseAllCapsWords("TATSUKI FUJIMOTO"))
        assertEquals("Ōima", titleCaseAllCapsWords("ŌIMA"))
    }

    @Test
    fun `mixed case names are untouched`() {
        assertEquals("Yoshitoki Ōima", titleCaseAllCapsWords("Yoshitoki Ōima"))
        assertEquals("one9", titleCaseAllCapsWords("one9"))
    }

    @Test
    fun `short and non letter led words are kept`() {
        assertEquals("A B C", titleCaseAllCapsWords("A B C"))
        assertEquals("3D Kana", titleCaseAllCapsWords("3D KANA"))
    }

    @Test
    fun `word order and separators are preserved`() {
        assertEquals("Fujimoto, Tatsuki", titleCaseAllCapsWords("FUJIMOTO, TATSUKI"))
        assertEquals("Natsume · Akatsuki", titleCaseAllCapsWords("NATSUME · AKATSUKI"))
        assertEquals("A  B", titleCaseAllCapsWords("A  B"))
    }

    @Test
    fun `grouped metadata replaces the flat tag list`() {
        val genre = listOf("Theme: Time Loop", "Theme: Time Loop", "Demographic: Seinen")
        val grouped = SearchMetadataChips(
            mapOf(
                "Theme" to listOf(DisplayTag("Theme", "Time Loop", "Theme: Time Loop", null)),
                "Demographic" to listOf(DisplayTag("Demographic", "Seinen", "Demographic: Seinen", null)),
            ),
        )
        val sections = aboutTagSections(genre, grouped)
        assertEquals(listOf("Theme", "Demographic"), sections.map { it.title })
        assertEquals(listOf("Time Loop", "Seinen"), sections.flatMap { it.chips }.map { it.label })
        assertEquals(listOf("Theme: Time Loop", "Demographic: Seinen"), sections.flatMap { it.chips }.map { it.query })
    }

    @Test
    fun `flat tags deduplicate when no grouping exists`() {
        val sections = aboutTagSections(listOf("Action", "Action", "Comedy"), null)
        assertEquals(1, sections.size)
        assertEquals("Tags", sections.single().title)
        assertEquals(listOf("Action", "Comedy"), sections.single().chips.map { it.label })
        assertEquals(listOf("Action", "Comedy"), sections.single().chips.map { it.query })
    }

    @Test
    fun `no tags yields no sections`() {
        assertEquals(0, aboutTagSections(emptyList(), null).size)
    }
}
