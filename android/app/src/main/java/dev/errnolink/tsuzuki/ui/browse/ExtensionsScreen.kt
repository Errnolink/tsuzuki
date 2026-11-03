package dev.errnolink.tsuzuki.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.IconButton
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.ProgressRing
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import eu.kanade.presentation.browse.components.ExtensionIcon
import dev.errnolink.tsuzuki.ui.shell.ShellEmpty
import dev.errnolink.tsuzuki.ui.shell.ShellLoading
import dev.errnolink.tsuzuki.ui.shell.ShellRefresh
import dev.errnolink.tsuzuki.ui.shell.WarningBanner
import eu.kanade.presentation.more.settings.screen.browse.ExtensionStoresScreen
import eu.kanade.presentation.util.rememberRequestPackageInstallsPermissionState
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionUiModel
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionsScreenModel
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.launchRequestPackageInstallsPermission
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ExtensionScreen(
    state: ExtensionsScreenModel.State,
    contentPadding: PaddingValues,
    searchQuery: String?,
    onLongClickItem: (Extension) -> Unit,
    onClickItemCancel: (Extension) -> Unit,
    onOpenWebView: (Extension.Available) -> Unit,
    onInstallExtension: (Extension.Available) -> Unit,
    onUninstallExtension: (Extension) -> Unit,
    onUpdateExtension: (Extension.Installed) -> Unit,
    onTrustExtension: (Extension.Untrusted) -> Unit,
    onOpenExtension: (Extension.Installed) -> Unit,
    onClickUpdateAll: () -> Unit,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    val navigator = LocalNavigator.currentOrThrow
    var trust by remember { mutableStateOf<Extension.Untrusted?>(null) }
    val installGranted = rememberRequestPackageInstallsPermissionState(initialValue = true)
    val activate: (Extension) -> Unit = {
        when (it) {
            is Extension.Available -> onInstallExtension(it)
            is Extension.Installed -> if (it.hasUpdate) onUpdateExtension(it) else onOpenExtension(it)
            is Extension.Untrusted -> trust = it
        }
    }
    ShellRefresh(state.isRefreshing, !state.isLoading, onRefresh) {
        LazyColumn(flingBehavior = dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior(), contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(0.dp)) {
            if (!installGranted && state.installer?.requiresSystemPermission == true) {
                item("extension-install-permission") {
                    WarningBanner(MR.strings.ext_permission_install_apps_warning, Modifier.clickable {
                        context.launchRequestPackageInstallsPermission()
                    })
                }
            }
            when {
                state.isLoading -> item("extension-loading") { ShellLoading("Loading extensions…") }
                state.isEmpty -> item("extension-empty") {
                    ShellEmpty(
                        if (searchQuery.isNullOrBlank()) "No extensions yet" else "No matching extensions",
                        "Add an extension repository to discover more catalogs.",
                    ) {
                        PillButton(stringResource(MR.strings.extensionStores), { navigator.push(ExtensionStoresScreen()) }, prominent = false)
                    }
                }
                else -> state.items.forEach { (header, entries) ->
                    item("extension-header-${header.hashCode()}", contentType = "header") {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 36.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TsuzukiText(
                                when (header) {
                                    is ExtensionUiModel.Header.Resource -> stringResource(header.textRes)
                                    is ExtensionUiModel.Header.Text -> header.text
                                },
                                Modifier.weight(1f),
                                TsuzukiTheme.typography.footnote,
                                TsuzukiTheme.colors.secondary,
                            )
                            if (header is ExtensionUiModel.Header.Resource) {
                                when (header.textRes) {
                                    MR.strings.ext_updates_pending -> PillButton("Update all", onClickUpdateAll, prominent = false)
                                    KMR.strings.extensions_page_more -> PillButton("Repositories", { navigator.push(ExtensionStoresScreen()) }, prominent = false)
                                }
                            }
                        }
                    }
                    itemsIndexed(entries, key = { _, it -> "${header.hashCode()}:${it.extension.pkgName}" }, contentType = { _, _ -> "extension" }) { index, entry ->
                        val extension = entry.extension
                        val busy = !entry.installStep.isCompleted()
                        val colors = TsuzukiTheme.colors
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                                .clip(RoundedCornerShape(
                                    topStart = if (index == 0) 14.dp else 0.dp, topEnd = if (index == 0) 14.dp else 0.dp,
                                    bottomStart = if (index == entries.lastIndex) 14.dp else 0.dp,
                                    bottomEnd = if (index == entries.lastIndex) 14.dp else 0.dp,
                                )).background(colors.grouped)
                                .combinedClickable(
                                    onClick = { if (extension is Extension.Installed) onOpenExtension(extension) else activate(extension) },
                                    onLongClick = { onLongClickItem(extension) },
                                ).drawBehind {
                                    if (index < entries.lastIndex) drawLine(colors.separator, Offset(56.dp.toPx(), size.height), Offset(size.width, size.height), 0.5.dp.toPx())
                                }.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (busy) ProgressRing(0f, extension.name)
                            else ExtensionIcon(extension, Modifier.size(32.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                TsuzukiText(extension.name, style = TsuzukiTheme.typography.callout, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                TsuzukiText(
                                    listOfNotNull(
                                        extension.versionName,
                                        extension.lang?.let { LocaleHelper.getSourceDisplayName(it, context) },
                                        extension.storeName,
                                        if (extension is Extension.Installed && extension.isObsolete) "Obsolete" else null,
                                        if (extension.isNsfw) stringResource(MR.strings.ext_nsfw_short) else null,
                                    ).joinToString(" · "),
                                    style = TsuzukiTheme.typography.caption1,
                                    color = TsuzukiTheme.colors.secondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (extension is Extension.Available && extension.sources.isNotEmpty() && !busy) {
                                IconButton("Open source website", { onOpenWebView(extension) }) {
                                    Icon(TsuzukiIcons.Globe, null, Modifier.size(20.dp), tint = TsuzukiTheme.colors.secondary)
                                }
                            }
                            PillButton(
                                label = when {
                                    busy -> "Cancel"
                                    entry.installStep == InstallStep.Error -> "Retry"
                                    extension is Extension.Untrusted -> "Trust"
                                    extension is Extension.Installed -> if (extension.hasUpdate) "Update" else "Open"
                                    else -> "Get"
                                },
                                onClick = { if (busy) onClickItemCancel(extension) else activate(extension) },
                                prominent = false,
                            )
                        }
                    }
                }
            }
        }
    }
    trust?.let { extension ->
        DetentSheet(true, stringResource(MR.strings.untrusted_extension), { trust = null }) {
            TsuzukiText(stringResource(MR.strings.untrusted_extension_message))
            PillButton(stringResource(MR.strings.ext_trust), { onTrustExtension(extension); trust = null })
            PillButton(stringResource(MR.strings.ext_uninstall), { onUninstallExtension(extension); trust = null }, destructive = true)
        }
    }
}
