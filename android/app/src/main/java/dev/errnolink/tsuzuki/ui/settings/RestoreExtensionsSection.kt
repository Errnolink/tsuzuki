package dev.errnolink.tsuzuki.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.Check
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.InsetGroupedList
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.ProgressRing
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep

sealed interface RestoreExtensionsState {
    data object None : RestoreExtensionsState
    data object Checking : RestoreExtensionsState
    data class Ready(val matches: List<ExtensionMatch>, val steps: Map<String, InstallStep>) : RestoreExtensionsState
}

@Composable
fun RestoreExtensionsSection(
    state: RestoreExtensionsState,
    onInstall: (Extension.Available) -> Unit,
    onInstallAll: () -> Unit,
    onOpenRepos: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (state) {
            RestoreExtensionsState.None -> Unit
            RestoreExtensionsState.Checking -> InsetGroupedList(title = "Extensions this backup needs") {
                GroupedRow(
                    title = "Checking your extension repos…",
                    divider = false,
                    leading = { ProgressRing(0f, "Checking extension repos") },
                )
            }
            is RestoreExtensionsState.Ready -> {
                val installable = state.matches.mapNotNull { it.extension }.distinctBy { it.pkgName }
                val pending = installable.filter { state.steps[it.pkgName] == null || state.steps[it.pkgName] == InstallStep.Error }
                val unavailable = state.matches.filter { it.extension == null }
                InsetGroupedList(
                    title = "Extensions this backup needs",
                    footer = "Install these before restoring so every title links to its source. " +
                        "Titles restored without them link up on their own once you install the extension.",
                ) {
                    state.matches.forEachIndexed { index, match ->
                        val extension = match.extension
                        val step = extension?.let { state.steps[it.pkgName] }
                        val titles = "${match.needed.titles} ${if (match.needed.titles == 1) "title" else "titles"}"
                        GroupedRow(
                            title = match.title,
                            subtitle = when {
                                extension == null -> "$titles · Not in your extension repos"
                                step == InstallStep.Error -> "$titles · Download failed. Check the connection or try again later"
                                else -> "$titles · ${extension.storeName}"
                            },
                            divider = index < state.matches.lastIndex || unavailable.isNotEmpty(),
                            trailing = {
                                if (extension != null) InstallAccessory(state.steps[extension.pkgName]) { onInstall(extension) }
                            },
                        )
                    }
                    if (unavailable.isNotEmpty()) {
                        GroupedRow(
                            title = "Add an extension repo",
                            subtitle = "For sources none of your repos have",
                            chevron = true,
                            divider = false,
                            onClick = onOpenRepos,
                        )
                    }
                }
                if (pending.size > 1) {
                    Box(Modifier.padding(horizontal = TsuzukiSpacing.gutter)) {
                        PillButton("Install all (${pending.size})", onInstallAll, Modifier.fillMaxWidth(), tinted = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun InstallAccessory(step: InstallStep?, onInstall: () -> Unit) {
    when (step) {
        InstallStep.Pending, InstallStep.Downloading, InstallStep.Installing ->
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { ProgressRing(0f, "Installing") }
        InstallStep.Installed ->
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Icon(TsuzukiIcons.Check, "Installed", Modifier.size(20.dp), tint = TsuzukiTheme.colors.accent)
            }
        InstallStep.Error -> PillButton("Retry", onInstall, prominent = false, tinted = true)
        else -> PillButton("Install", onInstall, prominent = false, tinted = true)
    }
}
