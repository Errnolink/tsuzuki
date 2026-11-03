package dev.errnolink.tsuzuki.sync

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
import dev.errnolink.tsuzuki.sync.ExtensionSyncRules.Action
import dev.errnolink.tsuzuki.sync.ExtensionSyncRules.Change
import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.api.ExtensionApi
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.util.ExtensionLoader
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.LogPriority
import mihon.domain.extension.interactor.GetExtensionStores
import mihon.domain.extension.model.ExtensionStore
import okhttp3.OkHttpClient
import okhttp3.Request
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.kmk.KMR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

object ExtensionSync {
    private val app: Application by lazy { Injekt.get<Application>() }
    private val extensionManager: ExtensionManager by lazy { Injekt.get<ExtensionManager>() }
    private val getExtensionStores: GetExtensionStores by lazy { Injekt.get<GetExtensionStores>() }
    private val syncPreferences: SyncPreferences by lazy { Injekt.get<SyncPreferences>() }
    private val httpClient: OkHttpClient by lazy { Injekt.get<NetworkHelper>().client }
    private val api: ExtensionApi by lazy { ExtensionApi() }

    private val preferences: SharedPreferences by lazy {
        app.getSharedPreferences("extension_sync", Context.MODE_PRIVATE)
    }
    private val json = Json
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val storeLock = Any()

    @Serializable
    private data class StoreState(
        val wantedAt: Map<String, Long> = emptyMap(),
        val tombstones: Map<String, Long> = emptyMap(),
        val fleetWanted: Map<String, BackupWantedExtension> = emptyMap(),
    )

    fun isEnabled(): Boolean = syncPreferences.syncExtensions().get()

    fun launchOnAppStart() {
        if (!isEnabled()) return
        scope.launch {
            runCatching { reconcile(null) }
                .onFailure { logcat(LogPriority.WARN, it) { "App-start wanted-extension reconcile failed" } }
        }
    }

    private fun readStore(): StoreState =
        runCatching {
            preferences.getString("state", null)?.let { json.decodeFromString(StoreState.serializer(), it) }
        }.getOrNull() ?: StoreState()

    private fun writeStore(state: StoreState) {
        preferences.edit().putString("state", json.encodeToString(StoreState.serializer(), state)).apply()
    }

    suspend fun trustedRepos(): Map<String, String> =
        getExtensionStores.get().associate { it.indexUrl.lowercase() to it.signingKey }

    suspend fun buildSection(): BackupWantedExtensions? {
        val state = synchronized(storeLock) { readStore() }
        val stores = getExtensionStores.get()
        val installed = extensionManager.installedExtensionsFlow.value
        val installedPkgs = installed.map { it.pkgName }.toSet()
        val now = System.currentTimeMillis()

        val newWantedAt = state.wantedAt.toMutableMap()
        val wanted =
            installed
                .asSequence()
                .filterNot { ExtensionSyncRules.isBuiltIn(it.pkgName) }
                .mapNotNull { ext ->
                    val store = storeFor(ext, stores) ?: return@mapNotNull null
                    val pkg = ext.pkgName
                    val at = state.wantedAt[pkg] ?: now.also { newWantedAt[pkg] = it }
                    BackupWantedExtension(
                        pkgName = pkg,
                        repoIndexUrl = store.indexUrl,
                        signingFingerprint = store.signingKey,
                        versionCode = ext.versionCode,
                        updatedAt = at,
                        versionName = ext.versionName,
                    )
                }.associateBy { it.pkgName }

        val fleetOnly =
            state.fleetWanted.filterKeys {
                it !in installedPkgs && !ExtensionSyncRules.isBuiltIn(it)
            }
        val allWanted = (wanted + fleetOnly).values.toList()

        if (newWantedAt != state.wantedAt) {
            synchronized(storeLock) { writeStore(state.copy(wantedAt = newWantedAt)) }
        }

        if (allWanted.isEmpty() && state.tombstones.isEmpty()) return null
        return BackupWantedExtensions(
            wanted = allWanted,
            tombstones = state.tombstones.map { BackupExtensionTombstone(it.key, it.value) },
            syncUninstalls = syncPreferences.syncUninstalls().get(),
        )
    }

