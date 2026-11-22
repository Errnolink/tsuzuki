package dev.errnolink.tsuzuki.ui.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import dev.errnolink.tsuzuki.data.library.chapterPageProgress
import dev.errnolink.tsuzuki.designsystem.M3Motion
import dev.errnolink.tsuzuki.designsystem.Play
import dev.errnolink.tsuzuki.designsystem.SectionHeader
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.expressiveCoverColors
import dev.errnolink.tsuzuki.designsystem.pressScale
import dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior
import dev.errnolink.tsuzuki.reader.ReaderOrigins
import dev.errnolink.tsuzuki.reader.readerOrigin
import dev.errnolink.tsuzuki.ui.details.DetailsCardColor
import dev.errnolink.tsuzuki.ui.motion.MangaHero
import dev.errnolink.tsuzuki.ui.shell.CoverTile
import dev.errnolink.tsuzuki.ui.shell.rememberPageTotals
import eu.kanade.presentation.util.formatChapterNumber
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tachiyomi.core.common.i18n.stringResource as contextStringResource
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.roundToInt

internal fun continueCardWidthDp(screenWidthDp: Float, gutterDp: Float): Float =
    (screenWidthDp - gutterDp * 2f - 36f).coerceIn(240f, 340f)

internal data class ContinueRecencyLabels(
    val justNow: String = "Just now",
    val minutesAgo: (Long) -> String = { "${it}m ago" },
    val hoursAgo: (Long) -> String = { "${it}h ago" },
    val yesterday: String = "Yesterday",
    val daysAgo: (Long) -> String = { "${it}d ago" },
)

internal fun continueRelativeRecency(
    readAtMillis: Long?,
    nowMillis: Long,
    labels: ContinueRecencyLabels = ContinueRecencyLabels(),
): String? {
    if (readAtMillis == null || readAtMillis <= 0L) return null
    val diff = (nowMillis - readAtMillis).coerceAtLeast(0L)
    return when {
        diff < 60_000L -> labels.justNow
        diff < 3_600_000L -> labels.minutesAgo(diff / 60_000L)
        diff < 86_400_000L -> labels.hoursAgo(diff / 3_600_000L)
        diff < 172_800_000L -> labels.yesterday
        diff < 604_800_000L -> labels.daysAgo(diff / 86_400_000L)
        else -> null
    }
}

internal fun continueProgressPercent(progress: Float?): Int? {
    if (progress == null || !progress.isFinite()) return null
    return (progress.coerceIn(0f, 1f) * 100f).roundToInt().coerceIn(0, 100)
}

internal fun continueCardAccent(coverSeed: Color?, fallbackAccent: Color, isDark: Boolean): Color =
    if (coverSeed == null) fallbackAccent else expressiveCoverColors(coverSeed, isDark).accent

internal fun continuePillOnAccent(accent: Color): Color =
    if (accent.luminance() > 0.45f) Color(0xFF0B1220) else Color.White

