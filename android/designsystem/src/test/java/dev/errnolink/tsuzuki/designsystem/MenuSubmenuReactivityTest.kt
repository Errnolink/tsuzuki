package dev.errnolink.tsuzuki.designsystem

import androidx.compose.runtime.snapshots.Snapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * An open submenu must follow the menu's live state: checkmarks, sort direction and multi-select
 * keep moving while the panel is up, not freeze at the snapshot taken when the row was tapped.
 */
class MenuSubmenuReactivityTest {

    @Test
    fun `open submenu reflects a live state change while open`() {
        var descending = false
        fun buildRows() = listOf(
            PullDownAction(
                "Sort",
                submenu = listOf(
                    PullDownAction("Newest first", selected = !descending, onClick = {}),
                    PullDownAction("Oldest first", selected = descending, onClick = {}),
                ),
                onClick = {},
            ),
        )
        val level = MenuLevelRows(buildRows())
        val opened = level.rows.single().submenu.orEmpty()
        assertEquals(false, level.submenuRows("Sort", opened).last().selected)

        var applies = 0
        val observer = Snapshot.registerApplyObserver { _, _ -> applies++ }

        // The user flips the sort direction while the panel is open; the level's rows refresh as
        // a snapshot write, invalidating the open panel's scope, and its rows now show it.
        descending = true
        level.rows = buildRows()
        Snapshot.sendApplyNotifications()
        observer.dispose()

        assertEquals(1, applies)
        assertEquals(true, level.submenuRows("Sort", opened).last().selected)
    }

    @Test
    fun `a submenu row missing from the live level keeps its opened rows`() {
        val opened = listOf(PullDownAction("Only these", selected = true, onClick = {}))
        val level = MenuLevelRows(listOf(PullDownAction("Unrelated", onClick = {})))
        assertSame(opened, level.submenuRows("Languages", opened))
        assertSame(opened, null.submenuRows("Languages", opened))
    }
}
