package dev.errnolink.tsuzuki.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

val LocalGroupedListGutter = androidx.compose.runtime.staticCompositionLocalOf { TsuzukiSpacing.gutter }

@Composable
fun InsetGroupedList(
    modifier: Modifier = Modifier,
    title: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.padding(horizontal = LocalGroupedListGutter.current), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        title?.let {
            TsuzukiText(it, Modifier.padding(horizontal = 16.dp), TsuzukiTheme.typography.sectionHeader, TsuzukiTheme.colors.secondary)
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(TsuzukiCorners.group)).background(TsuzukiTheme.colors.grouped),
            content = content,
        )
        footer?.let {
            TsuzukiText(it, Modifier.padding(horizontal = 16.dp), TsuzukiTheme.typography.footnote, TsuzukiTheme.colors.secondary)
        }
    }
}

@Composable
fun GroupedRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    enabled: Boolean = true,
    divider: Boolean = true,
    destructive: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    require((checked == null) == (onCheckedChange == null))
    require(checked == null || onClick == null)
    val colors = TsuzukiTheme.colors
    val interaction = when {
        checked != null -> Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange!!)
        onClick != null -> Modifier.clickable(enabled = enabled, onClick = onClick)
        else -> Modifier
    }
    val stackValue = LocalDensity.current.fontScale >= 1.5f
    Column(modifier.then(interaction)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            leading?.invoke()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                TsuzukiText(title, color = if (!enabled) colors.secondary else if (destructive) colors.destructive else colors.text)
                subtitle?.let { TsuzukiText(it, style = TsuzukiTheme.typography.footnote, color = colors.secondary) }
                if (stackValue && value != null) TsuzukiText(value, style = TsuzukiTheme.typography.subhead, color = colors.secondary)
            }
            if (!stackValue && value != null) {
                Box(Modifier.weight(0.65f), contentAlignment = Alignment.CenterEnd) {
                    TsuzukiText(
                        value, style = TsuzukiTheme.typography.subhead, color = colors.secondary,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) trailing()
            if (checked != null) TsuzukiToggle(checked, enabled)
            if (chevron) TsuzukiChevron()
        }
        if (divider) Spacer(Modifier.padding(start = 16.dp).fillMaxWidth().height(TsuzukiSpacing.hairline).background(colors.separator))
    }
}

@Composable
fun TsuzukiToggle(checked: Boolean, enabled: Boolean = true) {
    val motion = TsuzukiTheme.motion
    val haptics = TsuzukiTheme.haptics
    val progress by animateFloatAsState(if (checked) 1f else 0f, if (motion.reduced) snap() else motion.tabSpring(), label = "Toggle")
    androidx.compose.runtime.LaunchedEffect(checked) {
        if (checked) haptics.toggleOn() else haptics.toggleOff()
    }
    val colors = TsuzukiTheme.colors
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val trackHeight = 31.dp
    val trackWidth = 51.dp
    val inset = 3.dp
    val knobHeight = trackHeight - inset * 2
    val knobWidth = knobHeight + 2.dp
    val knobTravel = trackWidth - knobWidth - inset * 2
    val position = if (rtl) 1f - progress else progress
    val offFill = androidx.compose.ui.graphics.Color(0xFF787880).copy(alpha = if (colors.isDark) 0.32f else 0.16f)
    val onFill = colors.accent
    androidx.compose.foundation.layout.Box(
        Modifier.size(trackWidth, trackHeight)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (checked) onFill else offFill),
    ) {
        Box(
            Modifier.offset(x = inset + knobTravel * position)
                .size(knobWidth, knobHeight)
                .align(Alignment.CenterStart)
                .shadow(1.dp, androidx.compose.foundation.shape.RoundedCornerShape(50))
                .background(androidx.compose.ui.graphics.Color.White, androidx.compose.foundation.shape.RoundedCornerShape(50)),
        )
    }
}

@Composable
fun TsuzukiChevron(modifier: Modifier = Modifier) {
    TsuzukiIcon(TsuzukiIcons.ChevronRight, null, modifier.size(18.dp), tint = TsuzukiTheme.colors.secondary)
}

@Composable
fun TsuzukiCheckMark(modifier: Modifier = Modifier) {
    TsuzukiIcon(TsuzukiIcons.Check, null, modifier.size(18.dp))
}

@Composable
fun TsuzukiMinusMark(modifier: Modifier = Modifier) {
    TsuzukiIcon(TsuzukiIcons.Minus, null, modifier.size(18.dp))
}

fun Modifier.insetDivider(start: Dp = 16.dp, fromEnd: Boolean = false): Modifier = composed {
    val color = TsuzukiTheme.colors.separator
    val hairline = TsuzukiSpacing.hairline
    drawWithContent {
        drawContent()
        val y = size.height - hairline.toPx() / 2f
        val x0 = if (fromEnd) 0f else start.toPx()
        val x1 = if (fromEnd) size.width - start.toPx() else size.width
        drawLine(color, Offset(x0, y), Offset(x1, y), hairline.toPx())
    }
}
