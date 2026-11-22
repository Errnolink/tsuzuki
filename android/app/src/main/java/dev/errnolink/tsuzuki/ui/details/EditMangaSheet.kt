package dev.errnolink.tsuzuki.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.Chip
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.PopUpItem
import dev.errnolink.tsuzuki.designsystem.PopUpRow
import dev.errnolink.tsuzuki.designsystem.Plus
import dev.errnolink.tsuzuki.designsystem.SheetKind
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.coverShape
import dev.errnolink.tsuzuki.ui.settings.SettingsTextField
import dev.errnolink.tsuzuki.ui.settings.TextEntrySheet
import dev.errnolink.tsuzuki.ui.shell.ShellCover
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.manga.components.RatioSwitchToPanorama
import eu.kanade.presentation.track.components.TrackLogoIcon
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.lang.chop
import eu.kanade.tachiyomi.util.system.toast
import exh.util.dropBlank
import exh.util.trimOrNull
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource as contextStringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.model.Track
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.core.common.i18n.stringResource as contextStringResource
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

internal fun editMangaStatusToSpinnerIndex(status: Int): Int = when (status) {
    SManga.ONGOING -> 1
    SManga.COMPLETED -> 2
    SManga.LICENSED -> 3
    SManga.PUBLISHING_FINISHED, 61 -> 4
    SManga.CANCELLED, 62 -> 5
    SManga.ON_HIATUS, 63 -> 6
    else -> 0
}

internal fun editMangaStatusFromSpinnerIndex(index: Int): Long? = when (index) {
    1 -> SManga.ONGOING
    2 -> SManga.COMPLETED
    3 -> SManga.LICENSED
    4 -> SManga.PUBLISHING_FINISHED
    5 -> SManga.CANCELLED
    6 -> SManga.ON_HIATUS
    else -> null
}?.toLong()

/**
 * Wave 2 WP11.3: DetentSheet port of `eu.kanade.tachiyomi.ui.manga.EditMangaDialog` (the XML
 * binding dialog is retired). Every upstream field survives: the cover preview (panorama when
 * the preference and ratio say so), status picker, title/author/artist/thumbnail/description
 * with the original-value hints, the tag chips with their add/remove entry, autofill from
 * tracker (toast when untracked, direct fill for one tracker, the picker for several), reset
 * tags, reset info, and the Save callback shape.
 */
