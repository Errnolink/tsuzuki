package dev.errnolink.tsuzuki.ui.library

import androidx.compose.runtime.Composable
import dev.errnolink.tsuzuki.designsystem.Bell
import dev.errnolink.tsuzuki.designsystem.ChartLine
import dev.errnolink.tsuzuki.designsystem.Download
import dev.errnolink.tsuzuki.designsystem.EyeOff
import dev.errnolink.tsuzuki.designsystem.Info
import dev.errnolink.tsuzuki.designsystem.PullDownDivider
import dev.errnolink.tsuzuki.designsystem.PullDownMenu
import dev.errnolink.tsuzuki.designsystem.PullDownMenuItem
import dev.errnolink.tsuzuki.designsystem.SettingsLucide
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons

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
        paneTitle = "Library pull-down menu",
    ) {
        PullDownMenuItem(
            text = "Incognito",
            icon = TsuzukiIcons.EyeOff,
            selected = incognito,
            onClick = {
                onToggleIncognito()
            },
        )
        PullDownMenuItem(
            text = "Downloaded only",
            selected = downloadedOnly,
            onClick = {
                onToggleDownloadedOnly()
            },
        )
        PullDownDivider()
        PullDownMenuItem(
            text = "Download queue",
            icon = TsuzukiIcons.Download,
            onClick = {
                onDismissRequest()
                onOpenDownloadQueue()
            },
        )
        PullDownMenuItem(
            text = "Library update errors",
            icon = TsuzukiIcons.Bell,
            onClick = {
                onDismissRequest()
                onOpenLibraryUpdateErrors()
            },
        )
        PullDownDivider()
        PullDownMenuItem(
            text = "Settings",
            icon = TsuzukiIcons.SettingsLucide,
            onClick = {
                onDismissRequest()
                onOpenSettings()
            },
        )
        PullDownDivider()
        PullDownMenuItem(
            text = "Stats",
            icon = TsuzukiIcons.ChartLine,
            onClick = {
                onDismissRequest()
                onOpenStats()
            },
        )
        PullDownDivider()
        PullDownMenuItem(
            text = "About",
            icon = TsuzukiIcons.Info,
            onClick = {
                onDismissRequest()
                onOpenAbout()
            },
        )
        PullDownMenuItem(
            text = "Help",
            onClick = {
                onDismissRequest()
                onOpenHelp()
            },
        )
    }
}
