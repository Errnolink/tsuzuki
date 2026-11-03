package eu.kanade.presentation.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class RelativeTimeValueTest {
    private val now = TimeUnit.DAYS.toMillis(1000)

    @Test
    fun `never for zero and negative timestamps`() {
        assertEquals(RelativeTimeUnit.Never, relativeTimeValue(0L, now).unit)
        assertEquals(RelativeTimeUnit.Never, relativeTimeValue(-5L, now).unit)
    }

    @Test
    fun `under a minute is just now`() {
        assertEquals(RelativeTimeUnit.JustNow, relativeTimeValue(now - 1, now).unit)
        assertEquals(RelativeTimeUnit.JustNow, relativeTimeValue(now - TimeUnit.SECONDS.toMillis(59), now).unit)
    }

    @Test
    fun `minute boundary and counts`() {
        val at = now - TimeUnit.MINUTES.toMillis(3)
        val value = relativeTimeValue(at, now)
        assertEquals(RelativeTimeUnit.Minutes, value.unit)
        assertEquals(3, value.count)
        assertEquals(59, relativeTimeValue(now - TimeUnit.MINUTES.toMillis(59), now).count)
    }

    @Test
    fun `hour boundary and counts`() {
        val value = relativeTimeValue(now - TimeUnit.HOURS.toMillis(5), now)
        assertEquals(RelativeTimeUnit.Hours, value.unit)
        assertEquals(5, value.count)
        assertEquals(RelativeTimeUnit.Hours, relativeTimeValue(now - TimeUnit.MINUTES.toMillis(60), now).unit)
    }

    @Test
    fun `day boundary and counts`() {
        val value = relativeTimeValue(now - TimeUnit.DAYS.toMillis(2), now)
        assertEquals(RelativeTimeUnit.Days, value.unit)
        assertEquals(2, value.count)
        assertEquals(1, relativeTimeValue(now - TimeUnit.HOURS.toMillis(24), now).count)
    }

    @Test
    fun `week and older falls to date formatting unit`() {
        assertEquals(RelativeTimeUnit.Older, relativeTimeValue(now - TimeUnit.DAYS.toMillis(7), now).unit)
        assertEquals(RelativeTimeUnit.Older, relativeTimeValue(now - TimeUnit.DAYS.toMillis(400), now).unit)
    }

    @Test
    fun `future timestamps inside window degrade to just now not minutes`() {
        assertEquals(RelativeTimeUnit.JustNow, relativeTimeValue(now + TimeUnit.MINUTES.toMillis(3), now).unit)
    }
}
