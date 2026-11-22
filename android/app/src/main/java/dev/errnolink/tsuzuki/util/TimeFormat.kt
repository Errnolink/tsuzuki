package dev.errnolink.tsuzuki.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

enum class TimeFormat {
    SYSTEM,
    H12,
    H24,
}

fun formatTime(
    epochMillis: Long,
    format: TimeFormat,
    is24HourSystem: Boolean,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    val use24Hour = when (format) {
        TimeFormat.SYSTEM -> is24HourSystem
        TimeFormat.H12 -> false
        TimeFormat.H24 -> true
    }
    val pattern = if (use24Hour) "HH:mm" else "h:mm a"
    val formatter = DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
    return ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zoneId).format(formatter)
}

fun formatTime(
    date: Date,
    format: TimeFormat,
    is24HourSystem: Boolean,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String = formatTime(date.time, format, is24HourSystem, zoneId)
