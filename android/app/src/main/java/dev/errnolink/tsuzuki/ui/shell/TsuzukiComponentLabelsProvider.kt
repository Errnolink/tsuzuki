package dev.errnolink.tsuzuki.ui.shell

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import dev.errnolink.tsuzuki.designsystem.TsuzukiComponentLabels
import dev.icerock.moko.resources.StringResource
import tachiyomi.core.common.i18n.stringResource as resolveContextString
import tachiyomi.i18n.MR

internal fun buildTsuzukiComponentLabels(
    resolve: (StringResource) -> String,
    resolveFormatted: (StringResource, Array<out Any>) -> String,
): TsuzukiComponentLabels = TsuzukiComponentLabels(
    resizeSheet = resolve(MR.strings.tsuzuki_resize_sheet),
    sheetFit = resolve(MR.strings.tsuzuki_sheet_fit),
    sheetMedium = resolve(MR.strings.tsuzuki_sheet_medium),
    sheetLarge = resolve(MR.strings.tsuzuki_sheet_large),
    closeSheet = { title -> resolveFormatted(MR.strings.tsuzuki_close_sheet, arrayOf(title)) },
    showActionsFor = { label -> resolveFormatted(MR.strings.tsuzuki_show_actions_for, arrayOf(label)) },
    clearSearch = resolve(MR.strings.tsuzuki_clear_search),
    collapseSubmenu = resolve(MR.strings.tsuzuki_collapse_submenu),
    selected = resolve(MR.strings.tsuzuki_selected),
    menu = resolve(MR.strings.tsuzuki_menu),
    confirm = resolve(MR.strings.tsuzuki_confirm),
    cancel = resolve(MR.strings.action_cancel),
    loading = resolve(MR.strings.tsuzuki_loading),
    removeItem = { label -> resolveFormatted(MR.strings.tsuzuki_remove_item, arrayOf(label)) },
    triStateOff = resolve(MR.strings.tsuzuki_any),
    triStateInclude = resolve(MR.strings.tsuzuki_only_these),
    triStateExclude = resolve(MR.strings.tsuzuki_hidden),
    dismiss = resolve(MR.strings.tsuzuki_dismiss),
    dismissNotice = resolve(MR.strings.tsuzuki_dismiss_notice),
    dismissNotification = resolve(MR.strings.tsuzuki_dismiss_notification),
    search = resolve(MR.strings.tsuzuki_search),
    done = resolve(MR.strings.tsuzuki_done),
    decreaseItem = { label -> resolveFormatted(MR.strings.tsuzuki_decrease_item, arrayOf(label)) },
    increaseItem = { label -> resolveFormatted(MR.strings.tsuzuki_increase_item, arrayOf(label)) },
    percentRead = { label, percent -> resolveFormatted(MR.strings.tsuzuki_percent_read, arrayOf<Any>(label, percent)) },
)

@Composable
fun rememberTsuzukiComponentLabels(context: Context = LocalContext.current): TsuzukiComponentLabels {
    val configuration = LocalConfiguration.current
    return remember(context, configuration) {
        buildTsuzukiComponentLabels(
            resolve = { res -> context.resolveContextString(res) },
            resolveFormatted = { res, args -> context.resolveContextString(res, *args) },
        )
    }
}
