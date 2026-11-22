package dev.errnolink.tsuzuki.ui.details

import eu.kanade.tachiyomi.ui.manga.MangaScreenModel
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager

/**
 * The title page's first state. The page header must be real on the first frame of the zoom (no
 * skeleton in flight), so the manga comes from what the app already knows: the tile that was
 * tapped primes it, a title opened before keeps it, and only a cold open reads the database within
 * a frame's budget. The model's own subscription replaces it right after.
 */
object DetailsEarlyState {
    private val readScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val known = object : LinkedHashMap<Long, Manga>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Manga>?): Boolean = size > KNOWN_CAPACITY
    }

    /** A tile about to open its title hands over the manga it shows. */
    fun prime(manga: Manga) {
        synchronized(known) { known[manga.id] = manga }
    }

    internal fun knownManga(mangaId: Long): Manga? = synchronized(known) { known[mangaId] }

    fun initial(
        mangaId: Long,
        isFromSource: Boolean,
        smartSearched: Boolean,
        getManga: GetManga,
        sourceManager: SourceManager,
    ): MangaScreenModel.State {
        val manga = knownManga(mangaId) ?: run {
            val read = readScope.async { getManga.await(mangaId)?.also(::prime) }
            awaitBounded(read, EARLY_READ_BUDGET_MS)
        } ?: return MangaScreenModel.State.Loading
        return MangaScreenModel.State.Success(
            manga = manga,
            source = sourceManager.getOrStub(manga.source),
            isFromSource = isFromSource,
            chapters = emptyList(),
            availableScanlators = persistentSetOf(),
            excludedScanlators = persistentSetOf(),
            chaptersPending = true,
            hideMissingChapters = false,
            showRecommendationsInOverflow = false,
            showMergeInOverflow = false,
            showMergeWithAnother = smartSearched,
            mergedData = null,
            meta = null,
            alwaysShowReadingProgress = false,
        )
    }

    internal fun <T> awaitBounded(read: Deferred<T?>, budgetMs: Long): T? =
        runCatching { runBlocking { withTimeoutOrNull(budgetMs) { read.await() } } }.getOrNull()

    private const val EARLY_READ_BUDGET_MS = 24L
    private const val KNOWN_CAPACITY = 64
}
