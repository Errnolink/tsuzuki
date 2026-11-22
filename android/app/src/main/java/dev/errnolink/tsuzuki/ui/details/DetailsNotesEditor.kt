package dev.errnolink.tsuzuki.ui.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import dev.errnolink.tsuzuki.designsystem.tsuzukiRounded
import dev.errnolink.tsuzuki.designsystem.TsuzukiCorners
import eu.kanade.presentation.manga.components.MangaNotesTextArea
import eu.kanade.tachiyomi.ui.manga.notes.MangaNotesScreen
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.manga.interactor.UpdateMangaNotes
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * The title page's notes, edited in the liquid card that pours out of the Notes circle. The rich-text
 * area and its format row are the shared notes editor; edits save as you type and once more when the
 * card closes (on an app-lifetime scope, so the last save outlives the card).
 */
@Composable
fun DetailsNotesEditor(manga: Manga, onDone: () -> Unit) {
    val updateNotes = remember { Injekt.get<UpdateMangaNotes>() }
    val initial = remember(manga.id) { MangaNotesScreen.State(manga, manga.notes) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            // N9: one breathing header — the title is gone (it is this title's own page) and the
            // row gets real spacing instead of three cramped lines.
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TsuzukiText(stringResource(MR.strings.action_notes), style = TsuzukiTheme.typography.headline)
            Box(
                Modifier
                    .clip(tsuzukiRounded(TsuzukiCorners.cover))
                    .clickable(role = Role.Button, onClick = onDone)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                TsuzukiText(stringResource(MR.strings.tsuzuki_done), style = TsuzukiTheme.typography.subhead, color = TsuzukiTheme.colors.accent)
            }
        }
        MangaNotesTextArea(
            state = initial,
            onUpdate = { text ->
                if (text != manga.notes) launchIO { updateNotes(manga.id, text) }
            },
            modifier = Modifier.heightIn(min = 180.dp),
        )
    }
}
