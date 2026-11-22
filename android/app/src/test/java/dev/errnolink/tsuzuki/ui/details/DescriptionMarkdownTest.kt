package dev.errnolink.tsuzuki.ui.details

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DescriptionMarkdownTest {

    private fun plain(text: String) = DescriptionInline(text)

    private fun inlineOf(block: DescriptionBlock): List<DescriptionInline> = when (block) {
        is DescriptionBlock.Paragraph -> block.lines.single()
        is DescriptionBlock.BulletList -> block.items.single()
        DescriptionBlock.Divider -> emptyList()
    }

    @Test
    fun `bold renders as a styled span`() {
        val blocks = parseDescriptionBlocks("**Rating:** 7.69 / 10")
        val line = (blocks.single() as DescriptionBlock.Paragraph).lines.single()
        assertEquals(listOf(DescriptionInline("Rating:", bold = true), plain(" 7.69 / 10")), line)
    }

    @Test
    fun `italic works with asterisk and underscore markers`() {
        val star = inlineOf(parseDescriptionBlocks("*emphasis*") .single())
        assertEquals(listOf(DescriptionInline("emphasis", italic = true)), star)
        val underscore = inlineOf(parseDescriptionBlocks("a _word_ here").single())
        assertEquals(listOf(plain("a "), DescriptionInline("word", italic = true), plain(" here")), underscore)
    }

    @Test
    fun `underscore inside a word is not italic`() {
        val line = inlineOf(parseDescriptionBlocks("snake_case_name stays").single())
        assertEquals(listOf(plain("snake_case_name stays")), line)
    }

    @Test
    fun `link keeps its target url`() {
        val line = inlineOf(parseDescriptionBlocks("Read on [MangaDex](https://mangadex.org) today").single())
        assertEquals(
            listOf(plain("Read on "), DescriptionInline("MangaDex", url = "https://mangadex.org"), plain(" today")),
            line,
        )
    }

    @Test
    fun `line breaks and blank lines split lines and paragraphs`() {
        val blocks = parseDescriptionBlocks("first\nsecond\n\nthird")
        assertEquals(2, blocks.size)
        val first = blocks[0] as DescriptionBlock.Paragraph
        assertEquals(listOf("first", "second"), first.lines.map { spans -> spans.single().text })
        val second = blocks[1] as DescriptionBlock.Paragraph
        assertEquals(listOf("third"), second.lines.map { it.single().text })
    }

    @Test
    fun `unordered list lines become list items`() {
        val blocks = parseDescriptionBlocks("Intro\n- item one\n- item two")
        assertEquals(2, blocks.size)
        val list = blocks[1] as DescriptionBlock.BulletList
        assertEquals(listOf("item one", "item two"), list.items.map { it.single().text })
    }

    @Test
    fun `three dashes render as one divider`() {
        val blocks = parseDescriptionBlocks("before\n\n---\n\nafter")
        assertEquals(listOf<DescriptionBlock>(DescriptionBlock.Paragraph(listOf(listOf(plain("before")))), DescriptionBlock.Divider, DescriptionBlock.Paragraph(listOf(listOf(plain("after"))))), blocks)
    }

    @Test
    fun `unknown syntax stays plain text`() {
        val heading = inlineOf(parseDescriptionBlocks("# Heading").single())
        assertEquals(listOf(plain("# Heading")), heading)
        val strike = inlineOf(parseDescriptionBlocks("~~struck~~").single())
        assertEquals(listOf(plain("~~struck~~")), strike)
    }

    @Test
    fun `unmatched markers stay literal`() {
        val bold = inlineOf(parseDescriptionBlocks("**never closed").single())
        assertEquals(listOf(plain("**never closed")), bold)
        val link = inlineOf(parseDescriptionBlocks("[text without target]()").single())
        assertEquals(listOf(plain("[text without target]()")), link)
    }

    @Test
    fun `mixed line keeps bold and link in order`() {
        val line = inlineOf(parseDescriptionBlocks("**Note** see [site](https://example.org) now").single())
        assertEquals(
            listOf(
                DescriptionInline("Note", bold = true),
                plain(" see "),
                DescriptionInline("site", url = "https://example.org"),
                plain(" now"),
            ),
            line,
        )
    }

    @Test
    fun `rendered text keeps plain reading order`() {
        val text = descriptionAnnotated(
            parseDescriptionBlocks("**Bold** start\n---\n- bullet"),
            accent = androidx.compose.ui.graphics.Color.Blue,
        ).text
        assertTrue(text.contains("Bold start"))
        assertTrue(text.contains("•  bullet"))
    }
}
