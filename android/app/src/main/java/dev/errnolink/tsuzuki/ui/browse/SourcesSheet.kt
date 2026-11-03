package dev.errnolink.tsuzuki.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.Pin
import dev.errnolink.tsuzuki.designsystem.SheetDetent
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import eu.kanade.domain.source.model.installedExtension
import eu.kanade.presentation.browse.components.SourceIcon
import eu.kanade.tachiyomi.ui.browse.extension.details.ExtensionDetailsScreen
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.domain.source.model.Source as DomainSource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.source.local.isLocal
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
@Composable
fun SourcesSheet(
    visible: Boolean,
    sources: List<BrowseSourceItem>,
    selectedSourceId: Long?,
    onDismiss: () -> Unit,
    onSelectSource: (DomainSource) -> Unit,
    onTogglePin: (DomainSource) -> Unit,
    onToggleDisable: (DomainSource) -> Unit,
    onUpdateExtension: (eu.kanade.tachiyomi.extension.model.Extension.Installed) -> Unit,
    onUninstallExtension: (eu.kanade.tachiyomi.extension.model.Extension.Installed) -> Unit,
    onAddSources: () -> Unit,
) {
    if (!visible) return
    val colors = TsuzukiTheme.colors
    val context = LocalContext.current
    val navigator = LocalNavigator.currentOrThrow

    val uniqueSources = remember(sources) { sources.distinctBy { it.source.id } }
    val pinned = remember(uniqueSources) { uniqueSources.filter { it.isPinned } }
    val other = remember(uniqueSources) { uniqueSources.filter { !it.isPinned } }

    var longPressedSource by remember { mutableStateOf<BrowseSourceItem?>(null) }

    DetentSheet(
        visible = visible,
        title = "Sources",
        onDismissRequest = onDismiss,
        initialDetent = SheetDetent.Large,
    ) {
        if (pinned.isNotEmpty()) {
            TsuzukiText(
                "PINNED",
                style = TsuzukiTheme.typography.footnote.copy(fontWeight = FontWeight.Bold),
                color = TsuzukiTheme.colors.secondary,
                modifier = Modifier.padding(start = 4.dp, top = 0.dp, bottom = 4.dp),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(TsuzukiTheme.colors.grouped),
            ) {
                pinned.forEachIndexed { index, item ->
                    val isSelected = item.source.id == selectedSourceId
                    SourceSheetRow(
                        item = item,
                        isPinnedSection = true,
                        isSelected = isSelected,
                        onClick = { onSelectSource(item.source) },
                        onLongClick = { longPressedSource = item },
                        onTogglePin = { onTogglePin(item.source) },
                    )
                    if (index < pinned.lastIndex) {
                        Spacer(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 56.dp)
                                .height(0.5.dp)
                                .background(TsuzukiTheme.colors.separator),
                        )
                    }
                }
            }
        }

        if (other.isNotEmpty()) {
            TsuzukiText(
                "OTHER INSTALLED",
                style = TsuzukiTheme.typography.footnote.copy(fontWeight = FontWeight.Bold),
                color = TsuzukiTheme.colors.secondary,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(TsuzukiTheme.colors.grouped),
            ) {
                other.forEachIndexed { index, item ->
                    SourceSheetRow(
                        item = item,
                        isPinnedSection = false,
                        isSelected = false,
                        onClick = { onSelectSource(item.source) },
                        onLongClick = { longPressedSource = item },
                        onTogglePin = { onTogglePin(item.source) },
                    )
                    if (index < other.lastIndex) {
                        Spacer(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 56.dp)
                                .height(0.5.dp)
                                .background(TsuzukiTheme.colors.separator),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        PillButton(
            label = "Add sources",
            onClick = onAddSources,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    longPressedSource?.let { item ->
        val src = item.source
        DetentSheet(
            visible = true,
            title = src.visualName,
            onDismissRequest = { longPressedSource = null },
        ) {
            val ext = src.installedExtension
            if (ext != null && !src.isLocal()) {
                GroupedRow(
                    "Settings",
                    onClick = {
                        longPressedSource = null
                        onDismiss()
                        navigator.push(ExtensionDetailsScreen(ext.pkgName))
                    },
                )
            }
            if (item.hasUpdate && ext != null) {
                GroupedRow(
                    "Update",
                    onClick = {
                        onUpdateExtension(ext)
                        longPressedSource = null
                    },
                )
            }
            GroupedRow(
                if (item.isPinned) "Unpin source" else "Pin source",
                onClick = {
                    onTogglePin(src)
                    longPressedSource = null
                },
            )
            if (!src.isLocal()) {
                GroupedRow(
                    "Disable",
                    onClick = {
                        onToggleDisable(src)
                        longPressedSource = null
                    },
                )
                if (ext != null) {
                    GroupedRow(
                        "Uninstall",
                        destructive = true,
                        divider = false,
                        onClick = {
                            onUninstallExtension(ext)
                            longPressedSource = null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceSheetRow(
    item: BrowseSourceItem,
    isPinnedSection: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTogglePin: () -> Unit,
) {
    val colors = TsuzukiTheme.colors
    val context = LocalContext.current
    val src = item.source

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClickLabel = stringResource(MR.strings.tsuzuki_source_options),
                onLongClick = onLongClick,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SourceIcon(source = src, modifier = Modifier.size(32.dp))
        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TsuzukiText(
                    src.visualName,
                    style = TsuzukiTheme.typography.callout.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.hasUpdate) {
                    BrowseUpdateTag()
                }
            }
            val desc = buildString {
                append(LocaleHelper.getSourceDisplayName(src.lang, context))
                if (item.hasUpdate) {
                    append(" · update available")
                }
            }
            TsuzukiText(
                desc,
                style = TsuzukiTheme.typography.footnote,
                color = colors.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (isPinnedSection) {
            val selectedLabel = stringResource(MR.strings.tsuzuki_selected)
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .semantics {
                        role = Role.RadioButton
                        if (isSelected) stateDescription = selectedLabel
                    }
                    .then(
                        if (isSelected) {
                            Modifier.border(6.dp, colors.accent, CircleShape)
                        } else {
                            Modifier.border(2.dp, colors.separator, CircleShape)
                        },
                    ),
            )
        } else {
            Box(
                modifier = Modifier.size(48.dp).clickable(onClick = onTogglePin),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape),
                ) {
                    Icon(
                        TsuzukiIcons.Pin,
                        "Pin",
                        Modifier.size(18.dp).align(Alignment.Center),
                        tint = colors.secondary.copy(alpha = 0.4f),
                    )
                }
            }
        }
    }
}
