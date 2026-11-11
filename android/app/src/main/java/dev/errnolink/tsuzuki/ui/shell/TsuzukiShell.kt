package dev.errnolink.tsuzuki.ui.shell

import dev.errnolink.tsuzuki.reader.readerOrigin
import android.util.LruCache
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.rememberConstraintsSizeResolver
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import androidx.compose.ui.platform.LocalContext
import coil3.decode.DataSource
import coil3.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.errnolink.tsuzuki.designsystem.CoverArrivalState
import dev.errnolink.tsuzuki.designsystem.CoverImage
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.EmptyState
import dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior
import dev.errnolink.tsuzuki.designsystem.GlassContext
import dev.errnolink.tsuzuki.designsystem.floatingNavigationScroll
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.IconButton
import dev.errnolink.tsuzuki.designsystem.LargeTitleScaffold
import dev.errnolink.tsuzuki.designsystem.TopBarSearch
import dev.errnolink.tsuzuki.designsystem.ProgressRing
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.glassSource
import dev.errnolink.tsuzuki.designsystem.rememberCoverArrivalState
import dev.errnolink.tsuzuki.designsystem.rememberGlassContext
import dev.errnolink.tsuzuki.data.library.PageTotals
import eu.kanade.tachiyomi.ui.home.HomeScreen
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.manga.model.asMangaCover

val LocalShellBottomPadding = staticCompositionLocalOf { 0.dp }
val LocalShellGlass = staticCompositionLocalOf<GlassContext?> { null }
val LocalShellArrivals = staticCompositionLocalOf<CoverArrivalState?> { null }
private val coverRatios = LruCache<String, Float>(1024)

class ShellSheetAction(val label: String, val destructive: Boolean = false, val onClick: () -> Unit)

@Composable
fun shellBottomPadding(): androidx.compose.ui.unit.Dp = maxOf(
    LocalShellBottomPadding.current,
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
)

@Composable
fun ShellAction(label: String, icon: ImageVector, onClick: () -> Unit, active: Boolean = false) {
    IconButton(label, onClick) {
        Icon(icon, null, tint = if (active) TsuzukiTheme.colors.accent else TsuzukiTheme.colors.text)
    }
}

@Composable
fun ShellList(
    title: String,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    reselectKey: String? = null,
    navigateUp: (() -> Unit)? = null,
    snackbar: SnackbarHostState? = null,
    actions: List<ShellTopAction> = emptyList(),
    search: TopBarSearch? = null,
    itemSpacing: androidx.compose.ui.unit.Dp = TsuzukiTheme.spacing.section,
    content: LazyListScope.() -> Unit,
) {
    val clearance = shellBottomPadding()
    val shellGlass = LocalShellGlass.current
    val glass = shellGlass ?: rememberGlassContext()
    val largeTitles = dev.errnolink.tsuzuki.designsystem.largeTitlesEnabled()
    val collapse = dev.errnolink.tsuzuki.designsystem.rememberLargeTitleCollapse(state, largeTitles)
    LaunchedEffect(reselectKey, state) {
        HomeScreen.reselectEvents.collect { if (it == reselectKey) state.scrollToItem(0) }
    }
    DeclareShellTop(
        title = title,
        glass = glass,
        navigateUp = navigateUp,
        actions = actions,
        search = search,
        inlineTitle = { if (largeTitles) collapse.value else 1f },
    )
    Box(modifier.fillMaxSize()) {
        LargeTitleScaffold(
            title,
            state = state,
            glassContext = glass,
            largeTitles = largeTitles,
            collapse = collapse,
            bottomPadding = clearance,
            itemSpacing = itemSpacing,
            content = content,
        )
        ShellSnackbar(snackbar, Modifier.align(Alignment.BottomCenter).statusSnackbarPadding())
    }
}

@Composable
fun ShellActionSheet(
    visible: Boolean,
    title: String,
    actions: List<ShellSheetAction>,
    onDismissRequest: () -> Unit,
) {
    if (!visible) return
    DetentSheet(visible = true, title = title, onDismissRequest = onDismissRequest) {
        actions.forEachIndexed { index, action ->
            ShellSheetRow(
                action.label,
                destructive = action.destructive,
                divider = index < actions.lastIndex,
                onClick = { onDismissRequest(); action.onClick() },
            )
        }
    }
}

@Composable
private fun ShellSheetRow(label: String, destructive: Boolean, divider: Boolean, onClick: () -> Unit) {
    val colors = TsuzukiTheme.colors
    Column {
        Row(
            Modifier.fillMaxWidth()
                .clickable(onClick = onClick)
                .heightIn(min = TsuzukiTheme.spacing.touchTarget)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TsuzukiText(label, color = if (destructive) colors.destructive else colors.text)
        }
        if (divider) {
            Spacer(
                Modifier.padding(start = 16.dp).fillMaxWidth()
                    .height(TsuzukiTheme.spacing.hairline)
                    .background(colors.separator),
            )
        }
    }
}

