package dev.errnolink.tsuzuki.ui.updates

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.ui.shell.ShellFilterRow
import eu.kanade.tachiyomi.ui.updates.UpdatesSettingsScreenModel
import tachiyomi.domain.updates.service.UpdatesPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

@Composable
fun UpdatesFilterDialog(onDismissRequest: () -> Unit, screenModel: UpdatesSettingsScreenModel) {
    val downloaded by screenModel.updatesPreferences.filterDownloaded().collectAsState()
    val unread by screenModel.updatesPreferences.filterUnread().collectAsState()
    val started by screenModel.updatesPreferences.filterStarted().collectAsState()
    val bookmarked by screenModel.updatesPreferences.filterBookmarked().collectAsState()
    val excluded by screenModel.updatesPreferences.filterExcludedScanlators().collectAsState()
    val panorama by screenModel.updatesPreferences.usePanoramaCover().collectAsState()
    DetentSheet(true, stringResource(MR.strings.action_filter), onDismissRequest) {
        ShellFilterRow(stringResource(MR.strings.label_downloaded), downloaded) {
            screenModel.toggleFilter(UpdatesPreferences::filterDownloaded)
        }
        ShellFilterRow(stringResource(MR.strings.action_filter_unread), unread) {
            screenModel.toggleFilter(UpdatesPreferences::filterUnread)
        }
        ShellFilterRow(stringResource(MR.strings.label_started), started) {
            screenModel.toggleFilter(UpdatesPreferences::filterStarted)
        }
        ShellFilterRow(stringResource(MR.strings.action_filter_bookmarked), bookmarked) {
            screenModel.toggleFilter(UpdatesPreferences::filterBookmarked)
        }
        GroupedRow(
            title = stringResource(MR.strings.action_filter_excluded_scanlators),
            checked = excluded,
            onCheckedChange = { screenModel.toggleSwitch(UpdatesPreferences::filterExcludedScanlators) },
        )
        GroupedRow(
            title = stringResource(KMR.strings.action_panorama_cover),
            checked = panorama,
            onCheckedChange = { screenModel.toggleSwitch(UpdatesPreferences::usePanoramaCover) },
            divider = false,
        )
    }
}
