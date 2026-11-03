package eu.kanade.presentation.more.settings.screen.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.LogoHeader
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.util.CrashLogUtil
import eu.kanade.tachiyomi.util.lang.toDateTimestampString
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.isDebugBuildType
import eu.kanade.tachiyomi.util.system.isPreviewBuildType
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.LinkIcon
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.icons.CustomIcons
import tachiyomi.presentation.core.icons.Discord
import tachiyomi.presentation.core.icons.Github
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import dev.errnolink.tsuzuki.ui.settings.TsuzukiAboutScreen

class AboutScreen : Screen() {
    @Suppress("unused")
    private fun readResolve(): Any = AboutScreen

    @Composable
    override fun Content() {
        TsuzukiAboutScreen().Content()
    }

    companion object {
        fun getVersionName(withBuildDate: Boolean): String {
            return when {
                isDebugBuildType -> {
                    "Debug ${BuildConfig.COMMIT_SHA}".let {
                        if (withBuildDate) {
                            "$it (${getFormattedBuildTime()})"
                        } else {
                            it
                        }
                    }
                }

                isPreviewBuildType -> {
                    "Beta r${BuildConfig.COMMIT_COUNT}".let {
                        if (withBuildDate) {
                            "$it (${BuildConfig.COMMIT_SHA}, ${getFormattedBuildTime()})"
                        } else {
                            "$it (${BuildConfig.COMMIT_SHA})"
                        }
                    }
                }

                else -> {
                    "Stable ${BuildConfig.VERSION_NAME}".let {
                        if (withBuildDate) {
                            "$it (${getFormattedBuildTime()})"
                        } else {
                            it
                        }
                    }
                }
            }
        }

        internal fun getFormattedBuildTime(): String {
            return try {
                LocalDateTime.ofInstant(
                    Instant.parse(BuildConfig.BUILD_TIME),
                    ZoneId.systemDefault(),
                )
                    .toDateTimestampString(
                        UiPreferences.dateFormat(
                            Injekt.get<UiPreferences>().dateFormat().get(),
                        ),
                    )
            } catch (_: Exception) {
                BuildConfig.BUILD_TIME
            }
        }
    }
}
