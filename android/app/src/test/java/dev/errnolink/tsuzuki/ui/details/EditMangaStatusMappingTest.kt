package dev.errnolink.tsuzuki.ui.details

import eu.kanade.tachiyomi.source.model.SManga
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EditMangaStatusMappingTest {
    @Test
    fun `spinner index maps back to the same status for every real status`() {
        for (status in listOf(SManga.ONGOING, SManga.COMPLETED, SManga.LICENSED, SManga.PUBLISHING_FINISHED, SManga.CANCELLED, SManga.ON_HIATUS)) {
            assertEquals(status.toLong(), editMangaStatusFromSpinnerIndex(editMangaStatusToSpinnerIndex(status)))
        }
    }

    @Test
    fun `extended statuses land on their spinner slot and save as the canonical constant`() {
        assertEquals(4, editMangaStatusToSpinnerIndex(61))
        assertEquals(5, editMangaStatusToSpinnerIndex(62))
        assertEquals(6, editMangaStatusToSpinnerIndex(63))
        assertEquals(SManga.PUBLISHING_FINISHED.toLong(), editMangaStatusFromSpinnerIndex(4))
    }

    @Test
    fun `unknown status and the default slot mean no override is saved`() {
        assertEquals(0, editMangaStatusToSpinnerIndex(SManga.UNKNOWN))
        assertEquals(0, editMangaStatusToSpinnerIndex(99))
        assertEquals(null, editMangaStatusFromSpinnerIndex(0))
        assertEquals(null, editMangaStatusFromSpinnerIndex(7))
    }
}
