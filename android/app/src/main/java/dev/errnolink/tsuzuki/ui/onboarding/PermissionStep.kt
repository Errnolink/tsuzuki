package dev.errnolink.tsuzuki.ui.onboarding
import dev.errnolink.tsuzuki.designsystem.TsuzukiCheckMark

import dev.errnolink.tsuzuki.designsystem.GroupedRow


import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.errnolink.tsuzuki.designsystem.InsetGroupedList
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.Check
import dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.ui.settings.GroupDivider
import eu.kanade.presentation.util.rememberRequestPackageInstallsPermissionState
import eu.kanade.tachiyomi.util.system.launchRequestPackageInstallsPermission
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import eu.kanade.presentation.more.onboarding.OnboardingStep

internal class PermissionStep : OnboardingStep {

    private var notificationGranted by mutableStateOf(false)
    private var batteryGranted by mutableStateOf(false)

    // KMK -->
    private var externalStoragePermissionGranted by mutableStateOf(false)
    // KMK <--

    override val isComplete: Boolean = true

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current

        val installGranted = rememberRequestPackageInstallsPermissionState()

        DisposableEffect(lifecycleOwner.lifecycle) {
            val observer = object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                            PackageManager.PERMISSION_GRANTED
                    } else {
                        true
                    }
                    batteryGranted = context.getSystemService<PowerManager>()!!
                        .isIgnoringBatteryOptimizations(context.packageName)
                    // KMK -->
                    externalStoragePermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) ==
                            PackageManager.PERMISSION_GRANTED
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
                            PackageManager.PERMISSION_GRANTED
                    } else {
                        context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                            PackageManager.PERMISSION_GRANTED
                    }
                    // KMK <--
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        InsetGroupedList {
            PermissionRow(
                title = stringResource(MR.strings.onboarding_permission_install_apps),
                subtitle = stringResource(MR.strings.onboarding_permission_install_apps_description),
                granted = installGranted,
                onButtonClick = {
                    context.launchRequestPackageInstallsPermission()
                },
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val permissionRequester = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = {
                        // no-op. resulting checks is being done on resume
                    },
                )
                PermissionRow(
                    title = stringResource(MR.strings.onboarding_permission_notifications),
                    subtitle = stringResource(MR.strings.onboarding_permission_notifications_description),
                    granted = notificationGranted,
                    onButtonClick = { permissionRequester.launch(Manifest.permission.POST_NOTIFICATIONS) },
                )
            }

            PermissionRow(
                title = stringResource(MR.strings.onboarding_permission_ignore_battery_opts),
                subtitle = stringResource(MR.strings.onboarding_permission_ignore_battery_opts_description),
                granted = batteryGranted,
                onButtonClick = {
                    @SuppressLint("BatteryLife")
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = "package:${context.packageName}".toUri()
                    }
                    context.startActivity(intent)
                },
            )

            // KMK -->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val permissionRequester = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = {
                        // no-op. resulting checks is being done on resume
                    },
                )
                PermissionRow(
                    title = stringResource(KMR.strings.onboarding_permission_external_storage),
                    subtitle = stringResource(KMR.strings.onboarding_permission_external_storage_description),
                    granted = externalStoragePermissionGranted,
                    onButtonClick = { permissionRequester.launch(Manifest.permission.READ_MEDIA_IMAGES) },
                    divider = false,
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val permissionRequester = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = {
                        // no-op. resulting checks is being done on resume
                    },
                )
                PermissionRow(
                    title = stringResource(KMR.strings.onboarding_permission_external_storage),
                    subtitle = stringResource(KMR.strings.onboarding_permission_writing_external_storage_description),
                    granted = externalStoragePermissionGranted,
                    onButtonClick = { permissionRequester.launch(Manifest.permission.READ_EXTERNAL_STORAGE) },
                    divider = false,
                )
            } else {
                val permissionRequester = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = {
                        // no-op. resulting checks is being done on resume
                    },
                )
                PermissionRow(
                    title = stringResource(KMR.strings.onboarding_permission_external_storage),
                    subtitle = stringResource(KMR.strings.onboarding_permission_writing_external_storage_description),
                    granted = externalStoragePermissionGranted,
                    onButtonClick = { permissionRequester.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) },
                    divider = false,
                )
            }
            // KMK <--
        }
    }

    @Composable
    private fun PermissionRow(
        title: String,
        subtitle: String,
        granted: Boolean,
        onButtonClick: () -> Unit,
        divider: Boolean = true,
    ) {
        GroupedRow(
            title = title,
            subtitle = subtitle,
            divider = divider,
            trailing = {
                if (granted) {
                    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { TsuzukiCheckMark() }
                } else {
                    PillButton(
                        label = stringResource(MR.strings.onboarding_permission_action_grant),
                        onClick = onButtonClick,
                        prominent = false,
                    )
                }
            },
        )
    }
}
