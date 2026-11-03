package exh.md

import exh.md.utils.MangaDexCoverQuality
import exh.md.utils.MangaDexCoverVariant
import exh.md.utils.MdCoverVariantUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

import eu.kanade.tachiyomi.data.coil.InFlightRequestCoalescer
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.runTest
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.assertThrows
class MangaDexCoverVariantTest {
    private val dexId = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
    private val fileName = "cover_art_1.png"
    private val baseUrl = "https://uploads.mangadex.org/covers/$dexId/$fileName"

    @Test
    fun `normalizeCoverUrl strips variant suffixes`() {
        assertEquals(baseUrl, MdCoverVariantUtil.normalizeCoverUrl(baseUrl))
        assertEquals(baseUrl, MdCoverVariantUtil.normalizeCoverUrl("$baseUrl.512.jpg"))
        assertEquals(baseUrl, MdCoverVariantUtil.normalizeCoverUrl("$baseUrl.256.jpg"))
    }

    @Test
    fun `cleanFileName strips variant suffixes`() {
        assertEquals("test.jpg", MdCoverVariantUtil.cleanFileName("test.jpg"))
        assertEquals("test.jpg", MdCoverVariantUtil.cleanFileName("test.jpg.512.jpg"))
        assertEquals("test.jpg", MdCoverVariantUtil.cleanFileName("test.jpg.256.jpg"))
    }

    @Test
    fun `automatic quality selects variant based on displayed width`() {
        assertEquals(
            MangaDexCoverVariant.LOW,
            MdCoverVariantUtil.selectVariant(MangaDexCoverQuality.AUTOMATIC, 120, 180),
        )
        assertEquals(
            MangaDexCoverVariant.LOW,
            MdCoverVariantUtil.selectVariant(MangaDexCoverQuality.AUTOMATIC, 200, 300),
        )
        assertEquals(
            MangaDexCoverVariant.LOW,
            MdCoverVariantUtil.selectVariant(MangaDexCoverQuality.AUTOMATIC, 256, 256),
        )
        assertEquals(
            MangaDexCoverVariant.MEDIUM,
            MdCoverVariantUtil.selectVariant(MangaDexCoverQuality.AUTOMATIC, 362, 543),
        )
        assertEquals(
            MangaDexCoverVariant.MEDIUM,
            MdCoverVariantUtil.selectVariant(MangaDexCoverQuality.AUTOMATIC, 472, 708),
        )
        assertEquals(
            MangaDexCoverVariant.MEDIUM,
            MdCoverVariantUtil.selectVariant(MangaDexCoverQuality.AUTOMATIC, 512, 512),
        )
        assertEquals(
            MangaDexCoverVariant.ORIGINAL,
            MdCoverVariantUtil.selectVariant(MangaDexCoverQuality.AUTOMATIC, 600, 900),
        )
    }

    @Test
    fun `automatic quality falls back to width from height when width is unknown`() {
        assertEquals(
            MangaDexCoverVariant.LOW,
            MdCoverVariantUtil.selectVariant(MangaDexCoverQuality.AUTOMATIC, 0, 300),
        )
        assertEquals(
            MangaDexCoverVariant.MEDIUM,
            MdCoverVariantUtil.selectVariant(MangaDexCoverQuality.AUTOMATIC, 0, 540),
        )
        assertEquals(
            MangaDexCoverVariant.ORIGINAL,
            MdCoverVariantUtil.selectVariant(MangaDexCoverQuality.AUTOMATIC, 0, 780),
        )
        assertEquals(
            MangaDexCoverVariant.LOW,
            MdCoverVariantUtil.selectVariant(MangaDexCoverQuality.AUTOMATIC, 0, 0),
        )
    }

    @Test
    fun `hd everywhere quality always selects original`() {
        assertEquals(
            MangaDexCoverVariant.ORIGINAL,
            MdCoverVariantUtil.selectVariant(MangaDexCoverQuality.HD_EVERYWHERE, 48, 72),
        )
        assertEquals(
            MangaDexCoverVariant.ORIGINAL,
            MdCoverVariantUtil.selectVariant(MangaDexCoverQuality.HD_EVERYWHERE, 300, 450),
        )
        assertEquals(
            MangaDexCoverVariant.ORIGINAL,
            MdCoverVariantUtil.selectVariant(MangaDexCoverQuality.HD_EVERYWHERE, 1000, 1500),
        )
    }

