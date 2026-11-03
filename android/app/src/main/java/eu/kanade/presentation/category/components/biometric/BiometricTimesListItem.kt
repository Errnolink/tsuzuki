package eu.kanade.presentation.category.components.biometric

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import eu.kanade.tachiyomi.ui.category.biometric.TimeRangeItem
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

// B34 legacy sweep: the Material elevated card is replaced by a grouped Tsuzuki row; the delete
// action keeps its upstream callback.
@Composable
fun BiometricTimesListItem(
    modifier: Modifier,
    timeRange: TimeRangeItem,
    onDelete: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TsuzukiTheme.colors.grouped)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.Label,
            contentDescription = null,
            tint = TsuzukiTheme.colors.secondary,
        )
        TsuzukiText(
            timeRange.formattedString,
            modifier = Modifier.weight(1f),
            style = TsuzukiTheme.typography.subhead,
        )
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(MR.strings.action_delete),
                tint = TsuzukiTheme.colors.destructive,
            )
        }
    }
}
