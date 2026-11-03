package dev.errnolink.tsuzuki.ui

import dev.errnolink.tsuzuki.ui.history.prepareHistoryUiModels
import dev.errnolink.tsuzuki.ui.updates.prepareUpdatesUiModels
import eu.kanade.presentation.history.HistoryUiModel
import eu.kanade.presentation.updates.UpdatesUiModel
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.ui.history.HistoryScreenModel
import eu.kanade.tachiyomi.ui.updates.UpdatesItem
import eu.kanade.tachiyomi.ui.updates.UpdatesScreenModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import tachiyomi.domain.history.model.HistoryWithRelations
import tachiyomi.domain.manga.interactor.GetCustomMangaInfo
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.manga.repository.CustomMangaRepository
import tachiyomi.domain.updates.model.UpdatesWithRelations
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

class UiModelGroupingEquivalenceTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            val repository: CustomMangaRepository = mockk(relaxed = true)
            every { repository.get(any()) } returns null
            Injekt.addSingleton(GetCustomMangaInfo(repository))
        }
    }

    private fun dateToMillis(year: Int, month: Int, day: Int): Long {
        return LocalDate.of(year, month, day)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    private fun UpdatesUiModel.toEquivalenceKey(): String = when (this) {
        is UpdatesUiModel.Header -> "Header($date, $mangaCount)"
        is UpdatesUiModel.Leader -> "Leader(${item.update.chapterId}, $isExpandable)"
        is UpdatesUiModel.Item -> "Item(${item.update.chapterId}, $isExpandable)"
    }

    @Test
    fun `prepareUpdatesUiModels matches state getUiModel output on fixture updates`() = runTest {
        val day1 = dateToMillis(2026, 10, 8)
        val day2 = dateToMillis(2026, 10, 9)

        val items = listOf(
            UpdatesItem(
                update = UpdatesWithRelations(
                    mangaId = 1L,
                    ogMangaTitle = "Manga One",
                    chapterId = 101L,
                    chapterName = "Ch. 1",
                    scanlator = null,
                    chapterUrl = "/m1/c1",
                    read = false,
                    bookmark = false,
                    lastPageRead = 0L,
                    sourceId = 1L,
                    dateFetch = day2,
                    coverData = MangaCover(1L, 1L, true, "", 0L),
                ),
                downloadStateProvider = { Download.State.NOT_DOWNLOADED },
                downloadProgressProvider = { 0 },
            ),
            UpdatesItem(
                update = UpdatesWithRelations(
                    mangaId = 1L,
                    ogMangaTitle = "Manga One",
                    chapterId = 102L,
                    chapterName = "Ch. 2",
                    scanlator = null,
                    chapterUrl = "/m1/c2",
                    read = false,
                    bookmark = false,
                    lastPageRead = 0L,
                    sourceId = 1L,
                    dateFetch = day2,
                    coverData = MangaCover(1L, 1L, true, "", 0L),
                ),
                downloadStateProvider = { Download.State.NOT_DOWNLOADED },
                downloadProgressProvider = { 0 },
            ),
            UpdatesItem(
                update = UpdatesWithRelations(
                    mangaId = 2L,
                    ogMangaTitle = "Manga Two",
                    chapterId = 201L,
                    chapterName = "Ch. 1",
                    scanlator = null,
                    chapterUrl = "/m2/c1",
                    read = false,
                    bookmark = false,
                    lastPageRead = 0L,
                    sourceId = 1L,
                    dateFetch = day1,
                    coverData = MangaCover(2L, 1L, true, "", 0L),
                ),
                downloadStateProvider = { Download.State.NOT_DOWNLOADED },
                downloadProgressProvider = { 0 },
            ),
        )

        val state = UpdatesScreenModel.State(isLoading = false, items = items.toPersistentList())

        val directModels: List<UpdatesUiModel> = state.getUiModel()
        val preparedModels: List<UpdatesUiModel> = prepareUpdatesUiModels(state)

        assertEquals(
            directModels.map { it.toEquivalenceKey() },
            preparedModels.map { it.toEquivalenceKey() },
        )
        assertEquals(5, preparedModels.size)
    }

    @Test
    fun `prepareHistoryUiModels matches state getUiModel output on fixture history`() = runTest {
        val date1 = Date(dateToMillis(2026, 10, 8))
        val date2 = Date(dateToMillis(2026, 10, 9))

        val list = listOf(
            HistoryWithRelations(
                id = 1L,
                chapterId = 101L,
                mangaId = 1L,
                ogTitle = "Manga One",
                chapterNumber = 1.0,
                read = true,
                lastPageRead = 20L,
                totalCountCalculated = 20L,
                readCountCalculated = 1L,
                readAt = date2,
                readDuration = 120L,
                coverData = MangaCover(1L, 1L, true, "", 0L),
            ),
            HistoryWithRelations(
                id = 2L,
                chapterId = 201L,
                mangaId = 2L,
                ogTitle = "Manga Two",
                chapterNumber = 2.0,
                read = true,
                lastPageRead = 15L,
                totalCountCalculated = 15L,
                readCountCalculated = 1L,
                readAt = date1,
                readDuration = 90L,
                coverData = MangaCover(2L, 1L, true, "", 0L),
            ),
        )

        val state = HistoryScreenModel.State(isLoading = false, list = list.toImmutableList())

        val directModels: List<HistoryUiModel> = state.getUiModel()
        val preparedModels: List<HistoryUiModel> = prepareHistoryUiModels(state)

        assertEquals(directModels, preparedModels)
        assertEquals(4, preparedModels.size)
    }
}
