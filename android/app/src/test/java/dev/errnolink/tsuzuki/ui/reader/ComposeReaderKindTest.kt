package dev.errnolink.tsuzuki.ui.reader

import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ComposeReaderKindTest {

    @Test
    fun `horizontal and vertical pager modes resolve to the pager viewer`() {
        assertEquals(
            ComposeReaderKind.Pager(isRtl = false, isVertical = false),
            resolveComposeReaderKind(ReadingMode.LEFT_TO_RIGHT),
        )
        assertEquals(
            ComposeReaderKind.Pager(isRtl = true, isVertical = false),
            resolveComposeReaderKind(ReadingMode.RIGHT_TO_LEFT),
        )
        assertEquals(
            ComposeReaderKind.Pager(isRtl = false, isVertical = true),
            resolveComposeReaderKind(ReadingMode.VERTICAL),
        )
    }

    @Test
    fun `webtoon and continuous vertical resolve to the webtoon viewer`() {
        assertEquals(ComposeReaderKind.Webtoon, resolveComposeReaderKind(ReadingMode.WEBTOON))
        assertEquals(ComposeReaderKind.Webtoon, resolveComposeReaderKind(ReadingMode.CONTINUOUS_VERTICAL))
    }

    @Test
    fun `unresolved default mode is rejected`() {
        assertThrows(IllegalStateException::class.java) {
            resolveComposeReaderKind(ReadingMode.DEFAULT)
        }
    }

    @Test
    fun `transitions follow the always-show setting except continuous vertical`() {
        assertTrue(
            composeReaderForceTransition(
                kind = ComposeReaderKind.Pager(isRtl = false, isVertical = false),
                mode = ReadingMode.LEFT_TO_RIGHT,
                alwaysShowChapterTransition = true,
            ),
        )
        assertFalse(
            composeReaderForceTransition(
                kind = ComposeReaderKind.Pager(isRtl = false, isVertical = false),
                mode = ReadingMode.LEFT_TO_RIGHT,
                alwaysShowChapterTransition = false,
            ),
        )
        assertTrue(
            composeReaderForceTransition(
                kind = ComposeReaderKind.Webtoon,
                mode = ReadingMode.WEBTOON,
                alwaysShowChapterTransition = true,
            ),
        )
        assertFalse(
            composeReaderForceTransition(
                kind = ComposeReaderKind.Webtoon,
                mode = ReadingMode.CONTINUOUS_VERTICAL,
                alwaysShowChapterTransition = true,
            ),
        )
        assertFalse(
            composeReaderForceTransition(
                kind = ComposeReaderKind.Webtoon,
                mode = ReadingMode.CONTINUOUS_VERTICAL,
                alwaysShowChapterTransition = false,
            ),
        )
    }
}
