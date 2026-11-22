package dev.errnolink.tsuzuki.ui.details

/**
 * N12: the last values a title page resolved, kept per manga so the next open renders them on its
 * first frame — no `Continue` / `Chapters` placeholders that swap once the chapter data lands.
 * An in-memory LRU: titles revisit quickly, and one session's counts are worthless after it ends.
 */
internal object TitlePageMemory {

    internal class Known(val chapterCount: Int, val continueLabel: String?, val continueEnabled: Boolean)

    private const val Capacity = 128

    private val map = LinkedHashMap<Long, Known>()

    fun recall(mangaId: Long): Known? = synchronized(map) { map[mangaId] }

    fun remember(mangaId: Long, chapterCount: Int, continueLabel: String?, continueEnabled: Boolean) {
        synchronized(map) {
            map.remove(mangaId)
            map[mangaId] = Known(chapterCount, continueLabel, continueEnabled)
            while (map.size > Capacity) map.remove(map.keys.first())
        }
    }
}
