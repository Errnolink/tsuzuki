package dev.errnolink.tsuzuki.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.ProgressRing
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Cold-open loading surface shown while the first chapter's pages are being fetched: the reader's
 * canvas colour (the same colour the transition's veil grew in and the page will sit on) with a
 * quiet progress ring. The viewer takes over as soon as pages arrive: the ring goes the moment
 * [loading] turns false, while the surface itself fades out over the decoded page.
 */
@Composable
fun ReaderLoadingSurface(
    canvasColor: Color,
    dark: Boolean,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    TsuzukiTheme(dark = dark) {
        Box(
            modifier
                .fillMaxSize()
                .background(canvasColor),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) ProgressRing(0f, stringResource(MR.strings.loading), Modifier.size(22.dp))
        }
    }
}
