package eu.kanade.presentation.more.settings.screen.debug

import androidx.compose.runtime.Composable
import dev.errnolink.tsuzuki.ui.settings.debug.TsuzukiBackupSchemaScreen
import eu.kanade.presentation.util.Screen

class BackupSchemaScreen : Screen() {

    @Composable
    override fun Content() {
        TsuzukiBackupSchemaScreen().Content()
    }

    companion object {
        const val TITLE = "Backup file schema"
    }
}
