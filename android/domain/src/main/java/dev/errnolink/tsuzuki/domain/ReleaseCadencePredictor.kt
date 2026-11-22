package dev.errnolink.tsuzuki.domain

import tachiyomi.domain.chapter.model.Chapter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

/**
 * Predicts release intervals from clean upload dates while ignoring future-dated or out-of-order
 * backdated drops, then snaps close cadence medians to weekly/biweekly/monthly schedules.
 */
object ReleaseCadencePredictor {

    private const val ONE_DAY_MILLIS = 86_400_000L

    fun predictIntervalDays(
        chapters: List<Chapter>,
        zone: ZoneId,
        maxInterval: Int,
        nowMillis: Long? = null,
    ): Int {
        val chapterWindow = if (chapters.size <= 8) 3 else 10
        val uploadDates = cleanUploadDays(chapters, zone, nowMillis, chapterWindow)
        val fetchDates = distinctRecentDays(chapters.map { it.dateFetch }, zone, chapterWindow)

        val interval = when {
            uploadDates.size >= 3 -> snapCadence(medianGapDays(uploadDates), maxInterval)
            fetchDates.size >= 3 -> snapCadence(medianGapDays(fetchDates), maxInterval)
            else -> 7
        }
        return interval.coerceIn(1, maxInterval)
    }

    private fun cleanUploadDays(
        chapters: List<Chapter>,
        zone: ZoneId,
        nowMillis: Long?,
        limit: Int,
    ): List<LocalDateTime> {
        val validUploads = chapters.filter { ch ->
            ch.dateUpload > 0L && (nowMillis == null || ch.dateUpload <= nowMillis + ONE_DAY_MILLIS)
        }
        val numbered = validUploads.filter { it.chapterNumber > 0.0 }
        val filtered = if (numbered.map { it.chapterNumber }.distinct().size >= 3) {
            var oldestAcceptable = Long.MAX_VALUE
            val kept = ArrayList<Chapter>(validUploads.size)
            for (ch in numbered.sortedByDescending { it.chapterNumber }) {
                if (oldestAcceptable == Long.MAX_VALUE || ch.dateUpload <= oldestAcceptable + ONE_DAY_MILLIS) {
                    kept += ch
                    if (ch.dateUpload < oldestAcceptable) oldestAcceptable = ch.dateUpload
                }
            }
            kept
        } else {
            validUploads
        }
        return distinctRecentDays(filtered.map { it.dateUpload }, zone, limit)
    }

    private fun distinctRecentDays(timestamps: List<Long>, zone: ZoneId, limit: Int): List<LocalDateTime> =
        timestamps.asSequence()
            .filter { it > 0L }
            .sortedDescending()
            .map { ZonedDateTime.ofInstant(Instant.ofEpochMilli(it), zone).toLocalDate().atStartOfDay() }
            .distinct()
            .take(limit)
            .toList()

    private fun medianGapDays(days: List<LocalDateTime>): Int {
        val ranges = days.windowed(2) { (newer, older) ->
            java.time.temporal.ChronoUnit.DAYS.between(older, newer).toInt()
        }.sorted()
        return ranges[(ranges.size - 1) / 2]
    }

    private fun snapCadence(median: Int, maxInterval: Int): Int {
        val candidate = when {
            abs(median - 7) <= 1 -> 7
            abs(median - 14) <= 1 -> 14
            median in 27..32 -> 30
            else -> median
        }
        return candidate.coerceAtMost(maxInterval)
    }
}
