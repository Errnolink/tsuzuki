// Kotatsu backup -> the app's own Backup model, so the normal restore pipeline
// (categories, chapter read state, history, stub sources) does the work.
// Format: docs/kotatsu-backup.md. Approach follows galpt/mk-bkconv (MIT).
package dev.errnolink.tsuzuki.backup.kotatsu

import dev.errnolink.tsuzuki.mangadex.backup.NekoBackupMapper.SourceIdentity
import dev.errnolink.tsuzuki.backup.kotatsu.KotatsuSourceResolver.MANGADEX_PARSER
import exh.md.utils.MdUtil
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupSource
import java.net.URI

/** A chapter of a title, in the app's own url form (`/chapter/<id>` for MangaDex). */
data class KotatsuChapter(
    val url: String,
    val name: String,
    val chapterNumber: Double,
    val scanlator: String? = null,
    val dateUpload: Long = 0,
)

data class KotatsuConversion(
    val backup: Backup,
    /** Kotatsu parser name -> titles whose source has no installed match. */
    val unmatchedSources: Map<String, Int>,
    val warnings: List<String>,
) {
    val unmatchedTitles: Int get() = unmatchedSources.values.sum()
}

/**
 * Converts a parsed Kotatsu backup into a [Backup]. Pure: source identities and
 * chapter lists are supplied by the caller (see [KotatsuRestoreSupport] for the
 * on-device wiring), which keeps the mapping testable.
 *
 * @param resolveChapters returns the chapter list of one MangaDex title (empty
 * when unavailable); only called for MangaDex titles that have history or
 * bookmarks, because progress can only be attached through real chapters.
 */
