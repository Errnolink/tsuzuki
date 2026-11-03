package dev.errnolink.tsuzuki.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

internal val PrefsHorizontalPadding = 16.dp
internal val PrefsVerticalPadding = 12.dp
internal val TrailingWidgetBuffer = 16.dp

@Composable
internal fun Modifier.preferenceHighlight(): Modifier {
    val highlighted = LocalPreferenceHighlighted.current
    var flag by remember { mutableStateOf(false) }
    LaunchedEffect(highlighted) {
        if (highlighted) {
            flag = true
            delay(2.seconds)
            flag = false
        }
    }
    val color by animateColorAsState(
        if (flag) TsuzukiTheme.colors.selectedFill.copy(alpha = 0.6f) else Color.Transparent,
        if (TsuzukiTheme.motion.reduced) {
            snap()
        } else if (flag) {
            tween(150)
        } else {
            tween(200)
        },
        label = "preference-highlight",
    )
    return this.background(color)
}





@Composable
internal fun BaseTextFieldRow(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
) {
    val colors = TsuzukiTheme.colors
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(TsuzukiCorners.field))
            .background(colors.grouped)
            .heightIn(min = 44.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            textStyle = TsuzukiTheme.typography.body.copy(color = colors.text),
            cursorBrush = SolidColor(colors.accent),
            singleLine = singleLine,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            decorationBox = { inner ->
                Box {
                    if (value.text.isEmpty() && placeholder != null) {
                        TsuzukiText(placeholder, color = colors.secondary, maxLines = 1)
                    }
                    inner()
                }
            },
        )
    }
}

internal const val CheckStateOn = "Selected"
internal const val CheckStateOff = "Not selected"
internal const val CheckStatePartial = "Excluded"
