package eu.kanade.presentation.more.settings.screen.about

import androidx.compose.runtime.Composable
import dev.errnolink.tsuzuki.ui.more.about.TsuzukiOpenSourceLibraryLicenseScreen
import eu.kanade.presentation.util.Screen

class OpenSourceLibraryLicenseScreen(
    private val name: String,
    private val website: String?,
    private val license: String,
) : Screen() {

    @Composable
    override fun Content() {
        TsuzukiOpenSourceLibraryLicenseScreen(name, website, license).Content()
    }
}
