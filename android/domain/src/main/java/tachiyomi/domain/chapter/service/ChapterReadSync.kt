package tachiyomi.domain.chapter.service

import tachiyomi.domain.chapter.model.ChapterUpdate

fun interface ChapterReadSync {
    fun onReadStatusChanged(chapters: List<ChapterUpdate>)
}
