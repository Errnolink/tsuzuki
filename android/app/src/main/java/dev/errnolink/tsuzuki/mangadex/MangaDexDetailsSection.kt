package dev.errnolink.tsuzuki.mangadex

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.designsystem.ContextMenu
import dev.errnolink.tsuzuki.designsystem.ContextMenuAction
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.SheetDetent
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.InsetGroupedList
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.rememberCoverArrivalState
import dev.errnolink.tsuzuki.ui.details.ShimmerBox
import dev.errnolink.tsuzuki.ui.shell.ShellBadge
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import exh.md.dto.RelationshipDto
import exh.md.dto.StatisticsDto
import exh.md.handlers.MangaDexFeatureMapper
import exh.md.handlers.MangaDexFeatures
import exh.md.handlers.MangaDexStatistics
import exh.md.service.MangaDexService
import exh.md.service.MangaDexFeatureRequests
import exh.md.service.SimilarService
import exh.md.service.ThirdPartySimilarService
import exh.md.utils.MdUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mihon.domain.manga.model.toDomainManga
import tachiyomi.domain.manga.interactor.NetworkToLocalManga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.NumberFormat
import eu.kanade.tachiyomi.source.model.SManga

data class MergedRecommendationItem(
    val title: String,
    val subtitle: String,
    val badge: String,
    val coverUrl: String?,
    val localMangaId: Long? = null,
    val searchQuery: String? = null,
    val openUrl: String? = null,
)

internal data class MangaDexDetailsData(
    val source: MangaDexContext? = null,
    val statistics: MangaDexStatistics? = null,
    val creators: List<RelationshipDto> = emptyList(),
    val altTitles: List<String> = emptyList(),
    val recommendations: List<MergedRecommendationItem> = emptyList(),
    val recsLoading: Boolean = false,
    val error: String? = null,
) {
    val isMangaDex: Boolean get() = source != null
}

/**
 * Session-scoped store so the hero rating line, About rows, Similar grid and the legacy
 * details section share ONE network pass per title instead of refetching per composable.
 */
