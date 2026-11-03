package dev.errnolink.tsuzuki.ui.reader

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.ui.details.DetailField
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun GoToPageSheet(
    visible: Boolean,
    currentPage: Int,
    totalPages: Int,
    onGo: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var pageText by remember(currentPage, visible) { mutableStateOf(currentPage.toString()) }
    DetentSheet(visible, "Go to page", onDismiss) {
        val target = pageText.toIntOrNull()
        DetailField(
            pageText,
            { pageText = it },
            "${stringResource(MR.strings.chapter_progress, 1).substringBefore(":")} (1–$totalPages)",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        if (target == null || target !in 1..totalPages) {
            TsuzukiText("Enter a page from 1 to $totalPages.", color = TsuzukiTheme.colors.destructive)
        }
        PillButton(
            stringResource(MR.strings.action_ok),
            { target?.takeIf { it in 1..totalPages }?.let { onGo(it - 1); onDismiss() } },
            enabled = target != null && target in 1..totalPages,
        )
    }
}
