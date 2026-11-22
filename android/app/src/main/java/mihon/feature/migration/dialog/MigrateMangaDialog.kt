package mihon.feature.migration.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import dev.errnolink.tsuzuki.designsystem.Check
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.PillButton
import dev.errnolink.tsuzuki.designsystem.SheetKind
import dev.errnolink.tsuzuki.designsystem.TsuzukiIcons
import dev.errnolink.tsuzuki.designsystem.TsuzukiSpacing
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.designsystem.TsuzukiTheme
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import kotlinx.coroutines.flow.update
import mihon.domain.migration.models.MigrationFlag
import mihon.domain.migration.usecases.MigrateMangaUseCase
import mihon.feature.common.utils.getLabel
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
internal fun Screen.MigrateMangaDialog(
    current: Manga,
    target: Manga,
    onClickTitle: () -> Unit,
    onDismissRequest: () -> Unit,
    onComplete: () -> Unit = onDismissRequest,
) {
    val scope = rememberCoroutineScope()

    val screenModel = rememberScreenModel { MigrateDialogScreenModel() }
    LaunchedEffect(current, target) {
        screenModel.init(current, target)
    }
    val state by screenModel.state.collectAsState()

    if (state.isMigrated) return

    if (state.isMigrating) {
        LoadingScreen(
            modifier = Modifier.background(TsuzukiTheme.colors.background.copy(alpha = 0.7f)),
        )
        return
    }

    DetentSheet(
        visible = true,
        title = stringResource(MR.strings.migration_dialog_what_to_include),
        onDismissRequest = onDismissRequest,
        kind = SheetKind.Picker,
    ) {
        state.applicableFlags.fastForEach { flag ->
            val checked = flag in state.selectedFlags
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = TsuzukiSpacing.touchTarget)
                    .toggleable(value = checked, role = Role.Checkbox) { screenModel.toggleSelection(flag) }
                    .padding(horizontal = TsuzukiSpacing.row, vertical = TsuzukiSpacing.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TsuzukiText(stringResource(flag.getLabel()), Modifier.weight(1f))
                if (checked) {
                    Icon(TsuzukiIcons.Check, null, Modifier.size(20.dp), tint = TsuzukiTheme.colors.accent)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(TsuzukiSpacing.small, Alignment.End),
        ) {
            PillButton(stringResource(MR.strings.action_show_manga), onClickTitle, prominent = false)
            PillButton(
                stringResource(MR.strings.copy),
                {
                    scope.launchIO {
                        screenModel.migrateManga(replace = false)
                        withUIContext { onComplete() }
                    }
                },
                prominent = false,
            )
            PillButton(
                stringResource(MR.strings.migrate),
                {
                    scope.launchIO {
                        screenModel.migrateManga(replace = true)
                        withUIContext { onComplete() }
                    }
                },
            )
        }
    }
}

private class MigrateDialogScreenModel(
    val sourcePreference: SourcePreferences = Injekt.get(),
    private val coverCache: CoverCache = Injekt.get(),
    private val downloadManager: DownloadManager = Injekt.get(),
    private val migrateManga: MigrateMangaUseCase = Injekt.get(),
) : StateScreenModel<MigrateDialogScreenModel.State>(State()) {

    fun init(current: Manga, target: Manga) {
        val applicableFlags = buildList {
            MigrationFlag.entries.forEach {
                val applicable = when (it) {
                    MigrationFlag.CHAPTER -> true
                    MigrationFlag.CATEGORY -> true
                    // KMK -->
                    MigrationFlag.TRACK -> true
                    // KMK <--
                    MigrationFlag.CUSTOM_COVER -> current.hasCustomCover(coverCache)
                    MigrationFlag.NOTES -> current.notes.isNotBlank()
                    MigrationFlag.REMOVE_DOWNLOAD -> downloadManager.getDownloadCount(current) > 0
                    // KMK -->
                    MigrationFlag.EXTRA -> true
                    // KMK <--
                }
                if (applicable) add(it)
            }
        }
        val selectedFlags = sourcePreference.migrationFlags().get()
        mutableState.update {
            State(
                current = current,
                target = target,
                applicableFlags = applicableFlags,
                selectedFlags = selectedFlags,
            )
        }
    }

    fun toggleSelection(flag: MigrationFlag) {
        mutableState.update {
            val selectedFlags = it.selectedFlags.toMutableSet()
                .apply { if (contains(flag)) remove(flag) else add(flag) }
                .toSet()
            it.copy(selectedFlags = selectedFlags)
        }
    }

    suspend fun migrateManga(replace: Boolean) {
        val state = state.value
        val current = state.current ?: return
        val target = state.target ?: return
        // KMK -->
        // sourcePreference.migrationFlags().set(state.selectedFlags)
        // KMK <--
        mutableState.update { it.copy(isMigrating = true) }
        try {
            migrateManga(current, target, replace, /* KMK --> */ state.selectedFlags /* KMK <-- */)
            mutableState.update { it.copy(isMigrating = false, isMigrated = true) }
            // KMK -->
        } catch (_: Throwable) {
            mutableState.update { it.copy(isMigrating = false, isMigrated = false) }
            // KMK <--
        }
    }

    data class State(
        val current: Manga? = null,
        val target: Manga? = null,
        val applicableFlags: List<MigrationFlag> = emptyList(),
        val selectedFlags: Set<MigrationFlag> = emptySet(),
        val isMigrating: Boolean = false,
        val isMigrated: Boolean = false,
    )
}
