package dev.errnolink.tsuzuki.ui.settings.debug

import dev.errnolink.tsuzuki.ui.settings.SettingsSectionHeader

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.ui.settings.SettingsScaffold
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.Copy
import dev.errnolink.tsuzuki.ui.shell.ShellTopAction
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.util.lang.toDateTimestampString
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.workManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import androidx.compose.ui.util.fastForEach
import androidx.work.WorkInfo
import androidx.work.WorkQuery
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class TsuzukiWorkerInfoScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { Model(context) }
        val enqueued by screenModel.enqueued.collectAsState()
        val finished by screenModel.finished.collectAsState()
        val running by screenModel.running.collectAsState()

        SettingsScaffold(
            title = TITLE,
            navigateUp = { navigator.pop() },
            actions = listOf(
                ShellTopAction(
                    stringResource(MR.strings.action_copy_to_clipboard),
                    TsuzukiIcons.Copy,
                    { context.copyToClipboard(TITLE, enqueued + finished + running) },
                ),
            ),
        ) {
            listOf(
                "Enqueued" to enqueued,
                "Finished" to finished,
                "Running" to running,
            ).forEachIndexed { index, (section, text) ->
                item(key = "worker-section-$index") {
                    WorkerSection(title = section, text = text)
                }
            }
        }
    }

    @Composable
    private fun WorkerSection(title: String, text: String) {
        Column(Modifier.padding(horizontal = TsuzukiSpacing.gutter)) {
            SettingsSectionHeader(title)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(TsuzukiCorners.group))
                    .background(TsuzukiTheme.colors.grouped),
            ) {
                TsuzukiText(
                    text,
                    Modifier
                        .padding(16.dp)
                        .horizontalScroll(rememberScrollState()),
                    TsuzukiTheme.typography.footnote.copy(fontFamily = FontFamily.Monospace),
                    TsuzukiTheme.colors.text,
                )
            }
        }
    }

    private class Model(context: Context) : ScreenModel {
        private val workManager = context.workManager

        val finished = workManager
            .getWorkInfosFlow(
                WorkQuery.fromStates(WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED, WorkInfo.State.CANCELLED),
            )
            .map(::constructString)
            .stateIn(ioCoroutineScope, SharingStarted.WhileSubscribed(), "")

        val running = workManager
            .getWorkInfosFlow(WorkQuery.fromStates(WorkInfo.State.RUNNING))
            .map(::constructString)
            .stateIn(ioCoroutineScope, SharingStarted.WhileSubscribed(), "")

        val enqueued = workManager
            .getWorkInfosFlow(WorkQuery.fromStates(WorkInfo.State.ENQUEUED))
            .map(::constructString)
            .stateIn(ioCoroutineScope, SharingStarted.WhileSubscribed(), "")

        private fun constructString(list: List<WorkInfo>) = buildString {
            if (list.isEmpty()) {
                appendLine("-")
            } else {
                list.fastForEach { workInfo ->
                    appendLine("Id: ${workInfo.id}")
                    appendLine("Tags:")
                    workInfo.tags.forEach {
                        appendLine(" - $it")
                    }
                    appendLine("State: ${workInfo.state}")
                    if (workInfo.state == WorkInfo.State.ENQUEUED) {
                        val timestamp = LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(workInfo.nextScheduleTimeMillis),
                            ZoneId.systemDefault(),
                        )
                            .toDateTimestampString(
                                UiPreferences.dateFormat(
                                    Injekt.get<UiPreferences>().dateFormat().get(),
                                ),
                            )
                        appendLine("Next scheduled run: $timestamp")
                        appendLine("Attempt #${workInfo.runAttemptCount + 1}")
                    }
                    appendLine()
                }
            }
        }
    }

    companion object {
        const val TITLE = "Worker info"
    }
}
