package tachiyomi.presentation.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import tachiyomi.domain.updates.interactor.GetUpdates
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WidgetManagerGateTest {

    private val getUpdates = mockk<GetUpdates>()
    private val securityPreferences = mockk<SecurityPreferences> {
        every { useAuthenticator() } returns mockk {
            every { changes() } returns flowOf(false)
        }
    }

    @Test
    fun `no updates widget placed skips the process-lifetime updatesView subscription`() {
        every { getUpdates.subscribe(any(), any()) } returns flowOf(emptyList())
        val manager = WidgetManager(getUpdates, securityPreferences)
        val context = mockk<Context>(relaxed = true)

        manager.run { with(context) { init(CoroutineScope(Job()), widgetsPlaced = false) } }

        verify { getUpdates wasNot Called }
    }

    @Test
    fun `a placed updates widget keeps the subscription`() = runBlocking {
        every { getUpdates.subscribe(any(), any()) } returns flowOf(emptyList())
        val manager = WidgetManager(getUpdates, securityPreferences)
        val context = mockk<Context>(relaxed = true)
        val scope = CoroutineScope(Job() + Dispatchers.Default)

        manager.run { with(context) { init(scope, widgetsPlaced = true) } }
        verify(timeout = 5_000) { getUpdates.subscribe(any(), any()) }

        scope.cancel()
        assertTrue(true)
    }

    @Test
    fun `updatesWidgetsPlaced is false when the launcher reports no widget ids`() {
        mockkStatic(AppWidgetManager::class)
        val appWidgetManager = mockk<AppWidgetManager> {
            every { getAppWidgetIds(any()) } returns IntArray(0)
        }
        every { AppWidgetManager.getInstance(any()) } returns appWidgetManager
        val manager = WidgetManager(getUpdates, securityPreferences)
        val context = mockk<Context>(relaxed = true)

        val placed = manager.updatesWidgetsPlaced(context)

        assertFalse(placed)
        unmockkStatic(AppWidgetManager::class)
    }
}
