package dev.errnolink.tsuzuki.ui.history

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HistoryChapterLabelTest {

    @Test
    fun `numbered chapters use the formatted number`() {
        assertEquals("Ch. 12.5", historyChapterLabel(12.5, "Oneshot"))
        assertEquals("Ch. 12.5", historyChapterLabel(12.5, null))
    }

    @Test
    fun `unnumbered chapters with a name show the name`() {
        assertEquals("Oneshot", historyChapterLabel(-1.0, "Oneshot"))
    }

    @Test
    fun `unnumbered chapters without a name fall back to Chapter`() {
        assertEquals("Chapter", historyChapterLabel(-1.0, ""))
        assertEquals("Chapter", historyChapterLabel(-1.0, "  "))
        assertEquals("Chapter", historyChapterLabel(-1.0, null))
    }
}
