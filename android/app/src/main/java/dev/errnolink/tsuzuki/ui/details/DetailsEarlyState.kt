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
import tachiyomi.domain.source.service.SourceManager

object DetailsEarlyState {
    private val readScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun initial(
        mangaId: Long,
        isFromSource: Boolean,
        smartSearched: Boolean,
        getManga: GetManga,
        sourceManager: SourceManager,
    ): MangaScreenModel.State {
        val read = readScope.async { getManga.await(mangaId) }
        val manga = awaitBounded(read, EARLY_READ_BUDGET_MS) ?: return MangaScreenModel.State.Loading
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
}
