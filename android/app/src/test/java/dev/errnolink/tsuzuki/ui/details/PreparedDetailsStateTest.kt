package dev.errnolink.tsuzuki.ui.details

import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.ui.manga.MangaScreenModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.model.StubSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
class PreparedDetailsStateTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            val basePreferences = mockk<BasePreferences>()
            every { basePreferences.downloadedOnly().get() } returns false
            Injekt.addSingleton(basePreferences)
        }
    }

    private fun sampleSuccessState(): MangaScreenModel.State.Success {
        val manga = Manga.create().copy(id = 42L, ogTitle = "Cached Manga")
        val source = StubSource(id = 1L, lang = "en", name = "Stub")
        return MangaScreenModel.State.Success(
            manga = manga,
            source = source,
            isFromSource = false,
            chapters = emptyList(),
            availableScanlators = persistentSetOf(),
            excludedScanlators = persistentSetOf(),
            meta = null,
            mergedData = null,
            showRecommendationsInOverflow = false,
            showMergeInOverflow = false,
            showMergeWithAnother = false,
            alwaysShowReadingProgress = false,
        )
    }

    @Test
    fun `initialPreparedDetailsState retains cached success state on first frame`() {
        val success = sampleSuccessState()
        val flow = MutableStateFlow<MangaScreenModel.State>(success)

        val initial = initialPreparedDetailsState(flow.value)

        assertSame(success, initial)
        assertTrue(initial is MangaScreenModel.State.Success)
        assertEquals(42L, (initial as MangaScreenModel.State.Success).manga.id)
    }

    @Test
    fun `initialPreparedDetailsState retains loading when unset`() {
        val flow = MutableStateFlow<MangaScreenModel.State>(MangaScreenModel.State.Loading)

        val initial = initialPreparedDetailsState(flow.value)

        assertSame(MangaScreenModel.State.Loading, initial)
    }

    @Test
    fun `prepareDetailsFlow emits success state without altering identity`() = runTest {
        val success = sampleSuccessState()
        val flow = MutableStateFlow<MangaScreenModel.State>(success)

        val emitted = prepareDetailsFlow(flow).first()

        assertSame(success, emitted)
    }
}
