package eu.kanade.presentation.more.settings.screen.about

import androidx.compose.runtime.Composable
import dev.errnolink.tsuzuki.ui.more.about.TsuzukiOpenSourceLicensesScreen
import eu.kanade.presentation.util.Screen

class OpenSourceLicensesScreen : Screen() {

    @Composable
    override fun Content() {
        TsuzukiOpenSourceLicensesScreen().Content()
    }
}
