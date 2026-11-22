package eu.kanade.tachiyomi.ui.manga

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import dev.errnolink.tsuzuki.ui.details.EditMangaSheet
import tachiyomi.domain.manga.model.Manga

@Composable
fun EditMangaDialog(
    manga: Manga,
    // KMK -->
    coverRatio: MutableFloatState,
    // KMK <--
    onDismissRequest: () -> Unit,
    onPositiveClick: (
        title: String?,
        author: String?,
        artist: String?,
        thumbnailUrl: String?,
        description: String?,
        tags: List<String>?,
        status: Long?,
    ) -> Unit,
) {
    EditMangaSheet(
        manga = manga,
        coverRatio = coverRatio,
        onDismissRequest = onDismissRequest,
        onPositiveClick = onPositiveClick,
    )
}
