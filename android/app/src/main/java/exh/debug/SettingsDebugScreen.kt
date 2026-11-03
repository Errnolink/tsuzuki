package exh.debug
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.errnolink.tsuzuki.designsystem.DetentSheet
import dev.errnolink.tsuzuki.designsystem.GroupedRow
import dev.errnolink.tsuzuki.designsystem.InsetGroupedList
import dev.errnolink.tsuzuki.designsystem.TsuzukiText
import dev.errnolink.tsuzuki.ui.settings.SettingsScaffold
import eu.kanade.core.preference.PreferenceMutableState
import eu.kanade.presentation.util.Screen
import exh.util.capitalize
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.presentation.core.screens.LoadingScreen
import java.util.Locale
import kotlin.reflect.KFunction
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredFunctions

class SettingsDebugScreen : Screen() {

    data class DebugToggle(val name: String, val pref: PreferenceMutableState<Boolean>, val default: Boolean)

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val functions by produceState<ImmutableList<Pair<KFunction<*>, String>>?>(initialValue = null) {
            value = withContext(Dispatchers.Default) {
                DebugFunctions::class.declaredFunctions.filter {
                    it.visibility == KVisibility.PUBLIC
                }.map {
                    it to it.name.replace("(.)(\\p{Upper})".toRegex(), "$1 $2")
                        .lowercase(Locale.getDefault())
                        .capitalize(Locale.getDefault())
                }.toImmutableList()
            }
        }
        val toggles by produceState(initialValue = persistentListOf()) {
            value = withContext(Dispatchers.Default) {
                DebugToggles.entries.map { DebugToggle(it.name, it.asPref(scope), it.default) }.toImmutableList()
            }
        }
        var running by remember { mutableStateOf(false) }
        var result by remember { mutableStateOf<Pair<String, String>?>(null) }

        // B34 legacy sweep: the Material Scaffold/AppBar, preference widgets and result
        // AlertDialog are replaced by the Tsuzuki settings shell, grouped rows and a
        // DetentSheet; the reflective function calls and toggles keep their behaviour.
        SettingsScaffold(
            title = "DEBUG MENU",
            navigateUp = navigator::pop,
        ) {
            val currentFunctions = functions
            if (currentFunctions == null) {
                item(key = "debug-loading") {
                    LoadingScreen(Modifier.fillParentMaxSize())
                }
            } else {
                item(key = "debug-functions") {
                    InsetGroupedList(title = "Functions") {
                        currentFunctions.forEach { (func, name) ->
                            GroupedRow(
                                title = name,
                                chevron = true,
                                onClick = {
                                    scope.launch(Dispatchers.Default) {
                                        val text = try {
                                            running = true
                                            "Function returned result:\n\n${func.call(DebugFunctions)}"
                                        } catch (e: Exception) {
                                            "Function threw exception:\n\n${Log.getStackTraceString(e)}"
                                        } finally {
                                            running = false
                                        }
                                        result = name to text
                                    }
                                },
                            )
                        }
                    }
                }
                item(key = "debug-toggles") {
                    InsetGroupedList(title = "Toggles") {
                        toggles.forEach { toggle ->
                            var state by toggle.pref
                            GroupedRow(
                                title = toggle.name.replace('_', ' ')
                                    .lowercase(Locale.getDefault())
                                    .capitalize(Locale.getDefault()),
                                subtitle = if (toggle.pref.value != toggle.default) {
                                    "MODIFIED"
                                } else {
                                    null
                                },
                                checked = state,
                                onCheckedChange = { state = it },
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = running && result == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(color = Color.Black.copy(alpha = 0.3F))
                    .pointerInput(running && result == null) {
                        forEachGesture {
                            awaitPointerEventScope {
                                waitForUpOrCancellation()?.consume()
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        val currentResult = result
        if (currentResult != null) {
            DetentSheet(
                true,
                currentResult.first,
                { result = null },
            ) {
                SelectionContainer(Modifier.verticalScroll(rememberScrollState())) {
                    TsuzukiText(
                        currentResult.second,
                        Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}
