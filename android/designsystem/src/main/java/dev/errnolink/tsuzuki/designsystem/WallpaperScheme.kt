package dev.errnolink.tsuzuki.designsystem

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** Minimum contrast for ink over its fill (WCAG AA body text). */
private const val MinInkContrast = 4.5f

/** Minimum contrast for an interactive colour against the page background (WCAG controls). */
private const val MinControlContrast = 3f

/**
 * Tsuzuki roles taken from the system's dynamic (Material You) scheme, API 31+ wallpaper only.
 *
 * Role mapping (dynamic → Tsuzuki), mirroring the static palette's relationships so contrast
 * holds for any hue: on-background → text, on-surface-variant → secondary, outline-variant →
 * separator, surface-container → grouped and glass tint, surface-container-high → elevated,
 * primary → accent, on-primary → on-accent, primary-container → selected fill.
 * destructive and success stay Tsuzuki's own: the wallpaper scheme has no semantic red/green.
 *
 * Every checked pair falls back to Tsuzuki's static role when contrast fails, so a hostile
 * wallpaper can never produce unreadable text or unfindable controls.
 */
internal fun wallpaperColors(
    scheme: ColorScheme,
    dark: Boolean,
    base: TsuzukiColors = tsuzukiColors(dark),
): TsuzukiColors {
    val mapped = base.copy(
        background = scheme.background,
        grouped = scheme.surfaceContainer,
        elevated = scheme.surfaceContainerHigh,
        text = scheme.onBackground,
        secondary = scheme.onSurfaceVariant,
        separator = scheme.outlineVariant,
        selectedFill = scheme.primaryContainer,
        glassTint = scheme.surfaceContainer,
        accent = scheme.primary,
        onAccent = scheme.onPrimary,
    )
    val accent = if (contrastRatio(mapped.accent, mapped.background) >= MinControlContrast) mapped.accent else base.accent
    return mapped.copy(
        text = if (contrastRatio(mapped.text, mapped.background) >= MinInkContrast) mapped.text else base.text,
        secondary = if (contrastRatio(mapped.secondary, mapped.background) >= MinInkContrast) mapped.secondary else base.secondary,
        accent = accent,
        onAccent = if (contrastRatio(mapped.onAccent, accent) >= MinInkContrast) mapped.onAccent else base.onAccent,
    )
}

/**
 * Reads the wallpaper scheme. Recomputes when the dynamic palette changes while the app runs
 * (`ACTION_WALLPAPER_CHANGED` — the framework colour resources only refresh for a configuration
 * change otherwise) and on dark-mode/configuration changes. Null below API 31.
 */
@Composable
internal fun rememberWallpaperColors(dark: Boolean): TsuzukiColors? {
    if (Build.VERSION.SDK_INT < 31) return null
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    var wallpaperVersion by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_WALLPAPER_CHANGED) wallpaperVersion++
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_WALLPAPER_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    return remember(context, dark, wallpaperVersion, configuration) {
        wallpaperColors(
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context),
            dark,
        )
    }
}
