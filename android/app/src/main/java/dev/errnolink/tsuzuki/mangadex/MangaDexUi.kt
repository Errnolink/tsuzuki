package dev.errnolink.tsuzuki.mangadex

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import coil3.toBitmap
import dev.errnolink.tsuzuki.ui.shell.mangaHeroSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onSizeChanged
import tachiyomi.presentation.core.util.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.decode.DataSource
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import dev.errnolink.tsuzuki.designsystem.Banner
import dev.errnolink.tsuzuki.designsystem.CoverArrivalState
import dev.errnolink.tsuzuki.designsystem.CoverImage
import dev.errnolink.tsuzuki.designsystem.EmptyState
import dev.errnolink.tsuzuki.designsystem.IconButton
import dev.errnolink.tsuzuki.designsystem.NoticeTone
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.ProgressRing
import dev.errnolink.tsuzuki.designsystem.Toast
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import eu.kanade.tachiyomi.util.system.openInBrowser
import exh.md.utils.MdConstants
import exh.md.utils.MdUtil

private val remoteCoverRatios = LruCache<String, Float>(512)

@Composable
internal fun MangaDexChromeAction(label: String, icon: ImageVector, onClick: () -> Unit, active: Boolean = false) {
    IconButton(label, onClick) {
        Image(icon, null, colorFilter = ColorFilter.tint(if (active) TsuzukiTheme.colors.accent else TsuzukiTheme.colors.text))
    }
}

@Composable
internal fun MangaDexBackAction(onBack: () -> Unit) {
    MangaDexChromeAction("Back", Icons.AutoMirrored.Outlined.ArrowBack, onBack)
}

@Composable
internal fun MangaDexLoading(label: String = "Loading…") {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProgressRing(0f, label)
        TsuzukiText(label, color = TsuzukiTheme.colors.secondary)
    }
}

@Composable
internal fun MangaDexEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    EmptyState(
        title,
        description,
        modifier,
        icon = {
            Image(Icons.Outlined.MenuBook, null, Modifier.size(42.dp), colorFilter = ColorFilter.tint(TsuzukiTheme.colors.secondary))
        },
        action = action,
    )
}

internal fun openMangaDexSignIn(context: Context) {
    context.openInBrowser(
        MdConstants.Login.authUrl(MdUtil.getPkceChallengeCode()),
        forceDefaultBrowser = true,
    )
}

@Composable
internal fun MangaDexSignInPrompt(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    MangaDexEmptyState(
        "Sign in to MangaDex",
        "Your feed, MD Lists and read markers are tied to your MangaDex account.",
        modifier,
    ) {
        PillButton("Sign in to MangaDex", onClick = { openMangaDexSignIn(context) })
    }
}

@Composable
internal fun MangaDexRemoteCover(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    arrivals: CoverArrivalState,
    fallbackRatio: Float = 2f / 3f,
    onClick: (() -> Unit)? = null,
    heroMangaId: Long? = null,
) {
    var heroImage by remember(url) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    val canonicalUrl = remember(url) { url?.let { exh.md.utils.MdCoverVariantUtil.normalizeCoverUrl(it) } }
    val qualityPref = remember { exh.md.MangaDexPreferences().coverQuality() }
    val quality by qualityPref.collectAsState()
    val coverQuality = remember(quality) { exh.md.utils.MangaDexCoverQuality.fromValue(quality) }
    var displaySizePx by remember { mutableStateOf<android.util.Size?>(null) }
    val effectiveUrl = remember(canonicalUrl, coverQuality, displaySizePx) {
        canonicalUrl?.let { base ->
            val size = displaySizePx
            val variant = exh.md.utils.MdCoverVariantUtil.selectVariant(
                coverQuality,
                size?.width ?: 0,
                size?.height ?: 0,
            )
            exh.md.utils.MdCoverVariantUtil.getVariantUrl(base, variant)
        }
    }
    val context = LocalContext.current
    val request = remember(effectiveUrl) {
        effectiveUrl?.let { target ->
            coil3.request.ImageRequest.Builder(context)
                .data(target)
                .apply {
                    dev.errnolink.tsuzuki.network.MangaDexCoverAgent.userAgentFor(target)?.let { agent ->
                        httpHeaders(NetworkHeaders.Builder().set("User-Agent", agent).build())
                    }
                }
                .build()
        }
    }
    var ratio by remember(canonicalUrl) { mutableFloatStateOf(canonicalUrl?.let { remoteCoverRatios.get(it) } ?: fallbackRatio) }
    var loaded by remember(canonicalUrl) { mutableStateOf(false) }
    var cached by remember(canonicalUrl) { mutableStateOf(false) }
    var failed by remember(canonicalUrl) { mutableStateOf(false) }
    if (request != null && !failed) {
        CoverImage(
            imageKey = canonicalUrl ?: request,
            aspectRatio = ratio,
            loaded = loaded,
            arrivals = arrivals,
            modifier = modifier
                .then(if (heroMangaId != null) Modifier.mangaHeroSource(heroMangaId, heroImage) else Modifier)
                .onSizeChanged { displaySizePx = android.util.Size(it.width, it.height) },
            cached = cached,
            label = contentDescription,
            onClick = onClick,
        ) {
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                onState = { result ->
                    when (result) {
                        is AsyncImagePainter.State.Success -> {
                            val image = result.result.image
                            if (image.width > 0 && image.height > 0) {
                                ratio = image.width.toFloat() / image.height
                                canonicalUrl?.let { remoteCoverRatios.put(it, ratio) }
                                heroImage = image.toBitmap().asImageBitmap()
                            }
                            cached = result.result.dataSource == DataSource.MEMORY_CACHE
                            loaded = true
                        }
                        is AsyncImagePainter.State.Error -> failed = true
                        else -> Unit
                    }
                },
            )
        }
    } else {
        Box(
            modifier.aspectRatio(fallbackRatio).let { if (onClick != null) it.clickable(onClick = onClick) else it },
        ) {
            Image(
                Icons.Outlined.MenuBook,
                "Cover unavailable",
                Modifier.align(Alignment.Center).size(28.dp),
                colorFilter = ColorFilter.tint(TsuzukiTheme.colors.secondary),
            )
        }
    }
}

@Composable
internal fun MangaDexResumeEffect(onResume: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) onResume() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

@Composable
internal fun MangaDexNavigationEffect(screenModel: MangaDexScreenModel, onEvent: (MangaDexNavigation) -> Unit) {
    LaunchedEffect(screenModel) {
        screenModel.events.collect { onEvent(it) }
    }
}

@Composable
internal fun MangaDexNoticeOverlay(state: MangaDexState, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.error?.let { error -> Banner(error, tone = NoticeTone.Error) }
        state.notice?.let { notice -> Toast(notice) }
    }
}
