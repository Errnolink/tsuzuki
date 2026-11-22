package dev.errnolink.tsuzuki.ui.browse

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.IconButton
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.ProgressMark
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import eu.kanade.presentation.browse.components.ExtensionIcon
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun extensionSubtitle(extension: Extension, context: Context): String =
    listOfNotNull(
        extension.versionName,
        extension.lang?.let { LocaleHelper.getSourceDisplayName(it, context) },
        extension.storeName,
        if (extension is Extension.Installed && extension.isObsolete) "Obsolete" else null,
        if (extension.isNsfw) stringResource(MR.strings.ext_nsfw_short) else null,
    ).joinToString(" · ")

@Composable
internal fun CurrentExtensionRow(
    extension: Extension,
    busy: Boolean,
    installStep: InstallStep,
    index: Int,
    lastIndex: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOpenWebView: () -> Unit,
    onTrailingClick: () -> Unit,
) {
    val context = LocalContext.current
    val colors = TsuzukiTheme.colors
    SourceRow(
        title = extension.name,
        onClick = onClick,
        onLongClick = onLongClick,
        subtitle = extensionSubtitle(extension, context),
        subtitleStyle = TsuzukiTheme.typography.caption1,
        subtitleMaxLines = 2,
        subtitleSpacing = 3.dp,
        leading = {
            if (busy) ProgressMark(0f, extension.name)
            else ExtensionIcon(extension, Modifier.size(32.dp))
        },
        dividerStart = if (index < lastIndex) 56.dp else null,
        contentPadding = PaddingValues(12.dp),
        modifier = Modifier
            .fillMaxWidth().padding(horizontal = 20.dp)
            .clip(
                RoundedCornerShape(
                    topStart = if (index == 0) 14.dp else 0.dp, topEnd = if (index == 0) 14.dp else 0.dp,
                    bottomStart = if (index == lastIndex) 14.dp else 0.dp,
                    bottomEnd = if (index == lastIndex) 14.dp else 0.dp,
                ),
            ).background(colors.grouped),
        trailing = {
            if (extension is Extension.Available && extension.sources.isNotEmpty() && !busy) {
                IconButton("Open source website", onOpenWebView) {
                    Icon(TsuzukiIcons.Globe, null, Modifier.size(20.dp), tint = TsuzukiTheme.colors.secondary)
                }
            }
            PillButton(
                label = when {
                    busy -> "Cancel"
                    installStep == InstallStep.Error -> "Retry"
                    extension is Extension.Untrusted -> "Trust"
                    extension is Extension.Installed -> if (extension.hasUpdate) "Update" else "Open"
                    else -> "Get"
                },
                onClick = onTrailingClick,
                prominent = false,
            )
        },
    )
}
