package dev.errnolink.tsuzuki.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.SegmentedControl
import dev.errnolink.tsuzuki.designsystem.SheetDetent
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel

@Composable
fun ReaderSettingsDialog(
    onDismissRequest: () -> Unit,
    onShowMenus: () -> Unit,
    onHideMenus: () -> Unit,
    screenModel: ReaderSettingsScreenModel,
) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    DetentSheet(true, "Reading settings", { onDismissRequest(); onShowMenus() }, initialDetent = SheetDetent.Large) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        LaunchedEffect(page) {
            window?.setDimAmount(if (page == 2) 0f else 0.32f)
            if (page == 2) onHideMenus() else onShowMenus()
        }
        SegmentedControl(listOf("Reading", "Display", "Filters"), page, { page = it })
        when (page) {
            0 -> ReadingModePage(screenModel)
            1 -> GeneralPage(screenModel)
            2 -> ColorFilterPage(screenModel)
        }
    }
}
