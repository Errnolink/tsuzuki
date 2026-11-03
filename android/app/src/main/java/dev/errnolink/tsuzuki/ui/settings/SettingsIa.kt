package dev.errnolink.tsuzuki.ui.settings

import eu.kanade.presentation.more.settings.Preference
import kotlinx.collections.immutable.toImmutableList
import tachiyomi.core.common.preference.Preference as PreferenceData

internal fun List<Preference>.preferenceGroups(): List<Preference.PreferenceGroup> =
    filterIsInstance<Preference.PreferenceGroup>()

internal fun List<Preference>.looseItems(): List<Preference> =
    filter { it !is Preference.PreferenceGroup }

internal fun newGroup(
    title: String,
    items: List<Preference>,
): Preference.PreferenceGroup = Preference.PreferenceGroup(
    title = title,
    preferenceItems = @Suppress("UNCHECKED_CAST") items.filterIsInstance<Preference.PreferenceItem<*, *>>()
        .toImmutableList() as kotlinx.collections.immutable.ImmutableList<Preference.PreferenceItem<out Any, out Any>>,
)

internal fun Preference.PreferenceGroup.withTitle(newTitle: String): Preference.PreferenceGroup =
    Preference.PreferenceGroup(
        title = newTitle,
        enabled = enabled,
        preferenceItems = preferenceItems,
    )


/**
 * Stable identity for row selection: the backing preference store key.
 * Rows without a backing preference (nav rows, actions, custom rows) return null and must be
 * matched by a documented structural identity instead of by title.
 */
internal fun Preference.PreferenceItem<*, *>.storeKey(): String? = when (this) {
    is Preference.PreferenceItem.SwitchPreference -> preference.key()
    is Preference.PreferenceItem.ListPreference<*> -> preference.key()
    is Preference.PreferenceItem.MultiSelectListPreference -> preference.key()
    is Preference.PreferenceItem.EditTextPreference -> preference.key()
    else -> null
}?.removePrefix("__APP_STATE_")

internal fun List<Preference.PreferenceItem<out Any, out Any>>.byStoreKey(
    key: String,
): Preference.PreferenceItem<*, *>? = firstOrNull { it.storeKey() == key }

internal fun Preference.PreferenceItem<*, *>.withRetitle(newTitle: String): Preference.PreferenceItem<*, *> = when (this) {
    is Preference.PreferenceItem.SwitchPreference -> copy(title = newTitle)
    is Preference.PreferenceItem.TextPreference -> copy(title = newTitle)
    is Preference.PreferenceItem.ListPreference<*> -> copy(title = newTitle)
    is Preference.PreferenceItem.MultiSelectListPreference -> copy(title = newTitle)
    is Preference.PreferenceItem.EditTextPreference -> copy(title = newTitle)
    is Preference.PreferenceItem.BasicListPreference -> copy(title = newTitle)
    else -> this
}

/**
 * Retitle the row whose backing preference has [key]; leave every other row untouched.
 * Returns a plain list so callers can feed newGroup directly.
 */
internal fun List<Preference>.retitledWhere(key: String, title: String): List<Preference> {
    val out = ArrayList<Preference>(size)
    for (item in this) {
        val typed = item as? Preference.PreferenceItem<*, *>
        out += if (typed != null && typed.storeKey() == key) typed.withRetitle(title) else item
    }
    return out
}

internal fun Preference.PreferenceItem<*, *>.withSubtitle(newSubtitle: String): Preference.PreferenceItem<*, *> = when (this) {
    is Preference.PreferenceItem.SwitchPreference -> copy(subtitle = newSubtitle)
    is Preference.PreferenceItem.TextPreference -> copy(subtitle = newSubtitle)
    is Preference.PreferenceItem.ListPreference<*> -> copy(subtitle = newSubtitle)
    is Preference.PreferenceItem.MultiSelectListPreference -> copy(subtitle = newSubtitle)
    is Preference.PreferenceItem.EditTextPreference -> copy(subtitle = newSubtitle)
    is Preference.PreferenceItem.BasicListPreference -> copy(subtitle = newSubtitle)
    else -> this
}

/**
 * Set a one-line subtitle on the row whose backing preference has [key]; leave every other row
 * untouched. Only pass [subtitle] for list-type rows when hiding their current-value text is intended.
 */
internal fun List<Preference>.subtitledWhere(key: String, subtitle: String): List<Preference> {
    val out = ArrayList<Preference>(size)
    for (item in this) {
        val typed = item as? Preference.PreferenceItem<*, *>
        out += if (typed != null && typed.storeKey() == key) typed.withSubtitle(subtitle) else item
    }
    return out
}
