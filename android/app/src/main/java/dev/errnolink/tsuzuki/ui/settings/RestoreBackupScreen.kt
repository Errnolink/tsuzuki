package dev.errnolink.tsuzuki.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import dev.errnolink.tsuzuki.designsystem.PillButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.core.net.toUri
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import dev.errnolink.tsuzuki.ui.shell.WarningBanner
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.backup.BackupFileValidator
import eu.kanade.tachiyomi.data.backup.restore.BackupRestoreJob
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import eu.kanade.tachiyomi.util.system.DeviceUtil
import kotlinx.coroutines.flow.update
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import cafe.adriel.voyager.core.model.screenModelScope
import dev.errnolink.tsuzuki.mangadex.backup.NekoBackupMapper
import eu.kanade.presentation.more.settings.screen.browse.ExtensionStoresScreen
import eu.kanade.tachiyomi.data.backup.BackupDecoder
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TsuzukiRestoreBackupScreen(
    private val uri: String,
) : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { RestoreBackupScreenModel(context, uri) }
        val state by model.state.collectAsState()

        SettingsScaffold(
            title = stringResource(MR.strings.pref_restore_backup),
            navigateUp = navigator::pop,
            bottomBar = {
                PillButton(
                    stringResource(MR.strings.action_restore),
                    {
                        model.startRestore()
                        navigator.pop()
                    },
                    Modifier.fillMaxWidth(), enabled = state.canRestore && state.options.canRestore(), large = true,
                )
            },
        ) {
                    if (DeviceUtil.isMiui && DeviceUtil.isMiuiOptimizationDisabled()) {
                        item {
                            WarningBanner(MR.strings.restore_miui_warning)
                        }
                    }

                    if (state.extensions != RestoreExtensionsState.None) {
                        item("extensions") {
                            RestoreExtensionsSection(
                                state = state.extensions,
                                onInstall = model::install,
                                onInstallAll = model::installAll,
                                onOpenRepos = { navigator.push(ExtensionStoresScreen()) },
                            )
                        }
                    }

                    if (state.canRestore) {
                        item {
                            SettingsSection {
                                RestoreOptions.options.forEachIndexed { index, option ->
                                    SettingsOptionRow(
                                        label = stringResource(option.label),
                                        checked = option.getter(state.options),
                                        onCheckedChange = {
                                            model.toggle(option.setter, it)
                                        },
                                        divider = index < RestoreOptions.options.lastIndex,
                                    )
                                }
                            }
                        }
                    }

                    if (state.error != null) {
                        errorMessageItem(state.error)
                    }
                }
    }

    private fun LazyListScope.errorMessageItem(
        error: Any?,
    ) {
        item {
            SettingsSection {
                Column(
                    modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    val msg = buildAnnotatedString {
                        when (error) {
                            is MissingRestoreComponents -> {
                                appendLine(stringResource(MR.strings.backup_restore_content_full))
                                if (error.trackers.isNotEmpty()) {
                                    appendLine()
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                        appendLine(stringResource(MR.strings.backup_restore_missing_trackers))
                                    }
                                    error.trackers.joinTo(
                                        this,
                                        separator = "\n- ",
                                        prefix = "- ",
                                    )
                                }
                            }

                            is InvalidRestore -> {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                    appendLine(stringResource(MR.strings.invalid_backup_file))
                                }
                                appendLine(error.uri.toString())

                                appendLine()

                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                    appendLine(stringResource(MR.strings.invalid_backup_file_error))
                                }
                                appendLine(error.message)
                            }

                            else -> {
                                appendLine(error.toString())
                            }
                        }
                    }

                    SelectionContainer {
                        Text(text = msg)
                    }
                }
            }
        }
    }
}

