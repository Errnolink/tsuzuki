package exh.md.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SimilarServiceParserTest {

    @Test
    fun parseSimilarDataLine_extractsTargetMatchingEntry() {
        val fixture = """
f981d2b1-1f56-47df-af1a-2c3db7f9e42b:::||@!@||:::{"id":"f981d2b1-1f56-47df-af1a-2c3db7f9e42b","title":{"en":"Yukionna Gensou - Michiyuki Hen"},"contentRating":"safe","matches":[{"id":"1017a17e-e409-4bf9-85f3-2751a4f83114","title":{"en":"Non-chan no Tenohira"},"score":0.33756408,"languages":["en"]},{"id":"642c43ce-47d0-47c5-984d-e323f7092ff5","title":{"en":"The Bride of Montefalco"},"score":0.30756664,"languages":["en"]}],"updatedAt":"2026-10-10"}
f983b7db-942e-4211-ade8-43f2aa39a77f:::||@!@||:::{"id":"f983b7db-942e-4211-ade8-43f2aa39a77f","title":{"en":"Campus Master"},"contentRating":"safe","matches":[{"id":"8ccffe01-9d3a-47d2-b3cb-a1448053643a","title":{"en":"If You're Gonna Dress Up"},"score":0.34738654,"languages":["en"]}],"updatedAt":"2026-10-10"}
        """.trimIndent()

        val parsed = SimilarService.parseSimilarDataLine(fixture, "f981d2b1-1f56-47df-af1a-2c3db7f9e42b")
        assertNotNull(parsed)
        assertEquals("f981d2b1-1f56-47df-af1a-2c3db7f9e42b", parsed!!.id)
        assertEquals("Yukionna Gensou - Michiyuki Hen", parsed.title["en"])
        assertEquals(2, parsed.matches.size)
        assertEquals("1017a17e-e409-4bf9-85f3-2751a4f83114", parsed.matches[0].id)
        assertEquals(0.33756408, parsed.matches[0].score, 0.00001)
        assertEquals(listOf("en"), parsed.matches[0].languages)
    }

    @Test
    fun parseSimilarDataLine_returnsNullWhenNotFound() {
        val fixture = """
f981d2b1-1f56-47df-af1a-2c3db7f9e42b:::||@!@||:::{"id":"f981d2b1-1f56-47df-af1a-2c3db7f9e42b","title":{"en":"Test"},"contentRating":"safe","matches":[],"updatedAt":"2026-10-10"}
        """.trimIndent()

        val parsed = SimilarService.parseSimilarDataLine(fixture, "non-existent-uuid")
        assertNull(parsed)
    }
}