@Composable
fun rememberPageTotals(): Map<Long, Int> =
    remember { PageTotals() }.inProgressTotals().collectAsState(initial = emptyMap()).value

@Composable
fun ShellScrollEdge(alpha: Float, modifier: Modifier = Modifier) {
    val colors = TsuzukiTheme.colors
    if (alpha <= 0.01f) return
    val brush = remember(colors.background) {
        Brush.verticalGradient(
            0f to colors.background,
            0.38f to colors.background,
            0.55f to colors.background.copy(alpha = 0.92f),
            0.72f to colors.background.copy(alpha = 0.65f),
            0.86f to colors.background.copy(alpha = 0.28f),
            1f to Color.Transparent,
        )
    }
    Spacer(
        modifier.drawWithCache {
            onDrawBehind {
                drawRect(brush = brush, alpha = alpha.coerceIn(0f, 1f))
            }
        },
    )
}

@Composable
fun ShellCaption(text: String, modifier: Modifier = Modifier) {
    TsuzukiText(
        text,
        modifier.padding(vertical = TsuzukiTheme.spacing.tiny),
        TsuzukiTheme.typography.footnote,
        TsuzukiTheme.colors.secondary,
    )
}

@Composable
fun ShellEmpty(
    title: String,
    description: String,
    secondaryActions: List<dev.errnolink.tsuzuki.designsystem.EmptyTextAction> = emptyList(),
    action: (@Composable () -> Unit)? = null,
) {
    EmptyState(title, description, icon = {
        Icon(Icons.Outlined.MenuBook, null, Modifier.size(42.dp), tint = TsuzukiTheme.colors.secondary)
    }, action = action, secondaryActions = secondaryActions)
}

