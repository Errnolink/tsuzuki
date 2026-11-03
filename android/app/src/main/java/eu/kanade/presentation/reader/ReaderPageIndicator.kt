package eu.kanade.presentation.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.GlassSurface
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import androidx.compose.ui.tooling.preview.PreviewLightDark

@Composable
fun ReaderPageIndicator(
    // SY -->
    currentPage: String,
    // SY <--
    totalPages: Int,
    modifier: Modifier = Modifier,
) {
    if (currentPage.isEmpty() || totalPages <= 0) return

    val text = "$currentPage / $totalPages"

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier,
    ) {
        GlassSurface(null, shape = RoundedCornerShape(50)) {
            TsuzukiText(
                text,
                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                TsuzukiTheme.typography.footnote.copy(fontFeatureSettings = "tnum"),
                TsuzukiTheme.colors.text,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ReaderPageIndicatorPreview() {
    TachiyomiPreviewTheme {
        ReaderPageIndicator(currentPage = "10", totalPages = 69)
    }
}
