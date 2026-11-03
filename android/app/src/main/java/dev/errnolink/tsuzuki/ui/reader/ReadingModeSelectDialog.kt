package dev.errnolink.tsuzuki.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.SheetDetent
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.manga.model.readingMode
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ReadingModeSelectDialog(
    onDismissRequest: () -> Unit,
    screenModel: ReaderSettingsScreenModel,
    onChange: (StringResource) -> Unit,
) {
    val manga by screenModel.mangaFlow.collectAsState()
    val current = ReadingMode.fromPreference(manga?.readingMode?.toInt())
    DetentSheet(true, "Reading mode", onDismissRequest, initialDetent = SheetDetent.FitContent) {
        ReadingMode.entries.forEach { mode ->
            GroupedRow(stringResource(mode.stringRes), value = if (mode == current) "Selected" else null, onClick = {
                screenModel.onChangeReadingMode(mode)
                onChange(mode.stringRes)
                onDismissRequest()
            })
        }
    }
}
