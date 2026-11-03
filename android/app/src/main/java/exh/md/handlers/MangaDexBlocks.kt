package exh.md.handlers

import exh.md.MangaDexPreferences

data class MangaDexBlockChange(val id: String, val uploader: Boolean, val added: Boolean)

class MangaDexBlocks(private val preferences: MangaDexPreferences = MangaDexPreferences()) {
    fun isBlocked(id: String, uploader: Boolean): Boolean = preference(uploader).get().contains(id)

    fun block(id: String, name: String, uploader: Boolean): MangaDexBlockChange {
        val preference = preference(uploader)
        val current = preference.get()
        preference.set(current + id)
        preferences.blockName(id).set(name)
        return MangaDexBlockChange(id, uploader, id !in current)
    }

    fun undo(change: MangaDexBlockChange) {
        if (!change.added) return
        val preference = preference(change.uploader)
        preference.set(preference.get() - change.id)
    }

    private fun preference(uploader: Boolean) = if (uploader) preferences.blockedUploaders() else preferences.blockedGroups()
}
