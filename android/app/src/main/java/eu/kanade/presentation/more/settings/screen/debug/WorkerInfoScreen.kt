package eu.kanade.presentation.more.settings.screen.debug

import androidx.compose.runtime.Composable
import dev.errnolink.tsuzuki.ui.settings.debug.TsuzukiWorkerInfoScreen
import eu.kanade.presentation.util.Screen

class WorkerInfoScreen : Screen() {

    @Composable
    override fun Content() {
        TsuzukiWorkerInfoScreen().Content()
    }

    companion object {
        const val TITLE = "Worker info"
    }
}
