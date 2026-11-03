package dev.errnolink.tsuzuki.ui.browse

import android.content.Context
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreference
import androidx.preference.SwitchPreferenceCompat
import androidx.preference.TwoStatePreference
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.InsetGroupedList
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.ui.settings.SettingsScaffold
import dev.errnolink.tsuzuki.ui.settings.SettingsTextField
import dev.errnolink.tsuzuki.ui.shell.ShellEmpty
import eu.kanade.core.util.ifSourcesLoaded
import eu.kanade.tachiyomi.data.preference.SharedPreferencesDataStore
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.sourcePreferences
import exh.source.EnhancedHttpSource
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

private sealed interface SourcePrefDialog {
    data class Edit(val pref: EditTextPreference) : SourcePrefDialog
    data class Single(val pref: ListPreference) : SourcePrefDialog
    data class Multi(val pref: MultiSelectListPreference) : SourcePrefDialog
}

private fun buildSourcePreferenceScreen(context: Context, source: eu.kanade.tachiyomi.source.Source): PreferenceScreen? {
    if (source !is ConfigurableSource) return null
    val manager = PreferenceManager(context)
    manager.preferenceDataStore = SharedPreferencesDataStore(source.sourcePreferences())
    val screen = manager.createPreferenceScreen(context) ?: return null
    source.setupPreferenceScreen(screen)
    return screen
}

@Composable
fun SourcePreferencesContent(
    sourceId: Long,
    navigateUp: () -> Unit,
) {
    if (!ifSourcesLoaded()) {
        LoadingScreen()
        return
    }

    val context = LocalContext.current
    val navigator = LocalNavigator.currentOrThrow

    val source = remember(sourceId) {
        Injekt.get<SourceManager>()
            .getOrStub(sourceId)
            .let { source ->
                if (source is EnhancedHttpSource) {
                    if (source.enhancedSource is ConfigurableSource) {
                        source.source()
                    } else {
                        source.originalSource
                    }
                } else {
                    source
                }
            }
    }
    val screen = remember(sourceId) { buildSourcePreferenceScreen(context, source) }
    var revision by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<SourcePrefDialog?>(null) }

    SettingsScaffold(
        title = source.toString(),
        navigateUp = navigator::pop,
    ) {
        val root = screen
        if (root == null || root.preferenceCount == 0) {
            item(key = "source-preferences-empty") {
                ShellEmpty("No settings", "This source does not expose any preferences.")
            }
            return@SettingsScaffold
        }

        val top = (0 until root.preferenceCount).map { root.getPreference(it) }
        val categories = mutableListOf<Pair<PreferenceCategory, List<Preference>>>()
        val loose = mutableListOf<Preference>()
        top.forEach { pref ->
            if (pref is PreferenceCategory) {
                categories += pref to (0 until pref.preferenceCount).map { pref.getPreference(it) }
            } else {
                loose += pref
            }
        }

        fun prefEnabled(pref: Preference): Boolean {
            if (!pref.isEnabled) return false
            val dependency = pref.dependency ?: return true
            val target = root.findPreference<Preference>(dependency) ?: return true
            return target !is TwoStatePreference || target.isChecked
        }

        if (loose.isNotEmpty()) {
            item(key = "source-prefs-loose", contentType = "source-pref-group") {
                revision
                InsetGroupedList {
                    loose.forEach { pref ->
                        SourcePrefRow(pref, prefEnabled(pref), onRevised = { revision++ }, onDialog = { dialog = it })
                    }
                }
            }
        }
        categories.forEach { (category, children) ->
            item(key = "source-pref-cat-${category.key ?: category.title}", contentType = "source-pref-group") {
                revision
                InsetGroupedList(title = category.title?.toString()) {
                    children.forEach { pref ->
                        SourcePrefRow(pref, prefEnabled(pref), onRevised = { revision++ }, onDialog = { dialog = it })
                    }
                }
            }
        }
    }

    when (val current = dialog) {
        is SourcePrefDialog.Edit -> {
            var text by remember(current.pref.key) { mutableStateOf(current.pref.text.orEmpty()) }
            DetentSheet(
                true,
                current.pref.dialogTitle?.toString() ?: current.pref.title?.toString().orEmpty(),
                { dialog = null },
            ) {
                SettingsTextField(
                    value = TextFieldValue(text),
                    onValueChange = { text = it.text },
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                )
                PillButton(stringResource(MR.strings.action_save), {
                    if (current.pref.callChangeListener(text)) {
                        current.pref.setText(text)
                    }
                    revision++
                    dialog = null
                })
            }
        }

        is SourcePrefDialog.Single -> {
            DetentSheet(
                true,
                current.pref.dialogTitle?.toString() ?: current.pref.title?.toString().orEmpty(),
                { dialog = null },
            ) {
                val entries = current.pref.entries.orEmpty()
                val entryValues = current.pref.entryValues.orEmpty()
                entries.forEachIndexed { index, entry ->
                    val value = entryValues.getOrNull(index)?.toString()
                    GroupedRow(
                        title = entry.toString(),
                        checked = value == current.pref.value,
                        onCheckedChange = { checked ->
                            if (checked && value != null) {
                                if (current.pref.callChangeListener(value)) {
                                    current.pref.value = value
                                }
                                revision++
                                dialog = null
                            }
                        },
                    )
                }
            }
        }

        is SourcePrefDialog.Multi -> {
            val selection = remember(current.pref.key) { mutableStateSetOf<String>().apply { addAll(current.pref.values) } }
            DetentSheet(
                true,
                current.pref.dialogTitle?.toString() ?: current.pref.title?.toString().orEmpty(),
                { dialog = null },
            ) {
                val entries = current.pref.entries.orEmpty()
                val entryValues = current.pref.entryValues.orEmpty()
                entries.forEachIndexed { index, entry ->
                    val value = entryValues.getOrNull(index)?.toString()
                    GroupedRow(
                        title = entry.toString(),
                        checked = value != null && value in selection,
                        onCheckedChange = { checked ->
                            if (checked) {
                                value?.let { selection += it }
                            } else {
                                value?.let { selection -= it }
                            }
                        },
                    )
                }
                PillButton(stringResource(MR.strings.action_save), {
                    if (current.pref.callChangeListener(selection)) {
                        current.pref.values = selection
                    }
                    revision++
                    dialog = null
                })
            }
        }

        null -> Unit
    }
}