internal object MangaDexDetailsStore {
    private const val TTL_MS = 5 * 60 * 1000L
    private const val MAX_ENTRIES = 32
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val entries: MutableMap<Long, Entry> = java.util.Collections.synchronizedMap(
        object : java.util.LinkedHashMap<Long, Entry>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Entry>): Boolean =
                if (size > MAX_ENTRIES) {
                    eldest.value.job?.cancel()
                    true
                } else {
                    false
                }
        },
    )

    private class Entry(val state: MutableStateFlow<MangaDexDetailsData>) {
        @Volatile var job: Job? = null
        @Volatile var startedAtMs: Long = 0L
        val recsWanted = MutableStateFlow(false)
    }

    private fun entryFor(mangaId: Long): Entry =
        synchronized(entries) { entries.getOrPut(mangaId) { Entry(MutableStateFlow(MangaDexDetailsData())) } }

    fun flow(mangaId: Long): StateFlow<MangaDexDetailsData> {
        val entry = entryFor(mangaId)
        if (entry.job == null || SystemClock.elapsedRealtime() - entry.startedAtMs > TTL_MS) start(entry, mangaId)
        return entry.state
    }

    fun refresh(mangaId: Long) {
        entries[mangaId]?.let { start(it, mangaId) }
    }

    fun requestRecommendations(mangaId: Long) {
        val entry = entryFor(mangaId)
        entry.recsWanted.value = true
        if (entry.state.value.recommendations.isEmpty()) entry.state.value = entry.state.value.copy(recsLoading = true)
        if (entry.job == null) start(entry, mangaId)
    }

    private fun start(entry: Entry, mangaId: Long) {
        entry.job?.cancel()
        entry.startedAtMs = SystemClock.elapsedRealtime()
        entry.job = scope.launch { load(mangaId, entry) }
    }

    private suspend fun load(mangaId: Long, entry: Entry) {
        val state = entry.state
        withContext(Dispatchers.IO) {
            state.value = if (state.value.source != null) {
                state.value.copy(error = null)
            } else {
                MangaDexDetailsData()
            }
            val resolved = resolveMangaDex(mangaId) ?: run {
                android.util.Log.w("TsuzukiMD", "details: no MangaDex source for manga $mangaId")
                state.value = MangaDexDetailsData()
                return@withContext
            }
            val id = MdUtil.getMangaId(resolved.manga.url)
            val client = resolved.source.baseHttpClient
            val features = MangaDexFeatures(resolved.source)
            val mangaDexService = MangaDexService(client, MangaDexFeatureRequests.headers)
            try {
                val manga = MangaDexSharedFetch.viewManga(client, id).data
                val stats = features.request<StatisticsDto>(MangaDexFeatureRequests.statistics(id)).statistics.getValue(id)
                val statistics = MangaDexFeatureMapper.statistics(
                    stats,
                    MangaDexFeatureMapper.estimatedMissing(MangaDexSharedFetch.aggregate(client, id, features.language)),
                )
                state.value = state.value.copy(
                    source = resolved,
                    creators = manga.relationships.filter { it.type == "author" || it.type == "artist" }.distinctBy { it.id },
                    altTitles = manga.attributes.altTitles.mapNotNull { it["en"] ?: it.values.firstOrNull() },
                    statistics = statistics,
                )
                MangaDexHeroStatsCache.put(mangaId, MangaDexHeroStats(statistics.rating, statistics.follows))

                entry.recsWanted.first { it }
                state.value = state.value.copy(recsLoading = true)
                val similarService = SimilarService(resolved.source.baseHttpClient)
                val thirdPartyService = ThirdPartySimilarService(resolved.source.baseHttpClient, Injekt.get<Application>())
                val trackerLinks = MangaDexFeatureMapper.trackerLinks(manga.attributes.links)

                try {
                    coroutineScope {
                        val similarDef = async {
                            runCatching { similarService.getSimilarManga(id) }.getOrNull()
                        }
                        val thirdPartyDef = async {
                            runCatching { thirdPartyService.fetchRecommendations(id, trackerLinks) }.getOrDefault(emptyList())
                        }

                        val similarDto = similarDef.await()
                        val thirdPartyRecs = thirdPartyDef.await()

                        val similarIds = similarDto?.matches.orEmpty().take(8).map { it.id }
                        val resolvedDexMap = if (similarIds.isNotEmpty()) {
                            runCatching {
                                mangaDexService.viewMangas(similarIds).data.associateBy { it.id }
                            }.getOrDefault(emptyMap())
                        } else emptyMap()

                        val importManga = Injekt.get<NetworkToLocalManga>()
                        val lang = MangaDexFeatures(resolved.source).language

                        val items = mutableListOf<MergedRecommendationItem>()
                        val seenDexIds = mutableSetOf(id)

                        similarDto?.matches.orEmpty().forEach { match ->
                            if (seenDexIds.add(match.id)) {
                                val dto = resolvedDexMap[match.id]
                                val title = dto?.let { MdUtil.getTitleFromManga(it.attributes, lang, true) }
                                    ?: match.title["en"] ?: match.title.values.firstOrNull() ?: "Similar title"
                                val coverFile = dto?.relationships?.firstOrNull { it.type == "cover_art" }?.attributes?.fileName
                                val coverUrl = coverFile?.let { MdUtil.cdnCoverUrl(match.id, it) }
                                val localManga = dto?.let {
                                    runCatching {
                                        importManga(MdUtil.createMangaEntry(it, lang).toDomainManga(resolved.source.id), updateInfo = false)
                                    }.getOrNull()
                                }

                                items.add(
                                    MergedRecommendationItem(
                                        title = title,
                                        subtitle = String.format("%.0f%% match", match.score * 100),
                                        badge = "MangaDex",
                                        coverUrl = coverUrl,
                                        localMangaId = localManga?.id,
                                        searchQuery = title,
                                    ),
                                )
                            }
                        }

                        thirdPartyRecs.forEach { rec ->
                            val dexUuid = rec.mangadexId
                            if (dexUuid == null || seenDexIds.add(dexUuid)) {
                                val localManga = if (dexUuid != null) {
                                    runCatching {
                                        val dto = mangaDexService.viewManga(dexUuid).data
                                        importManga(MdUtil.createMangaEntry(dto, lang).toDomainManga(resolved.source.id), updateInfo = false)
                                    }.getOrNull()
                                } else null

                                items.add(
                                    MergedRecommendationItem(
                                        title = rec.title,
                                        subtitle = rec.scoreOrVotes,
                                        badge = rec.source,
                                        coverUrl = rec.coverUrl,
                                        localMangaId = localManga?.id,
                                        searchQuery = rec.title,
                                        openUrl = rec.originalUrl,
                                    ),
                                )
                            }
                        }

                        state.value = state.value.copy(recommendations = items, recsLoading = false)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    android.util.Log.w("TsuzukiMD", "details: recommendations failed for $mangaId", e)
                    state.value = state.value.copy(recsLoading = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("TsuzukiMD", "details: load failed for $mangaId", e)
                state.value = state.value.copy(error = e.message ?: "MangaDex details could not be loaded", recsLoading = false)
            }
        }
    }
}

data class MangaDexHeroStats(val rating: Double?, val follows: Long?)

private object MangaDexHeroStatsCache {
    private val prefs by lazy {
        Injekt.get<Application>().getSharedPreferences("tsuzuki_md_hero_stats", android.content.Context.MODE_PRIVATE)
    }

    fun get(mangaId: Long): MangaDexHeroStats? {
        val raw = prefs.getString(mangaId.toString(), null) ?: return null
        val (rating, follows) = raw.split('|').takeIf { it.size == 2 } ?: return null
        return MangaDexHeroStats(rating.toDoubleOrNull(), follows.toLongOrNull()).takeIf { it.rating != null || it.follows != null }
    }

    fun warmUp() {
        prefs.all
    }

    fun put(mangaId: Long, stats: MangaDexHeroStats) {
        if (stats.rating == null && stats.follows == null) return
        prefs.edit().putString(mangaId.toString(), "${stats.rating ?: ""}|${stats.follows ?: ""}").apply()
    }
}

internal fun warmUpMangaDexHeroStatsCache() {
    MangaDexHeroStatsCache.warmUp()
}

@Composable
fun rememberMangaDexHeroStats(mangaId: Long): MangaDexHeroStats? {
    val data by MangaDexDetailsStore.flow(mangaId).collectAsState()
    val cached = remember(mangaId) { MangaDexHeroStatsCache.get(mangaId) }
    val stats = data.statistics ?: return cached
    return remember(stats) { MangaDexHeroStats(stats.rating, stats.follows) }
}

@Composable
fun MangaDexRatingSheet(
    mangaId: Long,
    visible: Boolean,
    onDismissRequest: () -> Unit,
) {
    val data by MangaDexDetailsStore.flow(mangaId).collectAsState()
    val stats = data.statistics ?: return
    DetentSheet(
        visible,
        "Rating distribution",
        onDismissRequest = onDismissRequest,
        initialDetent = SheetDetent.FitContent,
    ) {
        RatingDistributionRows(stats)
    }
}

@Composable
fun MangaDexAboutRows(mangaId: Long) {
    val data by MangaDexDetailsStore.flow(mangaId).collectAsState()
    val sourceContext = data.source ?: return
    val activity = LocalContext.current
    val manga = sourceContext.manga
    val numbers = remember { NumberFormat.getIntegerInstance() }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        data.statistics?.let { stats ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TsuzukiText("MangaDex rating", style = TsuzukiTheme.typography.footnote, color = TsuzukiTheme.colors.secondary)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TsuzukiText(
                        stats.rating?.let { String.format("%.2f", it) } ?: "Not rated",
                        style = TsuzukiTheme.typography.title1,
                    )
                    RatingDistributionRows(
                        stats,
                        modifier = Modifier.weight(1f),
                        labelStyle = false,
                    )
                }
            }
        }
        InsetGroupedList(title = "Information") {
            manga.author?.takeIf { it.isNotBlank() }?.let { GroupedRow("Author", value = it) }
            manga.artist?.takeIf { it.isNotBlank() && it != manga.author }?.let { GroupedRow("Artist", value = it) }
            val status = when (manga.status.toInt()) {
                SManga.ONGOING -> MR.strings.ongoing
                SManga.COMPLETED -> MR.strings.completed
                SManga.LICENSED -> MR.strings.licensed
                SManga.PUBLISHING_FINISHED -> MR.strings.publishing_finished
                SManga.CANCELLED -> MR.strings.cancelled
                SManga.ON_HIATUS -> MR.strings.on_hiatus
                else -> null
            }
            status?.let { GroupedRow("Status", value = stringResource(it)) }
            data.statistics?.let { stats ->
                GroupedRow("Follows", value = stats.follows?.let(numbers::format) ?: "Not available")
                GroupedRow(
                    "Comments",
                    value = numbers.format(stats.comments),
                    chevron = stats.threadId != null,
                    onClick = stats.threadId?.let { thread ->
                        { sourceContext.openThread(activity, thread) }
                    },
                )
                stats.estimatedMissing?.let { missing ->
                    GroupedRow("Estimated missing chapters", value = numbers.format(missing), divider = stats.unavailable > 0)
                }
                if (stats.unavailable > 0) GroupedRow("Unavailable chapters", value = numbers.format(stats.unavailable), divider = false)
            }
            if (data.altTitles.isNotEmpty()) {
                GroupedRow("Alt titles", subtitle = data.altTitles.joinToString("\n"), divider = false)
            }
        }
        val namedCreators = remember(data.creators) { data.creators.filter { !it.attributes?.name.isNullOrBlank() } }
        if (namedCreators.isNotEmpty()) {
            InsetGroupedList(title = "Creators") {
                namedCreators.forEachIndexed { index, creator ->
                    CreatorBrowseRow(
                        creator = creator,
                        sourceId = sourceContext.source.id,
                        last = index == namedCreators.lastIndex,
                    )
                }
            }
        }
    }
}

