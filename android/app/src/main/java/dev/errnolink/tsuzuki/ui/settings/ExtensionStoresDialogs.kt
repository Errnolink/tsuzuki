package dev.errnolink.tsuzuki.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.time.Duration.Companion.seconds

@Composable
fun ExtensionStoreCreateDialog(
    onDismissRequest: () -> Unit,
    onCreate: (String) -> Unit,
    storeIndexUrls: Set<String>,
    processing: Boolean,
    errorMessage: String?,
) {
    val state = rememberTextFieldState()
    val storeAlreadyExists by remember(storeIndexUrls) {
        derivedStateOf {
            val indexUrl = state.text.toString()
            storeIndexUrls.contains(indexUrl)
        }
    }

    val focusRequester = remember { FocusRequester() }

    SettingsAlert(onDismissRequest = onDismissRequest,
    title = {
        TsuzukiText(text = stringResource(MR.strings.extensionStoresScreen_addStore_title))
    },
    text = {
        SettingsTextField(modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        state = state,
        label = {
            TsuzukiText(text = stringResource(MR.strings.extensionStoresScreen_addStoreInput_inputLabel))
        },
        supportingText = {
            val msgRes = if (storeAlreadyExists) {
                MR.strings.extensionStoresScreen_addStore_alreadyExists
            } else {
                MR.strings.information_required_plain
            }
            TsuzukiText(text = errorMessage ?: stringResource(msgRes))
        },
        isError = errorMessage != null || storeAlreadyExists,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        lineLimits = TextFieldLineLimits.SingleLine,)
    },
    confirmButton = {
        SettingsActionButton(onClick = { onCreate(state.text.toString()) },
        enabled = !processing && state.text.isNotEmpty() && !storeAlreadyExists,) { TsuzukiText(
            text = stringResource(
                resource = if (processing) {
                    MR.strings.extensionStoresScreen_addStore_processing
                } else {
                    MR.strings.action_add
                },
            ),
        ) }
    },
    dismissButton = {
        SettingsActionButton(onClick = onDismissRequest) { TsuzukiText(text = stringResource(MR.strings.action_cancel)) }
    },)

    LaunchedEffect(focusRequester) {
        // TODO: https://issuetracker.google.com/issues/204502668
        delay(0.1.seconds)
        focusRequester.requestFocus()
    }
}

@Composable
fun ExtensionStoreDeleteDialog(
    onDismissRequest: () -> Unit,
    onDelete: () -> Unit,
    storeName: String,
    storeIndexUrl: String,
) {
    SettingsAlert(onDismissRequest = onDismissRequest,
    title = {
        TsuzukiText(text = stringResource(MR.strings.extensionStoresScreen_deleteStore_title))
    },
    text = {
        TsuzukiText(text = stringResource(MR.strings.extensionStoresScreen_deleteStore_body, storeName, storeIndexUrl))
    },
    confirmButton = {
        SettingsActionButton(onClick = {
            onDelete()
            onDismissRequest()
        }, destructive = true) { TsuzukiText(text = stringResource(MR.strings.action_delete)) }
    },
    dismissButton = {
        SettingsActionButton(onClick = onDismissRequest) { TsuzukiText(text = stringResource(MR.strings.action_cancel)) }
    },)
}

@Composable
fun ExtensionStoreConfirmDialog(
    onDismissRequest: () -> Unit,
    onCreate: () -> Unit,
    storeIndexUrl: String,
    storeAlreadyExists: Boolean,
    processing: Boolean,
    errorMessage: String?,
) {
    val state = rememberTextFieldState(initialText = storeIndexUrl)
    SettingsAlert(onDismissRequest = onDismissRequest,
    title = {
        TsuzukiText(text = stringResource(MR.strings.extensionStoresScreen_addStore_title))
    },
    text = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TsuzukiText(text = stringResource(MR.strings.extensionStoresScreen_addStoreDeeplink_bodyText))
            SettingsTextField(modifier = Modifier.fillMaxWidth(),
            state = state,
            readOnly = true,
            supportingText = when {
                storeAlreadyExists -> {
                    {
                        TsuzukiText(text = stringResource(MR.strings.extensionStoresScreen_addStore_alreadyExists))
                    }
                }
                errorMessage != null -> {
                    {
                        TsuzukiText(text = errorMessage)
                    }
                }
                else -> null
            },
            isError = errorMessage != null || storeAlreadyExists,)
        }
    },
    confirmButton = {
        SettingsActionButton(onClick = onCreate, enabled = !storeAlreadyExists && !processing) { TsuzukiText(
            text = stringResource(
                resource = if (processing) {
                    MR.strings.extensionStoresScreen_addStore_processing
                } else {
                    MR.strings.action_add
                },
            ),
        ) }
    },
    dismissButton = {
        SettingsActionButton(onClick = onDismissRequest) { TsuzukiText(text = stringResource(MR.strings.action_cancel)) }
    },)
}
