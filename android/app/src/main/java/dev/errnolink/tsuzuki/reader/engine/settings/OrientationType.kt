// Adapted from Neko (nekomangaorg/Neko, Apache-2.0): eu/kanade/tachiyomi/ui/reader/settings/OrientationType.kt
package dev.errnolink.tsuzuki.reader.engine.settings

import android.content.pm.ActivityInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ScreenLockLandscape
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.StayCurrentLandscape
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.ui.graphics.vector.ImageVector
import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.MR

enum class OrientationType(
    val prefValue: Int,
    val flag: Int,
    val stringRes: StringResource,
    val icon: ImageVector,
    val flagValue: Int = prefValue shl OrientationType.SHIFT,
) {
    DEFAULT(
        0,
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
        MR.strings.label_default,
        Icons.Default.ScreenRotation,
    ),
    FREE(
        1,
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
        MR.strings.rotation_free,
        Icons.Default.ScreenRotation,
    ),
    PORTRAIT(
        2,
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
        MR.strings.rotation_portrait,
        Icons.Default.StayCurrentPortrait,
    ),
    LANDSCAPE(
        3,
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
        MR.strings.rotation_landscape,
        Icons.Default.StayCurrentLandscape,
    ),
    LOCKED_PORTRAIT(
        4,
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
        MR.strings.rotation_force_portrait,
        Icons.Default.ScreenLockPortrait,
    ),
    LOCKED_LANDSCAPE(
        5,
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
        MR.strings.rotation_force_landscape,
        Icons.Default.ScreenLockLandscape,
    );

    companion object {
        private const val SHIFT = 0x00000003
        const val MASK = 7 shl SHIFT

        fun fromPreference(preference: Int): OrientationType =
            entries.find { it.flagValue == preference } ?: FREE

        fun fromSpinner(position: Int?) =
            entries.find { value -> value.prefValue == position } ?: DEFAULT
    }
}
