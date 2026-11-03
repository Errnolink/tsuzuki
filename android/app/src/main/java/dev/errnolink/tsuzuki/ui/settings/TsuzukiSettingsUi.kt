package dev.errnolink.tsuzuki.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas as SliderCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.stateDescription
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.InsetGroupedList
import dev.errnolink.tsuzuki.designsystem.LargeTitleScaffold
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.TsuzukiCheckMark
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiMinusMark
import dev.errnolink.tsuzuki.designsystem.SliderRow
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.rememberGlassContext
import dev.errnolink.tsuzuki.ui.shell.LocalShellGlass
import kotlinx.coroutines.launch

@Composable
fun SettingsScaffold(
    title: String,
    navigateUp: (() -> Unit)?,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    reselectKey: String? = null,
    snackbar: SnackbarHostState? = null,
    actions: List<dev.errnolink.tsuzuki.ui.shell.ShellTopAction> = emptyList(),
    search: dev.errnolink.tsuzuki.designsystem.TopBarSearch? = null,
    onTitleClick: (() -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    itemSpacing: androidx.compose.ui.unit.Dp = dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing.section,
    content: LazyListScope.() -> Unit,
) {
    val shellGlass = LocalShellGlass.current
    val glassContext = shellGlass ?: rememberGlassContext()
    val largeTitles = dev.errnolink.tsuzuki.designsystem.largeTitlesEnabled()
    val collapse = dev.errnolink.tsuzuki.designsystem.rememberLargeTitleCollapse(state, largeTitles)
    LaunchedEffect(reselectKey, state) {
        eu.kanade.tachiyomi.ui.home.HomeScreen.reselectEvents.collect { if (it == reselectKey) state.scrollToItem(0) }
    }
    dev.errnolink.tsuzuki.ui.shell.DeclareShellTop(
        title = title,
        glass = glassContext,
        navigateUp = navigateUp,
        actions = actions,
        search = search,
        onTitleClick = onTitleClick,
        inlineTitle = { if (largeTitles) collapse.value else 1f },
    )
    Box(modifier.fillMaxSize()) {
        LargeTitleScaffold(
            title = title,
            modifier = Modifier.fillMaxSize(),
            state = state,
            glassContext = glassContext,
            largeTitles = largeTitles,
            collapse = collapse,
            bottomBar = bottomBar,
            bottomPadding = dev.errnolink.tsuzuki.ui.shell.shellBottomPadding(),
            itemSpacing = itemSpacing,
            content = content,
        )
        dev.errnolink.tsuzuki.ui.shell.ShellSnackbar(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = dev.errnolink.tsuzuki.ui.shell.shellBottomPadding() + 12.dp),
        )
    }
}

@Composable
fun SettingsIconButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable () -> Unit,
) {
    dev.errnolink.tsuzuki.designsystem.IconButton(
        label = label,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        icon = icon,
    )
}

@Composable
fun GroupDivider() {
    Spacer(
        Modifier
            .padding(start = 16.dp)
            .fillMaxWidth()
            .height(dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing.hairline)
            .background(TsuzukiTheme.colors.separator),
    )
}

@Composable
fun <T> ChoiceSheet(
    visible: Boolean,
    title: String,
    entries: List<Pair<T, String>>,
    selected: T?,
    onDismissRequest: () -> Unit,
    onSelect: (T) -> Unit,
) {
    DetentSheet(visible = visible, title = title, onDismissRequest = onDismissRequest) {
        InsetGroupedList {
            entries.forEachIndexed { index, (key, label) ->
                if (index > 0) GroupDivider()
                val selected = selected == key
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                        ) {
                            onDismissRequest()
                            onSelect(key)
                        }
                        .semantics {
                            if (selected) stateDescription = CheckStateOn else stateDescription = CheckStateOff
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TsuzukiText(label, Modifier.weight(1f))
                    if (selected) TsuzukiCheckMark()
                }
            }
        }
    }
}

@Composable
fun MultiChoiceSheet(
    visible: Boolean,
    title: String,
    footer: String? = null,
    doneLabel: String? = null,
    onDone: (() -> Unit)? = null,
    options: List<Triple<String, Boolean, (Boolean) -> Unit>>,
    onDismissRequest: () -> Unit,
) {
    DetentSheet(visible = visible, title = title, onDismissRequest = onDismissRequest) {
        InsetGroupedList {
            options.forEachIndexed { index, (label, checked, onChange) ->
                if (index > 0) GroupDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .toggleable(
                            value = checked,
                            role = Role.Checkbox,
                            onValueChange = { onChange(it) },
                        )
                        .semantics {
                            stateDescription = if (checked) CheckStateOn else CheckStateOff
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TsuzukiText(label, Modifier.weight(1f))
                    if (checked) TsuzukiCheckMark()
                }
            }
        }
        footer?.let {
            TsuzukiText(
                it,
                Modifier.padding(horizontal = 16.dp),
                TsuzukiTheme.typography.footnote,
                TsuzukiTheme.colors.secondary,
            )
        }
        if (doneLabel != null && onDone != null) {
            PillButton(doneLabel, onDone, prominent = false, modifier = Modifier.align(Alignment.End))
        }
    }
}

