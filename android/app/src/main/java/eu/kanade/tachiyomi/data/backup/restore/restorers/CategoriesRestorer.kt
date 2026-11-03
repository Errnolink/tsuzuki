package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.category.CategoryMapper
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.service.LibraryPreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class CategoriesRestorer(
    private val handler: DatabaseHandler = Injekt.get(),
    private val getCategories: GetCategories = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val downloadPreferences: DownloadPreferences = Injekt.get(),
) {
    suspend operator fun invoke(backupCategories: List<BackupCategory>) {
        val dbCategories = getCategories.await()
        var nextOrder = dbCategories.maxOfOrNull { it.order }?.plus(1) ?: 0
        handler.await(true) {
            backupCategories.sortedBy { it.order }.forEach { incoming ->
                if (dbCategories.any { it.name == incoming.name }) return@forEach
                categoriesQueries.insertSync(
                    name = incoming.name,
                    order = nextOrder++,
                    flags = incoming.flags,
                    hidden = if (incoming.hidden) 1L else 0L,
                    uid = incoming.uid,
                    version = incoming.version,
                    lastModifiedAt = incoming.lastModifiedAt,
                )
            }
        }
        updateDisplaySettings()
    }

    suspend fun applySync(incoming: List<BackupCategory>, snapshot: List<BackupCategory>) {
        val deletedIds = mutableListOf<Long>()
        handler.await(true) {
            val local = categoriesQueries.getCategories(CategoryMapper::mapCategory).executeAsList()
                .filterNot { it.isSystemCategory }
            val retained = mutableSetOf<Long>()
            incoming.forEach { remote ->
                val existing = local.firstOrNull { remote.uid != 0L && it.uid == remote.uid }
                    ?: local.firstOrNull { it.name == remote.name }
                if (existing == null) {
                    categoriesQueries.insertSync(
                        remote.name, remote.order, remote.flags, if (remote.hidden) 1L else 0L,
                        remote.uid, remote.version, remote.lastModifiedAt,
                    )
                } else {
                    retained += existing.id
                    if (changedSinceSnapshot(existing, snapshot)) return@forEach
                    categoriesQueries.adopt(
                        name = remote.name,
                        order = remote.order,
                        flags = remote.flags,
                        hidden = if (remote.hidden) 1L else 0L,
                        uid = remote.uid.takeIf { it != 0L } ?: existing.uid,
                        version = remote.version,
                        lastModifiedAt = remote.lastModifiedAt,
                        id = existing.id,
                    )
                }
            }
            local.filter { it.id !in retained && !changedSinceSnapshot(it, snapshot) }.forEach {
                categoriesQueries.delete(it.id)
                deletedIds += it.id
            }
            categoriesQueries.resetIsSyncing()
        }
        deletedIds.forEach(::forgetCategoryPreferences)
        updateDisplaySettings()
    }

    private fun changedSinceSnapshot(local: Category, snapshot: List<BackupCategory>): Boolean {
        val before = snapshot.firstOrNull { it.uid == local.uid && it.uid != 0L }
            ?: snapshot.firstOrNull { it.name == local.name }
            ?: return true
        return local.version != before.version || local.lastModifiedAt != before.lastModifiedAt ||
            local.name != before.name || local.order != before.order || local.flags != before.flags ||
            local.hidden != before.hidden
    }

    private fun forgetCategoryPreferences(id: Long) {
        if (libraryPreferences.defaultCategory().get() == id.toInt()) libraryPreferences.defaultCategory().delete()
        listOf(
            libraryPreferences.updateCategories(),
            libraryPreferences.updateCategoriesExclude(),
            libraryPreferences.filterCategoriesInclude(),
            libraryPreferences.filterCategoriesExclude(),
            downloadPreferences.removeExcludeCategories(),
            downloadPreferences.downloadNewChapterCategories(),
            downloadPreferences.downloadNewChapterCategoriesExclude(),
        ).forEach { it.set(it.get() - id.toString()) }
    }

    private suspend fun updateDisplaySettings() {
        libraryPreferences.categorizedDisplaySettings().set(getCategories.await().distinctBy { it.flags }.size > 1)
    }
}
