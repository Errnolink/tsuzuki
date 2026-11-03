package eu.kanade.presentation.util

import android.content.Context
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import java.time.Instant
import kotlin.time.Duration

enum class RelativeTimeUnit { Never, JustNow, Minutes, Hours, Days, Older }

data class RelativeTimeValue(val unit: RelativeTimeUnit, val count: Int)

fun relativeTimeValue(epochMillis: Long, nowMillis: Long): RelativeTimeValue {
    if (epochMillis <= 0L) return RelativeTimeValue(RelativeTimeUnit.Never, 0)
    val diff = nowMillis - epochMillis
    val minutes = (diff / DateUtils.MINUTE_IN_MILLIS).toInt()
    return when {
        diff < DateUtils.MINUTE_IN_MILLIS -> RelativeTimeValue(RelativeTimeUnit.JustNow, 0)
        minutes < 60 -> RelativeTimeValue(RelativeTimeUnit.Minutes, minutes)
        minutes < 24 * 60 -> RelativeTimeValue(RelativeTimeUnit.Hours, minutes / 60)
        minutes < 7 * 24 * 60 -> RelativeTimeValue(RelativeTimeUnit.Days, minutes / (24 * 60))
        else -> RelativeTimeValue(RelativeTimeUnit.Older, 0)
    }
}

fun RelativeTimeValue.toRelativeTimeString(context: Context, epochMillis: Long): String = when (unit) {
    RelativeTimeUnit.Never -> context.stringResource(MR.strings.relative_time_span_never)
    RelativeTimeUnit.JustNow -> context.stringResource(MR.strings.updates_last_update_info_just_now)
    RelativeTimeUnit.Minutes -> context.pluralStringResource(SYMR.plurals.humanize_minute, count, count)
    RelativeTimeUnit.Hours -> context.pluralStringResource(SYMR.plurals.humanize_hour, count, count)
    RelativeTimeUnit.Days -> context.pluralStringResource(MR.plurals.relative_time, count, count)
    RelativeTimeUnit.Older ->
        DateUtils.formatDateTime(context, epochMillis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_YEAR)
}

fun Duration.toDurationString(context: Context, fallback: String): String {
    return toComponents { days, hours, minutes, seconds, _ ->
        buildList(4) {
            if (days != 0L) add(context.stringResource(MR.strings.day_short, days))
            if (hours != 0) add(context.stringResource(MR.strings.hour_short, hours))
            if (minutes != 0 && (days == 0L || hours == 0)) {
                add(
                    context.stringResource(MR.strings.minute_short, minutes),
                )
            }
            if (seconds != 0 && days == 0L && hours == 0) add(context.stringResource(MR.strings.seconds_short, seconds))
        }.joinToString(" ").ifBlank { fallback }
    }
}

@Composable
@ReadOnlyComposable
fun relativeTimeSpanString(epochMillis: Long): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    val now = Instant.now().toEpochMilli()
    return relativeTimeValue(epochMillis, now).toRelativeTimeString(context, epochMillis)
}
