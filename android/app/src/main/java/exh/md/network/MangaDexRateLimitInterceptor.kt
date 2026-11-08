// Adapted from Neko (https://github.com/nekomangaorg/Neko), Apache License 2.0. Modified for Tsuzuki.
package exh.md.network

import exh.md.utils.MdApi
import java.io.IOException
import java.util.PriorityQueue
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

fun OkHttpClient.Builder.rateLimitPriority(
    permits: Int,
    period: Long = 1,
    unit: TimeUnit = TimeUnit.SECONDS,
    prioritySelector: (HttpUrl) -> Int,
) = addInterceptor(PriorityRateLimitInterceptor(permits, period, unit, prioritySelector))

class PriorityRateLimitInterceptor(
    private val permits: Int,
    period: Long,
    unit: TimeUnit,
    private val prioritySelector: (HttpUrl) -> Int,
    private val limits: (HttpUrl) -> Boolean = { true },
) : Interceptor {

    private val lock = Object()
    private val queue = PriorityQueue<Ticket>()
    private val startTimes = ArrayDeque<Long>()
    private val windowNanos = unit.toNanos(period)
    private var sequenceCounter = 0L

    private class Ticket(val priority: Int, val sequence: Long) : Comparable<Ticket> {
        override fun compareTo(other: Ticket): Int {
            val pComp = other.priority.compareTo(this.priority)
            if (pComp != 0) return pComp
            return this.sequence.compareTo(other.sequence)
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        if (!limits(url)) return chain.proceed(chain.request())
        val priority = prioritySelector(url)

        val ticket: Ticket

        synchronized(lock) {
            ticket = Ticket(priority, sequenceCounter++)
            queue.add(ticket)

            lock.notifyAll()

            try {
                while (true) {
                    if (chain.call().isCanceled()) {
                        queue.remove(ticket)
                        lock.notifyAll()
                        throw IOException("Canceled")
                    }

                    val head = queue.peek()

                    if (head === ticket) {
                        val now = System.nanoTime()
                        while (startTimes.isNotEmpty() && startTimes.first() <= now - windowNanos) {
                            startTimes.removeFirst()
                        }

                        if (startTimes.size < permits) {
                            startTimes.addLast(now)
                            queue.poll()
                            lock.notifyAll()
                            break
                        } else {
                            val waitNanos = startTimes.first() + windowNanos - now + 1_000_000L
                            if (waitNanos > 0) {
                                lock.wait(waitNanos / 1_000_000, (waitNanos % 1_000_000).toInt())
                            } else {
                                lock.wait(1)
                            }
                        }
                    } else {
                        lock.wait()
                    }
                }
            } catch (e: InterruptedException) {
                queue.remove(ticket)
                lock.notifyAll()
                Thread.currentThread().interrupt()
                throw IOException("Interrupted waiting for rate limit", e)
            }
        }

        return chain.proceed(chain.request())
    }
}

object MangaDexRateLimit {
    const val API_PERMITS_PER_SECOND = 5

    val interceptor = PriorityRateLimitInterceptor(
        permits = API_PERMITS_PER_SECOND,
        period = 1,
        unit = TimeUnit.SECONDS,
        prioritySelector = ::mangaDexRequestPriority,
        limits = ::isMangaDexApi,
    )

    fun isMangaDexApi(url: HttpUrl): Boolean = url.host == "api.mangadex.org" || url.host == "auth.mangadex.org"

    fun mangaDexRequestPriority(url: HttpUrl): Int {
        val string = url.toString()
        val isReaderChapter =
            url.pathSegments.isNotEmpty() &&
                url.pathSegments[0] == MdApi.chapter.substringAfterLast("/") &&
                string.substringAfterLast("/").isUUID()
        val isHomePageOrSearch =
            url.pathSegments.size == 1 &&
                url.pathSegments[0] == MdApi.manga.substringAfterLast("/")

        return when {
            isReaderChapter ||
                isHomePageOrSearch ||
                url.pathSegments.contains("chapter") ||
                url.pathSegments.contains("at-home") ||
                url.pathSegments.contains("user") ||
                url.pathSegments.contains("list") -> 10

            else -> 1
        }
    }

    fun limited(builder: OkHttpClient.Builder): OkHttpClient.Builder = builder.addInterceptor(interceptor)
}

private val uuidFormatLines = arrayOf(8, 13, 18, 23)
private val uuidFormatDigits = arrayOf((0..7), (9..12), (14..17), (19..22), (24..35))

private fun String.isUUID() =
    this.length == 36 &&
        uuidFormatLines.all { idx -> this[idx] == '-' } &&
        uuidFormatDigits.all { range ->
            range.all { idx ->
                this[idx].let { char -> char in '0'..'9' || char in 'a'..'f' || char in 'A'..'F' }
            }
        }
