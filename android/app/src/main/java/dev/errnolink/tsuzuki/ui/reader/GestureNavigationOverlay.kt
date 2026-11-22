// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): org/nekomanga/presentation/screens/reader/GestureNavigationOverlay.kt
package dev.errnolink.tsuzuki.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.reader.engine.viewer.ViewerNavigation
import dev.errnolink.tsuzuki.reader.engine.viewer.navigation.DisabledNavigation
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun GestureNavigationOverlay(
    navigation: ViewerNavigation?,
    isLtr: Boolean,
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    invertMode: ViewerNavigation.TappingInvertMode =
        navigation?.invertMode ?: ViewerNavigation.TappingInvertMode.NONE,
) {
    if (navigation == null || navigation is DisabledNavigation) return

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(250)),
        exit = fadeOut(animationSpec = tween(500)),
        modifier = modifier.fillMaxSize(),
    ) {
        BoxWithConstraints(
            modifier =
                Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.any { it.changedToDown() }) {
                                onDismiss()
                            }
                        }
                    }
                }
        ) {
            val width = maxWidth
            val height = maxHeight

            navigation.scaledRegions().forEach { regionItem ->
                val region = regionItem.invert(invertMode)
                val rect = region.rectF
                val directionalRegion = region.type.directionalRegion(isLtr)
                val color = Color(directionalRegion.color)

                val leftDp = width * rect.left
                val topDp = height * rect.top
                val rightDp = width * rect.right
                val bottomDp = height * rect.bottom

                val regionWidth = rightDp - leftDp
                val regionHeight = bottomDp - topDp

                Box(
                    modifier =
                        Modifier.offset(x = leftDp, y = topDp)
                            .size(width = regionWidth, height = regionHeight)
                            .background(color)
                ) {
                    val regionText = stringResource(directionalRegion.nameRes)
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        TsuzukiText(
                            text = regionText,
                            color = Color.Black,
                            style =
                                TsuzukiTheme.typography.title2.copy(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    drawStyle =
                                        Stroke(miter = 10f, width = 6f, join = StrokeJoin.Round),
                                ),
                        )
                        TsuzukiText(
                            text = regionText,
                            color = Color.White,
                            style =
                                TsuzukiTheme.typography.title2.copy(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                        )
                    }
                }
            }
        }
    }
}
