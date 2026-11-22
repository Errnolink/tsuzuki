package dev.errnolink.tsuzuki.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.GlassContext
import dev.errnolink.tsuzuki.designsystem.GlassSurface
import dev.errnolink.tsuzuki.designsystem.LocalGlassFallbackAlpha
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import kotlinx.coroutines.delay
import dev.icerock.moko.resources.StringResource
import tachiyomi.presentation.core.i18n.stringResource

/** One transient reader notice: [textRes] is shown as a pill; [id] makes repeats re-show. */
@androidx.compose.runtime.Immutable
data class ReaderNotice(val textRes: StringResource, val id: Long)

/**
 * How long a notice waits before showing: the chrome's arrival animation (500 ms) must have
 * settled, so a notice never rides in on top of the controls. Null while the chrome is away —
 * the notice then waits for it. Pure; behaviour-tested.
 */
fun noticeDelayMillis(chromeVisibleForMillis: Long?): Long? = when {
    chromeVisibleForMillis == null -> null
    else -> (ChromeSettleMillis - chromeVisibleForMillis).coerceAtLeast(0L)
}

private const val ChromeSettleMillis = 520L
private const val NoticeHoldMillis = 1600L

/**
 * The reading-mode / orientation / quick-toggle notice, in-app: a small glass pill under the top
 * row, never over the page slider, never while the controls are animating in (T5). It replaces
 * the platform toast, which sat on the scrubber capsule and read as a blank dark pill at rest.
 */
@Composable
fun ReaderNotice(
    notice: ReaderNotice?,
    chromeVisibleSince: Long?,
    glass: GlassContext?,
    onConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var shown by remember { mutableStateOf<ReaderNotice?>(null) }
    val reveal = remember { Animatable(0f) }
    val reduced = dev.errnolink.tsuzuki.designsystem.TsuzukiTheme.motion.reduced
    LaunchedEffect(notice?.id, chromeVisibleSince) {
        val current = notice ?: return@LaunchedEffect
        val visibleFor = chromeVisibleSince?.let { System.currentTimeMillis() - it }
        val wait = noticeDelayMillis(visibleFor)
        if (wait == null) {
            shown = null
            reveal.snapTo(0f)
            return@LaunchedEffect
        }
        delay(wait)
        shown = current
        reveal.animateTo(1f, tween(dev.errnolink.tsuzuki.designsystem.M3Motion.Short4))
        delay(NoticeHoldMillis)
        reveal.animateTo(0f, tween(dev.errnolink.tsuzuki.designsystem.M3Motion.Medium1))
        shown = null
        onConsumed()
    }
    val current = shown ?: return
    CompositionLocalProvider(LocalGlassFallbackAlpha provides ReaderClearGlassAlpha) {
        Box(modifier.graphicsLayer {
            alpha = reveal.value
            translationY = if (reduced) 0f else (1f - reveal.value) * 6.dp.toPx()
        }) {
            GlassSurface(glass, shape = RoundedCornerShape(TsuzukiCorners.capsulePercent)) {
                TsuzukiText(
                    stringResource(current.textRes),
                    Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    TsuzukiTheme.typography.footnote,
                    color = TsuzukiTheme.colors.text,
                )
            }
        }
    }
}
