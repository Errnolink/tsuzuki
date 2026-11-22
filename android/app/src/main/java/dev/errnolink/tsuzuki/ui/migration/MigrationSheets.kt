package dev.errnolink.tsuzuki.ui.migration

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.SheetKind
import dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.WavyProgress
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

/** Wave 2 WP11.3: DetentSheet port of `mihon.feature.migration.list.components.MigrationExitDialog`. */
@Composable
fun MigrationExitSheet(
    onDismissRequest: () -> Unit,
    exitMigration: () -> Unit,
) {
    DetentSheet(
        visible = true,
        title = stringResource(MR.strings.migrationListScreen_exitDialogTitle),
        onDismissRequest = onDismissRequest,
        kind = SheetKind.Confirm,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.medium),
        ) {
            Box(Modifier.weight(1f)) {
                PillButton(
                    label = stringResource(MR.strings.migrationListScreen_exitDialog_cancelLabel),
                    onClick = onDismissRequest,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(Modifier.weight(1f)) {
                PillButton(
                    label = stringResource(MR.strings.migrationListScreen_exitDialog_stopLabel),
                    onClick = exitMigration,
                    modifier = Modifier.fillMaxWidth(),
                    destructive = true,
                )
            }
        }
    }
}

/** Wave 2 WP11.3: DetentSheet port of `MigrationMangaDialog`; counts and labels are unchanged. */
@Composable
fun MigrationMangaSheet(
    onDismissRequest: () -> Unit,
    copy: Boolean,
    totalCount: Int,
    skippedCount: Int,
    onMigrate: () -> Unit,
) {
    DetentSheet(
        visible = true,
        title = pluralStringResource(
            resource = if (copy) {
                MR.plurals.migrationListScreen_migrateDialog_copyTitle
            } else {
                MR.plurals.migrationListScreen_migrateDialog_migrateTitle
            },
            count = totalCount,
            totalCount,
        ),
        onDismissRequest = onDismissRequest,
        kind = SheetKind.Confirm,
    ) {
        if (skippedCount > 0) {
            TsuzukiText(
                pluralStringResource(
                    resource = MR.plurals.migrationListScreen_migrateDialog_skipText,
                    count = skippedCount,
                    skippedCount,
                ),
                Modifier.padding(bottom = TsuzukiSpacing.medium),
                TsuzukiTheme.typography.body,
                TsuzukiTheme.colors.text,
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.medium),
        ) {
            Box(Modifier.weight(1f)) {
                PillButton(
                    label = stringResource(MR.strings.migrationListScreen_migrateDialog_cancelLabel),
                    onClick = onDismissRequest,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(Modifier.weight(1f)) {
                PillButton(
                    label = stringResource(
                        if (copy) {
                            MR.strings.migrationListScreen_migrateDialog_copyLabel
                        } else {
                            MR.strings.migrationListScreen_migrateDialog_migrateLabel
                        },
                    ),
                    onClick = onMigrate,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * Wave 2 WP11.3: DetentSheet port of `MigrationProgressDialog`. Upstream blocked back and
 * outside taps so the only exit was Cancel; a sheet cannot block its own drag, so every
 * dismissal path routes to the Cancel action instead of leaving a hidden modal behind.
 * NaN progress sweeps instead of showing nothing.
 */
@Composable
fun MigrationProgressSheet(
    progress: Float,
    exitMigration: () -> Unit,
) {
    DetentSheet(
        visible = true,
        title = stringResource(MR.strings.migrationListScreenTitle),
        onDismissRequest = exitMigration,
        kind = SheetKind.Confirm,
        scrollContent = false,
    ) {
        val animated by animateFloatAsState(
            targetValue = progress.coerceIn(0f, 1f),
            animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
            label = "migration_progress",
        )
        WavyProgress(
            if (progress.isNaN()) null else animated,
            Modifier.fillMaxWidth().padding(vertical = TsuzukiSpacing.section),
        )
        PillButton(
            label = stringResource(MR.strings.migrationListScreen_progressDialog_cancelLabel),
            onClick = exitMigration,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
