package dev.errnolink.tsuzuki.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.errnolink.tsuzuki.designsystem.ToastHost
import dev.errnolink.tsuzuki.designsystem.ToastHostState

/**
 * The shell's toast slot: the Kasane stacked glass pill with swipe-to-dismiss, the promise morph
 * and pause-while-reading timers. Screens post through [ToastHostState]; the old
 * [androidx.compose.material3.SnackbarHostState] plumbing is gone.
 */
@Composable
fun ShellToasts(state: ToastHostState?, modifier: Modifier = Modifier) {
    if (state == null) return
    ToastHost(state, modifier, glass = LocalShellGlass.current)
}