@Composable
private fun SourcePrefRow(
    pref: Preference,
    isEnabled: Boolean,
    onRevised: () -> Unit,
    onDialog: (SourcePrefDialog) -> Unit,
) {
    when (pref) {
        is TwoStatePreference -> GroupedRow(
            title = pref.title?.toString().orEmpty(),
            subtitle = pref.summary?.toString(),
            checked = pref.isChecked,
            onCheckedChange = { checked ->
                if (pref.callChangeListener(checked)) {
                    pref.isChecked = checked
                }
                onRevised()
            },
            enabled = isEnabled,
        )

        is EditTextPreference -> GroupedRow(
            title = pref.title?.toString().orEmpty(),
            subtitle = pref.summary?.toString(),
            value = pref.text,
            onClick = { onDialog(SourcePrefDialog.Edit(pref)) },
            enabled = isEnabled,
        )

        is ListPreference -> GroupedRow(
            title = pref.title?.toString().orEmpty(),
            subtitle = pref.summary?.toString(),
            value = pref.entry?.toString(),
            onClick = { onDialog(SourcePrefDialog.Single(pref)) },
            enabled = isEnabled,
        )

        is MultiSelectListPreference -> GroupedRow(
            title = pref.title?.toString().orEmpty(),
            subtitle = pref.summary?.toString(),
            value = pref.values.mapNotNull { value ->
                val index = pref.entryValues.orEmpty().indexOf(value)
                pref.entries.orEmpty().getOrNull(index)?.toString()
            }.joinToString(", "),
            onClick = { onDialog(SourcePrefDialog.Multi(pref)) },
            enabled = isEnabled,
        )

        else -> GroupedRow(
            title = pref.title?.toString().orEmpty(),
            subtitle = pref.summary?.toString(),
            onClick = {
                pref.onPreferenceClickListener?.onPreferenceClick(pref)
                onRevised()
            },
            enabled = isEnabled,
        )
    }
}
