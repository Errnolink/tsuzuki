package dev.errnolink.tsuzuki.ui.library

import androidx.compose.runtime.Composable
import dev.errnolink.tsuzuki.designsystem.BookOpen
import dev.errnolink.tsuzuki.designsystem.ChartLine
import dev.errnolink.tsuzuki.designsystem.Download
import dev.errnolink.tsuzuki.designsystem.EyeOff
import dev.errnolink.tsuzuki.designsystem.Info
import dev.errnolink.tsuzuki.designsystem.Layers
import dev.errnolink.tsuzuki.designsystem.PullDownSectionDivider
import dev.errnolink.tsuzuki.designsystem.PullDownMenu
import dev.errnolink.tsuzuki.designsystem.PullDownMenuItem
import dev.errnolink.tsuzuki.designsystem.Gear
import dev.errnolink.tsuzuki.designsystem.TriangleAlert
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun LibraryMenuPullDown(
    expanded: Boolean,
    incognito: Boolean,
    onToggleIncognito: () -> Unit,
    downloadedOnly: Boolean,
    onToggleDownloadedOnly: () -> Unit,
    onOpenDownloadQueue: () -> Unit,
    onOpenLibraryUpdateErrors: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenAbout: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    PullDownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        paneTitle = stringResource(MR.strings.tsuzuki_library_pull_down),
    ) {
        PullDownMenuItem(
            text = stringResource(MR.strings.tsuzuki_incognito),
            icon = TsuzukiIcons.EyeOff,
            selected = incognito,
            onClick = {
                onToggleIncognito()
            },
        )
        PullDownMenuItem(
            text = stringResource(MR.strings.label_downloaded_only),
            icon = TsuzukiIcons.Download,
            selected = downloadedOnly,
            onClick = {
                onToggleDownloadedOnly()
            },
        )
        PullDownSectionDivider()
        PullDownMenuItem(
            text = stringResource(MR.strings.label_download_queue),
            icon = TsuzukiIcons.Layers,
            onClick = {
                onDismissRequest()
                onOpenDownloadQueue()
            },
        )
        PullDownMenuItem(
            text = stringResource(MR.strings.tsuzuki_update_errors),
            icon = TsuzukiIcons.TriangleAlert,
            onClick = {
                onDismissRequest()
                onOpenLibraryUpdateErrors()
            },
        )
        PullDownSectionDivider()
        PullDownMenuItem(
            text = stringResource(MR.strings.label_settings),
            icon = TsuzukiIcons.Gear,
            onClick = {
                onDismissRequest()
                onOpenSettings()
            },
        )
        PullDownMenuItem(
            text = stringResource(MR.strings.tsuzuki_stats),
            icon = TsuzukiIcons.ChartLine,
            onClick = {
                onDismissRequest()
                onOpenStats()
            },
        )
        PullDownMenuItem(
            text = stringResource(MR.strings.pref_category_about),
            icon = TsuzukiIcons.Info,
            onClick = {
                onDismissRequest()
                onOpenAbout()
            },
        )
        PullDownMenuItem(
            text = stringResource(MR.strings.label_help),
            icon = TsuzukiIcons.BookOpen,
            onClick = {
                onDismissRequest()
                onOpenHelp()
            },
        )
    }
}
