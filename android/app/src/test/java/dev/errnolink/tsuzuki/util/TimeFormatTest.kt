package dev.errnolink.tsuzuki.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class TimeFormatTest {

    private val evening = ZonedDateTime.of(2026, 11, 21, 21, 5, 0, 0, ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()
    private val morning = ZonedDateTime.of(2026, 11, 21, 9, 5, 0, 0, ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()

    @Test
    fun `24-hour and 12-hour modes format independently of system clock setting`() {
        assertEquals("21:05", formatTime(evening, TimeFormat.H24, is24HourSystem = false, ZoneOffset.UTC))
        assertEquals("09:05", formatTime(morning, TimeFormat.H24, is24HourSystem = false, ZoneOffset.UTC))

        val formatted12 = formatTime(evening, TimeFormat.H12, is24HourSystem = true, ZoneOffset.UTC)
        assertEquals(true, formatted12.startsWith("9:05"))
    }

    @Test
    fun `system format follows device 24-hour preference`() {
        assertEquals("21:05", formatTime(evening, TimeFormat.SYSTEM, is24HourSystem = true, ZoneOffset.UTC))
        val sys12 = formatTime(evening, TimeFormat.SYSTEM, is24HourSystem = false, ZoneOffset.UTC)
        assertEquals(true, sys12.startsWith("9:05"))
    }
}