private class RestoreBackupScreenModel(
    private val context: Context,
    private val uri: String,
) : StateScreenModel<RestoreBackupScreenModel.State>(State()) {

    private val extensionManager: ExtensionManager = Injekt.get()
    private val sourceManager: SourceManager = Injekt.get()
    private val installSteps = MutableStateFlow(emptyMap<String, InstallStep>())

    init {
        validate(uri.toUri())
        screenModelScope.launch(Dispatchers.IO) { loadExtensions() }
    }

    private suspend fun loadExtensions() {
        val backup = runCatching { BackupDecoder(context).decode(uri.toUri()) }.getOrNull() ?: return
        fun neededNow(sources: List<Source>) = RestoreExtensions.needed(
            backup,
            sources.map {
                NekoBackupMapper.SourceIdentity(it.id, it.name, it.lang, (it as? HttpSource)?.baseUrl)
            },
        )
        if (neededNow(sourceManager.getAll()).isEmpty()) return
        mutableState.update { it.copy(extensions = RestoreExtensionsState.Checking) }
        runCatching { extensionManager.findAvailableExtensions() }
        combine(
            extensionManager.availableExtensionsFlow,
            extensionManager.installedExtensionsFlow,
            installSteps,
            sourceManager.sources,
        ) { available, _, steps, sources ->
            val matches = RestoreExtensions.match(neededNow(sources), available)
            if (matches.isEmpty()) RestoreExtensionsState.None else RestoreExtensionsState.Ready(matches, steps)
        }.collect { extensions -> mutableState.update { it.copy(extensions = extensions) } }
    }

    fun install(extension: Extension.Available) {
        val current = installSteps.value[extension.pkgName]
        if (current != null && !current.isCompleted()) return
        installSteps.update { it + (extension.pkgName to InstallStep.Pending) }
        screenModelScope.launch {
            val step = retrying(
                delaysMs = listOf(2_000L, 6_000L),
                isFailure = { it == InstallStep.Error },
            ) {
                var last = InstallStep.Pending
                extensionManager.installExtension(extension).collect { step ->
                    last = step
                    installSteps.update { it + (extension.pkgName to step) }
                }
                if (last == InstallStep.Error) {
                    installSteps.update { it + (extension.pkgName to InstallStep.Pending) }
                }
                last
            }
            installSteps.update { it + (extension.pkgName to step) }
        }
    }

    fun installAll() {
        (state.value.extensions as? RestoreExtensionsState.Ready)?.matches
            ?.mapNotNull { it.extension }
            ?.distinctBy { it.pkgName }
            ?.forEach(::install)
    }

    fun toggle(setter: (RestoreOptions, Boolean) -> RestoreOptions, enabled: Boolean) {
        mutableState.update {
            it.copy(
                options = setter(it.options, enabled),
            )
        }
    }

    fun startRestore() {
        BackupRestoreJob.start(
            context = context,
            uri = uri.toUri(),
            options = state.value.options,
        )
    }

    private fun validate(uri: Uri) {
        val results = try {
            BackupFileValidator(context).validate(uri)
        } catch (e: Exception) {
            setError(
                error = InvalidRestore(uri, e.message.toString()),
                canRestore = false,
            )
            return
        }

        if (results.missingTrackers.isNotEmpty()) {
            setError(
                error = MissingRestoreComponents(uri, results.missingTrackers),
                canRestore = true,
            )
            return
        }

        setError(error = null, canRestore = true)
    }

    private fun setError(error: Any?, canRestore: Boolean) {
        mutableState.update {
            it.copy(
                error = error,
                canRestore = canRestore,
            )
        }
    }

    @Immutable
    data class State(
        val error: Any? = null,
        val canRestore: Boolean = false,
        val options: RestoreOptions = RestoreOptions(),
        val extensions: RestoreExtensionsState = RestoreExtensionsState.None,
    )
}

private data class MissingRestoreComponents(
    val uri: Uri,
    val trackers: List<String>,
)

private data class InvalidRestore(
    val uri: Uri? = null,
    val message: String,
)
