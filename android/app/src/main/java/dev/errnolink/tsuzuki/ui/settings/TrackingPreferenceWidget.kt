package dev.errnolink.tsuzuki.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.TsuzukiCheckMark
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import eu.kanade.presentation.track.components.TrackLogoIcon
import eu.kanade.tachiyomi.data.track.Tracker
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun TrackingPreferenceWidget(
    modifier: Modifier = Modifier,
    tracker: Tracker,
    checked: Boolean,
    onClick: (() -> Unit)? = null,
) {
    GroupedRow(
        title = tracker.name,
        modifier = modifier.preferenceHighlight(),
        divider = false,
        leading = { TrackLogoIcon(tracker) },
        trailing = {
            if (checked) {
                val loginSuccess = stringResource(MR.strings.login_success)
                Box(
                    Modifier
                        .size(24.dp)
                        .semantics { contentDescription = loginSuccess },
                    contentAlignment = Alignment.Center,
                ) {
                    TsuzukiCheckMark()
                }
            }
        },
        onClick = onClick,
    )
}
