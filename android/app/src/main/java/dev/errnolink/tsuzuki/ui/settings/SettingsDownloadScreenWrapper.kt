package dev.errnolink.tsuzuki.ui.settings
import eu.kanade.presentation.more.settings.screen.SearchableSettings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SettingsDownloadScreen
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.storage.displayablePath
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.storage.service.StorageManager.Companion.allowAccessStorage
import tachiyomi.domain.storage.service.StorageManager.Companion.directoryAccessible
import tachiyomi.domain.storage.service.StoragePreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object TsuzukiSettingsDownloadScreen : SearchableSettings {
    @Suppress("unused")
    private fun readResolve(): Any = TsuzukiSettingsDownloadScreen

    override val displayName = "Downloads & storage"

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_downloads

    @Composable
    override fun Content() = TsuzukiPreferenceContent()

    val prefKeys: Set<String> = setOf(
        "pref_download_only_over_wifi_key",
        "save_chapter_as_cbz",
        "split_tall_images",
        "download_parallel_source_limit",
        "download_parallel_page_limit",
        "pref_remove_after_marked_as_read_key",
        "remove_after_read_slots",
        "pref_remove_bookmarked",
        "remove_exclude_categories",
        "download_new",
        "download_new_unread_chapters_only",
        "download_new_categories",
        "auto_download_while_reading",
        "download_cache_renew_interval",
        "storage_dir",
        "auto_clear_chapter_cache",
    )

    @Composable
    override fun getPreferences(): List<Preference> {
        val base = SettingsDownloadScreen.getPreferences()
        val groups = base.preferenceGroups()
        val loose = base.looseItems()

        val deleteChapters = groups.firstOrNull { it.title == stringResource(MR.strings.pref_category_delete_chapters) }
            ?: groups.getOrNull(0)
        val autoDownload = groups.firstOrNull { it.title == stringResource(MR.strings.pref_category_auto_download) }
            ?: groups.getOrNull(1)
        val downloadAhead = groups.firstOrNull { it.title == stringResource(MR.strings.download_ahead) }
            ?: groups.getOrNull(2)
        val cacheRenew = groups.firstOrNull { it.title == stringResource(KMR.strings.download_cache_renew_interval) }
            ?: groups.getOrNull(3)

        val deleteRows = deleteChapters?.let { retitleAutoDeleteRows(it.withTitle("Auto-delete")) }
        val autoDownloadGroup = autoDownload?.withTitle("Auto-download")
        val aheadGroup = downloadAhead?.withTitle("Download ahead")
        val storageGroup = getStorageGroup(
            storagePreferences = remember { Injekt.get<StoragePreferences>() },
            cacheRenewRows = listOfNotNull(
                cacheRenew?.preferenceItems?.byStoreKey("download_cache_renew_interval")
                    ?.withRetitle("Reindex downloads")
                    ?.withSubtitle("Rescans download folders for added or removed chapters — %s"),
                cacheRenew?.preferenceItems
                    ?.filterIsInstance<Preference.PreferenceItem.InfoPreference>()?.firstOrNull(),
            ),
        )

        return listOfNotNull(
                newGroup(
                    title = "Download rules",
                    items = loose
                        .retitledWhere("pref_download_only_over_wifi_key", "Download only on Wi-Fi")
                        .retitledWhere("download_parallel_source_limit", "Sources downloaded at once")
                        .retitledWhere("download_parallel_page_limit", "Pages downloaded at once"),
                ),
                autoDownloadGroup,
                aheadGroup,
                deleteRows,
                storageGroup,
            )
    }

    // Upstream keys/titles for the auto-delete tail rows; promoted to plain-language titles.
    private fun retitleAutoDeleteRows(group: Preference.PreferenceGroup): Preference.PreferenceGroup =
        newGroup(
            title = group.title,
            items = group.preferenceItems
                .retitledWhere("remove_after_read_slots", "Delete after finishing")
                .subtitledWhere("remove_after_read_slots", "Deletes downloaded chapters as you read on — %s is the one removed")
                .retitledWhere("pref_remove_after_marked_as_read_key", "Delete chapters you mark as read")
                .retitledWhere("pref_remove_bookmarked", "Also delete bookmarked chapters"),
        )

    @Composable
    private fun getStorageGroup(
        storagePreferences: StoragePreferences,
        cacheRenewRows: List<Preference>,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }

        val chapterCache = remember { Injekt.get<ChapterCache>() }
        var cacheReadableSizeSema by remember { mutableIntStateOf(0) }
        val cacheReadableSize = remember(cacheReadableSizeSema) { chapterCache.readableSize }
        val storagePref = storagePreferences.baseStorageDirectory()
        val storageDir by storagePref.collectAsState()
        val locationValid = remember(storageDir) { directoryAccessible(context, storageDir) }
        val displayPath = remember(storageDir) {
            UniFile.fromUri(context, storageDir.toUri())?.displayablePath
        }
        val pickStorageLocation = storageLocationPicker(storagePref)
        val openPicker: () -> Unit = {
            try {
                allowAccessStorage(context, storagePref) {
                    pickStorageLocation.launch(null)
                }
            } catch (_: Exception) {
                context.toast(MR.strings.file_picker_error)
            }
        }

        val storageRows: List<Preference> = listOf(
            Preference.PreferenceItem.CustomPreference(
                title = stringResource(MR.strings.pref_storage_location),
            ) {
                val friendly = displayPath?.let { friendlyStorageName(it) }
                if (locationValid && friendly != null) {
                    GroupedRow(
                        title = friendly,
                        subtitle = displayPath,
                        chevron = true,
                        divider = false,
                        onClick = openPicker,
                    )
                } else {
                    GroupedRow(
                        title = stringResource(MR.strings.pref_storage_location),
                        subtitle = stringResource(MR.strings.no_location_set),
                        chevron = true,
                        divider = false,
                        destructive = true,
                        onClick = openPicker,
                    )
                }
            },
            Preference.PreferenceItem.InfoPreference(stringResource(MR.strings.pref_storage_location_info)),
            Preference.PreferenceItem.CustomPreference(
                title = stringResource(MR.strings.pref_storage_usage),
            ) {
                StorageInfo(
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            },
            Preference.PreferenceItem.TextPreference(
                title = stringResource(MR.strings.pref_clear_chapter_cache),
                subtitle = stringResource(MR.strings.used_cache, cacheReadableSize),
                onClick = {
                    scope.launchNonCancellable {
                        try {
                            val deletedFiles = chapterCache.clear()
                            withUIContext {
                                context.toast(context.stringResource(MR.strings.cache_deleted, deletedFiles))
                                cacheReadableSizeSema++
                            }
                        } catch (e: Throwable) {
                            logcat(LogPriority.ERROR, e)
                            withUIContext { context.toast(MR.strings.cache_delete_error) }
                        }
                    }
                },
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = libraryPreferences.autoClearChapterCache(),
                title = stringResource(MR.strings.pref_auto_clear_chapter_cache),
            ),
        ) + cacheRenewRows

        return newGroup(title = "Storage", items = storageRows)
    }

    private fun friendlyStorageName(path: String): String = when {
        path == "/storage/emulated/0" || path.startsWith("/storage/emulated/0/") -> "Internal storage"
        path.startsWith("/storage/") -> path.trimStart('/').substringBefore('/').replaceFirstChar { it.uppercaseChar() }
        else -> path.substringAfterLast('/').ifEmpty { path }
    }

    @Composable
    fun storageLocationPicker(
        storageDirPref: tachiyomi.core.common.preference.Preference<String>,
    ): ManagedActivityResultLauncher<Uri?, Uri?> {
        val context = LocalContext.current

        return rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            if (uri != null) {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION

                // For some reason InkBook devices do not implement the SAF properly. Persistable URI grants do not
                // work. However, simply retrieving the URI and using it works fine for these devices. Access is not
                // revoked after the app is closed or the device is restarted.
                // This also holds for some Samsung devices. Thus, we simply execute inside of a try-catch block and
                // ignore the exception if it is thrown.
                try {
                    context.contentResolver.takePersistableUriPermission(uri, flags)
                } catch (e: SecurityException) {
                    logcat(LogPriority.ERROR, e)
                    context.toast(MR.strings.file_picker_uri_permission_unsupported)
                }

                UniFile.fromUri(context, uri)?.let {
                    storageDirPref.set("") // Trigger recompose
                    storageDirPref.set(it.uri.toString())
                }
            }
        }
    }

    @Composable
    fun storageLocationText(
        storageDirPref: tachiyomi.core.common.preference.Preference<String>,
    ): String {
        val context = LocalContext.current
        val storageDir by storageDirPref.collectAsState()

        val locationValid = remember(storageDir) {
            directoryAccessible(context, storageDir)
        }

        if (!locationValid) {
            return stringResource(MR.strings.no_location_set)
        }

        return remember(storageDir) {
            val file = UniFile.fromUri(context, storageDir.toUri())
            file?.displayablePath
        } ?: stringResource(MR.strings.invalid_location, storageDir)
    }
}
