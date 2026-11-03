package eu.kanade.tachiyomi.data.sync

import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSavedSearch
import eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue
import dev.errnolink.tsuzuki.sync.BackupWantedExtension
import dev.errnolink.tsuzuki.sync.BackupWantedExtensions
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.manga.model.Manga

class SyncDeltaTest {

    private fun manga(
        url: String,
        lastModifiedAt: Long,
        chapters: List<BackupChapter> = emptyList(),
    ) = BackupManga(source = 1, url = url, title = url, lastModifiedAt = lastModifiedAt, chapters = chapters)

    private fun chapter(url: String, lastModifiedAt: Long) =
        BackupChapter(url = url, name = url, lastModifiedAt = lastModifiedAt)

    @Test
    fun `selects manga modified since the boundary`() {
        val delta = selectSyncDelta(
            Backup(
                backupManga = listOf(
                    manga("/new", lastModifiedAt = 200),
                    manga("/old", lastModifiedAt = 99),
                ),
            ),
            since = 100,
        )
        delta.backupManga.map { it.url } shouldContainExactly listOf("/new")
    }

    @Test
    fun `boundary equality is retained`() {
        val delta = selectSyncDelta(
            Backup(backupManga = listOf(manga("/edge", lastModifiedAt = 100))),
            since = 100,
        )
        delta.backupManga.map { it.url } shouldContainExactly listOf("/edge")
    }

    @Test
    fun `includes manga with only changed chapters and restricts chapter list`() {
        val delta = selectSyncDelta(
            Backup(
                backupManga = listOf(
                    manga(
                        "/child",
                        lastModifiedAt = 10,
                        chapters = listOf(chapter("/c1", lastModifiedAt = 50), chapter("/c2", lastModifiedAt = 150)),
                    ),
                ),
            ),
            since = 100,
        )
        val selected = delta.backupManga.single()
        selected.url shouldBe "/child"
        selected.chapters.map { it.url } shouldContainExactly listOf("/c2")
        selected.chapters.single().lastModifiedAt shouldBe 150
    }

    @Test
    fun `manga-only change sends no chapter updates`() {
        val delta = selectSyncDelta(
            Backup(
                backupManga = listOf(
                    manga("/parent", lastModifiedAt = 300, chapters = listOf(chapter("/c1", lastModifiedAt = 1))),
                ),
            ),
            since = 100,
        )
        delta.backupManga.single().url shouldBe "/parent"
        delta.backupManga.single().chapters.shouldBeEmpty()
    }

    @Test
    fun `chapter inserted with the legacy zero stamp never rides a delta`() {
        // Refresh-inserted chapters used to be written with last_modified_at = 0,
        // so no watermark could ever select them and they only reached the hub
        // through the 24 h full sync.
        val delta = selectSyncDelta(
            Backup(
                backupManga = listOf(
                    manga("/refreshed", lastModifiedAt = 150, chapters = listOf(chapter("/new-ch", lastModifiedAt = 0))),
                ),
            ),
            since = 100,
        )
        delta.backupManga.single().url shouldBe "/refreshed"
        delta.backupManga.single().chapters.shouldBeEmpty()
    }

    @Test
    fun `chapter stamped at insert rides the delta with its parent`() {
        // The insert now stamps last_modified_at (epoch seconds, matching the
        // update trigger), so a refresh-added chapter is selected like any other
        // change made after the last push.
        val delta = selectSyncDelta(
            Backup(
                backupManga = listOf(
                    manga("/refreshed", lastModifiedAt = 0, chapters = listOf(chapter("/new-ch", lastModifiedAt = 150))),
                ),
            ),
            since = 100,
        )
        val selected = delta.backupManga.single()
        selected.url shouldBe "/refreshed"
        selected.chapters.map { it.url } shouldContainExactly listOf("/new-ch")
        selected.chapters.single().lastModifiedAt shouldBe 150
    }

    @Test
    fun `empty delta keeps categories and sources`() {
        val backup = Backup(
            backupManga = listOf(manga("/old", lastModifiedAt = 1)),
            backupCategories = listOf(BackupCategory(name = "Reading")),
        )
        val delta = selectSyncDelta(backup, since = 100)
        delta.backupManga.shouldBeEmpty()
        delta.backupCategories.map { it.name } shouldContainExactly listOf("Reading")
    }

