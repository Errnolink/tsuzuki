package dev.errnolink.tsuzuki.ui.library

import exh.source.MERGED_SOURCE_ID
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.manga.model.applyFilter

internal fun matchesMergedFilter(filter: TriState, sourceId: Long): Boolean =
    applyFilter(filter) { sourceId == MERGED_SOURCE_ID }

internal fun matchesMissingChaptersFilter(filter: TriState, missingChapters: Long): Boolean =
    applyFilter(filter) { missingChapters > 0 }

internal fun matchesUnavailableChaptersFilter(filter: TriState, unavailableChapters: Long): Boolean =
    applyFilter(filter) { unavailableChapters > 0 }
