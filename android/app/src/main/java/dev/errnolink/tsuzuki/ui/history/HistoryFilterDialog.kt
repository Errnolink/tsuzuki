package dev.errnolink.tsuzuki.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.ui.shell.ShellFilterRow
import eu.kanade.tachiyomi.ui.history.HistorySettingsScreenModel
import tachiyomi.domain.history.service.HistoryPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

@Composable
fun HistoryFilterDialog(onDismissRequest: () -> Unit, screenModel: HistorySettingsScreenModel) {
    val manga by screenModel.historyPreferences.filterUnfinishedManga().collectAsState()
    val chapter by screenModel.historyPreferences.filterUnfinishedChapter().collectAsState()
    val nonLibrary by screenModel.historyPreferences.filterNonLibraryManga().collectAsState()
    val panorama by screenModel.historyPreferences.usePanoramaCover().collectAsState()
    DetentSheet(true, stringResource(MR.strings.action_filter), onDismissRequest) {
        ShellFilterRow(stringResource(KMR.strings.action_filter_unfinished_manga), manga) {
            screenModel.toggleFilter(HistoryPreferences::filterUnfinishedManga)
        }
        ShellFilterRow(stringResource(KMR.strings.action_filter_unfinished_chapter), chapter) {
            screenModel.toggleFilter(HistoryPreferences::filterUnfinishedChapter)
        }
        ShellFilterRow(stringResource(KMR.strings.action_filter_non_library_entries), nonLibrary) {
            screenModel.toggleFilter(HistoryPreferences::filterNonLibraryManga)
        }
        GroupedRow(
            title = stringResource(KMR.strings.action_panorama_cover),
            checked = panorama,
            onCheckedChange = { screenModel.toggleSwitch(HistoryPreferences::usePanoramaCover) },
            divider = false,
        )
    }
}
