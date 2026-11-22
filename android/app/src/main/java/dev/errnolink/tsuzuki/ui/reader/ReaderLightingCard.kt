package dev.errnolink.tsuzuki.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.Chip
import dev.errnolink.tsuzuki.designsystem.ChipTone
import dev.errnolink.tsuzuki.designsystem.GlassContext
import dev.errnolink.tsuzuki.designsystem.GlassSurface
import dev.errnolink.tsuzuki.designsystem.LiquidCard
import dev.errnolink.tsuzuki.designsystem.Sun
import dev.errnolink.tsuzuki.designsystem.ThemeAuto
import dev.errnolink.tsuzuki.designsystem.TsuzukiButton
import dev.errnolink.tsuzuki.designsystem.TsuzukiButtonSize
import dev.errnolink.tsuzuki.designsystem.TsuzukiButtonVariant
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Floating Quick Lighting & Paper popover synced directly with [ReaderPreferences]:
 * - Brightness / Dim slider (`customBrightness` + `customBrightnessValue` in `-75..100`)
 * - Warmth slider (`colorFilter` + warm amber ARGB tint via [lightingWarmthColor])
 * - Quick toggle chips for Grayscale & Invert
 *
 * Presented inside Kasane [LiquidCard] pouring from the Lighting trigger when
 * [LocalReaderMorphSources] is provided.
 */
@Composable
internal fun ReaderLightingCard(
    visible: Boolean,
    glass: GlassContext?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    preferences: ReaderPreferences = remember { Injekt.get() },
) {
    val morphSources = LocalReaderMorphSources.current
    if (morphSources != null) {
        LiquidCard(
            visible = visible,
            source = morphSources.lighting,
            onDismissRequest = onDismiss,
            maxHeight = 360.dp,
        ) {
            ReaderLightingContent(
                onDismiss = onDismiss,
                preferences = preferences,
            )
        }
    } else if (visible) {
        GlassSurface(
            context = glass,
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(TsuzukiCorners.menu),
        ) {
            ReaderLightingContent(
                onDismiss = onDismiss,
                preferences = preferences,
            )
        }
    }
}

@Composable
private fun ReaderLightingContent(
    onDismiss: () -> Unit,
    preferences: ReaderPreferences,
) {
    val customBrightness by preferences.customBrightness().collectAsState()
    val customBrightnessValue by preferences.customBrightnessValue().collectAsState()
    val effectiveBrightness = if (customBrightness) customBrightnessValue else 0

    val colorFilterEnabled by preferences.colorFilter().collectAsState()
    val colorFilterValue by preferences.colorFilterValue().collectAsState()
    val warmthPercent = if (colorFilterEnabled) warmthPercentFromColor(colorFilterValue) else 0

    val grayscale by preferences.grayscale().collectAsState()
    val inverted by preferences.invertedColors().collectAsState()

    val hasAnyActive = customBrightness && customBrightnessValue != 0 ||
        warmthPercent > 0 ||
        grayscale ||
        inverted

    val brightnessLabel = stringResource(MR.strings.tsuzuki_reader_brightness)
    val systemLabel = stringResource(MR.strings.tsuzuki_reader_system_brightness)
    val warmthLabel = stringResource(MR.strings.tsuzuki_reader_warmth)

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TsuzukiText(
                stringResource(MR.strings.tsuzuki_reader_lighting),
                style = TsuzukiTheme.typography.headline.copy(fontWeight = FontWeight.Bold),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasAnyActive) {
                    TsuzukiButton(
                        label = stringResource(MR.strings.action_reset),
                        onClick = {
                            preferences.customBrightness().set(false)
                            preferences.customBrightnessValue().set(0)
                            preferences.colorFilter().set(false)
                            preferences.colorFilterValue().set(0)
                            preferences.grayscale().set(false)
                            preferences.invertedColors().set(false)
                        },
                        size = TsuzukiButtonSize.Xs,
                        variant = TsuzukiButtonVariant.Soft,
                        minWidth = 0.dp,
                    )
                }
                TsuzukiButton(
                    label = stringResource(MR.strings.tsuzuki_done),
                    onClick = onDismiss,
                    size = TsuzukiButtonSize.Xs,
                    variant = TsuzukiButtonVariant.Filled,
                    minWidth = 0.dp,
                )
            }
        }

        // Brightness / Dim slider (-75 .. 100)
        LightingSliderRow(
            icon = TsuzukiIcons.Sun,
            label = brightnessLabel,
            valueText = formatBrightnessLabel(effectiveBrightness, systemLabel),
            value = effectiveBrightness,
            valueRange = -75..100,
            onValueChange = { next ->
                preferences.customBrightness().set(next != 0)
                preferences.customBrightnessValue().set(next)
            },
        )

        // Warmth slider (0 .. 100%)
        LightingSliderRow(
            icon = TsuzukiIcons.ThemeAuto,
            label = warmthLabel,
            valueText = "$warmthPercent%",
            value = warmthPercent,
            valueRange = 0..100,
            onValueChange = { nextPct ->
                if (nextPct <= 0) {
                    preferences.colorFilter().set(false)
                    preferences.colorFilterValue().set(0)
                } else {
                    preferences.colorFilter().set(true)
                    preferences.colorFilterValue().set(lightingWarmthColor(nextPct))
                    preferences.colorFilterMode().set(1)
                }
            },
        )

        // Quick toggle chips using Kasane Chip: Grayscale & Invert
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Chip(
                label = stringResource(MR.strings.pref_grayscale),
                selected = grayscale,
                onClick = { preferences.grayscale().set(!grayscale) },
                tone = ChipTone.Neutral,
                minHeight = 40.dp,
                modifier = Modifier.weight(1f),
            )
            Chip(
                label = stringResource(MR.strings.pref_inverted_colors),
                selected = inverted,
                onClick = { preferences.invertedColors().set(!inverted) },
                tone = ChipTone.Neutral,
                minHeight = 40.dp,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LightingSliderRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    valueText: String,
    value: Int,
    valueRange: IntRange,
    onValueChange: (Int) -> Unit,
) {
    val colors = TsuzukiTheme.colors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, null, Modifier.size(16.dp), tint = colors.secondary)
                TsuzukiText(label, style = TsuzukiTheme.typography.footnote, color = colors.secondary)
            }
            TsuzukiText(
                valueText,
                style = TsuzukiTheme.typography.footnote.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum",
                ),
            )
        }
        ReaderSlider(
            value = value,
            valueRange = valueRange,
            onCommit = onValueChange,
            onPreview = { preview -> preview?.let(onValueChange) },
            label = label,
        )
    }
}
