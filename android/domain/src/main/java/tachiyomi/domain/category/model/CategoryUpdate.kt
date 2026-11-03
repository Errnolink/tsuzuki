package tachiyomi.domain.category.model

data class CategoryUpdate(
    val id: Long,
    val name: String? = null,
    val order: Long? = null,
    val flags: Long? = null,
    // KMK -->
    val hidden: Boolean? = null,
    // KMK <--
    val uid: Long? = null,
    val version: Long? = null,
    val lastModifiedAt: Long? = null,
    val isSyncing: Boolean = false,
)
