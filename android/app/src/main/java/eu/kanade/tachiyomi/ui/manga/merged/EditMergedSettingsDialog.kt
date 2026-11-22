package eu.kanade.tachiyomi.ui.manga.merged

import androidx.compose.runtime.Composable
import dev.errnolink.tsuzuki.ui.details.EditMergedSettingsSheet
import eu.kanade.tachiyomi.ui.manga.MergedMangaData
import tachiyomi.domain.manga.model.MergedMangaReference

@Composable
fun EditMergedSettingsDialog(
    onDismissRequest: () -> Unit,
    mergedData: MergedMangaData,
    onDeleteClick: (MergedMangaReference) -> Unit,
    onPositiveClick: (List<MergedMangaReference>) -> Unit,
    // KMK -->
    onOpenEntryClick: (MergedMangaReference) -> Unit,
    // KMK <--
) {
    EditMergedSettingsSheet(
        mergedData = mergedData,
        onDismissRequest = onDismissRequest,
        onDeleteClick = onDeleteClick,
        onPositiveClick = onPositiveClick,
        onOpenEntryClick = onOpenEntryClick,
    )
}
