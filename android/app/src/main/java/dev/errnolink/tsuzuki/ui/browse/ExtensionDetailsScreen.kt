package dev.errnolink.tsuzuki.ui.browse

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.DisplayMetrics
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.Ellipsis
import dev.errnolink.tsuzuki.designsystem.EyeOff
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.IconButton
import dev.errnolink.tsuzuki.designsystem.InsetGroupedList
import dev.errnolink.tsuzuki.designsystem.Link
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.PullDownAction
import dev.errnolink.tsuzuki.designsystem.SettingsLucide
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.TsuzukiToggle
import dev.errnolink.tsuzuki.ui.shell.ShellEmpty
import dev.errnolink.tsuzuki.ui.shell.ShellTopAction
import dev.errnolink.tsuzuki.ui.shell.WarningBanner
import dev.errnolink.tsuzuki.ui.settings.SettingsScaffold
import eu.kanade.domain.extension.interactor.ExtensionSourceItem
import eu.kanade.presentation.browse.components.ExtensionIcon
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.ui.browse.extension.details.ExtensionDetailsScreenModel
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.copyToClipboard
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ExtensionDetailsContent(
    navigateUp: () -> Unit,
    state: ExtensionDetailsScreenModel.State,
    onClickSourcePreferences: (sourceId: Long) -> Unit,
    onOpenWebView: (() -> Unit)?,
    onClickEnableAll: () -> Unit,
    onClickDisableAll: () -> Unit,
    onClickClearCookies: () -> Unit,
    onClickUninstall: () -> Unit,
    onClickSource: (sourceId: Long) -> Unit,
    onClickIncognito: (Boolean) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val url = remember(state.extension) {
        val regex = """https://raw.githubusercontent.com/(.+?)/(.+?)/.+""".toRegex()
        regex.find(state.extension?.store?.indexUrl.orEmpty())
            ?.let {
                val (user, repo) = it.destructured
                "https://github.com/$user/$repo"
            }
            ?: state.extension?.store?.indexUrl
    }

    SettingsScaffold(
        title = stringResource(MR.strings.label_extension_info),
        navigateUp = navigateUp,
        actions = buildList {
            if (onOpenWebView != null) {
                add(ShellTopAction(stringResource(MR.strings.action_open_in_web_view), TsuzukiIcons.Globe, onOpenWebView))
            }
            if (url != null) {
                add(
                    ShellTopAction(stringResource(MR.strings.action_open_repo), TsuzukiIcons.Link, {
                        uriHandler.openUri(url)
                    }),
                )
            }
            add(
                ShellTopAction(
                    "More actions",
                    TsuzukiIcons.Ellipsis,
                    {},
                    menuTitle = stringResource(MR.strings.label_extension_info),
                    menu = listOf(
                        PullDownAction(stringResource(MR.strings.action_enable_all), onClick = onClickEnableAll),
                        PullDownAction(stringResource(MR.strings.action_disable_all), onClick = onClickDisableAll),
                        PullDownAction(stringResource(MR.strings.pref_clear_cookies), onClick = onClickClearCookies),
                    ),
                ),
            )
        },
    ) {
        val extension = state.extension
        if (extension == null) {
            item(key = "extension-details-empty") {
                ShellEmpty("Extension unavailable", "This extension is no longer installed.")
            }
            return@SettingsScaffold
        }

        if (extension.isRedundant) {
            item(key = "extension-details-redundant") {
                WarningBanner(SYMR.strings.redundant_extension_message)
            }
        }
        if (extension.isObsolete) {
            item(key = "extension-details-obsolete") {
                WarningBanner(MR.strings.obsolete_extension_message)
            }
        }

        item(key = "extension-details-header") {
            DetailsHeader(
                extension = extension,
                extIncognitoMode = state.isIncognito,
                onClickUninstall = onClickUninstall,
                onExtIncognitoChange = onClickIncognito,
            )
        }

        items(
            items = state.sources,
            key = { "extension-details-${it.source.id}" },
        ) { source ->
            SourceRow(
                modifier = Modifier.animateItem(),
                source = source,
                onClickSourcePreferences = onClickSourcePreferences,
                onClickSource = onClickSource,
            )
        }
    }
}

