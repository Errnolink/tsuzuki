package dev.errnolink.tsuzuki.ui.settings
import eu.kanade.presentation.more.settings.screen.SearchableSettings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.Eye
import dev.errnolink.tsuzuki.designsystem.EyeOff
import dev.errnolink.tsuzuki.designsystem.Info
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcon
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.X
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import dev.icerock.moko.resources.StringResource
import dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.mangadex.AutoTrackBackfillJob
import dev.errnolink.tsuzuki.mangadex.TrackingSyncJob
import dev.errnolink.tsuzuki.mangadex.AutoTrackState as AutoTrackJobState
import eu.kanade.domain.track.model.AutoTrackState
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.AnilistApi
import eu.kanade.tachiyomi.data.track.bangumi.BangumiApi
import eu.kanade.tachiyomi.data.track.comick.ComickApi
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeListApi
import eu.kanade.tachiyomi.data.track.shikimori.ShikimoriApi
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentMap
import dev.errnolink.tsuzuki.data.track.trackerLogin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object TsuzukiSettingsTrackingScreen : SearchableSettings {
    @Suppress("unused")
    private fun readResolve(): Any = TsuzukiSettingsTrackingScreen

    override val displayName = "Trackers"

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_tracking


    @Composable
    override fun Content() = TsuzukiPreferenceContent()
val prefKeys: Set<String> = setOf(
        "track_on_adding_to_library",
        "pref_auto_update_manga_sync_key",
        "pref_auto_update_manga_on_mark_read",
        "pref_auto_sync_progress_from_trackers_key",
        "tsuzuki_auto_refresh_tracking_metadata",
        "pref_resolve_using_source_metadata_key",
    )

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val trackPreferences = remember { Injekt.get<TrackPreferences>() }
        val trackerManager = remember { Injekt.get<TrackerManager>() }
        val sourceManager = remember { Injekt.get<SourceManager>() }
        val trackingWork by remember(context) { TrackingSyncJob.observe(context) }.collectAsState(initial = null)
        val trackingActive = trackingWork?.state?.isFinished == false
        val autoTrackWork by remember(context) { AutoTrackBackfillJob.observe(context) }.collectAsState(initial = null)
        val autoTrackActive = autoTrackWork?.state?.isFinished == false
        val autoTrackSummary = AutoTrackJobState().summary()
        val autoTrackSubtitle = when {
            autoTrackActive -> AutoTrackBackfillJob.progressText(autoTrackWork) ?: autoTrackSummary
            autoTrackSummary.isNotBlank() -> autoTrackSummary
            else -> "Bind library titles to your trackers using the links MangaDex publishes"
        }

        var dialog by remember { mutableStateOf<Any?>(null) }
        dialog?.run {
            when (this) {
                is LoginDialog -> {
                    TrackingLoginDialog(
                        tracker = tracker,
                        uNameStringRes = uNameStringRes,
                        onDismissRequest = { dialog = null },
                    )
                }
                is LogoutDialog -> {
                    TrackingLogoutDialog(
                        tracker = tracker,
                        onDismissRequest = { dialog = null },
                    )
                }
            }
        }

        val enhancedTrackers = trackerManager.trackers
            .filter { it is EnhancedTracker }
            .partition { service ->
                val acceptedSources = (service as EnhancedTracker).getAcceptedSources()
                sourceManager.getAll().any { it::class.qualifiedName in acceptedSources }
            }
        var enhancedTrackerInfo = stringResource(MR.strings.enhanced_tracking_info)
        if (enhancedTrackers.second.isNotEmpty()) {
            val missingSourcesInfo = stringResource(
                MR.strings.enhanced_services_not_installed,
                enhancedTrackers.second.joinToString { it.name },
            )
            enhancedTrackerInfo += "\n\n$missingSourcesInfo"
        }

        val servicesGroup = Preference.PreferenceGroup(
            title = stringResource(MR.strings.services),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TrackerPreference(
                    tracker = trackerManager.myAnimeList,
                    login = { context.openInBrowser(MyAnimeListApi.authUrl(), forceDefaultBrowser = true) },
                    logout = { dialog = LogoutDialog(trackerManager.myAnimeList) },
                ),
                Preference.PreferenceItem.TrackerPreference(
                    tracker = trackerManager.aniList,
                    login = { context.openInBrowser(AnilistApi.authUrl(), forceDefaultBrowser = true) },
                    logout = { dialog = LogoutDialog(trackerManager.aniList) },
                ),
                Preference.PreferenceItem.TrackerPreference(
                    tracker = trackerManager.kitsu,
                    login = { dialog = LoginDialog(trackerManager.kitsu, MR.strings.email) },
                    logout = { dialog = LogoutDialog(trackerManager.kitsu) },
                ),
                Preference.PreferenceItem.TrackerPreference(
                    tracker = trackerManager.mangaUpdates,
                    login = { dialog = LoginDialog(trackerManager.mangaUpdates, MR.strings.username) },
                    logout = { dialog = LogoutDialog(trackerManager.mangaUpdates) },
                ),
                Preference.PreferenceItem.TrackerPreference(
                    tracker = trackerManager.shikimori,
                    login = { context.openInBrowser(ShikimoriApi.authUrl(), forceDefaultBrowser = true) },
                    logout = { dialog = LogoutDialog(trackerManager.shikimori) },
                ),
                Preference.PreferenceItem.TrackerPreference(
                    tracker = trackerManager.bangumi,
                    login = { context.openInBrowser(BangumiApi.authUrl(), forceDefaultBrowser = true) },
                    logout = { dialog = LogoutDialog(trackerManager.bangumi) },
                ),
                Preference.PreferenceItem.TrackerPreference(
                    tracker = trackerManager.comick,
                    login = { context.openInBrowser(ComickApi.authUrl(), forceDefaultBrowser = true) },
                    logout = { dialog = LogoutDialog(trackerManager.comick) },
                ),
                Preference.PreferenceItem.InfoPreference(stringResource(MR.strings.tracking_info)),
            ),
        )
        val enhancedGroup = Preference.PreferenceGroup(
            title = stringResource(MR.strings.enhanced_services),
            preferenceItems = (
                enhancedTrackers.first
                    .map { service ->
                        Preference.PreferenceItem.TrackerPreference(
                            tracker = service,
                            login = { (service as EnhancedTracker).loginNoop() },
                            logout = service::logout,
                        )
                    } + listOf(Preference.PreferenceItem.InfoPreference(enhancedTrackerInfo))
                ).toImmutableList(),
        )
        val automationGroup = newGroup(
            title = "Automation",
            items = listOf(
                Preference.PreferenceItem.SwitchPreference(
                    preference = trackPreferences.trackOnAddingToLibrary(),
                    title = stringResource(KMR.strings.pref_track_on_add_library),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = trackPreferences.autoUpdateTrack(),
                    title = stringResource(MR.strings.pref_auto_update_manga_sync),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = trackPreferences.autoUpdateTrackOnMarkRead(),
                    entries = AutoTrackState.entries
                        .associateWith { stringResource(it.titleRes) }
                        .toPersistentMap(),
                    title = stringResource(MR.strings.pref_auto_update_manga_on_mark_read),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = trackPreferences.autoSyncProgressFromTrackers(),
                    title = "Sync progress from trackers",
                    subtitle = "Refreshes tracking from the tracker service when you open an entry",
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = trackPreferences.autoRefreshTrackingMetadata(),
                    title = "Refresh tracking metadata daily",
                    subtitle = "Run the tracking refresh in the background once a day when connected and the battery is not low",
                    onValueChanged = { TrackingSyncJob.schedulePeriodic(context, it); true },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = trackPreferences.resolveUsingSourceMetadata(),
                    title = stringResource(SYMR.strings.pref_tracker_resolve_using_source_metadata),
                    subtitle = stringResource(SYMR.strings.pref_tracker_resolve_using_source_metadata_summary),
                ),
                Preference.PreferenceItem.TextPreference(
                    title = if (trackingActive) "Cancel tracking refresh" else "Refresh tracking metadata",
                    subtitle = TrackingSyncJob.status(trackingWork),
                    onClick = {
                        if (trackingActive) TrackingSyncJob.cancel(context) else TrackingSyncJob.start(context)
                    },
                ),
                Preference.PreferenceItem.TextPreference(
                    title = if (autoTrackActive) "Stop linking trackers" else "Link MangaDex titles automatically",
                    subtitle = autoTrackSubtitle,
                    onClick = {
                        if (autoTrackActive) {
                            AutoTrackBackfillJob.cancel(context)
                        } else if (!AutoTrackBackfillJob.tryStart(context)) {
                            context.toast("Sign in to a tracker first")
                        }
                    },
                ),
            ),
        )

        return listOf(servicesGroup, enhancedGroup, automationGroup)
    }

    @Composable
    private fun TrackingLoginDialog(
        tracker: Tracker,
        uNameStringRes: StringResource,
        onDismissRequest: () -> Unit,
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        var username by remember { mutableStateOf(TextFieldValue(tracker.getUsername())) }
        var password by remember { mutableStateOf(TextFieldValue(tracker.getPassword())) }
        var processing by remember { mutableStateOf(false) }
        var inputError by remember { mutableStateOf(false) }

        SettingsAlert(onDismissRequest = onDismissRequest,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TsuzukiText(
                    text = stringResource(MR.strings.login_title, tracker.name),
                    modifier = Modifier.weight(1f),
                )
                SettingsIconControl(onClick = onDismissRequest) { TsuzukiIcon(
                    TsuzukiIcons.X,
                    contentDescription = stringResource(MR.strings.action_close),
                    tint = TsuzukiTheme.colors.text,
                ) }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.medium)) {
                SettingsTextField(modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentType = ContentType.Username + ContentType.EmailAddress },
                value = username,
                onValueChange = { username = it },
                label = { TsuzukiText(text = stringResource(uNameStringRes)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                singleLine = true,
                isError = inputError && !processing,)

                var hidePassword by remember { mutableStateOf(true) }
                SettingsTextField(modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentType = ContentType.Password },
                value = password,
                onValueChange = { password = it },
                label = { TsuzukiText(text = stringResource(MR.strings.password)) },
                trailingIcon = {
                    SettingsIconControl(onClick = { hidePassword = !hidePassword }) { TsuzukiIcon(
                        if (hidePassword) {
                            TsuzukiIcons.Eye
                        } else {
                            TsuzukiIcons.EyeOff
                        },
                        contentDescription = null,
                        tint = TsuzukiTheme.colors.secondary,
                    ) }
                },
                visualTransformation = if (hidePassword) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                singleLine = true,
                isError = inputError && !processing,)
            }
        },
        confirmButton = {
            PillButton(
                label = stringResource(if (processing) MR.strings.logging_in else MR.strings.login),
                onClick = {
                    scope.launch {
                        processing = true
                        try {
                            val result = checkLogin(
                                context = context,
                                tracker = tracker,
                                username = username.text,
                                password = password.text,
                            )
                            inputError = !result
                            if (result) onDismissRequest()
                        } finally {
                            processing = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !processing && username.text.isNotBlank() && password.text.isNotBlank(),
            )
        },)
    }

    private suspend fun checkLogin(
        context: Context,
        tracker: Tracker,
        username: String,
        password: String,
    ): Boolean {
        return try {
            trackerLogin { tracker.login(username, password) }
            withUIContext { context.toast(MR.strings.login_success) }
            true
        } catch (e: TimeoutCancellationException) {
            tracker.logout()
            withUIContext { context.toast("Sign-in timed out. Check your connection and try again.") }
            false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            tracker.logout()
            withUIContext { context.toast(e.message.toString()) }
            false
        }
    }

    @Composable
    private fun TrackingLogoutDialog(
        tracker: Tracker,
        onDismissRequest: () -> Unit,
    ) {
        val context = LocalContext.current
        ConfirmSheet(
            visible = true,
            title = stringResource(MR.strings.logout_title, tracker.name),
            confirmLabel = stringResource(MR.strings.logout),
            destructive = true,
            onConfirm = {
                tracker.logout()
                onDismissRequest()
                context.toast(MR.strings.logout_success)
            },
            onDismissRequest = onDismissRequest,
        )
    }
}

private data class LoginDialog(
    val tracker: Tracker,
    val uNameStringRes: StringResource,
)

private data class LogoutDialog(
    val tracker: Tracker,
)