class KotatsuBackupConverter(
    private val sources: List<SourceIdentity>,
    private val mangaDex: SourceIdentity?,
    private val resolveChapters: suspend (manga: KotatsuManga) -> List<KotatsuChapter> = { emptyList() },
) {

    /** A chapter is finished when Kotatsu recorded full progress for it. */
    private val finishedPercent = 0.99f

    suspend fun convert(backup: KotatsuBackup): KotatsuConversion {
        val warnings = mutableListOf<String>()

        val categories = categoryOrders(backup)
        val backupCategories = categories.map { (category, order) ->
            BackupCategory(name = category.title, order = order, id = category.categoryId)
        }

        val entries = collectManga(backup)

        val unmatched = mutableMapOf<String, Int>()
        val usedSources = LinkedHashMap<Long, BackupSource>()
        val progressByUnresolvedSource = mutableMapOf<String, Int>()

        val backupManga = entries.map { entry ->
            val manga = entry.manga
            val match = KotatsuSourceResolver.resolve(manga.source, manga.publicUrl, sources, mangaDex)
            usedSources.getOrPut(match.id) { BackupSource(name = match.name, sourceId = match.id) }
            if (match.unmatched) {
                unmatched[match.name] = (unmatched[match.name] ?: 0) + 1
            }

            val chapters = ArrayList<BackupChapter>()
            val history = ArrayList<BackupHistory>()
            val isMangaDex = manga.source.equals(MANGADEX_PARSER, ignoreCase = true) && mangaDex != null
            val hasProgress = entry.history.isNotEmpty() || entry.bookmarks.isNotEmpty()
            if (isMangaDex && hasProgress) {
                buildMangaDexProgress(manga, entry, resolveChapters(manga), history, chapters, warnings)
            } else if (hasProgress) {
                progressByUnresolvedSource[match.name] = (progressByUnresolvedSource[match.name] ?: 0) + 1
            }

            BackupManga(
                source = match.id,
                url = mangaUrl(manga, match),
                title = manga.title,
                artist = manga.author,
                author = manga.author,
                status = statusOf(manga.state),
                thumbnailUrl = manga.coverUrl ?: manga.largeCoverUrl,
                favorite = entry.favorite,
                dateAdded = entry.createdAt,
                lastModifiedAt = maxOf(entry.createdAt, entry.history.maxOfOrNull { it.updatedAt } ?: 0L),
                categories = entry.categories.mapNotNull { orderOf(categories, it) },
                chapters = chapters,
                history = history,
                version = 1,
                initialized = true,
            )
        }

        unmatched.forEach { (name, count) ->
            warnings.add(
                "Kotatsu source $name: $count title(s) restored without their extension. Install the extension and restore again to read them.",
            )
        }
        progressByUnresolvedSource.forEach { (name, count) ->
            warnings.add(
                "Kotatsu source $name: chapter urls are not stored in the backup, so reading progress for $count title(s) was not restored.",
            )
        }

        return KotatsuConversion(
            backup = Backup(
                backupManga = backupManga,
                backupCategories = backupCategories,
                backupSources = usedSources.values.toList(),
            ),
            unmatchedSources = unmatched,
            warnings = warnings,
        )
    }

    /** Categories in Kotatsu's sort order; the list position is the backup order. */
    private fun categoryOrders(backup: KotatsuBackup): List<Pair<KotatsuCategory, Long>> {
        val seen = HashSet<String>()
        return backup.categories
            .sortedBy { it.sortKey }
            .filter { it.title.isNotBlank() && seen.add(it.title.lowercase()) }
            .mapIndexed { index, category -> category to index.toLong() }
    }

    private fun orderOf(categories: List<Pair<KotatsuCategory, Long>>, categoryId: Long): Long? =
        categories.firstOrNull { it.first.categoryId == categoryId }?.second

    private class Entry(
        val manga: KotatsuManga,
        val history: MutableList<KotatsuHistoryEntry> = mutableListOf(),
        val bookmarks: MutableSet<Long> = mutableSetOf(),
        var favorite: Boolean = false,
        var createdAt: Long = 0,
        val categories: MutableSet<Long> = mutableSetOf(),
    )

    /** Joins favourites, history and bookmarks on the Kotatsu manga id. */
    private fun collectManga(backup: KotatsuBackup): List<Entry> {
        val byId = LinkedHashMap<Long, Entry>()
        fun entry(manga: KotatsuManga) = byId.getOrPut(manga.id) { Entry(manga) }

        for (favourite in backup.favourites) {
            val e = entry(favourite.manga)
            e.favorite = true
            e.categories += favourite.categoryId
            if (favourite.createdAt > e.createdAt) e.createdAt = favourite.createdAt
        }
        for (row in backup.history) {
            val e = entry(row.manga)
            e.history += row
            if (!e.favorite && row.createdAt > 0 && (e.createdAt == 0L || row.createdAt < e.createdAt)) {
                e.createdAt = row.createdAt
            }
        }
        for (group in backup.bookmarks) {
            val e = entry(group.manga)
            group.bookmarks.forEach { e.bookmarks += it.chapterId }
        }
        return byId.values.filter { it.manga.url.isNotBlank() || it.manga.publicUrl.isNotBlank() }
    }

    /**
     * Attaches Kotatsu progress to real MangaDex chapters: every chapter of the
     * title is hashed back to its Kotatsu id, the latest history row is the
     * current position, and — like Kotatsu's own details screen — everything
     * before the current chapter counts as read, alternate scanlator versions
     * of earlier numbers included.
     */
    private fun buildMangaDexProgress(
        manga: KotatsuManga,
        entry: Entry,
        chapters: List<KotatsuChapter>,
        history: MutableList<BackupHistory>,
        backupChapters: MutableList<BackupChapter>,
        warnings: MutableList<String>,
    ) {
        if (chapters.isEmpty()) {
            warnings.add("${manga.title}: reading progress was not restored (the MangaDex chapter list was unavailable).")
            return
        }
        val byKotatsuId = HashMap<Long, KotatsuChapter>(chapters.size)
        for (chapter in chapters) {
            byKotatsuId[KotatsuIds.mangaId(MANGADEX_PARSER, MdUtil.getChapterId(chapter.url))] = chapter
        }
        val readState = HashMap<String, BackupChapter>()
        val order = chapters.withIndex().associate { (index, chapter) -> chapter.url to index.toLong() }

        fun chapterOf(kotatsuId: Long): KotatsuChapter? = byKotatsuId[kotatsuId]

        for (bookmarkId in entry.bookmarks) {
            chapterOf(bookmarkId)?.let { chapter ->
                readState[chapter.url] = (readState[chapter.url] ?: baseChapter(chapter, order)).copy(bookmark = true)
            }
        }
        val rows = entry.history.sortedByDescending { it.updatedAt }
        val resolvedRows = rows.mapNotNull { row -> chapterOf(row.chapterId)?.let { row to it } }
        resolvedRows.forEachIndexed { index, (row, chapter) ->
            val finished = index == 0 && row.percent >= finishedPercent
            readState[chapter.url] = (readState[chapter.url] ?: baseChapter(chapter, order)).copy(
                read = finished || (readState[chapter.url]?.read ?: false) || index > 0,
                lastPageRead = maxOf(readState[chapter.url]?.lastPageRead ?: 0L, row.page.toLong()),
            )
            history += BackupHistory(url = chapter.url, lastRead = row.updatedAt, readDuration = 0)
        }

        // Kotatsu only stores the current chapter; every chapter before it was passed on the way
        // there, so it restores as read. "Before" compares chapter numbers when the current one
        // has a number (prologues at 0 count, unnumbered extras do not); otherwise the source's
        // own order, which runs newest first (a larger index is an earlier chapter).
        val currentChapter = resolvedRows.firstOrNull()?.second
        if (currentChapter != null) {
            val currentNumber = currentChapter.chapterNumber
            val currentIndex = order[currentChapter.url] ?: 0L
            chapters.forEach { chapter ->
                val before = if (currentNumber > 0) {
                    chapter.chapterNumber >= 0 && chapter.chapterNumber < currentNumber
                } else {
                    (order[chapter.url] ?: 0L) > currentIndex
                }
                if (before) {
                    readState[chapter.url] = (readState[chapter.url] ?: baseChapter(chapter, order)).copy(read = true)
                }
            }
        }

        val unresolved = entry.history.count { chapterOf(it.chapterId) == null } +
            entry.bookmarks.count { chapterOf(it) == null }
        if (unresolved > 0) {
            warnings.add("${manga.title}: $unresolved history/bookmark entries did not match a MangaDex chapter and were skipped.")
        }

        // The whole chapter list is emitted so the restored title is browsable;
        // read state only exists for chapters the backup actually referenced.
        chapters.forEach { chapter ->
            backupChapters += readState[chapter.url] ?: baseChapter(chapter, order)
        }
    }

    private fun baseChapter(chapter: KotatsuChapter, order: Map<String, Long>) = BackupChapter(
        url = chapter.url,
        name = chapter.name,
        scanlator = chapter.scanlator,
        chapterNumber = chapter.chapterNumber.toFloat(),
        dateUpload = chapter.dateUpload,
        sourceOrder = order[chapter.url] ?: 0,
        version = 1,
    )

    /** Kotatsu state string -> Mihon [SManga] status. */
    private fun statusOf(state: String?): Int = when (state?.uppercase()) {
        "ONGOING", "UPCOMING" -> 1
        "FINISHED" -> 2
        "ABANDONED" -> 5
        "PAUSED" -> 6
        else -> 0
    }

    /**
     * MangaDex titles keep the app's own url form (`/manga/<uuid>`); other
     * sources keep Kotatsu's site-relative url, stripped of the source's host
     * when it carries one (the Neko mapper's rule).
     */
    private fun mangaUrl(manga: KotatsuManga, match: KotatsuSourceMatch): String {
        if (manga.source.equals(MANGADEX_PARSER, ignoreCase = true)) {
            val uuid = MdUtil.getMangaId(manga.url.ifBlank { manga.publicUrl })
            if (uuid.isNotBlank()) return MdUtil.buildMangaUrl(uuid)
        }
        val baseUrl = sources.firstOrNull { it.id == match.id }?.baseUrl
        return relativeUrl(manga.url, baseUrl)
    }

    private fun relativeUrl(url: String, baseUrl: String?): String {
        if (baseUrl == null) return url
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return url
        val base = runCatching { URI(baseUrl).host?.lowercase() }.getOrNull() ?: return url
        if (host != base) return url
        return URI(url).let { it.rawPath + (it.rawQuery?.let { q -> "?$q" } ?: "") }
    }
}
