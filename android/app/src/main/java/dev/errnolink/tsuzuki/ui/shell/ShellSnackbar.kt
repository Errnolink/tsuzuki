package dev.errnolink.tsuzuki.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.Banner
import dev.errnolink.tsuzuki.designsystem.M3Motion
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import kotlinx.coroutines.delay

@Composable
fun ShellSnackbar(state: SnackbarHostState?, modifier: Modifier = Modifier) {
    val motion = TsuzukiTheme.motion
    val data = state?.currentSnackbarData
    var held by remember { mutableStateOf<SnackbarData?>(null) }
    if (data != null) held = data
    LaunchedEffect(data) {
        if (data != null && data.visuals.duration != SnackbarDuration.Indefinite) {
            delay(if (data.visuals.actionLabel == null) 4000L else 10000L)
            data.dismiss()
        }
    }
    AnimatedVisibility(
        visible = data != null,
        modifier = modifier.padding(horizontal = TsuzukiTheme.spacing.gutter),
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
        held?.let { current ->
            Banner(
                current.visuals.message,
                actionLabel = current.visuals.actionLabel,
                onAction = current.visuals.actionLabel?.let { { current.performAction() } },
                onDismiss = current::dismiss,
            )
        }
    }
}