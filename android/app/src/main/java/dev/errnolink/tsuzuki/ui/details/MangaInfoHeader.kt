package dev.errnolink.tsuzuki.ui.details

import androidx.compose.animation.Crossfade

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import dev.errnolink.tsuzuki.designsystem.M3Motion
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.compose.rememberConstraintsSizeResolver
import coil3.decode.DataSource
import dev.icerock.moko.resources.StringResource
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import eu.kanade.tachiyomi.data.cache.CoverCache
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import dev.errnolink.tsuzuki.designsystem.Copy
import dev.errnolink.tsuzuki.designsystem.CoverImage
import dev.errnolink.tsuzuki.designsystem.coverShape
import dev.errnolink.tsuzuki.designsystem.GlassMenu
import dev.errnolink.tsuzuki.designsystem.RefreshCw
import dev.errnolink.tsuzuki.designsystem.Search
import dev.errnolink.tsuzuki.designsystem.StarFilled
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.ChevronRight
import dev.errnolink.tsuzuki.designsystem.rememberCoverArrivalState
import dev.errnolink.tsuzuki.mangadex.MangaDexHeroStats
import dev.errnolink.tsuzuki.mangadex.rememberMangaDexHeroStats
import dev.errnolink.tsuzuki.ui.shell.LocalShellArrivals
import dev.errnolink.tsuzuki.reader.readerFallbackOrigin
import dev.errnolink.tsuzuki.ui.motion.mangaHeroTarget
import tachiyomi.domain.manga.model.asMangaCover
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.ContextMenu
import dev.errnolink.tsuzuki.designsystem.ContextMenuAction
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.presentation.util.relativeTimeSpanString
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.i18n.pluralStringResource
import java.util.Locale

private val RatingStarDark = Color(0xFFFFB84D)
private val RatingStarLight = Color(0xFFC97A10)

