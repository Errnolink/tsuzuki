package dev.errnolink.tsuzuki.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.TsuzukiText

@Composable
internal fun InfoWidget(text: String) {
    TsuzukiText(
        text,
        Modifier
            .fillMaxWidth()
            .padding(horizontal = PrefsHorizontalPadding, vertical = 8.dp),
        dev.errnolink.tsuzuki.designsystem.TsuzukiTheme.typography.footnote,
        TsuzukiTheme.colors.secondary,
    )
}
