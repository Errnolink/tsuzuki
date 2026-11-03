package dev.errnolink.tsuzuki.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.ContextMenuAction
import dev.errnolink.tsuzuki.designsystem.Ellipsis
import dev.errnolink.tsuzuki.designsystem.Trash2
import dev.errnolink.tsuzuki.designsystem.EyeOff
import dev.errnolink.tsuzuki.designsystem.Copy
import dev.errnolink.tsuzuki.designsystem.GlassMenu
import dev.errnolink.tsuzuki.designsystem.InsetGroupedList
import dev.errnolink.tsuzuki.ui.shell.ShellAction
import eu.kanade.tachiyomi.R
import mihon.domain.extension.model.ExtensionStore
import mihon.domain.extension.model.KOMIKKU_SIGNATURE
import mihon.domain.extension.model.REPO_SIGNATURE

@Composable
fun ExtensionStoresList(
    repos: List<ExtensionStore>,
    onCopy: (ExtensionStore) -> Unit,
    onOpenWebsite: (ExtensionStore) -> Unit,
    onOpenDiscord: (ExtensionStore) -> Unit,
    onClickDelete: (ExtensionStore) -> Unit,
    onClickEnable: (ExtensionStore) -> Unit,
    onClickDisable: (ExtensionStore) -> Unit,
    disabledRepos: Set<String>,
    modifier: Modifier = Modifier,
) {
    InsetGroupedList(modifier = modifier) {
        repos.forEachIndexed { index, store ->
            if (index > 0) GroupDivider()
            ExtensionStoresListItem(
                store = store,
                onOpenWebsite = { onOpenWebsite(store) },
                onOpenDiscord = { onOpenDiscord(store) },
                onCopy = { onCopy(store) },
                onDelete = { onClickDelete(store) },
                onEnable = { onClickEnable(store) },
                onDisable = { onClickDisable(store) },
                isDisabled = store.indexUrl in disabledRepos,
            )
        }
    }
}

@Composable
private fun ExtensionStoresListItem(
    store: ExtensionStore,
    onOpenWebsite: () -> Unit,
    onOpenDiscord: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    isDisabled: Boolean,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    GroupedRow(
        title = store.name,
        subtitle = if (isDisabled) "Disabled" else store.indexUrl,
        divider = true,
        leading = {
            Image(
                painter = painterResource(repoResId(store.signingKey)),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )
        },
        trailing = {
            ShellAction("Repository actions", TsuzukiIcons.Ellipsis, { expanded = true })
        },
        onClick = onOpenWebsite,
    )
    GlassMenu(
        expanded = expanded,
        label = store.name,
        actions = buildList {
            add(ContextMenuAction("Website", icon = { dev.errnolink.tsuzuki.designsystem.TsuzukiIcon(TsuzukiIcons.Globe, null) }, onClick = onOpenWebsite))
            if (store.contact.discord != null) {
                add(ContextMenuAction("Discord", icon = { dev.errnolink.tsuzuki.designsystem.TsuzukiIcon(TsuzukiIcons.Globe, null) }, onClick = onOpenDiscord))
            }
            add(ContextMenuAction("Copy URL", icon = { dev.errnolink.tsuzuki.designsystem.TsuzukiIcon(TsuzukiIcons.Copy, null) }, onClick = onCopy))
            add(
                ContextMenuAction(
                    if (isDisabled) "Enable repository" else "Disable repository",
                    icon = { dev.errnolink.tsuzuki.designsystem.TsuzukiIcon(TsuzukiIcons.EyeOff, null) },
                    group = 1,
                    onClick = if (isDisabled) onEnable else onDisable,
                ),
            )
            add(
                ContextMenuAction(
                    "Delete repository",
                    icon = { dev.errnolink.tsuzuki.designsystem.TsuzukiIcon(TsuzukiIcons.Trash2, null) },
                    group = 2,
                    destructive = true,
                    onClick = onDelete,
                ),
            )
        },
        onDismissRequest = { expanded = false },
    )
}

fun repoResId(signKey: String) = when (signKey) {
    KOMIKKU_SIGNATURE -> R.mipmap.komikku
    REPO_SIGNATURE -> R.mipmap.repo
    else -> R.mipmap.extension
}
