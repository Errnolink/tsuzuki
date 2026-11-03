package dev.errnolink.tsuzuki.ui.details

import android.app.Application
import android.content.Context
import eu.kanade.tachiyomi.ui.manga.ChapterList
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

sealed interface ChapterRowEntry {
    val key: String

    data class Gap(val missing: ChapterList.MissingCount) : ChapterRowEntry {
        override val key: String get() = "gap-${missing.id}"
    }

    data class Group(
        val primary: ChapterList.Item,
        val alternates: List<ChapterList.Item>,
    ) : ChapterRowEntry {
        override val key: String get() = "chapter-${primary.id}"
    }

    data class Alternate(val item: ChapterList.Item, val groupKey: String) : ChapterRowEntry {
        override val key: String get() = "chapter-${item.id}"
    }
}

internal fun preferredScanlator(items: List<ChapterList>): String? =
    items.asSequence()
        .filterIsInstance<ChapterList.Item>()
        .filter { it.chapter.read && it.chapter.isRecognizedNumber && !it.chapter.scanlator.isNullOrBlank() }
        .maxByOrNull { it.chapter.chapterNumber }
        ?.chapter?.scanlator

data class ScanlatorGroup(val name: String, val chapters: Int)

internal fun scanlatorGroups(items: List<ChapterList>): List<ScanlatorGroup> {
    val counts = LinkedHashMap<String, Int>()
    val names = HashMap<String, String>()
    val latest = HashMap<String, Long>()
    for (entry in items) {
        val chapter = (entry as? ChapterList.Item)?.chapter ?: continue
        val name = chapter.scanlator?.trim()?.takeIf { it.isNotEmpty() } ?: continue
        val key = name.lowercase()
        names.putIfAbsent(key, name)
        counts[key] = (counts[key] ?: 0) + 1
        latest[key] = maxOf(latest[key] ?: 0L, chapter.dateUpload)
    }
    return counts.keys
        .sortedWith(compareByDescending<String> { counts.getValue(it) }.thenByDescending { latest.getValue(it) })
        .map { ScanlatorGroup(names.getValue(it), counts.getValue(it)) }
}

internal fun groupChapterRows(items: List<ChapterList>, chosen: String? = null): List<ChapterRowEntry> {
    val byNumber = HashMap<Double, MutableList<ChapterList.Item>>()
    for (entry in items) {
        val item = entry as? ChapterList.Item ?: continue
        val number = item.chapter.chapterNumber
        if (!item.chapter.isRecognizedNumber || !number.isFinite()) continue
        byNumber.getOrPut(number) { ArrayList(2) } += item
    }
    val groupable = byNumber.filterValues { members ->
        members.size > 1 && members.mapNotNullTo(HashSet()) { it.chapter.scanlator?.trim()?.lowercase() }.size == members.size
    }
    val ranking = listOfNotNull(chosen, preferredScanlator(items)) + scanlatorGroups(items).map { it.name }
    val out = ArrayList<ChapterRowEntry>(items.size)
    val emitted = HashSet<Double>()
    for (entry in items) {
        when (entry) {
            is ChapterList.MissingCount -> out += ChapterRowEntry.Gap(entry)
            is ChapterList.Item -> {
                val members = groupable[entry.chapter.chapterNumber]
                if (members == null) {
                    out += ChapterRowEntry.Group(entry, emptyList())
                } else if (emitted.add(entry.chapter.chapterNumber)) {
                    val primary = pickPrimary(members, ranking)
                    out += ChapterRowEntry.Group(primary, members.filter { it !== primary })
                }
            }
        }
    }
    return out
}

private fun pickPrimary(members: List<ChapterList.Item>, ranking: List<String>): ChapterList.Item =
    members.firstOrNull { !it.chapter.read && it.chapter.lastPageRead > 0 }
        ?: ranking.firstNotNullOfOrNull { name -> members.firstOrNull { it.chapter.scanlator?.trim().equals(name.trim(), ignoreCase = true) } }
        ?: members.maxBy { it.chapter.dateUpload }

object PreferredGroups {
    private val prefs by lazy {
        Injekt.get<Application>().getSharedPreferences("tsuzuki_preferred_group", Context.MODE_PRIVATE)
    }

    fun get(mangaId: Long): String? = prefs.getString(mangaId.toString(), null)

    fun set(mangaId: Long, name: String?) {
        prefs.edit().apply { if (name == null) remove(mangaId.toString()) else putString(mangaId.toString(), name) }.apply()
    }
}

internal fun List<ChapterRowEntry>.expanded(open: Set<String>): List<ChapterRowEntry> {
    if (open.isEmpty()) return this
    val out = ArrayList<ChapterRowEntry>(size + open.size * 2)
    for (entry in this) {
        out += entry
        if (entry is ChapterRowEntry.Group && entry.key in open) {
            entry.alternates.forEach { out += ChapterRowEntry.Alternate(it, entry.key) }
        }
    }
    return out
}