@Composable
fun MangaDexSimilarGrid(mangaId: Long, modifier: Modifier = Modifier) {
    LaunchedEffect(mangaId) { MangaDexDetailsStore.requestRecommendations(mangaId) }
    val data by MangaDexDetailsStore.flow(mangaId).collectAsState()
    if (data.recommendations.isEmpty() && !data.recsLoading) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 32.dp), contentAlignment = Alignment.Center) {
            TsuzukiText(
                when {
                    data.error != null -> "Couldn't load similar titles. Pull to refresh or try again later."
                    data.source == null -> "Similar titles come from MangaDex and aren't available for this title."
                    else -> "No similar titles found."
                },
                style = TsuzukiTheme.typography.subhead,
                color = TsuzukiTheme.colors.secondary,
            )
        }
        return
    }
    if (data.recommendations.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) {
                ShimmerBox(
                    Modifier.fillMaxWidth().aspectRatio(2.4f).clip(RoundedCornerShape(TsuzukiCorners.cover)),
                )
            }
        }
        return
    }
    val arrivals = rememberCoverArrivalState()
    val source = data.source ?: return
    LazyVerticalGrid(flingBehavior = dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior(), columns = GridCells.Fixed(3),
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),) { gridItems(data.recommendations, key = { "${it.badge}-${it.title}" }) { item ->
        RecommendationCard(item, source, arrivals)
    } }
}

