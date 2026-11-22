package dev.errnolink.tsuzuki.ui.settings

import eu.kanade.tachiyomi.data.export.LibraryExporter.ExportOptions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CsvColumnSelectionTest {

    private val all = ExportOptions(includeTitle = true, includeAuthor = true, includeArtist = true)

    @Test
    fun `toggling a column updates state and never exports`() {
        val actions = CsvExportColumn.entries.map { csvColumnTap(all, it, checked = false) }
        actions.forEach { action ->
            assertTrue(action is CsvColumnAction.Update, "a column tap must not launch the export")
        }
        assertFalse((csvColumnTap(all, CsvExportColumn.Author, false) as CsvColumnAction.Update).options.includeAuthor)
    }

    @Test
    fun `unchecking title clears author and artist with it`() {
        val action = csvColumnTap(all, CsvExportColumn.Title, checked = false) as CsvColumnAction.Update
        assertEquals(ExportOptions(includeTitle = false, includeAuthor = false, includeArtist = false), action.options)
    }

    @Test
    fun `title can be re-checked after being cleared`() {
        val cleared = csvColumnTap(all, CsvExportColumn.Title, checked = false) as CsvColumnAction.Update
        val restored = csvColumnTap(cleared.options, CsvExportColumn.Title, checked = true) as CsvColumnAction.Update
        assertTrue(restored.options.includeTitle)
        assertNotEquals(cleared.options, restored.options)
    }

    @Test
    fun `author and artist stay locked while title is off`() {
        val cleared = csvColumnTap(all, CsvExportColumn.Title, checked = false) as CsvColumnAction.Update
        val author = csvColumnTap(cleared.options, CsvExportColumn.Author, checked = true) as CsvColumnAction.Update
        val artist = csvColumnTap(cleared.options, CsvExportColumn.Artist, checked = true) as CsvColumnAction.Update
        assertFalse(author.options.includeAuthor)
        assertFalse(artist.options.includeArtist)
    }

    @Test
    fun `only the save action exports the selected columns`() {
        val action = csvDone(all)
        assertTrue(action is CsvColumnAction.Export)
        assertEquals(all, (action as CsvColumnAction.Export).options)
    }
}
