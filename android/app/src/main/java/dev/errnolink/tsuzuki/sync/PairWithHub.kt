package dev.errnolink.tsuzuki.sync

import android.content.Context
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.sync.SyncDataJob
import eu.kanade.tachiyomi.data.sync.SyncManager
import tachiyomi.core.common.preference.PreferenceStore
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** Preferences owned by the PC pairing flow. */
object PairingStore {
    private val preferenceStore by lazy { Injekt.get<PreferenceStore>() }

    /** Display name of the paired PC, empty while unpaired. */
    fun pairedPcName() = preferenceStore.getString("sync_paired_pc_name", "")
}

/**
 * Applies a scanned pair link: the phone becomes a SyncYomi client of the scanned PC.
 */
object PairWithHub {

    /**
     * Sets the sync service to SyncYomi with the scanned host and key, resets the sync
     * identity the same way the settings screen does (clearing the cursor and cached
     * capabilities), installs a 15-minute schedule when none is set, remembers the PC
     * name, and starts a manual sync immediately.
     */
    fun pair(context: Context, request: PairRequest) {
        val syncPreferences = Injekt.get<SyncPreferences>()
        syncPreferences.syncService().set(SyncManager.SyncService.SYNCYOMI.value)
        syncPreferences.clientHost().set(request.host)
        syncPreferences.clientAPIKey().set(request.key)
        syncPreferences.prepareSyncIdentity()

        if (syncPreferences.syncInterval().get() == 0) {
            syncPreferences.syncInterval().set(15)
        }
        SyncDataJob.setupTask(context)

        PairingStore.pairedPcName().set(request.name ?: "")
        SyncDataJob.startNow(context, manual = true)
    }
}
