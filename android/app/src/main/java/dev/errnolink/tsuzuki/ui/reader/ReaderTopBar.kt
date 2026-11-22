package dev.errnolink.tsuzuki.ui.reader

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.errnolink.tsuzuki.designsystem.ChevronDown
import dev.errnolink.tsuzuki.designsystem.GlassBand
import dev.errnolink.tsuzuki.designsystem.GlassContext
import dev.errnolink.tsuzuki.designsystem.LocalGlassTintStrength
import dev.errnolink.tsuzuki.designsystem.LocalInsideGlass
import dev.errnolink.tsuzuki.designsystem.LocalOpaqueGlass
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.bandBlurEnabled
import dev.errnolink.tsuzuki.designsystem.menuMorphSource
import dev.errnolink.tsuzuki.designsystem.pressScale
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

private val ReaderBandFade = 44.dp
private val ReaderTopPad = 8.dp

/**
 * Reader UI v3 top bar: sleek `44.dp` glass `[Back]` circle beside clean floating typography for
 * the chapter title (+ trailing chevron) and quiet secondary page count over [ReaderTopBand].
 */
@Composable
fun ReaderTopBar(
    enabled: Boolean,
    chapterTitle: String?,
    pageCount: String?,
    navigateUp: () -> Unit,
    onClickChapterList: () -> Unit,
    modifier: Modifier = Modifier,
    glass: GlassContext? = null,
) {
    val colors = TsuzukiTheme.colors
    val morphSources = LocalReaderMorphSources.current
    val titleInteraction = remember { MutableInteractionSource() }
    val chaptersLabel = stringResource(MR.strings.chapters)
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GlassCircle(
            label = stringResource(MR.strings.tsuzuki_back),
            icon = TsuzukiIcons.Back,
            onClick = navigateUp,
            size = ReaderChromeHeight,
            enabled = enabled,
            glass = glass,
        )
        Column(
            Modifier
                .weight(1f)
                .then(if (morphSources != null) Modifier.menuMorphSource(morphSources.chaptersTop) else Modifier)
                .pressScale(titleInteraction)
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = titleInteraction,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClickLabel = chaptersLabel,
                ) {
                    morphSources?.let { it.activeChaptersSource = it.chaptersTop }
                    onClickChapterList()
                }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TsuzukiText(
                    chapterTitle.orEmpty(),
                    modifier = Modifier.weight(1f, fill = false),
                    style = TsuzukiTheme.typography.headline.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = TsuzukiIcons.ChevronDown,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = colors.secondary,
                )
            }
            if (pageCount != null) {
                TsuzukiText(
                    pageCount,
                    style = TsuzukiTheme.typography.caption1.copy(
                        fontSize = 12.sp,
                        fontFeatureSettings = "tnum",
                    ),
                    color = colors.secondary,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The progressive blur behind the reader's top row — matches the Library / shell [TopEdge] blur,
 * tapering from inside the row down [ReaderBandFade] below it with the matching dark dim.
 */
@Composable
fun ReaderTopBand(glass: GlassContext?, modifier: Modifier = Modifier) {
    val colors = TsuzukiTheme.colors
    val density = LocalDensity.current
    val tintStrength = LocalGlassTintStrength.current
    val statusTop = WindowInsets.statusBarsIgnoringVisibility.getTop(density)
    val rowBottomPx = statusTop + with(density) { (ReaderTopPad * 2 + ReaderChromeHeight).toPx() }
    val fadeFromPx = rowBottomPx * 0.72f
    val totalPx = rowBottomPx + with(density) { ReaderBandFade.toPx() }
    val height = with(density) { totalPx.toDp() }
    val bgTintTop = if (colors.isDark) 0.30f + 0.24f * tintStrength else 0.38f + 0.24f * tintStrength
    val dimAlpha = if (colors.isDark) 0.10f else 0f
    val liveGlassPossible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        glass != null && !LocalOpaqueGlass.current && !LocalInsideGlass.current
    if (liveGlassPossible && bandBlurEnabled(glass)) {
        Box(modifier.fillMaxWidth().height(height)) {
            GlassBand(
                context = glass,
                modifier = Modifier.fillMaxWidth().height(height),
                fadeFromPx = fadeFromPx,
                fadeToPx = totalPx,
                backgroundTintTop = bgTintTop,
            )
            if (dimAlpha > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(height)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = dimAlpha),
                                1f to Color.Transparent,
                            ),
                        ),
                )
            }
        }
    } else {
        val bg = colors.background
        val stop = (fadeFromPx / totalPx).coerceIn(0f, 1f)
        Box(
            modifier.fillMaxWidth().height(height).background(
                Brush.verticalGradient(
                    0f to bg.copy(alpha = 0.94f),
                    stop to bg.copy(alpha = 0.78f),
                    ((stop + 1f) / 2f) to bg.copy(alpha = 0.34f),
                    1f to Color.Transparent,
                ),
            ),
        )
    }
}
