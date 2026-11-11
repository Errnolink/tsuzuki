package dev.errnolink.tsuzuki.ui.settings

import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InstallRetryTest {

    @Test
    fun `Succeeds on the first attempt without waiting`() = runTest {
        var attempts = 0
        val result = retrying(listOf(2_000L, 6_000L), { it < 0 }) { attempts++; 7 }
        assertEquals(7, result)
        assertEquals(1, attempts)
        assertEquals(0, currentTime)
    }

    @Test
    fun `Retries after 2 s and 6 s and succeeds on the third attempt`() = runTest {
        var attempts = 0
        val result = retrying(listOf(2_000L, 6_000L), { it < 0 }) {
            attempts++
            if (attempts < 3) -1 else 5
        }
        assertEquals(5, result)
        assertEquals(3, attempts)
        assertEquals(8_000, currentTime)
    }

    @Test
    fun `Returns the failure after three attempts`() = runTest {
        var attempts = 0
        val result = retrying(listOf(2_000L, 6_000L), { it < 0 }) { attempts++; -1 }
        assertEquals(-1, result)
        assertEquals(3, attempts)
        assertEquals(8_000, currentTime)
    }
}
