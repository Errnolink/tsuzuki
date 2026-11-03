package dev.errnolink.tsuzuki.designsystem

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat

val LocalTsuzukiColors = staticCompositionLocalOf { tsuzukiColors(false) }
val LocalTsuzukiTypography = staticCompositionLocalOf { TsuzukiTypography() }
val LocalTsuzukiSpacing = staticCompositionLocalOf { TsuzukiSpacing }
val LocalTsuzukiCorners = staticCompositionLocalOf { TsuzukiCorners }
val LocalTsuzukiMotion = staticCompositionLocalOf { TsuzukiMotion(reduced = true) }
val LocalOpaqueGlass = staticCompositionLocalOf { true }
val LocalGlassTintStrength = staticCompositionLocalOf { GlassMaterial.DefaultTintStrength }
val LocalTsuzukiContentColor = staticCompositionLocalOf { Color.Unspecified }
val LocalTsuzukiComponentLabels = staticCompositionLocalOf { TsuzukiComponentLabels() }
val LocalMotionTokens = staticCompositionLocalOf { false }
val LocalLargeTitles = staticCompositionLocalOf { false }
@Immutable
data class TsuzukiComponentLabels(
    val resizeSheet: String = "Resize sheet",
    val sheetFit: String = "Fit",
    val sheetMedium: String = "Medium",
    val sheetLarge: String = "Large",
    val closeSheet: (String) -> String = { "Close $it" },
    val showActionsFor: (String) -> String = { "Show actions for $it" },
    val clearSearch: String = "Clear search",
    val collapseSubmenu: String = "Collapse submenu",
    val selected: String = "Selected",
    val menu: String = "Menu",
    val confirm: String = "Confirm",
    val cancel: String = "Cancel",
)

object TsuzukiTheme {
    val colors: TsuzukiColors @Composable get() = LocalTsuzukiColors.current
    val typography: TsuzukiTypography @Composable get() = LocalTsuzukiTypography.current
    val spacing: TsuzukiSpacing @Composable get() = LocalTsuzukiSpacing.current
    val corners: TsuzukiCorners @Composable get() = LocalTsuzukiCorners.current
    val motion: TsuzukiMotion @Composable get() = LocalTsuzukiMotion.current
    val haptics: Haptics @Composable get() = LocalHaptics.current ?: rememberHaptics()
}

@Composable
fun TsuzukiTheme(
    dark: Boolean = isSystemInDarkTheme(),
    systemAccent: Boolean = false,
    reducedTransparency: Boolean = false,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val policy = rememberSystemPolicy()
    val glassPreferences = remember(context) { context.getSharedPreferences("tsuzuki_glass", Context.MODE_PRIVATE) }
    var glassTintStrength by remember(glassPreferences) {
        mutableStateOf(glassPreferences.getFloat("tint_strength", GlassMaterial.DefaultTintStrength))
    }
    DisposableEffect(glassPreferences) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
            if (key == "tint_strength") glassTintStrength = preferences.getFloat(key, GlassMaterial.DefaultTintStrength)
        }
        glassPreferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { glassPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val accent = remember(context, dark, systemAccent) {
        if (systemAccent && Build.VERSION.SDK_INT >= 31) {
            Color(context.getColor(if (dark) android.R.color.system_accent1_200 else android.R.color.system_accent1_700))
        } else {
            null
        }
    }
    val colors = remember(dark, accent) { tsuzukiColors(dark, accent) }
    val typography = remember { TsuzukiTypography() }
    val standardTokens = LocalMotionTokens.current
    val motion = remember(reducedMotion, policy, standardTokens) { TsuzukiMotion(reducedMotion || policy.reducedMotion, standardTokens) }
    val indication = remember(motion) { PressScaleIndication(motion) }
    val selectionColors = remember(colors) { TextSelectionColors(colors.accent, colors.accent.copy(alpha = 0.24f)) }
    val haptics = rememberHaptics()
    CompositionLocalProvider(
        LocalTsuzukiColors provides colors,
        LocalTsuzukiTypography provides typography,
        LocalTsuzukiSpacing provides TsuzukiSpacing,
        LocalTsuzukiCorners provides TsuzukiCorners,
        LocalTsuzukiMotion provides motion,
        LocalTsuzukiContentColor provides colors.text,
        LocalContentColor provides colors.text,
        LocalTextStyle provides typography.body,
        LocalOpaqueGlass provides (reducedTransparency || glassTintStrength >= 1f || policy.powerSave || policy.highContrast),
        LocalGlassTintStrength provides glassTintStrength.coerceIn(0f, 1f),
        LocalIndication provides indication,
        LocalTextSelectionColors provides selectionColors,
        LocalHaptics provides haptics,
        LocalIosFling provides remember { IosFlingBehavior() },
        content = content,
    )
}

@Composable
fun TsuzukiText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TsuzukiTheme.typography.body,
    color: Color = LocalTsuzukiContentColor.current,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    BasicText(text, modifier, style.copy(color = color), overflow = overflow, maxLines = maxLines)
}

private data class SystemPolicy(val powerSave: Boolean, val reducedMotion: Boolean, val highContrast: Boolean)

fun systemReducedMotion(animatorDurationScale: Float, transitionAnimationScale: Float): Boolean =
    animatorDurationScale == 0f || transitionAnimationScale == 0f

@Composable
private fun rememberSystemPolicy(): SystemPolicy {
    val context = LocalContext.current
    fun readPolicy(): SystemPolicy {
        val resolver = context.contentResolver
        return SystemPolicy(
            context.getSystemService(PowerManager::class.java).isPowerSaveMode,
            systemReducedMotion(
                Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f),
                Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f),
            ),
            Settings.Secure.getInt(resolver, "high_text_contrast_enabled", 0) == 1,
        )
    }
    var policy by remember(context) { mutableStateOf(readPolicy()) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { policy = readPolicy() }
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { policy = readPolicy() }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer,
        )
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.TRANSITION_ANIMATION_SCALE), false, observer,
        )
        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor("high_text_contrast_enabled"), false, observer,
        )
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
            context.unregisterReceiver(receiver)
        }
    }
    return policy
}
