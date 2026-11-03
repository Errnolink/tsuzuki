package dev.errnolink.tsuzuki.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.ui.details.ShimmerBox
import dev.errnolink.tsuzuki.ui.details.SkeletonPulseClock
/**
 * Cold-open loading surface shown while the first chapter's pages are being fetched.
 * Uses the reader's canvas color and the CR-3 shimmer language; the viewer takes over
 * as soon as pages arrive.
 */
@Composable
fun ReaderLoadingSurface(
    canvasColor: Color,
    dark: Boolean,
    modifier: Modifier = Modifier,
) {
    TsuzukiTheme(dark = dark) {
        Box(
            modifier
                .fillMaxSize()
                .background(canvasColor),
            contentAlignment = Alignment.Center,
        ) {
            SkeletonPulseClock {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small),
                ) {
                    ShimmerBox(
                        modifier = Modifier
                            .width(126.dp)
                            .height(189.dp),
                        shape = RoundedCornerShape(TsuzukiCorners.cover),
                    )
                    ShimmerBox(
                        modifier = Modifier
                            .width(168.dp)
                            .height(12.dp),
                    )
                    ShimmerBox(
                        modifier = Modifier
                            .width(112.dp)
                            .height(12.dp),
                    )
                }
            }
        }
    }
}
