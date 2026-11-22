// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): org/nekomanga/presentation/screens/reader/ReaderPageActionsSheet.kt
package dev.errnolink.tsuzuki.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.SheetDetent
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Immutable
sealed interface ReaderPageAction {
    data object Share : ReaderPageAction

    data object Save : ReaderPageAction

    data object SetAsCover : ReaderPageAction

    data object ShareFirstPage : ReaderPageAction

    data object SaveFirstPage : ReaderPageAction

    data object SetFirstPageAsCover : ReaderPageAction

    data object ShareSecondPage : ReaderPageAction

    data object SaveSecondPage : ReaderPageAction

    data object SetSecondPageAsCover : ReaderPageAction

    data object ShareCombinedPages : ReaderPageAction

    data object SaveCombinedPages : ReaderPageAction
}

@Composable
fun ReaderPageActionsSheet(
    visible: Boolean,
    hasExtraPage: Boolean,
    onActionClicked: (ReaderPageAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DetentSheet(
        visible = visible,
        title = stringResource(MR.strings.action_menu),
        onDismissRequest = onDismiss,
        modifier = modifier,
        initialDetent = SheetDetent.FitContent,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            if (hasExtraPage) {
                ActionRow(
                    title = stringResource(MR.strings.tsuzuki_share_second_page),
                    icon = Icons.Outlined.Share,
                    onClick = {
                        onActionClicked(ReaderPageAction.ShareSecondPage)
                        onDismiss()
                    },
                )
                ActionRow(
                    title = stringResource(MR.strings.tsuzuki_save_second_page),
                    icon = Icons.Outlined.Save,
                    onClick = {
                        onActionClicked(ReaderPageAction.SaveSecondPage)
                        onDismiss()
                    },
                )
                ActionRow(
                    title = stringResource(MR.strings.tsuzuki_set_second_page_as_cover),
                    icon = Icons.Outlined.Image,
                    onClick = {
                        onActionClicked(ReaderPageAction.SetSecondPageAsCover)
                        onDismiss()
                    },
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = TsuzukiTheme.colors.separator,
                )

                ActionRow(
                    title = stringResource(MR.strings.tsuzuki_share_first_page),
                    icon = Icons.Outlined.Share,
                    onClick = {
                        onActionClicked(ReaderPageAction.ShareFirstPage)
                        onDismiss()
                    },
                )
                ActionRow(
                    title = stringResource(MR.strings.tsuzuki_save_first_page),
                    icon = Icons.Outlined.Save,
                    onClick = {
                        onActionClicked(ReaderPageAction.SaveFirstPage)
                        onDismiss()
                    },
                )
                ActionRow(
                    title = stringResource(MR.strings.tsuzuki_set_first_page_as_cover),
                    icon = Icons.Outlined.Image,
                    onClick = {
                        onActionClicked(ReaderPageAction.SetFirstPageAsCover)
                        onDismiss()
                    },
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = TsuzukiTheme.colors.separator,
                )

                ActionRow(
                    title = stringResource(MR.strings.tsuzuki_share_combined_pages),
                    icon = Icons.Outlined.Collections,
                    onClick = {
                        onActionClicked(ReaderPageAction.ShareCombinedPages)
                        onDismiss()
                    },
                )
                ActionRow(
                    title = stringResource(MR.strings.tsuzuki_save_combined_pages),
                    icon = Icons.Outlined.Collections,
                    onClick = {
                        onActionClicked(ReaderPageAction.SaveCombinedPages)
                        onDismiss()
                    },
                )
            } else {
                ActionRow(
                    title = stringResource(MR.strings.action_share),
                    icon = Icons.Outlined.Share,
                    onClick = {
                        onActionClicked(ReaderPageAction.Share)
                        onDismiss()
                    },
                )
                ActionRow(
                    title = stringResource(MR.strings.action_save),
                    icon = Icons.Outlined.Save,
                    onClick = {
                        onActionClicked(ReaderPageAction.Save)
                        onDismiss()
                    },
                )
                ActionRow(
                    title = stringResource(MR.strings.set_as_cover),
                    icon = Icons.Outlined.Image,
                    onClick = {
                        onActionClicked(ReaderPageAction.SetAsCover)
                        onDismiss()
                    },
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ActionRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TsuzukiTheme.colors.accent,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        TsuzukiText(
            text = title,
            style = TsuzukiTheme.typography.body,
            color = TsuzukiTheme.colors.text,
        )
    }
}
