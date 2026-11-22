package dev.errnolink.tsuzuki.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One run of description text carrying a single markdown meaning. */
internal data class DescriptionInline(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val url: String? = null,
)

/** Block-level structure of a description; everything outside the supported subset stays literal text. */
internal sealed interface DescriptionBlock {
    data class Paragraph(val lines: List<List<DescriptionInline>>) : DescriptionBlock
    data class BulletList(val items: MutableList<List<DescriptionInline>>) : DescriptionBlock
    data object Divider : DescriptionBlock
}

private val descriptionDividerLine = Regex("""^\s*(-{3,}|\*{3,}|_{3,})\s*$""")
private val descriptionUnorderedItem = Regex("""^\s*[-*+]\s+(.*)$""")
private val descriptionBoldMarker = Regex("""\*\*(.+?)\*\*""")
private val descriptionItalicStar = Regex("""(?<!\*)\*([^*\n]+)\*(?!\*)""")
private val descriptionItalicUnderscore = Regex("""(?<![\w])_([^_\n]+)_(?![_\w])""")
private val descriptionLinkMarker = Regex("""\[([^]\n]+)]\(([^()\n]+)\)""")

/**
 * Parses the description subset the title page renders: bold, italic, links, line breaks,
 * simple unordered lists and `---` dividers. Unknown syntax (headings, tables, fenced code,
 * unmatched markers) is kept as plain text.
 */
internal fun parseDescriptionBlocks(source: String): List<DescriptionBlock> {
    val blocks = mutableListOf<DescriptionBlock>()
    var paragraphLines = mutableListOf<List<DescriptionInline>>()

    fun flushParagraph() {
        if (paragraphLines.isNotEmpty()) {
            blocks.add(DescriptionBlock.Paragraph(paragraphLines))
            paragraphLines = mutableListOf()
        }
    }

    fun addListItem(item: List<DescriptionInline>) {
        val last = blocks.lastOrNull()
        if (last is DescriptionBlock.BulletList) {
            last.items.add(item)
        } else {
            blocks.add(DescriptionBlock.BulletList(mutableListOf(item)))
        }
    }

    for (rawLine in source.lines()) {
        val line = rawLine.trim()
        when {
            descriptionDividerLine.matches(line) -> {
                flushParagraph()
                if (blocks.lastOrNull() != DescriptionBlock.Divider) blocks.add(DescriptionBlock.Divider)
            }
            descriptionUnorderedItem.matches(line) -> {
                flushParagraph()
                addListItem(parseDescriptionInline(descriptionUnorderedItem.find(line)!!.groupValues[1]))
            }
            line.isEmpty() -> flushParagraph()
            else -> paragraphLines.add(parseDescriptionInline(line))
        }
    }
    flushParagraph()
    return blocks
}

private fun parseDescriptionInline(line: String): List<DescriptionInline> {
    val spans = mutableListOf<DescriptionInline>()
    var cursor = 0
    while (cursor < line.length) {
        val match = firstInlineMatch(line, cursor)
        if (match == null) {
            spans.add(DescriptionInline(line.substring(cursor)))
            break
        }
        if (match.first > cursor) {
            spans.add(DescriptionInline(line.substring(cursor, match.first)))
        }
        when (match.second.first) {
            InlineKind.Bold -> spans.add(DescriptionInline(match.second.second, bold = true))
            InlineKind.Italic -> spans.add(DescriptionInline(match.second.second, italic = true))
            InlineKind.Link -> spans.add(DescriptionInline(match.second.second, url = line.substring(match.thirdStart, match.thirdEnd)))
        }
        cursor = match.secondEnd
    }
    return spans
}

private enum class InlineKind { Bold, Italic, Link }

private data class InlineMatch(
    val first: Int,
    val second: Pair<InlineKind, String>,
    val secondEnd: Int,
    val thirdStart: Int,
    val thirdEnd: Int,
)

/** Finds the earliest supported inline construct at or after [from]; null keeps the rest literal. */
private fun firstInlineMatch(line: String, from: Int): InlineMatch? {
    var best: InlineMatch? = null
    for (regex in listOf(descriptionBoldMarker, descriptionItalicStar, descriptionItalicUnderscore, descriptionLinkMarker)) {
        val found = regex.find(line, from) ?: continue
        val current = when (regex) {
            descriptionBoldMarker -> InlineMatch(found.range.first, InlineKind.Bold to found.groupValues[1], found.range.last + 1, -1, -1)
            descriptionItalicStar, descriptionItalicUnderscore -> InlineMatch(found.range.first, InlineKind.Italic to found.groupValues[1], found.range.last + 1, -1, -1)
            else -> {
                val urlRange = found.groups[2]?.range
                InlineMatch(found.range.first, InlineKind.Link to found.groupValues[1], found.range.last + 1, urlRange?.first ?: -1, urlRange?.last?.plus(1) ?: -1)
            }
        }
        if (best == null || current.first < best.first) best = current
    }
    return best
}

internal const val DescriptionDividerId = "tsuzuki-description-divider"

/** Renders parsed blocks as an [AnnotatedString]; blocks and lines are separated by line breaks. */
internal fun descriptionAnnotated(
    blocks: List<DescriptionBlock>,
    accent: Color,
    onLink: ((url: String) -> Unit)? = null,
): AnnotatedString = buildAnnotatedString {
    blocks.forEachIndexed { blockIndex, block ->
        if (blockIndex > 0) append('\n')
        when (block) {
            is DescriptionBlock.Paragraph -> block.lines.forEachIndexed { lineIndex, line ->
                if (lineIndex > 0) append('\n')
                appendDescriptionInline(line, accent, onLink)
            }
            is DescriptionBlock.BulletList -> block.items.forEachIndexed { itemIndex, item ->
                if (itemIndex > 0) append('\n')
                append("•  ")
                appendDescriptionInline(item, accent, onLink)
            }
            DescriptionBlock.Divider -> appendInlineContent(DescriptionDividerId, " ")
        }
    }
}

private fun AnnotatedString.Builder.appendDescriptionInline(
    spans: List<DescriptionInline>,
    accent: Color,
    onLink: ((url: String) -> Unit)?,
) {
    for (span in spans) {
        val base = SpanStyle(
            fontWeight = if (span.bold) FontWeight.Bold else null,
            fontStyle = if (span.italic) FontStyle.Italic else null,
        )
        val url = span.url
        if (url != null) {
            val style = base.merge(SpanStyle(color = accent, textDecoration = TextDecoration.Underline))
            withLink(LinkAnnotation.Url(url, TextLinkStyles(style)) { onLink?.invoke(url) }) {
                append(span.text)
            }
        } else if (span.bold || span.italic) {
            withStyle(base) { append(span.text) }
        } else {
            append(span.text)
        }
    }
}

/** Inline content drawing the `---` divider as a hairline rule. */
internal fun descriptionInlineContent(accent: Color): Map<String, InlineTextContent> = mapOf(
    DescriptionDividerId to InlineTextContent(
        Placeholder(width = 28.sp, height = 6.sp, placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(accent.copy(alpha = 0.35f)),
            )
        }
    },
)
