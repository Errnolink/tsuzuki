package exh.md.utils

import exh.md.dto.ListCallDto
import exh.util.under
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

suspend fun <T> mdListCall(request: suspend (offset: Int) -> ListCallDto<T>): List<T> {
    val first = request(0)
    if (first.limit <= 0 || !(first.limit under first.total)) return first.data
    val offsets = generateSequence(first.limit) { it + first.limit }.takeWhile { it under first.total }.toList()
    val gate = Semaphore(MAX_CONCURRENT_PAGE_FETCHES)
    val rest = coroutineScope { offsets.map { offset -> async { gate.withPermit { request(offset).data } } }.awaitAll() }
    return first.data + rest.flatten()
}

private const val MAX_CONCURRENT_PAGE_FETCHES = 4
inline fun <reified T> JsonElement.asMdMap(): Map<String, T> {
    return runCatching {
        MdUtil.jsonParser.decodeFromJsonElement<Map<String, T>>(jsonObject)
    }.getOrElse { emptyMap() }
}
