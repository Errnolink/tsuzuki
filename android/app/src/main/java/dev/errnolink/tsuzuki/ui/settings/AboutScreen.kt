package dev.errnolink.tsuzuki.ui.settings
import tachiyomi.core.common.i18n.stringResource

import kotlinx.coroutines.launch

import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource


import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.InsetGroupedList
import dev.errnolink.tsuzuki.designsystem.ProgressRing
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.update.AppDownloadWorker
import dev.errnolink.tsuzuki.update.AppUpdateChecker
import dev.errnolink.tsuzuki.update.AppUpdatePermission
import dev.errnolink.tsuzuki.update.CheckResult
import dev.errnolink.tsuzuki.update.UpdatePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.more.settings.screen.about.OpenSourceLicensesScreen
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.lang.toDateTimestampString
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.isDebugBuildType
import eu.kanade.tachiyomi.util.system.isPreviewBuildType
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class TsuzukiAboutScreen : Screen() {
    @Suppress("unused")
    private fun readResolve(): Any = TsuzukiAboutScreen

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val handleBack = LocalBackPress.current
        val navigator = LocalNavigator.currentOrThrow
        val updatePreferences = remember { Injekt.get<UpdatePreferences>() }
        val autoUpdatePref = updatePreferences.autoUpdate()
        val autoUpdateEnabled by autoUpdatePref.collectAsState()
        val canInstall = remember(autoUpdateEnabled) { AppUpdatePermission.canInstallPackages(context) }
        val coroutineScope = rememberCoroutineScope()
        var isCheckingUpdate by remember { mutableStateOf(false) }
        var updateStatus by remember { mutableStateOf<String?>(null) }

        SettingsScaffold(
            title = stringResource(MR.strings.pref_category_about),
            navigateUp = handleBack?.let { { it() } },
        ) {
            item(key = "about-header") {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = TsuzukiSpacing.gutter),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(TsuzukiCorners.field))
                            .background(TsuzukiTheme.colors.elevated)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_tsuzuki),
                            contentDescription = stringResource(MR.strings.app_name),
                            contentScale = ContentScale.Fit,
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(TsuzukiTheme.colors.text),
                        )
                    }
                    Spacer(Modifier.height(TsuzukiSpacing.small))
                    TsuzukiText(
                        stringResource(MR.strings.app_name),
                        style = TsuzukiTheme.typography.title2,
                    )
                    TsuzukiText(
                        TsuzukiAboutScreen.getVersionName(withBuildDate = true),
                        style = TsuzukiTheme.typography.footnote,
                        color = TsuzukiTheme.colors.secondary,
                    )
                }
            }
            item(key = "about-rows") {
                InsetGroupedList {
                    GroupedRow(
                        title = stringResource(MR.strings.version),
                        subtitle = updateStatus,
                        value = BuildConfig.VERSION_NAME,
                        chevron = false,
                        divider = true,
                        onClick = {
                            context.copyToClipboard(BuildConfig.VERSION_NAME, BuildConfig.VERSION_NAME)
                        },
                    )
                    GroupedRow(
                        title = stringResource(MR.strings.check_for_updates),
                        value = if (isCheckingUpdate) null else updateStatus,
                        divider = true,
                        leading = if (isCheckingUpdate) {
                            { ProgressRing(progress = 0f, label = stringResource(MR.strings.check_for_updates)) }
                        } else {
                            null
                        },
                        enabled = !isCheckingUpdate,
                        onClick = {
                            if (isCheckingUpdate) return@GroupedRow
                            isCheckingUpdate = true
                            updateStatus = null
                            coroutineScope.launch {
                                try {
                                    when (val result = AppUpdateChecker().checkForUpdate(isUserPrompt = true)) {
                                        is CheckResult.NewUpdate -> {
                                            updateStatus = result.candidate.versionName
                                            if (!AppUpdatePermission.canInstallPackages(context)) {
                                                AppUpdatePermission.openInstallPermissionSettings(context)
                                            }
                                            AppDownloadWorker.start(context, result.candidate.downloadUrl)
                                        }
                                        is CheckResult.NoNewUpdate -> {
                                            updateStatus = context.stringResource(MR.strings.update_check_no_new_updates)
                                        }
                                        is CheckResult.Error -> {
                                            updateStatus = context.stringResource(MR.strings.update_check_notification_download_error)
                                        }
                                    }
                                } finally {
                                    isCheckingUpdate = false
                                }
                            }
                        },
                    )
                    GroupedRow(
                        title = "Auto-update app",
                        subtitle = if (!canInstall) "Requires permission to install apps from unknown sources" else null,
                        checked = autoUpdateEnabled,
                        onCheckedChange = { enabled ->
                            autoUpdatePref.set(enabled)
                            if (enabled && !AppUpdatePermission.canInstallPackages(context)) {
                                AppUpdatePermission.openInstallPermissionSettings(context)
                            }
                        },
                        divider = false,
                    )
                }
            }
            item(key = "about-licenses") {
                InsetGroupedList {
                    GroupedRow(
                        title = stringResource(MR.strings.licenses),
                        value = null,
                        chevron = true,
                        divider = false,
                        onClick = { navigator.push(OpenSourceLicensesScreen()) },
                    )
                }
            }
        }
    }

    companion object {
        fun getVersionName(withBuildDate: Boolean): String {
            val version = BuildConfig.VERSION_NAME
            val channel = when {
                isDebugBuildType || version.contains("debug", ignoreCase = true) -> "Debug"
                isPreviewBuildType || version.contains("beta", ignoreCase = true) -> "Beta"
                else -> "Stable"
            }
            return when {
                isDebugBuildType -> "$channel ${BuildConfig.COMMIT_SHA}".let {
                    if (withBuildDate) "$it (${getFormattedBuildTime()})" else it
                }

                else -> "$channel $version".let {
                    if (withBuildDate) "$it (${getFormattedBuildTime()})" else it
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
