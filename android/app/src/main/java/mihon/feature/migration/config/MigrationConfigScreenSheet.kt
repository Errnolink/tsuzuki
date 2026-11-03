package mihon.feature.migration.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.InsetGroupedList
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.ui.settings.SettingsTextField
import eu.kanade.core.util.fastFilterNot
import eu.kanade.domain.source.service.SourcePreferences
import mihon.domain.migration.models.MigrationFlag
import mihon.feature.common.utils.getLabel
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.core.common.preference.toggle
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

// KMK -->
// B34: the Material AdaptiveSheet was replaced by a Tsuzuki DetentSheet; the flag chips, switches,
// extra search query and continue action keep their upstream behaviour.
// KMK <--

@Composable
fun MigrationConfigScreenSheet(
    preferences: SourcePreferences,
    onDismissRequest: () -> Unit,
    onStartMigration: (extraSearchQuery: String?) -> Unit,
    // KMK -->
    fullSettings: Boolean = true,
    isSingleEntry: Boolean = false,
    // KMK <--
) {
    var extraSearchQuery by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    val migrationFlags by preferences.migrationFlags().collectAsState()

    DetentSheet(
        true,
        stringResource(MR.strings.migrationConfigScreen_dataToMigrateHeader),
        onDismissRequest,
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MigrationFlag.entries
                .fastFilterNot { it == MigrationFlag.REMOVE_DOWNLOAD }
                .fastForEach { flag ->
                    val selected = flag in migrationFlags
                    FilterChip(
                        selected = selected,
                        onClick = {
                            preferences.migrationFlags().getAndSet {
                                if (flag in it) it - flag else it + flag
                            }
                        },
                        label = { Text(stringResource(flag.getLabel())) },
                        leadingIcon = if (selected) {
                            { Icon(imageVector = Icons.Outlined.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = TsuzukiTheme.colors.grouped,
                        ),
                    )
                }
        }

        val removeDownloads = MigrationFlag.REMOVE_DOWNLOAD in migrationFlags
        InsetGroupedList {
            MigrationSheetSwitchItem(
                title = stringResource(MR.strings.migrationConfigScreen_removeDownloadsTitle),
                subtitle = null,
                checked = removeDownloads,
                onClick = {
                    preferences.migrationFlags().getAndSet {
                        if (MigrationFlag.REMOVE_DOWNLOAD in it) it - MigrationFlag.REMOVE_DOWNLOAD else it + MigrationFlag.REMOVE_DOWNLOAD
                    }
                },
            )
            MigrationSheetSwitchItem(
                title = stringResource(MR.strings.migrationConfigScreen_hideUnmatchedTitle),
                subtitle = null,
                preference = preferences.migrationHideUnmatched(),
            )
            MigrationSheetSwitchItem(
                title = stringResource(MR.strings.migrationConfigScreen_hideWithoutUpdatesTitle),
                subtitle = stringResource(MR.strings.migrationConfigScreen_hideWithoutUpdatesSubtitle),
                preference = preferences.migrationHideWithoutUpdates(),
            )
            // KMK -->
            if (fullSettings) {
                // KMK <--
                val migrationSmartSearchSingleEntryPref = preferences.migrationSmartSearchSingleEntry()
                val isSmartSearchSingleEntry by migrationSmartSearchSingleEntryPref.collectAsState()
                if (isSingleEntry) {
                    MigrationSheetSwitchItem(
                        title = stringResource(KMR.strings.migrationConfigScreen_smartSearchSingleEntryTitle),
                        subtitle = stringResource(KMR.strings.migrationConfigScreen_smartSearchSingleEntrySubtitle),
                        preference = migrationSmartSearchSingleEntryPref,
                    )
                }
                if (!isSingleEntry || isSmartSearchSingleEntry) {
                    MigrationSheetWarningItem(stringResource(MR.strings.migrationConfigScreen_enhancedOptionsWarning))
                    MigrationSheetSwitchItem(
                        title = stringResource(MR.strings.migrationConfigScreen_deepSearchModeTitle),
                        subtitle = stringResource(MR.strings.migrationConfigScreen_deepSearchModeSubtitle),
                        preference = preferences.migrationDeepSearchMode(),
                    )
                    MigrationSheetSwitchItem(
                        title = stringResource(MR.strings.migrationConfigScreen_prioritizeByChaptersTitle),
                        subtitle = stringResource(MR.strings.migrationConfigScreen_prioritizeByChaptersSubtitle),
                        preference = preferences.migrationPrioritizeByChapters(),
                    )
                }
            }
        }

        if (fullSettings) {
            SettingsTextField(
                value = extraSearchQuery,
                onValueChange = { extraSearchQuery = it },
                label = { TsuzukiText(stringResource(MR.strings.migrationConfigScreen_additionalSearchQueryLabel)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        PillButton(
            stringResource(
                // KMK -->
                if (!fullSettings) {
                    MR.strings.action_save
                } else {
                    // KMK <--
                    MR.strings.migrationConfigScreen_continueButtonText
                },
            ),
            {
                onStartMigration(extraSearchQuery.text.trim().ifBlank { null })
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun MigrationSheetSwitchItem(
    title: String,
    subtitle: String?,
    preference: Preference<Boolean>,
) {
    MigrationSheetSwitchItem(
        title = title,
        subtitle = subtitle,
        checked = preference.collectAsState().value,
        onClick = { preference.toggle() },
    )
}

// KMK -->
@Composable
private fun MigrationSheetSwitchItem(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onClick: () -> Unit,
) {
    GroupedRow(
        title = title,
        subtitle = subtitle,
        checked = checked,
        onCheckedChange = { onClick() },
    )
}

@Composable
private fun MigrationSheetWarningItem(
    text: String,
) {
    TsuzukiText(
        text,
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        TsuzukiTheme.typography.footnote,
        TsuzukiTheme.colors.destructive,
    )
}
// KMK <--
