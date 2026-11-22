package dev.errnolink.tsuzuki.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.SheetKind
import dev.errnolink.tsuzuki.designsystem.IconButton
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.ui.shell.ShellEmpty
import dev.errnolink.tsuzuki.ui.shell.ShellLoading
import dev.errnolink.tsuzuki.ui.shell.ShellRefresh
import dev.errnolink.tsuzuki.ui.shell.WarningBanner
import eu.kanade.presentation.more.settings.screen.browse.ExtensionStoresScreen
import eu.kanade.presentation.util.rememberRequestPackageInstallsPermissionState
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionUiModel
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionsScreenModel
import eu.kanade.tachiyomi.util.system.launchRequestPackageInstallsPermission
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import dev.errnolink.tsuzuki.designsystem.SectionHeader

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
                        SectionHeader(
                            when (header) {
                                is ExtensionUiModel.Header.Resource -> stringResource(header.textRes)
                                is ExtensionUiModel.Header.Text -> header.text
                            },
                            Modifier.fillMaxWidth().padding(start = 36.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
                            trailing = {
                                if (header is ExtensionUiModel.Header.Resource) {
                                    when (header.textRes) {
                                        MR.strings.ext_updates_pending -> PillButton("Update all", onClickUpdateAll, prominent = false)
                                        KMR.strings.extensions_page_more -> PillButton("Repositories", { navigator.push(ExtensionStoresScreen()) }, prominent = false)
                                    }
                                }
                            },
                        )
                    }
                    itemsIndexed(entries, key = { _, it -> "${header.hashCode()}:${it.extension.pkgName}" }, contentType = { _, _ -> "extension" }) { index, entry ->
                        val extension = entry.extension
                        val busy = !entry.installStep.isCompleted()
                        CurrentExtensionRow(
                            extension = extension,
                            busy = busy,
                            installStep = entry.installStep,
                            index = index,
                            lastIndex = entries.lastIndex,
                            onClick = { if (extension is Extension.Installed) onOpenExtension(extension) else activate(extension) },
                            onLongClick = { onLongClickItem(extension) },
                            onOpenWebView = { if (extension is Extension.Available) onOpenWebView(extension) },
                            onTrailingClick = { if (busy) onClickItemCancel(extension) else activate(extension) },
                        )
                    }
                }
            }
        }
    }
    trust?.let { extension ->
        DetentSheet(true, stringResource(MR.strings.untrusted_extension), { trust = null }, kind = SheetKind.Confirm) {
            TsuzukiText(stringResource(MR.strings.untrusted_extension_message))
            PillButton(stringResource(MR.strings.ext_trust), { onTrustExtension(extension); trust = null })
            PillButton(stringResource(MR.strings.ext_uninstall), { onUninstallExtension(extension); trust = null }, destructive = true)
        }
    }
}
