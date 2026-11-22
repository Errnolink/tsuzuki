package dev.errnolink.tsuzuki.ui.reader

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.M3Motion
import dev.errnolink.tsuzuki.preference.TsuzukiPreferences
import dev.errnolink.tsuzuki.util.formatTime
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import kotlinx.coroutines.delay
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Kasane tokens: transform moves on the spatial curve, opacity on the effects curve. */
private val StatusSpatial = CubicBezierEasing(0.3f, 1.25f, 0.45f, 1f)
private val StatusEffects = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** System clock text that follows the user's format and updates every minute. */
@Composable
private fun rememberClockText(): String {
    val context = LocalContext.current
    val timeFormat by remember { Injekt.get<TsuzukiPreferences>().timeFormat() }.collectAsState()
    val is24Hour = remember(context) { android.text.format.DateFormat.is24HourFormat(context) }
    var text by remember { mutableStateOf("") }
    LaunchedEffect(context, timeFormat, is24Hour) {
        while (true) {
            val now = System.currentTimeMillis()
            text = formatTime(now, timeFormat, is24Hour)
            delay(60_000 - now % 60_000 + 250)
        }
    }
    return text
}

/**
 * The Kasane status line that takes the chrome's place while it is away: clock, chapter, page
 * n/total and a small battery-style progress pill on a soft scrim, readable over white pages.
 * It follows the system clock and updates on page change; replaces the old page caption.
 */
@Composable
fun ReaderStatusLine(
    visible: Boolean,
    chapterTitle: String?,
    currentPage: Int,
    totalPages: Int,
    modifier: Modifier = Modifier,
) {
    val prefs = remember { Injekt.get<ReaderPreferences>() }
    val showPageNumber by prefs.showPageNumber().collectAsState()
    val readerTheme by prefs.readerTheme().collectAsState()
    if (!showPageNumber) return
    val shown = !visible
    val reduced = TsuzukiTheme.motion.reduced
    val fade by animateFloatAsState(
        if (shown) 1f else 0f,
        if (reduced) dev.errnolink.tsuzuki.designsystem.M3Motion.reducedFade() else tween(M3Motion.Short4, easing = StatusEffects),
        label = "Status fade",
    )
    val rise by animateFloatAsState(
        if (shown || reduced) 0f else 6f,
        tween(M3Motion.Long2, easing = StatusSpatial),
        label = "Status rise",
    )
    if (fade <= 0.02f) return

    val dark = dev.errnolink.tsuzuki.reader.readerCanvasDark(readerTheme, isSystemInDarkTheme())
    val colors = TsuzukiTheme.colors
    val text = if (dark) Color.White.copy(alpha = 0.66f) else colors.secondary
    val scrimColor = if (dark) Color.Black.copy(alpha = 0.5f) else colors.background.copy(alpha = 0.85f)
    val clock = rememberClockText()
    val progress = readerProgressFraction(currentPage, totalPages)
    val pillFill by animateFloatAsState(
        progress,
        if (reduced) androidx.compose.animation.core.snap() else tween(M3Motion.Long2, easing = StatusSpatial),
        label = "Status pill",
    )

    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .alpha(fade)
            .graphicsLayer { translationY = rise.dp.toPx() },
    ) {
        // soft scrim so the line stays readable over white pages
        Box(
            Modifier
                .fillMaxWidth()
                .height(76.dp)
                .drawBehind {
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, scrimColor),
                            startY = 0f,
                            endY = size.height,
                        ),
                    )
                },
        )
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TsuzukiText(
                clock,
                Modifier,
                TsuzukiTheme.typography.caption1.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum",
                ),
                text,
            )
            TsuzukiText(
                chapterTitle.orEmpty(),
                Modifier.weight(1f),
                TsuzukiTheme.typography.caption1,
                text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TsuzukiText(
                readerPageCount(currentPage, totalPages)?.replace(" ", "").orEmpty(),
                Modifier,
                TsuzukiTheme.typography.caption1.copy(fontFeatureSettings = "tnum"),
                text,
            )
            Canvas(Modifier.size(width = 24.dp, height = 11.dp)) {
                val stroke = Stroke(1.dp.toPx())
                val inset = 1.5.dp.toPx()
                val nub = Size(2.dp.toPx(), 4.dp.toPx())
                // the battery nub sits outside the right edge
                drawRoundRect(
                    text,
                    topLeft = Offset(size.width - nub.width, size.height / 2f - nub.height / 2f),
                    size = nub,
                    cornerRadius = CornerRadius(1f, 1f),
                    alpha = 0.7f,
                )
                val body = Size(size.width - nub.width - 2.dp.toPx(), size.height)
                drawRoundRect(
                    text,
                    topLeft = Offset(0f, 0f),
                    size = body,
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    style = stroke,
                )
                val fillW = (body.width - 2 * inset) * pillFill
                drawRoundRect(
                    text,
                    topLeft = Offset(inset, inset),
                    size = Size(fillW.coerceAtLeast(0f), body.height - 2 * inset),
                    cornerRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx()),
                )
            }
        }
    }
}
