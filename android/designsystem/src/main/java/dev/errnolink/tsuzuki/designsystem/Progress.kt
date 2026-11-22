package dev.errnolink.tsuzuki.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * M3 Expressive feedback shapes (Kasane `loader` and `progress`). The seven-shape morph and the wavy
 * tracks are material3's own implementations; Kasane draws the same spec, so we wrap them with Tsuzuki
 * colours and semantics instead of redrawing them.
 *
 * Loader: a new shape every 650 ms on a 200/0.6 spring plus a 4666 ms turn. Progress: amplitude
 * 3 / wavelength 40 (linear) and 1.6 / 15 (circular), flat at the ends and travelling while active —
 * all inside material3, so nothing here pins numbers.
 */

/** The indeterminate loading mark: one solid shape morphing through the seven M3E shapes. */
@Composable
fun LoadingMark(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    color: Color = TsuzukiTheme.colors.accent,
) {
    if (TsuzukiTheme.motion.reduced) {
        // Reduced motion: the morph freezes into one resting shape (Kasane: still, no travelling).
        Box(
            modifier
                .size(size)
                .clip(MaterialShapes.SoftBurst.toShape())
                .background(color),
        )
    } else {
        Box(modifier.size(size), contentAlignment = Alignment.Center) {
            LoadingIndicator(Modifier.fillMaxSize(), color = color)
        }
    }
}

/** The loading mark on a tinted disc (Kasane `.k-loader--contained`). */
@Composable
fun ContainedLoadingMark(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    color: Color = TsuzukiTheme.colors.accent,
) {
    val colors = TsuzukiTheme.colors
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(stateRowCapsuleFill(colors.accent, colors.isDark)),
        contentAlignment = Alignment.Center,
    ) {
        if (TsuzukiTheme.motion.reduced) {
            Box(Modifier.fillMaxSize(0.56f).clip(MaterialShapes.SoftBurst.toShape()).background(color))
        } else {
            ContainedLoadingIndicator(
                Modifier.fillMaxSize(),
                indicatorColor = color,
                containerColor = Color.Transparent,
            )
        }
    }
}

/** Wavy linear progress (Kasane `.k-wavy`): null sweeps a segment, otherwise the wave is the fill. */
@Composable
fun WavyProgress(
    progress: Float?,
    modifier: Modifier = Modifier,
    color: Color = TsuzukiTheme.colors.accent,
    trackColor: Color = TsuzukiTheme.colors.separator,
) {
    if (progress == null) {
        LinearWavyProgressIndicator(modifier = modifier, color = color, trackColor = trackColor)
    } else {
        LinearWavyProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = modifier,
            color = color,
            trackColor = trackColor,
        )
    }
}

/** Wavy circular progress (Kasane `.k-ring[data-wavy]`): null turns, otherwise the wave is the fill. */
@Composable
fun WavyRing(
    progress: Float?,
    modifier: Modifier = Modifier,
    color: Color = TsuzukiTheme.colors.accent,
    trackColor: Color = TsuzukiTheme.colors.separator,
) {
    if (progress == null) {
        CircularWavyProgressIndicator(modifier = modifier, color = color, trackColor = trackColor)
    } else {
        CircularWavyProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = modifier,
            color = color,
            trackColor = trackColor,
        )
    }
}

/**
 * The small activity ring used in rows and pills: a loading mark while idle or unknown, a wavy ring
 * carrying a fraction otherwise. [modifier] wins over the default [size].
 */
@Composable
fun ProgressRing(progress: Float, label: String, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    val colors = TsuzukiTheme.colors
    val fraction = if (progress.isFinite()) progress.coerceIn(0f, 1f) else 0f
    val indeterminate = fraction <= 0f
    Box(
        modifier
            .size(size)
            .then(modifier)
            .semantics {
                contentDescription = label
                progressBarRangeInfo =
                    if (indeterminate) ProgressBarRangeInfo.Indeterminate else ProgressBarRangeInfo(fraction, 0f..1f)
            },
        contentAlignment = Alignment.Center,
    ) {
        if (indeterminate) {
            LoadingMark(Modifier.fillMaxSize(), color = colors.accent)
        } else {
            WavyRing(fraction, Modifier.fillMaxSize(), color = colors.accent, trackColor = colors.separator)
        }
    }
}

/** Row-level alias kept for the app's call sites: same mark, same semantics. */
@Composable
fun ProgressMark(progress: Float, label: String, modifier: Modifier = Modifier) {
    ProgressRing(progress, label, modifier)
}
