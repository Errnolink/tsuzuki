package dev.errnolink.tsuzuki.ui.details

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.ui.shell.MangaHero
import tachiyomi.domain.manga.model.MangaCover

internal const val DefaultCoverRatio = 2f / 3f
private const val CoverRatioMin = 0.45f
private const val CoverRatioMax = 1.8f
private const val CenteredMaxWidth = 0.78f
private const val CenteredMaxHeight = 0.93f
internal val ClassicCoverMaxWidth = 112.dp
private val ClassicCoverMaxHeight = 168.dp

internal fun detailsCoverRatio(known: Float?): Float =
    (known?.takeIf { it.isFinite() && it > 0f } ?: DefaultCoverRatio).coerceIn(CoverRatioMin, CoverRatioMax)

internal fun detailsCoverSize(ratio: Float, maxWidth: Dp, maxHeight: Dp): DpSize {
    val r = detailsCoverRatio(ratio)
    val width = minOf(maxWidth, maxHeight * r)
    return DpSize(width, width / r)
}

internal fun knownCoverRatio(mangaId: Long): Float? =
    MangaCover.coverRatioMap[mangaId]?.takeIf { it.isFinite() && it > 0f }
        ?: MangaHero.source(mangaId)?.image?.takeIf { it.height > 0 }?.let { it.width.toFloat() / it.height }

@Composable
internal fun DetailsCoverSlot(
    centered: Boolean,
    ratio: Float,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    if (centered) {
        BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            content(Modifier.size(detailsCoverSize(ratio, maxWidth * CenteredMaxWidth, maxWidth * CenteredMaxHeight)))
        }
    } else {
        Box(modifier) {
            content(Modifier.size(detailsCoverSize(ratio, ClassicCoverMaxWidth, ClassicCoverMaxHeight)))
        }
    }
}
