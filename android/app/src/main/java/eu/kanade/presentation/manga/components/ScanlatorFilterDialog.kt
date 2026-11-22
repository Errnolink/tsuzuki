package eu.kanade.presentation.manga.components

import androidx.compose.runtime.Composable
import dev.errnolink.tsuzuki.ui.details.ScanlatorFilterSheet
import kotlinx.collections.immutable.ImmutableSet

@Composable
fun ScanlatorFilterDialog(
    availableScanlators: ImmutableSet<String>,
    excludedScanlators: ImmutableSet<String>,
    onDismissRequest: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    ScanlatorFilterSheet(
        availableScanlators = availableScanlators,
        excludedScanlators = excludedScanlators,
        onDismissRequest = onDismissRequest,
        onConfirm = onConfirm,
    )
}