@Composable
private fun DetailsHeader(
    extension: Extension.Installed,
    extIncognitoMode: Boolean,
    onClickUninstall: () -> Unit,
    onExtIncognitoChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var showNsfwWarning by remember { mutableStateOf(false) }
    val colors = TsuzukiTheme.colors

    Column(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    val extDebugInfo = buildString {
                        append(
                            """
                            Extension name: ${extension.name} (lang: ${extension.lang}; package: ${extension.pkgName})
                            Extension version: ${extension.versionName} (lib: ${extension.libVersion}; version code: ${extension.versionCode})
                            NSFW: ${extension.isNsfw}
                            """.trimIndent(),
                        )

                        append("\n\n")
                        appendLine(
                            """
                            Update available: ${extension.hasUpdate}
                            Orphaned: ${extension.isObsolete}
                            Shared: ${extension.isShared}
                            """.trimIndent(),
                        )
                        val store = extension.store
                        if (store != null) {
                            append("Repository: ${store.indexUrl}")
                        }
                    }
                    context.copyToClipboard("Extension Debug information", extDebugInfo)
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ExtensionIcon(
                modifier = Modifier.size(96.dp),
                extension = extension,
                density = DisplayMetrics.DENSITY_XXXHIGH,
            )

            TsuzukiText(
                extension.name,
                Modifier.fillMaxWidth(),
                TsuzukiTheme.typography.title2.copy(textAlign = TextAlign.Center),
            )
            TsuzukiText(
                extension.pkgName.substringAfter("eu.kanade.tachiyomi.extension."),
                style = TsuzukiTheme.typography.caption1,
                color = colors.secondary,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderInfo(
                modifier = Modifier.weight(1f),
                primary = extension.versionName,
                secondary = stringResource(MR.strings.ext_info_version),
            )
            HeaderInfo(
                modifier = Modifier.weight(1f),
                primary = LocaleHelper.getSourceDisplayName(extension.lang, context),
                secondary = stringResource(MR.strings.ext_info_language),
            )
            if (extension.isNsfw) {
                HeaderInfo(
                    modifier = Modifier.weight(1f),
                    primary = stringResource(MR.strings.ext_nsfw_short),
                    primaryColor = colors.destructive,
                    secondary = stringResource(MR.strings.ext_info_age_rating),
                    onClick = { showNsfwWarning = true },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PillButton(stringResource(MR.strings.ext_uninstall), onClickUninstall, modifier = Modifier.weight(1f), destructive = true)
            if (extension.isShared) {
                PillButton(
                    stringResource(MR.strings.ext_app_info),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", extension.pkgName, null)
                            context.startActivity(this)
                        }
                        Unit
                    },
                )
            }
        }

        InsetGroupedList {
            GroupedRow(
                title = stringResource(MR.strings.pref_incognito_mode),
                subtitle = stringResource(MR.strings.pref_incognito_mode_extension_summary),
                leading = {
                    Icon(TsuzukiIcons.EyeOff, null, Modifier.size(22.dp), tint = TsuzukiTheme.colors.secondary)
                },
                checked = extIncognitoMode,
                onCheckedChange = onExtIncognitoChange,
            )
        }
    }

    if (showNsfwWarning) {
        DetentSheet(true, stringResource(MR.strings.ext_info_age_rating), { showNsfwWarning = false }) {
            TsuzukiText(stringResource(MR.strings.ext_nsfw_warning))
            PillButton(stringResource(MR.strings.action_ok), { showNsfwWarning = false })
        }
    }
}

@Composable
private fun HeaderInfo(
    primary: String,
    secondary: String,
    modifier: Modifier = Modifier,
    primaryColor: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val clickable = if (onClick != null) {
        Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    } else {
        Modifier
    }
    Column(
        modifier = modifier.then(clickable),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TsuzukiText(
            primary,
            style = TsuzukiTheme.typography.callout.copy(textAlign = TextAlign.Center),
            color = primaryColor ?: TsuzukiTheme.colors.text,
        )
        TsuzukiText(
            secondary,
            style = TsuzukiTheme.typography.caption1.copy(textAlign = TextAlign.Center),
            color = TsuzukiTheme.colors.secondary,
        )
    }
}

@Composable
private fun SourceRow(
    source: ExtensionSourceItem,
    onClickSourcePreferences: (sourceId: Long) -> Unit,
    onClickSource: (sourceId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    InsetGroupedList(modifier = modifier) {
        GroupedRow(
                title = if (source.labelAsName) {
                    source.source.toString()
                } else {
                    LocaleHelper.getSourceDisplayName(source.source.lang, context)
                },
                onClick = { onClickSource(source.source.id) },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (source.source is ConfigurableSource) {
                            IconButton(stringResource(MR.strings.label_settings), { onClickSourcePreferences(source.source.id) }) {
                                Icon(TsuzukiIcons.SettingsLucide, null, Modifier.size(20.dp), tint = TsuzukiTheme.colors.secondary)
                            }
                        }
                        TsuzukiToggle(source.enabled)
                    }
                },
            )
    }
}
