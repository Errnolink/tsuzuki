package dev.errnolink.tsuzuki.mangadex

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MissingChapterEstimateTest {
    @Test
    fun `ordinary gaps and fractional duplicates retain an estimate`() {
        assertEquals(2, missingChapterEstimate(sequenceOf("1", "1.5", "3", "5")))
        assertEquals(0, missingChapterEstimate(emptySequence()))
    }

    @Test
    fun `enormous specials make the estimate unavailable`() {
        assertNull(missingChapterEstimate((1..82).map(Int::toString).asSequence() + "99999999"))
        assertNull(missingChapterEstimate(sequenceOf("100000000")))
    }

    @Test
    fun `declared ending is bounded by observed chapter count`() {
        assertEquals(3, missingChapterEstimate(sequenceOf("1", "2"), 5))
        assertNull(missingChapterEstimate(sequenceOf("1", "2"), 100000000))
    }

    @Test
    fun `nonnumeric and nonfinite numbers do not enter the count`() {
        assertEquals(0, missingChapterEstimate(sequenceOf("1", "2", "none", "NaN", "Infinity", "-1")))
    }

    @Test
    fun `aggregate volume flattening keeps the bounded estimate`() {
        val aggregate = sequenceOf("1", "2", "1.5", "4", "none")
        assertEquals(1, missingChapterEstimate(aggregate))
        assertEquals(4, missingChapterEstimate(aggregate, 7))
    }

    @Test
    fun `aggregate specials past the plausibility bound return null`() {
        val specialHeavy = (1..30).map(Int::toString).asSequence() +
            (2000..2100).map(Int::toString).asSequence()
        assertNull(missingChapterEstimate(specialHeavy))
    }
}
