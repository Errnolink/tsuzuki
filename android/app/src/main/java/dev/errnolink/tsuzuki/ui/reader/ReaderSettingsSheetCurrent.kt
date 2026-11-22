package dev.errnolink.tsuzuki.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.LiquidCard
import dev.errnolink.tsuzuki.designsystem.SegmentedControl
import dev.errnolink.tsuzuki.designsystem.SheetKind
import dev.errnolink.tsuzuki.designsystem.TsuzukiButton
import dev.errnolink.tsuzuki.designsystem.TsuzukiButtonSize
import dev.errnolink.tsuzuki.designsystem.TsuzukiButtonVariant
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun ReaderSettingsSheetCurrent(
    visible: Boolean = true,
    onDismissRequest: () -> Unit,
    onShowMenus: () -> Unit,
    onHideMenus: () -> Unit,
    screenModel: ReaderSettingsScreenModel,
) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val morphSources = LocalReaderMorphSources.current
    val dismiss = {
        onDismissRequest()
        onShowMenus()
    }
    LaunchedEffect(visible, page) {
        if (!visible) return@LaunchedEffect
        // RDR-09/WP4.6: only the Filters page hides the menus so users see color changes live.
        if (page == 2) onHideMenus() else onShowMenus()
    }
    if (morphSources != null) {
        LiquidCard(
            visible = visible,
            source = morphSources.settings,
            onDismissRequest = dismiss,
            maxHeight = 440.dp,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TsuzukiText(
                        stringResource(MR.strings.tsuzuki_reading_settings),
                        style = TsuzukiTheme.typography.headline.copy(fontWeight = FontWeight.Bold),
                    )
                    TsuzukiButton(
                        label = stringResource(MR.strings.tsuzuki_done),
                        onClick = dismiss,
                        size = TsuzukiButtonSize.Xs,
                        variant = TsuzukiButtonVariant.Soft,
                        minWidth = 0.dp,
                    )
                }
                SegmentedControl(
                    listOf(
                        stringResource(MR.strings.reading),
                        stringResource(MR.strings.pref_category_display),
                        stringResource(MR.strings.tsuzuki_filters),
                    ),
                    page,
                    { page = it },
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (page) {
                        0 -> ReadingModePage(screenModel)
                        1 -> GeneralPage(screenModel)
                        2 -> ColorFilterPage(screenModel)
                    }
                }
            }
        }
    } else if (visible) {
        DetentSheet(true, stringResource(MR.strings.tsuzuki_reading_settings), dismiss, kind = SheetKind.LiveSettings) {
            SegmentedControl(
                listOf(
                    stringResource(MR.strings.reading),
                    stringResource(MR.strings.pref_category_display),
                    stringResource(MR.strings.tsuzuki_filters),
                ),
                page,
                { page = it },
            )
            when (page) {
                0 -> ReadingModePage(screenModel)
                1 -> GeneralPage(screenModel)
                2 -> ColorFilterPage(screenModel)
            }
        }
    }
}
