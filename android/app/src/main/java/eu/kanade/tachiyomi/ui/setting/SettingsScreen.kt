package eu.kanade.tachiyomi.ui.setting

import androidx.compose.runtime.Composable
import eu.kanade.presentation.util.Screen

class SettingsScreen(
    private val destination: Int? = null,
) : Screen() {

    constructor(destination: Destination) : this(destination.id)

    @Composable
    override fun Content() {
        dev.errnolink.tsuzuki.ui.settings.SettingsHost(destination)
    }

    sealed class Destination(val id: Int) {
        data object About : Destination(0)
        data object DataAndStorage : Destination(1)
        data object Tracking : Destination(2)
    }
}
