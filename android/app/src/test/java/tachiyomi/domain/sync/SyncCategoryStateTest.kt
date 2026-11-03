package tachiyomi.domain.sync

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

@Execution(ExecutionMode.CONCURRENT)
class SyncCategoryStateTest {

    @Test
    fun `acknowledged tombstones stop pending so the idle skip can fire again`() {
        val state = SyncCategoryState(TestPreferenceStore())
        state.remember(7L)
        state.remember(9L)

        val sent = state.deleted()
        state.acknowledge(sent)

        state.deleted() shouldBe emptySet()
    }

    @Test
    fun `a delete made during the sync stays pending until the next acknowledgement`() {
        val state = SyncCategoryState(TestPreferenceStore())
        state.remember(7L)

        val sent = state.deleted()
        state.remember(11L)
        state.acknowledge(sent)

        state.deleted() shouldBe setOf("11")

        state.acknowledge(state.deleted())
        state.deleted() shouldBe emptySet()
    }

    private class TestPreferenceStore : PreferenceStore {
        private val values = HashMap<String, Any?>()

        private inner class Pref<T>(private val prefKey: String, private val default: T) : Preference<T> {
            override fun key(): String = prefKey
            @Suppress("UNCHECKED_CAST")
            override fun get(): T = values[prefKey] as? T ?: default
            override fun set(value: T) { values[prefKey] = value }
            override fun isSet(): Boolean = values.containsKey(prefKey)
            override fun delete() { values.remove(prefKey) }
            override fun defaultValue(): T = default
            override fun changes(): Flow<T> = MutableStateFlow(get())
            override fun stateIn(scope: CoroutineScope): StateFlow<T> = MutableStateFlow(get())
        }

        override fun getString(key: String, defaultValue: String): Preference<String> = Pref(key, defaultValue)
        override fun getLong(key: String, defaultValue: Long): Preference<Long> = Pref(key, defaultValue)
        override fun getInt(key: String, defaultValue: Int): Preference<Int> = Pref(key, defaultValue)
        override fun getFloat(key: String, defaultValue: Float): Preference<Float> = Pref(key, defaultValue)
        override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> = Pref(key, defaultValue)
        override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> = Pref(key, defaultValue)
        override fun <T> getObjectFromString(key: String, defaultValue: T, serializer: (T) -> String, deserializer: (String) -> T): Preference<T> = Pref(key, defaultValue)
        override fun <T> getObjectFromInt(key: String, defaultValue: T, serializer: (T) -> Int, deserializer: (Int) -> T): Preference<T> = Pref(key, defaultValue)
        override fun getAll(): Map<String, *> = values
    }
}
