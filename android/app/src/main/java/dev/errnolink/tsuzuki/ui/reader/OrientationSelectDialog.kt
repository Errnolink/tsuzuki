package dev.errnolink.tsuzuki.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.manga.model.readerOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.i18n.MR

@Composable
fun OrientationSelectDialog(
    onDismissRequest: () -> Unit,
    screenModel: ReaderSettingsScreenModel,
    onChange: (StringResource) -> Unit,
) {
    val manga by screenModel.mangaFlow.collectAsState()
    val current = ReaderOrientation.fromPreference(manga?.readerOrientation?.toInt())
    DetentSheet(true, stringResource(MR.strings.rotation_type), onDismissRequest) {
        ReaderOrientation.entries.forEach { mode ->
            GroupedRow(stringResource(mode.stringRes), value = if (mode == current) stringResource(MR.strings.tsuzuki_selected) else null, onClick = {
                screenModel.onChangeOrientation(mode)
                onChange(mode.stringRes)
                onDismissRequest()
            })
        }
    }
}
