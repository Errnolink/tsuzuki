package dev.errnolink.tsuzuki.data

import android.content.Context
import android.content.SharedPreferences

class SmartUpdate(private val store: DetailsFreshness.Store) {

    fun isDue(mangaId: Long, nextUpdate: Long, now: Long = System.currentTimeMillis()): Boolean {
        val checkedAt = store.get(mangaId)
        return when {
            nextUpdate <= 0L -> now - checkedAt >= UNPREDICTED_RECHECK_MILLIS
            now < nextUpdate -> false
            checkedAt < nextUpdate -> true
            else -> now - checkedAt >= LATE_RECHECK_MILLIS
        }
    }

    fun markChecked(mangaId: Long, now: Long = System.currentTimeMillis()) {
        store.put(mangaId, now)
    }

    private class PrefsStore(private val prefs: SharedPreferences) : DetailsFreshness.Store {
        override fun get(mangaId: Long) = prefs.getLong(mangaId.toString(), 0L)
        override fun put(mangaId: Long, at: Long) {
            prefs.edit().putLong(mangaId.toString(), at).apply()
        }
    }

    companion object {
        const val RUN_EVERY_HOURS = 6
        const val LATE_RECHECK_MILLIS = 12 * 60 * 60_000L
        const val UNPREDICTED_RECHECK_MILLIS = 7 * 24 * 60 * 60_000L

        fun of(context: Context) = SmartUpdate(PrefsStore(context.getSharedPreferences("smart_update_checked", Context.MODE_PRIVATE)))
    }
}