    private fun storeFor(extension: Extension.Installed, stores: List<ExtensionStore>): ExtensionStore? {
        extension.store?.let { return it }
        return stores.firstOrNull { it.signingKey.equals(extension.signatureHash, ignoreCase = true) }
    }

    fun recordInstall(pkgName: String) {
        if (ExtensionSyncRules.isBuiltIn(pkgName)) return
        synchronized(storeLock) {
            val state = readStore()
            writeStore(
                state.copy(
                    wantedAt = state.wantedAt + (pkgName to System.currentTimeMillis()),
                    tombstones = state.tombstones - pkgName,
                    fleetWanted = state.fleetWanted - pkgName,
                ),
            )
        }
    }

    fun recordUninstall(pkgName: String) {
        if (ExtensionSyncRules.isBuiltIn(pkgName)) return
        synchronized(storeLock) {
            val state = readStore()
            writeStore(
                state.copy(
                    wantedAt = state.wantedAt - pkgName,
                    tombstones = state.tombstones + (pkgName to System.currentTimeMillis()),
                    fleetWanted = state.fleetWanted - pkgName,
                ),
            )
        }
    }

    suspend fun reconcile(remote: BackupWantedExtensions?): List<Change> {
        if (!isEnabled()) return emptyList()
        return mutex.withLock {
            val local = buildSection() ?: BackupWantedExtensions()
            val resolved = ExtensionSyncRules.mergeSections(local, remote)

            val installed = extensionManager.installedExtensionsFlow.value.associateBy { it.pkgName }
            val installedForRules = installed.values.associate {
                it.pkgName to ExtensionSyncRules.InstalledExtension(it.pkgName, it.versionCode)
            }
            val trusted = trustedRepos()

            val needsCatalog = resolved.any { (pkg, state) ->
                state is ExtensionSyncRules.PkgResolution.Wanted &&
                    pkg !in installed &&
                    !ExtensionSyncRules.isBuiltIn(pkg)
            }
            val available =
                if (needsCatalog) {
                    runCatching { api.findExtensions() }.getOrElse {
                        logcat(LogPriority.WARN, it) { "Extension catalog refresh failed" }
                        extensionManager.availableExtensionsFlow.value
                    }
                } else {
                    extensionManager.availableExtensionsFlow.value
                }
            val catalog = available.associate {
                it.pkgName to ExtensionSyncRules.CatalogEntry(
                    storeIndexUrl = it.store.indexUrl,
                    hasDownloadUrl = it.apkUrl.isNotBlank(),
                )
            }

            val planned =
                ExtensionSyncRules.planChanges(
                    resolved,
                    installedForRules,
                    catalog,
                    trusted,
                    remote?.syncUninstalls ?: syncPreferences.syncUninstalls().get(),
                ).associateBy { it.pkgName }

            val changes = mutableListOf<Change>()
            val installCandidates = mutableListOf<Extension.Available>()

            resolved.forEach { (pkg, state) ->
                when (state) {
                    is ExtensionSyncRules.PkgResolution.Wanted -> {
                        if (!installed.containsKey(pkg) && !ExtensionSyncRules.isBuiltIn(pkg)) {
                            val plan = planned[pkg]
                            if (plan != null) {
                                changes.add(plan)
                            } else {
                                val candidate = available.firstOrNull {
                                    it.pkgName == pkg &&
                                        it.store.indexUrl.equals(state.entry.repoIndexUrl, ignoreCase = true)
                                }
                                if (candidate != null) {
                                    installCandidates.add(candidate)
                                } else {
                                    changes.add(Change(pkg, Action.SKIPPED, ExtensionSyncRules.REASON_NOT_IN_REPO))
                                }
                            }
                        }
                    }

                    is ExtensionSyncRules.PkgResolution.Tombstoned -> {
                        val ext = installed[pkg]
                        if (ext != null && !ExtensionSyncRules.isBuiltIn(pkg)) {
                            val plan = planned[pkg]
                            if (plan != null) {
                                changes.add(plan)
                            } else {
                                changes.add(
                                    runCatching { extensionManager.uninstallExtension(ext) }
                                        .fold(
                                            { Change(pkg, Action.UNINSTALLED) },
                                            { e ->
                                                logcat(LogPriority.WARN, e) { "Failed to uninstall tombstoned extension $pkg" }
                                                Change(pkg, Action.FAILED, e.message)
                                            },
                                        ),
                                )
                            }
                        }
                    }
                }
            }

            if (installCandidates.isNotEmpty()) {
                showInstallProgress(installCandidates.size, 0)
                installCandidates.forEachIndexed { index, availableExt ->
                    changes.add(installPrivately(availableExt))
                    showInstallProgress(installCandidates.size, index + 1)
                }
                app.cancelNotification(Notifications.ID_EXTENSION_SYNC)
            }

            updateStoreFromResolution(resolved, changes, installed.keys)

            logcat(LogPriority.INFO) {
                "Wanted-extension reconcile: " +
                    changes.joinToString { "${it.action} ${it.pkgName}${it.reason?.let { r -> " ($r)" }.orEmpty()}" }
                        .ifEmpty { "no changes" }
            }
            changes
        }
    }