    @Test
    fun `sections are included on first sync and only when digests change`() {
        val backup = Backup(
            backupPreferences = listOf(BackupPreference("key", IntPreferenceValue(1))),
            backupSavedSearches = listOf(BackupSavedSearch(name = "search")),
        )

        val first = selectSyncSections(backup, previous = emptySet(), full = true)
        first.backup.backupPreferences shouldContainExactly backup.backupPreferences
        first.backup.backupSavedSearches shouldContainExactly backup.backupSavedSearches

        val unchanged = selectSyncSections(backup, previous = first.digests, full = false)
        unchanged.backup.backupPreferences.shouldBeEmpty()
        unchanged.backup.backupSavedSearches.shouldBeEmpty()
        unchanged.digests shouldBe first.digests

        val changed = selectSyncSections(
            backup.copy(backupPreferences = listOf(BackupPreference("key", IntPreferenceValue(2)))),
            previous = first.digests,
            full = false,
        )
        changed.backup.backupPreferences shouldContainExactly listOf(BackupPreference("key", IntPreferenceValue(2)))
        changed.backup.backupSavedSearches.shouldBeEmpty()
        changed.digests.size shouldBe first.digests.size
    }

    @Test
    fun `category changes ride the section digests`() {
        val backup = Backup(backupCategories = listOf(BackupCategory(name = "Reading", uid = 3)))
        val first = selectSyncSections(backup, previous = emptySet(), full = false)

        val renamed = selectSyncSections(
            backup.copy(backupCategories = listOf(BackupCategory(name = "Renamed", uid = 3))),
            previous = first.digests,
            full = false,
        )
        renamed.digests.first { it.startsWith("categories:") } shouldNotBe
            first.digests.first { it.startsWith("categories:") }
    }

    @Test
    fun `wanted extension changes ride the section digests`() {
        val backup = Backup()
        val first = selectSyncSections(backup, previous = emptySet(), full = false)

        val withExtension = selectSyncSections(
            backup.copy(
                wantedExtensions = BackupWantedExtensions(
                    wanted = listOf(BackupWantedExtension(pkgName = "eu.kanade.tachiyomi.extension.en.sample")),
                ),
            ),
            previous = first.digests,
            full = false,
        )
        withExtension.digests.first { it.startsWith("extensions:") } shouldNotBe
            first.digests.first { it.startsWith("extensions:") }
    }

    @Test
    fun `idle sync skips only when hub matches cursor and nothing is pending`() {
        val idle = SyncWorkState(
            fullDue = false,
            tombstonesPending = false,
            localStamps = false,
            digestsChanged = false,
        )
        syncIsSkippable(hubVersion = 7, cursor = 7, work = idle) shouldBe true

        // A hub write between polls must still be pulled.
        syncIsSkippable(hubVersion = 8, cursor = 7, work = idle) shouldBe false
        // Any local work must still push.
        syncIsSkippable(hubVersion = 7, cursor = 7, work = idle.copy(fullDue = true)) shouldBe false
        syncIsSkippable(hubVersion = 7, cursor = 7, work = idle.copy(tombstonesPending = true)) shouldBe false
        syncIsSkippable(hubVersion = 7, cursor = 7, work = idle.copy(localStamps = true)) shouldBe false
        syncIsSkippable(hubVersion = 7, cursor = 7, work = idle.copy(digestsChanged = true)) shouldBe false
    }

    @Test
    fun `metadata-only restamp is skipped by the acked guard`() {
        // A global refresh stamps last_modified_at on every manga without moving any
        // version counter; the manga rode its last full sync, so nothing is new.
        val ack = mapOf("1|/stale" to PushedManga(version = 4, maxChapterVersion = 2, chapterCount = 1))
        val delta = selectSyncDelta(
            Backup(
                backupManga = listOf(
                    manga(
                        "/stale",
                        lastModifiedAt = 500,
                        chapters = listOf(
                            BackupChapter(url = "/c1", name = "/c1", version = 2, lastModifiedAt = 500),
                        ),
                    ).copy(version = 4),
                ),
            ),
            since = 100,
            pushed = ack,
        )
        delta.backupManga.shouldBeEmpty()
    }