@Composable
fun MangaInfoBox(
    isTabletUi: Boolean,
    appBarPadding: Dp,
    manga: Manga,
    sourceName: String,
    sourceLanguage: String,
    isStubSource: Boolean,
    isSourceIncognito: Boolean,
    onCoverClick: () -> Unit,
    doSearch: (query: String, global: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    librarySearch: (query: String) -> Unit,
    onSourceClick: () -> Unit,
    coverRatio: MutableFloatState,
    showMangaDexRating: Boolean = false,
    mergedSourceCount: Int = 0,
    chapterCount: Int? = null,
    lastChapterUpdate: Long = 0L,
    onRatingClick: () -> Unit = {},
    onCoverLoaded: () -> Unit = {},
) {
    val context = LocalContext.current
    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(start = 20.dp, end = 20.dp, top = appBarPadding + 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val knownRatio = remember(manga.id) { knownCoverRatio(manga.id) }
        var ratio by remember(manga.id) { mutableFloatStateOf(detailsCoverRatio(knownRatio)) }
        DetailsCoverSlot(centered = false, ratio = ratio) { slot ->
            DetailsCover(
                cover = manga.asMangaCover(),
                title = manga.title,
                ratio = ratio,
                onClick = onCoverClick,
                modifier = slot,
                onCoverDecoded = { height, width ->
                    coverRatio.floatValue = height.toFloat() / width
                    if (knownRatio == null) ratio = detailsCoverRatio(width.toFloat() / height)
                },
                onLoaded = onCoverLoaded,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TsuzukiTheme.spacing.small)) {
            var titleExpanded by rememberSaveable(manga.id) { mutableStateOf(false) }
            ContextMenu(
                manga.title,
                listOf(
                    ContextMenuAction(stringResource(MR.strings.tsuzuki_search_library), icon = { Icon(TsuzukiIcons.Library, null, tint = TsuzukiTheme.colors.text) }) { librarySearch(manga.title) },
                    ContextMenuAction(stringResource(MR.strings.tsuzuki_search_all_sources), icon = { Icon(TsuzukiIcons.Globe, null, tint = TsuzukiTheme.colors.text) }) { doSearch(manga.title, true) },
                ),
                onClick = { titleExpanded = !titleExpanded },
            ) {
                TsuzukiText(
                    manga.title,
                    style = TsuzukiTheme.typography.title3,
                    maxLines = if (titleExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = LocalHeroTitleAnchor.current?.title() ?: Modifier,
                )
            }
            val author = manga.author?.takeIf { it.isNotBlank() }
            val artist = manga.artist?.takeIf { it.isNotBlank() && it != manga.author }
            if (author != null || artist != null) {
                Byline(
                    author = author?.let(::titleCaseAllCapsWords),
                    artist = artist?.let(::titleCaseAllCapsWords),
                    onBrowse = { doSearch(it, true) },
                    onCopy = { context.copyToClipboard(it, it) },
                )
            }
            if (showMangaDexRating) {
                RatingLine(mangaId = manga.id, onClick = onRatingClick)
            }
            val statusRes = mangaStatusRes(manga)
            StatusChaptersRow(
                statusText = statusRes?.let { stringResource(it) },
                statusColor = mangaStatusColor(statusRes),
                chapterCount = chapterCount,
            )
            TsuzukiText(
                if (lastChapterUpdate > 0L) stringResource(MR.strings.tsuzuki_updated_relative, relativeTimeSpanString(lastChapterUpdate)) else " ",
                style = TsuzukiTheme.typography.subhead,
                color = TsuzukiTheme.colors.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isSourceIncognito) TsuzukiText(stringResource(MR.strings.pref_incognito_mode), style = TsuzukiTheme.typography.caption1, color = TsuzukiTheme.colors.secondary)
            SourceLine(
                label = sourceLineLabel(sourceName, sourceLanguage, mergedSourceCount, isStubSource),
                onClick = onSourceClick,
            )
        }
    }
}

private fun mangaStatusRes(manga: Manga) = when (manga.status.toInt()) {
    SManga.ONGOING -> MR.strings.ongoing
    SManga.COMPLETED -> MR.strings.completed
    SManga.LICENSED -> MR.strings.licensed
    SManga.PUBLISHING_FINISHED -> MR.strings.publishing_finished
    SManga.CANCELLED -> MR.strings.cancelled
    SManga.ON_HIATUS -> MR.strings.on_hiatus
    else -> null
}

@Composable
private fun mangaStatusColor(status: StringResource?): Color {
    val colors = TsuzukiTheme.colors
    return when (status) {
        MR.strings.completed, MR.strings.licensed, MR.strings.publishing_finished -> colors.success
        MR.strings.ongoing -> colors.accent
        else -> colors.secondary
    }
}

@Composable
private fun sourceLineLabel(
    sourceName: String,
    sourceLanguage: String,
    mergedSourceCount: Int,
    isStubSource: Boolean,
): String = when {
    mergedSourceCount > 1 -> pluralStringResource(MR.plurals.tsuzuki_merged_from_sources, mergedSourceCount, mergedSourceCount)
    else -> listOfNotNull(sourceName.takeIf { it.isNotBlank() }, sourceLanguage.takeIf { it.isNotBlank() }).joinToString(" · ")
} + if (isStubSource) " · " + stringResource(MR.strings.tsuzuki_unavailable) else ""

@Composable
private fun StatusChaptersRow(statusText: String?, statusColor: Color, chapterCount: Int?) {
    val colors = TsuzukiTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TsuzukiTheme.spacing.tiny)) {
        statusText?.let {
            TsuzukiText(
                it,
                Modifier.weight(1f, fill = false),
                style = TsuzukiTheme.typography.subhead,
                color = statusColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (chapterCount != null || statusText != null) {
            if (statusText != null) TsuzukiText("·", style = TsuzukiTheme.typography.subhead, color = colors.secondary)
            ChapterCountText(chapterCount)
        }
    }
}

/**
 * N12: the chapter-count segment of a metadata line. Known → the count; unknown → its slot is
 * reserved with a quiet skeleton so nothing reflows and no placeholder label ever swaps.
 */
@Composable
private fun ChapterCountText(chapterCount: Int?) {
    ChapterCountSlot(known = chapterCount != null, skeletonWidth = 72.dp, skeletonHeight = 12.dp) {
        if (chapterCount != null) {
            TsuzukiText(
                pluralStringResource(MR.plurals.manga_num_chapters, chapterCount, chapterCount),
                style = TsuzukiTheme.typography.subhead,
                color = TsuzukiTheme.colors.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * DET-12: the chapter count and its loading skeleton cross-fade inside a slot reserved at the
 * skeleton's size, so the count arriving neither pops in nor re-centres the line it sits in.
 */
@Composable
internal fun ChapterCountSlot(
    known: Boolean,
    skeletonWidth: Dp,
    skeletonHeight: Dp,
    content: @Composable () -> Unit,
) {
    Box(Modifier.widthIn(min = skeletonWidth), contentAlignment = Alignment.CenterStart) {
        Crossfade(
            targetState = known,
            animationSpec = if (TsuzukiTheme.motion.reduced) {
                androidx.compose.animation.core.snap()
            } else {
                androidx.compose.animation.core.tween(M3Motion.Short3)
            },
            label = "chapterCount",
        ) { show ->
            if (show) {
                content()
            } else {
                dev.errnolink.tsuzuki.designsystem.Skeleton(Modifier.size(skeletonWidth, skeletonHeight))
            }
        }
    }
}
@Composable
fun MangaInfoHeaderApple(
    appBarPadding: Dp,
    manga: Manga,
    accent: Color?,
    sourceName: String,
    sourceLanguage: String,
    isStubSource: Boolean,
    isSourceIncognito: Boolean,
    onCoverClick: () -> Unit,
    doSearch: (query: String, global: Boolean) -> Unit,
    librarySearch: (query: String) -> Unit,
    onSourceClick: () -> Unit,
    coverRatio: MutableFloatState,
    onCoverLoaded: () -> Unit,
    chapterCount: Int?,
    mergedSourceCount: Int = 0,
    actionRow: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = TsuzukiTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(top = appBarPadding + 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TsuzukiTheme.spacing.medium),
    ) {
        val knownRatio = remember(manga.id) { knownCoverRatio(manga.id) }
        var ratio by remember(manga.id) { mutableFloatStateOf(detailsCoverRatio(knownRatio)) }
        DetailsCoverSlot(centered = true, ratio = ratio, modifier = Modifier.padding(horizontal = 20.dp)) { slot ->
            DetailsCover(
                cover = manga.asMangaCover(),
                title = manga.title,
                ratio = ratio,
                onClick = onCoverClick,
                modifier = slot,
                onCoverDecoded = { height, width ->
                    coverRatio.floatValue = height.toFloat() / width
                    if (knownRatio == null) ratio = detailsCoverRatio(width.toFloat() / height)
                },
                onLoaded = onCoverLoaded,
            )
        }
        var titleExpanded by rememberSaveable(manga.id) { mutableStateOf(false) }
        ContextMenu(
            manga.title,
            listOf(
                ContextMenuAction(stringResource(MR.strings.tsuzuki_search_library), icon = { Icon(TsuzukiIcons.Library, null, tint = colors.text) }) { librarySearch(manga.title) },
                ContextMenuAction(stringResource(MR.strings.tsuzuki_search_all_sources), icon = { Icon(TsuzukiIcons.Globe, null, tint = colors.text) }) { doSearch(manga.title, true) },
            ),
            modifier = Modifier.padding(horizontal = 20.dp),
            onClick = { titleExpanded = !titleExpanded },
        ) {
            TsuzukiText(
                manga.title,
                style = TsuzukiTheme.typography.title2.copy(textAlign = TextAlign.Center),
                maxLines = if (titleExpanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = LocalHeroTitleAnchor.current?.title() ?: Modifier,
            )
        }
        val author = manga.author?.takeIf { it.isNotBlank() }
        val artist = manga.artist?.takeIf { it.isNotBlank() && it != manga.author }
        if (author != null || artist != null) {
            Byline(
                author = author?.let(::titleCaseAllCapsWords),
                artist = artist?.let(::titleCaseAllCapsWords),
                onBrowse = { doSearch(it, true) },
                onCopy = { context.copyToClipboard(it, it) },
                color = ContinueActionColor.readableOn(accent ?: colors.accent, colors.background),
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        val statusRes = mangaStatusRes(manga)
        AppleMetaLine(
            statusText = statusRes?.let { stringResource(it) },
            statusColor = mangaStatusColor(statusRes),
            chapterCount = chapterCount,
            sourceLabel = sourceLineLabel(sourceName, sourceLanguage, mergedSourceCount, isStubSource),
            onSourceClick = onSourceClick,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        if (isSourceIncognito) {
            TsuzukiText(
                stringResource(MR.strings.pref_incognito_mode),
                modifier = Modifier.padding(horizontal = 20.dp),
                style = TsuzukiTheme.typography.caption1,
                color = colors.secondary,
            )
        }
        actionRow()
    }
}

@Composable
private fun AppleMetaLine(
    statusText: String?,
    statusColor: Color,
    chapterCount: Int?,
    sourceLabel: String,
    onSourceClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TsuzukiTheme.colors
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TsuzukiTheme.spacing.tiny),
    ) {
        var emitted = false
        statusText?.let {
            TsuzukiText(it, style = TsuzukiTheme.typography.subhead, color = statusColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            emitted = true
        }
        // N12: the count's slot is always present (skeleton until known) so the line never reflows
        // and no `Chapters` placeholder swaps for `31 chapters` after arrival.
        if (emitted) TsuzukiText("·", style = TsuzukiTheme.typography.subhead, color = colors.secondary)
        ChapterCountText(chapterCount)
        TsuzukiText("·", style = TsuzukiTheme.typography.subhead, color = colors.secondary)
        Row(
            Modifier
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onSourceClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TsuzukiText(
                sourceLabel,
                modifier = Modifier.weight(1f, fill = false),
                style = TsuzukiTheme.typography.subhead,
                color = colors.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(TsuzukiIcons.ChevronRight, null, Modifier.size(12.dp), tint = colors.secondary)
        }
    }
}

/** Cleans display names: whitespace words written fully in caps (length > 1, leading letter) become Title Case; order untouched. */
internal fun titleCaseAllCapsWords(name: String): String =
    name.split(' ').joinToString(" ") { word ->
        if (word.length > 1 && word.firstOrNull()?.isLetter() == true && word == word.uppercase(Locale.ROOT)) {
            word.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase(Locale.ROOT) }
        } else {
            word
        }
    }

@Composable
private fun SourceLine(label: String, onClick: () -> Unit) {
    val colors = TsuzukiTheme.colors
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TsuzukiText(
            label,
            Modifier.weight(1f, fill = false),
            style = TsuzukiTheme.typography.subhead,
            color = colors.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(TsuzukiIcons.ChevronRight, null, Modifier.size(14.dp), tint = colors.secondary)
    }
}

@Composable
private fun Byline(
    author: String?,
    artist: String?,
    onBrowse: (String) -> Unit,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = TsuzukiTheme.colors.secondary,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = listOfNotNull(author, artist).joinToString(" · ")
    Box(modifier) {
        TsuzukiText(
            label,
            Modifier
                .clickable { expanded = true }
                .padding(vertical = 2.dp),
            style = TsuzukiTheme.typography.subhead,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        GlassMenu(
            expanded,
            label,
            buildList {
                author?.let {
                    add(ContextMenuAction(stringResource(MR.strings.tsuzuki_browse_author), icon = { Icon(TsuzukiIcons.Search, null, tint = TsuzukiTheme.colors.text) }) { onBrowse(it) })
                }
                artist?.let {
                    add(ContextMenuAction(stringResource(MR.strings.tsuzuki_browse_artist), icon = { Icon(TsuzukiIcons.Search, null, tint = TsuzukiTheme.colors.text) }) { onBrowse(it) })
                }
                add(ContextMenuAction(stringResource(MR.strings.tsuzuki_copy_name), icon = { Icon(TsuzukiIcons.Copy, null, tint = TsuzukiTheme.colors.text) }) { onCopy(label) })
            },
            onDismissRequest = { expanded = false },
        )
    }
}

@Composable
private fun RatingLine(mangaId: Long, onClick: () -> Unit) {
    val stats = rememberMangaDexHeroStats(mangaId = mangaId)
    if (stats == null) {
        TsuzukiText(" ", style = TsuzukiTheme.typography.subhead)
        return
    }
    Row(
        Modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TsuzukiTheme.spacing.tiny),
    ) {
        Icon(TsuzukiIcons.StarFilled, null, Modifier.size(14.dp), tint = if (TsuzukiTheme.colors.isDark) RatingStarDark else RatingStarLight)
        stats.rating?.let { TsuzukiText(String.format("%.2f", it), style = TsuzukiTheme.typography.subhead, maxLines = 1) }
        stats.follows?.let { follows ->
            TsuzukiText(
                (if (stats.rating != null) "· " else "") + stringResource(MR.strings.tsuzuki_follows, formatFollowsCompact(follows)),
                style = TsuzukiTheme.typography.subhead,
                color = TsuzukiTheme.colors.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal fun formatFollowsCompact(follows: Long): String = when {
    follows >= 1_000_000 -> {
        // C-follows: millions are divided by a million — the old 100_000f printed 1 234 567
        // follows as "12.3M".
        val millions = follows / 1_000_000f
        if (millions % 1f == 0f) "${millions.toInt()}M" else String.format("%.1fM", millions)
    }
    follows >= 1_000 -> "${follows / 1_000}K"
    else -> follows.toString()
}

@Composable
private fun DetailsCover(
    cover: tachiyomi.domain.manga.model.MangaCover,
    title: String,
    ratio: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onCoverDecoded: (height: Int, width: Int) -> Unit,
    onLoaded: () -> Unit = {},
) {
    val key = "${cover.sourceId}:${cover.mangaId}:${cover.url}:${cover.lastModified}"
    val arrivals = LocalShellArrivals.current ?: rememberCoverArrivalState()
    val isMdCover = remember(cover.url) { cover.url?.let { exh.md.utils.MdCoverVariantUtil.isMangaDexCover(it) } == true }
    val hasCustomCover = remember(cover.mangaId, cover.lastModified) { Injekt.get<CoverCache>().getCustomCoverFile(cover.mangaId).exists() }
    val smallVariantUrl = remember(cover.url, isMdCover) {
        if (isMdCover) cover.url?.let { exh.md.utils.MdCoverVariantUtil.getVariantUrl(it, exh.md.utils.MangaDexCoverVariant.MEDIUM) } else null
    }
    var originalLoaded by remember(key) { mutableStateOf(false) }
    var smallLoaded by remember(key) { mutableStateOf(false) }
    var originalCached by remember(key) { mutableStateOf(false) }
    var smallCached by remember(key) { mutableStateOf(false) }
    var failed by remember(key) { mutableStateOf(false) }
    var retryAttempt by remember(key) { mutableIntStateOf(0) }
    val retry: () -> Unit = {
        cover.url?.let(dev.errnolink.tsuzuki.network.RefusedCovers.shared::forget)
        retryAttempt++
    }
    val context = LocalContext.current
    val sizeResolver = rememberConstraintsSizeResolver()
    val originalRequest = remember(cover, context, sizeResolver, retryAttempt) {
        ImageRequest.Builder(context)
            .data(cover)
            .size(sizeResolver)
            .precision(Precision.INEXACT)
            .crossfade(false)
            .build()
    }
    val placeholderRequest = remember(smallVariantUrl, context) {
        smallVariantUrl?.let {
            ImageRequest.Builder(context)
                .data(it)
                .precision(Precision.INEXACT)
                .crossfade(false)
                .build()
        }
    }
    val motion = TsuzukiTheme.motion
    val alpha by animateFloatAsState(
        targetValue = if (originalLoaded) 1f else 0f,
        animationSpec = if (motion.reduced) snap() else tween(durationMillis = 300),
        label = "details_cover_crossfade",
    )
    val heroBitmap = remember(cover.mangaId) { dev.errnolink.tsuzuki.ui.motion.MangaHero.source(cover.mangaId)?.image }
    Box(
        modifier
            .readerFallbackOrigin()
            .mangaHeroTarget(cover.mangaId)
            .shadow(6.dp, coverShape()),
    ) {
        CoverImage(
            imageKey = key,
            aspectRatio = ratio,
            loaded = smallLoaded || originalLoaded || heroBitmap != null,
            arrivals = arrivals,
            cached = originalCached || smallCached || heroBitmap != null,
            label = title,
            onClick = if (failed && !smallLoaded && heroBitmap == null) retry else onClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (heroBitmap != null && (!originalLoaded || alpha < 1f)) {
                androidx.compose.foundation.Image(
                    bitmap = heroBitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            if (isMdCover && !hasCustomCover && placeholderRequest != null && (!originalLoaded || alpha < 1f)) {
                AsyncImage(
                    model = placeholderRequest,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    onSuccess = {
                        smallCached = it.result.dataSource == DataSource.MEMORY_CACHE
                        smallLoaded = true
                    },
                )
            }
            AsyncImage(
                model = originalRequest,
                contentDescription = stringResource(MR.strings.manga_cover),
                modifier = Modifier.fillMaxSize().alpha(if (smallLoaded || heroBitmap != null) alpha else 1f).then(sizeResolver),
                contentScale = ContentScale.Crop,
                onSuccess = {
                    val image = it.result.image
                    if (image.width > 0 && image.height > 0) {
                        onCoverDecoded(image.height, image.width)
                    }
                    originalCached = it.result.dataSource == DataSource.MEMORY_CACHE
                    originalLoaded = true
                    onLoaded()
                    failed = false
                },
                onError = { failed = true },
            )
        }
        if (failed && !smallLoaded && heroBitmap == null) {
            Column(
                Modifier.align(Alignment.Center).clickable(onClick = retry).padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(TsuzukiIcons.RefreshCw, stringResource(MR.strings.tsuzuki_retry_cover), Modifier.size(24.dp), tint = TsuzukiTheme.colors.secondary)
                TsuzukiText(stringResource(MR.strings.tsuzuki_cover_unavailable_retry), style = TsuzukiTheme.typography.caption1, color = TsuzukiTheme.colors.secondary)
            }
        }
    }
}

@Composable
internal fun TagChip(
    label: String,
    query: String,
    onSearch: (String) -> Unit,
    onCopy: (String) -> Unit,
    onGlobalSearch: (String, Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = TsuzukiTheme.colors
    Box {
        TsuzukiText(
            label,
            Modifier.clip(RoundedCornerShape(10.dp)).background(colors.elevated)
                .clickable { expanded = true }.padding(vertical = 7.dp, horizontal = 10.dp),
            style = TsuzukiTheme.typography.footnote,
            color = colors.secondary,
        )
        GlassMenu(
            expanded,
            label,
            listOf(
                ContextMenuAction(stringResource(MR.strings.tsuzuki_search_this_source), icon = { Icon(TsuzukiIcons.Search, null, tint = colors.text) }) { onSearch(query) },
                ContextMenuAction(stringResource(MR.strings.tsuzuki_search_all_sources), icon = { Icon(TsuzukiIcons.Globe, null, tint = colors.text) }) { onGlobalSearch(query, true) },
                ContextMenuAction(stringResource(MR.strings.tsuzuki_copy_tag), icon = { Icon(TsuzukiIcons.Copy, null, tint = colors.text) }) { onCopy(query) },
            ),
            onDismissRequest = { expanded = false },
        )
    }
}
