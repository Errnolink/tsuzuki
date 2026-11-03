package dev.errnolink.tsuzuki.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import eu.kanade.tachiyomi.util.storage.DiskUtil
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import java.io.File

@Composable
fun StorageInfo(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val storages = remember { DiskUtil.getExternalStorages(context) }
    Column(modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        storages.forEach { StorageInfo(it) }
    }
}

@Composable
private fun StorageInfo(file: File) {
    val context = LocalContext.current
    val colors = TsuzukiTheme.colors
    val available = remember(file) { DiskUtil.getAvailableStorageSpace(file) }
    val total = remember(file) { DiskUtil.getTotalStorageSpace(file) }
    val availableText = remember(available) { Formatter.formatFileSize(context, available) }
    val totalText = remember(total) { Formatter.formatFileSize(context, total) }
    val used = if (total > 0) (1f - available / total.toFloat()).coerceIn(0f, 1f) else 0f
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TsuzukiText(file.absolutePath, style = TsuzukiTheme.typography.subhead, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Canvas(Modifier.fillMaxWidth().height(4.dp).semantics { progressBarRangeInfo = ProgressBarRangeInfo(used, 0f..1f) }) {
            val corner = CornerRadius(size.height / 2f)
            drawRoundRect(colors.separator, cornerRadius = corner)
            drawRoundRect(colors.accent, size = Size(size.width * used, size.height), cornerRadius = corner)
        }
        TsuzukiText(
            stringResource(MR.strings.available_disk_space_info, availableText, totalText),
            style = TsuzukiTheme.typography.footnote,
            color = colors.secondary,
        )
    }
}
