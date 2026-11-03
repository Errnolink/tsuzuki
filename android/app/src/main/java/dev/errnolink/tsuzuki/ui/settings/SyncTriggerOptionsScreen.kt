package dev.errnolink.tsuzuki.ui.settings

import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import dev.errnolink.tsuzuki.designsystem.PillButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.sync.models.SyncTriggerOptions
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.update
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class TsuzukiSyncTriggerOptionsScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { SyncOptionsScreenModel() }
        val state by model.state.collectAsState()

        SettingsScaffold(
            title = stringResource(SYMR.strings.pref_sync_options),
            navigateUp = navigator::pop,
            bottomBar = {
                PillButton(
                    stringResource(MR.strings.action_save),
                    {
                        navigator.pop()
                    },
                    Modifier.fillMaxWidth(),
                    large = true,
                )
            },
        ) {
                    item {
                        SettingsSection(SYMR.strings.label_triggers) {
                            Options(SyncTriggerOptions.mainOptions, state, model)
                        }
                    }
                }
    }

    @Composable
    private fun Options(
        options: ImmutableList<SyncTriggerOptions.Entry>,
        state: SyncOptionsScreenModel.State,
        model: SyncOptionsScreenModel,
    ) {
        options.forEachIndexed { index, option ->
            SettingsOptionRow(
                label = stringResource(option.label),
                checked = option.getter(state.options),
                onCheckedChange = {
                    model.toggle(option.setter, it)
                },
                enabled = option.enabled(state.options),
                divider = index < options.lastIndex,
            )
        }
    }
}

private class SyncOptionsScreenModel(
    val syncPreferences: SyncPreferences = Injekt.get(),
) : StateScreenModel<SyncOptionsScreenModel.State>(
    State(
        syncPreferences.getSyncTriggerOptions(),
    ),
) {

    fun toggle(setter: (SyncTriggerOptions, Boolean) -> SyncTriggerOptions, enabled: Boolean) {
        mutableState.update {
            val updatedTriggerOptions = setter(it.options, enabled)
            syncPreferences.setSyncTriggerOptions(updatedTriggerOptions)
            it.copy(
                options = updatedTriggerOptions,
            )
        }
    }

    @Immutable
    data class State(
        val options: SyncTriggerOptions = SyncTriggerOptions(),
    )
}
