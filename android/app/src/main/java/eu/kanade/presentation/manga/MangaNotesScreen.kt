package eu.kanade.presentation.manga

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.ui.shell.ShellCaption
import dev.errnolink.tsuzuki.ui.shell.ShellContentScaffold
import eu.kanade.presentation.manga.components.MangaNotesTextArea
import eu.kanade.tachiyomi.ui.manga.notes.MangaNotesScreen
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun MangaNotesScreen(
    state: MangaNotesScreen.State,
    navigateUp: () -> Unit,
    onUpdate: (String) -> Unit,
) {
    // B34 legacy sweep: the Material Scaffold/AppBar pair is replaced by the Tsuzuki shell;
    // the rich-text area and its format row are unchanged.
    ShellContentScaffold(
        title = stringResource(MR.strings.action_edit_notes),
        navigateUp = navigateUp,
        header = {
            ShellCaption(
                state.manga.title,
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 2.dp),
            )
        },
    ) { contentPadding ->
        MangaNotesTextArea(
            state = state,
            onUpdate = onUpdate,
            modifier = Modifier
                .padding(contentPadding)
                .imePadding(),
        )
    }
}