@Composable
fun ShellLoading(label: String = "Loading…") {
    Row(
        Modifier.fillMaxWidth().padding(TsuzukiTheme.spacing.section),
        horizontalArrangement = Arrangement.spacedBy(TsuzukiTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProgressRing(0f, label)
        TsuzukiText(label, color = TsuzukiTheme.colors.secondary)
    }
}

@Composable
fun ShellCover(cover: MangaCover, title: String, modifier: Modifier = Modifier, fixedRatio: Float? = null, hero: Boolean = true) {
    val key = "${cover.sourceId}:${cover.mangaId}:${cover.url}:${cover.lastModified}"
    val arrivals = LocalShellArrivals.current ?: rememberCoverArrivalState()
    var ratio by remember(key) {
        mutableFloatStateOf(
            cover.ratio?.takeIf { it.isFinite() && it > 0f }
                ?: coverRatios.get(key)
                ?: (2f / 3f),
        )
    }
    var loaded by remember(key) { mutableStateOf(false) }
    var cached by remember(key) { mutableStateOf(false) }
    var failed by remember(key) { mutableStateOf(false) }
    var heroImage by remember(key) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    val context = LocalContext.current
    val sizeResolver = rememberConstraintsSizeResolver()
    val request = remember(cover, context, sizeResolver) {
        ImageRequest.Builder(context)
            .data(cover)
            .size(sizeResolver)
            .precision(Precision.INEXACT)
            .crossfade(false)
            .build()
    }
    Box(if (hero) modifier.mangaHeroSource(cover.mangaId, heroImage) else modifier) {
        CoverImage(key, fixedRatio ?: ratio, loaded, arrivals, cached = cached, label = title, modifier = Modifier.fillMaxWidth()) {
            AsyncImage(
                model = request,
                contentDescription = title,
                modifier = Modifier.fillMaxSize().then(sizeResolver),
                contentScale = ContentScale.Crop,
                onSuccess = {
                    val image = it.result.image
                    if (image.width > 0 && image.height > 0) {
                        ratio = cover.ratio?.takeIf { value -> value.isFinite() && value > 0f }
                            ?: (image.width.toFloat() / image.height)
                        coverRatios.put(key, ratio)
                        cover.ratio = ratio
                    }
                    cached = it.result.dataSource == DataSource.MEMORY_CACHE
                    loaded = true
                    heroImage = it.result.image.toBitmap().asImageBitmap()
                },
                onError = {
                    failed = true
                    logcat(LogPriority.WARN, it.result.throwable) { "Cover failed for manga ${cover.mangaId} source ${cover.sourceId}: ${cover.url}" }
                },
            )
        }
        if (failed) {
            Icon(
                Icons.Outlined.MenuBook,
                "Cover unavailable",
                Modifier.align(Alignment.Center).size(28.dp),
                tint = TsuzukiTheme.colors.secondary,
            )
        }
    }
}

@Composable
fun ShellMangaTile(
    manga: Manga,
    selected: Boolean,
    unread: Long = 0,
    downloads: Long = 0,
    overlays: dev.errnolink.tsuzuki.ui.library.LibraryTileOverlays =
        dev.errnolink.tsuzuki.ui.library.LibraryTileOverlays(unread = unread, downloads = downloads),
    badges: dev.errnolink.tsuzuki.ui.library.LibraryTileBadges = dev.errnolink.tsuzuki.ui.library.LibraryTileBadges(),
    modifier: Modifier = Modifier,
    onResume: (() -> Unit)? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Box {
            ShellCover(manga.asMangaCover(), manga.title, Modifier.fillMaxWidth())
            dev.errnolink.tsuzuki.ui.library.LibraryTileOverlayStack(
                overlays = overlays,
                badges = badges,
                selected = selected,
                onResume = onResume,
                modifier = Modifier.matchParentSize(),
            )
        }
        TsuzukiText(manga.title, style = TsuzukiTheme.typography.subhead, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ShellBadge(label: String) {
    TsuzukiText(
        label,
        Modifier.clip(RoundedCornerShape(7.dp))
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.58f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        TsuzukiTheme.typography.caption2.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        color = androidx.compose.ui.graphics.Color.White,
    )
}

@Composable
fun ShellMasonry(
    title: String,
    state: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    reselectKey: String? = null,
    navigateUp: (() -> Unit)? = null,
    actions: List<ShellTopAction> = emptyList(),
    columns: StaggeredGridCells = StaggeredGridCells.Adaptive(140.dp),
    content: LazyStaggeredGridScope.() -> Unit,
) {
    val colors = TsuzukiTheme.colors
    val shellGlass = LocalShellGlass.current
    val glass = shellGlass ?: rememberGlassContext()
    val clearance = shellBottomPadding()
    LaunchedEffect(reselectKey, state) {
        HomeScreen.reselectEvents.collect { if (it == reselectKey) state.scrollToItem(0) }
    }
    DeclareShellTop(title = title, glass = glass, navigateUp = navigateUp, actions = actions)
    val topBodyDp = remember { mutableFloatStateOf(dev.errnolink.tsuzuki.designsystem.TsuzukiTopBar.bodyHeightDp) }
    if (dev.errnolink.tsuzuki.designsystem.TsuzukiTopBar.bodyHeightDp > topBodyDp.floatValue) {
        topBodyDp.floatValue = dev.errnolink.tsuzuki.designsystem.TsuzukiTopBar.bodyHeightDp
    }
    Box(Modifier.fillMaxSize().background(colors.background).statusBarsPadding()) {
        LazyVerticalStaggeredGrid(
            columns = columns,
            state = state,
            modifier = Modifier.fillMaxSize().floatingNavigationScroll(glass).glassSource(glass),
            horizontalArrangement = Arrangement.spacedBy(TsuzukiTheme.spacing.medium),
            verticalItemSpacing = TsuzukiTheme.spacing.section,
            flingBehavior = tsuzukiFlingBehavior(),
            contentPadding = PaddingValues(
                start = TsuzukiTheme.spacing.gutter,
                end = TsuzukiTheme.spacing.gutter,
                top = topBodyDp.floatValue.dp + 8.dp,
                bottom = clearance + TsuzukiTheme.spacing.section,
            ),
        ) {
            content()
        }
    }
}

@Composable
fun ShellRefresh(
    refreshing: Boolean,
    enabled: Boolean = true,
    onRefresh: () -> Unit,
    pullLabel: String = "Pull to update",
    content: @Composable () -> Unit,
) {
    val threshold = with(LocalDensity.current) { 88.dp.toPx() }
    var pulled by remember { mutableFloatStateOf(0f) }
    val currentRefresh by rememberUpdatedState(onRefresh)
    val connection = remember(enabled, refreshing, threshold) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0 && pulled > 0 && source == NestedScrollSource.UserInput) {
                    val consumed = available.y.coerceAtLeast(-pulled)
                    pulled += consumed
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!enabled || refreshing || available.y <= 0 || source != NestedScrollSource.UserInput) return Offset.Zero
                pulled = (pulled + available.y * 0.5f).coerceAtMost(threshold * 1.5f)
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pulled == 0f) return Velocity.Zero
                if (pulled >= threshold) currentRefresh()
                pulled = 0f
                return Velocity(0f, available.y)
            }
        }
    }
    Box(Modifier.fillMaxSize().nestedScroll(connection)) {
        content()
        AnimatedVisibility(
            visible = pulled > 0 && !refreshing,
            enter = fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.8f),
            exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.8f),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Box(
                Modifier
                    .statusBarsPadding()
                    .padding(top = TsuzukiTheme.spacing.small)
                    .clip(RoundedCornerShape(24.dp))
                    .background(TsuzukiTheme.colors.elevated)
                    .padding(10.dp),
            ) {
                ProgressRing(pulled / threshold, pullLabel)
            }
        }
    }
}

@Composable
fun ShellFilterRow(
    label: String,
    state: tachiyomi.core.common.preference.TriState,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    dev.errnolink.tsuzuki.designsystem.GroupedRow(
        title = label,
        value = when (state) {
            tachiyomi.core.common.preference.TriState.DISABLED -> "Any"
            tachiyomi.core.common.preference.TriState.ENABLED_IS -> "Include"
            tachiyomi.core.common.preference.TriState.ENABLED_NOT -> "Exclude"
        },
        enabled = enabled,
        onClick = onClick,
    )
}
