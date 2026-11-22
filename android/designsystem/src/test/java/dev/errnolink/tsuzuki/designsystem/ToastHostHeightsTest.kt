package dev.errnolink.tsuzuki.designsystem

import androidx.compose.runtime.snapshots.Snapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Toast height accounting: a posted toast is visible, so a reported pill height must reach the
 * host as snapshot state — the stack pose reads these heights every frame, and a toast stuck at
 * zero height renders invisible.
 */
class ToastHostHeightsTest {

    @Test
    fun `a newly posted toast gets non-zero height`() {
        val state = ToastHostState()
        val handle = state.show("Chapters updated")
        assertEquals(0, state.heightOf(handle.id))

        var applies = 0
        val observer = Snapshot.registerApplyObserver { _, _ -> applies++ }

        // The pill lays out and reports its natural height; the host accounts for it and every
        // reader of the heights (stack pose, clamps) is invalidated by the write.
        state.setHeight(handle.id, 148)
        Snapshot.sendApplyNotifications()
        observer.dispose()

        assertEquals(1, applies)
        assertEquals(148, state.heightOf(handle.id))
    }

    @Test
    fun `pruning a toast drops its height from the map`() {
        val state = ToastHostState()
        val handle = state.show("bye")
        state.setHeight(handle.id, 64)
        assertEquals(64, state.heightOf(handle.id))
        state.dismiss(handle.id)
        val entry = state.entries.single()
        state.prune(entry)
        assertEquals(0, state.heightOf(handle.id))
        assertTrue(state.entries.isEmpty())
    }
}
