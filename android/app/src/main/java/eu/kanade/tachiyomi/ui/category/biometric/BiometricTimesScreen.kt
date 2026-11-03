package eu.kanade.tachiyomi.ui.category.biometric

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.google.android.material.timepicker.MaterialTimePicker
import eu.kanade.presentation.category.BiometricTimesScreen
import eu.kanade.presentation.category.components.CategoryDeleteDialog
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.errnolink.tsuzuki.designsystem.ProgressRing
import dev.errnolink.tsuzuki.ui.settings.SettingsScaffold
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class BiometricTimesScreen : Screen() {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { BiometricTimesScreenModel() }

        val state by screenModel.state.collectAsState()

        if (state is BiometricTimesScreenState.Loading) {
            SettingsScaffold(
                title = stringResource(SYMR.strings.biometric_lock_times),
                navigateUp = navigator::pop,
            ) {
                item(key = "loading") {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        ProgressRing(progress = 0f, label = stringResource(MR.strings.loading))
                    }
                }
            }
            return
        }

        val successState = state as BiometricTimesScreenState.Success

        BiometricTimesScreen(
            state = successState,
            onClickCreate = { screenModel.showDialog(BiometricTimesDialog.Create) },
            onClickDelete = { screenModel.showDialog(BiometricTimesDialog.Delete(it)) },
            navigateUp = navigator::pop,
        )

        fun showTimePicker(startTime: Duration? = null) {
            val activity = context as? MainActivity ?: return
            val picker = MaterialTimePicker.Builder()
                .setTitleText(
                    if (startTime ==
                        null
                    ) {
                        SYMR.strings.biometric_lock_start_time.getString(context)
                    } else {
                        SYMR.strings.biometric_lock_end_time.getString(context)
                    },
                )
                .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
                .build()
            picker.addOnPositiveButtonClickListener {
                val timeRange = picker.hour.hours + picker.minute.minutes
                if (startTime != null) {
                    screenModel.dismissDialog()
                    screenModel.createTimeRange(TimeRange(startTime, timeRange))
                } else {
                    showTimePicker(timeRange)
                }
            }
            picker.addOnDismissListener {
                screenModel.dismissDialog()
            }
            picker.show(activity.supportFragmentManager, null)
        }

        when (val dialog = successState.dialog) {
            null -> {}
            BiometricTimesDialog.Create -> {
                LaunchedEffect(Unit) {
                    showTimePicker()
                }
            }
            is BiometricTimesDialog.Delete -> {
                CategoryDeleteDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onDelete = { screenModel.deleteTimeRanges(dialog.timeRange) },
                    title = stringResource(SYMR.strings.delete_time_range),
                    text = stringResource(SYMR.strings.delete_time_range_confirmation, dialog.timeRange.formattedString),
                )
            }
        }

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                if (event is BiometricTimesEvent.LocalizedMessage) {
                    context.toast(event.stringRes)
                }
            }
        }
    }
}
