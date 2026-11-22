package dev.errnolink.tsuzuki.ui.shell

import eu.kanade.tachiyomi.extension.model.InstallStep

enum class InstallRowTrailing { Cancel, Action, Done, None }

data class InstallRowView(
    val busy: Boolean,
    val failed: Boolean,
    val trailing: InstallRowTrailing,
    val actionLabel: String,
    val doneText: String,
)

fun installRowState(
    step: InstallStep?,
    isAvailable: Boolean,
    isInstalled: Boolean,
    isUntrusted: Boolean,
    hasUpdate: Boolean,
    cancellable: Boolean,
): InstallRowView = when (step) {
    InstallStep.Pending, InstallStep.Downloading, InstallStep.Installing -> InstallRowView(
        busy = true,
        failed = false,
        trailing = if (cancellable) InstallRowTrailing.Cancel else InstallRowTrailing.None,
        actionLabel = "",
        doneText = "",
    )
    InstallStep.Installed -> InstallRowView(
        busy = false,
        failed = false,
        trailing = InstallRowTrailing.Done,
        actionLabel = "",
        doneText = "Installed",
    )
    InstallStep.Error -> InstallRowView(
        busy = false,
        failed = true,
        trailing = InstallRowTrailing.Action,
        actionLabel = "Retry",
        doneText = "",
    )
    null, InstallStep.Idle -> when {
        isUntrusted -> InstallRowView(false, false, InstallRowTrailing.Action, "Trust", "")
        isInstalled && hasUpdate -> InstallRowView(false, false, InstallRowTrailing.Action, "Update", "")
        isInstalled -> InstallRowView(false, false, InstallRowTrailing.Done, "", "Installed")
        isAvailable -> InstallRowView(false, false, InstallRowTrailing.Action, "Install", "")
        else -> InstallRowView(false, false, InstallRowTrailing.None, "", "")
    }
}
