// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): eu/kanade/tachiyomi/ui/reader/domain/ResolveChapterTransitionUiModelUseCase.kt
package dev.errnolink.tsuzuki.reader.engine.domain

import dev.errnolink.tsuzuki.reader.engine.model.ChapterTransition
import dev.errnolink.tsuzuki.reader.engine.model.ChapterTransitionUiModel
import dev.errnolink.tsuzuki.reader.engine.model.ReaderChapter
import dev.errnolink.tsuzuki.reader.engine.viewer.calculateChapterDifference
import dev.errnolink.tsuzuki.reader.engine.viewer.hasMissingChapters
import eu.kanade.tachiyomi.data.download.DownloadManager
import tachiyomi.domain.manga.model.Manga

/**
 * Domain interactor that prepares an immutable [ChapterTransitionUiModel] by evaluating chapter
 * difference / missing chapter count and download statuses via [DownloadManager].
 */
class ResolveChapterTransitionUiModelUseCase(private val downloadManager: DownloadManager) {

    operator fun invoke(
        transition: ChapterTransition,
        manga: Manga?,
    ): ChapterTransitionUiModel {
        val isFromDownloaded = manga?.let { isDownloaded(transition.from.chapter, it) } ?: false

        return when (transition) {
            is ChapterTransition.Prev -> {
                val to = transition.to
                val isToDownloaded =
                    if (to != null && manga != null) {
                        isDownloaded(to.chapter, manga)
                    } else {
                        false
                    }
                val diff =
                    if (to != null && hasMissingChapters(transition.from, to)) {
                        calculateChapterDifference(transition.from, to).toInt()
                    } else {
                        0
                    }
                val preloadState =
                    when (val state = to?.state) {
                        is ReaderChapter.State.Loading ->
                            ChapterTransitionUiModel.PreloadState.Loading
                        is ReaderChapter.State.Error ->
                            ChapterTransitionUiModel.PreloadState.Error(state.error.message ?: "")
                        else -> ChapterTransitionUiModel.PreloadState.Ready
                    }
                ChapterTransitionUiModel.Prev(
                    fromChapterName = transition.from.chapter.name,
                    isFromDownloaded = isFromDownloaded,
                    toChapter =
                        to?.let {
                            ChapterTransitionUiModel.TargetChapterInfo(
                                chapterId = it.chapter.id,
                                name = it.chapter.name,
                                isDownloaded = isToDownloaded,
                                preloadState = preloadState,
                            )
                        },
                    missingChaptersCount = diff,
                )
            }
            is ChapterTransition.Next -> {
                val to = transition.to
                val isToDownloaded =
                    if (to != null && manga != null) {
                        isDownloaded(to.chapter, manga)
                    } else {
                        false
                    }
                val diff =
                    if (to != null && hasMissingChapters(to, transition.from)) {
                        calculateChapterDifference(to, transition.from).toInt()
                    } else {
                        0
                    }
                val preloadState =
                    when (val state = to?.state) {
                        is ReaderChapter.State.Loading ->
                            ChapterTransitionUiModel.PreloadState.Loading
                        is ReaderChapter.State.Error ->
                            ChapterTransitionUiModel.PreloadState.Error(state.error.message ?: "")
                        else -> ChapterTransitionUiModel.PreloadState.Ready
                    }
                ChapterTransitionUiModel.Next(
                    fromChapterName = transition.from.chapter.name,
                    isFromDownloaded = isFromDownloaded,
                    toChapter =
                        to?.let {
                            ChapterTransitionUiModel.TargetChapterInfo(
                                chapterId = it.chapter.id,
                                name = it.chapter.name,
                                isDownloaded = isToDownloaded,
                                preloadState = preloadState,
                            )
                        },
                    missingChaptersCount = diff,
                )
            }
        }
    }

    private fun isDownloaded(
        chapter: tachiyomi.domain.chapter.model.Chapter,
        manga: Manga,
    ): Boolean {
        return downloadManager.isChapterDownloaded(
            chapterName = chapter.name,
            chapterScanlator = chapter.scanlator,
            chapterUrl = chapter.url,
            mangaTitle = manga.title,
            sourceId = manga.source,
        )
    }
}
