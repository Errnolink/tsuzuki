package dev.errnolink.tsuzuki.ui.shell

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.Locale

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StatusLayerTest {

    @BeforeAll
    fun pinLocale() {
        Locale.setDefault(Locale.US)
    }

    @Test
    fun `label is the activity name alone when progress is unknown or tiny`() {
        assertEquals("Syncing library", statusLabel("Syncing library", null))
        assertEquals("Syncing library", statusLabel("Syncing library", 0f))
        assertEquals("Syncing library", statusLabel("Syncing library", 0.004f))
    }

    @Test
    fun `label gains a percent once a real progress is known`() {
        assertEquals("Syncing library 37%", statusLabel("Syncing library", 0.37f))
        assertEquals("Syncing library 100%", statusLabel("Syncing library", 1f))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a two hundred millisecond run never shows the pill`() = runTest {
        val running = MutableStateFlow(false)
        val seen = mutableListOf<Boolean>()
        val collector = launch { running.statusPillVisibility().collect { seen.add(it) } }
        runCurrent()
        running.value = true
        advanceTimeBy(200)
        running.value = false
        advanceTimeBy(1000)
        runCurrent()
        assertTrue(seen.none { it })
        collector.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a four hundred millisecond run shows from three hundred to nine hundred`() = runTest {
        val running = MutableStateFlow(false)
        val seen = mutableListOf<Boolean>()
        val collector = launch { running.statusPillVisibility().collect { seen.add(it) } }
        runCurrent()
        running.value = true
        advanceTimeBy(299)
        runCurrent()
        assertFalse(seen.isNotEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(true), seen)
        advanceTimeBy(100)
        running.value = false
        runCurrent()
        assertEquals(listOf(true), seen)
        advanceTimeBy(499)
        runCurrent()
        assertEquals(listOf(true), seen)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(true, false), seen)
        collector.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a five second run hides immediately when it stops`() = runTest {
        val running = MutableStateFlow(false)
        val seen = mutableListOf<Boolean>()
        val collector = launch { running.statusPillVisibility().collect { seen.add(it) } }
        runCurrent()
        running.value = true
        advanceTimeBy(3000)
        runCurrent()
        assertEquals(listOf(true), seen)
        running.value = false
        runCurrent()
        assertEquals(listOf(true, false), seen)
        collector.cancel()
    }
}