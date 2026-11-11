package dev.errnolink.tsuzuki.lab

import android.content.ClipData
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.InsetGroupedList
import dev.errnolink.tsuzuki.designsystem.PullDownAction
import dev.errnolink.tsuzuki.designsystem.PullDownMenu
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Screen
import dev.errnolink.tsuzuki.ui.settings.SettingsScaffold
import dev.errnolink.tsuzuki.ui.settings.TextEntrySheet
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val Presets = listOf("current", "glass", "liquid", "expressive", "blend")

object DesignLabScreen : Screen() {

    @Composable
    override fun Content() {
        val backPress = LocalBackPress.currentOrThrow
        val context = LocalContext.current
        val clipboard = LocalClipboard.current
        val scope = rememberCoroutineScope()
        val snackbar = remember { SnackbarHostState() }
        val state = rememberLazyListState()
        var pasteOpen by remember { mutableStateOf(false) }

        val writeMix = {
            val dir = context.getExternalFilesDir(null)
            if (dir != null) {
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        File(dir, LabMix.MIX_FILE_NAME).writeText(LabMix.encode(DesignLab.currentValues()))
                    }
                }
            }
        }

        LaunchedEffect(Unit) { writeMix() }

        SettingsScaffold(
            title = "Design lab",
            navigateUp = backPress::invoke,
            state = state,
            snackbar = snackbar,
        ) {
            item("lab-presets") {
                InsetGroupedList {
                    Presets.forEachIndexed { index, preset ->
                        GroupedRow(
                            title = "Everything $preset",
                            divider = index < Presets.size - 1,
                            onClick = {
                                DesignLab.applyPreset(preset)
                                writeMix()
                            },
                        )
                    }
                    GroupedRow(
                        title = "Copy my mix",
                        divider = true,
                        onClick = {
                            scope.launch {
                                clipboard.setClipEntry(
                                    ClipData.newPlainText("design-lab", LabMix.encode(DesignLab.currentValues())).toClipEntry(),
                                )
                            }
                        },
                    )
                    GroupedRow(
                        title = "Paste a mix",
                        divider = false,
                        onClick = { pasteOpen = true },
                    )
                }
            }
            DesignLab.areas.forEach { area ->
                item("lab-area-$area") {
                    InsetGroupedList(title = area) {
                        val options = DesignLab.options(area)
                        options.forEachIndexed { index, option ->
                            LabOptionRow(option, divider = index < options.size - 1, onChange = writeMix)
                        }
                    }
                }
            }
        }

        TextEntrySheet(
            visible = pasteOpen,
            title = "Paste a mix",
            initialValue = "",
            confirmLabel = "Apply",
            errorText = null,
            supportsConfirm = { it.isNotBlank() },
            onConfirm = { text ->
                val result = LabMix.decode(text)
                DesignLab.applyMix(result)
                writeMix()
                reportPaste(result, snackbar)
                true
            },
            onDismissRequest = { pasteOpen = false },
            placeholder = LabMix.VERSION + ";pressShape=on;tabBar=liquid",
        )
    }

    private suspend fun reportPaste(result: LabMix.Result, snackbar: SnackbarHostState) {
        if (!result.malformed && result.ignoredKeys.isEmpty() && result.ignoredValues.isEmpty()) return
        val message = when {
            result.malformed -> "Not a design lab mix"
            else -> buildString {
                append("Ignored")
                if (result.ignoredKeys.isNotEmpty()) append(" keys ${result.ignoredKeys.joinToString()}")
                if (result.ignoredValues.isNotEmpty()) append(" values ${result.ignoredValues.joinToString()}")
            }
        }
        snackbar.showSnackbar(message)
    }
}

@Composable
private fun LabOptionRow(option: LabOption<*>, divider: Boolean, onChange: () -> Unit) {
    val current by option.stateWire()
    var menuOpen by remember { mutableStateOf(false) }
    GroupedRow(
        title = option.title,
        subtitle = if (option.built) option.summary else "not built yet",
        divider = divider,
        onClick = { menuOpen = true },
        trailing = {
            Box {
                TsuzukiText(
                    option.labelOf(current),
                    style = TsuzukiTheme.typography.subhead,
                    color = TsuzukiTheme.colors.secondary,
                )
                PullDownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    paneTitle = option.title,
                    actions = option.choices.map { choice ->
                        val blocked = option.choiceBlocked(choice.name) { key -> DesignLab.currentName(key) ?: "" }
                        PullDownAction(
                            label = option.labelOf(choice.name),
                            subtitle = blocked?.reason ?: option.summaryOf(choice.name),
                            enabled = blocked == null,
                            selected = choice.name == current,
                            onClick = {
                                menuOpen = false
                                option.setWire(choice.name)
                                onChange()
                            },
                        )
                    },
                )
            }
        },
    )
}
