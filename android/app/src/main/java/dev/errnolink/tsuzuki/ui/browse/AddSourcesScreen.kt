package dev.errnolink.tsuzuki.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.designsystem.ChevronRight
import dev.errnolink.tsuzuki.designsystem.Chip
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.Download
import dev.errnolink.tsuzuki.designsystem.ListFilter
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.ProgressMark
import dev.errnolink.tsuzuki.designsystem.RefreshCw
import dev.errnolink.tsuzuki.designsystem.Search
import dev.errnolink.tsuzuki.designsystem.SearchField
import dev.errnolink.tsuzuki.designsystem.SectionHeader
import dev.errnolink.tsuzuki.designsystem.Shield
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.X
import dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior
import dev.errnolink.tsuzuki.ui.shell.ShellContentScaffold
import dev.errnolink.tsuzuki.ui.shell.ShellEmpty
import dev.errnolink.tsuzuki.ui.shell.ShellLoading
import dev.errnolink.tsuzuki.ui.shell.ShellRefresh
import dev.errnolink.tsuzuki.ui.shell.ShellTopAction
import dev.errnolink.tsuzuki.ui.shell.WarningBanner
import eu.kanade.presentation.browse.components.ExtensionIcon
import eu.kanade.presentation.more.settings.screen.browse.ExtensionStoresScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.rememberRequestPackageInstallsPermissionState
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionFilterScreen
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionUiModel
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionsScreenModel
import eu.kanade.tachiyomi.ui.browse.extension.ItemGroups
import eu.kanade.tachiyomi.ui.browse.extension.details.ExtensionDetailsScreen
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.launchRequestPackageInstallsPermission
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

internal enum class AddSourcesRowActionKind {
    CANCEL,
    RETRY,
    TRUST,
    UPDATE,
    INSTALLED,
    ADD,
}

internal enum class AddSourcesEmptyKind {
    NONE,
    NO_REPOS,
    NO_MATCHES,
}

internal data class AddSourcesLanguageOption(
    val code: String?,
    val label: String,
)

internal fun resolveAddSourcesRowAction(
    extension: Extension,
    installStep: InstallStep,
): AddSourcesRowActionKind = when {
    !installStep.isCompleted() -> AddSourcesRowActionKind.CANCEL
    installStep == InstallStep.Error -> AddSourcesRowActionKind.RETRY
    extension is Extension.Untrusted -> AddSourcesRowActionKind.TRUST
    extension is Extension.Installed -> {
        if (extension.hasUpdate) AddSourcesRowActionKind.UPDATE else AddSourcesRowActionKind.INSTALLED
    }
    else -> AddSourcesRowActionKind.ADD
}

internal fun installStepStatusLabel(installStep: InstallStep): String? = when (installStep) {
    InstallStep.Pending -> "Pending…"
    InstallStep.Downloading -> "Downloading…"
    InstallStep.Installing -> "Installing…"
    InstallStep.Error -> "Failed"
    InstallStep.Idle, InstallStep.Installed -> null
}

internal fun buildAddSourcesLanguageOptions(
    items: ItemGroups,
    displayName: (String) -> String,
): List<AddSourcesLanguageOption> {
    val options = mutableListOf(AddSourcesLanguageOption(null, "All languages"))
    val seenCodes = LinkedHashSet<String>()
    items.values.asSequence()
        .flatten()
        .mapNotNull { it.extension.lang }
        .filter { it.isNotBlank() && it != "all" }
        .forEach { code ->
            if (seenCodes.add(code)) {
                options.add(AddSourcesLanguageOption(code, displayName(code)))
            }
        }
    return options
}

internal fun filterAddSourcesGroups(
    items: ItemGroups,
    selectedLangCode: String?,
): ItemGroups = items
    .filterKeys { header ->
        header !is ExtensionUiModel.Header.Resource || header.textRes != MR.strings.ext_updates_pending
    }
    .mapValues { (_, groupItems) ->
        if (selectedLangCode == null) {
            groupItems
        } else {
            groupItems.filter { it.extension.lang == selectedLangCode || it.extension.lang == "all" }
        }
    }
    .filterValues { it.isNotEmpty() }

internal fun resolveAddSourcesEmptyKind(
    isLoading: Boolean,
    updateCount: Int,
    availableGroupCount: Int,
    searchQuery: String?,
    nsfwOnly: Boolean,
    selectedLangCode: String?,
): AddSourcesEmptyKind {
    if (isLoading || updateCount > 0 || availableGroupCount > 0) {
        return AddSourcesEmptyKind.NONE
    }
    if (!searchQuery.isNullOrBlank() || nsfwOnly || selectedLangCode != null) {
        return AddSourcesEmptyKind.NO_MATCHES
    }
    return AddSourcesEmptyKind.NO_REPOS
}

class AddSourcesScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val focusManager = LocalFocusManager.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { ExtensionsScreenModel() }
        val state by screenModel.state.collectAsState()
        val installGranted = rememberRequestPackageInstallsPermissionState(initialValue = true)

        var selectedLangCode by remember { mutableStateOf<String?>(null) }
        var trustDialogExtension by remember { mutableStateOf<Extension.Untrusted?>(null) }

        val languageOptions = remember(state.items, context) {
            buildAddSourcesLanguageOptions(state.items) { code ->
                LocaleHelper.getSourceDisplayName(code, context)
            }
        }
        LaunchedEffect(languageOptions) {
            if (selectedLangCode != null && languageOptions.none { it.code == selectedLangCode }) {
                selectedLangCode = null
            }
        }

        val updateItems = remember(state.items) {
            state.items.entries.firstOrNull {
                it.key is ExtensionUiModel.Header.Resource &&
                    (it.key as ExtensionUiModel.Header.Resource).textRes == MR.strings.ext_updates_pending
            }?.value.orEmpty()
        }

        val availableGroups = remember(state.items, selectedLangCode) {
            filterAddSourcesGroups(state.items, selectedLangCode)
        }

        val hasNoAvailableHeader = remember(state.items) {
            state.items.keys.any {
                it is ExtensionUiModel.Header.Resource && it.textRes == KMR.strings.extensions_page_more
            }
        }

        val emptyKind = remember(
            state.isLoading,
            updateItems.size,
            availableGroups.size,
            state.searchQuery,
            state.nsfwOnly,
            selectedLangCode,
        ) {
            resolveAddSourcesEmptyKind(
                isLoading = state.isLoading,
                updateCount = updateItems.size,
                availableGroupCount = availableGroups.size,
                searchQuery = state.searchQuery,
                nsfwOnly = state.nsfwOnly,
                selectedLangCode = selectedLangCode,
            )
        }

        ShellContentScaffold(
            title = "Add sources",
            navigateUp = navigator::pop,
            actions = listOf(
                ShellTopAction("Filter", TsuzukiIcons.ListFilter, { navigator.push(ExtensionFilterScreen()) }),
            ),
        ) { paddingValues ->
            ShellRefresh(
                refreshing = state.isRefreshing,
                enabled = !state.isLoading,
                onRefresh = screenModel::findAvailableExtensions,
            ) {
                LazyColumn(
                    flingBehavior = tsuzukiFlingBehavior(),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = paddingValues,
                ) {
                    if (!installGranted && state.installer?.requiresSystemPermission == true) {
                        item(key = "extension-install-permission") {
                            WarningBanner(
                                MR.strings.ext_permission_install_apps_warning,
                                Modifier.clickable { context.launchRequestPackageInstallsPermission() },
                            )
                        }
                    }

                    item(key = "search-field") {
                        val totalAvailable = availableGroups.values.sumOf { it.size }
                        BrowseSearchRow(
                            query = state.searchQuery.orEmpty(),
                            placeholder = if (totalAvailable > 0) "Search $totalAvailable sources" else "Search sources",
                            onQueryChange = screenModel::search,
                            onSearch = {
                                screenModel.search(state.searchQuery.orEmpty())
                                focusManager.clearFocus()
                            },
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                        )
                    }

                    item(key = "filter-chips") {
                        LazyRow(
                            flingBehavior = tsuzukiFlingBehavior(),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(languageOptions, key = { it.code ?: "all-languages" }) { option ->
                                Chip(
                                    label = option.label,
                                    selected = selectedLangCode == option.code,
                                    onClick = { selectedLangCode = option.code },
                                    minHeight = 36.dp,
                                    horizontalPadding = 14.dp,
                                )
                            }

                            item(key = "nsfw-chip") {
                                Chip(
                                    label = "NSFW only",
                                    selected = state.nsfwOnly,
                                    role = Role.Checkbox,
                                    onClick = screenModel::toggleNsfwOnly,
                                    minHeight = 36.dp,
                                    horizontalPadding = 14.dp,
                                )
                            }
                        }
                    }

                    if (state.isLoading) {
                        item(key = "extensions-loading") {
                            ShellLoading("Loading extensions…")
                        }
                    } else if (emptyKind == AddSourcesEmptyKind.NO_REPOS) {
                        item(key = "extensions-empty-repos") {
                            ShellEmpty(
                                title = "No extension repositories",
                                description = "Add an extension repository to discover and install sources.",
                                icon = TsuzukiIcons.Shield,
                            ) {
                                PillButton(
                                    stringResource(MR.strings.extensionStores),
                                    { navigator.push(ExtensionStoresScreen()) },
                                )
                            }
                        }
                    } else if (emptyKind == AddSourcesEmptyKind.NO_MATCHES) {
                        item(key = "extensions-empty-search") {
                            ShellEmpty(
                                title = "No matching sources",
                                description = "Try another search or clear your filters.",
                                icon = TsuzukiIcons.Search,
                            ) {
                                PillButton(
                                    label = "Clear filters",
                                    onClick = {
                                        screenModel.search("")
                                        selectedLangCode = null
                                        if (state.nsfwOnly) {
                                            screenModel.toggleNsfwOnly()
                                        }
                                    },
                                    prominent = false,
                                )
                            }
                        }
                    } else {
                        if (updateItems.isNotEmpty()) {
                            item(key = "updates-header") {
                                SectionHeader(
                                    "UPDATES · ${updateItems.size}",
                                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                                    titleStyle = TsuzukiTheme.typography.footnote.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp,
                                    ),
                                    actionLabel = "Update all",
                                    onAction = screenModel::updateAllExtensions,
                                    actionStyle = TsuzukiTheme.typography.subhead.copy(fontWeight = FontWeight.SemiBold),
                                    actionMinHeight = null,
                                )
                            }
                            item(key = "updates-group") {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(TsuzukiTheme.colors.grouped),
                                ) {
                                    updateItems.forEachIndexed { idx, item ->
                                        val ext = item.extension as? Extension.Installed
                                        val actionKind = resolveAddSourcesRowAction(item.extension, item.installStep)
                                        ExtensionActionRow(
                                            extension = item.extension,
                                            installStep = item.installStep,
                                            actionKind = actionKind,
                                            onRowClick = {
                                                if (ext != null) {
                                                    navigator.push(ExtensionDetailsScreen(ext.pkgName))
                                                }
                                            },
                                            onActionClick = {
                                                if (ext != null) {
                                                    if (actionKind == AddSourcesRowActionKind.CANCEL) {
                                                        screenModel.cancelInstallUpdateExtension(ext)
                                                    } else {
                                                        screenModel.updateExtension(ext)
                                                    }
                                                }
                                            },
                                        )
                                        if (idx < updateItems.lastIndex) {
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
                        }

                        availableGroups.forEach { (header, items) ->
                            item(key = "header-${header.hashCode()}") {
                                val headerText = when (header) {
                                    is ExtensionUiModel.Header.Resource -> stringResource(header.textRes)
                                    is ExtensionUiModel.Header.Text -> header.text
                                }
                                SectionHeader(
                                    headerText.uppercase(),
                                    Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
                                    titleStyle = TsuzukiTheme.typography.footnote.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp,
                                    ),
                                )
                            }
                            item(key = "group-${header.hashCode()}") {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(TsuzukiTheme.colors.grouped),
                                ) {
                                    items.forEachIndexed { idx, item ->
                                        val ext = item.extension
                                        val actionKind = resolveAddSourcesRowAction(ext, item.installStep)
                                        ExtensionActionRow(
                                            extension = ext,
                                            installStep = item.installStep,
                                            actionKind = actionKind,
                                            onRowClick = {
                                                when (ext) {
                                                    is Extension.Installed -> navigator.push(ExtensionDetailsScreen(ext.pkgName))
                                                    is Extension.Untrusted -> trustDialogExtension = ext
                                                    is Extension.Available -> {
                                                        if (actionKind == AddSourcesRowActionKind.CANCEL) {
                                                            screenModel.cancelInstallUpdateExtension(ext)
                                                        } else {
                                                            screenModel.installExtension(ext)
                                                        }
                                                    }
                                                }
                                            },
                                            onActionClick = {
                                                when (ext) {
                                                    is Extension.Untrusted -> trustDialogExtension = ext
                                                    is Extension.Installed -> {
                                                        when (actionKind) {
                                                            AddSourcesRowActionKind.CANCEL -> screenModel.cancelInstallUpdateExtension(ext)
                                                            AddSourcesRowActionKind.UPDATE,
                                                            AddSourcesRowActionKind.RETRY,
                                                            -> screenModel.updateExtension(ext)
                                                            else -> navigator.push(ExtensionDetailsScreen(ext.pkgName))
                                                        }
                                                    }
                                                    is Extension.Available -> {
                                                        if (actionKind == AddSourcesRowActionKind.CANCEL) {
                                                            screenModel.cancelInstallUpdateExtension(ext)
                                                        } else {
                                                            screenModel.installExtension(ext)
                                                        }
                                                    }
                                                }
                                            },
                                        )
                                        if (idx < items.lastIndex) {
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
                        }
                    }

                    item(key = "repos-row") {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .clip(RoundedCornerShape(TsuzukiCorners.field))
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
                                    if (hasNoAvailableHeader && !state.isLoading && emptyKind == AddSourcesEmptyKind.NONE) {
                                        "No repository added · Add one to install more sources"
                                    } else {
                                        "Manage repositories & trust"
                                    },
                                    style = TsuzukiTheme.typography.footnote,
                                    color = TsuzukiTheme.colors.secondary,
                                )
                            }
                            Icon(TsuzukiIcons.ChevronRight, null, Modifier.size(16.dp), tint = TsuzukiTheme.colors.secondary)
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }

            trustDialogExtension?.let { ext ->
                DetentSheet(
                    visible = true,
                    title = stringResource(MR.strings.untrusted_extension),
                    onDismissRequest = { trustDialogExtension = null },
                ) {
                    TsuzukiText(
                        stringResource(MR.strings.untrusted_extension_message),
                        modifier = Modifier.padding(bottom = 16.dp),
                    )
                    PillButton(
                        stringResource(MR.strings.ext_trust),
                        onClick = {
                            screenModel.trustExtension(ext)
                            trustDialogExtension = null
                        },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    )
                    PillButton(
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
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SearchField(
        value = query,
        onValueChange = onQueryChange,
        onSearch = onSearch,
        placeholder = placeholder,
        modifier = modifier,
    )
}

@Composable
private fun ExtensionActionRow(
    extension: Extension,
    installStep: InstallStep,
    actionKind: AddSourcesRowActionKind,
    onRowClick: () -> Unit,
    onActionClick: () -> Unit,
) {
    val colors = TsuzukiTheme.colors
    val stepStatus = installStepStatusLabel(installStep)
    val sub = listOfNotNull(
        extension.versionName,
        extension.lang?.takeIf { it.isNotEmpty() },
        stepStatus,
    ).joinToString(" · ")

    val prominent = actionKind == AddSourcesRowActionKind.RETRY ||
        actionKind == AddSourcesRowActionKind.TRUST ||
        actionKind == AddSourcesRowActionKind.UPDATE
    val buttonLabel = when (actionKind) {
        AddSourcesRowActionKind.CANCEL -> "Cancel"
        AddSourcesRowActionKind.RETRY -> "Retry"
        AddSourcesRowActionKind.TRUST -> "Trust"
        AddSourcesRowActionKind.UPDATE -> "Update"
        AddSourcesRowActionKind.INSTALLED -> "Added"
        AddSourcesRowActionKind.ADD -> "Add"
    }
    val actionIcon = when (actionKind) {
        AddSourcesRowActionKind.CANCEL -> TsuzukiIcons.X
        AddSourcesRowActionKind.RETRY -> TsuzukiIcons.RefreshCw
        AddSourcesRowActionKind.TRUST -> TsuzukiIcons.Shield
        AddSourcesRowActionKind.UPDATE -> TsuzukiIcons.RefreshCw
        AddSourcesRowActionKind.INSTALLED -> TsuzukiIcons.ChevronRight
        AddSourcesRowActionKind.ADD -> TsuzukiIcons.Download
    }
    val contentTint = if (prominent) colors.onAccent else colors.text

    SourceRow(
        title = extension.name,
        onClick = onRowClick,
        titleSemibold = true,
        subtitle = sub,
        subtitleMaxLines = 1,
        leading = {
            if (actionKind == AddSourcesRowActionKind.CANCEL) {
                ProgressMark(0f, extension.name)
            } else {
                ExtensionIcon(extension = extension, modifier = Modifier.size(34.dp))
            }
        },
        trailing = {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (prominent) colors.accent else colors.separator)
                    .clickable(onClick = onActionClick)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(actionIcon, null, Modifier.size(12.dp), tint = contentTint)
                    TsuzukiText(
                        buttonLabel,
                        style = TsuzukiTheme.typography.caption1.copy(fontWeight = FontWeight.Bold),
                        color = contentTint,
                    )
                }
            }
        },
    )
}