@Composable
internal fun ContinueCardSurface(
    recent: List<HistoryWithRelations>,
    onSeeAllContinue: () -> Unit,
    onOpen: (Long) -> Unit,
    onResume: (Long, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TsuzukiTheme.colors
    val pageTotals = rememberPageTotals()
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val gutter = TsuzukiTheme.spacing.gutter
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionHeader(
            stringResource(MR.strings.tsuzuki_continue_reading),
            Modifier.padding(horizontal = gutter),
            titleStyle = TsuzukiTheme.typography.title2,
            titleColor = colors.text,
            trailing = {
                Box(
                    Modifier
                        .heightIn(min = 44.dp)
                        .wrapContentHeight(Alignment.CenterVertically)
                        .clickable(onClick = onSeeAllContinue)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    TsuzukiText(
                        stringResource(MR.strings.tsuzuki_see_all),
                        style = TsuzukiTheme.typography.subhead,
                        color = colors.accent,
                    )
                }
            },
        )
        if (recent.size == 1) {
            val first = recent.first()
            ContinueCard(
                first,
                pageTotals,
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .padding(horizontal = gutter),
                onClick = { onOpen(first.mangaId) },
                onResume = { onResume(first.mangaId, first.chapterId) },
            )
        } else {
            LazyRow(
                flingBehavior = tsuzukiFlingBehavior(),
                contentPadding = PaddingValues(horizontal = gutter),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(recent, key = { it.chapterId }, contentType = { "continue" }) { entry ->
                    ContinueCard(
                        entry,
                        pageTotals,
                        Modifier.width(continueCardWidthDp(screenWidth.value, gutter.value).dp),
                        onClick = { onOpen(entry.mangaId) },
                        onResume = { onResume(entry.mangaId, entry.chapterId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ContinueCard(
    entry: HistoryWithRelations,
    pageTotals: Map<Long, Int>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onResume: () -> Unit,
) {
    val colors = TsuzukiTheme.colors
    val motion = TsuzukiTheme.motion
    val context = LocalContext.current
    val totalPages = pageTotals[entry.chapterId]
    val progress = chapterPageProgress(entry.lastPageRead, totalPages, entry.read)
    val percent = continueProgressPercent(progress)
    val page = (entry.lastPageRead + 1).toInt()
    val pageLabel = totalPages?.let { stringResource(MR.strings.tsuzuki_page_n_of_total, page, it) }
        ?: stringResource(MR.strings.tsuzuki_page_n, page)
    val chapterLabel = stringResource(MR.strings.tsuzuki_ch_short, formatChapterNumber(entry.chapterNumber))
    val nowMillis = remember(entry.mangaId, entry.chapterId, entry.readAt) { System.currentTimeMillis() }
    val recencyLabels = ContinueRecencyLabels(
        justNow = stringResource(MR.strings.tsuzuki_just_now),
        minutesAgo = { mins -> context.contextStringResource(MR.strings.tsuzuki_minutes_ago, mins) },
        hoursAgo = { hrs -> context.contextStringResource(MR.strings.tsuzuki_hours_ago, hrs) },
        yesterday = stringResource(MR.strings.tsuzuki_yesterday),
        daysAgo = { days -> context.contextStringResource(MR.strings.tsuzuki_days_ago, days) },
    )
    val recency = continueRelativeRecency(entry.readAt?.time, nowMillis, recencyLabels)
    val resumeDesc = stringResource(MR.strings.tsuzuki_resume_title, entry.title)

    val coverSeed = MangaHero.peekCardColor(entry.mangaId)
    LaunchedEffect(entry.mangaId, entry.coverData.url, entry.coverData.lastModified) {
        if (MangaHero.peekCardColor(entry.mangaId) != null) return@LaunchedEffect
        try {
            val result = withContext(Dispatchers.IO) {
                context.imageLoader.execute(
                    ImageRequest.Builder(context)
                        .data(entry.coverData)
                        .size(64, 96)
                        .allowHardware(false)
                        .build(),
                )
            }
            val bitmap = (result as? SuccessResult)?.image?.toBitmap() ?: return@LaunchedEffect
            val extracted = withContext(Dispatchers.Default) {
                DetailsCardColor.fromBitmap(bitmap)
            }
            if (extracted != null) {
                MangaHero.reportCardColor(entry.mangaId, extracted)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
        }
    }

    val colorSpec = if (motion.reduced) snap<Color>() else tween<Color>(M3Motion.Short4)
    val cardAccent by animateColorAsState(
        targetValue = continueCardAccent(coverSeed, colors.accent, colors.isDark),
        animationSpec = colorSpec,
        label = "continue-card-accent",
    )
    val tintBase by animateColorAsState(
        targetValue = coverSeed ?: colors.accent,
        animationSpec = colorSpec,
        label = "continue-card-tint",
    )
    val onAccent = continuePillOnAccent(cardAccent)
    val cardShape = RoundedCornerShape(18.dp)
    val borderTint = if (coverSeed != null) {
        cardAccent.copy(alpha = if (colors.isDark) 0.30f else 0.24f)
    } else {
        colors.separator
    }
    val linearAlpha = if (colors.isDark) 0.24f else 0.14f
    val radialAlpha = if (colors.isDark) 0.26f else 0.14f

    var cardBounds by remember { mutableStateOf<Rect?>(null) }

    Column(
        modifier
            .onGloballyPositioned { cardBounds = it.boundsInWindow() }
            .clip(cardShape)
            .background(colors.grouped)
            .drawBehind {
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(tintBase.copy(alpha = linearAlpha), Color.Transparent),
                        start = Offset.Zero,
                        end = Offset(size.width * 0.72f, size.height),
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(tintBase.copy(alpha = radialAlpha), Color.Transparent),
                        center = Offset(36.dp.toPx(), 36.dp.toPx()),
                        radius = 108.dp.toPx(),
                    ),
                )
            }
            .border(1.dp, borderTint, cardShape)
            .clickable {
                ReaderOrigins.markActive(cardBounds)
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverTile(
                entry.coverData,
                entry.title,
                Modifier.width(52.dp),
                fixedRatio = 2f / 3f,
                hero = false,
                shape = RoundedCornerShape(10.dp),
                coverModifier = Modifier,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (recency != null) {
                    TsuzukiText(
                        recency.uppercase(),
                        style = TsuzukiTheme.typography.caption2.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.4.sp,
                        ),
                        color = cardAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TsuzukiText(
                    entry.title,
                    style = TsuzukiTheme.typography.subhead.copy(fontWeight = FontWeight.Bold),
                    color = colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                TsuzukiText(
                    "$chapterLabel · $pageLabel",
                    style = TsuzukiTheme.typography.caption1,
                    color = colors.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(colors.separator),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth((progress ?: 0f).coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(cardAccent),
                )
            }
            if (percent != null) {
                TsuzukiText(
                    "$percent%",
                    style = TsuzukiTheme.typography.caption2.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.secondary,
                    maxLines = 1,
                )
            }
            val resumeInteraction = remember { MutableInteractionSource() }
            Row(
                Modifier
                    .pressScale(resumeInteraction)
                    .heightIn(min = 32.dp)
                    .clip(CircleShape)
                    .background(cardAccent)
                    .clickable(
                        interactionSource = resumeInteraction,
                        indication = null,
                        role = Role.Button,
                    ) {
                        ReaderOrigins.markActive(cardBounds)
                        onResume()
                    }
                    .semantics { contentDescription = resumeDesc }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    TsuzukiIcons.Play,
                    contentDescription = null,
                    tint = onAccent,
                    modifier = Modifier.size(11.dp),
                )
                TsuzukiText(
                    stringResource(MR.strings.tsuzuki_resume),
                    style = TsuzukiTheme.typography.caption1.copy(fontWeight = FontWeight.Bold),
                    color = onAccent,
                    maxLines = 1,
                )
            }
        }
    }
}
