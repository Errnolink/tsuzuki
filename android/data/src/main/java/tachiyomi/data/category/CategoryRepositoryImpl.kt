package tachiyomi.data.category

import kotlinx.coroutines.flow.Flow
import tachiyomi.data.Database
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryUpdate
import tachiyomi.domain.category.repository.CategoryRepository

class CategoryRepositoryImpl(
    private val handler: DatabaseHandler,
) : CategoryRepository {

    override suspend fun get(id: Long): Category? {
        return handler.awaitOneOrNull { categoriesQueries.getCategory(id, CategoryMapper::mapCategory) }
    }

    override suspend fun getAll(): List<Category> {
        return handler.awaitList { categoriesQueries.getCategories(CategoryMapper::mapCategory) }
    }

    override fun getAllAsFlow(): Flow<List<Category>> {
        return handler.subscribeToList { categoriesQueries.getCategories(CategoryMapper::mapCategory) }
    }

    override suspend fun getCategoriesByMangaId(mangaId: Long): List<Category> {
        return handler.awaitList {
            categoriesQueries.getCategoriesByMangaId(mangaId, CategoryMapper::mapCategory)
        }
    }

    override fun getCategoriesByMangaIdAsFlow(mangaId: Long): Flow<List<Category>> {
        return handler.subscribeToList {
            categoriesQueries.getCategoriesByMangaId(mangaId, CategoryMapper::mapCategory)
        }
    }

    // SY -->
    override suspend fun insert(category: Category): Long {
        return handler.awaitOneExecutable(true) {
            categoriesQueries.insertSync(
                name = category.name,
                order = category.order,
                flags = category.flags,
                uid = category.uid,
                version = category.version,
                lastModifiedAt = category.lastModifiedAt,
                // KMK -->
                hidden = if (category.hidden) 1L else 0L,
                // KMK <--
            )
            categoriesQueries.selectLastInsertedRowId()
        }
    }
    // SY <--

    override suspend fun updatePartial(update: CategoryUpdate) {
        handler.await(inTransaction = true) {
            updatePartialBlocking(update)
        }
    }

    override suspend fun updatePartial(updates: List<CategoryUpdate>) {
        handler.await(inTransaction = true) {
            for (update in updates) {
                updatePartialBlocking(update)
            }
        }
    }

    private fun Database.updatePartialBlocking(update: CategoryUpdate) {
        if (update.isSyncing) {
            val current = categoriesQueries.getCategory(update.id, CategoryMapper::mapCategory).executeAsOneOrNull()
                ?: return
            categoriesQueries.adopt(
                id = update.id,
                name = update.name ?: current.name,
                order = update.order ?: current.order,
                flags = update.flags ?: current.flags,
                hidden = if (update.hidden ?: current.hidden) 1L else 0L,
                uid = update.uid ?: current.uid,
                version = update.version ?: current.version,
                lastModifiedAt = update.lastModifiedAt ?: current.lastModifiedAt,
            )
            categoriesQueries.resetIsSyncing()
            return
        }
        categoriesQueries.update(
            name = update.name,
            order = update.order,
            flags = update.flags,
            // KMK -->
            hidden = update.hidden?.let { if (it) 1L else 0L },
            // KMK <--
            categoryId = update.id,
        )
    }

    override suspend fun updateAllFlags(flags: Long?) {
        handler.await {
            categoriesQueries.updateAllFlags(flags)
        }
    }

    override suspend fun delete(categoryId: Long) {
        handler.await {
            categoriesQueries.delete(
                categoryId = categoryId,
            )
        }
    }
}
