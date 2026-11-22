package dev.errnolink.tsuzuki.ui.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.EmptyState
import dev.errnolink.tsuzuki.designsystem.Info
import dev.errnolink.tsuzuki.designsystem.InsetGroupedList
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.SheetKind
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.X
import dev.errnolink.tsuzuki.ui.settings.CheckStateOff
import dev.errnolink.tsuzuki.ui.settings.CheckStateOn
import dev.errnolink.tsuzuki.ui.settings.GroupDivider
import kotlinx.collections.immutable.ImmutableSet
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Wave 2 WP11.3: DetentSheet port of `eu.kanade.presentation.manga.components.ScanlatorFilterDialog`.
 * Rows toggle a scanlator's exclusion; Select all/Reset swap like the upstream confirm row, and
 * every callback (confirm with the full excluded set, dismiss) is unchanged.
 */
@Composable
fun ScanlatorFilterSheet(
    availableScanlators: ImmutableSet<String>,
    excludedScanlators: ImmutableSet<String>,
    onDismissRequest: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    val sortedAvailableScanlators = remember(availableScanlators) {
        availableScanlators.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
    }
    val mutableExcludedScanlators = remember(excludedScanlators) { excludedScanlators.toMutableStateList() }

    DetentSheet(
        visible = true,
        title = stringResource(MR.strings.exclude_scanlators),
        onDismissRequest = onDismissRequest,
        kind = SheetKind.Picker,
    ) {
        if (sortedAvailableScanlators.isEmpty()) {
            EmptyState(
                title = stringResource(MR.strings.no_scanlators_found),
                description = stringResource(MR.strings.tsuzuki_all_scanlators_shown_desc),
                icon = { Icon(TsuzukiIcons.Info, null, Modifier.size(28.dp), tint = TsuzukiTheme.colors.secondary) },
                action = {
                    PillButton(
                        label = stringResource(MR.strings.action_cancel),
                        onClick = onDismissRequest,
                    )
                },
            )
        } else {
            InsetGroupedList {
                sortedAvailableScanlators.forEachIndexed { index, scanlator ->
                    val isExcluded = scanlator in mutableExcludedScanlators
                    if (index > 0) GroupDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = TsuzukiSpacing.rowMin)
                            .clickable(role = Role.Checkbox) {
                                if (isExcluded) {
                                    mutableExcludedScanlators.remove(scanlator)
                                } else {
                                    mutableExcludedScanlators.add(scanlator)
                                }
                            }
                            .semantics { stateDescription = if (isExcluded) CheckStateOn else CheckStateOff }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TsuzukiText(scanlator, Modifier.weight(1f))
                        if (isExcluded) {
                            Icon(
                                TsuzukiIcons.X,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = TsuzukiTheme.colors.destructive,
                            )
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = TsuzukiSpacing.medium),
                horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PillButton(
                    label = if (mutableExcludedScanlators.isEmpty()) {
                        stringResource(MR.strings.action_select_all)
                    } else {
                        stringResource(MR.strings.action_reset)
                    },
                    onClick = {
                        if (mutableExcludedScanlators.isEmpty()) {
                            mutableExcludedScanlators.addAll(availableScanlators)
                        } else {
                            mutableExcludedScanlators.clear()
                        }
                    },
                    prominent = false,
                )
                Box(Modifier.weight(1f))
                PillButton(
                    label = stringResource(MR.strings.action_cancel),
                    onClick = onDismissRequest,
                    prominent = false,
                )
                PillButton(
                    label = stringResource(MR.strings.action_ok),
                    onClick = {
                        onConfirm(mutableExcludedScanlators.toSet())
                        onDismissRequest()
                    },
                )
            }
        }
    }
}
