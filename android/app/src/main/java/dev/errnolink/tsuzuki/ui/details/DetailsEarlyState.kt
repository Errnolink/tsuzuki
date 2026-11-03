package dev.errnolink.tsuzuki.ui.details

import eu.kanade.tachiyomi.ui.manga.MangaScreenModel
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.source.service.SourceManager

object DetailsEarlyState {
    fun initial(
        mangaId: Long,
        isFromSource: Boolean,
        smartSearched: Boolean,
        getManga: GetManga,
        sourceManager: SourceManager,
    ): MangaScreenModel.State {
        val manga = runCatching { runBlocking { withTimeoutOrNull(EARLY_READ_BUDGET_MS) { getManga.await(mangaId) } } }.getOrNull()
            ?: return MangaScreenModel.State.Loading
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

    private const val EARLY_READ_BUDGET_MS = 24L
}
