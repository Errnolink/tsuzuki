package dev.errnolink.tsuzuki.ui.shell

import eu.kanade.tachiyomi.extension.model.InstallStep
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InstallRowStateTest {

    @Test
    fun `queued install is busy with a cancel circle when cancellable`() {
        val view = installRowState(InstallStep.Pending, isAvailable = true, isInstalled = false, isUntrusted = false, hasUpdate = false, cancellable = true)
        assertTrue(view.busy)
        assertEquals(InstallRowTrailing.Cancel, view.trailing)
        assertFalse(view.failed)
    }

    @Test
    fun `queued install without cancel stays busy with no trailing`() {
        val view = installRowState(InstallStep.Downloading, isAvailable = true, isInstalled = false, isUntrusted = false, hasUpdate = false, cancellable = false)
        assertTrue(view.busy)
        assertEquals(InstallRowTrailing.None, view.trailing)
    }

    @Test
    fun `installing is busy`() {
        val view = installRowState(InstallStep.Installing, isAvailable = true, isInstalled = false, isUntrusted = false, hasUpdate = false, cancellable = false)
        assertTrue(view.busy)
        assertFalse(view.failed)
    }

    @Test
    fun `failed install offers retry`() {
        val view = installRowState(InstallStep.Error, isAvailable = true, isInstalled = false, isUntrusted = false, hasUpdate = false, cancellable = false)
        assertFalse(view.busy)
        assertTrue(view.failed)
        assertEquals(InstallRowTrailing.Action, view.trailing)
        assertEquals("Retry", view.actionLabel)
    }

    @Test
    fun `installed step shows installed text`() {
        val view = installRowState(InstallStep.Installed, isAvailable = true, isInstalled = true, isUntrusted = false, hasUpdate = false, cancellable = false)
        assertFalse(view.busy)
        assertEquals(InstallRowTrailing.Done, view.trailing)
        assertEquals("Installed", view.doneText)
    }

    @Test
    fun `installed extension without a step shows installed text`() {
        val view = installRowState(null, isAvailable = false, isInstalled = true, isUntrusted = false, hasUpdate = false, cancellable = false)
        assertEquals(InstallRowTrailing.Done, view.trailing)
        assertEquals("Installed", view.doneText)
    }

    @Test
    fun `installed extension with an update offers the update capsule`() {
        val view = installRowState(null, isAvailable = false, isInstalled = true, isUntrusted = false, hasUpdate = true, cancellable = false)
        assertEquals(InstallRowTrailing.Action, view.trailing)
        assertEquals("Update", view.actionLabel)
    }

    @Test
    fun `available extension offers install`() {
        val view = installRowState(null, isAvailable = true, isInstalled = false, isUntrusted = false, hasUpdate = false, cancellable = false)
        assertEquals(InstallRowTrailing.Action, view.trailing)
        assertEquals("Install", view.actionLabel)
    }

    @Test
    fun `untrusted extension offers trust`() {
        val view = installRowState(null, isAvailable = false, isInstalled = false, isUntrusted = true, hasUpdate = false, cancellable = false)
        assertEquals(InstallRowTrailing.Action, view.trailing)
        assertEquals("Trust", view.actionLabel)
    }
}
