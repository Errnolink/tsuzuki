package dev.errnolink.tsuzuki.data

import dev.errnolink.tsuzuki.preference.TsuzukiPreferences
import eu.kanade.tachiyomi.BuildConfig
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.DatabaseHandler

class DbNullRepair(
    private val handler: DatabaseHandler,
    private val tsuzukiPreferences: TsuzukiPreferences,
) {

    suspend fun run() {
        if (tsuzukiPreferences.nullRepairLastVersionCode().get() >= BuildConfig.VERSION_CODE) return
        runCatching {
            handler.await(inTransaction = true) {
                val repairQueries = tsuzuki_db_null_repairQueries
                repairQueries.deleteOrphanedChapters()
                val orphaned = repairQueries.selectNullRepairCount().executeAsOne()
                repairQueries.repairNullChapterColumns()
                val chapters = repairQueries.selectNullRepairCount().executeAsOne()
                repairQueries.repairNullMangaColumns()
                val mangas = repairQueries.selectNullRepairCount().executeAsOne()
                if (orphaned > 0 || chapters > 0 || mangas > 0) {
                    logcat(LogPriority.WARN) {
                        "NULL repair: $orphaned orphaned chapter row(s) deleted, " +
                            "$chapters chapter row(s) and $mangas manga row(s) normalized"
                    }
                }
            }
        }.onSuccess {
            tsuzukiPreferences.nullRepairLastVersionCode().set(BuildConfig.VERSION_CODE)
        }.onFailure {
            logcat(LogPriority.ERROR, it) { "Startup NULL repair failed" }
        }
    }
}
