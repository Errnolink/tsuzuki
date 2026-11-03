package dev.errnolink.tsuzuki.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.PillButton
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun DeleteLibraryMangaDialog(
    containsLocalManga: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: (Boolean, Boolean) -> Unit,
) {
    var removeLibrary by remember { mutableStateOf(false) }
    var removeDownloads by remember { mutableStateOf(false) }
    DetentSheet(true, stringResource(MR.strings.action_remove), onDismissRequest) {
        GroupedRow(
            title = stringResource(MR.strings.manga_from_library),
            checked = removeLibrary,
            onCheckedChange = { removeLibrary = it },
        )
        if (!containsLocalManga) {
            GroupedRow(
                title = stringResource(MR.strings.downloaded_chapters),
                checked = removeDownloads,
                onCheckedChange = { removeDownloads = it },
                divider = false,
            )
        }
        PillButton(
            label = stringResource(MR.strings.action_remove),
            destructive = true,
            enabled = removeLibrary || removeDownloads,
            onClick = {
                onDismissRequest()
                onConfirm(removeLibrary, removeDownloads)
            },
        )
    }
}
