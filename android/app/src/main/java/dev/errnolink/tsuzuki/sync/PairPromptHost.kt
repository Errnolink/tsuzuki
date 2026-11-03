package dev.errnolink.tsuzuki.sync

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import eu.kanade.tachiyomi.util.system.toast

/**
 * Hosts the pair confirmation sheet for whatever link [PairDeepLink] received,
 * whichever surface scanned it (phone camera or the in-app QR button).
 */
@Composable
fun PairPromptHost() {
    val context = LocalContext.current
    val request by PairDeepLink.pending.collectAsState()
    val current = request ?: return

    DetentSheet(
        visible = true,
        title = "Pair with ${current.name?.takeIf { it.isNotBlank() } ?: "this PC"}?",
        onDismissRequest = { PairDeepLink.dismiss() },
    ) {
        TsuzukiText(
            "Your library and reading progress will sync with this PC over Wi-Fi.",
            Modifier.padding(horizontal = 16.dp),
            TsuzukiTheme.typography.body,
            TsuzukiTheme.colors.secondary,
        )
        PillButton(
            label = "Pair",
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            onClick = {
                PairWithHub.pair(context, current)
                context.toast("Paired with ${current.name?.takeIf { it.isNotBlank() } ?: current.host}")
                PairDeepLink.dismiss()
            },
        )
        PillButton(
            label = "Cancel",
            prominent = false,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            onClick = { PairDeepLink.dismiss() },
        )
    }
}
