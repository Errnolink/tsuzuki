package dev.errnolink.tsuzuki.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.InsetGroupedList
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import eu.kanade.presentation.more.onboarding.OnboardingStep

internal class GuidesStep(
    private val onRestoreBackup: () -> Unit,
) : OnboardingStep {

    override val isComplete: Boolean = true

    @Composable
    override fun Content() {
        val handler = LocalUriHandler.current

        InsetGroupedList(
            footer = stringResource(MR.strings.onboarding_guides_returning_user, stringResource(MR.strings.app_name)),
        ) {
            GroupedRow(
                title = stringResource(MR.strings.getting_started_guide),
                subtitle = stringResource(MR.strings.onboarding_guides_new_user, stringResource(MR.strings.app_name)),
                chevron = true,
                onClick = { handler.openUri(GETTING_STARTED_URL) },
            )
            GroupedRow(
                title = stringResource(MR.strings.pref_restore_backup),
                chevron = true,
                onClick = onRestoreBackup,
                divider = false,
            )
        }
    }
}

const val GETTING_STARTED_URL = "https://mihon.app/docs/guides/getting-started"