@Composable
fun TriStateChoiceSheet(
    visible: Boolean,
    title: String,
    message: String? = null,
    doneLabel: String,
    options: List<Triple<String, Int, (Int) -> Unit>>,
    onDismissRequest: () -> Unit,
    onDone: () -> Unit,
) {
    DetentSheet(visible = visible, title = title, onDismissRequest = onDismissRequest) {
        message?.let {
            TsuzukiText(it, Modifier.padding(horizontal = 16.dp), TsuzukiTheme.typography.footnote, TsuzukiTheme.colors.secondary)
        }
        InsetGroupedList {
            options.forEachIndexed { index, (label, state, onCycle) ->
                if (index > 0) GroupDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .clickable(role = Role.Checkbox) { onCycle((state + 1) % 3) }
                        .semantics {
                            stateDescription = when (state) {
                                1 -> CheckStateOn
                                2 -> CheckStatePartial
                                else -> CheckStateOff
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TsuzukiText(label, Modifier.weight(1f))
                    when (state) {
                        1 -> TsuzukiCheckMark()
                        2 -> TsuzukiMinusMark()
                    }
                }
            }
        }
        PillButton(doneLabel, onDone, prominent = false, modifier = Modifier.align(Alignment.End))
    }
}

@Composable
fun TextEntrySheet(
    visible: Boolean,
    title: String,
    initialValue: String,
    confirmLabel: String,
    errorText: String?,
    supportsConfirm: (String) -> Boolean,
    onConfirm: suspend (String) -> Boolean,
    onDismissRequest: () -> Unit,
    message: String? = null,
    placeholder: String? = null,
    centered: Boolean = false,
    dismissLabel: String? = null,
) {
    if (!visible) return
    val scope = rememberCoroutineScope()
    var fieldValue by rememberSaveable(visible, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialValue))
    }
    var saving by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    DetentSheet(visible = true, title = title, onDismissRequest = onDismissRequest, centered = centered) {
        message?.let {
            TsuzukiText(
                it,
                Modifier.padding(horizontal = 16.dp),
                TsuzukiTheme.typography.footnote,
                TsuzukiTheme.colors.secondary,
            )
        }
        BaseTextFieldRow(
            value = fieldValue,
            onValueChange = { fieldValue = it },
            modifier = Modifier.focusRequester(focusRequester),
            placeholder = placeholder,
        )
        if (fieldValue.text.isNotEmpty() && errorText != null && !supportsConfirm(fieldValue.text)) {
            TsuzukiText(
                errorText,
                Modifier.padding(horizontal = 16.dp),
                TsuzukiTheme.typography.footnote,
                TsuzukiTheme.colors.destructive,
            )
        }
        Row(
            Modifier.align(if (centered) Alignment.CenterHorizontally else Alignment.End),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (dismissLabel != null) {
                PillButton(dismissLabel, onDismissRequest, prominent = false)
            }
            PillButton(
                confirmLabel,
                enabled = supportsConfirm(fieldValue.text) && !saving,
                onClick = {
                    scope.launch {
                        saving = true
                        if (onConfirm(fieldValue.text)) onDismissRequest()
                        saving = false
                    }
                },
            )
        }
    }
}

@Composable
fun ConfirmSheet(
    visible: Boolean,
    title: String,
    message: String? = null,
    confirmLabel: String,
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    DetentSheet(visible = visible, title = title, onDismissRequest = onDismissRequest) {
        message?.let {
            TsuzukiText(it, Modifier.padding(horizontal = 16.dp), TsuzukiTheme.typography.body, TsuzukiTheme.colors.secondary)
        }
        PillButton(
            label = confirmLabel,
            onClick = { onDismissRequest(); onConfirm() },
            destructive = destructive,
        )
    }
}

@Composable
fun TsuzukiSliderRow(
    title: String,
    value: Int,
    valueString: String,
    range: IntProgression,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    SliderRow(
        title = title,
        value = value,
        valueString = valueString,
        range = range,
        enabled = enabled,
        onChange = onChange,
    )
}
