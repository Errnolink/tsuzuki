package dev.errnolink.tsuzuki.ui.details

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.errnolink.tsuzuki.designsystem.GlassContext
import dev.errnolink.tsuzuki.designsystem.glassSource
import dev.errnolink.tsuzuki.designsystem.tsuzukiFlingBehavior

internal val DetailsStartPaneWidth = 360.dp
internal val DetailsEndPaneMaxWidth = 840.dp
internal val DetailsPaneGap = 24.dp

@Composable
internal fun DetailsTwoPane(
    headerState: LazyListState,
    bodyState: LazyListState,
    glass: GlassContext,
    contentPadding: PaddingValues,
    header: LazyListScope.() -> Unit,
    body: LazyListScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxSize()) {
        LazyColumn(
            flingBehavior = tsuzukiFlingBehavior(),
            state = headerState,
            modifier = Modifier.width(DetailsStartPaneWidth).glassSource(glass),
            contentPadding = contentPadding,
            content = header,
        )
        Spacer(Modifier.width(DetailsPaneGap))
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            LazyColumn(
                flingBehavior = tsuzukiFlingBehavior(),
                state = bodyState,
                modifier = Modifier.fillMaxSize().widthIn(max = DetailsEndPaneMaxWidth).glassSource(glass),
                contentPadding = contentPadding,
                content = body,
            )
        }
    }
}