@Composable
fun EditMangaSheet(
    manga: Manga,
    coverRatio: MutableFloatState,
    onDismissRequest: () -> Unit,
    onPositiveClick: (
        title: String?,
        author: String?,
        artist: String?,
        thumbnailUrl: String?,
        description: String?,
        tags: List<String>?,
        status: Long?,
    ) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val getTracks = remember { Injekt.get<GetTracks>() }
    val trackerManager = remember { Injekt.get<TrackerManager>() }
    val isLocal = manga.isLocal()

    var title by remember {
        mutableStateOf(
            when {
                isLocal -> manga.title.takeIf { it != manga.url }.orEmpty()
                manga.title != manga.ogTitle -> manga.title
                else -> ""
            },
        )
    }
    var author by remember {
        mutableStateOf(
            when {
                isLocal -> manga.author.orEmpty()
                manga.author != manga.ogAuthor -> manga.author.orEmpty()
                else -> ""
            },
        )
    }
    var artist by remember {
        mutableStateOf(
            when {
                isLocal -> manga.artist.orEmpty()
                manga.artist != manga.ogArtist -> manga.artist.orEmpty()
                else -> ""
            },
        )
    }
    var thumbnailUrl by remember {
        mutableStateOf(
            when {
                isLocal -> manga.thumbnailUrl.orEmpty()
                manga.thumbnailUrl != manga.ogThumbnailUrl -> manga.thumbnailUrl.orEmpty()
                else -> ""
            },
        )
    }
    var description by remember {
        mutableStateOf(
            when {
                isLocal -> manga.description.orEmpty()
                manga.description != manga.ogDescription -> manga.description.orEmpty()
                else -> ""
            },
        )
    }
    var tags by remember { mutableStateOf(manga.genre.orEmpty().dropBlank()) }
    var statusIndex by remember {
        mutableStateOf(if (manga.status != manga.ogStatus) editMangaStatusToSpinnerIndex(manga.status.toInt()) else 0)
    }
    var trackerChoices by remember { mutableStateOf(emptyList<Pair<Track, Tracker>>()) }
    var showTrackerPicker by remember { mutableStateOf(false) }
    var showAddTags by remember { mutableStateOf(false) }

    fun resetTags() {
        tags = if (manga.genre.isNullOrEmpty() || isLocal) {
            emptyList()
        } else {
            manga.ogGenre.orEmpty()
        }
    }

    suspend fun autofillFromTracker(track: Track, tracker: Tracker) {
        try {
            val metadata = tracker.getMangaMetadata(track)
            metadata.title?.takeIf { it.isNotBlank() }?.let { title = it }
            metadata.authors?.takeIf { it.isNotBlank() }?.let { author = it }
            metadata.artists?.takeIf { it.isNotBlank() }?.let { artist = it }
            metadata.thumbnailUrl?.takeIf { it.isNotBlank() }?.let { thumbnailUrl = it }
            metadata.description?.takeIf { it.isNotBlank() }?.let { description = it }
        } catch (e: Throwable) {
            tracker.logcat(LogPriority.ERROR, e)
            context.toast(
                context.contextStringResource(MR.strings.track_error, tracker.name, e.message ?: ""),
            )
        }
    }

    val wideCover = remember {
        Injekt.get<UiPreferences>().usePanoramaCoverAlways().get() && coverRatio.floatValue <= RatioSwitchToPanorama
    }

    DetentSheet(
        visible = true,
        title = stringResource(SYMR.strings.action_edit_info),
        onDismissRequest = onDismissRequest,
        kind = SheetKind.Picker,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = TsuzukiSpacing.medium),
            horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(width = if (wideCover) 168.dp else 96.dp, height = if (wideCover) 96.dp else 140.dp)
                    .clip(coverShape())
                    .background(TsuzukiTheme.colors.elevated),
            ) {
                ShellCover(
                    manga.asMangaCover(),
                    manga.title,
                    Modifier.fillMaxSize(),
                    fixedRatio = if (wideCover) 168f / 96f else 96f / 140f,
                    hero = false,
                )
            }
            Box(Modifier.weight(1f)) {
                PopUpRow(
                    title = stringResource(MR.strings.status),
                    selected = statusIndex,
                    options = listOf(
                        MR.strings.label_default,
                        MR.strings.ongoing,
                        MR.strings.completed,
                        MR.strings.licensed,
                        MR.strings.publishing_finished,
                        MR.strings.cancelled,
                        MR.strings.on_hiatus,
                    ).mapIndexed { index, res -> PopUpItem(index, stringResource(res)) },
                    onSelect = { statusIndex = it },
                    modifier = Modifier.fillMaxWidth(),
                    divider = false,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.medium)) {
            SettingsTextField(
                value = title,
                onValueChange = { title = it },
                label = { TsuzukiText(stringResource(SYMR.strings.title_hint, if (isLocal) manga.url else manga.ogTitle)) },
            )
            SettingsTextField(
                value = author,
                onValueChange = { author = it },
                label = {
                    TsuzukiText(
                        stringResource(
                            SYMR.strings.author_hint,
                            if (isLocal) manga.author.orEmpty() else manga.ogAuthor ?: "",
                        ),
                    )
                },
            )
            SettingsTextField(
                value = artist,
                onValueChange = { artist = it },
                label = {
                    TsuzukiText(
                        stringResource(
                            SYMR.strings.artist_hint,
                            if (isLocal) manga.artist.orEmpty() else manga.ogArtist ?: "",
                        ),
                    )
                },
            )
            SettingsTextField(
                value = thumbnailUrl,
                onValueChange = { thumbnailUrl = it },
                label = {
                    val hints = listOfNotNull(
                        if (isLocal) null else manga.ogThumbnailUrl?.let {
                            it.chop(40) + if (it.length > 46) "." + it.substringAfterLast(".").chop(6) else ""
                        },
                        "file:///storage/emulated/0/Pictures/Komikku/Cover.jpg",
                    ).joinToString("\nor\n")
                    TsuzukiText(stringResource(SYMR.strings.thumbnail_url_hint, hints))
                },
            )
            SettingsTextField(
                value = description,
                onValueChange = { description = it },
                singleLine = false,
                label = {
                    val og = if (isLocal) manga.description else manga.ogDescription
                    TsuzukiText(
                        stringResource(
                            SYMR.strings.description_hint,
                            og?.takeIf { it.isNotBlank() }?.replace("\n", " ")?.chop(20) ?: "",
                        ),
                    )
                },
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small),
                verticalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small),
            ) {
                tags.forEach { tag ->
                    Chip(
                        tag,
                        false,
                        {},
                        role = Role.Button,
                        onRemove = { tags -= tag },
                    )
                }
                Chip(
                    stringResource(SYMR.strings.add_tags),
                    false,
                    { showAddTags = true },
                    role = Role.Button,
                    leading = { Icon(TsuzukiIcons.Plus, null, Modifier.size(16.dp)) },
                )
            }
        }

        FlowRow(
            Modifier.fillMaxWidth().padding(top = TsuzukiSpacing.medium),
            horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small),
        ) {
            PillButton(
                label = stringResource(SYMR.strings.fill_from_tracker),
                onClick = {
                    scope.launch {
                        val choices = getTracks.await(manga.id)
                            .mapNotNull { track -> track to (trackerManager.get(track.trackerId) ?: return@mapNotNull null) }
                            .filterNot { (_, tracker) -> tracker is EnhancedTracker }
                        if (choices.isEmpty()) {
                            context.toast(SYMR.strings.entry_not_tracked)
                            return@launch
                        }
                        if (choices.size == 1) {
                            autofillFromTracker(choices.first().first, choices.first().second)
                        } else {
                            trackerChoices = choices
                            showTrackerPicker = true
                        }
                    }
                },
                prominent = false,
            )
            PillButton(label = stringResource(SYMR.strings.reset_tags), onClick = ::resetTags, prominent = false)
            PillButton(
                label = stringResource(SYMR.strings.reset_info),
                onClick = {
                    title = ""
                    author = ""
                    artist = ""
                    thumbnailUrl = ""
                    description = ""
                    resetTags()
                },
                prominent = false,
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(top = TsuzukiSpacing.medium),
            horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.medium),
        ) {
            Box(Modifier.weight(1f)) {
                PillButton(
                    label = stringResource(MR.strings.action_cancel),
                    onClick = onDismissRequest,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(Modifier.weight(1f)) {
                PillButton(
                    label = stringResource(MR.strings.action_save),
                    onClick = {
                        onPositiveClick(
                            title,
                            author,
                            artist,
                            thumbnailUrl,
                            description,
                            tags,
                            editMangaStatusFromSpinnerIndex(statusIndex),
                        )
                        onDismissRequest()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (showTrackerPicker) {
        DetentSheet(
            visible = true,
            title = stringResource(SYMR.strings.select_tracker),
            onDismissRequest = { showTrackerPicker = false },
            kind = SheetKind.Confirm,
        ) {
            FlowRow(
                Modifier.fillMaxWidth().padding(vertical = TsuzukiSpacing.medium),
                horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.section),
            ) {
                trackerChoices.forEach { (track, tracker) ->
                    TrackLogoIcon(tracker) {
                        scope.launch {
                            showTrackerPicker = false
                            autofillFromTracker(track, tracker)
                        }
                    }
                }
            }
        }
    }

    if (showAddTags) {
        TextEntrySheet(
            visible = true,
            title = stringResource(SYMR.strings.add_tags),
            initialValue = "",
            confirmLabel = stringResource(MR.strings.action_ok),
            errorText = null,
            supportsConfirm = { it.isNotBlank() },
            onConfirm = { text ->
                text.trimOrNull()?.let { trimmed ->
                    tags = tags + trimmed.split(",").mapNotNull { it.trimOrNull() }
                }
                true
            },
            onDismissRequest = { showAddTags = false },
            message = stringResource(SYMR.strings.multi_tags_comma_separated),
        )
    }
}
