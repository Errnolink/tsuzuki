package eu.kanade.tachiyomi.data.sync

import android.content.Context
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.restore.BackupRestorer
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import dev.errnolink.tsuzuki.sync.ExtensionSync
import dev.errnolink.tsuzuki.sync.UnchangedRemoteManga
import eu.kanade.tachiyomi.data.SyncStatus
import eu.kanade.tachiyomi.data.backup.restore.restorers.CategoriesRestorer
import eu.kanade.tachiyomi.data.sync.service.GoogleDriveSyncService
import eu.kanade.tachiyomi.data.sync.service.SyncService as SyncServiceProvider
import eu.kanade.tachiyomi.data.sync.service.SyncData
import eu.kanade.tachiyomi.data.sync.service.SyncResult
import eu.kanade.tachiyomi.data.sync.service.SyncYomiSyncService
import eu.kanade.tachiyomi.data.sync.service.WebDavSyncService
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import logcat.LogPriority
import logcat.logcat
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.manga.MangaMapper.mapManga
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date

/**
 * A manager to handle synchronization tasks in the app, such as updating
 * sync preferences and performing synchronization with a remote server.
 *
 * @property context The application context.
 */
class SyncManager(
    private val context: Context,
    private val handler: DatabaseHandler = Injekt.get(),
    private val syncPreferences: SyncPreferences = Injekt.get(),
    private var json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    },
) {
    private val backupCreator: BackupCreator = BackupCreator(context, false)
    private val notifier: SyncNotifier = SyncNotifier(context)
    private val categoriesRestorer: CategoriesRestorer = CategoriesRestorer()

    // KMK -->
    private val syncStatus: SyncStatus = Injekt.get()
    // KMK <--

    enum class SyncService(val value: Int) {
        NONE(0),
        SYNCYOMI(1),
        GOOGLE_DRIVE(2),
        // KMK -->
        WEB_DAV(3),
        // KMK <--
        ;

        companion object {
            fun fromInt(value: Int) = entries.firstOrNull { it.value == value } ?: NONE
        }
    }

    /**
     * Syncs data with a sync service.
     *
     * This function retrieves local data (favorites, manga, extensions, and categories)
     * from the database using the BackupManager, then synchronizes the data with a sync service.
     */
    suspend fun syncData() {
        // Reset isSyncing in case it was left over or failed syncing during restore.
        handler.await(inTransaction = true) {
            mangasQueries.resetIsSyncing()
            chaptersQueries.resetIsSyncing()
            // KMK -->
            categoriesQueries.resetIsSyncing()
            // KMK <--
        }

        val syncOptions = syncPreferences.getSyncSettings()

        val backupOptions = BackupOptions(
            libraryEntries = syncOptions.libraryEntries,
            categories = syncOptions.categories,
            chapters = syncOptions.chapters,
            tracking = syncOptions.tracking,
            history = syncOptions.history,
            extensionStores = syncOptions.extensionStores,
            appSettings = syncOptions.appSettings,
            sourceSettings = syncOptions.sourceSettings,
            privateSettings = syncOptions.privateSettings,

            // SY -->
            customInfo = syncOptions.customInfo,
            readEntries = syncOptions.readEntries,
            savedSearchesFeeds = syncOptions.savedSearchesFeeds,
            // SY <--
        )

        // Handle sync based on the selected service
        val syncService = when (val syncService = SyncService.fromInt(syncPreferences.syncService().get())) {
            SyncService.SYNCYOMI -> {
                SyncYomiSyncService(
                    context,
                    json,
                    syncPreferences,
                    notifier,
                )
            }

            SyncService.GOOGLE_DRIVE -> {
                if (!eu.kanade.tachiyomi.data.sync.service.GoogleDriveService.isConfigured(context)) {
                    syncPreferences.syncService().set(SyncService.NONE.value)
                    null
                } else {
                    GoogleDriveSyncService(context, json, syncPreferences)
                }
            }

            // KMK -->
            SyncService.WEB_DAV -> {
                WebDavSyncService(context, json, syncPreferences, notifier)
            }
            // KMK <--

            else -> {
                logcat(LogPriority.ERROR) { "Invalid sync service type: $syncService" }
                null
            }
        } ?: return

        // KMK -->
        val identity = if (syncService is SyncYomiSyncService) syncPreferences.prepareSyncIdentity() else ""
        // R2 idle skip: when the hub's version equals our cursor and nothing local
        // needs pushing, skip the manga backup build and the merge request entirely.
        if (syncService is SyncYomiSyncService) {
            val cursor = syncPreferences.syncCursor().get()
            val hubVersion = syncService.probeHubVersion()
            if (hubVersion != null && syncIsSkippable(hubVersion, cursor, localSyncWork(backupOptions))) {
                logcat(LogPriority.INFO) {
                    "Sync skipped: hub version $hubVersion matches cursor $cursor, nothing to push"
                }
                syncPreferences.lastSyncTimestamp().set(Date().time)
                return
            }
        }
        // KMK <--

        val snapshotStartedAt = System.currentTimeMillis() / 1000
        // KMK -->
        // A delta sync only rides manga with local work since the last acked push —
        // the same keeps selectSyncDelta applies, decided per manga from watermark
        // stamps (manga row stamp, or any chapter stamp: reads bump chapter rows
        // only). Building just the candidates keeps the merge request off the
        // multi-minute full-library backup path (b28 stall: PC write -> phone sat in
        // the backup build, merge reached the hub minutes late).
        val fullUploadForBuild = syncPreferences.syncCursor().get() == 0L ||
            syncPreferences.fullSyncRequested().get() ||
            System.currentTimeMillis() - syncPreferences.lastFullSync().get() >
            SyncYomiSyncService.FULL_SYNC_INTERVAL_MS
        val databaseManga = if (syncService is SyncYomiSyncService && !fullUploadForBuild) {
            val nowSec = snapshotStartedAt
            val recordedSince = syncPreferences.lastPushedAt().get()
            val since = if (recordedSince > nowSec) {
                syncPreferences.fullSyncRequested().set(true)
                0L
            } else {
                recordedSince
            }
            val stamped = handler.awaitList { chaptersQueries.mangaIdsWithChapterStampSince(since) }.toHashSet()
            getAllMangaThatNeedsSync().filter { manga ->
                isDeltaCandidate(manga, since, hasChapterStamp = manga.id in stamped)
            }
        } else {
            getAllMangaThatNeedsSync()
        }
        // KMK <--

        logcat(LogPriority.DEBUG) { "Begin create backup" }
        val backupManga = backupCreator.backupMangas(databaseManga, backupOptions)
        val backup = Backup(
            backupManga = backupManga,
            backupCategories = backupCreator.backupCategories(backupOptions),
            backupSources = backupCreator.backupSources(backupManga),
            backupPreferences = backupCreator.backupAppPreferences(backupOptions),
            backupSourcePreferences = backupCreator.backupSourcePreferences(backupOptions),
            backupExtensionStores = backupCreator.backupExtensionStores(backupOptions),

            // SY -->
            backupSavedSearches = backupCreator.backupSavedSearches(backupOptions),
            // SY <--

            // KMK -->
            backupFeeds = backupCreator.backupFeeds(backupOptions),
            // KMK <--
        )

        // KMK -->
        backup.wantedExtensions =
            if (syncPreferences.syncExtensions().get()) ExtensionSync.buildSection() else null
        val categorySnapshot = backupCreator.backupCategories(backupOptions)
        // KMK <--
        logcat(LogPriority.DEBUG) { "End create backup" }
        // KMK -->
        syncStatus.updateProgress(0.25f)
        // KMK <--

        // Create the SyncData object
        val syncData = SyncData(
            deviceId = syncPreferences.uniqueDeviceID(),
            backup = backup,
            // KMK -->
            snapshotStartedAt = snapshotStartedAt,
            identity = identity,
            fullUpload = fullUploadForBuild,
            // KMK <--
        )

        val tombstonesSent = syncPreferences.categoryState.deleted()
        val result = syncService.doSync(syncData)

        // KMK -->
        syncStatus.updateProgress(0.5f)
        // KMK <--

        // KMK -->
        val applyFailed = result.changed && applyRemoteBackup(syncService, result, categorySnapshot, backup)
        if (syncService is SyncYomiSyncService && syncPreferences.currentSyncIdentity() != identity) {
            logcat(LogPriority.WARN) { "Hub pairing changed during sync; result not acknowledged" }
            return
        }
        acknowledgeResult(result, backup, tombstonesSent, applyFailed)
        (syncService as? SyncYomiSyncService)?.reportSyncSuccess()
        // KMK <--
        syncPreferences.lastSyncTimestamp().set(Date().time)
        notifier.showSyncSuccess("Sync completed successfully")
    }

    // KMK -->
    /**
     * Whether this device has anything to push, decided without building the manga
     * backup: watermark stamps on manga/chapters (the delta filter), pending category
     * tombstones, a due full upload, or any non-manga section digest change
     * (categories, app/source settings, stores, searches, feeds, wanted extensions).
     */
    private suspend fun localSyncWork(backupOptions: BackupOptions): SyncWorkState {
        val since = syncPreferences.lastPushedAt().get()
        val stamps = handler.awaitList { mangasQueries.hasSyncStampSince(since) }.first() ||
            handler.awaitList { chaptersQueries.hasSyncStampSince(since) }.first()
        val sectionsBackup = Backup(
            backupCategories = backupCreator.backupCategories(backupOptions),
            backupPreferences = backupCreator.backupAppPreferences(backupOptions),
            backupSourcePreferences = backupCreator.backupSourcePreferences(backupOptions),
            backupExtensionStores = backupCreator.backupExtensionStores(backupOptions),
            backupSavedSearches = backupCreator.backupSavedSearches(backupOptions),
            backupFeeds = backupCreator.backupFeeds(backupOptions),
            wantedExtensions = if (syncPreferences.syncExtensions().get()) ExtensionSync.buildSection() else null,
        )
        val digests = selectSyncSections(sectionsBackup, syncPreferences.sectionDigests().get(), full = false)
        return SyncWorkState(
            fullDue = syncPreferences.syncCursor().get() == 0L ||
                syncPreferences.fullSyncRequested().get() ||
                System.currentTimeMillis() - syncPreferences.lastFullSync().get() > SyncYomiSyncService.FULL_SYNC_INTERVAL_MS,
            tombstonesPending = syncPreferences.categoryState.deleted().isNotEmpty(),
            localStamps = stamps,
            digestsChanged = digests.digests != syncPreferences.sectionDigests().get(),
        )
    }

    private suspend fun applyRemoteBackup(
        syncService: SyncServiceProvider,
        result: SyncResult,
        categorySnapshot: List<BackupCategory>,
        uploaded: Backup,
    ): Boolean {
        try {
            // KMK -->
            // A capped delta omits the category list when the store is quiet; an empty
            // list must not trigger the absence-driven delete path (R4).
            if (result.backup.backupCategories.isNotEmpty() || !result.chapterDelta) {
                categoriesRestorer.applySync(result.backup.backupCategories, categorySnapshot)
            }
            // KMK <--

            val restoreErrors = BackupRestorer(context, BackupNotifier(context), isSync = true).restore(
                // KMK -->
                UnchangedRemoteManga.drop(result.backup, uploaded),
                // KMK <--
                RestoreOptions(
                    appSettings = true,
                    sourceSettings = true,
                    libraryEntries = true,
                    extensionStores = true,
                ),
            )
            if (restoreErrors.isNotEmpty()) {
                logcat(LogPriority.WARN) { "Sync restore finished with ${restoreErrors.size} per-entry errors" }
            }
            return restoreErrors.isNotEmpty() && result.protocolV2
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (result.protocolV2) {
                // Re-deliver the unapplied response on the next sync instead of
                // acknowledging it with the cursor.
                syncPreferences.fullSyncRequested().set(true)
            }
            (syncService as? SyncYomiSyncService)?.reportSyncFailed(e.message)
            throw e
        }
    }

    /**
     * Persists protocol-v2 acknowledgement state. Only called after the remote
     * response has been applied (or there was nothing to apply). `snapshot` is the
     * full pre-trim backup this sync uploaded from; its per-manga versions and
     * chapter counts become the R3 pushed-state guard for the next delta.
     */
    private fun acknowledgeResult(result: SyncResult, snapshot: Backup, tombstonesSent: Set<String>, applyFailed: Boolean) {
        val ack = result.acknowledgement ?: return
        syncPreferences.syncCursor().set(ack.cursor)
        if (ack.fullUpload) {
            syncPreferences.lastFullSync().set(System.currentTimeMillis())
        }
        syncPreferences.fullSyncRequested().set(ack.fullRequested || applyFailed)
        if (ack.snapshotStartedAt > 0) {
            val nowSec = System.currentTimeMillis() / 1000
            val watermark = if (ack.snapshotStartedAt > nowSec) nowSec else ack.snapshotStartedAt
            syncPreferences.lastPushedAt().set(watermark)
        }
        syncPreferences.sectionDigests().set(ack.sectionDigests)
        val pushed = snapshot.backupManga.associate { manga ->
            pushedMangaKey(manga.source, manga.url) to PushedManga(
                version = manga.version,
                maxChapterVersion = manga.chapters.maxOfOrNull { chapter -> chapter.version } ?: 0L,
                chapterCount = manga.chapters.size,
            )
        }
        val previous = if (ack.fullUpload) emptyMap() else decodePushedManga(syncPreferences.pushedManga().get())
        syncPreferences.pushedManga().set(encodePushedManga(previous + pushed))
        syncPreferences.categoryState.acknowledge(tombstonesSent)
    }
    // KMK <--

    /**
     * Retrieves all manga from the local database that must participate in sync:
     * favorites, previously favorited (unfavorite records), merged aggregates and
     * every referenced merged child.
     *
     * @return a list of all manga stored in the database
     */
    private suspend fun getAllMangaThatNeedsSync(): List<Manga> {
        return handler.awaitList { mangasQueries.getMangasWithFavoriteTimestamp(::mapManga) }
    }
}
