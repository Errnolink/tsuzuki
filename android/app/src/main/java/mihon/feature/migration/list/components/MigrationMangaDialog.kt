package mihon.feature.migration.list.components

import androidx.compose.runtime.Composable
import dev.errnolink.tsuzuki.ui.migration.MigrationMangaSheet

@Composable
fun MigrationMangaDialog(
    onDismissRequest: () -> Unit,
    copy: Boolean,
    totalCount: Int,
    skippedCount: Int,
    onMigrate: () -> Unit,
) {
    MigrationMangaSheet(
        onDismissRequest = onDismissRequest,
        copy = copy,
        totalCount = totalCount,
        skippedCount = skippedCount,
        onMigrate = onMigrate,
    )
}
