package dev.errnolink.tsuzuki.ui.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReaderNoticeTest {
    @Test
    fun `a notice never rides in on the chrome's arrival`() {
        assertNull(noticeDelayMillis(null), "no notice while the chrome is away")
        val settle = noticeDelayMillis(0L)!!
        assertTrue(settle > 0L, "a notice right as the chrome appears waits for the settle")
        // the wait counts down one for one with the chrome's age, then the notice shows at once
        assertEquals(settle, noticeDelayMillis(200L)!! + 200L)
        assertEquals(0L, noticeDelayMillis(settle))
        assertEquals(0L, noticeDelayMillis(settle + 5_000L), "an old chrome shows the notice immediately")
    }
}
