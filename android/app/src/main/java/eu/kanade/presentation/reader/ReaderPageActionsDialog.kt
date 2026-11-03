package eu.kanade.presentation.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.Copy
import dev.errnolink.tsuzuki.designsystem.Download
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.Images
import dev.errnolink.tsuzuki.designsystem.InsetGroupedList
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.ui.details.DetailDialog
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun ReaderPageActionsDialog(
    onDismissRequest: () -> Unit,
    onSetAsCover: (/* SY --> */useExtraPage: Boolean/* SY <-- */) -> Unit,
    onShare: (copyToClipboard: Boolean, /* SY --> */useExtraPage: Boolean/* SY <-- */) -> Unit,
    onSave: (/* SY --> */useExtraPage: Boolean/* SY <-- */) -> Unit,
    // SY -->
    onShareCombined: (copyToClipboard: Boolean) -> Unit,
    onSaveCombined: () -> Unit,
    hasExtraPage: Boolean,
    // SY <--
) {
    var showSetCoverDialog by remember { mutableStateOf(false) }
    // SY -->
    var useExtraPage by remember { mutableStateOf(false) }
    // SY <--

    DetentSheet(true, "Page actions", onDismissRequest, initialDetent = dev.errnolink.tsuzuki.designsystem.SheetDetent.FitContent) {
        InsetGroupedList(title = "This page") {
            PageActionRow(stringResource(if (hasExtraPage) SYMR.strings.action_set_first_page_cover else MR.strings.set_as_cover), TsuzukiIcons.Images) {
                useExtraPage = false
                showSetCoverDialog = true
            }
            PageActionRow(stringResource(if (hasExtraPage) KMR.strings.action_copy_to_clipboard_first_page else MR.strings.action_copy_to_clipboard), TsuzukiIcons.Copy) {
                // SY -->
                onShare(true, false)
                // SY <--
                onDismissRequest()
            }
            PageActionRow(stringResource(if (hasExtraPage) SYMR.strings.action_share_first_page else MR.strings.action_share), TsuzukiIcons.Share) {
                // SY -->
                onShare(false, false)
                // SY <--
                onDismissRequest()
            }
            PageActionRow(stringResource(if (hasExtraPage) SYMR.strings.action_save_first_page else MR.strings.action_save), TsuzukiIcons.Download, divider = false) {
                // SY -->
                onSave(false)
                // SY <--
                onDismissRequest()
            }
        }
        if (hasExtraPage) {
            InsetGroupedList(title = "Second page") {
                PageActionRow(stringResource(SYMR.strings.action_set_second_page_cover), TsuzukiIcons.Images) {
                    useExtraPage = true
                    showSetCoverDialog = true
                }
                PageActionRow(stringResource(KMR.strings.action_copy_to_clipboard_second_page), TsuzukiIcons.Copy) {
                    onShare(true, true)
                    onDismissRequest()
                }
                PageActionRow(stringResource(SYMR.strings.action_share_second_page), TsuzukiIcons.Share) {
                    onShare(false, true)
                    onDismissRequest()
                }
                PageActionRow(stringResource(SYMR.strings.action_save_second_page), TsuzukiIcons.Download, divider = false) {
                    onSave(true)
                    onDismissRequest()
                }
            }
            InsetGroupedList(title = "Combined") {
                PageActionRow(stringResource(KMR.strings.action_copy_to_clipboard_combined_page), TsuzukiIcons.Copy) {
                    onShareCombined(true)
                    onDismissRequest()
                }
                PageActionRow(stringResource(SYMR.strings.action_share_combined_page), TsuzukiIcons.Share) {
                    onShareCombined(false)
                    onDismissRequest()
                }
                PageActionRow(stringResource(SYMR.strings.action_save_combined_page), TsuzukiIcons.Download, divider = false) {
                    onSaveCombined()
                    onDismissRequest()
                }
            }
        }
    }

    if (showSetCoverDialog) {
        DetailDialog(
            title = stringResource(MR.strings.set_as_cover),
            onDismissRequest = {
                showSetCoverDialog = false
                useExtraPage = false
            },
            confirmLabel = stringResource(MR.strings.action_ok),
            onConfirm = {
                // SY -->
                onSetAsCover(useExtraPage)
                showSetCoverDialog = false
                useExtraPage = false
                // SY <--
            },
        ) {
            TsuzukiText(
                stringResource(MR.strings.confirm_set_image_as_cover),
                style = TsuzukiTheme.typography.callout,
                color = TsuzukiTheme.colors.secondary,
            )
        }
    }
}

@Composable
private fun PageActionRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    divider: Boolean = true,
    onClick: () -> Unit,
) {
    GroupedRow(
        title = label,
        leading = {
            androidx.compose.material3.Icon(icon, null, tint = TsuzukiTheme.colors.text)
        },
        onClick = onClick,
        divider = divider,
    )
}
