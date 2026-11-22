// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): org/nekomanga/presentation/screens/reader/viewer/ReaderPageOverlay.kt (Neko #3486)
package dev.errnolink.tsuzuki.ui.reader.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.ProgressRing
import dev.errnolink.tsuzuki.designsystem.TsuzukiButton
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.TsuzukiText

import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.delay
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ReaderPageLoadingOverlay(
    status: Page.State,
    progress: Int,
    delayMs: Long = 250L,
    modifier: Modifier = Modifier,
) {
    val isLoading =
        status == Page.State.Queue ||
            status == Page.State.LoadPage ||
            status == Page.State.DownloadImage

    var showIndicator by remember { mutableStateOf(false) }

    LaunchedEffect(isLoading) {
        if (isLoading) {
            showIndicator = false
            delay(delayMs)
            showIndicator = true
        } else {
            showIndicator = false
        }
    }

    AnimatedVisibility(
        visible = isLoading && showIndicator,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize().padding(16.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (progress > 0) {
                    ProgressRing(
                        progress = progress / 100f,
                        label = stringResource(MR.strings.loading),
                        modifier = Modifier.size(28.dp),
                    )
                    TsuzukiText(
                        text = "$progress%",
                        style = TsuzukiTheme.typography.footnote,
                        color = TsuzukiTheme.colors.text,
                    )
                } else {
                    ProgressRing(
                        progress = 0f,
                        label = stringResource(MR.strings.loading),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun ReaderPageErrorOverlay(
    visible: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    tapClaim: TapNavigationClaim? = null,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize().padding(16.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = TsuzukiTheme.colors.destructive,
                    modifier = Modifier.size(32.dp),
                )
                TsuzukiText(
                    text = stringResource(MR.strings.decode_image_error),
                    style = TsuzukiTheme.typography.body,
                    color = TsuzukiTheme.colors.text,
                    maxLines = 4,
                )
                if (message != null) {
                    TsuzukiText(
                        text = message,
                        style = TsuzukiTheme.typography.footnote,
                        color = TsuzukiTheme.colors.secondary,
                        maxLines = 3,
                    )
                }
                TsuzukiButton(
                    label = stringResource(MR.strings.action_retry),
                    onClick = onRetry,
                    modifier = Modifier.claimTapNavigation(tapClaim),
                )
            }
        }
    }
}
