package dev.errnolink.tsuzuki.ui.manga

import androidx.annotation.ColorInt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.InsetGroupedList
import dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.ui.settings.SettingsScaffold
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Screen
import java.util.Locale

class PaletteScreen(
    @param:ColorInt private val seedColor: Int?,
) : Screen() {

    @Composable
    override fun Content() {
        val handleBack = LocalBackPress.current
        val colors = TsuzukiTheme.colors
        val swatches = listOf(
            "background" to colors.background,
            "grouped" to colors.grouped,
            "elevated" to colors.elevated,
            "text" to colors.text,
            "secondary" to colors.secondary,
            "separator" to colors.separator,
            "selectedFill" to colors.selectedFill,
            "glassTint" to colors.glassTint,
            "accent" to colors.accent,
            "onAccent" to colors.onAccent,
            "destructive" to colors.destructive,
            "success" to colors.success,
        )
        SettingsScaffold(
            title = buildString {
                append("Colors Palette")
                seedColor?.let {
                    append("  #")
                    append(String.format(Locale.ROOT, "%06X", it))
                }
            },
            navigateUp = handleBack?.let { { it() } },
        ) {
            item(key = "tsuzuki-palette-colors") {
                InsetGroupedList(title = "Theme colors") {
                    swatches.forEachIndexed { index, (name, color) ->
                        GroupedRow(
                            title = name,
                            value = String.format(Locale.ROOT, "#%08X", color.toArgb()),
                            divider = index < swatches.lastIndex,
                            leading = {
                                Box(
                                    Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(TsuzukiSpacing.hairline, colors.separator, CircleShape),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