    private fun updateStoreFromResolution(
        resolved: Map<String, ExtensionSyncRules.PkgResolution>,
        changes: List<Change>,
        installedBefore: Set<String>,
    ) {
        synchronized(storeLock) {
            val state = readStore()
            val wantedAt = state.wantedAt.toMutableMap()
            val tombstones = state.tombstones.toMutableMap()
            val fleetWanted = state.fleetWanted.toMutableMap()
            resolved.forEach { (pkg, resolution) ->
                when (resolution) {
                    is ExtensionSyncRules.PkgResolution.Wanted -> {
                        fleetWanted[pkg] = resolution.entry
                        tombstones.remove(pkg)
                        val wasInstalledHere = changes.any { it.pkgName == pkg && it.action == Action.INSTALLED }
                        val alreadyInstalled = pkg in installedBefore
                        if (wasInstalledHere) {
                            wantedAt[pkg] = resolution.entry.updatedAt
                        } else if (alreadyInstalled) {
                            wantedAt[pkg] = maxOf(wantedAt[pkg] ?: 0L, resolution.entry.updatedAt)
                        }
                    }

                    is ExtensionSyncRules.PkgResolution.Tombstoned -> {
                        tombstones[pkg] = maxOf(tombstones[pkg] ?: 0L, resolution.deletedAt)
                        fleetWanted.remove(pkg)
                        wantedAt.remove(pkg)
                    }
                }
            }
            writeStore(StoreState(wantedAt, tombstones, fleetWanted))
        }
    }

    private suspend fun installPrivately(available: Extension.Available): Change {
        val tmpFile = File(app.cacheDir, "ext_sync_${available.pkgName}.apk")
        return try {
            val request = Request.Builder().url(available.apkUrl).build()
            httpClient.newCall(request).await().use { response ->
                if (!response.isSuccessful) {
                    return Change(available.pkgName, Action.FAILED, "HTTP ${response.code}")
                }
                response.body.byteStream().use { input ->
                    tmpFile.outputStream().use { input.copyTo(it) }
                }
            }
            if (ExtensionLoader.installPrivateExtensionFile(app, tmpFile)) {
                Change(available.pkgName, Action.INSTALLED)
            } else {
                Change(available.pkgName, Action.FAILED, ExtensionSyncRules.REASON_INSTALL_FAILED)
            }
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "Failed to install wanted extension ${available.pkgName}" }
            Change(available.pkgName, Action.FAILED, e.message)
        } finally {
            tmpFile.delete()
        }
    }

    private fun showInstallProgress(total: Int, progress: Int) {
        val builder = app.notificationBuilder(Notifications.CHANNEL_EXT_SYNC) {
            setSmallIcon(R.drawable.ic_tsuzuki)
            setColor(ContextCompat.getColor(app, R.color.ic_launcher))
            setContentTitle(app.pluralStringResource(KMR.plurals.ext_sync_installing, total, total))
            setProgress(total, progress, false)
            setOngoing(true)
            setOnlyAlertOnce(true)
        }
        app.notify(Notifications.ID_EXTENSION_SYNC, builder.build())
    }
}
