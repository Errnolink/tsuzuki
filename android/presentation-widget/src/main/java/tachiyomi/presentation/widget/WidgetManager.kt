package tachiyomi.presentation.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.updates.interactor.GetUpdates

class WidgetManager(
    private val getUpdates: GetUpdates,
    private val securityPreferences: SecurityPreferences,
) {

    /**
     * The subscription re-runs the heavy updatesView query on every mangas/chapters
     * invalidation for the whole process lifetime. When no Updates widget is placed
     * there is nothing to refresh, so skip the query entirely (it also ran at every
     * cold start). Measure (load-speed, 2026-11-08, bench fixture 2,410 mangas /
     * 72k chapters): the getUpdatesByReadStatus execution alone is ~157 ms of
     * device DB time that competes with the library grid's first libraryView read.
     */
    fun Context.init(scope: CoroutineScope, widgetsPlaced: Boolean = updatesWidgetsPlaced(this)) {
        if (!widgetsPlaced) return
        combine(
            getUpdates.subscribe(read = false, after = BaseUpdatesGridGlanceWidget.DateLimit.toEpochMilli()),
            securityPreferences.useAuthenticator().changes(),
            transform = { a, b -> a to b },
        )
            .distinctUntilChanged { old, new ->
                old.second == new.second &&
                    old.first.map { it.chapterId }.toSet() == new.first.map { it.chapterId }.toSet()
            }
            .onEach {
                try {
                    UpdatesGridGlanceWidget().updateAll(this)
                    UpdatesGridCoverScreenGlanceWidget().updateAll(this)
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to update widget" }
                }
            }
            .flowOn(Dispatchers.Default)
            .launchIn(scope)
    }

    /** Whether any Updates grid widget is currently placed on a launcher. */
    fun updatesWidgetsPlaced(context: Context): Boolean {
        val appWidgetManager = AppWidgetManager.getInstance(context) ?: return false
        return listOf(
            ComponentName(context, UpdatesGridGlanceReceiver::class.java),
            ComponentName(context, UpdatesGridCoverScreenGlanceReceiver::class.java),
        ).any { component ->
            appWidgetManager.getAppWidgetIds(component)?.isNotEmpty() == true
        }
    }
}
