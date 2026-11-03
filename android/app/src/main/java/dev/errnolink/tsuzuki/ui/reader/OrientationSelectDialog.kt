package dev.errnolink.tsuzuki.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.SheetDetent
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.manga.model.readerOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun OrientationSelectDialog(
    onDismissRequest: () -> Unit,
    screenModel: ReaderSettingsScreenModel,
    onChange: (StringResource) -> Unit,
) {
    val manga by screenModel.mangaFlow.collectAsState()
    val current = ReaderOrientation.fromPreference(manga?.readerOrientation?.toInt())
    DetentSheet(true, "Orientation", onDismissRequest, initialDetent = SheetDetent.FitContent) {
        ReaderOrientation.entries.forEach { mode ->
            GroupedRow(stringResource(mode.stringRes), value = if (mode == current) "Selected" else null, onClick = {
                screenModel.onChangeOrientation(mode)
                onChange(mode.stringRes)
                onDismissRequest()
            })
        }
    }
}