@Composable
fun MangaDexDetailsSection(mangaId: Long) {
    LaunchedEffect(mangaId) { MangaDexDetailsStore.requestRecommendations(mangaId) }
    val context = LocalContext.current
    val navigator = LocalNavigator.currentOrThrow
    val state by MangaDexDetailsStore.flow(mangaId).collectAsState()
    val source = state.source ?: return
    var showDistribution by remember { mutableStateOf(false) }
    val numbers = remember { NumberFormat.getIntegerInstance() }
    val namedCreators = remember(state.creators) { state.creators.filter { !it.attributes?.name.isNullOrBlank() } }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        InsetGroupedList(
            title = "MangaDex",
            footer = if (state.statistics?.estimatedMissing != null) {
                "Missing-chapter estimates use MangaDex aggregate numbering; specials and volume resets may affect them."
            } else {
                null
            },
        ) {
            state.statistics?.let { stats ->
                GroupedRow("Rating", value = stats.rating?.let { String.format("%.2f / 10", it) } ?: "Not rated", chevron = true, onClick = { showDistribution = true })
                GroupedRow("Follows", value = stats.follows?.let(numbers::format) ?: "Not available")
                GroupedRow(
                    "Comments",
                    value = numbers.format(stats.comments),
                    chevron = stats.threadId != null,
                    onClick = stats.threadId?.let { thread -> { source.openThread(context, thread) } },
                )
                stats.estimatedMissing?.let { missing ->
                    GroupedRow("Estimated missing chapters", value = numbers.format(missing), divider = stats.unavailable > 0)
                }
                if (stats.unavailable > 0) GroupedRow("Unavailable chapters", value = numbers.format(stats.unavailable), divider = false)
            }
            state.error?.let { GroupedRow("Statistics unavailable", subtitle = it, value = "Retry", divider = false, onClick = { MangaDexDetailsStore.refresh(mangaId) }) }
            if (state.statistics == null && state.error == null) GroupedRow("Loading statistics…", divider = false)
        }
        if (namedCreators.isNotEmpty()) InsetGroupedList(title = "Creators") {
            namedCreators.forEachIndexed { index, creator ->
                CreatorBrowseRow(
                    creator = creator,
                    sourceId = source.source.id,
                    last = index == namedCreators.lastIndex,
                )
            }
        }
        if (state.recommendations.isNotEmpty()) {
            val arrivals = rememberCoverArrivalState()
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TsuzukiText("Recommendations", Modifier.padding(horizontal = 20.dp), TsuzukiTheme.typography.title3)
                LazyRow(flingBehavior = dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior(), contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),) { items(state.recommendations, key = { "${it.badge}-${it.title}" }) { item ->
                    RecommendationCard(item, source, arrivals, width = 138.dp)
                } }
            }
        }
    }
    state.statistics?.let { stats ->
        DetentSheet(
            showDistribution,
            "Rating distribution",
            onDismissRequest = { showDistribution = false },
            initialDetent = SheetDetent.FitContent,
        ) {
            RatingDistributionRows(stats)
        }
    }
}

