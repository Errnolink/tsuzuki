package dev.errnolink.tsuzuki.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.Companion.ColorFilterMode
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsScreenModel
import tachiyomi.core.common.preference.getAndSet
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

@Composable
internal fun ColorFilterPage(screenModel: ReaderSettingsScreenModel) {
    val customBrightness by screenModel.preferences.customBrightness().collectAsState()
    ReaderToggle(label = stringResource(MR.strings.pref_custom_brightness), pref = screenModel.preferences.customBrightness())

    /*
     * Sets the brightness of the screen. Range is [-75, 100].
     * From -75 to -1 a semi-transparent black view is shown at the top with the minimum brightness.
     * From 1 to 100 it sets that value as brightness.
     * 0 sets system brightness and hides the overlay.
     */
    if (customBrightness) {
        val customBrightnessValue by screenModel.preferences.customBrightnessValue().collectAsState()
        ReaderSettingSlider(value = customBrightnessValue,
        valueRange = -75..100,
        label = stringResource(MR.strings.pref_custom_brightness),
        onChange = { screenModel.preferences.customBrightnessValue().set(it) },
        )
    }

    val colorFilter by screenModel.preferences.colorFilter().collectAsState()
    ReaderToggle(label = stringResource(MR.strings.pref_custom_color_filter), pref = screenModel.preferences.colorFilter())
    if (colorFilter) {
        val colorFilterValue by screenModel.preferences.colorFilterValue().collectAsState()
        ReaderSettingSlider(value = colorFilterValue.red,
        valueRange = 0..255,
        label = stringResource(MR.strings.color_filter_r_value),
        onChange = { newRValue ->
            screenModel.preferences.colorFilterValue().getAndSet {
                getColorValue(it, newRValue, RED_MASK, 16)
            }
        },
        )
        ReaderSettingSlider(value = colorFilterValue.green,
        valueRange = 0..255,
        label = stringResource(MR.strings.color_filter_g_value),
        onChange = { newGValue ->
            screenModel.preferences.colorFilterValue().getAndSet {
                getColorValue(it, newGValue, GREEN_MASK, 8)
            }
        },
        )
        ReaderSettingSlider(value = colorFilterValue.blue,
        valueRange = 0..255,
        label = stringResource(MR.strings.color_filter_b_value),
        onChange = { newBValue ->
            screenModel.preferences.colorFilterValue().getAndSet {
                getColorValue(it, newBValue, BLUE_MASK, 0)
            }
        },
        )
        ReaderSettingSlider(value = colorFilterValue.alpha,
        valueRange = 0..255,
        label = stringResource(MR.strings.color_filter_a_value),
        onChange = { newAValue ->
            screenModel.preferences.colorFilterValue().getAndSet {
                getColorValue(it, newAValue, ALPHA_MASK, 24)
            }
        },
        )

        val colorFilterMode by screenModel.preferences.colorFilterMode().collectAsState()
        ReaderChoice(
            stringResource(MR.strings.pref_color_filter_mode),
            ColorFilterMode.map { stringResource(it.first) },
            colorFilterMode,
            screenModel.preferences.colorFilterMode()::set,
        )
    }

    ReaderToggle(label = stringResource(MR.strings.pref_grayscale), pref = screenModel.preferences.grayscale())
    ReaderToggle(label = stringResource(MR.strings.pref_inverted_colors), pref = screenModel.preferences.invertedColors())
}

private fun getColorValue(currentColor: Int, color: Int, mask: Long, bitShift: Int): Int {
    return (color shl bitShift) or (currentColor and mask.inv().toInt())
}
private const val ALPHA_MASK: Long = 0xFF000000
private const val RED_MASK: Long = 0x00FF0000
private const val GREEN_MASK: Long = 0x0000FF00
private const val BLUE_MASK: Long = 0x000000FF
