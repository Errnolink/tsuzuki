package dev.errnolink.tsuzuki.data

import android.content.Context
import android.content.SharedPreferences

class DetailsFreshness(private val store: Store) {

    interface Store {
        fun get(mangaId: Long): Long
        fun put(mangaId: Long, at: Long)
    }

    fun chaptersDue(mangaId: Long, hasChapters: Boolean, lastUpdate: Long, now: Long = System.currentTimeMillis()): Boolean {
        if (!hasChapters) return true
        val newest = maxOf(lastUpdate, store.get(mangaId))
        return now - newest >= RECHECK_AFTER_MILLIS
    }

    fun markChecked(mangaId: Long, now: Long = System.currentTimeMillis()) {
        store.put(mangaId, now)
    }

    private class PrefsStore(private val prefs: SharedPreferences) : Store {
        override fun get(mangaId: Long) = prefs.getLong(mangaId.toString(), 0L)
        override fun put(mangaId: Long, at: Long) {
            prefs.edit().putLong(mangaId.toString(), at).apply()
        }
    }

    companion object {
        const val RECHECK_AFTER_MILLIS = 6 * 60 * 60_000L

        fun of(context: Context) = DetailsFreshness(PrefsStore(context.getSharedPreferences("details_checked", Context.MODE_PRIVATE)))
    }
}
