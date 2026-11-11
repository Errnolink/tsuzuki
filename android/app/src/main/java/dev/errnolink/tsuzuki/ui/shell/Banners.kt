package dev.errnolink.tsuzuki.ui.shell

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.errnolink.tsuzuki.designsystem.Banner
import dev.errnolink.tsuzuki.designsystem.NoticeTone
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.icerock.moko.resources.StringResource
import tachiyomi.presentation.core.i18n.stringResource
import java.math.RoundingMode
import java.text.NumberFormat

@Composable
fun WarningBanner(
    textRes: StringResource,
    modifier: Modifier = Modifier,
) {
    Banner(
        stringResource(textRes),
        modifier = modifier.padding(horizontal = TsuzukiTheme.spacing.gutter, vertical = TsuzukiTheme.spacing.small),
        tone = NoticeTone.Error,
    )
}

private val percentFormatter = NumberFormat.getPercentInstance().apply {
    roundingMode = RoundingMode.DOWN
    maximumFractionDigits = 0
}

fun statusLabel(activity: String, progress: Float?): String =
    if (progress == null || progress < 0.01f) {
        activity
    } else {
        "$activity ${percentFormatter.format(progress)}"
    }
