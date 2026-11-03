package eu.kanade.presentation.manga.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedSuggestionChip
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.ChipBorder
import eu.kanade.presentation.components.SuggestionChip
import eu.kanade.presentation.components.SuggestionChipDefaults
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import exh.metadata.metadata.MangaDexSearchMetadata
import exh.metadata.metadata.RaisedSearchMetadata
import exh.metadata.metadata.base.RaisedTag
import exh.util.SourceTagsUtil
import exh.source.MANGADEX_IDS
import androidx.compose.material3.SuggestionChipDefaults as SuggestionChipDefaultsM3

@Immutable
data class DisplayTag(
    val namespace: String?,
    val text: String,
    val search: String,
    val border: Int?,
)

@Immutable
@JvmInline
value class SearchMetadataChips(
    val tags: Map<String, List<DisplayTag>>,
) {
    companion object {
        operator fun invoke(meta: RaisedSearchMetadata?, sourceId: Long, tags: List<String>?): SearchMetadataChips? {
            return if (meta != null) {
                SearchMetadataChips(
                    meta.tags
                        .filterNot { it.type == RaisedSearchMetadata.TAG_TYPE_VIRTUAL }
                        .map {
                            DisplayTag(
                                namespace = it.namespace,
                                text = it.name,
                                search = if (!it.namespace.isNullOrEmpty()) {
                                    SourceTagsUtil.getWrappedTag(sourceId, namespace = it.namespace, tag = it.name)
                                } else {
                                    SourceTagsUtil.getWrappedTag(sourceId, fullTag = it.name)
                                } ?: it.name,
                                border = null,
                            )
                        }
                        .groupBy { it.namespace.orEmpty() },
                )
            } else if (tags != null && tags.all { it.contains(':') }) {
                SearchMetadataChips(
                    tags
                        .map { tag ->
                            val index = tag.indexOf(':')
                            DisplayTag(tag.substring(0, index).trim(), tag.substring(index + 1).trim(), tag, null)
                        }
                        .groupBy {
                            it.namespace.orEmpty()
                        },
                )
            } else {
                null
            }
        }
    }
}

@Composable
fun NamespaceTags(
    tags: SearchMetadataChips,
    onClick: (item: String) -> Unit,
    // KMK -->
    pureDarkMode: Boolean = false,
    // KMK <--
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        tags.tags.forEach { (namespace, tags) ->
            Row(Modifier.padding(start = 16.dp)) {
                if (namespace.isNotEmpty()) {
                    TagsChip(
                        modifier = Modifier.padding(top = 4.dp),
                        text = namespace,
                        onClick = null,
                        // KMK -->
                        pureDarkMode = pureDarkMode,
                        // KMK <--
                    )
                }
                FlowRow(
                    modifier = Modifier.padding(start = 8.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    tags.forEach { (_, text, search, border) ->
                        val borderDp = border?.dp
                        TagsChip(
                            modifier = Modifier.padding(vertical = 4.dp),
                            text = text,
                            onClick = { onClick(search) },
                            border = borderDp?.let {
                                SuggestionChipDefaults.suggestionChipBorder(
                                    borderWidth = it,
                                    // KMK -->
                                    borderColor = MaterialTheme.colorScheme.primary,
                                    // KMK <--
                                )
                            } ?: SuggestionChipDefaults.suggestionChipBorder(
                                // KMK -->
                                borderColor = MaterialTheme.colorScheme.primary,
                                // KMK <--
                            ),
                            borderM3 = borderDp?.let {
                                SuggestionChipDefaultsM3.suggestionChipBorder(
                                    enabled = true,
                                    borderWidth = it,
                                    // KMK -->
                                    borderColor = MaterialTheme.colorScheme.primary,
                                    // KMK <--
                                )
                            } ?: SuggestionChipDefaultsM3.suggestionChipBorder(
                                enabled = true,
                                // KMK -->
                                borderColor = MaterialTheme.colorScheme.primary,
                                // KMK <--
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TagsChip(
    text: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    border: ChipBorder? = SuggestionChipDefaults.suggestionChipBorder(),
    // KMK -->
    // borderM3: BorderStroke? = SuggestionChipDefaultsM3.suggestionChipBorder(enabled = true),
    borderM3: BorderStroke? = null,
    pureDarkMode: Boolean = false,
    // KMK <--
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        if (onClick != null) {
            // KMK -->
            if (borderM3 != null || pureDarkMode) {
                // KMK <--
                SuggestionChip(
                    modifier = modifier,
                    onClick = onClick,
                    label = {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    border = borderM3
                        // KMK -->
                        ?: SuggestionChipDefaultsM3.suggestionChipBorder(
                            enabled = true,
                            borderColor = MaterialTheme.colorScheme.primary,
                        ),
                )
            } else {
                ElevatedSuggestionChip(
                    modifier = modifier,
                    onClick = onClick,
                    label = {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    colors = SuggestionChipDefaultsM3.elevatedSuggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                )
            }
            // KMK <--
        } else {
            SuggestionChip(
                modifier = modifier,
                label = {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                border = border,
            )
        }
    }
}

@PreviewLightDark
@Composable
fun NamespaceTagsPreview() {
    TachiyomiPreviewTheme {
        Surface {
            NamespaceTags(
                tags = remember {
                    MangaDexSearchMetadata().apply {
                        this.tags.addAll(
                            listOf(
                                RaisedTag("Male", "Test", 1),
                                RaisedTag("Male", "Test2", 1),
                                RaisedTag("Female", "Test", 1),
                                RaisedTag("Female", "Test2", 1),
                            ),
                        )
                    }.let { SearchMetadataChips(it, MANGADEX_IDS.first(), emptyList()) }!!
                },
                onClick = {},
            )
        }
    }
}
