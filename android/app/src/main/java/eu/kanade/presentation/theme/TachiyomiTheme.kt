package eu.kanade.presentation.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollFactory
import dev.errnolink.tsuzuki.ui.utils.rememberIosOverscrollFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.ThemeMode
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun TachiyomiTheme(
    content: @Composable () -> Unit,
) {
    val preferences = remember { Injekt.get<UiPreferences>() }
    val mode by preferences.themeMode().collectAsState()
    TsuzukiTheme(
        dark = when (mode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        },
    ) {
        BaseTachiyomiTheme(content)
    }
}


@Composable
fun TachiyomiPreviewTheme(
    content: @Composable () -> Unit,
) {
    TsuzukiTheme { BaseTachiyomiTheme(content) }
}

@Composable
private fun BaseTachiyomiTheme(content: @Composable () -> Unit) {
    val colors = TsuzukiTheme.colors
    val type = TsuzukiTheme.typography
    val indication = LocalIndication.current
    val scheme = remember(colors) {
        ColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            primaryContainer = colors.elevated,
            onPrimaryContainer = colors.text,
            inversePrimary = colors.accent,
            secondary = colors.secondary,
            onSecondary = colors.background,
            secondaryContainer = colors.elevated,
            onSecondaryContainer = colors.text,
            tertiary = colors.accent,
            onTertiary = colors.onAccent,
            tertiaryContainer = colors.elevated,
            onTertiaryContainer = colors.text,
            background = colors.background,
            onBackground = colors.text,
            surface = colors.grouped,
            onSurface = colors.text,
            surfaceVariant = colors.elevated,
            onSurfaceVariant = colors.secondary,
            surfaceTint = Color.Transparent,
            inverseSurface = colors.text,
            inverseOnSurface = colors.background,
            error = colors.destructive,
            onError = colors.background,
            errorContainer = colors.elevated,
            onErrorContainer = colors.destructive,
            outline = colors.separator,
            outlineVariant = colors.separator,
            scrim = Color.Black,
            surfaceBright = colors.elevated,
            surfaceDim = colors.background,
            surfaceContainer = colors.grouped,
            surfaceContainerHigh = colors.elevated,
            surfaceContainerHighest = colors.elevated,
            surfaceContainerLow = colors.background,
            surfaceContainerLowest = colors.background,
        )
    }
    val typography = remember(type) {
        Typography(
            displayLarge = type.largeTitle,
            displayMedium = type.largeTitle,
            displaySmall = type.title1,
            headlineLarge = type.title1,
            headlineMedium = type.title2,
            headlineSmall = type.title3,
            titleLarge = type.title3,
            titleMedium = type.headline,
            titleSmall = type.subhead,
            bodyLarge = type.body,
            bodyMedium = type.callout,
            bodySmall = type.footnote,
            labelLarge = type.headline,
            labelMedium = type.footnote,
            labelSmall = type.caption2,
        )
    }
    val shapes = remember {
        Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(14.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(28.dp),
        )
    }
    MaterialExpressiveTheme(colorScheme = scheme, typography = typography, shapes = shapes) {
        val overscrollFactory = rememberIosOverscrollFactory()
        CompositionLocalProvider(
            LocalIndication provides indication,
            LocalOverscrollFactory provides overscrollFactory,
            content = content,
        )
    }
}
