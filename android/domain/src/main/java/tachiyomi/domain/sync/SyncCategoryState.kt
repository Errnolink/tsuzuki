package tachiyomi.domain.sync

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class SyncCategoryState(private val preferenceStore: PreferenceStore) {
    private fun pending() = preferenceStore.getStringSet(
        Preference.appStateKey("sync_v2_deleted_categories"),
        emptySet(),
    )

    fun deleted(): Set<String> = synchronized(lock) { pending().get().toSet() }

    fun remember(uid: Long) = synchronized(lock) {
        if (uid != 0L) pending().set(pending().get() + uid.toString())
    }

    fun acknowledge(sent: Set<String>) = synchronized(lock) {
        pending().set(pending().get() - sent)
    }

    fun reset() = synchronized(lock) { pending().delete() }

    private companion object {
        val lock = Any()
    }
}
