package dev.errnolink.tsuzuki.ui.shell

import dev.icerock.moko.resources.StringResource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.i18n.MR

class TsuzukiComponentLabelsProviderTest {

    @Test
    fun `buildTsuzukiComponentLabels resolves every static label and formatted lambda from resources`() {
        val seenPlain = mutableListOf<StringResource>()
        val labels = buildTsuzukiComponentLabels(
            resolve = { res ->
                seenPlain.add(res)
                "res:${res.resourceId}"
            },
            resolveFormatted = { res, args ->
                "fmt:${res.resourceId}:${args.joinToString(",")}"
            },
        )

        assertEquals("res:${MR.strings.tsuzuki_resize_sheet.resourceId}", labels.resizeSheet)
        assertEquals("res:${MR.strings.tsuzuki_sheet_fit.resourceId}", labels.sheetFit)
        assertEquals("res:${MR.strings.tsuzuki_sheet_medium.resourceId}", labels.sheetMedium)
        assertEquals("res:${MR.strings.tsuzuki_sheet_large.resourceId}", labels.sheetLarge)
        assertEquals("res:${MR.strings.tsuzuki_clear_search.resourceId}", labels.clearSearch)
        assertEquals("res:${MR.strings.tsuzuki_collapse_submenu.resourceId}", labels.collapseSubmenu)
        assertEquals("res:${MR.strings.tsuzuki_selected.resourceId}", labels.selected)
        assertEquals("res:${MR.strings.tsuzuki_menu.resourceId}", labels.menu)
        assertEquals("res:${MR.strings.tsuzuki_confirm.resourceId}", labels.confirm)
        assertEquals("res:${MR.strings.action_cancel.resourceId}", labels.cancel)
        assertEquals("res:${MR.strings.tsuzuki_loading.resourceId}", labels.loading)
        assertEquals("res:${MR.strings.tsuzuki_any.resourceId}", labels.triStateOff)
        assertEquals("res:${MR.strings.tsuzuki_only_these.resourceId}", labels.triStateInclude)
        assertEquals("res:${MR.strings.tsuzuki_hidden.resourceId}", labels.triStateExclude)
        assertEquals("res:${MR.strings.tsuzuki_dismiss.resourceId}", labels.dismiss)
        assertEquals("res:${MR.strings.tsuzuki_dismiss_notice.resourceId}", labels.dismissNotice)
        assertEquals("res:${MR.strings.tsuzuki_dismiss_notification.resourceId}", labels.dismissNotification)
        assertEquals("res:${MR.strings.tsuzuki_search.resourceId}", labels.search)
        assertEquals("res:${MR.strings.tsuzuki_done.resourceId}", labels.done)

        assertEquals(seenPlain.size, seenPlain.toSet().size)

        assertEquals("fmt:${MR.strings.tsuzuki_close_sheet.resourceId}:Details", labels.closeSheet("Details"))
        assertEquals("fmt:${MR.strings.tsuzuki_show_actions_for.resourceId}:Ch. 1", labels.showActionsFor("Ch. 1"))
        assertEquals("fmt:${MR.strings.tsuzuki_remove_item.resourceId}:Tag", labels.removeItem("Tag"))
        assertEquals("fmt:${MR.strings.tsuzuki_decrease_item.resourceId}:Columns", labels.decreaseItem("Columns"))
        assertEquals("fmt:${MR.strings.tsuzuki_increase_item.resourceId}:Columns", labels.increaseItem("Columns"))
        assertEquals("fmt:${MR.strings.tsuzuki_percent_read.resourceId}:Ch. 4,75", labels.percentRead("Ch. 4", 75))
    }
}
