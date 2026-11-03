package dev.errnolink.tsuzuki.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.GlassSurface
import dev.errnolink.tsuzuki.designsystem.Banner
import dev.errnolink.tsuzuki.designsystem.M3Motion
import dev.errnolink.tsuzuki.designsystem.NoticeTone
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import java.math.RoundingMode
import java.text.NumberFormat

val LocalAppStateBottomPadding = androidx.compose.runtime.staticCompositionLocalOf { 0.dp }

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

private data class AppStateBannerData(
    val text: String,
    val activity: String?,
    val progress: Float,
)

@Composable
fun AppStateBanners(
    downloadedOnlyMode: Boolean,
    incognitoMode: Boolean,
    indexing: Boolean,
    restoring: Boolean,
    syncing: Boolean,
    updating: Boolean,
    modifier: Modifier = Modifier,
    progress: Float? = null,
) {
    val motion = TsuzukiTheme.motion
    val visible = downloadedOnlyMode || incognitoMode || indexing || restoring || syncing || updating
    val activity = when {
        updating -> stringResource(MR.strings.updating_library)
        syncing -> stringResource(MR.strings.syncing_library)
        restoring -> stringResource(MR.strings.restoring_backup)
        indexing -> stringResource(MR.strings.download_notifier_cache_renewal)
        else -> null
    }
    val labels = buildList {
        if (downloadedOnlyMode) add(stringResource(MR.strings.label_downloaded_only))
        if (incognitoMode) add(stringResource(MR.strings.pref_incognito_mode))
        if (activity != null) add(activity + (progress?.let { " ${percentFormatter.format(it)}" } ?: ""))
    }
    var held by remember { mutableStateOf(AppStateBannerData("", null, 0f)) }
    if (visible) {
        held = AppStateBannerData(
            text = labels.joinToString(" · "),
            activity = activity,
            progress = progress ?: 0f,
        )
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (motion.reduced) {
            fadeIn(M3Motion.reducedFade())
        } else {
            scaleIn(
                M3Motion.enter(M3Motion.Medium1),
                initialScale = 0.92f,
                transformOrigin = TransformOrigin(0.5f, 1f),
            ) + fadeIn(tween(M3Motion.Short3, easing = M3Motion.EmphasizedDecelerate))
        },
        exit = if (motion.reduced) {
            fadeOut(M3Motion.reducedFade())
        } else {
            fadeOut(M3Motion.exit(M3Motion.Short3))
        },
    ) {
        GlassSurface(context = null) {
            androidx.compose.foundation.layout.Row(
                Modifier.padding(
                    start = if (held.activity != null) 10.dp else 14.dp,
                    end = 14.dp,
                    top = 8.dp,
                    bottom = 8.dp,
                ),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                held.activity?.let { activityLabel ->
                    dev.errnolink.tsuzuki.designsystem.ProgressRing(
                        held.progress,
                        activityLabel,
                        Modifier.size(16.dp),
                    )
                }
                TsuzukiText(
                    held.text,
                    style = TsuzukiTheme.typography.footnote,
                    color = TsuzukiTheme.colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

