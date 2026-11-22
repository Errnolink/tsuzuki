package mihon.feature.migration.list.components

import androidx.compose.runtime.Composable
import dev.errnolink.tsuzuki.ui.migration.MigrationExitSheet

@Composable
fun MigrationExitDialog(
    onDismissRequest: () -> Unit,
    exitMigration: () -> Unit,
) {
    MigrationExitSheet(
        onDismissRequest = onDismissRequest,
        exitMigration = exitMigration,
    )
}