    @Test
    fun `progress change rides the delta immediately`() {
        val ack = mapOf("1|/read" to PushedManga(version = 4, maxChapterVersion = 2, chapterCount = 2))
        val delta = selectSyncDelta(
            Backup(
                backupManga = listOf(
                    manga(
                        "/read",
                        lastModifiedAt = 500,
                        chapters = listOf(
                            BackupChapter(url = "/c1", name = "/c1", version = 2, lastModifiedAt = 10),
                            // reading a chapter bumps the chapter version past the ack
                            BackupChapter(url = "/c2", name = "/c2", version = 5, lastModifiedAt = 500),
                        ),
                    ).copy(version = 5),
                ),
            ),
            since = 100,
            pushed = ack,
        )
        val selected = delta.backupManga.single()
        selected.chapters.map { it.url } shouldContainExactly listOf("/c2")
    }

    @Test
    fun `favourite or description change rides the delta`() {
        val ack = mapOf("1|/fav" to PushedManga(version = 4, maxChapterVersion = 9, chapterCount = 3))
        val delta = selectSyncDelta(
            Backup(
                backupManga = listOf(
                    manga("/fav", lastModifiedAt = 500).copy(
                        version = 5,
                        chapters = listOf(BackupChapter(url = "/c1", name = "/c1", version = 1, lastModifiedAt = 10)),
                    ),
                ),
            ),
            since = 100,
            pushed = ack,
        )
        delta.backupManga.single().url shouldBe "/fav"
        delta.backupManga.single().chapters.shouldBeEmpty()
    }

    @Test
    fun `new chapters ride the delta via the count guard`() {
        val ack = mapOf("1|/refreshed" to PushedManga(version = 4, maxChapterVersion = 2, chapterCount = 1))
        val delta = selectSyncDelta(
            Backup(
                backupManga = listOf(
                    manga(
                        "/refreshed",
                        lastModifiedAt = 10,
                        chapters = listOf(
                            BackupChapter(url = "/c1", name = "/c1", version = 2, lastModifiedAt = 10),
                            // refresh-added chapter: insert-stamped, version still 0
                            BackupChapter(url = "/new", name = "/new", version = 0, lastModifiedAt = 500),
                        ),
                    ).copy(version = 4),
                ),
            ),
            since = 100,
            pushed = ack,
        )
        val selected = delta.backupManga.single()
        selected.chapters.map { it.url } shouldContainExactly listOf("/new")
    }

    @Test
    fun `manga without acked state falls back to the watermark rule`() {
        val delta = selectSyncDelta(
            Backup(
                backupManga = listOf(
                    manga("/known", lastModifiedAt = 500),
                    manga("/unknown", lastModifiedAt = 500),
                ),
            ),
            since = 100,
            pushed = mapOf("1|/known" to PushedManga(version = 500, maxChapterVersion = 0, chapterCount = 0)),
        )
        // /known's version matches the ack (metadata-only), /unknown has no ack
        delta.backupManga.map { it.url } shouldContainExactly listOf("/unknown")
    }

    @Test
    fun `pushed state roundtrips through the compact encoding`() {
        val states = mapOf(
            "2499283573021220255|/manga/abc" to PushedManga(version = 12, maxChapterVersion = 7, chapterCount = 1694),
            "1|/x" to PushedManga(version = 0, maxChapterVersion = 0, chapterCount = 0),
        )
        decodePushedManga(encodePushedManga(states)) shouldBe states
        decodePushedManga("") shouldBe emptyMap()
        decodePushedManga("garbage\u001ewith\u001ftoo\u001efew") shouldBe emptyMap()
    }

    @Test
    fun `chapter delta header only fires when the hub advertised it`() {
        chapterDeltaCapEnabled("chapter-delta") shouldBe true
        chapterDeltaCapEnabled("chapter-delta, future-cap") shouldBe true
        chapterDeltaCapEnabled("") shouldBe false
        chapterDeltaCapEnabled("other-cap") shouldBe false
    }

    @Test
    fun `a manga stamped after the watermark rides the delta`() {
        val manga = Manga.create().copy(lastModifiedAt = 200)
        isDeltaCandidate(manga, since = 100, hasChapterStamp = false) shouldBe true
    }

    @Test
    fun `an untouched manga with a stamped chapter rides the delta`() {
        // Reads and progress update chapter rows only; the manga row stays stale.
        val manga = Manga.create().copy(lastModifiedAt = 50)
        isDeltaCandidate(manga, since = 100, hasChapterStamp = true) shouldBe true
    }

    @Test
    fun `an untouched manga without any stamp stays out of the delta build`() {
        val manga = Manga.create().copy(lastModifiedAt = 50)
        isDeltaCandidate(manga, since = 100, hasChapterStamp = false) shouldBe false
    }
}
