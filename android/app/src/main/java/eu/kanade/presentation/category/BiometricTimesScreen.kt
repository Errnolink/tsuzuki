package eu.kanade.presentation.category

import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.errnolink.tsuzuki.designsystem.Plus
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.ui.settings.SettingsScaffold
import dev.errnolink.tsuzuki.ui.shell.ShellTopAction
import eu.kanade.presentation.category.components.biometric.BiometricTimesListItem
import eu.kanade.tachiyomi.ui.category.biometric.BiometricTimesScreenState
import eu.kanade.tachiyomi.ui.category.biometric.TimeRangeItem
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

// B34 legacy sweep: the Material Scaffold/AppBar and the floating action button are replaced by
// the Tsuzuki settings shell with an Add top action; the time-range rows render grouped.
@Composable
fun BiometricTimesScreen(
    state: BiometricTimesScreenState.Success,
    onClickCreate: () -> Unit,
    onClickDelete: (TimeRangeItem) -> Unit,
    navigateUp: () -> Unit,
) {
    val lazyListState = rememberLazyListState()
    SettingsScaffold(
        title = stringResource(SYMR.strings.biometric_lock_times),
        navigateUp = navigateUp,
        state = lazyListState,
        actions = listOf(
            ShellTopAction(
                stringResource(MR.strings.action_add),
                TsuzukiIcons.Plus,
                onClickCreate,
            ),
        ),
    ) {
        if (state.isEmpty) {
            item(key = "biometric-empty") {
                EmptyScreen(
                    SYMR.strings.biometric_lock_times_empty,
                    modifier = Modifier.fillParentMaxSize(),
                )
            }
        } else {
            items(state.timeRanges, key = { "biometric-${it.formattedString}" }) { timeRange ->
                BiometricTimesListItem(
                    modifier = Modifier.animateItem(),
                    timeRange = timeRange,
                    onDelete = { onClickDelete(timeRange) },
                )
            }
        }
    }
}
