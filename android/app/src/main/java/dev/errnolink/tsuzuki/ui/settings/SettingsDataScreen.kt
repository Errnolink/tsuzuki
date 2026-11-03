package dev.errnolink.tsuzuki.ui.settings
import kotlinx.collections.immutable.toPersistentMap
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.sync.PairDeepLink
import dev.errnolink.tsuzuki.sync.PairingStore
import dev.errnolink.tsuzuki.sync.parsePairLink

import eu.kanade.presentation.more.settings.screen.SearchableSettings

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.util.relativeTimeSpanString
import eu.kanade.tachiyomi.data.backup.create.BackupCreateJob
import eu.kanade.tachiyomi.data.backup.restore.BackupRestoreJob
import eu.kanade.tachiyomi.data.export.LibraryExporter
import eu.kanade.tachiyomi.data.export.LibraryExporter.ExportOptions
import eu.kanade.tachiyomi.data.sync.SyncDataJob
import eu.kanade.tachiyomi.data.sync.SyncManager
import eu.kanade.tachiyomi.data.sync.service.GoogleDriveService
import eu.kanade.tachiyomi.data.sync.service.GoogleDriveSyncService
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.domain.manga.interactor.GetFavorites
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object TsuzukiSettingsDataScreen : SearchableSettings {
    @Suppress("unused")
    private fun readResolve(): Any = TsuzukiSettingsDataScreen

    override val displayName = "Backup & sync"

    val restorePreferenceKeyString = MR.strings.label_backup
    const val HELP_URL = "https://mihon.app/docs/faq/storage"

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.label_data_storage


    @Composable
    override fun Content() = TsuzukiPreferenceContent()
val prefKeys: Set<String> = setOf(
        "backup_interval",
        "last_auto_backup_timestamp",
        "pref_show_restoring_progress_banner_key",
        "sync_service",
        "connection_sync_client_host",
        "connection_sync_client_api_key",
        "connection_webdav_url",
        "connection_webdav_username",
        "connection_webdav_password",
        "connection_webdav_folder",
        "sync_interval",
        "pref_sync_extensions",
        "pref_sync_uninstalls",
        "pref_show_syncing_progress_banner_key",
        "last_sync_timestamp",
    )

    @Composable
    override fun RowScope.AppBarAction() {
        val uriHandler = LocalUriHandler.current
        SettingsIconControl(onClick = { uriHandler.openUri(HELP_URL) }) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                contentDescription = stringResource(MR.strings.label_help),
                tint = TsuzukiTheme.colors.text,
            )
        }
    }

    @Composable
    override fun getPreferences(): List<Preference> {
        val backupPreferences = Injekt.get<BackupPreferences>()

        val syncPreferences = remember { Injekt.get<SyncPreferences>() }
        val syncService by syncPreferences.syncService().collectAsState()

        return persistentListOf(
            getBackupAndRestoreGroup(backupPreferences = backupPreferences),
            getExportGroup(),
        ) +
            // SY -->
            getSyncPreferences(syncPreferences = syncPreferences, syncService = syncService)
        // SY <--
    }

    @Composable
    private fun getBackupAndRestoreGroup(backupPreferences: BackupPreferences): Preference.PreferenceGroup {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val lastAutoBackup by backupPreferences.lastAutoBackupTimestamp().collectAsState()

        val chooseBackup = rememberLauncherForActivityResult(
            object : ActivityResultContracts.GetContent() {
                override fun createIntent(context: Context, input: String): Intent {
                    val intent = super.createIntent(context, input)
                    return Intent.createChooser(intent, context.stringResource(MR.strings.file_select_backup))
                }
            },
        ) {
            if (it == null) {
                context.toast(MR.strings.file_null_uri_error)
                return@rememberLauncherForActivityResult
            }

            navigator.push(TsuzukiRestoreBackupScreen(it.toString()))
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.label_backup),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_create_backup),
                    onClick = {
                        navigator.push(TsuzukiCreateBackupScreen())
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_restore_backup),
                    onClick = {
                        if (!BackupRestoreJob.isRunning(context)) {
                            if (DeviceUtil.isMiui && DeviceUtil.isMiuiOptimizationDisabled()) {
                                context.toast(MR.strings.restore_miui_warning)
                            }

                            // no need to catch because it's wrapped with a chooser
                            chooseBackup.launch("*/*")
                        } else {
                            context.toast(MR.strings.restore_in_progress)
                        }
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = backupPreferences.backupInterval(),
                    entries = persistentMapOf(
                        0 to stringResource(MR.strings.off),
                        1 to stringResource(MR.strings.update_1hour),
                        3 to stringResource(MR.strings.update_3hour),
                        6 to stringResource(MR.strings.update_6hour),
                        12 to stringResource(MR.strings.update_12hour),
                        24 to stringResource(MR.strings.update_24hour),
                        48 to stringResource(MR.strings.update_48hour),
                        168 to stringResource(MR.strings.update_weekly),
                    ),
                    title = "Automatic backups interval",
                    onValueChanged = {
                        BackupCreateJob.setupTask(context, it)
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = backupPreferences.showRestoringProgressBanner(),
                    title = stringResource(KMR.strings.pref_show_restoring_progress_banner),
                ),
                Preference.PreferenceItem.InfoPreference(
                    stringResource(MR.strings.backup_info) + "\n\n" +
                        stringResource(MR.strings.last_auto_backup_info, relativeTimeSpanString(lastAutoBackup)),
                ),
            ),
        )
    }

    @Composable
    private fun getExportGroup(): Preference.PreferenceGroup {
        var showDialog by remember { mutableStateOf(false) }
        var exportOptions by remember {
            mutableStateOf(
                ExportOptions(
                    includeTitle = true,
                    includeAuthor = true,
                    includeArtist = true,
                ),
            )
        }

        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val getFavorites = remember { Injekt.get<GetFavorites>() }
        var favorites by remember { mutableStateOf<List<Manga>>(emptyList()) }
        LaunchedEffect(Unit) {
            favorites = getFavorites.await()
        }

        val saveFileLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/csv"),
        ) { uri ->
            uri?.let {
                scope.launch {
                    LibraryExporter.exportToCsv(
                        context = context,
                        uri = it,
                        favorites = favorites,
                        options = exportOptions,
                        onExportComplete = {
                            scope.launch(Dispatchers.Main) {
                                context.toast(MR.strings.library_exported)
                            }
                        },
                    )
                }
            }
        }

        if (showDialog) {
            ColumnSelectionDialog(
                options = exportOptions,
                onConfirm = { options ->
                    exportOptions = options
                    saveFileLauncher.launch("tsuzuki_library.csv")
                },
                onDismissRequest = { showDialog = false },
            )
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.export),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = "Export library to CSV",
                    onClick = { showDialog = true },
                ),
            ),
        )
    }

    @Composable
    private fun ColumnSelectionDialog(
        options: ExportOptions,
        onConfirm: (ExportOptions) -> Unit,
        onDismissRequest: () -> Unit,
    ) {
        MultiChoiceSheet(
            visible = true,
            title = stringResource(MR.strings.migration_dialog_what_to_include),
            doneLabel = stringResource(MR.strings.action_save),
            onDone = {
                onConfirm(options)
                onDismissRequest()
            },
            options = listOf(
                Triple(
                    stringResource(MR.strings.title),
                    options.includeTitle,
                ) { checked ->
                    onConfirm(
                        if (checked) {
                            options
                        } else {
                            options.copy(includeTitle = false, includeAuthor = false, includeArtist = false)
                        },
                    )
                },
                Triple(
                    stringResource(MR.strings.author),
                    options.includeAuthor,
                ) { checked ->
                    if (options.includeTitle) onConfirm(options.copy(includeAuthor = checked))
                },
                Triple(
                    stringResource(MR.strings.artist),
                    options.includeArtist,
                ) { checked ->
                    if (options.includeTitle) onConfirm(options.copy(includeArtist = checked))
                },
            ),
            onDismissRequest = onDismissRequest,
        )
    }

    // SY -->
    @Composable
    private fun getSyncPreferences(syncPreferences: SyncPreferences, syncService: Int): List<Preference> {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val googleDriveAvailable = eu.kanade.tachiyomi.data.sync.service.GoogleDriveService.isConfigured(context)
        if (!googleDriveAvailable && syncService == SyncManager.SyncService.GOOGLE_DRIVE.value) {
            syncPreferences.syncService().set(SyncManager.SyncService.NONE.value)
        }
        val syncServiceType = if (!googleDriveAvailable && syncService == SyncManager.SyncService.GOOGLE_DRIVE.value) {
            SyncManager.SyncService.NONE
        } else {
            SyncManager.SyncService.fromInt(syncService)
        }
        val lastSync by syncPreferences.lastSyncTimestamp().collectAsState()

        val serviceEntries = mutableMapOf(
            SyncManager.SyncService.NONE.value to stringResource(MR.strings.off),
            SyncManager.SyncService.SYNCYOMI.value to stringResource(SYMR.strings.syncyomi),
        )
        if (googleDriveAvailable) {
            serviceEntries[SyncManager.SyncService.GOOGLE_DRIVE.value] = stringResource(SYMR.strings.google_drive)
        }
        serviceEntries[SyncManager.SyncService.WEB_DAV.value] = stringResource(KMR.strings.web_dav)

        val serviceRow = Preference.PreferenceItem.ListPreference(
            preference = syncPreferences.syncService(),
            entries = serviceEntries.toPersistentMap(),
            title = stringResource(SYMR.strings.pref_sync_service),
            onValueChanged = {
                if (it != SyncManager.SyncService.NONE.value) {
                    SyncDataJob.setupTask(context)
                } else {
                    SyncDataJob.setupTask(context, prefInterval = 0)
                }
                true
            },
        )

        val accountRows = when (syncServiceType) {
            SyncManager.SyncService.NONE -> emptyList()
            SyncManager.SyncService.SYNCYOMI -> getSelfHostPreferences(syncPreferences)
            SyncManager.SyncService.GOOGLE_DRIVE -> getGoogleDrivePreferences()
            SyncManager.SyncService.WEB_DAV -> getWebDavPreferences(syncPreferences)
        }

        val chooseWhatRow = if (syncServiceType != SyncManager.SyncService.NONE) {
            listOf(
                Preference.PreferenceItem.TextPreference(
                    title = "Choose what syncs",
                    onClick = {
                        navigator.push(TsuzukiSyncSettingsSelector())
                    },
                ),
            )
        } else {
            emptyList()
        }

        val triggerRow = if (syncServiceType != SyncManager.SyncService.NONE) {
            listOf(
                Preference.PreferenceItem.TextPreference(
                    title = "When to sync automatically",
                    subtitle = stringResource(SYMR.strings.pref_sync_options_summ),
                    onClick = { navigator.push(TsuzukiSyncTriggerOptionsScreen()) },
                ),
            )
        } else {
            emptyList()
        }

        val automationRows = if (syncServiceType != SyncManager.SyncService.NONE) {
            listOf(
                Preference.PreferenceItem.ListPreference(
                    preference = syncPreferences.syncInterval(),
                    entries = persistentMapOf(
                        0 to stringResource(MR.strings.off),
                        15 to "Every 15 minutes",
                        30 to stringResource(SYMR.strings.update_30min),
                        60 to stringResource(SYMR.strings.update_1hour),
                        180 to stringResource(SYMR.strings.update_3hour),
                        360 to stringResource(MR.strings.update_6hour),
                        720 to stringResource(MR.strings.update_12hour),
                        1440 to stringResource(MR.strings.update_24hour),
                        2880 to stringResource(MR.strings.update_48hour),
                        10080 to stringResource(MR.strings.update_weekly),
                    ),
                    title = stringResource(SYMR.strings.pref_sync_interval),
                    onValueChanged = {
                        SyncDataJob.setupTask(context, prefInterval = it)
                        true
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(SYMR.strings.pref_sync_now),
                    subtitle = stringResource(SYMR.strings.pref_sync_now_subtitle),
                    onClick = {
                        if (!SyncDataJob.isRunning(context)) {
                            SyncDataJob.startNow(context, manual = true)
                        } else {
                            context.toast(SYMR.strings.sync_in_progress)
                        }
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = syncPreferences.syncExtensions(),
                    title = stringResource(KMR.strings.pref_sync_extensions),
                    subtitle = stringResource(KMR.strings.pref_sync_extensions_summ),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = syncPreferences.syncUninstalls(),
                    title = stringResource(KMR.strings.pref_sync_uninstalls),
                    subtitle = stringResource(KMR.strings.pref_sync_uninstalls_summ),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = syncPreferences.showSyncingProgressBanner(),
                    title = stringResource(KMR.strings.pref_show_syncing_progress_banner),
                ),
                Preference.PreferenceItem.InfoPreference(
                    stringResource(SYMR.strings.last_synchronization, relativeTimeSpanString(lastSync)),
                ),
            )
        } else {
            emptyList()
        }

        val connectionRows = listOf(serviceRow) + accountRows + chooseWhatRow
        val optionRows = triggerRow + automationRows

        return listOf(
            newGroup(
                title = stringResource(SYMR.strings.pref_sync_service_category),
                items = connectionRows,
            ),
        ) + if (optionRows.isNotEmpty()) {
            listOf(
                newGroup(
                    title = "Sync options",
                    items = optionRows.retitledWhere("sync_interval", "Sync frequency"),
                ),
            )
        } else {
            emptyList()
        }
    }

    @Composable
    private fun getGoogleDrivePreferences(): List<Preference> {
        val context = LocalContext.current
        val googleDriveSync = Injekt.get<GoogleDriveService>()
        return listOf(
            Preference.PreferenceItem.TextPreference(
                title = stringResource(SYMR.strings.pref_google_drive_sign_in),
                onClick = {
                    val intent = googleDriveSync.getSignInIntent()
                    context.startActivity(intent)
                },
            ),
            getGoogleDrivePurge(),
        )
    }

    @Composable
    private fun getGoogleDrivePurge(): Preference.PreferenceItem.TextPreference {
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val googleDriveSync = remember { GoogleDriveSyncService(context) }
        var showPurgeDialog by remember { mutableStateOf(false) }

        if (showPurgeDialog) {
            PurgeConfirmationDialog(
                onConfirm = {
                    showPurgeDialog = false
                    scope.launch {
                        val result = googleDriveSync.deleteSyncDataFromGoogleDrive()
                        when (result) {
                            GoogleDriveSyncService.DeleteSyncDataStatus.NOT_INITIALIZED -> context.toast(
                                SYMR.strings.google_drive_not_signed_in,
                                duration = 5000,
                            )
                            GoogleDriveSyncService.DeleteSyncDataStatus.NO_FILES -> context.toast(
                                SYMR.strings.google_drive_sync_data_not_found,
                                duration = 5000,
                            )
                            GoogleDriveSyncService.DeleteSyncDataStatus.SUCCESS -> context.toast(
                                SYMR.strings.google_drive_sync_data_purged,
                                duration = 5000,
                            )
                            GoogleDriveSyncService.DeleteSyncDataStatus.ERROR -> context.toast(
                                SYMR.strings.google_drive_sync_data_purge_error,
                                duration = 10000,
                            )
                        }
                    }
                },
                onDismissRequest = { showPurgeDialog = false },
            )
        }

        return Preference.PreferenceItem.TextPreference(
            title = stringResource(SYMR.strings.pref_google_drive_purge_sync_data),
            onClick = { showPurgeDialog = true },
        )
    }

    @Composable
    private fun PurgeConfirmationDialog(
        onConfirm: () -> Unit,
        onDismissRequest: () -> Unit,
    ) {
        ConfirmSheet(
            visible = true,
            title = stringResource(SYMR.strings.pref_purge_confirmation_title),
            message = stringResource(SYMR.strings.pref_purge_confirmation_message),
            confirmLabel = stringResource(MR.strings.action_ok),
            destructive = true,
            onConfirm = onConfirm,
            onDismissRequest = onDismissRequest,
        )
    }

    @Composable
    private fun getSelfHostPreferences(syncPreferences: SyncPreferences): List<Preference> {
        val scope = rememberCoroutineScope()

        val qrScanLauncher = rememberLauncherForActivityResult(ScanContract()) {
            if (it.contents != null && it.contents.isNotEmpty()) {
                // KMK -->
                // A pair link from the PC hub runs the same confirmation flow as
                // scanning its QR with the phone camera; anything else stays a raw key.
                val pairRequest = parsePairLink(it.contents)
                if (pairRequest != null) {
                    PairDeepLink.propose(pairRequest)
                } else {
                    syncPreferences.clientAPIKey().set(it.contents)
                }
                // KMK <--
            }
        }
        val context = LocalContext.current
        val scanOptions = remember {
            ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setOrientationLocked(false)
                setPrompt(SYMR.strings.scan_qr_code.getString(context))
                addExtra(Intents.Scan.SCAN_TYPE, Intents.Scan.MIXED_SCAN)
            }
        }

        // KMK -->
        val pairedName by PairingStore.pairedPcName().collectAsState()
        val hubHost by syncPreferences.clientHost().collectAsState()
        // KMK <--

        return listOf(
            Preference.PreferenceItem.EditTextPreference(
                preference = syncPreferences.clientHost(),
                title = "Sync server address",
                // KMK -->
                subtitle = if (pairedName.isBlank()) {
                    stringResource(SYMR.strings.pref_sync_host_summ)
                } else {
                    "Paired with $pairedName · $hubHost"
                },
                // KMK <--
                onValueChanged = { newValue ->
                    scope.launch {
                        // Trim spaces at the beginning and end, then remove trailing slash if present
                        val trimmedValue = newValue.trim()
                        val modifiedValue = trimmedValue.trimEnd { it == '/' }
                        syncPreferences.clientHost().set(modifiedValue)
                    }
                    true
                },
            ),
            Preference.PreferenceItem.CustomPreference(
                title = "Sync server key",
            ) {
                val values by syncPreferences.clientAPIKey().collectAsState()
                EditTextPreferenceWidget(
                    title = "Sync server key",
                    subtitle = stringResource(SYMR.strings.pref_sync_api_key_summ),
                    onConfirm = {
                        scope.launch {
                            syncPreferences.clientAPIKey().set(it)
                        }
                        true
                    },
                    icon = null,
                    value = values,
                    widget = {
                        SettingsIconControl(
                            onClick = { qrScanLauncher.launch(scanOptions) },
                            modifier = Modifier.padding(start = TrailingWidgetBuffer),
                        ) {
                            Icon(
                                Icons.Filled.QrCodeScanner,
                                contentDescription = stringResource(SYMR.strings.scan_qr_code),
                            )
                        }
                    },
                )
            },
        )
    }

    // KMK -->
    @Composable
    private fun getWebDavPreferences(syncPreferences: SyncPreferences): List<Preference> {
        val scope = rememberCoroutineScope()

        return listOf(
            Preference.PreferenceItem.EditTextPreference(
                preference = syncPreferences.webDavUrl(),
                title = stringResource(KMR.strings.pref_webdav_url),
                subtitle = stringResource(KMR.strings.pref_webdav_url_summ),
                onValueChanged = { newValue ->
                    scope.launch {
                        syncPreferences.webDavUrl().set(newValue.trim())
                    }
                    true
                },
            ),
            Preference.PreferenceItem.EditTextPreference(
                preference = syncPreferences.webDavUsername(),
                title = stringResource(KMR.strings.pref_webdav_username),
                subtitle = stringResource(KMR.strings.pref_webdav_username_summ),
                onValueChanged = { newValue ->
                    scope.launch {
                        syncPreferences.webDavUsername().set(newValue.trim())
                    }
                    true
                },
            ),
            run {
                var dialogOpen by remember { mutableStateOf(false) }
                if (dialogOpen) {
                    TsuzukiSettingsSecurityScreen.PasswordDialog(
                        onDismissRequest = { dialogOpen = false },
                        onReturnPassword = { password ->
                            dialogOpen = false
                            scope.launch {
                                syncPreferences.webDavPassword().set(password.replace("\n", ""))
                            }
                        },
                        title = KMR.strings.pref_webdav_password,
                    )
                }
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(KMR.strings.pref_webdav_password),
                    subtitle = stringResource(KMR.strings.pref_webdav_password_summ),
                    onClick = {
                        dialogOpen = true
                    },
                )
            },
            Preference.PreferenceItem.EditTextPreference(
                preference = syncPreferences.webDavFolder(),
                title = stringResource(KMR.strings.pref_webdav_folder),
                subtitle = stringResource(KMR.strings.pref_webdav_folder_summ),
                onValueChanged = { newValue ->
                    scope.launch {
                        syncPreferences.webDavFolder().set(newValue.trim())
                    }
                    true
                },
            ),
        )
    }
    // KMK <--

    // SY <--
}
