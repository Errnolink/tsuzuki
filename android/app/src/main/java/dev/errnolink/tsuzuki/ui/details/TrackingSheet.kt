package dev.errnolink.tsuzuki.ui.details

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import cafe.adriel.voyager.core.annotation.InternalVoyagerApi
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.M3Motion
import dev.errnolink.tsuzuki.designsystem.SheetDetent
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import eu.kanade.tachiyomi.ui.manga.track.TrackInfoDialogHomeScreen
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

/**
 * Today's tracking hub ([TrackInfoDialogHomeScreen] plus its selector/search/login sub-screens)
 * hosted in a [DetentSheet] instead of the legacy NavigatorAdaptiveSheet.
 * Back pops pushed sub-screens first, then dismisses the sheet.
 */
@OptIn(InternalVoyagerApi::class)
@Composable
fun TrackingSheet(
    mangaId: Long,
    mangaTitle: String,
    sourceId: Long,
    visible: Boolean,
    onDismissRequest: () -> Unit,
) {
    DetentSheet(
        visible = visible,
        title = "Tracking",
        onDismissRequest = onDismissRequest,
        initialDetent = SheetDetent.Large,
        scrollContent = false,
    ) {
        Navigator(
            TrackInfoDialogHomeScreen(
                mangaId = mangaId,
                mangaTitle = mangaTitle,
                sourceId = sourceId,
                embedded = true,
            ),
        ) { sheetNavigator ->
            val motion = TsuzukiTheme.motion
            val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            val slidePx = with(LocalDensity.current) { M3Motion.SharedAxisSlide.roundToPx() }
            val seekable = remember { SeekableTransitionState(sheetNavigator.lastItem) }
            val pageTransition = rememberTransition(seekable, label = "Tracking sheet pages")
            LaunchedEffect(sheetNavigator.lastItem) {
                seekable.animateTo(sheetNavigator.lastItem)
            }
            PredictiveBackHandler(enabled = sheetNavigator.size > 1) { progress ->
                var seeked = false
                try {
                    progress.collect { event ->
                        val below = sheetNavigator.items.getOrNull(sheetNavigator.items.lastIndex - 1)
                        if (below != null) {
                            seeked = true
                            seekable.seekTo(M3Motion.StandardDecelerate.transform(event.progress), below)
                        }
                    }
                    if (seeked) seekable.animateTo(seekable.targetState)
                    sheetNavigator.pop()
                } catch (e: CancellationException) {
                    if (seeked) seekable.animateTo(seekable.currentState)
                    throw e
                }
            }
            with(pageTransition) {
                AnimatedContent(
                    transitionSpec = {
                        trackingPageTransform(sheetNavigator.items, rtl, motion.reduced, slidePx)
                    },
                ) { screen ->
                    screen.Content()
                }
            }
            DisposableEffect(sheetNavigator) {
                onDispose {
                    sheetNavigator.items
                        .asReversed()
                        .forEach(sheetNavigator::dispose)
                }
            }
        }
    }
}

private fun AnimatedContentTransitionScope<Screen>.trackingPageTransform(
    items: List<Screen>,
    rtl: Boolean,
    reduced: Boolean,
    slidePx: Int,
): ContentTransform {
    val forward = when {
        initialState !in items -> false
        targetState !in items -> true
        else -> items.indexOf(initialState) < items.indexOf(targetState)
    }
    val dir = if (forward xor rtl) 1 else -1
    if (reduced) {
        return fadeIn(M3Motion.reducedFade()) togetherWith fadeOut(M3Motion.reducedFade())
    }
    val goneAt = (M3Motion.Long1 * M3Motion.FadeThroughThreshold).roundToInt()
    return (
        slideInHorizontally(M3Motion.emphasized(M3Motion.Long1)) { dir * slidePx } +
            fadeIn(tween(M3Motion.Long1 - goneAt, goneAt, easing = M3Motion.EmphasizedDecelerate))
        ) togetherWith (
        slideOutHorizontally(M3Motion.emphasized(M3Motion.Long1)) { -dir * slidePx } +
            fadeOut(tween(goneAt, easing = M3Motion.EmphasizedAccelerate))
        )
}
