package dev.errnolink.tsuzuki.ui.browse

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.Banner
import dev.errnolink.tsuzuki.designsystem.NoticeTone
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.ui.shell.CoverRowSkeleton
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun GlobalSearchResultItem(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier.padding(vertical = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                TsuzukiText(title, style = TsuzukiTheme.typography.title3, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) TsuzukiText(subtitle, style = TsuzukiTheme.typography.footnote, color = TsuzukiTheme.colors.secondary)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(22.dp), tint = TsuzukiTheme.colors.secondary)
        }
        content()
    }
}

@Composable
fun GlobalSearchLoadingResultItem() {
    CoverRowSkeleton()
}

@Composable
fun GlobalSearchErrorResultItem(message: String?) {
    Banner(
        message ?: stringResource(MR.strings.unknown_error),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        tone = NoticeTone.Error,
    )
}