@Composable
private fun RatingDistributionRows(
    stats: MangaDexStatistics,
    modifier: Modifier = Modifier,
    labelStyle: Boolean = true,
) {
    val max = stats.distribution.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    val numbers = remember { NumberFormat.getIntegerInstance() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        stats.distribution.entries.sortedByDescending { it.key }.forEach { (rating, votes) ->
            val fraction = votes.toFloat() / max
            val fill by animateFloatAsState(
                fraction,
                if (TsuzukiTheme.motion.reduced) snap() else tween(220),
                label = "Rating bar fill",
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (labelStyle) {
                    TsuzukiText(rating.toString(), Modifier.width(24.dp), TsuzukiTheme.typography.footnote)
                }
                Box(Modifier.weight(1f).height(8.dp).background(TsuzukiTheme.colors.elevated)) {
                    Box(Modifier.fillMaxWidth(fill.coerceIn(0f, 1f)).height(8.dp).background(TsuzukiTheme.colors.accent))
                }
                if (labelStyle) {
                    TsuzukiText(numbers.format(votes), Modifier.widthIn(min = 54.dp), TsuzukiTheme.typography.caption1)
                }
            }
        }
    }
}

@Composable
private fun CreatorBrowseRow(creator: RelationshipDto, sourceId: Long, last: Boolean) {
    val context = LocalContext.current
    val navigator = LocalNavigator.currentOrThrow
    val name = creator.attributes?.name ?: return
    val browse = { navigator.push(BrowseSourceScreen(sourceId, "author:${creator.id}")); Unit }
    ContextMenu(
        name,
        listOf(
            ContextMenuAction("Browse titles", onClick = browse),
            ContextMenuAction("Copy name") { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Creator", name)) },
        ),
        onClick = browse,
    ) {
        GroupedRow(name, subtitle = creator.type.replaceFirstChar(Char::uppercase), chevron = true, divider = !last)
    }
}

@Composable
private fun RecommendationCard(
    item: MergedRecommendationItem,
    context: MangaDexContext,
    arrivals: dev.errnolink.tsuzuki.designsystem.CoverArrivalState,
    width: androidx.compose.ui.unit.Dp? = null,
) {
    val navigator = LocalNavigator.currentOrThrow
    val activity = LocalContext.current
    Column(
        (if (width != null) Modifier.width(width) else Modifier).clickable {
            if (item.localMangaId != null) {
                navigator.push(MangaScreen(item.localMangaId, true))
            } else if (item.searchQuery != null) {
                navigator.push(BrowseSourceScreen(context.source.id, item.searchQuery))
            } else if (item.openUrl != null) {
                activity.startActivity(WebViewActivity.newIntent(activity, item.openUrl, context.source.id, item.title))
            }
        },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            MangaDexRemoteCover(
                url = item.coverUrl,
                contentDescription = item.title,
                modifier = Modifier.fillMaxWidth(),
                arrivals = arrivals,
            )
            Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                ShellBadge(item.badge)
            }
        }
        TsuzukiText(item.title, style = TsuzukiTheme.typography.subhead, maxLines = 2, overflow = TextOverflow.Ellipsis)
        TsuzukiText(item.subtitle, style = TsuzukiTheme.typography.caption1, color = TsuzukiTheme.colors.secondary, maxLines = 1)
    }
}

private fun MangaDexContext.openThread(context: Context, threadId: Long) {
    context.startActivity(
        WebViewActivity.newIntent(context, "https://forums.mangadex.org/threads/$threadId", source.id, "MangaDex comments"),
    )
}
