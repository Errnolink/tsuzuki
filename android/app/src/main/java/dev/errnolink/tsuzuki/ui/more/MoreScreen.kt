package dev.errnolink.tsuzuki.ui.more

import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.InsetGroupedList
import dev.errnolink.tsuzuki.designsystem.ArrowDownUp
import dev.errnolink.tsuzuki.designsystem.ArrowLeftRight
import dev.errnolink.tsuzuki.designsystem.Bell
import dev.errnolink.tsuzuki.designsystem.ChartLine
import dev.errnolink.tsuzuki.designsystem.Download
import dev.errnolink.tsuzuki.designsystem.Folder
import dev.errnolink.tsuzuki.designsystem.HistoryLucide
import dev.errnolink.tsuzuki.designsystem.Info
import dev.errnolink.tsuzuki.designsystem.SettingsLucide
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TriangleAlert
import dev.errnolink.tsuzuki.ui.settings.GroupDivider
import dev.errnolink.tsuzuki.ui.settings.SettingsSectionBadge
import dev.errnolink.tsuzuki.ui.settings.SettingsScaffold
import dev.errnolink.tsuzuki.ui.settings.SwitchPreferenceWidget
import eu.kanade.tachiyomi.ui.more.DownloadQueueState
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import eu.kanade.tachiyomi.R

@Composable
fun MoreScreen(
    downloadQueueStateProvider: () -> DownloadQueueState,
    downloadedOnly: Boolean,
    onDownloadedOnlyChange: (Boolean) -> Unit,
    incognitoMode: Boolean,
    onIncognitoModeChange: (Boolean) -> Unit,
    // SY -->
    showNavUpdates: Boolean,
    showNavHistory: Boolean,
    // SY <--
    onClickDownloadQueue: () -> Unit,
    onClickCategories: () -> Unit,
    onClickStats: () -> Unit,
    onClickBackupAndRestore: () -> Unit,
    onClickSettings: () -> Unit,
    onClickAbout: () -> Unit,
    onClickUpdates: () -> Unit,
    onClickHistory: () -> Unit,
    // KMK -->
    onClickLibraryUpdateErrors: () -> Unit,
    // KMK <--
) {
    val uriHandler = LocalUriHandler.current

    SettingsScaffold(
        title = stringResource(MR.strings.label_more),
        navigateUp = null,
    ) {
        item(key = "more-modes") {
            InsetGroupedList {
                SwitchPreferenceWidget(
                    title = stringResource(MR.strings.label_downloaded_only),
                    subtitle = stringResource(MR.strings.downloaded_only_summary),
                    icon = TsuzukiIcons.Download,
                    checked = downloadedOnly,
                    onCheckedChanged = onDownloadedOnlyChange,
                    divider = false,
                )
                GroupDivider()
                SwitchPreferenceWidget(
                    title = stringResource(MR.strings.pref_incognito_mode),
                    subtitle = stringResource(MR.strings.pref_incognito_mode_summary),
                    icon = rememberAnimatedVectorPainter(
                        AnimatedImageVector.animatedVectorResource(R.drawable.anim_incognito),
                        incognitoMode,
                    ),
                    checked = incognitoMode,
                    onCheckedChanged = onIncognitoModeChange,
                    divider = false,
                )
            }
        }
        item(key = "more-library") {
            InsetGroupedList {
                if (!showNavUpdates) {
                    MoreRow(
                        title = stringResource(MR.strings.label_recent_updates),
                        icon = TsuzukiIcons.Bell,
                        divider = true,
                        onClick = onClickUpdates,
                    )
                }
                if (!showNavHistory) {
                    MoreRow(
                        title = stringResource(MR.strings.label_recent_manga),
                        icon = TsuzukiIcons.HistoryLucide,
                        divider = true,
                        onClick = onClickHistory,
                    )
                }
                val downloadQueueState = downloadQueueStateProvider()
                MoreRow(
                    title = stringResource(MR.strings.label_download_queue),
                    value = when (downloadQueueState) {
                        DownloadQueueState.Stopped -> null
                        is DownloadQueueState.Paused -> {
                            val pending = downloadQueueState.pending
                            if (pending == 0) {
                                stringResource(MR.strings.paused)
                            } else {
                                "${stringResource(MR.strings.paused)} • ${
                                    pluralStringResource(
                                        MR.plurals.download_queue_summary,
                                        count = pending,
                                        pending,
                                    )
                                }"
                            }
                        }
                        is DownloadQueueState.Downloading -> {
                            val pending = downloadQueueState.pending
                            pluralStringResource(MR.plurals.download_queue_summary, count = pending, pending)
                        }
                    },
                    icon = TsuzukiIcons.ArrowDownUp,
                    divider = true,
                    onClick = onClickDownloadQueue,
                )
                MoreRow(
                    title = stringResource(MR.strings.categories),
                    icon = TsuzukiIcons.Folder,
                    divider = true,
                    onClick = onClickCategories,
                )
                MoreRow(
                    title = stringResource(MR.strings.label_stats),
                    icon = TsuzukiIcons.ChartLine,
                    divider = true,
                    onClick = onClickStats,
                )
                // KMK -->
                MoreRow(
                    title = stringResource(KMR.strings.option_label_library_update_errors),
                    icon = TsuzukiIcons.TriangleAlert,
                    divider = true,
                    onClick = onClickLibraryUpdateErrors,
                )
                // KMK <--
            }
        }
        item(key = "more-backup") {
            InsetGroupedList {
                MoreRow(
                    title = stringResource(MR.strings.label_backup),
                    icon = TsuzukiIcons.ArrowLeftRight,
                    divider = false,
                    onClick = onClickBackupAndRestore,
                )
            }
        }
        item(key = "more-about") {
            InsetGroupedList {
                MoreRow(
                    title = stringResource(MR.strings.label_settings),
                    icon = TsuzukiIcons.SettingsLucide,
                    divider = true,
                    onClick = onClickSettings,
                )
                MoreRow(
                    title = stringResource(MR.strings.pref_category_about),
                    icon = TsuzukiIcons.Info,
                    divider = true,
                    onClick = onClickAbout,
                )
                MoreRow(
                    title = stringResource(MR.strings.label_help),
                    icon = TsuzukiIcons.Globe,
                    divider = false,
                    onClick = { uriHandler.openUri(tachiyomi.core.common.Constants.URL_HELP) },
                )
            }
        }
    }
}

@Composable
private fun MoreRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    divider: Boolean,
    value: String? = null,
    onClick: () -> Unit,
) {
    GroupedRow(
        title = title,
        value = value,
        chevron = true,
        divider = divider,
        leading = { SettingsSectionBadge(icon) },
        onClick = onClick,
    )
}
