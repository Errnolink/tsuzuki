package dev.errnolink.tsuzuki.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.designsystem.GlassMaterial
import dev.errnolink.tsuzuki.designsystem.GlassSurface
import dev.errnolink.tsuzuki.designsystem.M3Motion
import dev.errnolink.tsuzuki.designsystem.ProgressRing
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

class StatusLayerState {
    var text by mutableStateOf<String?>(null)
    var activity by mutableStateOf<String?>(null)
    var progress by mutableStateOf<Float?>(null)
    var heightPx by mutableIntStateOf(0)
}

val LocalStatusLayer = staticCompositionLocalOf<StatusLayerState?> { null }

fun Flow<Boolean>.statusPillVisibility(
    showAfterMillis: Long = 300,
    minVisibleMillis: Long = 600,
): Flow<Boolean> = channelFlow {
    var showing = false
    var showJob: Job? = null
    var minHoldJob: Job? = null
    var hideJob: Job? = null
    coroutineScope {
        collect { running ->
            if (running) {
                hideJob?.cancel()
                hideJob = null
                if (!showing && showJob == null) {
                    showJob = launch {
                        delay(showAfterMillis)
                        showJob = null
                        showing = true
                        minHoldJob = launch { delay(minVisibleMillis) }
                        send(true)
                    }
                }
            } else {
                showJob?.cancel()
                showJob = null
                if (showing && hideJob == null) {
                    val hold = minHoldJob
                    hideJob = launch {
                        hold?.join()
                        showing = false
                        minHoldJob = null
                        hideJob = null
                        send(false)
                    }
                }
            }
        }
    }
}

@Composable
fun statusBottomInset(): Dp {
    val shell = LocalShellBottomPadding.current
    val state = LocalStatusLayer.current ?: return shell
    if (state.heightPx <= 0) return shell
    val pill = with(LocalDensity.current) { state.heightPx.toDp() }
    return shell + pill + 8.dp
}

@Composable
fun Modifier.statusSnackbarPadding(): Modifier {
    val inset = statusBottomInset() + 8.dp
    val motion = TsuzukiTheme.motion
    val animated by animateDpAsState(
        inset,
        animationSpec = if (motion.reduced) snap() else M3Motion.standard(M3Motion.Medium1),
        label = "snackbar inset",
    )
    return this.padding(bottom = animated)
}

@Composable
fun StatusLayerHost(
    state: StatusLayerState,
    downloadedOnlyMode: Boolean,
    incognitoMode: Boolean,
    indexing: Boolean,
    restoring: Boolean,
    syncing: Boolean,
    updating: Boolean,
    progress: Float?,
) {
    val running = rememberUpdatedState(indexing || restoring || syncing || updating)
    var activityVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        snapshotFlow { running.value }
            .statusPillVisibility()
            .collect { activityVisible = it }
    }
    val visible = downloadedOnlyMode || incognitoMode || activityVisible
    val activity = when {
        updating -> stringResource(MR.strings.updating_library)
        syncing -> stringResource(MR.strings.syncing_library)
        restoring -> stringResource(MR.strings.restoring_backup)
        indexing -> stringResource(MR.strings.download_notifier_cache_renewal)
        else -> null
    }
    val labels = buildList {
        if (downloadedOnlyMode) add(stringResource(MR.strings.label_downloaded_only))
        if (incognitoMode) add(stringResource(MR.strings.pref_incognito_mode))
        if (activityVisible && activity != null) add(statusLabel(activity, progress))
    }
    SideEffect {
        val text = labels.joinToString(" · ")
        state.text = if (visible && text.isNotEmpty()) text else null
        state.activity = if (activityVisible) activity else null
        state.progress = if (activityVisible) progress?.takeIf { it >= 0.01f } else null
    }
}

@Composable
fun StatusPill(modifier: Modifier = Modifier) {
    val state = LocalStatusLayer.current ?: return
    val motion = TsuzukiTheme.motion
    val navigator = LocalNavigator.currentOrThrow
    val homeTop = navigator.lastItem == eu.kanade.tachiyomi.ui.home.HomeScreen
    var heldText by remember { mutableStateOf<String?>(null) }
    var heldActivity by remember { mutableStateOf<String?>(null) }
    var heldProgress by remember { mutableStateOf<Float?>(null) }
    if (state.text != null) {
        heldText = state.text
        heldActivity = state.activity
        heldProgress = state.progress
    }
    val visible = state.text != null && homeTop
    if (!visible) {
        SideEffect { state.heightPx = 0 }
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier
            .padding(bottom = LocalShellBottomPadding.current + 8.dp)
            .widthIn(max = 280.dp),
        enter = if (motion.reduced) {
            fadeIn(M3Motion.reducedFade())
        } else {
            scaleIn(
                M3Motion.enter(M3Motion.Medium1),
                initialScale = 0.92f,
                transformOrigin = TransformOrigin(0.5f, 1f),
            ) + fadeIn(tween(M3Motion.Short3, easing = M3Motion.EmphasizedDecelerate))
        },
        exit = if (motion.reduced) {
            fadeOut(M3Motion.reducedFade())
        } else {
            fadeOut(M3Motion.exit(M3Motion.Short3))
        },
    ) {
        GlassSurface(
            context = LocalShellGlass.current,
            material = GlassMaterial.Regular,
        ) {
            Row(
                Modifier
                    .height(36.dp)
                    .onSizeChanged { state.heightPx = it.height }
                    .padding(
                        start = if (heldActivity != null) 10.dp else 14.dp,
                        end = 14.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                heldActivity?.let { activityLabel ->
                    ProgressRing(
                        heldProgress ?: 0f,
                        activityLabel,
                        Modifier.size(16.dp),
                    )
                }
                TsuzukiText(
                    heldText.orEmpty(),
                    style = TsuzukiTheme.typography.footnote,
                    color = TsuzukiTheme.colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}