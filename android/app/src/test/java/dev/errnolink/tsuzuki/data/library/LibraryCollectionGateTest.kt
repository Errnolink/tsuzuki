package dev.errnolink.tsuzuki.data.library

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LibraryCollectionGateTest {

    @Test
    fun `the first library load runs even while a sync or restore is running`() {
        assertTrue(libraryCollectionActive(visible = true, bulkWriteRunning = true, loaded = false))
    }

    @Test
    fun `a loaded library holds still during a sync and resumes when it ends`() {
        assertFalse(libraryCollectionActive(visible = true, bulkWriteRunning = true, loaded = true))
        assertTrue(libraryCollectionActive(visible = true, bulkWriteRunning = false, loaded = true))
    }

    @Test
    fun `a hidden library does not collect`() {
        assertFalse(libraryCollectionActive(visible = false, bulkWriteRunning = false, loaded = false))
    }
}
