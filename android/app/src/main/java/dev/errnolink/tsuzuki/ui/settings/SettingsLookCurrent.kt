package dev.errnolink.tsuzuki.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme

/** Settings/main badge leading width; dividers derive their inset from it (S1). */
internal val SettingsSectionBadgeSize = 29.dp

@Composable
internal fun SettingsSectionBadgeCurrent(icon: ImageVector) {
    Box(
        Modifier
            .size(SettingsSectionBadgeSize)
            .background(
                TsuzukiTheme.colors.secondary.copy(alpha = 0.12f),
                RoundedCornerShape(TsuzukiSpacing.small),
            )
            .padding(TsuzukiSpacing.tiny),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            icon,
            null,
            Modifier.size(17.dp),
            tint = TsuzukiTheme.colors.secondary,
        )
    }
}
