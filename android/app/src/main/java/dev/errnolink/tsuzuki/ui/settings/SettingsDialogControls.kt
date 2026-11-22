package dev.errnolink.tsuzuki.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.TsuzukiSize

@Composable
fun SettingsAlert(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    val colors = TsuzukiTheme.colors
    Dialog(onDismissRequest, properties = properties) {
        Column(
            modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = 640.dp)
                .clip(RoundedCornerShape(24.dp)).background(colors.grouped).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CompositionLocalProvider(LocalContentColor provides colors.text) {
                title?.let { ProvideTextStyle(TsuzukiTheme.typography.headline, it) }
                text?.let { body ->
                    Box(Modifier.weight(1f, fill = false)) { ProvideTextStyle(TsuzukiTheme.typography.callout, body) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}

@Composable
fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
) {
    FieldLabel(label) {
        BasicTextField(
            value,
            onValueChange,
            modifier.fillMaxWidth(),
            textStyle = TsuzukiTheme.typography.body.copy(color = TsuzukiTheme.colors.text),
            singleLine = singleLine,
            maxLines = maxLines,
            cursorBrush = SolidColor(TsuzukiTheme.colors.accent),
            decorationBox = { FieldDecoration(isError, trailingIcon, it) },
        )
    }
}

@Composable
fun SettingsTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    FieldLabel(label) {
        BasicTextField(
            value,
            onValueChange,
            modifier.fillMaxWidth(),
            textStyle = TsuzukiTheme.typography.body.copy(color = TsuzukiTheme.colors.text),
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            cursorBrush = SolidColor(TsuzukiTheme.colors.accent),
            decorationBox = { FieldDecoration(isError, trailingIcon, it) },
        )
    }
}

@Composable
fun SettingsTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    readOnly: Boolean = false,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.SingleLine,
) {
    FieldLabel(label) {
        BasicTextField(
            state = state,
            modifier = modifier.fillMaxWidth(),
            readOnly = readOnly,
            textStyle = TsuzukiTheme.typography.body.copy(color = TsuzukiTheme.colors.text),
            keyboardOptions = keyboardOptions,
            lineLimits = lineLimits,
            cursorBrush = SolidColor(TsuzukiTheme.colors.accent),
            decorator = { FieldDecoration(isError, null, it) },
        )
        supportingText?.let {
            CompositionLocalProvider(LocalContentColor provides if (isError) TsuzukiTheme.colors.destructive else TsuzukiTheme.colors.secondary) {
                ProvideTextStyle(TsuzukiTheme.typography.footnote, it)
            }
        }
    }
}

@Composable
private fun FieldLabel(label: (@Composable () -> Unit)?, field: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        label?.let {
            CompositionLocalProvider(LocalContentColor provides TsuzukiTheme.colors.secondary) {
                ProvideTextStyle(TsuzukiTheme.typography.footnote, it)
            }
        }
        field()
    }
}

@Composable
private fun FieldDecoration(isError: Boolean, trailing: (@Composable () -> Unit)?, field: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(TsuzukiCorners.field))
            .background(TsuzukiTheme.colors.elevated).defaultMinSize(minHeight = TsuzukiSpacing.rowMin)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { field() }
        CompositionLocalProvider(LocalContentColor provides if (isError) TsuzukiTheme.colors.destructive else TsuzukiTheme.colors.secondary) {
            trailing?.invoke()
        }
    }
}

@Composable
fun SettingsIconControl(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.size(TsuzukiSize.control).clip(CircleShape).background(TsuzukiTheme.colors.elevated)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}
