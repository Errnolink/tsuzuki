package dev.errnolink.tsuzuki.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.ui.details.DetailIcon

@Composable
fun ChapterNavigator(
    onNextChapter: () -> Unit,
    enabledNext: Boolean,
    onPreviousChapter: () -> Unit,
    enabledPrevious: Boolean,
    currentPage: Int,
    currentPageText: String,
    totalPages: Int,
    onPageIndexChange: (Int) -> Unit,
    enabled: Boolean,
) {
    val haptics = TsuzukiTheme.haptics
    var preview by remember { mutableStateOf<Int?>(null) }
    var showJump by remember { mutableStateOf(false) }
    val readout = if (totalPages > 0) "${preview?.toString() ?: currentPageText} of $totalPages" else "No pages"
    val pageReadout: @Composable () -> Unit = {
        TsuzukiText(
            readout,
            Modifier.clickable(enabled = enabled && totalPages > 0, role = Role.Button, onClick = { showJump = true })
                .padding(horizontal = 12.dp, vertical = 12.dp),
            TsuzukiTheme.typography.subhead,
        )
    }
    Column(Modifier.fillMaxHeight().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        DetailIcon("Previous chapter", TsuzukiIcons.Back, { haptics.chapterArm(); onPreviousChapter() }, Modifier.rotate(90f), enabled && enabledPrevious)
        pageReadout()
        ReaderSlider(
            currentPage, 1..totalPages.coerceAtLeast(1), { onPageIndexChange(it - 1) },
            Modifier.weight(1f), vertical = true, enabled = enabled && totalPages > 1, onPreview = { preview = it },
        )
        DetailIcon("Next chapter", TsuzukiIcons.Forward, { haptics.chapterArm(); onNextChapter() }, Modifier.rotate(90f), enabled && enabledNext)
    }
    GoToPageSheet(showJump, currentPage, totalPages, onPageIndexChange) { showJump = false }
}
