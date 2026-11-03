package dev.errnolink.tsuzuki.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.InsetGroupedList
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.icerock.moko.resources.StringResource
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun SettingsSection(titleRes: StringResource? = null, content: @Composable ColumnScope.() -> Unit) {
    InsetGroupedList(title = titleRes?.let { stringResource(it) }, content = content)
}

@Composable
fun SettingsOptionRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    divider: Boolean = true,
) {
    GroupedRow(label, modifier, checked = checked, onCheckedChange = onCheckedChange, enabled = enabled, divider = divider)
}
