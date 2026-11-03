package eu.kanade.tachiyomi.ui.category.genre

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.ui.category.CategoryDeleteDialog
import dev.errnolink.tsuzuki.ui.category.genre.TsuzukiSortTagScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.i18n.sy.SYMR
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.errnolink.tsuzuki.designsystem.ProgressRing
import dev.errnolink.tsuzuki.ui.shell.ShellList
class SortTagScreen : Screen() {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { SortTagScreenModel() }

        val state by screenModel.state.collectAsState()

        if (state is SortTagScreenState.Loading) {
            ShellList(
                title = stringResource(SYMR.strings.action_edit_tags),
                navigateUp = navigator::pop,
            ) {
                item("loading") {
                    Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        ProgressRing(0f, stringResource(MR.strings.loading))
                    }
                }
            }
            return
        }

        val successState = state as SortTagScreenState.Success

        TsuzukiSortTagScreen(
            state = successState,
            onCreate = { screenModel.createTag(it) },
            onClickDelete = { screenModel.showDialog(SortTagDialog.Delete(it)) },
            onClickMoveUp = screenModel::moveUp,
            onClickMoveDown = screenModel::moveDown,
            navigateUp = navigator::pop,
        )

        when (val dialog = successState.dialog) {
            null -> {}
            is SortTagDialog.Delete -> {
                CategoryDeleteDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onDelete = { screenModel.delete(dialog.tag) },
                    title = stringResource(SYMR.strings.delete_tag),
                    text = stringResource(SYMR.strings.delete_tag_confirmation, dialog.tag),
                )
            }
        }

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                if (event is SortTagEvent.LocalizedMessage) {
                    context.toast(event.stringRes)
                }
            }
        }
    }
}
