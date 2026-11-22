package dev.errnolink.tsuzuki.ui.reader

import androidx.compose.runtime.Composable
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel

@Composable
fun ReaderSettingsDialog(
    visible: Boolean = true,
    onDismissRequest: () -> Unit,
    onShowMenus: () -> Unit,
    onHideMenus: () -> Unit,
    screenModel: ReaderSettingsScreenModel,
) {
    ReaderSettingsSheetCurrent(visible, onDismissRequest, onShowMenus, onHideMenus, screenModel)
}
