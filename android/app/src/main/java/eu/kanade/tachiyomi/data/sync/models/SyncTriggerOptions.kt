package eu.kanade.tachiyomi.data.sync.models

import dev.icerock.moko.resources.StringResource
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.sy.SYMR

data class SyncTriggerOptions(
    val syncOnChapterRead: Boolean = true,
    val syncOnChapterOpen: Boolean = true,
    val syncOnAppStart: Boolean = true,
    val syncOnAppResume: Boolean = true,
    // KMK -->
    val syncOnReaderPause: Boolean = true,
    val syncOnReaderExit: Boolean = true,
    val syncOnPageChange: Boolean = true,
    // KMK <--
) {
    fun asBooleanArray() = booleanArrayOf(
        syncOnChapterRead,
        syncOnChapterOpen,
        syncOnAppStart,
        syncOnAppResume,
        // KMK -->
        syncOnReaderPause,
        syncOnReaderExit,
        syncOnPageChange,
        // KMK <--
    )

    fun anyEnabled() = syncOnChapterRead ||
        syncOnChapterOpen ||
        syncOnAppStart ||
        syncOnAppResume ||
        // KMK -->
        syncOnReaderPause ||
        syncOnReaderExit ||
        syncOnPageChange
        // KMK <--


    companion object {
        val mainOptions = persistentListOf(
            Entry(
                label = SYMR.strings.sync_on_chapter_read,
                getter = SyncTriggerOptions::syncOnChapterRead,
                setter = { options, enabled -> options.copy(syncOnChapterRead = enabled) },
            ),
            Entry(
                label = SYMR.strings.sync_on_chapter_open,
                getter = SyncTriggerOptions::syncOnChapterOpen,
                setter = { options, enabled -> options.copy(syncOnChapterOpen = enabled) },
            ),
            Entry(
                label = SYMR.strings.sync_on_app_start,
                getter = SyncTriggerOptions::syncOnAppStart,
                setter = { options, enabled -> options.copy(syncOnAppStart = enabled) },
            ),
            Entry(
                label = SYMR.strings.sync_on_app_resume,
                getter = SyncTriggerOptions::syncOnAppResume,
                setter = { options, enabled -> options.copy(syncOnAppResume = enabled) },
            ),
            // KMK -->
            Entry(
                label = SYMR.strings.sync_on_reader_pause,
                getter = SyncTriggerOptions::syncOnReaderPause,
                setter = { options, enabled -> options.copy(syncOnReaderPause = enabled) },
            ),
            Entry(
                label = SYMR.strings.sync_on_reader_exit,
                getter = SyncTriggerOptions::syncOnReaderExit,
                setter = { options, enabled -> options.copy(syncOnReaderExit = enabled) },
            ),
            Entry(
                label = SYMR.strings.sync_on_page_change,
                getter = SyncTriggerOptions::syncOnPageChange,
                setter = { options, enabled -> options.copy(syncOnPageChange = enabled) },
            ),
            // KMK <--
        )

        fun fromBooleanArray(array: BooleanArray): SyncTriggerOptions {
            val options = SyncTriggerOptions(
                syncOnChapterRead = array[0],
                syncOnChapterOpen = array[1],
                syncOnAppStart = array[2],
                syncOnAppResume = array[3],
            )
            // KMK -->
            if (array.size < 7) return options
            return options.copy(
                syncOnReaderPause = array[4],
                syncOnReaderExit = array[5],
                syncOnPageChange = array[6],
            )
            // KMK <--
        }
    }

    data class Entry(
        val label: StringResource,
        val getter: (SyncTriggerOptions) -> Boolean,
        val setter: (SyncTriggerOptions, Boolean) -> SyncTriggerOptions,
        val enabled: (SyncTriggerOptions) -> Boolean = { true },
    )
}
