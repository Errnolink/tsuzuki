package dev.errnolink.tsuzuki.ui.settings
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegate
import eu.kanade.tachiyomi.ui.category.biometric.BiometricTimesScreen
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.authenticate
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.isAuthenticationSupported
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import mihon.core.archive.CbzCrypto
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object TsuzukiSettingsSecurityScreen : SearchableSettings {
    private fun readResolve(): Any = TsuzukiSettingsSecurityScreen

    override val displayName = "Privacy & security"

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_security


    @Composable
    override fun Content() = TsuzukiPreferenceContent()
val prefKeys: Set<String> = setOf(
        "use_biometric_lock",
        "lock_app_after",
        "hide_notification_content",
        "secure_screen_v2",
        "password_protect_downloads",
        "encryption_type",
        "cbz_password",
        "biometric_time_ranges",
        "biometric_days",
    )

    @Composable
    override fun getPreferences(): List<Preference> {
        val securityPreferences = remember { Injekt.get<SecurityPreferences>() }
        return listOf(
            getAppLockGroup(securityPreferences),
            getPrivacyGroup(securityPreferences),
            getCbzGroup(securityPreferences),
        )
    }

    @Composable
    private fun getAppLockGroup(
        securityPreferences: SecurityPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val authSupported = remember { context.isAuthenticationSupported() }
        val useAuthPref = securityPreferences.useAuthenticator()
        val useAuth by useAuthPref.collectAsState()

        return Preference.PreferenceGroup(
            title = "App lock",
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = useAuthPref,
                    title = stringResource(MR.strings.lock_with_biometrics),
                    subtitle = "Locks the app behind your fingerprint, face, or PIN",
                    enabled = authSupported,
                    onValueChanged = {
                        (context as FragmentActivity).authenticate(
                            title = context.stringResource(MR.strings.lock_with_biometrics),
                        )
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = securityPreferences.lockAppAfter(),
                    entries = LockAfterValues
                        .associateWith {
                            when (it) {
                                -1 -> stringResource(MR.strings.lock_never)
                                0 -> stringResource(MR.strings.lock_always)
                                else -> pluralStringResource(MR.plurals.lock_after_mins, count = it, it)
                            }
                        }
                        .toImmutableMap(),
                    title = stringResource(MR.strings.lock_when_idle),
                    enabled = authSupported && useAuth,
                    onValueChanged = {
                        (context as FragmentActivity).authenticate(
                            title = context.stringResource(MR.strings.lock_when_idle),
                        )
                    },
                ),
                run {
                    val navigator = LocalNavigator.currentOrThrow
                    val count by securityPreferences.authenticatorTimeRanges().collectAsState()
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(SYMR.strings.action_edit_biometric_lock_times),
                        subtitle = pluralStringResource(
                            SYMR.plurals.num_lock_times,
                            count.size,
                            count.size,
                        ),
                        enabled = useAuth,
                        onClick = {
                            navigator.push(BiometricTimesScreen())
                        },
                    )
                },
                run {
                    val selection by securityPreferences.authenticatorDays().collectAsState()
                    var dialogOpen by remember { mutableStateOf(false) }
                    if (dialogOpen) {
                        SetLockedDaysDialog(
                            onDismissRequest = { dialogOpen = false },
                            initialSelection = selection,
                            onDaysSelected = {
                                dialogOpen = false
                                securityPreferences.authenticatorDays().set(it)
                            },
                        )
                    }
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(SYMR.strings.biometric_lock_days),
                        subtitle = stringResource(SYMR.strings.biometric_lock_days_summary),
                        enabled = useAuth,
                        onClick = { dialogOpen = true },
                    )
                },
            ),
        )
    }

    @Composable
    private fun getPrivacyGroup(
        securityPreferences: SecurityPreferences,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = "Privacy",
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = securityPreferences.hideNotificationContent(),
                    title = stringResource(MR.strings.hide_notification_content),
                    subtitle = "Hides titles and progress in update and download notifications",
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = securityPreferences.secureScreen(),
                    entries = SecurityPreferences.SecureScreenMode.entries
                        .associateWith { stringResource(it.titleRes) }
                        .toImmutableMap(),
                    title = stringResource(MR.strings.secure_screen),
                ),
                Preference.PreferenceItem.InfoPreference(
                    stringResource(MR.strings.secure_screen_summary) +
                        "\n\n" +
                        "Incognito and Downloaded-only are one-tap modes on the More tab.",
                ),
            ),
        )
    }

    @Composable
    private fun getCbzGroup(
        securityPreferences: SecurityPreferences,
    ): Preference.PreferenceGroup {
        val scope = rememberCoroutineScope()
        val isCbzPasswordSet by remember { CbzCrypto.isPasswordSetState(scope) }.collectAsState()
        val passwordProtectDownloads by securityPreferences.passwordProtectDownloads().collectAsState()

        return Preference.PreferenceGroup(
            title = "Encrypted CBZ",
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = securityPreferences.passwordProtectDownloads(),
                    title = stringResource(SYMR.strings.password_protect_downloads),
                    subtitle = stringResource(SYMR.strings.password_protect_downloads_summary),
                    enabled = isCbzPasswordSet,
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = securityPreferences.encryptionType(),
                    entries = SecurityPreferences.EncryptionType.entries
                        .associateWith { stringResource(it.titleRes) }
                        .toImmutableMap(),
                    title = stringResource(SYMR.strings.encryption_type),
                    enabled = passwordProtectDownloads,

                ),
                run {
                    var dialogOpen by remember { mutableStateOf(false) }
                    if (dialogOpen) {
                        PasswordDialog(
                            onDismissRequest = { dialogOpen = false },
                            onReturnPassword = { password ->
                                dialogOpen = false

                                CbzCrypto.deleteKeyCbz()
                                securityPreferences.cbzPassword().set(CbzCrypto.encryptCbz(password.replace("\n", "")))
                            },
                        )
                    }
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(SYMR.strings.set_cbz_zip_password),
                        onClick = {
                            dialogOpen = true
                        },
                    )
                },
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(SYMR.strings.delete_cbz_archive_password),
                    enabled = isCbzPasswordSet,
                    onClick = {
                        CbzCrypto.deleteKeyCbz()
                        securityPreferences.cbzPassword().set("")
                    },
                ),
            ),
        )
    }

    // SY -->
    enum class DayOption(val day: Int, val stringRes: StringResource) {
        Sunday(SecureActivityDelegate.LOCK_SUNDAY, SYMR.strings.sunday),
        Monday(SecureActivityDelegate.LOCK_MONDAY, SYMR.strings.monday),
        Tuesday(SecureActivityDelegate.LOCK_TUESDAY, SYMR.strings.tuesday),
        Wednesday(SecureActivityDelegate.LOCK_WEDNESDAY, SYMR.strings.wednesday),
        Thursday(SecureActivityDelegate.LOCK_THURSDAY, SYMR.strings.thursday),
        Friday(SecureActivityDelegate.LOCK_FRIDAY, SYMR.strings.friday),
        Saturday(SecureActivityDelegate.LOCK_SATURDAY, SYMR.strings.saturday),
    }

    @Composable
    fun SetLockedDaysDialog(
        onDismissRequest: () -> Unit,
        initialSelection: Int,
        onDaysSelected: (Int) -> Unit,
    ) {
        val selected = remember(initialSelection) {
            DayOption.entries.filter { it.day and initialSelection == it.day }
                .toMutableStateList()
        }
        MultiChoiceSheet(
            visible = true,
            title = stringResource(SYMR.strings.biometric_lock_days),
            doneLabel = stringResource(MR.strings.action_ok),
            onDone = {
                onDaysSelected(
                    selected.fold(0) { i, day ->
                        i or day.day
                    },
                )
            },
            options = DayOption.entries.map { day ->
                Triple(
                    stringResource(day.stringRes),
                    selected.contains(day),
                ) { checked ->
                    if (checked) {
                        selected.add(day)
                    } else {
                        selected.remove(day)
                    }
                }
            },
            onDismissRequest = onDismissRequest,
        )
    }

    @Composable
    fun PasswordDialog(
        onDismissRequest: () -> Unit,
        onReturnPassword: (String) -> Unit,
        // KMK -->
        title: StringResource = SYMR.strings.cbz_archive_password,
        // KMK <--
    ) {
        TextEntrySheet(
            visible = true,
            title = stringResource(title),
            initialValue = "",
            confirmLabel = stringResource(MR.strings.action_ok),
            errorText = null,
            placeholder = stringResource(MR.strings.password),
            supportsConfirm = { true },
            onConfirm = { value ->
                onReturnPassword(value)
                true
            },
            onDismissRequest = onDismissRequest,
        )
    }
    // SY <--
}

private val LockAfterValues = persistentListOf(
    0, // Always
    1,
    2,
    5,
    10,
    -1, // Never
)
