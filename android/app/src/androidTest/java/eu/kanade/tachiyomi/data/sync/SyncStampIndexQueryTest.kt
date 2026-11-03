package eu.kanade.tachiyomi.data.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import tachiyomi.data.Database
import java.util.Locale

/**
 * The pre-merge local-work check on a live-size library: the watermark stamps must be
 * answered from indexes, not full-table scans, and the chapter-stamp probe that feeds
 * the b28 delta candidate filter must stay cheap per manga on a 166k-chapter store.
 *
 * The DB is created from the shipped [Database.Schema] (the same .sq + migration chain
 * the app installs), seeded with a 1,653-manga / 166,300-chapter fixture.
 */
@RunWith(AndroidJUnit4::class)
class SyncStampIndexQueryTest {

    private companion object {
        const val MANGA_COUNT = 1_653L
        const val CHAPTERS_PER_MANGA = 100L
        const val GATE_MS = 50L
        const val SINCE = 1_790_000_000L
        const val NEW_STAMP = 1_800_000_000L
    }

    private lateinit var driver: AndroidSqliteDriver

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        driver = AndroidSqliteDriver(Database.Schema, context, name = null)
        seed()
    }

    @After
    fun tearDown() {
        driver.close()
    }

    private fun seed() {
        val started = System.currentTimeMillis()
        val rowsPerStatement = 500L
        var mangaId = 1L
        while (mangaId <= MANGA_COUNT) {
            val values = StringBuilder()
            var i = 0L
            while (i < CHAPTERS_PER_MANGA) {
                if (i > 0 && i % rowsPerStatement == 0L) {
                    driver.execute(null, "INSERT INTO chapters(manga_id,url,name,read,bookmark,last_page_read,chapter_number,source_order,date_fetch,date_upload,last_modified_at) VALUES $values", 0)
                    values.setLength(0)
                }
                if (values.isNotEmpty()) values.append(',')
                // Only manga 1 carries post-watermark stamps; every other manga is
                // entirely pre-watermark so per-manga probe results are checkable.
                val stamp = if (mangaId == 1L && i < CHAPTERS_PER_MANGA / 20) NEW_STAMP else SINCE - 10
                values.append("($mangaId,'/ch/$mangaId/$i','Chapter $i',0,0,0,$i.0,$i,$stamp,$stamp,$stamp)")
                i++
            }
            if (values.isNotEmpty()) {
                driver.execute(null, "INSERT INTO chapters(manga_id,url,name,read,bookmark,last_page_read,chapter_number,source_order,date_fetch,date_upload,last_modified_at) VALUES $values", 0)
            }
            mangaId++
        }
        // A matching manga row so the manga watermark query returns true.
        driver.execute(
            null,
            "INSERT INTO mangas(source,url,title,status,favorite,initialized,viewer,chapter_flags,cover_last_modified,date_added,last_modified_at) VALUES (1,'/manga/1','T',0,1,0,0,0,0,0,$NEW_STAMP)",
            0,
        )
        android.util.Log.i("SyncStampIndexQueryTest", "seeded in ${System.currentTimeMillis() - started} ms")
    }

    private fun sql(sql: String): String {
        val cursor = driver.executeQuery(null, sql, { c ->
            val parts = ArrayList<String>(2)
            while (c.next().value) {
                parts.add(c.getString(0) ?: "")
            }
            app.cash.sqldelight.db.QueryResult.Value(parts.joinToString(" | "))
        }, 0)
        return cursor
    }

    private fun medianMs(query: String, runs: Int = 9): Double {
        // Warm the page cache first.
        repeat(2) { driver.executeQuery(null, query, { app.cash.sqldelight.db.QueryResult.Value(0) }, 0) }
        val times = LongArray(runs)
        for (r in 0 until runs) {
            val t0 = System.nanoTime()
            driver.executeQuery(null, query, { app.cash.sqldelight.db.QueryResult.Value(0) }, 0)
            times[r] = (System.nanoTime() - t0) / 1_000_000
        }
        times.sort()
        return times[runs / 2].toDouble()
    }

    @Test
    fun `watermark stamp probe is index-backed and under the gate`() {
        val query = "SELECT EXISTS(SELECT 1 FROM chapters WHERE last_modified_at >= $SINCE)"

        val plan = sql("EXPLAIN QUERY PLAN $query")
        android.util.Log.i("SyncStampIndexQueryTest", "indexed plan: $plan")
        org.junit.Assert.assertTrue(
            "stamp probe must use the stamp index, plan was: $plan",
            plan.contains("idx_chapters_last_modified_at"),
        )

        val median = medianMs(query)
        android.util.Log.i("SyncStampIndexQueryTest", "indexed stamp probe median: $median ms")
        org.junit.Assert.assertTrue("stamp probe took $median ms (gate $GATE_MS ms)", median < GATE_MS)
    }

    @Test
    fun `pre-fix full scan cost is recorded for comparison`() {
        driver.execute(null, "DROP INDEX idx_chapters_last_modified_at", 0)
        try {
            val query = "SELECT EXISTS(SELECT 1 FROM chapters WHERE last_modified_at >= $SINCE)"
            val plan = sql("EXPLAIN QUERY PLAN $query")
            android.util.Log.i("SyncStampIndexQueryTest", "scan plan: $plan")
            org.junit.Assert.assertTrue(plan.contains("SCAN chapters"))
            android.util.Log.i(
                "SyncStampIndexQueryTest",
                String.format(Locale.US, "unindexed scan median: %.3f ms", medianMs(query)),
            )
        } finally {
            driver.execute(null, "CREATE INDEX idx_chapters_last_modified_at ON chapters(last_modified_at)", 0)
        }
    }

    @Test
    fun `per-manga chapter stamp probes stay cheap across the whole library`() {
        val query =
            "SELECT EXISTS(SELECT 1 FROM chapters WHERE manga_id = $MANGA_COUNT AND last_modified_at >= $SINCE)"
        val plan = sql("EXPLAIN QUERY PLAN $query")
        android.util.Log.i("SyncStampIndexQueryTest", "per-manga plan: $plan")
        org.junit.Assert.assertTrue(
            "per-manga probe must use the manga_id index, plan was: $plan",
            plan.contains("chapters_manga_id_index"),
        )

        val totalMs = measureTotal {
            var mangaId = 1L
            while (mangaId <= MANGA_COUNT) {
                driver.executeQuery(
                    null,
                    "SELECT EXISTS(SELECT 1 FROM chapters WHERE manga_id = $mangaId AND last_modified_at >= $SINCE)",
                    { app.cash.sqldelight.db.QueryResult.Value(0) },
                    0,
                )
                mangaId++
            }
        }
        android.util.Log.i(
            "SyncStampIndexQueryTest",
            String.format(Locale.US, "delta candidate sweep over %d manga: %.1f ms total", MANGA_COUNT, totalMs),
        )
        org.junit.Assert.assertTrue(
            "candidate sweep over the whole library took $totalMs ms",
            totalMs < 2_000.0,
        )
    }

    @Test
    fun `stamped and unstamped probe results are correct`() {
        sql("SELECT EXISTS(SELECT 1 FROM chapters WHERE last_modified_at >= $NEW_STAMP)").let {
            org.junit.Assert.assertEquals("1", it)
        }
        sql("SELECT EXISTS(SELECT 1 FROM chapters WHERE last_modified_at >= ${NEW_STAMP + 1})").let {
            org.junit.Assert.assertEquals("0", it)
        }
        sql("SELECT EXISTS(SELECT 1 FROM chapters WHERE manga_id = 1 AND last_modified_at >= $NEW_STAMP)").let {
            org.junit.Assert.assertEquals("1", it)
        }
        sql("SELECT EXISTS(SELECT 1 FROM chapters WHERE manga_id = 2 AND last_modified_at >= $NEW_STAMP)").let {
            org.junit.Assert.assertEquals("0", it)
        }
    }

    private inline fun measureTotal(block: () -> Unit): Double {
        val t0 = System.nanoTime()
        block()
        return (System.nanoTime() - t0) / 1_000_000.0
    }
}
