package dev.errnolink.tsuzuki.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import java.time.ZoneOffset
import java.time.ZonedDateTime

class ReleaseCadencePredictorTest {

    private val zone = ZoneOffset.UTC
    private val base = ZonedDateTime.parse("2026-06-01T00:00:00Z")
    private val nowMillis = base.plusDays(120).toInstant().toEpochMilli()

    @Test
    fun `recognizes near-weekly release drift and snaps to 7 days`() {
        val chapters = listOf(0L, 6L, 14L, 21L, 28L, 35L).mapIndexed { i, dayOffset ->
            Chapter.create().copy(
                chapterNumber = (i + 1).toDouble(),
                dateUpload = base.plusDays(dayOffset).toInstant().toEpochMilli(),
            )
        }
        assertEquals(7, ReleaseCadencePredictor.predictIntervalDays(chapters, zone, maxInterval = 28, nowMillis))
    }

    @Test
    fun `ignores backdated reupload noise and future upload dates`() {
        val chapters = listOf(
            Chapter.create().copy(chapterNumber = 1.0, dateUpload = base.plusDays(40).toInstant().toEpochMilli()),
            Chapter.create().copy(chapterNumber = 2.0, dateUpload = base.plusDays(2).toInstant().toEpochMilli()),
            Chapter.create().copy(chapterNumber = 3.0, dateUpload = base.plusDays(4).toInstant().toEpochMilli()),
            Chapter.create().copy(chapterNumber = 4.0, dateUpload = base.plusDays(6).toInstant().toEpochMilli()),
            Chapter.create().copy(chapterNumber = 5.0, dateUpload = base.plusDays(8).toInstant().toEpochMilli()),
            Chapter.create().copy(chapterNumber = 6.0, dateUpload = base.plusDays(200).toInstant().toEpochMilli()),
        )
        assertEquals(2, ReleaseCadencePredictor.predictIntervalDays(chapters, zone, maxInterval = 28, nowMillis))
    }
}