    @Test
    fun `data saver quality selects smallest acceptable variant by width`() {
        assertEquals(
            MangaDexCoverVariant.LOW,
            MdCoverVariantUtil.selectVariant(MangaDexCoverQuality.DATA_SAVER, 48, 72),
        )
        assertEquals(
            MangaDexCoverVariant.LOW,
            MdCoverVariantUtil.selectVariant(MangaDexCoverQuality.DATA_SAVER, 200, 300),
        )
        assertEquals(
            MangaDexCoverVariant.LOW,
            MdCoverVariantUtil.selectVariant(MangaDexCoverQuality.DATA_SAVER, 362, 543),
        )
        assertEquals(
            MangaDexCoverVariant.LOW,
            MdCoverVariantUtil.selectVariant(MangaDexCoverQuality.DATA_SAVER, 472, 708),
        )
        assertEquals(
            MangaDexCoverVariant.MEDIUM,
            MdCoverVariantUtil.selectVariant(MangaDexCoverQuality.DATA_SAVER, 800, 1200),
        )
        assertEquals(
            MangaDexCoverVariant.LOW,
            MdCoverVariantUtil.selectVariant(MangaDexCoverQuality.DATA_SAVER, 0, 0),
        )
    }

    @Test
    fun `getVariantUrl produces correct URLs with suffixes`() {
        assertEquals(
            baseUrl,
            MdCoverVariantUtil.getVariantUrl(baseUrl, MangaDexCoverVariant.ORIGINAL),
        )
        assertEquals(
            "$baseUrl.512.jpg",
            MdCoverVariantUtil.getVariantUrl(baseUrl, MangaDexCoverVariant.MEDIUM),
        )
        assertEquals(
            "$baseUrl.256.jpg",
            MdCoverVariantUtil.getVariantUrl(baseUrl, MangaDexCoverVariant.LOW),
        )
        assertEquals(
            "$baseUrl.256.jpg",
            MdCoverVariantUtil.getVariantUrl("$baseUrl.512.jpg", MangaDexCoverVariant.LOW),
        )
    }
    @Test
    fun `in-flight request coalescer prevents duplicate concurrent executions`() = runTest {
        val executedCount = AtomicInteger(0)
        val coalescedCount = AtomicInteger(0)

        val task1 = async {
            InFlightRequestCoalescer.coalesce(
                key = "test_key",
                block = {
                    executedCount.incrementAndGet()
                    delay(50)
                    "downloaded"
                },
                onCoalesced = {
                    coalescedCount.incrementAndGet()
                    "cached"
                },
            )
        }

        val task2 = async {
            delay(10)
            InFlightRequestCoalescer.coalesce(
                key = "test_key",
                block = {
                    executedCount.incrementAndGet()
                    "downloaded"
                },
                onCoalesced = {
                    coalescedCount.incrementAndGet()
                    "cached"
                },
            )
        }

        val res1 = task1.await()
        val res2 = task2.await()

        assertEquals("downloaded", res1)
        assertEquals("cached", res2)
        assertEquals(1, executedCount.get())
        assertEquals(1, coalescedCount.get())
    }

    @Test
    fun `in-flight request coalescer lets followers self-fetch when leader fails`() = runTest {
        val followerSelfFetch = AtomicInteger(0)

        supervisorScope {
            val leader = async {
                InFlightRequestCoalescer.coalesce(
                    key = "fail_key",
                    block = { delay(50); error("leader failed") },
                    onCoalesced = { "cached" },
                )
            }
            val follower = async {
                delay(10)
                InFlightRequestCoalescer.coalesce(
                    key = "fail_key",
                    block = {
                        followerSelfFetch.incrementAndGet()
                        "self-fetched"
                    },
                    onCoalesced = { "cached" },
                )
            }

            assertEquals("self-fetched", follower.await())
            assertThrows<IllegalStateException> { leader.await() }
        }
        assertEquals(1, followerSelfFetch.get())
    }

    @Test
    fun `in-flight request coalescer rethrows follower cancellation instead of self-fetching`() = runTest {
        val followerSelfFetch = AtomicInteger(0)

        val leader = async {
            InFlightRequestCoalescer.coalesce(
                key = "cancel_key",
                block = { delay(100); "downloaded" },
                onCoalesced = { "cached" },
            )
        }
        val follower = async {
            InFlightRequestCoalescer.coalesce(
                key = "cancel_key",
                block = {
                    followerSelfFetch.incrementAndGet()
                    "self-fetched"
                },
                onCoalesced = { "cached" },
            )
        }
        follower.cancelAndJoin()

        assertEquals("downloaded", leader.await())
        assertEquals(0, followerSelfFetch.get())
    }

    @Test
    fun `isMangaDexCover identifies MangaDex URLs`() {
        assertTrue(MdCoverVariantUtil.isMangaDexCover(baseUrl))
        assertTrue(MdCoverVariantUtil.isMangaDexCover("$baseUrl.512.jpg"))
        assertFalse(MdCoverVariantUtil.isMangaDexCover("https://example.com/covers/123/img.jpg"))
        assertFalse(MdCoverVariantUtil.isMangaDexCover("file:///sdcard/cover.jpg"))
    }
}
