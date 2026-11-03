package dev.errnolink.tsuzuki.ui.shell

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ShellSearch {
    private val mutableQuery = MutableStateFlow<String?>(null)
    val query: StateFlow<String?> = mutableQuery.asStateFlow()
    var onSubmit: ((String) -> Unit)? = null

    fun open(initial: String = mutableQuery.value.orEmpty()) { mutableQuery.value = initial }
    fun update(text: String) { if (mutableQuery.value != null) mutableQuery.value = text }
    fun submit(text: String) { onSubmit?.invoke(text) }
    fun close() { mutableQuery.value = null }
}
