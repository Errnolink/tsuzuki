package mihon.feature.migration.list.components

import androidx.compose.runtime.Composable
import dev.errnolink.tsuzuki.ui.migration.MigrationProgressSheet

@Composable
fun MigrationProgressDialog(
    progress: Float,
    exitMigration: () -> Unit,
) {
    MigrationProgressSheet(
        progress = progress,
        exitMigration = exitMigration,
    )
}
