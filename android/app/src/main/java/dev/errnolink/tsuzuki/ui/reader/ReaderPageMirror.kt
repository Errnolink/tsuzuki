package dev.errnolink.tsuzuki.ui.reader

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import dev.errnolink.tsuzuki.designsystem.GlassContext
import dev.errnolink.tsuzuki.designsystem.glassSource
import kotlinx.coroutines.delay

/**
 * Feeds the native reader page into the chrome's glass without moving it: the page View keeps its
 * own parent and touch handling (UPSTREAM local deviation 2026-10-06), and this invisible layer
 * replays its display list as the blur source. It re-records only while the chrome is showing, on
 * a page change and a few times a second for late-loading images; hidden chrome costs nothing.
 */
@Composable
internal fun ReaderPageMirror(
    page: View,
    glass: GlassContext,
    active: Boolean,
    pageKey: Any?,
    modifier: Modifier = Modifier,
) {
    if (!active) return
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(pageKey) {
        frame++
        delay(350L)
        frame++
        delay(850L)
        frame++
    }
    Box(
        modifier
            .graphicsLayer { alpha = 0f }
            .glassSource(glass)
            .drawBehind {
                frame
                drawIntoCanvas { page.draw(it.nativeCanvas) }
            },
    )
}
