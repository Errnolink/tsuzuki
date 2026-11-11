package eu.kanade.tachiyomi.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn

open class BannerProgressStatus {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isRunning = MutableStateFlow(false)
    val isActive = _isRunning.asStateFlow()

    val isRunning = _isRunning
        .debounce(1000L) // Don't notify if it finishes quickly enough
        .stateIn(scope, SharingStarted.WhileSubscribed(), false)

    // KMK -->
    // Nullable progress: null means unknown. Start and stop reset it so a new
    // run never inherits the previous run's last value.
    private val _progress = MutableStateFlow<Float?>(null)
    val progress: StateFlow<Float?> = _progress.asStateFlow()

    suspend fun start() {
        _progress.value = null
        _isRunning.emit(true)
    }

    suspend fun stop() {
        _progress.value = null
        _isRunning.emit(false)
    }

    suspend fun updateProgress(progress: Float) {
        _progress.emit(if (progress.isFinite()) progress else null)
    }
    // KMK <--
}

class LibraryUpdateStatus : BannerProgressStatus()
class SyncStatus : BannerProgressStatus()
class BackupRestoreStatus : BannerProgressStatus()
