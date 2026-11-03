package dev.errnolink.tsuzuki.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import eu.kanade.presentation.util.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.designsystem.ChevronRight
import dev.errnolink.tsuzuki.designsystem.Download
import dev.errnolink.tsuzuki.designsystem.IconButton
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.RefreshCw
import dev.errnolink.tsuzuki.designsystem.Shield
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.ui.shell.ShellContentScaffold
import dev.errnolink.tsuzuki.ui.shell.ShellTopAction
import eu.kanade.presentation.browse.components.ExtensionIcon
import eu.kanade.presentation.more.settings.screen.browse.ExtensionStoresScreen
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionFilterScreen
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionUiModel
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionsScreenModel
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

class AddSourcesScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { ExtensionsScreenModel() }
        val state by screenModel.state.collectAsState()

        var selectedLangChip by remember { mutableStateOf("All languages") }
        var nsfwOff by remember { mutableStateOf(!state.nsfwOnly) }
        var trustDialogExtension by remember { mutableStateOf<Extension.Untrusted?>(null) }
        val updateItems = remember(state.items) {
            state.items.entries.firstOrNull { it.key is ExtensionUiModel.Header.Resource && (it.key as ExtensionUiModel.Header.Resource).textRes == MR.strings.ext_updates_pending }
                ?.value.orEmpty()
        }

        val availableGroups = remember(state.items, selectedLangChip) {
            val code = when (selectedLangChip) {
                "English" -> "en"
                "Japanese" -> "ja"
                else -> null
            }
            state.items
                .filterKeys { it !is ExtensionUiModel.Header.Resource || (it as ExtensionUiModel.Header.Resource).textRes != MR.strings.ext_updates_pending }
                .mapValues { (_, items) ->
                    if (code == null) items else items.filter { it.extension.lang == code || it.extension.lang == "all" }
                }
                .filterValues { it.isNotEmpty() }
        }

        ShellContentScaffold(
            title = "Add sources",
            navigateUp = navigator::pop,
            actions = listOf(
                ShellTopAction("Filter", Icons.Outlined.FilterList, { navigator.push(ExtensionFilterScreen()) }),
            ),
        ) { paddingValues ->
            LazyColumn(
                flingBehavior = dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior(),
                modifier = Modifier.fillMaxSize(),
                contentPadding = paddingValues,
            ) {
                item(key = "search-field") {
                    val totalAvailable = availableGroups.values.sumOf { it.size }
                    BrowseSearchRow(
                        query = state.searchQuery.orEmpty(),
                        placeholder = if (totalAvailable > 0) "Search $totalAvailable sources" else "Search sources",
                        onQueryChange = screenModel::search,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                }

                item(key = "filter-chips") {
                    LazyRow(
                        flingBehavior = dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior(),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val langs = listOf("English", "Japanese", "All languages")
                        items(langs) { lang ->
                            val selected = selectedLangChip == lang
                            val colors = TsuzukiTheme.colors
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (selected) colors.text else colors.grouped)
                                    .clickable { selectedLangChip = lang }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                TsuzukiText(
                                    lang,
                                    style = TsuzukiTheme.typography.subhead.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (selected) colors.background else colors.secondary,
                                )
                            }
                        }

                        item(key = "nsfw-chip") {
                            val colors = TsuzukiTheme.colors
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (nsfwOff) colors.grouped else colors.text)
                                    .clickable {
                                        nsfwOff = !nsfwOff
                                        screenModel.toggleNsfwOnly()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                TsuzukiText(
                                    if (nsfwOff) "NSFW off" else "NSFW only",
                                    style = TsuzukiTheme.typography.subhead.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (nsfwOff) colors.secondary else colors.background,
                                )
                            }
                        }
                    }
                }

                if (updateItems.isNotEmpty()) {
                    item(key = "updates-header") {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            TsuzukiText(
                                "UPDATES · ${updateItems.size}",
                                style = TsuzukiTheme.typography.footnote.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                                color = TsuzukiTheme.colors.secondary,
                            )
                            TsuzukiText(
                                "Update all",
                                style = TsuzukiTheme.typography.subhead.copy(fontWeight = FontWeight.SemiBold),
                                color = TsuzukiTheme.colors.accent,
                                modifier = Modifier.clickable { screenModel.updateAllExtensions() },
                            )
                        }
                    }
                    item(key = "updates-group") {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(16.dp)).background(TsuzukiTheme.colors.grouped),
                        ) {
                            updateItems.forEachIndexed { idx, item ->
                                val ext = item.extension as? Extension.Installed
                                ExtensionActionRow(
                                    extension = item.extension,
                                    buttonLabel = "Update",
                                    isActionBlue = true,
                                    onClickAction = {
                                        if (ext != null) screenModel.updateExtension(ext)
                                    },
                                )
                                if (idx < updateItems.lastIndex) {
                                    Spacer(Modifier.fillMaxWidth().padding(start = 56.dp).height(0.5.dp).background(TsuzukiTheme.colors.separator))
                                }
                            }
                        }
                    }
                }

                availableGroups.forEach { (header, items) ->
                    item(key = "header-${header.hashCode()}") {
                        val headerText = when (header) {
                            is ExtensionUiModel.Header.Resource -> stringResource(header.textRes)
                            is ExtensionUiModel.Header.Text -> header.text
                        }
                        TsuzukiText(
                            headerText.uppercase(),
                            style = TsuzukiTheme.typography.footnote.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                            color = TsuzukiTheme.colors.secondary,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
                        )
                    }
                    item(key = "group-${header.hashCode()}") {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(16.dp)).background(TsuzukiTheme.colors.grouped),
                        ) {
                            items.forEachIndexed { idx, item ->
                                val ext = item.extension
                                val isInstalled = ext is Extension.Installed
                                val isUntrusted = ext is Extension.Untrusted
                                val canInstall = ext is Extension.Available
                                val buttonLabel = when {
                                    isUntrusted -> "Trust"
                                    isInstalled -> "Added"
                                    else -> "Add"
                                }
                                ExtensionActionRow(
                                    extension = ext,
                                    buttonLabel = buttonLabel,
                                    isActionBlue = isUntrusted,
                                    onClickAction = {
                                        when {
                                            isUntrusted -> trustDialogExtension = ext as Extension.Untrusted
                                            canInstall -> screenModel.installExtension(ext as Extension.Available)
                                        }
                                    },
                                )
                                if (idx < items.lastIndex) {
                                    Spacer(Modifier.fillMaxWidth().padding(start = 56.dp).height(0.5.dp).background(TsuzukiTheme.colors.separator))
                                }
                            }
                        }
                    }
                }

                item(key = "repos-row") {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(TsuzukiTheme.colors.grouped)
                            .clickable { navigator.push(ExtensionStoresScreen()) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(TsuzukiTheme.colors.separator),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(TsuzukiIcons.Shield, null, Modifier.size(18.dp), tint = TsuzukiTheme.colors.text)
                        }
                        Column(Modifier.weight(1f)) {
                            TsuzukiText(
                                "Repositories",
                                style = TsuzukiTheme.typography.callout.copy(fontWeight = FontWeight.SemiBold),
                            )
                            TsuzukiText(
                                "Manage repositories & trust",
                                style = TsuzukiTheme.typography.footnote,
                                color = TsuzukiTheme.colors.secondary,
                            )
                        }
                        Icon(TsuzukiIcons.ChevronRight, null, Modifier.size(16.dp), tint = TsuzukiTheme.colors.secondary)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
            trustDialogExtension?.let { ext ->
                dev.errnolink.tsuzuki.designsystem.DetentSheet(
                    visible = true,
                    title = stringResource(MR.strings.untrusted_extension),
                    onDismissRequest = { trustDialogExtension = null },
                ) {
                    dev.errnolink.tsuzuki.designsystem.TsuzukiText(
                        stringResource(MR.strings.untrusted_extension_message),
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                    dev.errnolink.tsuzuki.designsystem.PillButton(
                        stringResource(MR.strings.ext_trust),
                        onClick = {
                            screenModel.trustExtension(ext)
                            trustDialogExtension = null
                        },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    )
                    dev.errnolink.tsuzuki.designsystem.PillButton(
                        stringResource(MR.strings.ext_uninstall),
                        onClick = {
                            screenModel.uninstallExtension(ext)
                            trustDialogExtension = null
                        },
                        destructive = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowseSearchRow(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    dev.errnolink.tsuzuki.designsystem.SearchField(
        value = query,
        onValueChange = onQueryChange,
        onSearch = {},
        placeholder = placeholder,
        modifier = modifier,
    )
}

@Composable
private fun ExtensionActionRow(
    extension: Extension,
    buttonLabel: String,
    isActionBlue: Boolean,
    onClickAction: () -> Unit,
) {
    val colors = TsuzukiTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ExtensionIcon(extension = extension, modifier = Modifier.size(34.dp))
        Column(Modifier.weight(1f)) {
            TsuzukiText(
                extension.name,
                style = TsuzukiTheme.typography.callout.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = buildString {
                append(extension.versionName)
                if (!extension.lang.isNullOrEmpty()) append(" · ").append(extension.lang)
            }
            TsuzukiText(
                sub,
                style = TsuzukiTheme.typography.footnote,
                color = colors.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(if (isActionBlue) colors.accent else colors.separator)
                .clickable(onClick = onClickAction)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (isActionBlue) {
                    Icon(TsuzukiIcons.RefreshCw, null, Modifier.size(12.dp), tint = Color.White)
                } else if (buttonLabel == "Add") {
                    Icon(TsuzukiIcons.Download, null, Modifier.size(12.dp), tint = colors.text)
                }
                TsuzukiText(
                    buttonLabel,
                    style = TsuzukiTheme.typography.caption1.copy(fontWeight = FontWeight.Bold),
                    color = if (isActionBlue) Color.White else colors.text,
                )
            }
        }
    }
}
