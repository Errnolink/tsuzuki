package dev.errnolink.tsuzuki.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.RefreshCw
import dev.errnolink.tsuzuki.designsystem.TsuzukiCheckMark
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcon
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import eu.kanade.presentation.track.components.TrackLogoIcon
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun TrackingPreferenceWidget(
    modifier: Modifier = Modifier,
    tracker: Tracker,
    checked: Boolean,
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    GroupedRow(
        title = tracker.name,
        subtitle = if (checked) tracker.getDisplayUsername() else null,
        modifier = modifier.preferenceHighlight(),
        divider = false,
        leading = { TrackLogoIcon(tracker) },
        trailing = {
            if (checked) {
                if (onRefresh != null && tracker !is EnhancedTracker && tracker.id != TrackerManager.MDLIST) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(enabled = !isRefreshing) { onRefresh() },
                    ) {
                        if (isRefreshing) {
                            LoadingIndicator(Modifier.size(24.dp))
                        } else {
                            val refreshLabel = stringResource(MR.strings.tsuzuki_refresh_tracker_profile)
                            TsuzukiIcon(
                                imageVector = TsuzukiIcons.RefreshCw,
                                contentDescription = refreshLabel,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                val loginSuccess = stringResource(MR.strings.login_success)
                androidx.compose.foundation.layout.Box(
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
